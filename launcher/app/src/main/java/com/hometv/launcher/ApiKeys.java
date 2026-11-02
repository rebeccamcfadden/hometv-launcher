package com.hometv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This TV's API keys (F16; design_preview/README.md, API keys): the TMDB, Watchmode and Movie of the Night keys the
 * household entered on the phone or laptop page, kept in the launcher's private storage only, and the build's own
 * (tv.properties) where none was entered. Each key entered is checked with one free request ({@link KeyRules#check}),
 * and shown again only by its last four characters. The rules are {@link KeyRules}'s.
 *
 * <p>The page's server ({@link KeyInbox}) is open only while an API keys pane shows ({@link #hold}); it is opened and
 * closed on a thread of its own. A key changed moves Launcher settings' change count, so the launcher is rebuilt with it
 * when the page closes. One for the process, like {@link Notes}. Nothing is logged: the keys are secrets.
 */
final class ApiKeys {
    static final String PREFS = "api_keys";
    private static final String CODE = "code", STATE = ".state", REMOVED = ".removed";
    private static final int CONNECT_MS = 8000, READ_MS = 8000;

    /** Holds the application context only, which lives as long as the process anyway. */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static ApiKeys instance;

    /** The process's keys. */
    static synchronized ApiKeys get(Context ctx) {
        if (instance == null) instance = new ApiKeys(ctx.getApplicationContext());
        return instance;
    }

    private static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setPriority(Thread.MIN_PRIORITY);
            t.setDaemon(true);
            return t;
        });
    }

    /** Opens and closes the page's server, one at a time. */
    private final ExecutorService control = single("key-inbox-control");
    /** Checks keys again (OK on a row, or a key not reached before). */
    private final ExecutorService checks = single("key-check");

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Runnable> keyListeners = new ArrayList<>();
    /**
     * Each key's revision, moved on by every key sent, check started and removal: a check writes its answer only while
     * the revision it started at is still the key's, so a late answer never lands on a newer key or a removed one.
     * The keys being checked map to the revision their check started at; never saved. Guarded by itself.
     */
    private final int[] revision = new int[KeyRules.Id.values().length];
    private final Map<KeyRules.Id, Integer> checking = new EnumMap<>(KeyRules.Id.class);
    /** The panes showing now (main thread): the server is open while there is one. */
    private final Set<Object> holders = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Control thread only. */
    private KeyInbox inbox;
    /** The port was taken when the server last opened; the pane says so. */
    private volatile boolean portTaken;
    /** Unreached keys were checked again once in this process. */
    private boolean recheckedOnStart;
    /** The launcher is in front (MainActivity started); main thread. */
    private boolean foreground = true;

    private ApiKeys(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- The keys ------------------------------------------------------------------------------------------------

    /** The build's own key (tv.properties), "" for none. */
    static String built(KeyRules.Id id) {
        switch (id) {
            case TMDB:
                return BuildConfig.TMDB_TOKEN.trim();
            case WATCHMODE:
                return BuildConfig.WATCHMODE_KEY.trim();
            default:
                return PriceKeys.pick(BuildConfig.MOVIE_OF_THE_NIGHT_KEY, BuildConfig.STREAMING_AVAILABILITY_KEY);
        }
    }

    private String saved(KeyRules.Id id) {
        return prefs.getString(id.id, "");
    }

    private KeyRules.State savedState(KeyRules.Id id) {
        return KeyRules.State.of(prefs.getString(id.id + STATE, ""));
    }

    private boolean removed(KeyRules.Id id) {
        return prefs.getBoolean(id.id + REMOVED, false);
    }

    /** The state a key shows now. */
    KeyRules.State state(KeyRules.Id id) {
        synchronized (revision) {
            if (checking.containsKey(id)) return KeyRules.State.CHECKING;
        }
        return KeyRules.state(saved(id), savedState(id), removed(id), built(id));
    }

    /** The last four characters of the key entered; "" for the build's key or none. */
    String last4(KeyRules.Id id) {
        return KeyRules.last4(saved(id));
    }

    /** The key the TV uses for that service, "" for none. */
    String key(KeyRules.Id id) {
        return KeyRules.effective(saved(id), savedState(id), removed(id), built(id));
    }

    /** The key's state as kept, a check under way or not: what readiness is judged by. */
    private KeyRules.State kept(KeyRules.Id id) {
        return KeyRules.state(saved(id), savedState(id), removed(id), built(id));
    }

    /**
     * First run's TMDB key tab is done, and Account can sign in: the TV has a TMDB key that works. Judged by the key
     * kept, so OK on a working key (a check again) doesn't make it unready while the check runs.
     */
    boolean tmdbReady() {
        return kept(KeyRules.Id.TMDB).works();
    }

    /** Price lookup is set up: the Watchmode key works (Movie of the Night only backs it up). As {@link #tmdbReady}. */
    boolean pricesReady() {
        return kept(KeyRules.Id.WATCHMODE).works();
    }
    Map<KeyRules.Id, KeyRules.State> states() {
        Map<KeyRules.Id, KeyRules.State> out = new EnumMap<>(KeyRules.Id.class);
        for (KeyRules.Id id : KeyRules.Id.values()) out.put(id, state(id));
        return out;
    }

    KeyRules.Summary summary(Collection<KeyRules.Id> ids) {
        return KeyRules.summary(ids, states());
    }

    /**
     * A key arrived from the page (the server's thread): kept, then checked with its service, and kept with what the
     * check said. A key the service could never accept is refused without asking it. False when it couldn't be written
     * to the TV's storage: the page then says so rather than that it was kept.
     */
    @android.annotation.SuppressLint("ApplySharedPref")
    boolean submit(KeyRules.Id id, String key) {
        String k = key.trim();
        boolean valid = KeyRules.valid(id, k);
        int rev;
        boolean written;
        synchronized (revision) {
            rev = ++revision[id.ordinal()];
            // Commit, not apply (this is the inbox's thread): the page is told the key is kept only once it is on disk.
            written = prefs.edit().putString(id.id, k).putString(id.id + STATE, (valid ? KeyRules.State.UNREACHABLE
                    : KeyRules.State.BAD).wire).remove(id.id + REMOVED).commit();
            if (valid && written) checking.put(id, rev);
            else checking.remove(id);
        }
        forgetQuota(id);
        keyChanged();
        if (valid && written) finishCheck(id, rev, KeyRules.State.UNREACHABLE, check(id, k));
        return written;
    }

    /** A price key replaced or removed: its service's quota ledger was the old key's (PriceBook). */
    private void forgetQuota(KeyRules.Id id) {
        if (id == KeyRules.Id.WATCHMODE) PriceBook.forgetQuota(app, WatchmodePrices.ID);
        else if (id == KeyRules.Id.MOTN) PriceBook.forgetQuota(app, StreamingAvailabilityPrices.ID);
    }

    /**
     * A check's answer, kept only if nothing changed the key since it started (its revision is still current). A check
     * that changes the key the TV uses (a key refused on a check again) tells {@link #addKeyListener}'s listeners, since
     * the launcher running was built with the old one.
     */
    private void finishCheck(KeyRules.Id id, int rev, KeyRules.State before, KeyRules.State answer) {
        boolean usedChanged;
        synchronized (revision) {
            if (revision[id.ordinal()] != rev) return;
            String was = key(id);
            checking.remove(id);
            prefs.edit().putString(id.id + STATE, KeyRules.afterCheck(before, answer).wire).apply();
            usedChanged = !was.equals(key(id));
        }
        keyChanged();
        if (usedChanged) main.post(() -> {
            for (Runnable l : new ArrayList<>(keyListeners)) l.run();
        });
    }
    /** Check the key entered again: OK on its row, or one the TV couldn't reach before. Main thread. */
    void recheck(KeyRules.Id id) {
        String k = saved(id);
        KeyRules.State before = savedState(id);
        int rev;
        synchronized (revision) {
            if (k.isEmpty() || checking.containsKey(id)) return;
            rev = ++revision[id.ordinal()];
            checking.put(id, rev);
        }
        // Posted: this can come from a pane being attached (hold), which mustn't redraw in the middle of that.
        main.post(this::changed);
        checks.execute(() -> finishCheck(id, rev, before,
                KeyRules.valid(id, k) ? check(id, k) : KeyRules.State.BAD));
    }

    /** The keys the TV couldn't reach before, checked again: once as the launcher starts, and as a keys pane opens. */
    void recheckUnreached(boolean onStart) {
        if (onStart && recheckedOnStart) return;
        recheckedOnStart = true;
        for (KeyRules.Id id : KeyRules.Id.values()) {
            if (state(id) == KeyRules.State.UNREACHABLE) recheck(id);
        }
    }

    /**
     * Remove: the TV has no key for that service, not even the build's (the project lead, 2026-11-02), until one is
     * entered on the page again. Main thread.
     */
    void remove(KeyRules.Id id) {
        synchronized (revision) {
            revision[id.ordinal()]++;
            checking.remove(id);
            prefs.edit().remove(id.id).remove(id.id + STATE).putBoolean(id.id + REMOVED, true).apply();
        }
        forgetQuota(id);
        keyChanged();
    }

    private KeyRules.State check(KeyRules.Id id, String key) {
        KeyRules.Check c = KeyRules.check(id, key);
        try {
            return KeyRules.fromStatus(httpStatus(c.url, c.headers));
        } catch (IOException | RuntimeException e) {
            return KeyRules.State.UNREACHABLE;
        }
    }

    private static int httpStatus(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            return c.getResponseCode();
        } finally {
            c.disconnect();
        }
    }

    /** A key changed: the launcher is rebuilt with it when Launcher settings or First run closes. */
    private void keyChanged() {
        LauncherSettingsPage.changed(app);
        main.post(this::changed);
    }

    // ---- The page's server ---------------------------------------------------------------------------------------

    /** The code the page needs, made the first time it is asked for. */
    String code() {
        String c = prefs.getString(CODE, "");
        if (c.isEmpty()) {
            c = NoteInbox.newCode(new SecureRandom());
            prefs.edit().putString(CODE, c).apply();
        }
        return c;
    }

    /** New code: a page opened with the old one can't send keys any more. Main thread. */
    void newCode() {
        prefs.edit().putString(CODE, NoteInbox.newCode(new SecureRandom())).apply();
        if (serving()) open();
        changed();
    }

    boolean portTaken() {
        return serving() && portTaken;
    }

    /** The server should be open: a keys pane shows, and the launcher is in front. Main thread. */
    private boolean serving() {
        return foreground && !holders.isEmpty();
    }

    /** A keys pane shows: the server opens if it isn't open. Main thread. */
    void hold(Object pane) {
        if (holders.add(pane) && holders.size() == 1 && foreground) {
            open();
            recheckUnreached(false);
        }
    }

    /** A keys pane is gone: the server closes when it was the last. Main thread. */
    void release(Object pane) {
        if (holders.remove(pane) && holders.isEmpty() && foreground) close();
    }

    /**
     * The launcher went to the background (another app, the screensaver, standby) or came back (MainActivity's
     * onStop and onStart): a keys pane left showing keeps its views, so the server closes here and opens again on
     * return. Main thread.
     */
    void setForeground(boolean on) {
        if (on == foreground) return;
        foreground = on;
        if (holders.isEmpty()) return;
        if (on) open();
        else close();
    }

    private void open() {
        String code = code();
        control.execute(() -> {
            if (inbox != null) inbox.stop();
            inbox = new KeyInbox(code, handler, System::currentTimeMillis, page());
            boolean taken = true;
            // A port just let go can take a moment to be free again; another app's stays taken.
            for (int i = 0; i < 3 && taken; i++) {
                try {
                    if (i > 0) Thread.sleep(300);
                    inbox.start(KeyInbox.DEFAULT_PORT);
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

    private final KeyInbox.Handler handler = new KeyInbox.Handler() {
        @Override
        public Map<KeyRules.Id, KeyInbox.Entry> states() {
            Map<KeyRules.Id, KeyInbox.Entry> out = new EnumMap<>(KeyRules.Id.class);
            for (KeyRules.Id id : KeyRules.Id.values()) out.put(id, new KeyInbox.Entry(state(id), last4(id)));
            return out;
        }

        @Override
        public boolean submit(KeyRules.Id id, String key) {
            return ApiKeys.this.submit(id, key);
        }
    };

    /** The page the QR opens (res/raw/keys_setup.html), or null if it can't be read. */
    private String page() {
        try (InputStream in = app.getResources().openRawResource(R.raw.keys_setup)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | android.content.res.Resources.NotFoundException e) {
            return null;
        }
    }

    // ---- Listeners (main thread) ---------------------------------------------------------------------------------

    /** A key's state, the code or the server changed: for the panes and the rows' values. */
    void addListener(Runnable l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Runnable l) {
        listeners.remove(l);
    }

    /** A check changed a key the launcher uses (refused it): the running launcher must be rebuilt. Main thread. */
    void addKeyListener(Runnable l) {
        if (!keyListeners.contains(l)) keyListeners.add(l);
    }

    void removeKeyListener(Runnable l) {
        keyListeners.remove(l);
    }

    private void changed() {
        for (Runnable l : new ArrayList<>(listeners)) l.run();
    }
}
