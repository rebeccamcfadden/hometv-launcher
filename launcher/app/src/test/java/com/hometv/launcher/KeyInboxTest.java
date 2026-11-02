package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** API keys' page on the home network (F16): the page, the code, the keys' states, a key sent, and what is refused. */
public class KeyInboxTest {
    private static final String CODE = "K7QM-4PXT";
    private static final String PAGE = "<!doctype html><title>keys</title>";
    private final List<String> got = new ArrayList<>();
    private final Map<KeyRules.Id, KeyInbox.Entry> states = new EnumMap<>(KeyRules.Id.class);
    private long now = 1_000_000L;
    private boolean saves = true;
    private final KeyInbox inbox = new KeyInbox(CODE, new KeyInbox.Handler() {
        @Override
        public Map<KeyRules.Id, KeyInbox.Entry> states() {
            return states;
        }

        @Override
        public boolean submit(KeyRules.Id id, String key) {
            got.add(id.id + "|" + key);
            states.put(id, new KeyInbox.Entry(KeyRules.State.OK, KeyRules.last4(key)));
            return saves;
        }
    }, () -> now, PAGE);

    @After
    public void stop() {
        inbox.stop();
    }

    private static NoteInbox.Request request(String raw) throws IOException {
        return NoteInbox.Request.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static String post(String auth, String body) {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        return "POST /keys HTTP/1.1\r\nHost: tv\r\n" + (auth == null ? "" : "Authorization: Bearer " + auth + "\r\n")
                + "Content-Type: application/json\r\nContent-Length: " + b.length + "\r\n\r\n" + body;
    }

    private static String get(String path, String auth) {
        return "GET " + path + " HTTP/1.1\r\nHost: tv\r\n" + (auth == null ? "" : "Authorization: Bearer " + auth + "\r\n")
                + "\r\n";
    }

    private KeyInbox.Reply handle(String raw) throws IOException {
        return inbox.handle(request(raw));
    }

    @Test
    public void thePageNeedsNoCodeAndHoldsNothingSecret() throws IOException {
        KeyInbox.Reply r = handle(get("/keys", null));
        assertEquals(200, r.status);
        assertTrue(r.html);
        assertEquals(PAGE, r.body);
    }

    @Test
    public void aKeySentWithTheCodeIsCheckedAndEveryKeyComesBack() throws Exception {
        states.put(KeyRules.Id.WATCHMODE, new KeyInbox.Entry(KeyRules.State.BUILT, ""));
        KeyInbox.Reply r = handle(post(" k7qm4pxt ", "{\"id\":\"tmdb\",\"key\":\"  eyJabc.def.ghij  \"}"));
        assertEquals(200, r.status);
        assertEquals(List.of("tmdb|eyJabc.def.ghij"), got);
        JSONObject keys = new JSONObject(r.body).getJSONObject("keys");
        assertEquals("ok", keys.getJSONObject("tmdb").getString("state"));
        assertEquals("ghij", keys.getJSONObject("tmdb").getString("last4"));
        assertEquals("built", keys.getJSONObject("watchmode").getString("state"));
        assertEquals("unset", keys.getJSONObject("motn").getString("state"));
        // Never the key itself.
        assertFalse(r.body, r.body.contains("eyJabc"));
    }

    @Test
    public void theStatesNeedTheCodeToo() throws Exception {
        assertEquals(401, handle(get("/keys/state", null)).status);
        assertEquals(401, handle(get("/keys/state", "WRONG-CODE")).status);
        KeyInbox.Reply r = handle(get("/keys/state", CODE));
        assertEquals(200, r.status);
        assertTrue(new JSONObject(r.body).getBoolean("ok"));
    }

    @Test
    public void wrongCodesLockThePage() throws IOException {
        for (int i = 0; i < NoteInbox.MAX_WRONG; i++) {
            assertEquals(401, handle(post("WRONG-CODE", "{\"id\":\"tmdb\",\"key\":\"x\"}")).status);
        }
        // Locked: the right code waits, and so does the page itself.
        assertEquals(429, handle(post(CODE, "{\"id\":\"tmdb\",\"key\":\"x\"}")).status);
        assertEquals(429, handle(get("/keys", null)).status);
        now += NoteInbox.LOCKOUT_MS;
        assertEquals(200, handle(post(CODE, "{\"id\":\"tmdb\",\"key\":\"x\"}")).status);
        assertEquals(1, got.size());
    }

    @Test
    public void anUnknownKeyAnEmptyOneOrNoJsonIsRefused() throws IOException {
        assertEquals(400, handle(post(CODE, "{\"id\":\"netflix\",\"key\":\"x\"}")).status);
        assertEquals(400, handle(post(CODE, "{\"id\":\"tmdb\",\"key\":\"   \"}")).status);
        assertEquals(400, handle(post(CODE, "not json")).status);
        String text = "POST /keys HTTP/1.1\r\nAuthorization: Bearer " + CODE
                + "\r\nContent-Type: text/plain\r\nContent-Length: 3\r\n\r\nabc";
        assertEquals(400, handle(text).status);
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 30_000; i++) deep.append('[');
        assertEquals(400, handle(post(CODE, deep.toString())).status);
        char[] longKey = new char[KeyRules.MAX_KEY + 1];
        java.util.Arrays.fill(longKey, 'a');
        assertEquals(413, handle(post(CODE, "{\"id\":\"tmdb\",\"key\":\"" + new String(longKey) + "\"}")).status);
        assertTrue(got.isEmpty());
    }

    @Test
    public void onlyThePageItsStatesAndPostingAKey() throws IOException {
        assertEquals(404, handle(get("/notes", CODE)).status);
        assertEquals(404, handle(get("/setup", CODE)).status);
        assertEquals(404, handle("PUT /keys HTTP/1.1\r\nAuthorization: Bearer " + CODE + "\r\nContent-Length: 0\r\n\r\n").status);
        // Without a page (it couldn't be read), GET /keys is not found rather than empty.
        KeyInbox none = new KeyInbox(CODE, null, () -> now, null);
        assertEquals(404, none.handle(request(get("/keys", null))).status);
    }

    @Test
    public void aKeyTheTvCouldNotSaveIsNotAcknowledged() throws Exception {
        saves = false;
        KeyInbox.Reply r = handle(post(CODE, "{\"id\":\"tmdb\",\"key\":\"abc\"}"));
        assertEquals(500, r.status);
        assertFalse(new JSONObject(r.body).getBoolean("ok"));
    }

    @Test
    public void aStoppedInboxTakesNoMoreKeys() throws IOException {
        // New code, or the pane gone: a request read before, or arriving after, is refused.
        inbox.stop();
        assertEquals(503, handle(post(CODE, "{\"id\":\"tmdb\",\"key\":\"abc\"}")).status);
        assertEquals(503, handle(get("/keys/state", CODE)).status);
        assertTrue(got.isEmpty());
    }

    @Test
    public void servesOverLoopback() throws Exception {
        int port = inbox.start(0);
        assertTrue(inbox.running());
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
            OutputStream out = s.getOutputStream();
            out.write(post(CODE, "{\"id\":\"watchmode\",\"key\":\"abcd1234\"}").getBytes(StandardCharsets.UTF_8));
            out.flush();
            String answer = readAll(s.getInputStream());
            assertTrue(answer, answer.startsWith("HTTP/1.1 200 OK\r\n"));
            assertTrue(answer, answer.contains("Cache-Control: no-store\r\n"));
            JSONObject o = new JSONObject(answer.substring(answer.indexOf("\r\n\r\n") + 4));
            assertEquals("1234", o.getJSONObject("keys").getJSONObject("watchmode").getString("last4"));
        }
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
            s.getOutputStream().write(get("/keys", null).getBytes(StandardCharsets.UTF_8));
            String answer = readAll(s.getInputStream());
            assertTrue(answer, answer.contains("Content-Type: text/html; charset=utf-8\r\n"));
            assertTrue(answer, answer.contains("connect-src 'self'"));
            assertTrue(answer, answer.endsWith(PAGE));
        }
        assertEquals(List.of("watchmode|abcd1234"), got);
        inbox.stop();
        assertFalse(inbox.running());
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }
}
