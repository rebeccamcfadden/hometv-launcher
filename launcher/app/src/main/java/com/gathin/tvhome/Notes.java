package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phone notes on this TV (BACKLOG.md, Phone notes; docs/phone-notes.md): the switch, the pairing code, the inbox that
 * listens while it is on ({@link NoteInbox}), the notes waiting to be imported and each list's last send
 * ({@link NoteBook}), and what the import pill shows. One for the process, like the account's worker: MainActivity is
 * rebuilt whenever Launcher settings change, and the inbox must not be opened twice. The imports themselves run on the
 * account's worker ({@link Accounts#runNotes}).
 *
 * <p>Off unless config.xml's note_inbox (or a pushed config.json) says on, or the household turned it on in Launcher
 * settings; the household's choice wins. The inbox is opened and closed on a thread of its own, never the main one.
 * Nothing is logged: the notes are the household's, and the code is a secret.
 */
final class Notes {
    static final String PREFS = "phone_notes";
    private static final String ON = "on", CODE = "code", PENDING = "pending", SENDS = "sends";
    /** How long the pill shows how an import ended. */
    static final long FINISHED_MS = 4000;

    /** Holds the application context only, which lives as long as the process anyway. */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static Notes instance;

    /** The process's Notes; main thread. */
    static Notes get(Context ctx) {
        if (instance == null) instance = new Notes(ctx.getApplicationContext());
        return instance;
    }

    /** Opens and closes the inbox, off the main thread; one at a time so a quick Off/On can't open it twice. */
    private static final ExecutorService control = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "note-inbox-control");
        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true);
        return t;
    });

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final boolean byDefault;
    private final NoteBook book;
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Runnable> pillListeners = new ArrayList<>();
    private final Runnable clearPill = () -> setPill(null);

    /** The account that imports notes: the newest Data's. Read on the inbox thread. */
    private volatile Accounts accounts;
    /** Control thread only. */
    private NoteInbox inbox;
    /** The port was taken when the inbox last opened; the pane says so. */
    private volatile boolean portTaken;
    /** Main thread: what the pill shows, or null. */
    private NoteWords.Pill pill;

    private Notes(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.byDefault = configDefault(app);
        this.book = NoteBook.restore(prefs.getString(PENDING, null), prefs.getString(SENDS, null));
        if (on()) open();
    }

    /** config.xml's note_inbox, overlaid by a pushed config.json like every other key. */
    private static boolean configDefault(Context ctx) {
        boolean on = ctx.getResources().getBoolean(R.bool.note_inbox);
        JSONObject json = Config.overlay(ctx);
        return json != null ? json.optBoolean("note_inbox", on) : on;
    }

    /**
     * The shared "Send to TV" shortcut's link: a pushed config.json's note_shortcut_url wins, then tv.properties' (built
     * in, per household), then config.xml's. NoteWords.shortcutLink checks it before the page shows it.
     */
    private static String shortcutUrl(Context ctx) {
        String url = BuildConfig.NOTE_SHORTCUT_URL;
        if (url == null || url.isEmpty()) url = ctx.getString(R.string.note_shortcut_url);
        JSONObject json = Config.overlay(ctx);
        return json != null ? json.optString("note_shortcut_url", url) : url;
    }

    /** The account whose lists notes go to; each new Data hands its own. Main thread. */
    void attach(Accounts a) {
        accounts = a;
        // Notes that waited for a rebuild (or a restart) are tried once the account has synced.
    }

    // ---- The switch and the code ---------------------------------------------------------------------------------

    boolean on() {
        return prefs.contains(ON) ? prefs.getBoolean(ON, false) : byDefault;
    }

    /** Launcher settings' switch: saved, and the inbox opened or closed. Main thread. */
    void setOn(boolean on) {
        if (on == on() && prefs.contains(ON)) return;
        SharedPreferences.Editor e = prefs.edit().putBoolean(ON, on);
        if (on && prefs.getString(CODE, "").isEmpty()) e.putString(CODE, NoteInbox.newCode(new SecureRandom()));
        e.apply();
        if (on) open();
        else close();
        changed();
    }

    /** The pairing code, or "" before Phone notes was first turned on. */
    String code() {
        return prefs.getString(CODE, "");
    }

    /** New code: phones need the new one; the inbox reopens with it. Main thread. */
    void newCode() {
        prefs.edit().putString(CODE, NoteInbox.newCode(new SecureRandom())).apply();
        if (on()) open();
        changed();
    }

    boolean portTaken() {
        return on() && portTaken;
    }

    /** The page the pane's QR opens on a phone (res/raw/notes_setup.html), or null if it can't be read. */
    private String setupPage() {
        try (java.io.InputStream in = app.getResources().openRawResource(R.raw.notes_setup)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return NoteWords.setupPage(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8),
                    shortcutUrl(app));
        } catch (IOException | android.content.res.Resources.NotFoundException e) {
            return null;
        }
    }

    private void open() {
        if (code().isEmpty()) prefs.edit().putString(CODE, NoteInbox.newCode(new SecureRandom())).apply();
        String code = code();
        control.execute(() -> {
            if (inbox != null) inbox.stop();
            inbox = new NoteInbox(code, this::accept, System::currentTimeMillis, setupPage());
            boolean taken = true;
            // A port just let go can take a moment to be free again; another app's stays taken.
            for (int i = 0; i < 3 && taken; i++) {
                try {
                    if (i > 0) Thread.sleep(300);
                    inbox.start(NoteInbox.DEFAULT_PORT);
                    taken = false;
                } catch (IOException | SecurityException e) {
                    taken = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (taken) inbox = null;
            if (taken != portTaken) {
                portTaken = taken;
                main.post(this::changed);
            }
        });
    }

    private void close() {
        control.execute(() -> {
            if (inbox != null) inbox.stop();
            inbox = null;
            portTaken = false;
        });
    }

    // ---- A note arrives (inbox thread) ---------------------------------------------------------------------------

    private NoteInbox.Reply accept(String list, String text) {
        Resources res = app.getResources();
        List<NoteLines.Line> lines = NoteLines.parse(text);
        if (lines.isEmpty()) return NoteInbox.Reply.error(400, res.getString(R.string.notes_reply_no_titles));
        Accounts a = accounts;
        NoteMatch.Memory memory = a != null ? a.noteMemory() : new NoteMatch.Memory();
        int fresh = 0;
        for (NoteLines.Line l : lines) if (memory.get(list, l.key) == null) fresh++;
        book.put(list, text, System.currentTimeMillis());
        prefs.edit().putString(PENDING, book.pendingJson()).apply();
        main.post(() -> {
            changed();
            Accounts now = accounts;
            if (now != null) now.runNotes(false);
        });
        boolean signedIn = a != null && a.signedIn();
        String message = signedIn ? res.getQuantityString(R.plurals.notes_reply_sent, lines.size(), lines.size(), fresh)
                : res.getString(R.string.notes_reply_signed_out);
        return new NoteInbox.Reply(200, message, lines.size(), fresh);
    }

    // ---- Imports (main thread; Accounts runs them) ---------------------------------------------------------------

    /** The oldest note waiting whose list isn't in {@code skip} (list keys), or null. */
    NoteBook.Pending next(java.util.Set<String> skip) {
        return book.next(skip);
    }

    List<NoteBook.Pending> pending() {
        return book.pending();
    }

    List<NoteBook.Send> sends() {
        return book.sends();
    }

    int unmatched() {
        return book.unmatched();
    }

    void importStarted(NoteBook.Pending p) {
        setPill(NoteWords.Pill.importing(p.list, 0, NoteLines.parse(p.text).size()));
    }

    void importProgress(NoteBook.Pending p, int done, int total) {
        if (pill == null || pill.kind != NoteWords.Kind.IMPORTING) return;
        setPill(NoteWords.Pill.importing(p.list, done, total));
    }

    /**
     * An import ended. Done: the note stops waiting and its send is recorded for the pane. Otherwise it waits; a
     * {@code quiet} retry (after a sync) says nothing about it failing again.
     */
    void importEnded(NoteBook.Pending p, NoteImport.Outcome o, boolean quiet) {
        if (o.status == NoteImport.Status.DONE) {
            book.done(p);
            book.record(new NoteBook.Send(p.list, System.currentTimeMillis(), o.lines, o.added, o.asks));
            prefs.edit().putString(PENDING, book.pendingJson()).putString(SENDS, book.sendsJson()).apply();
            changed();
        }
        if (quiet && o.status != NoteImport.Status.DONE) {
            setPill(null);
            return;
        }
        setPill(NoteWords.Pill.ended(p.list, o.status, o.added, o.unmatched.size()));
        main.postDelayed(clearPill, FINISHED_MS);
    }

    /**
     * Launcher settings' picker answered a line of a list's last send: {@code id} onto the list (or Seen, for a
     * ticked line), or null for "None of these". Done, the line leaves Last sends; otherwise it stays to ask again and
     * the pill says why. Main thread.
     */
    void answer(String list, NoteMatch.Ask ask, String id) {
        Accounts a = accounts;
        if (a == null) return;
        a.runPick(list, ask, id, o -> {
            if (o.status == NoteImport.Status.DONE) {
                if (book.answered(list, ask.key)) prefs.edit().putString(SENDS, book.sendsJson()).apply();
                changed();
                if (o.added == 0) return;
            }
            setPill(NoteWords.Pill.ended(list, o.status, o.added, 0));
            main.postDelayed(clearPill, FINISHED_MS);
        });
    }

    /**
     * Looks up TMDB's answers for an asked line, by {@code query} (the line's text, or what the household typed to
     * search again), keeps them with the send, and hands back the line with them (none if TMDB had none; null when it
     * couldn't be reached). Main thread.
     */
    void lookUp(String list, NoteMatch.Ask ask, String query, java.util.function.Consumer<NoteMatch.Ask> done) {
        Accounts a = accounts;
        if (a == null) {
            done.accept(null);
            return;
        }
        a.runLookUp(query, top -> {
            if (top == null) {
                done.accept(null);
                return;
            }
            // Kept even when there were none, so reopening the line shows that search, not an older one.
            book.offered(list, ask.key, top);
            prefs.edit().putString(SENDS, book.sendsJson()).apply();
            changed();
            done.accept(ask.with(top));
        });
    }

    NoteWords.Pill pill() {
        return pill;
    }

    private void setPill(NoteWords.Pill p) {
        main.removeCallbacks(clearPill);
        pill = p;
        for (Runnable l : new ArrayList<>(pillListeners)) l.run();
    }

    // ---- Listeners -----------------------------------------------------------------------------------------------

    /** The switch, the code, a note arriving or an import ending: for the pane and the section's value. */
    void addListener(Runnable l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Runnable l) {
        listeners.remove(l);
    }

    /** The pill's text changed. */
    void addPillListener(Runnable l) {
        if (!pillListeners.contains(l)) pillListeners.add(l);
    }

    void removePillListener(Runnable l) {
        pillListeners.remove(l);
    }

    private void changed() {
        for (Runnable l : new ArrayList<>(listeners)) l.run();
    }

    // ---- The TV's address ----------------------------------------------------------------------------------------

    /** The TV's IPv4 address on Wi-Fi (or, failing that, Ethernet) as a phone reaches it, or null. */
    static String localAddress(Context ctx) {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        if (cm == null) return null;
        String ethernet = null;
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                LinkProperties lp = cm.getLinkProperties(n);
                if (caps == null || lp == null) continue;
                boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                if (!wifi && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) continue;
                for (LinkAddress la : lp.getLinkAddresses()) {
                    InetAddress a = la.getAddress();
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    if (wifi) return a.getHostAddress();
                    if (ethernet == null) ethernet = a.getHostAddress();
                }
            }
        } catch (SecurityException e) {
            return null;
        }
        return ethernet;
    }
}
