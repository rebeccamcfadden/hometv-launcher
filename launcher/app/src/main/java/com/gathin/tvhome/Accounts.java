package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The TMDB account on this TV (BACKLOG.md, TMDB sign-in and watchlist sync; docs/rework.md, Accounts, 2026-10-30):
 * signing in by QR code ({@link SignInFlow}), keeping My list, Favorites and Seen in step with the account
 * ({@link AccountSync}), and reading the TMDB lists shown as chips, by name: chosen on the TV ({@link ListChoices}) or
 * set in config.xml ({@link AccountConfig}); the title menu puts a title on one of them or takes it off
 * ({@link #setOnShownList}). Signing in is optional: signed out, everything stays on the TV.
 *
 * <p>Threads: everything here runs on the main thread except the network and the sync ledger, which live on one
 * low-priority worker thread, so Home never waits on TMDB. Network only while Home (or the sign-in page) is on
 * screen ({@link SyncPlan}). Nothing is logged: no token, session id or URL that carries one.
 */
final class Accounts implements Household.Recorder {
    private static final String PREFS = "tmdb_sync";
    private static final String LEDGER = "ledger", LEDGER_ACCOUNT = "ledger_account", IMPORTED = "imported";
    /** The shown lists chosen on the TV; before them, the lists chosen for Household and Friends (read to migrate). */
    private static final String CHOICE_SHOWN = "shown_lists";
    private static final String CHOICE_HOUSEHOLD = "household_list", CHOICE_FRIENDS = "friends_list";
    /** Phone notes: each note line's answer (NoteMatch.Memory), with the account it was found for. */
    private static final String NOTE_MEMORY = "note_memory";

    private final Household household;
    private final AccountConfig config;
    private final TmdbSessionStore store;
    private final SharedPreferences prefs;
    private final String appToken;
    private final Handler main = new Handler(Looper.getMainLooper());
    /**
     * One worker for the app's lifetime, shared by every Accounts: MainActivity makes a new Data (and with it a new
     * Accounts) each time Launcher settings change, and a thread each would pile up.
     */
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tmdb-account");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final List<Runnable> listeners = new ArrayList<>();
    /** The title menu's shown-list changes out to TMDB (main thread). */
    private final ShownEdits shownEdits = new ShownEdits();
    private final AccountHttp http;
    /** A shown list's name until TMDB gives it: "List 8221116". */
    private final java.util.function.LongFunction<String> unnamed;

    /** Main thread. Read on the worker through {@link #userToken}. */
    private volatile TmdbSession session;
    private final SignInFlow flow;
    private boolean visible;
    /** Main thread: whether changes are queued for an account (see {@link #queues}). */
    private boolean hasAccount;
    /** The lists shown as chips, as chosen on the TV (Choose lists). */
    private final ListChoices choices;
    private boolean signInVisible;
    private long lastFull;
    private boolean running;
    private SyncPlan.Run again = SyncPlan.Run.NONE;
    /** Worker thread only; shared like the worker, so an Accounts made after a rebuild sees the same queue. */
    private static SyncLedger ledger;
    /** Phone notes, whose waiting notes this account imports ({@link #runNotes}). */
    private final Notes notes;
    /** Main thread: a note is being imported; shared like the worker, so a rebuild doesn't start a second one. */
    private static boolean importing;
    /** Main thread: lists whose note TMDB refused in this round of imports, so the others still get their turn. */
    private static final java.util.Set<String> refused = new java.util.HashSet<>();

    private final Runnable pushSoon = () -> request(SyncPlan.Run.PUSH);
    private final Runnable fullSoon = () -> request(SyncPlan.Run.FULL);
    private final Runnable signInTick = this::signInTick;

    Accounts(Context ctx, Household household, String appToken) {
        Context app = ctx.getApplicationContext();
        this.household = household;
        this.appToken = appToken;
        this.config = AccountConfig.load(app);
        this.store = new TmdbSessionStore(app);
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.http = call -> TmdbClient.send(call, call.auth == AccountHttp.Auth.APP ? this.appToken : userToken());
        this.unnamed = id -> app.getString(R.string.account_kv_list_number, id);
        session = available() ? store.load() : null;
        // A TV that was ever signed in keeps queueing for that account, so changes made signed out go up later.
        hasAccount = available() && (session != null || !prefs.getString(LEDGER_ACCOUNT, "").isEmpty());
        choices = ListChoices.load(prefs.getString(CHOICE_SHOWN, null), prefs.getLong(CHOICE_HOUSEHOLD, 0),
                prefs.getLong(CHOICE_FRIENDS, 0));
        flow = new SignInFlow(session);
        loadImported();
        household.setRecorder(this);
        notes = Notes.get(app);
        notes.attach(this);
    }

    /** Whether this build can sign in at all: it needs the TMDB token from tv.properties. */
    boolean available() {
        return !appToken.isEmpty();
    }

    boolean signedIn() {
        return session != null;
    }

    /** The signed-in account's name, or null. */
    String username() {
        TmdbSession s = session;
        return s == null ? null : s.username;
    }

    SignInFlow flow() {
        return flow;
    }

    /** Hears sign-in steps, sign-outs and full syncs, on the main thread; for the sign-in page and Account pane. */
    void addListener(Runnable l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Runnable l) {
        listeners.remove(l);
    }

    // ---- Home on screen ------------------------------------------------------------------------------------------

    /** Home started or stopped (MainActivity onStart and onStop). */
    void setVisible(boolean v) {
        visible = v;
        if (!v) {
            main.removeCallbacks(pushSoon);
            main.removeCallbacks(fullSoon);
            main.removeCallbacks(signInTick);
            // An approved sign-in finishes even with Home hidden: a few calls, and the token is only in memory.
            if (flow.runsHidden()) signInTick();
            return;
        }
        if (!available()) return;
        request(SyncPlan.onShow(signedIn(), now(), lastFull, publicLists()));
        if (signInVisible) signInTick();
    }

    private boolean publicLists() {
        for (ListRef r : shownRefs()) if (r.id > 0) return true;
        return false;
    }

    // ---- Changes on the TV ---------------------------------------------------------------------------------------

    /**
     * Whether a change goes into the account's queue: only once this TV has an account to keep one for (signed in
     * now or before), and Seen only when it follows a TMDB list. Main thread.
     */
    @Override
    public boolean queues(SyncList list) {
        return available() && hasAccount && (list != SyncList.SEEN || config.syncsSeen());
    }

    /** The change and its unsent copy are saved (Household); move the copy into the ledger and send it soon. */
    @Override
    public void onListChanged() {
        if (!available()) return;
        List<SyncLedger.Op> unsent = household.unsent();
        if (!unsent.isEmpty()) {
            worker.execute(() -> {
                if (moveIntoLedger(unsent)) main.post(() -> household.queued(unsent));
            });
        }
        if (SyncPlan.onChange(signedIn(), visible) == SyncPlan.Run.PUSH) {
            main.removeCallbacks(pushSoon);
            main.postDelayed(pushSoon, SyncPlan.DEBOUNCE);
        }
    }

    /**
     * Worker: the household's unsent changes join the ledger, which is saved before they leave the household's file
     * (a process that dies in between has them in both, and the ledger keeps the newest). False while the ledger has
     * no account yet (they wait for the first sync to set it up) or could not be saved (they stay in the household's
     * file and are moved again next time).
     */
    private boolean moveIntoLedger(List<SyncLedger.Op> unsent) {
        if (unsent.isEmpty()) return false;
        SyncLedger l = ledger();
        if (l.account().isEmpty()) return false;
        l.recordAll(unsent);
        return saveLedger();
    }

    // ---- Syncing -------------------------------------------------------------------------------------------------

    private void request(SyncPlan.Run run) {
        if (run == SyncPlan.Run.NONE || !visible || !available()) return;
        if (running) {
            again = SyncPlan.combine(again, run);
            return;
        }
        TmdbSession s = session;
        if (run != SyncPlan.Run.PUBLIC && s == null) return;
        running = true;
        // The first full read after a sign-in shows its progress in the Account pane ("Importing your lists… 3 of 5").
        boolean showImport = importPending && run == SyncPlan.Run.FULL;
        if (showImport) {
            importDone = 0;
            importTotal = 0;
        }
        Map<SyncList, List<String>> local = localForSync();
        List<SyncLedger.Op> unsent = household.unsent();
        AccountSync.Settings settings = settings();
        int readAt = household.version();
        long at = now();
        worker.execute(() -> {
            if (run == SyncPlan.Run.PUBLIC) {
                Map<String, ImportedLists.Entry> read = readPublic(settings);
                main.post(() -> finishPublic(read, settings, at));
                return;
            }
            SyncLedger l = ledger();
            boolean adopted = SeedPolicy.prepare(l, s.accountObjectId) == SeedPolicy.FirstSync.ADOPT;
            if (adopted) {
                // The account's lists replace the TV's, so nothing of the TV's goes up, unsent changes included.
                if (saveLedger()) main.post(() -> household.queued(unsent));
            } else if (moveIntoLedger(unsent)) {
                // Changes saved on the TV but not queued yet (the process died first) are queued before the merge.
                main.post(() -> household.queued(unsent));
            }
            AccountSync sync = new AccountSync(http, s, l, settings);
            if (showImport) {
                sync.withProgress((done, total) -> main.post(() -> {
                    if (s != session) return;
                    importDone = done;
                    importTotal = total;
                    changed();
                }));
            }
            AccountSync.Result r = sync.run(local, at, run == SyncPlan.Run.FULL || adopted);
            saveLedger();
            main.post(() -> finish(s, settings, r, readAt, at, showImport));
        });
    }

    /** My list and Favorites, and Seen only when it follows a TMDB list. */
    private Map<SyncList, List<String>> localForSync() {
        Map<SyncList, List<String>> local = household.syncedLists();
        if (!config.syncsSeen()) local.remove(SyncList.SEEN);
        return local;
    }

    private void finish(TmdbSession s, AccountSync.Settings used, AccountSync.Result r, int readAt, long at,
            boolean imported) {
        running = false;
        if (imported) {
            importPending = false;
            importTotal = -1;
            changed();
        }
        if (s != session) {
            next();
            return;
        }
        if (r.signedOut) {
            dropSession();
            return;
        }
        if (!r.lists.isEmpty()) {
            lastFull = at;
            // Launcher settings' Account pane shows when the lists were last read.
            changed();
        }
        Map<SyncList, List<String>> mine = new EnumMap<>(SyncList.class);
        for (Map.Entry<SyncList, List<String>> e : r.lists.entrySet()) {
            if (e.getKey().writable) mine.put(e.getKey(), e.getValue());
        }
        if (!mine.isEmpty() && !household.applySynced(mine, readAt)) {
            // Changed on the TV while TMDB was read: that change is queued; merge again shortly.
            main.removeCallbacks(fullSoon);
            main.postDelayed(fullSoon, SyncPlan.DEBOUNCE);
        }
        // The TV's choice follows the account: its order, and a list deleted on TMDB leaves it.
        if (r.accountLists != null && choices.follow(listIds(r.accountLists))) saveChoices();
        applyImported(r.shown, used);
        // Notes sent while signed out or offline go on once the account's lists have been read.
        if (!r.lists.isEmpty()) runNotes(true);
        next();
    }

    private void finishPublic(Map<String, ImportedLists.Entry> read, AccountSync.Settings used, long at) {
        running = false;
        if (!read.isEmpty()) lastFull = at;
        applyImported(read, used);
        next();
    }

    private void next() {
        SyncPlan.Run run = again;
        again = SyncPlan.Run.NONE;
        request(run);
    }

    /** Sync now (Launcher settings, Account): a full sync while signed in; one already running runs again after. */
    void syncNow() {
        request(SyncPlan.Run.FULL);
    }

    /** When the account's lists were last read in full since Home started (ms), or 0 for not yet. */
    long lastSynced() {
        return lastFull;
    }

    /** Main thread: the first full read after a sign-in wants its progress shown (and hasn't finished). */
    private boolean importPending;
    /** Lists read so far, of {@link #importTotal}; total -1 while no import runs, 0 before the first step. */
    private int importDone, importTotal = -1;

    /** Whether the first read after a sign-in is running, for the Account pane's import bar. */
    boolean importing() {
        return importTotal >= 0;
    }

    int importDone() {
        return importDone;
    }

    int importTotal() {
        return importTotal;
    }

    /** Worker: the shown lists set by number, read with the app's token. A list that fails keeps its copy. */
    private Map<String, ImportedLists.Entry> readPublic(AccountSync.Settings settings) {
        Map<String, ImportedLists.Entry> out = new java.util.LinkedHashMap<>();
        for (ListRef ref : settings.shown) {
            if (ref.id <= 0) continue;
            try {
                out.put(ref.toString(), AccountSync.readPublicList(http, ref));
            } catch (TmdbAccountParse.Failure e) {
                // Private, gone or offline: the last copy stays.
            }
        }
        return out;
    }

    // ---- The lists shown as chips --------------------------------------------------------------------------------

    /** The lists that show now, in chip order: config.xml's when it sets any, else the TV's choice while signed in. */
    List<ListRef> shownRefs() {
        return choices.refs(config.shown, signedIn());
    }

    /** Whether config.xml sets the shown lists, so a choice on the TV doesn't change them. */
    boolean overridden() {
        return config.overridesShown();
    }

    /** Whether the TV's choice shows the list (Choose lists' switch). */
    boolean isShown(long listId) {
        return choices.isShown(listId);
    }

    /**
     * Shows or hides one of the account's lists (Choose lists): saved, shown at once in the account's order
     * ({@code accountLists}), and read on the next sync.
     */
    void setShown(long listId, boolean show, List<TmdbAccountParse.ListInfo> accountLists) {
        if (!choices.setShown(listId, show, listIds(accountLists))) return;
        saveChoices();
        // A few presses in a row pick the lists; read them once they settle.
        main.removeCallbacks(fullSoon);
        main.postDelayed(fullSoon, SyncPlan.DEBOUNCE);
    }

    private void saveChoices() {
        prefs.edit().putString(CHOICE_SHOWN, choices.save()).apply();
        loadImported();
    }

    /**
     * Puts a title on one of the shown TMDB lists, or takes it off (the title menu, F3): shown on the TV at once, then
     * sent to TMDB on the worker, after any sync queued before it. {@code done} hears on the main thread whether TMDB
     * took it; refused or offline, the list's copy goes back to what it held before (nothing is queued for later). Only
     * while signed in, for a list with a number and a TMDB title (TitleMenuRules.lists). Returns false, sending
     * nothing and without calling {@code done}, while a change to the same title on the same list is still out
     * ({@link #isShownPending}).
     */
    boolean setOnShownList(long listId, String titleId, boolean on, java.util.function.Consumer<Boolean> done) {
        TmdbSession s = session;
        if (s == null || listId <= 0 || TmdbAccountApi.mediaType(titleId) == null) {
            done.accept(false);
            return true;
        }
        if (!shownEdits.start(listId, titleId, on, shownHas(listId, titleId))) return false;
        // Where the title stood, so a refused removal puts it back in the same place ("Recently added" reads it).
        int at = on ? -1 : shownIndex(listId, titleId);
        editShown(listId, titleId, on);
        worker.execute(() -> {
            boolean ok;
            try {
                TmdbAccountParse.done(http.send(TmdbAccountApi.listItems(listId,
                        java.util.Collections.singletonList(titleId), on)));
                ok = true;
            } catch (IOException | TmdbAccountParse.Failure e) {
                ok = false;
            }
            boolean took = ok;
            main.post(() -> {
                Boolean now = shownEdits.finish(listId, titleId, took);
                // Signed out meanwhile: the TV's copy is still this account's, so a refusal still undoes it. Another
                // account signed in since: its copies aren't touched.
                if (now != null && (s == session || session == null)) {
                    if (!took && now && at >= 0) editShownAt(listId, titleId, at);
                    else editShown(listId, titleId, now);
                }
                done.accept(took);
            });
        });
        return true;
    }

    /** Where titleId stands on the saved copy of the shown list numbered listId, or -1. */
    private int shownIndex(long listId, String titleId) {
        for (ImportedLists.Entry e : ImportedLists.restore(shownRefs(), prefs.getString(IMPORTED, null), unnamed)) {
            if (e.id == listId) return e.ids.indexOf(titleId);
        }
        return -1;
    }

    /** The saved copy of the shown list numbered listId with titleId put back at {@code at}, shown at once. */
    private void editShownAt(long listId, String titleId, int at) {
        List<ImportedLists.Entry> saved = ImportedLists.restore(shownRefs(), prefs.getString(IMPORTED, null), unnamed);
        List<ImportedLists.Entry> next = ImportedLists.withTitleAt(saved, listId, titleId, at);
        if (next.equals(saved)) return;
        prefs.edit().putString(IMPORTED, ImportedLists.save(next)).apply();
        loadImported();
    }

    /** Whether a title menu change to titleId on the shown list numbered listId is still out to TMDB. */
    boolean isShownPending(long listId, String titleId) {
        return shownEdits.isPending(listId, titleId);
    }

    /** Whether the saved copy of the shown list numbered listId holds titleId. */
    private boolean shownHas(long listId, String titleId) {
        for (ImportedLists.Entry e : ImportedLists.restore(shownRefs(), prefs.getString(IMPORTED, null), unnamed)) {
            if (e.id == listId) return e.ids.contains(titleId);
        }
        return false;
    }

    /** The saved copy of the shown list numbered listId, with titleId on it or off, shown at once. */
    private void editShown(long listId, String titleId, boolean on) {
        List<ImportedLists.Entry> saved = ImportedLists.restore(shownRefs(), prefs.getString(IMPORTED, null), unnamed);
        List<ImportedLists.Entry> next = ImportedLists.withTitle(saved, listId, titleId, on);
        if (next.equals(saved)) return;
        prefs.edit().putString(IMPORTED, ImportedLists.save(next)).apply();
        loadImported();
    }

    private static List<Long> listIds(List<TmdbAccountParse.ListInfo> lists) {
        List<Long> out = new ArrayList<>();
        if (lists != null) for (TmdbAccountParse.ListInfo l : lists) out.add(l.id);
        return out;
    }

    /** The account's own lists, for Choose lists; {@code failed} when TMDB can't be reached. Main thread. */
    void loadAccountLists(java.util.function.Consumer<List<TmdbAccountParse.ListInfo>> done, Runnable failed) {
        TmdbSession s = session;
        if (s == null) {
            failed.run();
            return;
        }
        worker.execute(() -> {
            List<TmdbAccountParse.ListInfo> out = new ArrayList<>();
            try {
                for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
                    TmdbAccountParse.ListPage p = TmdbAccountParse.lists(
                            http.send(TmdbAccountApi.accountLists(s.accountObjectId, page)));
                    out.addAll(p.lists);
                    if (page >= p.totalPages) break;
                }
            } catch (IOException | TmdbAccountParse.Failure e) {
                main.post(failed);
                return;
            }
            main.post(() -> done.accept(out));
        });
    }

    private AccountSync.Settings settings() {
        return new AccountSync.Settings(config.seen, shownRefs(), config.createSeen);
    }

    /**
     * The lists after My list as the TV shows them: each shown list from the copy read last for that same setting (or
     * empty, until it is read); the mock Household and Friends while this TV never had an account and shows no list.
     */
    private void loadImported() {
        List<ListRef> refs = shownRefs();
        if (ListChoices.mock(available(), hasAccount, refs)) {
            household.setShownLists(null);
            return;
        }
        household.setShownLists(ImportedLists.restore(refs, prefs.getString(IMPORTED, null), unnamed));
    }

    /**
     * What a sync read, for the lists it was asked to read that still show ({@code used} is what it was asked); the
     * copies kept for offline are the shown lists' only.
     */
    private void applyImported(Map<String, ImportedLists.Entry> read, AccountSync.Settings used) {
        if (read.isEmpty()) return;
        List<String> asked = new ArrayList<>();
        for (ListRef r : used.shown) asked.add(r.toString());
        List<ImportedLists.Entry> out = new ArrayList<>();
        for (ImportedLists.Entry e : ImportedLists.restore(shownRefs(), prefs.getString(IMPORTED, null), unnamed)) {
            ImportedLists.Entry fresh = asked.contains(e.ref.toString()) ? read.get(e.ref.toString()) : null;
            out.add(fresh != null ? fresh : e);
        }
        // A title menu change TMDB hasn't answered yet may not be in what was read: keep showing it.
        out = shownEdits.apply(out);
        prefs.edit().putString(IMPORTED, ImportedLists.save(out)).apply();
        loadImported();
        // The Account pane names the shown lists.
        changed();
    }
    // ---- Phone notes ------------------------------------------------------------------------------------------------

    /**
     * Imports the notes waiting (Notes), one at a time on the worker so an import never races a sync, while signed in;
     * signed out they wait for the first full sync after the next sign-in. A note done makes its list a chip and
     * reads it again; one that fails waits for the next send or full sync. {@code quiet}: a retry, whose failing again
     * isn't shown. Main thread.
     */
    void runNotes(boolean quiet) {
        if (importing || !available()) return;
        TmdbSession s = session;
        if (s == null) return;
        NoteBook.Pending p = notes.next(refused);
        if (p == null) {
            refused.clear();
            return;
        }
        importing = true;
        notes.importStarted(p);
        NoteImport.Seen seen = ids -> main.post(() -> markSeen(ids));
        worker.execute(() -> {
            NoteMatch.Memory memory = readNoteMemory(s.accountObjectId);
            NoteImport.Outcome o = new NoteImport(http, s, seen).run(p.list, p.text, memory,
                    (done, total) -> main.post(() -> notes.importProgress(p, done, total)));
            if (o.memory != null) saveNoteMemory(s.accountObjectId, o.memory);
            main.post(() -> noteImported(s, p, o, quiet));
        });
    }

    private void noteImported(TmdbSession s, NoteBook.Pending p, NoteImport.Outcome o, boolean quiet) {
        importing = false;
        notes.importEnded(p, o, quiet);
        if (o.listId > 0 && o.memory != null && s == session) {
            // The list shows as a chip; read it again so the chip fills, but only when this import got somewhere: a
            // failing search would otherwise sync, retry and fail again every few seconds.
            setShown(o.listId, true, o.accountLists);
            if (o.status == NoteImport.Status.DONE || o.added > 0 || o.seen > 0) {
                main.removeCallbacks(fullSoon);
                main.postDelayed(fullSoon, SyncPlan.DEBOUNCE);
            }
        }
        switch (o.status) {
            case DONE:
                runNotes(quiet);
                break;
            case FAILED:
                refused.add(NoteLines.key(p.list));
                runNotes(quiet);
                break;
            default:
                // Offline or signed out: the others would fail too; the next send or full sync tries again.
                refused.clear();
                break;
        }
    }

    /**
     * The household answered a line asked about in Launcher settings (NotePicker): {@code id} onto the list, or to
     * Seen for a ticked line, or null for "None of these". On the worker, after any import queued before it, so the
     * lines' answers are never written by two at once. Main thread; {@code done} runs on it.
     */
    void runPick(String list, NoteMatch.Ask ask, String id, java.util.function.Consumer<NoteImport.Outcome> done) {
        TmdbSession s = session;
        if (s == null || !available()) {
            done.accept(NoteImport.Outcome.stopped(NoteImport.Status.SIGNED_OUT, 0, null));
            return;
        }
        NoteImport.Seen seen = ids -> main.post(() -> markSeen(ids));
        worker.execute(() -> {
            NoteMatch.Memory memory = readNoteMemory(s.accountObjectId);
            NoteImport.Outcome o = new NoteImport(http, s, seen).pick(list, ask, id, memory);
            if (o.memory != null) saveNoteMemory(s.accountObjectId, o.memory);
            main.post(() -> {
                if (o.listId > 0 && o.memory != null && s == session) {
                    setShown(o.listId, true, o.accountLists);
                    main.removeCallbacks(fullSoon);
                    main.postDelayed(fullSoon, SyncPlan.DEBOUNCE);
                }
                done.accept(o);
            });
        });
    }

    /** TMDB's answers for an asked line with none saved (NoteImport.lookUp), on the worker; null offline. Main thread. */
    void runLookUp(String query, java.util.function.Consumer<List<NoteMatch.Candidate>> done) {
        TmdbSession s = session;
        worker.execute(() -> {
            List<NoteMatch.Candidate> top = new NoteImport(http, s, ids -> { }).lookUp(query);
            main.post(() -> done.accept(top));
        });
    }

    /** Titles ticked in a note go to Seen, each only if it isn't there yet (synced like a change on the TV). */
    private void markSeen(List<String> ids) {
        for (String id : ids) if (!household.isSeen(id)) household.toggleSeen(id);
    }

    /** The note lines' answers for the signed-in account, to count a send's new lines. Any thread. */
    NoteMatch.Memory noteMemory() {
        TmdbSession s = session;
        return s == null ? new NoteMatch.Memory() : readNoteMemory(s.accountObjectId);
    }

    /** Saved per account: another account's lists are other lists. */
    private NoteMatch.Memory readNoteMemory(String account) {
        String saved = prefs.getString(NOTE_MEMORY, null);
        if (saved == null) return new NoteMatch.Memory();
        try {
            JSONObject o = new JSONObject(saved);
            if (!account.equals(o.optString("account", ""))) return new NoteMatch.Memory();
            return NoteMatch.Memory.fromJson(o.optJSONObject("lines"));
        } catch (JSONException e) {
            return new NoteMatch.Memory();
        }
    }

    private void saveNoteMemory(String account, NoteMatch.Memory memory) {
        try {
            prefs.edit().putString(NOTE_MEMORY, new JSONObject().put("account", account).put("lines", memory.toJson())
                    .toString()).apply();
        } catch (JSONException e) {
            // Not saved: the lines are looked up again next time.
        }
    }

    // ---- Signing in and out --------------------------------------------------------------------------------------

    /** The sign-in page shows or hides: the code is polled only while it shows; an approved sign-in still finishes. */
    void setSignInVisible(boolean v) {
        signInVisible = v;
        main.removeCallbacks(signInTick);
        if (v || flow.runsHidden()) signInTick();
    }

    /** Opening the sign-in page while signed out asks for a code; after it expired or failed, a new one. */
    void startSignIn() {
        if (!available()) return;
        flow.start(now());
        changed();
        signInTick();
    }

    /**
     * The sign-in page closed before the code was approved. An approved one (FINISHING) is let finish: its access
     * token lives only in memory until the session is saved, so its next step is scheduled again.
     */
    void cancelSignIn() {
        flow.cancel();
        main.removeCallbacks(signInTick);
        if (flow.runsHidden()) signInTick();
    }

    private void signInTick() {
        main.removeCallbacks(signInTick);
        long t = now();
        AccountHttp.Call call = flow.due(t, signInVisible && visible);
        if (call != null) {
            worker.execute(() -> {
                AccountHttp.Reply reply = null;
                IOException error = null;
                try {
                    reply = http.send(call);
                } catch (IOException e) {
                    error = e;
                }
                AccountHttp.Reply r = reply;
                IOException err = error;
                main.post(() -> {
                    if (r != null) flow.onReply(call, r, now());
                    else flow.onError(call, err, now());
                    afterStep();
                });
            });
        }
        changed();
        long next = flow.nextAt();
        if (call == null && next != Long.MAX_VALUE && (signInVisible && visible || flow.runsHidden())) {
            main.postDelayed(signInTick, Math.max(0, next - t));
        }
    }

    private void afterStep() {
        if (flow.phase() == SignInFlow.Phase.SIGNED_IN && session == null) {
            TmdbSession s = flow.session();
            store.save(s);
            session = s;
            hasAccount = true;
            lastFull = 0;
            // The mock lists go now that the TV has an account; the lists chosen to show come back.
            loadImported();
            // A new account's lists replace the TV's (SeedPolicy); the same account carries on.
            importPending = true;
            request(SyncPlan.Run.FULL);
        }
        signInTick();
    }

    /** Signs out: the tokens are forgotten on the TV and revoked on TMDB when it answers. The lists stay. */
    void signOut() {
        List<AccountHttp.Call> revoke = flow.signOut();
        TmdbSession s = session;
        dropSession();
        if (s == null) return;
        worker.execute(() -> {
            for (AccountHttp.Call c : revoke) {
                try {
                    http.send(c);
                } catch (IOException e) {
                    // Offline: the person can revoke the app in TMDB's settings; nothing on the TV depends on it.
                }
            }
        });
    }

    /** No session any more (signed out here, or TMDB no longer accepts it). The queue stays for the same account. */
    private void dropSession() {
        if (flow.phase() == SignInFlow.Phase.SIGNED_IN) flow.signOut();
        session = null;
        importPending = false;
        importTotal = -1;
        store.clear();
        main.removeCallbacks(pushSoon);
        main.removeCallbacks(fullSoon);
        // The lists chosen on the TV are the account's: hidden until it signs in again (config.xml's stay).
        loadImported();
        changed();
    }

    private void changed() {
        for (Runnable l : new ArrayList<>(listeners)) l.run();
    }

    // ---- Worker-side state ---------------------------------------------------------------------------------------

    private String userToken() throws IOException {
        TmdbSession s = session;
        if (s == null) throw new IOException("signed out");
        return s.accessToken;
    }

    private SyncLedger ledger() {
        if (ledger == null) ledger = SyncLedger.fromJson(prefs.getString(LEDGER, null));
        return ledger;
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    private boolean saveLedger() {
        SyncLedger l = ledger();
        // commit, not apply: the household's unsent copies are dropped only once this is on disk, so the caller drops
        // them only when this says it is. Worker thread.
        return prefs.edit().putString(LEDGER, l.toJson()).putString(LEDGER_ACCOUNT, l.account()).commit();
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
