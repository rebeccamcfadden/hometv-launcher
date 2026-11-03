package com.hometv.launcher;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLSocket;

/**
 * The phone remote's server (F27; docs/phone-remote.md): {@code GET /remote} is the page a phone opens from the QR
 * (res/raw/remote_page.html; it holds nothing secret: the pairing code stays in the link's #fragment), and
 * {@code /api/*} is what the page calls. HTTPS on the home network ({@link RemoteTls}): TLS only, no plain-HTTP answer.
 *
 * <p>Who may call: private, loopback and link-local addresses only ({@link NoteInbox#local}); a {@code Host} that is
 * the address the call came in on, so a web page elsewhere that points a name at this TV (DNS rebinding) is refused;
 * and an {@code Origin}, when a browser sends one, of this same server, so another page on the Wi-Fi can't drive it.
 * No CORS headers are ever sent, and every API call needs the custom {@code Authorization} header, which a page on
 * another origin can't send without a preflight that never succeeds.
 *
 * <p>Pairing: {@code POST /api/pair} with the code shown in Launcher settings and the phone's name gives that phone
 * its own token. {@link #MAX_WRONG} wrong codes in a row from one address pause pairing there for
 * {@link #LOCKOUT_MS}, and {@link #MAX_WRONG_ALL} from all addresses pause it for everyone; paired phones keep working.
 * A token the TV doesn't know isn't counted (it is a forgotten phone; 128 bits can't be guessed), so nothing on the
 * Wi-Fi can keep the household from pairing. At most {@link #MAX_RATE} calls a second per address. Small JSON bodies
 * ({@link #MAX_BODY}), deadlines on the handshake and each request, at most {@link #THREADS} connections at once with
 * keep-alive. Stopping closes every connection. Plain Java over java.net, unit-tested over loopback TLS
 * (RemoteServerTest); nothing is logged: what a phone types may be a password.
 */
final class RemoteServer {
    /** Beside Phone notes' 8765 and API keys' 8766. Not 8443: Chromecast built-in listens there on most Android TVs. */
    static final int DEFAULT_PORT = 8767;
    /** Ports tried from DEFAULT_PORT up when it is taken; the one used is kept (PhoneRemote). */
    static final int PORTS_TRIED = 4;
    static final int MAX_BODY = 4 * 1024, MAX_HEADERS = 8 * 1024;
    /** How long a kept-alive connection may sit quiet before it is closed; the page polls every 2 s while open. */
    static final int IDLE_MS = 5_000;
    /** The TLS handshake must be over within this, however slowly its bytes trickle in. */
    static final long HANDSHAKE_MS = 5_000L;
    /** A whole request must arrive within this once it has begun. */
    static final long REQUEST_DEADLINE_MS = 10_000L;
    /** Wrong codes from one address before pairing pauses for it. */
    static final int MAX_WRONG = 5;
    /** Wrong codes from all addresses together, within a lockout's time, before pairing pauses for everyone. */
    static final int MAX_WRONG_ALL = 25;
    static final long LOCKOUT_MS = 5 * 60_000L;
    /** Calls a second one address may make: a held arrow repeats at about 8. */
    static final int MAX_RATE = 30;
    /** Connections served at once; one more is closed at once rather than queued. */
    static final int THREADS = 8;
    static final long STOP_WAIT_MS = 2000;
    /** Addresses remembered for wrong codes and rates; past this, the ones with nothing running are dropped. */
    private static final int MAX_CALLERS = 256;
    /** A paired phone, as the handler knows it. */
    static final class Phone {
        final String id, name;

        Phone(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** The TV's side, called on the server's threads; each answer is JSON for the page. */
    interface Handler {
        /** The code was right: a new phone named {@code name}; returns its token, or null if it can't be kept. */
        String pair(String name);

        /** The phone a token belongs to, or null. */
        Phone phone(String token);

        /** What the TV shows now, for this phone; its last known state with {@code "stale": true} while the TV is busy. */
        JSONObject state(Phone p);

        /** A remote key ("up", "down", "left", "right", "ok", "back", "home"), done in turn; answers at once. */
        JSONObject key(Phone p, String key);

        /** The TV's text box set to {@code value} (sent as the phone types); answers at once. */
        JSONObject text(Phone p, String value);

        /** Enter in the TV's text box, after any text still waiting; answers at once. */
        JSONObject enter(Phone p);

        /** F39: My list for a title, on or off; null for a title the TV doesn't know. */
        JSONObject myList(Phone p, String id);

        /** F39: titles for a query, from the TV's catalogue. */
        JSONObject search(Phone p, String query);

        /** The phone unpairs itself. */
        void forget(Phone p);
    }

    interface Clock {
        long now();
    }

    private final ServerSocketFactory factory;
    private final Supplier<String> code;
    private final Handler handler;
    private final Clock clock;
    private final String page;
    /** Per address: {wrong codes in a row, paused until}; and for all of them: wrong codes since, and paused until. */
    private final Map<String, long[]> tries = new HashMap<>();
    private int wrongAll;
    private long wrongAllSince, lockedAllUntil;
    /** Per address: the second its calls are counted in, and how many so far. */
    private final Map<String, long[]> rate = new HashMap<>();
    /** The connections open now, closed when the server stops. */
    private final Set<Socket> live = Collections.synchronizedSet(new HashSet<>());
    private volatile ServerSocket socket;
    private Thread thread;
    private ExecutorService pool;
    private ScheduledExecutorService watch;
    /**
     * @param factory TLS on the TV ({@link RemoteTls}); the tests hand in their own
     * @param code the pairing code now (it changes with New code and Forget all without a restart)
     * @param page what {@code GET /remote} answers
     */
    RemoteServer(ServerSocketFactory factory, Supplier<String> code, Handler handler, Clock clock, String page) {
        this.factory = factory;
        this.code = code;
        this.handler = handler;
        this.clock = clock;
        this.page = page;
    }

    // ---- Serving -------------------------------------------------------------------------------------------------

    /** Listens on the port (all interfaces; callers are filtered by address). Returns the port bound. */
    synchronized int start(int port) throws IOException {
        if (socket != null) return socket.getLocalPort();
        ServerSocket s = factory.createServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(port), 8);
        socket = s;
        // No queue: a connection past THREADS is closed at once, so a quiet one can't keep the household waiting.
        pool = new ThreadPoolExecutor(THREADS, THREADS, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "phone-remote");
            t.setPriority(Thread.MIN_PRIORITY + 1);
            t.setDaemon(true);
            return t;
        });
        watch = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "phone-remote-watch");
            t.setDaemon(true);
            return t;
        });
        thread = new Thread(this::loop, "phone-remote-accept");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
        return s.getLocalPort();
    }

    /** Stops listening and closes every open connection, waiting briefly for the port to be let go. */
    void stop() {
        Thread t;
        synchronized (this) {
            ServerSocket s = socket;
            socket = null;
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // Closing anyway.
                }
            }
            if (pool != null) pool.shutdownNow();
            pool = null;
            if (watch != null) watch.shutdownNow();
            watch = null;
            t = thread;
            thread = null;
        }
        Socket[] open;
        synchronized (live) {
            open = live.toArray(new Socket[0]);
            live.clear();
        }
        for (Socket c : open) close(c);
        if (t == null || t == Thread.currentThread()) return;
        try {
            t.join(STOP_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    boolean running() {
        return socket != null;
    }

    private void loop() {
        ServerSocket s;
        while ((s = socket) != null) {
            Socket c;
            try {
                c = s.accept();
            } catch (SocketException e) {
                if (socket == null) return;
                continue;
            } catch (IOException | RuntimeException e) {
                continue;
            }
            live.add(c);
            ExecutorService p = pool;
            try {
                if (p == null) throw new RejectedExecutionException();
                p.execute(() -> connection(c));
            } catch (RejectedExecutionException e) {
                live.remove(c);
                close(c);
            }
        }
    }

    /** Closes {@code c} after {@code ms} unless cancelled: a handshake or request that trickles in can't hold a thread. */
    private ScheduledFuture<?> deadline(Socket c, long ms) {
        ScheduledExecutorService w = watch;
        if (w == null) return null;
        try {
            return w.schedule(() -> close(c), ms, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            return null;
        }
    }

    private static void cancel(ScheduledFuture<?> f) {
        if (f != null) f.cancel(false);
    }

    /** One connection: requests one after another while the phone keeps it alive. */
    private void connection(Socket c) {
        try {
            if (!NoteInbox.local(c.getInetAddress())) return;
            c.setSoTimeout(IDLE_MS);
            c.setTcpNoDelay(true);
            if (c instanceof SSLSocket) {
                ScheduledFuture<?> hs = deadline(c, HANDSHAKE_MS);
                try {
                    ((SSLSocket) c).startHandshake();
                } finally {
                    cancel(hs);
                }
            }
            InputStream in = c.getInputStream();
            OutputStream out = c.getOutputStream();
            while (running()) {
                Request q;
                ScheduledFuture<?> rq = null;
                try {
                    int first = in.read();
                    if (first < 0) return;
                    rq = deadline(c, REQUEST_DEADLINE_MS);
                    q = Request.read(first, in, System.nanoTime() + REQUEST_DEADLINE_MS * 1_000_000L);
                } catch (SocketTimeoutException e) {
                    return;
                } catch (NoteInbox.TooLarge e) {
                    write(out, Reply.error(413, "too large"), false);
                    return;
                } catch (IOException e) {
                    write(out, Reply.error(400, "bad request"), false);
                    return;
                } finally {
                    cancel(rq);
                }
                Reply r = handle(q, c.getLocalAddress(), c.getLocalPort(), c.getInetAddress());
                boolean again = q.keepAlive && r.status < 400 && running();
                write(out, r, again);
                if (!again) return;
            }
        } catch (IOException | RuntimeException | StackOverflowError e) {
            // One bad connection never stops the server; nothing is logged.
        } finally {
            live.remove(c);
            close(c);
        }
    }

    private static void close(Socket c) {
        try {
            c.close();
        } catch (IOException ignored) {
            // Gone anyway.
        }
    }
    // ---- Answering -----------------------------------------------------------------------------------------------

    /** Everything after reading: who may call, then the route. Package-private for the tests. */
    Reply handle(Request q, InetAddress local, int port, InetAddress from) {
        if (!running()) return Reply.error(503, "off");
        String host = hostOf(local, port);
        if (q.host == null || !q.host.equalsIgnoreCase(host)) return Reply.error(421, "wrong host");
        if (q.origin != null && !q.origin.equalsIgnoreCase("https://" + host)) return Reply.error(403, "wrong origin");
        if ("GET".equals(q.method) && ("/remote".equals(q.path) || "/".equals(q.path))) {
            return page == null ? Reply.error(404, "no page") : Reply.page(page);
        }
        if (!q.path.startsWith("/api/")) return Reply.error(404, "not found");
        String caller = from == null ? "" : from.getHostAddress();
        long now = clock.now();
        // Every call counts, before its token is looked at: guessing tokens is as slow as anything else.
        if (!allowed(caller, now)) return Reply.error(429, "slow down");
        String route = q.method + " " + q.path;
        if ("POST /api/pair".equals(route)) return pair(q, caller, now);
        Phone p = q.bearer == null ? null : handler.phone(q.bearer);
        if (p == null) {
            // A token the TV doesn't know isn't a wrong code: it is a phone forgotten (or the TV's tokens made again),
            // and guessing one of 128 bits is hopeless. Counting it would let anything on the Wi-Fi lock pairing.
            JSONObject o = new JSONObject();
            put(o, "error", "unpaired");
            put(o, "locked", lockedLeft(caller, now));
            return new Reply(401, o);
        }
        JSONObject body;
        try {
            body = q.body.isEmpty() ? new JSONObject() : new JSONObject(q.body);
        } catch (JSONException | StackOverflowError e) {
            return Reply.error(400, "bad json");
        }
        switch (route) {
            case "GET /api/state":
                return Reply.ok(handler.state(p));
            case "POST /api/key": {
                String k = body.optString("key", "");
                if (!k.matches("up|down|left|right|ok|back|home")) return Reply.error(400, "unknown key");
                return Reply.ok(handler.key(p, k));
            }
            case "POST /api/text":
                return Reply.ok(handler.text(p, body.optString("value", "")));
            case "POST /api/enter":
                return Reply.ok(handler.enter(p));
            case "POST /api/mylist": {
                JSONObject r = handler.myList(p, body.optString("id", ""));
                return r == null ? Reply.error(404, "unknown title") : Reply.ok(r);
            }
            case "GET /api/search":
                return Reply.ok(handler.search(p, q.query("q") == null ? "" : q.query("q")));
            case "POST /api/forget":
                handler.forget(p);
                return Reply.ok(new JSONObject());
            default:
                return Reply.error(404, "not found");
        }
    }

    private Reply pair(Request q, String caller, long now) {
        if (lockedLeft(caller, now) > 0) return locked(caller, now);
        JSONObject body;
        try {
            body = new JSONObject(q.body);
        } catch (JSONException | StackOverflowError e) {
            return Reply.error(400, "bad json");
        }
        if (!sameCode(body.optString("code", ""), code.get())) {
            int left = wrongTry(caller, now);
            if (left <= 0) return locked(caller, now);
            JSONObject o = new JSONObject();
            put(o, "error", "badcode");
            put(o, "left", left);
            return new Reply(401, o);
        }
        synchronized (tries) {
            tries.remove(caller);
        }
        String name = body.optString("name", "").trim();
        if (name.length() > 40) name = name.substring(0, 40);
        String token = handler.pair(name);
        if (token == null) return Reply.error(500, "can't pair");
        JSONObject o = new JSONObject();
        put(o, "token", token);
        put(o, "name", name);
        return Reply.ok(o);
    }

    /**
     * A wrong code from {@code caller}: {@link #MAX_WRONG} in a row pause pairing for that address, and
     * {@link #MAX_WRONG_ALL} from all addresses within {@link #LOCKOUT_MS} for everyone. Returns the tries left.
     */
    private int wrongTry(String caller, long now) {
        synchronized (tries) {
            if (now - wrongAllSince > LOCKOUT_MS) {
                wrongAll = 0;
                wrongAllSince = now;
            }
            if (++wrongAll >= MAX_WRONG_ALL) {
                wrongAll = 0;
                lockedAllUntil = now + LOCKOUT_MS;
            }
            forgetOld(tries, now);
            long[] t = tries.get(caller);
            if (t == null) tries.put(caller, t = new long[2]);
            if (++t[0] >= MAX_WRONG) {
                t[0] = 0;
                t[1] = now + LOCKOUT_MS;
            }
            return now < Math.max(t[1], lockedAllUntil) ? 0 : (int) (MAX_WRONG - t[0]);
        }
    }

    private long lockedLeft(String caller, long now) {
        synchronized (tries) {
            long[] t = tries.get(caller);
            long until = Math.max(t == null ? 0 : t[1], lockedAllUntil);
            return Math.max(0, until - now);
        }
    }

    private Reply locked(String caller, long now) {
        JSONObject o = new JSONObject();
        put(o, "error", "locked");
        put(o, "until", lockedLeft(caller, now));
        return new Reply(429, o);
    }

    /** At most {@link #MAX_RATE} calls in a second per address. */
    private boolean allowed(String caller, long now) {
        synchronized (rate) {
            long second = now / 1000;
            if (rate.size() > MAX_CALLERS) rate.values().removeIf(r -> r[0] != second);
            long[] r = rate.get(caller);
            if (r == null || r[0] != second) {
                rate.put(caller, new long[] {second, 1});
                return true;
            }
            return ++r[1] <= MAX_RATE;
        }
    }

    /** Drops addresses with no pause running and no wrong code, once there are many. Holds {@code tries}. */
    private static void forgetOld(Map<String, long[]> tries, long now) {
        if (tries.size() > MAX_CALLERS) tries.values().removeIf(t -> t[1] < now);
    }
    private static boolean sameCode(String given, String code) {
        byte[] a = NoteInbox.normalize(given).getBytes(StandardCharsets.UTF_8);
        byte[] b = NoteInbox.normalize(code).getBytes(StandardCharsets.UTF_8);
        return b.length > 0 && MessageDigest.isEqual(a, b);
    }

    /** "192.168.1.50:8767", or "[fe80::1]:8767": what a browser that came to this address sends as Host. */
    static String hostOf(InetAddress local, int port) {
        String a = local.getHostAddress();
        int zone = a.indexOf('%');
        if (zone >= 0) a = a.substring(0, zone);
        return (local instanceof Inet6Address ? "[" + a + "]" : a) + ":" + port;
    }

    private static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class Reply {
        final int status;
        final String body;
        final boolean html;

        private Reply(int status, String body, boolean html) {
            this.status = status;
            this.body = body;
            this.html = html;
        }

        Reply(int status, JSONObject o) {
            this(status, o.toString(), false);
        }

        static Reply ok(JSONObject o) {
            return new Reply(200, o == null ? new JSONObject() : o);
        }

        static Reply error(int status, String error) {
            JSONObject o = new JSONObject();
            put(o, "error", error);
            return new Reply(status, o);
        }

        static Reply page(String html) {
            return new Reply(200, html, true);
        }
    }

    private static void write(OutputStream out, Reply r, boolean keepAlive) throws IOException {
        byte[] body = r.body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + r.status + " " + reason(r.status) + "\r\n"
                + "Content-Type: " + (r.html ? "text/html" : "application/json") + "; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                // The page runs only its own inline script and style, and talks only to this server. No HSTS: with a
                // certificate the TV made itself it would take away the browser's "continue" for good.
                + (r.html ? "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; "
                        + "style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; "
                        + "frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n" : "")
                + "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 413: return "Payload Too Large";
            case 421: return "Misdirected Request";
            case 429: return "Too Many Requests";
            default: return "Error";
        }
    }

    // ---- Reading a request ---------------------------------------------------------------------------------------

    /** One HTTP/1.1 request: the method, path and query, the headers this server reads, and the body. */
    static final class Request {
        final String method, path, rawQuery, host, origin, bearer, body;
        final boolean keepAlive;

        Request(String method, String path, String rawQuery, String host, String origin, String bearer, String body,
                boolean keepAlive) {
            this.method = method;
            this.path = path;
            this.rawQuery = rawQuery;
            this.host = host;
            this.origin = origin;
            this.bearer = bearer;
            this.body = body;
            this.keepAlive = keepAlive;
        }

        /** A query parameter, URL-decoded, or null. */
        String query(String name) {
            if (rawQuery == null) return null;
            for (String part : rawQuery.split("&")) {
                int eq = part.indexOf('=');
                String k = eq < 0 ? part : part.substring(0, eq);
                if (!k.equals(name)) continue;
                try {
                    return java.net.URLDecoder.decode(eq < 0 ? "" : part.substring(eq + 1), "UTF-8");
                } catch (java.io.UnsupportedEncodingException | IllegalArgumentException e) {
                    return null;
                }
            }
            return null;
        }

        /**
         * Reads one request whose first byte, {@code first}, has come: headers up to {@link #MAX_HEADERS}, then a
         * Content-Length body up to {@link #MAX_BODY}, all before {@code deadlineNanos}.
         */
        static Request read(int first, InputStream raw, long deadlineNanos) throws IOException {
            InputStream in = new NoteInbox.Deadline(raw, deadlineNanos);
            String head = readHead(first, in);
            String[] lines = head.split("\r\n");
            String[] start = lines[0].split(" ");
            if (start.length != 3 || !start[2].startsWith("HTTP/1.")) throw new IOException("not HTTP");
            String target = start[1];
            int q = target.indexOf('?');
            String path = q < 0 ? target : target.substring(0, q);
            String query = q < 0 ? null : target.substring(q + 1);
            Map<String, String> h = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0) continue;
                String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT);
                // A repeated Host, Origin or Authorization is refused rather than guessed at.
                if (h.containsKey(name) && (name.equals("host") || name.equals("origin") || name.equals("authorization"))) {
                    throw new IOException("repeated " + name);
                }
                h.put(name, lines[i].substring(colon + 1).trim());
            }
            String te = h.get("transfer-encoding");
            if (te != null) throw new IOException("only Content-Length bodies are read");
            long length = 0;
            String cl = h.get("content-length");
            if (cl != null) {
                try {
                    length = Long.parseLong(cl);
                } catch (NumberFormatException e) {
                    throw new IOException("bad length");
                }
            }
            if (length < 0) throw new IOException("bad length");
            if (length > MAX_BODY) throw new NoteInbox.TooLarge();
            byte[] body = new byte[(int) length];
            int off = 0;
            while (off < body.length) {
                int n = in.read(body, off, body.length - off);
                if (n < 0) throw new IOException("short body");
                off += n;
            }
            String auth = h.get("authorization");
            String bearer = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7).trim() : null;
            String conn = h.get("connection");
            boolean keepAlive = start[2].equals("HTTP/1.1")
                    ? conn == null || !conn.equalsIgnoreCase("close")
                    : conn != null && conn.equalsIgnoreCase("keep-alive");
            return new Request(start[0], path, query, h.get("host"), h.get("origin"), bearer,
                    new String(body, StandardCharsets.UTF_8), keepAlive);
        }

        private static String readHead(int first, InputStream in) throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            int state = 0;
            int c = first;
            while (true) {
                b.write(c);
                if (b.size() > MAX_HEADERS) throw new NoteInbox.TooLarge();
                state = (c == '\r' && (state == 0 || state == 2)) || (c == '\n' && (state == 1 || state == 3))
                        ? state + 1 : c == '\r' ? 1 : 0;
                if (state == 4) break;
                c = in.read();
                if (c < 0) throw new IOException("closed");
            }
            return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
