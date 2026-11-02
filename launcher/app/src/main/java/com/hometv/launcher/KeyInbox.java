package com.hometv.launcher;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * API keys' page on the home network (F16; design_preview/keys.html): the QR in Launcher settings › API keys opens
 * {@code http://<tv>:8766/keys#<code>} on a phone, and a laptop opens the same address and types the code. The page
 * sends each key to the TV, which keeps it, checks it with its service and answers how that went. Open only while an
 * API keys pane shows ({@link ApiKeys}); plain HTTP on the home network, like Phone notes' inbox.
 *
 * <p>One request at a time on a single low-priority thread, from a private, loopback or link-local address only, read
 * as {@link NoteInbox} reads (at most {@link NoteInbox#MAX_BODY} bytes, within {@link NoteInbox#REQUEST_DEADLINE_MS}):
 * <ul>
 *   <li>{@code GET /keys}: the page, which holds nothing secret and reads the code from the address's #fragment;</li>
 *   <li>{@code GET /keys/state}: each key's state and last four characters;</li>
 *   <li>{@code POST /keys}: {@code {"id": "tmdb", "key": "…"}}; answers once the key is checked.</li>
 * </ul>
 * The last two need the code as {@code Authorization: Bearer}. After {@link NoteInbox#MAX_WRONG} wrong codes in a row
 * the page refuses everything for {@link NoteInbox#LOCKOUT_MS}. Nothing is logged: the body is a key. Plain Java over
 * java.net, unit-tested over loopback (KeyInboxTest).
 */
final class KeyInbox {
    static final int DEFAULT_PORT = 8766;

    /** One key as the page shows it. */
    static final class Entry {
        final KeyRules.State state;
        final String last4;

        Entry(KeyRules.State state, String last4) {
            this.state = state;
            this.last4 = last4 == null ? "" : last4;
        }
    }

    /** The TV's keys; called on the inbox thread. */
    interface Handler {
        /** Every key, as it is now. */
        Map<KeyRules.Id, Entry> states();

        /**
         * A key arrived: kept, checked with its service (blocking), and kept with what the check said. False when it
         * couldn't be written to the TV's storage.
         */
        boolean submit(KeyRules.Id id, String key);
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

        static Reply error(int status, String message) {
            return new Reply(status, json(false, message, null), false);
        }

        static Reply keys(Map<KeyRules.Id, Entry> states) {
            return new Reply(200, json(true, "", states), false);
        }

        static Reply page(String html) {
            return new Reply(200, html, true);
        }

        private static String json(boolean ok, String message, Map<KeyRules.Id, Entry> states) {
            JSONObject o = new JSONObject();
            try {
                o.put("ok", ok).put("message", message);
                if (states != null) {
                    JSONObject keys = new JSONObject();
                    for (KeyRules.Id id : KeyRules.Id.values()) {
                        Entry e = states.get(id);
                        if (e == null) e = new Entry(KeyRules.State.UNSET, "");
                        keys.put(id.id, new JSONObject().put("state", e.state.wire).put("last4", e.last4));
                    }
                    o.put("keys", keys);
                }
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
            return o.toString();
        }
    }

    private final String code;
    private final Handler handler;
    private final NoteInbox.Clock clock;
    private final String page;
    private int wrong;
    private long lockedUntil;
    private volatile ServerSocket socket;
    /** The connection being served now, closed by {@link #stop} so a request half sent can't finish after it. */
    private volatile Socket current;
    /** Stopped: nothing more is accepted, not even a request already read (New code, or the pane gone). */
    private volatile boolean stopped;
    private Thread thread;

    /** @param page what GET /keys answers (res/raw/keys_setup.html), or null for none */
    KeyInbox(String code, Handler handler, NoteInbox.Clock clock, String page) {
        this.code = code;
        this.handler = handler;
        this.clock = clock;
        this.page = page;
    }

    // ---- Serving -------------------------------------------------------------------------------------------------

    /** Listens on the port (all interfaces; callers are filtered by address). Returns the port bound. */
    synchronized int start(int port) throws IOException {
        if (socket != null) return socket.getLocalPort();
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(port), 4);
        socket = s;
        thread = new Thread(this::loop, "key-inbox");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
        return s.getLocalPort();
    }

    /** Stops listening, and waits briefly for the thread to let the port go (New code opens it again at once). */
    void stop() {
        stopped = true;
        Socket c = current;
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // Closing anyway.
            }
        }
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
            t = thread;
            thread = null;
        }
        if (t == null || t == Thread.currentThread()) return;
        try {
            t.join(NoteInbox.STOP_WAIT_MS);
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
            try (Socket c = s.accept()) {
                current = c;
                c.setSoTimeout(NoteInbox.READ_TIMEOUT_MS);
                if (!stopped) serve(c);
            } catch (SocketException e) {
                if (socket == null) return;
            } catch (IOException | RuntimeException | StackOverflowError e) {
                // One bad request never stops the page; nothing is logged (the body is a key).
            } finally {
                current = null;
            }
        }
    }

    private void serve(Socket c) throws IOException {
        Reply r;
        if (!NoteInbox.local(c.getInetAddress())) {
            r = Reply.error(403, "Only phones and laptops on this home network can send keys.");
        } else {
            try {
                NoteInbox.Request q = NoteInbox.Request.read(new NoteInbox.Deadline(c.getInputStream(),
                        System.nanoTime() + NoteInbox.REQUEST_DEADLINE_MS * 1_000_000L));
                r = handle(q);
            } catch (NoteInbox.TooLarge e) {
                r = Reply.error(413, "That is too long to be a key.");
            } catch (IOException e) {
                r = Reply.error(400, "The TV couldn't read that request.");
            }
        }
        write(c.getOutputStream(), r);
    }

    /** Everything after reading: the method, path, code and body. Package-private for the tests. */
    Reply handle(NoteInbox.Request q) {
        if (stopped) return Reply.error(503, "The TV stopped listening. Open API keys on the TV, then scan its QR again.");
        synchronized (this) {
            long now = clock.now();
            if (now < lockedUntil) return Reply.error(429, "Too many wrong codes. Try again in a few minutes.");
            if (page != null && "GET".equals(q.method) && "/keys".equals(q.path)) return Reply.page(page);
            boolean state = "GET".equals(q.method) && "/keys/state".equals(q.path);
            boolean post = "POST".equals(q.method) && "/keys".equals(q.path);
            if (!state && !post) return Reply.error(404, "Open /keys on the TV's address.");
            if (!sameCode(q.bearer, code)) {
                if (++wrong >= NoteInbox.MAX_WRONG) {
                    wrong = 0;
                    lockedUntil = now + NoteInbox.LOCKOUT_MS;
                }
                return Reply.error(401, "The TV didn't accept that code. Check it on the TV, or scan its QR again.");
            }
            wrong = 0;
            if (state) return Reply.keys(handler.states());
        }
        // A key: read it, then check it outside the lock (the check is a network call).
        if (!q.json() || NoteInbox.depth(q.body) > NoteInbox.MAX_DEPTH) {
            return Reply.error(400, "The TV couldn't read that key.");
        }
        KeyRules.Id id;
        String key;
        try {
            JSONObject o = new JSONObject(q.body);
            id = KeyRules.Id.of(o.optString("id", ""));
            key = o.optString("key", "").trim();
        } catch (JSONException | StackOverflowError e) {
            return Reply.error(400, "The TV couldn't read that key.");
        }
        if (id == null) return Reply.error(400, "The TV doesn't know that key.");
        if (key.isEmpty()) return Reply.error(400, "Paste the key first.");
        if (key.length() > KeyRules.MAX_KEY) return Reply.error(413, "That is too long to be a key.");
        // Stopped while the key was read (New code, or the pane gone): it was sent with a code no longer shown.
        if (stopped) return Reply.error(503, "The TV stopped listening. Open API keys on the TV, then scan its QR again.");
        if (!handler.submit(id, key)) return Reply.error(500, "The TV couldn't save that key. Try again.");
        return Reply.keys(handler.states());
    }

    private static boolean sameCode(String given, String code) {
        byte[] a = NoteInbox.normalize(given).getBytes(StandardCharsets.UTF_8);
        byte[] b = NoteInbox.normalize(code).getBytes(StandardCharsets.UTF_8);
        return b.length > 0 && MessageDigest.isEqual(a, b);
    }

    private static void write(OutputStream out, Reply r) throws IOException {
        byte[] body = r.body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + r.status + " " + reason(r.status) + "\r\n"
                + "Content-Type: " + (r.html ? "text/html" : "application/json") + "; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                // The page runs only its own inline script and talks only to the TV that served it.
                + (r.html ? "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; "
                        + "style-src 'unsafe-inline'; connect-src 'self'\r\nReferrer-Policy: no-referrer\r\n" : "")
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200:
                return "OK";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 413:
                return "Payload Too Large";
            case 429:
                return "Too Many Requests";
            case 500:
                return "Internal Server Error";
            case 503:
                return "Service Unavailable";
            default:
                return "Error";
        }
    }
}
