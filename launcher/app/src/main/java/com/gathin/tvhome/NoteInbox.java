package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Phone notes' inbox on the home network (BACKLOG.md, Phone notes): a "Send to TV" Shortcut on a phone posts a note's
 * text to {@code http://<tv>:<port>/notes} with the pairing code shown in Launcher settings, and the TV turns its
 * lines into titles on that person's TMDB list. Off unless note_inbox is on; plain HTTP on the home network, so the
 * code only lets a caller add titles to the household's lists.
 *
 * <p>One request at a time on a single low-priority thread: {@code POST /notes} (and {@code GET /setup}, the static page
 * the Phone notes QR opens, which holds nothing secret) only, from a private, loopback or
 * link-local address only, at most {@link #MAX_BODY} bytes, all of it within {@link #REQUEST_DEADLINE_MS} (JSON at most
 * {@link #MAX_DEPTH} deep). The body is JSON,
 * {@code {"list": "Rebecca", "text": "…"}}, or the note's text itself with the list in {@code ?list=}. After
 * {@link #MAX_WRONG} wrong codes in a row the inbox refuses everything for {@link #LOCKOUT_MS}. The answer is JSON
 * with a {@code message} for the Shortcut to show. Plain Java over java.net, unit-tested over loopback
 * (NoteInboxTest); nothing is logged.
 */
final class NoteInbox {
    static final int DEFAULT_PORT = 8765;
    static final int MAX_BODY = 64 * 1024, MAX_HEADERS = 8 * 1024, READ_TIMEOUT_MS = 5000;
    static final int MAX_WRONG = 5;
    static final long LOCKOUT_MS = 5 * 60_000L;
    /** How long {@link #stop} waits for the listening thread to let the port go. */
    static final long STOP_WAIT_MS = 2000;

    /** Takes a note for a list, off the main thread; returns what to tell the phone. */
    interface Handler {
        Reply accept(String list, String text);
    }

    static final class Reply {
        final int status;
        final String message;
        final int lines, fresh;
        /** A page to send instead of the JSON answer (GET /setup), or null. */
        final String html;

        Reply(int status, String message, int lines, int fresh) {
            this(status, message, lines, fresh, null);
        }

        private Reply(int status, String message, int lines, int fresh, String html) {
            this.status = status;
            this.message = message;
            this.lines = lines;
            this.fresh = fresh;
            this.html = html;
        }

        static Reply error(int status, String message) {
            return new Reply(status, message, 0, 0);
        }

        static Reply page(String html) {
            return new Reply(200, "", 0, 0, html);
        }

        String json() {
            JSONObject o = new JSONObject();
            try {
                o.put("ok", status == 200).put("message", message).put("lines", lines).put("new", fresh);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
            return o.toString();
        }
    }

    interface Clock {
        long now();
    }

    private final String code;
    private final Handler handler;
    private final Clock clock;
    /** The setup page the Phone notes QR opens (GET /setup), or null for none. */
    private final String setupPage;
    private int wrong;
    private long lockedUntil;
    private volatile ServerSocket socket;
    private Thread thread;

    NoteInbox(String code, Handler handler, Clock clock) {
        this(code, handler, clock, null);
    }

    /**
     * @param setupPage what GET /setup answers: a static page that reads the code from the address's #fragment, which
     *     the phone never sends, so the page holds nothing secret and needs no code to fetch
     */
    NoteInbox(String code, Handler handler, Clock clock, String setupPage) {
        this.code = code;
        this.handler = handler;
        this.clock = clock;
        this.setupPage = setupPage;
    }

    // ---- The pairing code ----------------------------------------------------------------------------------------

    /** Letters and digits that can't be mistaken for each other when typed off a TV: no 0/O, 1/I/L. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /** A new code, "K7QM-4PXT": 8 characters from 31, about 40 bits; with the lockout, plenty for a home network. */
    static String newCode(SecureRandom random) {
        StringBuilder b = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) b.append('-');
            b.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return b.toString();
    }

    /** The code as typed: case, spaces and dashes don't count. */
    static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    // ---- Serving -------------------------------------------------------------------------------------------------

    /** Listens on the port (all interfaces; callers are filtered by address). Returns the port bound. */
    synchronized int start(int port) throws IOException {
        if (socket != null) return socket.getLocalPort();
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(port), 4);
        socket = s;
        thread = new Thread(this::loop, "note-inbox");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
        return s.getLocalPort();
    }

    /**
     * Stops listening, and waits (briefly) for the listening thread to end: on Android the port is let go only once
     * that thread has left accept, and New code opens the inbox again at once.
     */
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
            t = thread;
            thread = null;
        }
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
            try (Socket c = s.accept()) {
                c.setSoTimeout(READ_TIMEOUT_MS);
                serve(c);
            } catch (SocketException e) {
                if (socket == null) return;
            } catch (IOException | RuntimeException | StackOverflowError e) {
                // One bad request never stops the inbox; nothing is logged (the body is someone's note).
            }
        }
    }

    private void serve(Socket c) throws IOException {
        Reply r;
        if (!local(c.getInetAddress())) {
            r = Reply.error(403, "Only phones on this home network can send notes.");
        } else {
            Request q;
            try {
                q = Request.read(new Deadline(c.getInputStream(), System.nanoTime() + REQUEST_DEADLINE_MS * 1_000_000L));
            } catch (TooLarge e) {
                r = Reply.error(413, "That note is too long for the TV.");
                write(c.getOutputStream(), r);
                return;
            } catch (IOException e) {
                r = Reply.error(400, "The TV couldn't read that request.");
                write(c.getOutputStream(), r);
                return;
            }
            r = handle(q);
        }
        write(c.getOutputStream(), r);
    }

    /** Everything after reading: the method, path, code and body. Package-private for the tests. */
    synchronized Reply handle(Request q) {
        long now = clock.now();
        if (now < lockedUntil) return Reply.error(429, "Too many wrong codes. Try again in a few minutes.");
        if (setupPage != null && "GET".equals(q.method) && "/setup".equals(q.path)) return Reply.page(setupPage);
        if (!"POST".equals(q.method) || !"/notes".equals(q.path)) {
            return Reply.error(404, "Send notes to /notes with POST.");
        }
        if (!sameCode(q.bearer, code)) {
            if (++wrong >= MAX_WRONG) {
                wrong = 0;
                lockedUntil = now + LOCKOUT_MS;
            }
            return Reply.error(401, "Wrong pairing code. Scan the QR in the TV's Launcher settings → Phone notes and tap Pair this phone.");
        }
        wrong = 0;
        String list = q.query("list");
        String text = q.body;
        if (q.json()) {
            if (depth(q.body) > MAX_DEPTH) return Reply.error(400, "The TV couldn't read that note.");
            try {
                JSONObject o = new JSONObject(q.body);
                list = o.optString("list", list == null ? "" : list);
                text = o.optString("text", "");
            } catch (JSONException | StackOverflowError e) {
                return Reply.error(400, "The TV couldn't read that note.");
            }
        }
        list = list == null ? "" : list.trim();
        if (list.isEmpty()) return Reply.error(400, "Say which list the note is for.");
        if (text == null || text.trim().isEmpty()) return Reply.error(400, "The note was empty.");
        return handler.accept(list, text);
    }

    /** A note is {"list", "text"}: anything nested deeper is refused before org.json, which parses recursively. */
    static final int MAX_DEPTH = 8;

    /** How deep the JSON's brackets go, outside strings. */
    static int depth(String json) {
        int depth = 0, max = 0;
        boolean inString = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                max = Math.max(max, ++depth);
                // Deep enough to refuse: no need to read the rest.
                if (max > MAX_DEPTH) return max;
            } else if (c == '}' || c == ']') {
                if (depth > 0) depth--;
            }
        }
        return max;
    }

    private static boolean sameCode(String given, String code) {
        byte[] a = normalize(given).getBytes(StandardCharsets.UTF_8);
        byte[] b = normalize(code).getBytes(StandardCharsets.UTF_8);
        return b.length > 0 && MessageDigest.isEqual(a, b);
    }

    /** Loopback, private (10/8, 172.16/12, 192.168/16, fc00::/7) or link-local: the home network, not the internet. */
    static boolean local(InetAddress a) {
        if (a == null) return false;
        if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()) return true;
        if (a instanceof Inet6Address) {
            byte[] b = a.getAddress();
            if ((b[0] & 0xfe) == 0xfc) return true;
            // An IPv4 caller seen through an IPv6 socket.
            boolean mapped = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) mapped = false;
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return local(Inet4Address.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return false;
    }

    private static void write(OutputStream out, Reply r) throws IOException {
        byte[] body = (r.html != null ? r.html : r.json()).getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + r.status + " " + reason(r.status) + "\r\n"
                + "Content-Type: " + (r.html != null ? "text/html" : "application/json") + "; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                // The setup page runs only its own inline script and fetches nothing.
                + (r.html != null ? "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; "
                        + "style-src 'unsafe-inline'\r\nReferrer-Policy: no-referrer\r\n" : "")
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
            default:
                return "Error";
        }
    }

    // ---- Reading a request ---------------------------------------------------------------------------------------

    static final class TooLarge extends IOException {
        TooLarge() {
            super("too large");
        }
    }

    /** A whole request must arrive within this, however slowly its bytes trickle in. */
    static final long REQUEST_DEADLINE_MS = 10_000L;

    /** Refuses to read past a deadline (System.nanoTime), so a slow sender can't hold the one-at-a-time inbox. */
    static final class Deadline extends java.io.FilterInputStream {
        private final long deadline;

        Deadline(InputStream in, long deadlineNanos) {
            super(in);
            this.deadline = deadlineNanos;
        }

        private void check() throws IOException {
            if (System.nanoTime() - deadline > 0) throw new java.net.SocketTimeoutException("request too slow");
        }

        @Override
        public int read() throws IOException {
            check();
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            check();
            return super.read(b, off, len);
        }
    }

    /** One HTTP/1.1 request: the method, path and query, the bearer code, the content type and the body. */
    static final class Request {
        final String method, path, rawQuery, bearer, contentType, body;

        Request(String method, String path, String rawQuery, String bearer, String contentType, String body) {
            this.method = method;
            this.path = path;
            this.rawQuery = rawQuery;
            this.bearer = bearer;
            this.contentType = contentType;
            this.body = body;
        }

        boolean json() {
            return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
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

        /** Reads one request: headers up to {@link #MAX_HEADERS}, then a Content-Length body up to {@link #MAX_BODY}. */
        static Request read(InputStream in) throws IOException {
            String head = readHead(in);
            String[] lines = head.split("\r\n");
            String[] start = lines[0].split(" ");
            if (start.length != 3 || !start[2].startsWith("HTTP/1.")) throw new IOException("not HTTP");
            String target = start[1];
            int q = target.indexOf('?');
            String path = q < 0 ? target : target.substring(0, q);
            String query = q < 0 ? null : target.substring(q + 1);
            String bearer = null, type = null;
            long length = 0;
            boolean chunked = false;
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0) continue;
                String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = lines[i].substring(colon + 1).trim();
                if (name.equals("authorization") && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    bearer = value.substring(7).trim();
                } else if (name.equals("content-type")) {
                    type = value;
                } else if (name.equals("content-length")) {
                    try {
                        length = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("bad length");
                    }
                } else if (name.equals("transfer-encoding") && value.toLowerCase(Locale.ROOT).contains("chunked")) {
                    chunked = true;
                }
            }
            if (chunked) throw new IOException("chunked bodies are not read");
            if (length < 0) throw new IOException("bad length");
            if (length > MAX_BODY) throw new TooLarge();
            byte[] body = new byte[(int) length];
            int off = 0;
            while (off < body.length) {
                int n = in.read(body, off, body.length - off);
                if (n < 0) throw new IOException("short body");
                off += n;
            }
            return new Request(start[0], path, query, bearer, type, new String(body, StandardCharsets.UTF_8));
        }

        private static String readHead(InputStream in) throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            int state = 0;
            while (state < 4) {
                int c = in.read();
                if (c < 0) throw new IOException("closed");
                b.write(c);
                if (b.size() > MAX_HEADERS) throw new TooLarge();
                state = (c == '\r' && (state == 0 || state == 2)) || (c == '\n' && (state == 1 || state == 3))
                        ? state + 1 : c == '\r' ? 1 : 0;
            }
            return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
