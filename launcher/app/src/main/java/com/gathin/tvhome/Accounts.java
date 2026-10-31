package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
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
 * ({@link AccountSync}), and reading Household and Friends from the TMDB lists chosen on the TV ({@link ListChoices})
 * or set in config.xml ({@link AccountConfig}). Signing in is optional: signed out, everything stays on the TV.
 *
 * <p>Threads: everything here runs on the main thread except the network and the sync ledger, which live on one
 * low-priority worker thread, so Home never waits on TMDB. Network only while Home (or the sign-in page) is on
 * screen ({@link SyncPlan}). Nothing is logged: no token, session id or URL that carries one.
 */
final class Accounts implements Household.Recorder {
    private static final String PREFS = "tmdb_sync";
    private static final String LEDGER = "ledger", LEDGER_ACCOUNT = "ledger_account", IMPORTED = "imported";
    private static final String CHOICE_HOUSEHOLD = "household_list", CHOICE_FRIENDS = "friends_list";

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
    private final AccountHttp http;

    /** Main thread. Read on the worker through {@link #userToken}. */
    private volatile TmdbSession session;
    private final SignInFlow flow;
    private boolean visible;
    /** Main thread: whether changes are queued for an account (see {@link #queues}). */
    private boolean hasAccount;
    /** Household and Friends as chosen on the TV (Choose lists). */
    private final ListChoices choices;
    private boolean signInVisible;
    private long lastFull;
    private boolean running;
    private SyncPlan.Run again = SyncPlan.Run.NONE;
    /** Worker thread only; shared like the worker, so an Accounts made after a rebuild sees the same queue. */
    private static SyncLedger ledger;

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
        session = available() ? store.load() : null;
        // A TV that was ever signed in keeps queueing for that account, so changes made signed out go up later.
        hasAccount = available() && (session != null || !prefs.getString(LEDGER_ACCOUNT, "").isEmpty());
        choices = new ListChoices(prefs.getLong(CHOICE_HOUSEHOLD, 0), prefs.getLong(CHOICE_FRIENDS, 0));
        flow = new SignInFlow(session);
        loadImported();
        household.setRecorder(this);
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
        return ref(ListId.HOUSEHOLD).id > 0 || ref(ListId.FRIENDS).id > 0;
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
        Map<SyncList, List<String>> local = localForSync();
        List<SyncLedger.Op> unsent = household.unsent();
        AccountSync.Settings settings = settings();
        int readAt = household.version();
        long at = now();
        worker.execute(() -> {
            if (run == SyncPlan.Run.PUBLIC) {
                Map<ListId, List<String>> read = readPublic(settings);
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
            AccountSync.Result r = new AccountSync(http, s, l, settings).run(local, at,
                    run == SyncPlan.Run.FULL || adopted);
            saveLedger();
            main.post(() -> finish(s, settings, r, readAt, at));
        });
    }

    /** My list and Favorites, and Seen only when it follows a TMDB list. */
    private Map<SyncList, List<String>> localForSync() {
        Map<SyncList, List<String>> local = household.syncedLists();
        if (!config.syncsSeen()) local.remove(SyncList.SEEN);
        return local;
    }

    private void finish(TmdbSession s, AccountSync.Settings used, AccountSync.Result r, int readAt, long at) {
        running = false;
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
        Map<ListId, List<String>> imported = new EnumMap<>(ListId.class);
        if (r.lists.containsKey(SyncList.HOUSEHOLD)) imported.put(ListId.HOUSEHOLD, r.lists.get(SyncList.HOUSEHOLD));
        if (r.lists.containsKey(SyncList.FRIENDS)) imported.put(ListId.FRIENDS, r.lists.get(SyncList.FRIENDS));
        applyImported(imported, used);
        next();
    }

    private void finishPublic(Map<ListId, List<String>> read, AccountSync.Settings used, long at) {
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

    /** Worker: Household and Friends set by number, read with the app's token. A list that fails keeps its copy. */
    private Map<ListId, List<String>> readPublic(AccountSync.Settings settings) {
        Map<ListId, List<String>> out = new EnumMap<>(ListId.class);
        for (ListId list : IMPORTED_LISTS) {
            ListRef ref = imported(settings, list);
            if (ref.id <= 0) continue;
            try {
                out.put(list, AccountSync.readPublicList(http, ref.id));
            } catch (TmdbAccountParse.Failure e) {
                // Private, gone or offline: the last copy stays.
            }
        }
        return out;
    }

    // ---- Household and Friends -----------------------------------------------------------------------------------

    private static final ListId[] IMPORTED_LISTS = {ListId.HOUSEHOLD, ListId.FRIENDS};

    /** The lists Household and Friends read now: config.xml's override, else the TV's choice (ListChoices). */
    ListRef ref(ListId list) {
        return choices.ref(list, config.imported(list));
    }

    /** Whether config.xml sets the list, so a choice on the TV doesn't count for it. */
    boolean overridden(ListId list) {
        return !config.imported(list).isNone();
    }

    ListChoices.Role role(long listId) {
        return choices.role(listId);
    }

    /** A list chosen on the TV for Household or Friends, or Not used: saved, shown at once, read on the next sync. */
    void choose(long listId, ListChoices.Role role) {
        choices.choose(listId, role);
        prefs.edit().putLong(CHOICE_HOUSEHOLD, choices.household()).putLong(CHOICE_FRIENDS, choices.friends()).apply();
        loadImported();
        // A few presses in a row pick a role; read the lists once they settle.
        main.removeCallbacks(fullSoon);
        main.postDelayed(fullSoon, SyncPlan.DEBOUNCE);
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
        return new AccountSync.Settings(config.seen, ref(ListId.HOUSEHOLD), ref(ListId.FRIENDS), config.createSeen);
    }

    private static ListRef imported(AccountSync.Settings s, ListId list) {
        return list == ListId.HOUSEHOLD ? s.household : s.friends;
    }

    /**
     * Household and Friends as the TV shows them: a list that is set starts from the copy read last for that same
     * list (or empty); one that isn't stays the mock list until this TV has an account, then it is empty.
     */
    private void loadImported() {
        JSONObject saved = savedImported();
        for (ListId list : IMPORTED_LISTS) {
            ListRef ref = ref(list);
            if (!available() || ref.isNone()) {
                household.setImported(list, available() && hasAccount ? new ArrayList<String>() : null);
                continue;
            }
            List<String> ids = new ArrayList<>();
            JSONObject e = saved.optJSONObject(list.name());
            JSONArray a = e != null && ref.toString().equals(e.optString("ref")) ? e.optJSONArray("ids") : null;
            if (a != null) for (int i = 0; i < a.length(); i++) ids.add(a.optString(i));
            household.setImported(list, ids);
        }
    }

    /** What a sync read, unless the list was changed meanwhile ({@code used} is what the sync was asked to read). */
    private void applyImported(Map<ListId, List<String>> read, AccountSync.Settings used) {
        if (read.isEmpty()) return;
        JSONObject saved = savedImported();
        try {
            for (Map.Entry<ListId, List<String>> e : read.entrySet()) {
                ListRef ref = ref(e.getKey());
                if (!ref.toString().equals(imported(used, e.getKey()).toString())) continue;
                household.setImported(e.getKey(), e.getValue());
                saved.put(e.getKey().name(), new JSONObject().put("ref", ref.toString())
                        .put("ids", new JSONArray(e.getValue())));
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(IMPORTED, saved.toString()).apply();
    }

    private JSONObject savedImported() {
        try {
            return new JSONObject(prefs.getString(IMPORTED, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
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
            // Household and Friends not chosen yet leave the mock lists now that the TV has an account.
            loadImported();
            // A new account's lists replace the TV's (SeedPolicy); the same account carries on.
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
        store.clear();
        main.removeCallbacks(pushSoon);
        main.removeCallbacks(fullSoon);
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
