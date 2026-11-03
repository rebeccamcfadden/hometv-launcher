package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** The phone remote's server over real TLS on loopback, with a certificate written by RemoteCert. */
public class RemoteServerTest {
    private static final String CODE = "K7QM-4PXT";
    private static final String TOKEN = "good-token";

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final List<String> keys = Collections.synchronizedList(new ArrayList<>());
    private final List<String> texts = Collections.synchronizedList(new ArrayList<>());
    private RemoteServer server;
    private SSLContext client;
    private int port;

    private final RemoteServer.Handler handler = new RemoteServer.Handler() {
        @Override public String pair(String name) {
            return TOKEN;
        }

        @Override public RemoteServer.Phone phone(String token) {
            return TOKEN.equals(token) ? new RemoteServer.Phone("p1", "Rebecca’s phone") : null;
        }

        @Override public JSONObject state(RemoteServer.Phone p) {
            return obj("screen", "Home");
        }

        @Override public JSONObject key(RemoteServer.Phone p, String key) {
            keys.add(key);
            return obj("screen", "Home");
        }

        @Override public JSONObject text(RemoteServer.Phone p, String value) {
            texts.add(value);
            return obj("text", value);
        }

        @Override public JSONObject enter(RemoteServer.Phone p) {
            return obj("entered", true);
        }

        @Override public JSONObject myList(RemoteServer.Phone p, String id, boolean mine) {
            return "arrival".equals(id) ? obj("mine", mine) : null;
        }

        @Override public JSONObject search(RemoteServer.Phone p, String query) {
            return obj("q", query);
        }

        @Override public void forget(RemoteServer.Phone p) {
        }
    };

    private static JSONObject obj(String k, Object v) {
        try {
            return new JSONObject().put(k, v);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Before public void start() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = g.generateKeyPair();
        byte[] der = RemoteCert.build(pair.getPublic(), data -> {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(pair.getPrivate());
            s.update(data);
            return s.sign();
        }, "HomeTV Launcher", Collections.singletonList(InetAddress.getLoopbackAddress()), null, BigInteger.TEN,
                new Date(), new Date(System.currentTimeMillis() + 86_400_000L));
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(der));
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        char[] pw = "x".toCharArray();
        ks.setKeyEntry("k", pair.getPrivate(), pw, new java.security.cert.Certificate[] {cert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pw);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        server = new RemoteServer(ctx.getServerSocketFactory(), () -> CODE, handler, now::get, "<!doctype html><p>remote");
        port = server.start(0);
        client = SSLContext.getInstance("TLS");
        client.init(null, new TrustManager[] {new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String a) {}

            @Override public void checkServerTrusted(X509Certificate[] c, String a) {}

            @Override public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
    }

    @After public void stop() {
        server.stop();
    }

    // ---- A tiny HTTPS client ----

    private static final class Answer {
        final int status;
        final Map<String, String> headers;
        final String body;

        Answer(int status, Map<String, String> headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        JSONObject json() throws Exception {
            return new JSONObject(body);
        }
    }

    private String host() {
        return "127.0.0.1:" + port;
    }

    private SSLSocket open() throws IOException {
        SSLSocket s = (SSLSocket) client.getSocketFactory().createSocket(InetAddress.getLoopbackAddress(), port);
        s.setSoTimeout(5000);
        return s;
    }

    private Answer call(String method, String path, String token, String body) throws Exception {
        return call(method, path, token, body, host(), null);
    }

    private Answer call(String method, String path, String token, String body, String host, String origin)
            throws Exception {
        try (SSLSocket s = open()) {
            send(s.getOutputStream(), method, path, token, body, host, origin, false);
            return read(s.getInputStream());
        }
    }

    private static void send(OutputStream out, String method, String path, String token, String body, String host,
                             String origin, boolean keepAlive) throws IOException {
        byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        StringBuilder h = new StringBuilder(method + " " + path + " HTTP/1.1\r\n");
        if (host != null) h.append("Host: ").append(host).append("\r\n");
        if (origin != null) h.append("Origin: ").append(origin).append("\r\n");
        if (token != null) h.append("Authorization: Bearer ").append(token).append("\r\n");
        if (body != null) h.append("Content-Type: application/json\r\n");
        h.append("Content-Length: ").append(b.length).append("\r\n");
        h.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(b);
        out.flush();
    }

    private static Answer read(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int c = in.read();
            if (c < 0) throw new IOException("closed");
            head.write(c);
            state = (c == '\r' && (state == 0 || state == 2)) || (c == '\n' && (state == 1 || state == 3)) ? state + 1
                    : c == '\r' ? 1 : 0;
        }
        String[] lines = new String(head.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        int status = Integer.parseInt(lines[0].split(" ")[1]);
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1).trim());
        }
        byte[] body = new byte[Integer.parseInt(headers.get("content-length"))];
        int off = 0;
        while (off < body.length) {
            int n = in.read(body, off, body.length - off);
            if (n < 0) throw new IOException("short");
            off += n;
        }
        return new Answer(status, headers, new String(body, StandardCharsets.UTF_8));
    }

    private String pairBody(String code) {
        return "{\"code\":\"" + code + "\",\"name\":\"Rebecca’s phone\"}";
    }

    // ---- Pairing ----

    @Test public void theRightCodeGivesThePhoneItsToken() throws Exception {
        Answer a = call("POST", "/api/pair", null, pairBody("k7qm 4pxt"));
        assertEquals(200, a.status);
        assertEquals(TOKEN, a.json().getString("token"));
        assertEquals("Rebecca’s phone", a.json().getString("name"));
    }

    @Test public void fiveWrongCodesLockPairingForFiveMinutes() throws Exception {
        for (int left = 4; left >= 1; left--) {
            Answer a = call("POST", "/api/pair", null, pairBody("AAAA-BBBB"));
            assertEquals(401, a.status);
            assertEquals(left, a.json().getInt("left"));
        }
        Answer locked = call("POST", "/api/pair", null, pairBody("AAAA-BBBB"));
        assertEquals(429, locked.status);
        assertEquals(RemoteServer.LOCKOUT_MS, locked.json().getLong("until"));
        // Even the right code waits, while a paired phone keeps working.
        assertEquals(429, call("POST", "/api/pair", null, pairBody(CODE)).status);
        assertEquals(200, call("GET", "/api/state", TOKEN, null).status);
        now.addAndGet(RemoteServer.LOCKOUT_MS);
        assertEquals(200, call("POST", "/api/pair", null, pairBody(CODE)).status);
    }

    @Test public void aTokenTheTvDoesntKnowNeverLocksPairing() throws Exception {
        for (int i = 0; i < RemoteServer.MAX_WRONG * 3; i++) {
            Answer a = call("POST", "/api/key", "guess-" + i, "{\"key\":\"up\"}");
            assertEquals(401, a.status);
            assertEquals("unpaired", a.json().getString("error"));
        }
        assertEquals(200, call("POST", "/api/pair", null, pairBody(CODE)).status);
        assertTrue(keys.isEmpty());
    }

    private RemoteServer.Reply pairFrom(String address, String code) throws Exception {
        RemoteServer.Request q = new RemoteServer.Request("POST", "/api/pair", null, host(), null, null, pairBody(code),
                false);
        return server.handle(q, InetAddress.getLoopbackAddress(), port, InetAddress.getByName(address));
    }

    @Test public void wrongCodesPausePairingOnlyForTheAddressThatSentThem() throws Exception {
        for (int i = 0; i < RemoteServer.MAX_WRONG; i++) pairFrom("192.168.1.66", "AAAA-BBBB");
        assertEquals(429, pairFrom("192.168.1.66", CODE).status);
        assertEquals(200, pairFrom("192.168.1.20", CODE).status);
    }

    @Test public void manyWrongCodesFromManyAddressesPausePairingForEveryone() throws Exception {
        int sent = 0;
        for (int a = 1; sent < RemoteServer.MAX_WRONG_ALL; a++) {
            for (int i = 0; i < RemoteServer.MAX_WRONG - 1 && sent < RemoteServer.MAX_WRONG_ALL; i++, sent++) {
                pairFrom("10.0.0." + a, "AAAA-BBBB");
            }
        }
        assertEquals(429, pairFrom("192.168.1.20", CODE).status);
        now.addAndGet(RemoteServer.LOCKOUT_MS);
        assertEquals(200, pairFrom("192.168.1.20", CODE).status);
    }

    @Test public void noTokenAsksToPairWithoutCounting() throws Exception {
        for (int i = 0; i < RemoteServer.MAX_WRONG + 2; i++) assertEquals(401, call("GET", "/api/state", null, null).status);
        assertEquals(200, call("POST", "/api/pair", null, pairBody(CODE)).status);
    }

    // ---- Calls ----

    @Test public void keysAndTypingReachTheTv() throws Exception {
        assertEquals(200, call("POST", "/api/key", TOKEN, "{\"key\":\"left\"}").status);
        assertEquals(200, call("POST", "/api/text", TOKEN, "{\"value\":\"the bear\"}").status);
        assertEquals(400, call("POST", "/api/key", TOKEN, "{\"key\":\"power\"}").status);
        assertEquals(Collections.singletonList("left"), keys);
        assertEquals(Collections.singletonList("the bear"), texts);
        assertEquals(true, call("POST", "/api/enter", TOKEN, "{}").json().getBoolean("entered"));
    }

    @Test public void myListAndSearchGoToTheTv() throws Exception {
        assertTrue(call("POST", "/api/mylist", TOKEN, "{\"id\":\"arrival\",\"mine\":true}").json().getBoolean("mine"));
        // What the phone wants, never a toggle: a stale button can't take off what it shows as added.
        assertFalse(call("POST", "/api/mylist", TOKEN, "{\"id\":\"arrival\",\"mine\":false}").json().getBoolean("mine"));
        assertEquals(400, call("POST", "/api/mylist", TOKEN, "{\"id\":\"arrival\"}").status);
        assertEquals(400, call("POST", "/api/mylist", TOKEN, "{\"id\":\"arrival\",\"mine\":\"yes\"}").status);
        assertEquals(404, call("POST", "/api/mylist", TOKEN, "{\"id\":\"nope\",\"mine\":true}").status);
        assertEquals("the bear", call("GET", "/api/search?q=the%20bear", TOKEN, null).json().getString("q"));
    }

    // ---- Who may call ----

    @Test public void onlyTheTvsOwnAddressIsAHost() throws Exception {
        assertEquals(421, call("GET", "/api/state", TOKEN, null, "evil.example:" + port, null).status);
        assertEquals(421, call("GET", "/api/state", TOKEN, null, null, null).status);
        assertEquals(200, call("GET", "/api/state", TOKEN, null, host(), null).status);
    }

    @Test public void anotherOriginIsRefused() throws Exception {
        assertEquals(403, call("POST", "/api/key", TOKEN, "{\"key\":\"up\"}", host(), "https://evil.example").status);
        assertEquals(403, call("POST", "/api/key", TOKEN, "{\"key\":\"up\"}", host(), "http://" + host()).status);
        assertEquals(200, call("POST", "/api/key", TOKEN, "{\"key\":\"up\"}", host(), "https://" + host()).status);
    }

    @Test public void thePageHasItsPolicyAndNoCors() throws Exception {
        Answer a = call("GET", "/remote", null, null);
        assertEquals(200, a.status);
        assertTrue(a.headers.get("content-type").startsWith("text/html"));
        assertTrue(a.headers.get("content-security-policy").contains("default-src 'none'"));
        assertTrue(a.headers.get("content-security-policy").contains("connect-src 'self'"));
        assertNull(a.headers.get("access-control-allow-origin"));
        assertNull(a.headers.get("strict-transport-security"));
        assertEquals("no-store", a.headers.get("cache-control"));
    }

    @Test public void aBodyTooLargeIsRefused() throws Exception {
        StringBuilder big = new StringBuilder("{\"value\":\"");
        while (big.length() < RemoteServer.MAX_BODY + 10) big.append('x');
        big.append("\"}");
        assertEquals(413, call("POST", "/api/text", TOKEN, big.toString()).status);
        assertTrue(texts.isEmpty());
    }

    @Test public void onePhoneMayCallAtMostThirtyTimesASecond() throws Exception {
        for (int i = 0; i < RemoteServer.MAX_RATE; i++) assertEquals(200, call("GET", "/api/state", TOKEN, null).status);
        assertEquals(429, call("GET", "/api/state", TOKEN, null).status);
        now.addAndGet(1000);
        assertEquals(200, call("GET", "/api/state", TOKEN, null).status);
    }

    @Test public void aConnectionIsKeptAliveBetweenCalls() throws Exception {
        try (SSLSocket s = open()) {
            for (int i = 0; i < 3; i++) {
                send(s.getOutputStream(), "POST", "/api/key", TOKEN, "{\"key\":\"down\"}", host(), null, true);
                Answer a = read(s.getInputStream());
                assertEquals(200, a.status);
                assertEquals("keep-alive", a.headers.get("connection"));
            }
        }
        assertEquals(3, keys.size());
    }

    @Test public void stopLetsThePortGo() throws Exception {
        assertTrue(server.running());
        server.stop();
        assertFalse(server.running());
    }

    @Test public void stopClosesAConnectionKeptAlive() throws Exception {
        try (SSLSocket s = open()) {
            send(s.getOutputStream(), "POST", "/api/key", TOKEN, "{\"key\":\"down\"}", host(), null, true);
            assertEquals(200, read(s.getInputStream()).status);
            server.stop();
            // The next key never reaches the TV: the connection is gone.
            try {
                send(s.getOutputStream(), "POST", "/api/key", TOKEN, "{\"key\":\"up\"}", host(), null, true);
                read(s.getInputStream());
                org.junit.Assert.fail("answered after stop");
            } catch (IOException expected) {
                // Closed.
            }
        }
        assertEquals(Collections.singletonList("down"), keys);
    }

    @Test public void quietConnectionsCantKeepPhonesOutForLong() throws Exception {
        List<java.net.Socket> quiet = new ArrayList<>();
        try {
            // Connections that never start their handshake take every thread...
            for (int i = 0; i < RemoteServer.THREADS; i++) quiet.add(new java.net.Socket(InetAddress.getLoopbackAddress(), port));
            Thread.sleep(300);
            // ...so one more is closed at once rather than left waiting behind them...
            long t0 = System.nanoTime();
            try {
                call("GET", "/api/state", TOKEN, null);
                org.junit.Assert.fail("served past the limit");
            } catch (IOException expected) {
                assertTrue((System.nanoTime() - t0) / 1_000_000 < 2000);
            }
            // ...and the handshake deadline frees them.
            Thread.sleep(RemoteServer.HANDSHAKE_MS + 700);
            assertEquals(200, call("GET", "/api/state", TOKEN, null).status);
        } finally {
            for (java.net.Socket s : quiet) s.close();
        }
    }

    @Test public void theHostOfAnIpv6AddressHasBrackets() throws Exception {
        assertEquals("[fe80:0:0:0:0:0:0:1]:8443", RemoteServer.hostOf(InetAddress.getByName("fe80::1%1"), 8443));
        assertEquals("192.168.1.50:8443", RemoteServer.hostOf(InetAddress.getByName("192.168.1.50"), 8443));
    }
}
