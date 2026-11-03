package com.hometv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Phone remote on this TV (F27 with F39; docs/phone-remote.md): the switch (Launcher settings › Features' "remote",
 * off by default), the pairing code, the paired phones, and the server that listens while it is on
 * ({@link RemoteServer} over {@link RemoteTls}). One for the process, like Phone notes: MainActivity is rebuilt
 * whenever Launcher settings change, and the server must not be opened twice. It is opened and closed on a thread of
 * its own, never the main one.
 *
 * <p>Each phone gets its own token when it pairs; the TV keeps only the token's SHA-256, the phone's name and when it
 * was last used. What a phone asks for is done on the main thread, through the {@link Bridge} MainActivity hands in
 * while it lives; with no Home showing, only Home works. Nothing is logged: what a phone types may be a password.
 */
final class PhoneRemote {
    static final String PREFS = "phone_remote";
    private static final String CODE = "code", PHONES = "phones", PORT = "port";
    /** The port the server listens on now (the pane's address and QR); any thread. */
    private volatile int port = RemoteServer.DEFAULT_PORT;
    /** A phone that said nothing for this long is no longer "connected" (its page polls every 2 s while open). */
    static final long CONNECTED_MS = 25_000;
    /** How long a server thread waits for the main thread to do what a phone asked. */
    private static final long MAIN_WAIT_MS = 2000;
    /** How long a state call waits before answering with the last known state (the page polls every 2 s). */
    private static final long STATE_WAIT_MS = 600;
    /** "Last used" is saved at most this often per phone, not once per key. */
    private static final long SAVE_USE_MS = 60_000;

    /** What the remote does on the launcher's screens; MainActivity's, main thread. */
    interface Bridge {
        /** A remote key: "up", "down", "left", "right", "ok", "back", or "home" (which also brings Home to the front). */
        void press(String key);

        /** The text box of the screen showing, or null for buttons only. */
        Screen.TextField textField();

        /** The screen showing, for "On the TV: Search": its tab, a title's name, or its label. */
        String screenName();

        /** F39: the title showing (an open preview, or a focused title card), or null. */
        Title title();

        /** F39: a title by id, from what the TV has seen (the catalogue and its searches), or null. */
        Title titleById(String id);

        boolean inMyList(String id);

        /**
         * F39: the title on My list or off it, as the phone asks; nothing changes, and no toast, when it already is
         * (another phone or the TV got there first). Returns whether it is on My list now.
         */
        boolean setMyList(Title t, boolean mine, String phone);

        /** F39: titles for a query, as Search finds them; {@code done} on the main thread. */
        void search(String query, Consumer<List<Title>> done);

        /** A phone paired: a toast on the TV. */
        void paired(String phone);
    }

    /** A phone as the pane lists it. */
    static final class Paired {
        final String id, name, hash;
        final long lastUsed;

        Paired(String id, String name, String hash, long lastUsed) {
            this.id = id;
            this.name = name;
            this.hash = hash;
            this.lastUsed = lastUsed;
        }
    }

    @android.annotation.SuppressLint("StaticFieldLeak")
    private static PhoneRemote instance;

    /** The process's PhoneRemote; main thread. */
    static PhoneRemote get(Context ctx) {
        if (instance == null) instance = new PhoneRemote(ctx.getApplicationContext());
        return instance;
    }

    private static final ExecutorService control = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "phone-remote-control");
        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true);
        return t;
    });

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> listeners = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();
    /** Phone id → when it last spoke; any thread. */
    private final Map<String, Long> seen = new HashMap<>();
    /** Main thread: the Home that is in front, or null. */
    private Bridge bridge;
    /** Control thread only. */
    private RemoteServer server;
    private volatile boolean portTaken, failed;
    private final Runnable tick = this::tick;
    private String connectedBefore = "";
    /** The last state the main thread answered, for a state call while it is busy; any thread. */
    private volatile JSONObject lastState;
    /** Text a phone sent that the main thread hasn't typed yet; only the newest is kept. */
    private final AtomicReference<String> pendingText = new AtomicReference<>();

    private PhoneRemote(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- The switch ----------------------------------------------------------------------------------------------

    /** Whether it is switched on, as saved now (Launcher settings may be open; the launcher's own copy is older). */
    boolean on() {
        return FeaturePrefs.live(app).on(Features.Feature.REMOTE);
    }

    /** The pane's On / Off: saved as the Features switch, which opens or closes the server (FeaturePrefs#save). Main thread. */
    void setOn(boolean on) {
        if (on == on()) return;
        FeaturePrefs.save(app, Features.Feature.REMOTE, on);
    }

    /** Opens or closes the server to match the switch (MainActivity as it is built, FeaturePrefs#save). Main thread. */
    void sync() {
        if (on()) open();
        else close();
        changed();
    }

    /** MainActivity in front hands itself in; null when it goes. Main thread. */
    void attach(Bridge b) {
        bridge = b;
    }

    void detach(Bridge b) {
        if (bridge == b) bridge = null;
    }

    // ---- The code and the phones -----------------------------------------------------------------------------------

    /** The pairing code, "K7QM-4PXT"; made the first time it is asked for. */
    String code() {
        String c = prefs.getString(CODE, "");
        if (c.isEmpty()) {
            c = NoteInbox.newCode(random);
            prefs.edit().putString(CODE, c).apply();
        }
        return c;
    }

    /** New code: phones already paired keep working; the old code no longer pairs. Main thread. */
    void newCode() {
        prefs.edit().putString(CODE, NoteInbox.newCode(random)).apply();
        changed();
    }

    synchronized List<Paired> phones() {
        List<Paired> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(PHONES, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Paired(o.getString("id"), o.optString("name"), o.getString("hash"), o.optLong("used")));
            }
        } catch (JSONException e) {
            // A list we can't read pairs nobody.
        }
        return out;
    }

    private synchronized void savePhones(List<Paired> phones) {
        JSONArray a = new JSONArray();
        try {
            for (Paired p : phones) {
                a.put(new JSONObject().put("id", p.id).put("name", p.name).put("hash", p.hash).put("used", p.lastUsed));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs.edit().putString(PHONES, a.toString()).apply();
    }

    /**
     * Every change to the paired phones, read and written under one lock: a phone forgotten on the main thread can't
     * come back from a server thread's older copy, and two phones pairing at once both stay.
     */
    private synchronized void update(java.util.function.UnaryOperator<List<Paired>> change) {
        savePhones(change.apply(phones()));
    }

    boolean isConnected(Paired p) {
        synchronized (seen) {
            Long at = seen.get(p.id);
            return at != null && System.currentTimeMillis() - at < CONNECTED_MS;
        }
    }

    /** The first phone connected now, or null; for "A phone is connected". */
    Paired connected() {
        for (Paired p : phones()) if (isConnected(p)) return p;
        return null;
    }

    /** Forget one phone: its token stops working at once. Main thread. */
    void forget(String id) {
        update(phones -> {
            List<Paired> keep = new ArrayList<>();
            for (Paired p : phones) if (!p.id.equals(id)) keep.add(p);
            return keep;
        });
        synchronized (seen) {
            seen.remove(id);
        }
        changed();
    }

    /** Forget all: every phone, a new code, and a new key and certificate (so the server opens again). Main thread. */
    void forgetAll() {
        update(phones -> new ArrayList<>());
        synchronized (seen) {
            seen.clear();
        }
        prefs.edit().putString(CODE, NoteInbox.newCode(random)).apply();
        boolean wasOn = on();
        control.execute(() -> {
            if (server != null) server.stop();
            server = null;
            RemoteTls.forget(app.getFilesDir());
        });
        if (wasOn) open();
        changed();
    }

    boolean portTaken() {
        return on() && portTaken;
    }

    /** The port phones reach: the one bound now, else the one used last. */
    int port() {
        return server != null && server.running() ? port : prefs.getInt(PORT, RemoteServer.DEFAULT_PORT);
    }

    /** The server couldn't start for another reason (the Keystore refused); the pane says so. */
    boolean failed() {
        return on() && failed;
    }

    /** The TV's name in the page's header: the name set in the TV's settings, else its model. */
    private String tvName() {
        String n = null;
        try {
            n = Settings.Global.getString(app.getContentResolver(), Settings.Global.DEVICE_NAME);
        } catch (SecurityException ignored) {
            // The model, then.
        }
        return n == null || n.trim().isEmpty() ? Build.MODEL : n.trim();
    }

    // ---- The server ----------------------------------------------------------------------------------------------

    private void open() {
        code();
        control.execute(() -> {
            if (server != null && server.running()) return;
            boolean taken = false, broken = false;
            try {
                server = new RemoteServer(RemoteTls.serverSocketFactory(app.getFilesDir()), this::code, new Calls(),
                        System::currentTimeMillis, page());
                taken = true;
                // The port this TV used last, so phones' saved pages still work; else the first free of the few.
                int last = prefs.getInt(PORT, RemoteServer.DEFAULT_PORT);
                List<Integer> ports = new ArrayList<>();
                ports.add(last);
                for (int p = RemoteServer.DEFAULT_PORT; p < RemoteServer.DEFAULT_PORT + RemoteServer.PORTS_TRIED; p++) {
                    if (p != last) ports.add(p);
                }
                for (int i = 0; i < ports.size() && taken; i++) {
                    // A port just let go can take a moment to be free again: the first is tried twice.
                    for (int again = 0; again < (i == 0 ? 2 : 1) && taken; again++) {
                        try {
                            if (again > 0) Thread.sleep(300);
                            int bound = server.start(ports.get(i));
                            taken = false;
                            port = bound;
                            if (bound != last) prefs.edit().putInt(PORT, bound).apply();
                        } catch (IOException | SecurityException e) {
                            taken = true;
                        }
                    }
                }
            } catch (java.security.GeneralSecurityException | IOException | RuntimeException e) {
                broken = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (taken || broken) server = null;
            portTaken = taken;
            failed = broken;
            // Also after a bind that worked: a fallback port changes the address and QR the pane drew before it.
            main.post(this::changed);
        });
    }

    private void close() {
        control.execute(() -> {
            if (server != null) server.stop();
            server = null;
            portTaken = false;
            failed = false;
        });
        synchronized (seen) {
            seen.clear();
        }
    }

    /** The page phones open (res/raw/remote_page.html), or null if it can't be read. */
    private String page() {
        try (InputStream in = app.getResources().openRawResource(R.raw.remote_page)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | android.content.res.Resources.NotFoundException e) {
            return null;
        }
    }

    /** The phones' calls, on the server's threads. */
    private final class Calls implements RemoteServer.Handler {
        @Override
        public String pair(String name) {
            byte[] b = new byte[16];
            random.nextBytes(b);
            String token = hex(b);
            byte[] idb = new byte[8];
            random.nextBytes(idb);
            String id = hex(idb);
            String shown = name.isEmpty() ? app.getString(R.string.remote_phone_unnamed) : name;
            long now = System.currentTimeMillis();
            Paired added = new Paired(id, shown, sha256(token), now);
            update(phones -> {
                phones.add(added);
                return phones;
            });
            synchronized (seen) {
                seen.put(id, now);
            }
            main.post(() -> {
                Bridge b2 = bridge;
                if (b2 != null) b2.paired(shown);
                changed();
            });
            return token;
        }

        @Override
        public RemoteServer.Phone phone(String token) {
            String hash = sha256(token);
            long now = System.currentTimeMillis();
            for (Paired p : phones()) {
                if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                        p.hash.getBytes(StandardCharsets.US_ASCII))) continue;
                synchronized (seen) {
                    seen.put(p.id, now);
                }
                if (now - p.lastUsed > SAVE_USE_MS) used(p.id, now);
                main.post(PhoneRemote.this::tick);
                return new RemoteServer.Phone(p.id, p.name);
            }
            return null;
        }

        @Override
        public JSONObject state(RemoteServer.Phone p) {
            JSONObject now = onMainOr(PhoneRemote.this::state, null, STATE_WAIT_MS);
            if (now != null) {
                lastState = now;
                return now;
            }
            // The TV is busy drawing (Search on a slow TV): its last known state, marked so the page doesn't act on it.
            JSONObject last = lastState;
            JSONObject o;
            try {
                o = last == null ? new JSONObject() : new JSONObject(last.toString());
            } catch (JSONException e) {
                o = new JSONObject();
            }
            put(o, "stale", true);
            return o;
        }

        /** Keys, typing and Enter are answered at once and done on the main thread in turn: the phone never waits. */
        @Override
        public JSONObject key(RemoteServer.Phone p, String key) {
            main.post(() -> {
                Bridge b = bridge;
                if (b != null) b.press(key);
                else if ("home".equals(key)) bringHome();
            });
            return ok();
        }

        @Override
        public JSONObject text(RemoteServer.Phone p, String value) {
            String v = value.length() > 200 ? value.substring(0, 200) : value;
            // Only the newest text is typed: a slow TV skips the ones in between instead of drawing each.
            if (pendingText.getAndSet(v) == null) {
                main.post(() -> {
                    String latest = pendingText.getAndSet(null);
                    Screen.TextField f = field();
                    if (latest != null && f != null) f.set(latest);
                });
            }
            return ok();
        }

        @Override
        public JSONObject enter(RemoteServer.Phone p) {
            main.post(() -> {
                // Text still waiting goes first, so Enter searches for what was typed.
                String latest = pendingText.getAndSet(null);
                Screen.TextField f = field();
                if (f == null) return;
                if (latest != null) f.set(latest);
                f.enter();
            });
            return ok();
        }

        @Override
        public JSONObject myList(RemoteServer.Phone p, String id, boolean want) {
            return onMain(() -> {
                Bridge b = bridge;
                Title t = b == null ? null : b.titleById(id);
                if (t == null) return null;
                boolean mine = b.setMyList(t, want, p.name);
                JSONObject o = new JSONObject();
                put(o, "mine", mine);
                put(o, "name", t.name);
                return o;
            });
        }

        @Override
        public JSONObject search(RemoteServer.Phone p, String query) {
            String q = query.trim();
            if (q.length() > 60) q = q.substring(0, 60);
            JSONObject o = new JSONObject();
            JSONArray items = new JSONArray();
            put(o, "q", query);
            put(o, "items", items);
            if (q.isEmpty()) return o;
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<List<Title>> found = new AtomicReference<>();
            String asked = q;
            main.post(() -> {
                Bridge b = bridge;
                if (b == null) {
                    done.countDown();
                    return;
                }
                b.search(asked, ts -> {
                    found.set(ts);
                    done.countDown();
                });
            });
            try {
                // TMDB may take a moment; the page shows what it has.
                done.await(4, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<Title> ts = found.get();
            if (ts == null) return o;
            JSONObject[] rows = onMainOr(() -> {
                Bridge b = bridge;
                List<JSONObject> out = new ArrayList<>();
                for (Title t : ts) {
                    if (out.size() >= 8) break;
                    out.add(title(t, b != null && b.inMyList(t.id)));
                }
                return out.toArray(new JSONObject[0]);
            }, new JSONObject[0]);
            for (JSONObject r : rows) items.put(r);
            return o;
        }

        @Override
        public void forget(RemoteServer.Phone p) {
            main.post(() -> PhoneRemote.this.forget(p.id));
        }
    }

    /** Home from a phone while another app is in front: the launcher, as the remote's Home would. */
    private void bringHome() {
        android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME).setPackage(app.getPackageName())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            app.startActivity(home);
        } catch (android.content.ActivityNotFoundException | SecurityException ignored) {
            // Nothing to bring.
        }
    }

    private void used(String id, long now) {
        update(phones -> {
            List<Paired> out = new ArrayList<>();
            for (Paired p : phones) out.add(p.id.equals(id) ? new Paired(p.id, p.name, p.hash, now) : p);
            return out;
        });
    }

    /** The text box a phone may type into now, or null. Main thread. */
    private Screen.TextField field() {
        Bridge b = bridge;
        Screen.TextField f = b == null ? null : b.textField();
        return f != null && f.ready() ? f : null;
    }

    /** What the TV shows now, for the page. Main thread. */
    private JSONObject state() {
        Bridge b = bridge;
        Screen.TextField f = field();
        Title t = b == null ? null : b.title();
        JSONObject o = new JSONObject();
        put(o, "tv", tvName());
        put(o, "screen", b == null ? app.getString(R.string.remote_another_app) : b.screenName());
        put(o, "home", b != null);
        put(o, "typing", f != null);
        put(o, "text", f != null ? f.value() : "");
        if (t != null) put(o, "title", title(t, b.inMyList(t.id)));
        return o;
    }

    private static JSONObject title(Title t, boolean mine) {
        JSONObject o = new JSONObject();
        put(o, "id", t.id);
        put(o, "name", t.name);
        put(o, "year", t.year);
        put(o, "kind", t.kind == Kind.SHOW ? "show" : "movie");
        put(o, "mine", mine);
        return o;
    }

    private JSONObject onMain(Supplier<JSONObject> work) {
        return onMainOr(work, null);
    }

    private static JSONObject ok() {
        JSONObject o = new JSONObject();
        put(o, "ok", true);
        return o;
    }

    private <T> T onMainOr(Supplier<T> work, T or) {
        return onMainOr(work, or, MAIN_WAIT_MS);
    }

    /** Runs {@code work} on the main thread and waits for it, up to {@code waitMs}; {@code or} if it can't. */
    private <T> T onMainOr(Supplier<T> work, T or, long waitMs) {
        if (Looper.myLooper() == Looper.getMainLooper()) return work.get();
        AtomicReference<T> out = new AtomicReference<>(or);
        CountDownLatch done = new CountDownLatch(1);
        main.post(() -> {
            try {
                out.set(work.get());
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(waitMs, TimeUnit.MILLISECONDS)) return or;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out.get();
    }

    // ---- Listeners -----------------------------------------------------------------------------------------------

    /** The switch, the phones, a phone connecting or going quiet: for the pane, the section's value and the nav. */
    void addListener(Runnable l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Runnable l) {
        listeners.remove(l);
    }

    private void changed() {
        connectedBefore = connectedIds();
        for (Runnable l : new ArrayList<>(listeners)) l.run();
    }

    /** Tells listeners when the set of connected phones changes, and checks again while any is. Main thread. */
    private void tick() {
        main.removeCallbacks(tick);
        String now = connectedIds();
        if (!now.equals(connectedBefore)) changed();
        if (!now.isEmpty()) main.postDelayed(tick, 5000);
    }

    private String connectedIds() {
        StringBuilder b = new StringBuilder();
        for (Paired p : phones()) if (isConnected(p)) b.append(p.id).append(',');
        return b.toString();
    }

    // ---- Small things --------------------------------------------------------------------------------------------

    static String sha256(String s) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        return s.toString();
    }

    private static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
