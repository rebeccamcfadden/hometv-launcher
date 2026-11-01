package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class NoteInboxTest {
    private static final String CODE = "K7QM-4PXT";
    private final List<String> got = new ArrayList<>();
    private long now = 1_000_000L;
    private final NoteInbox inbox = new NoteInbox(CODE, (list, text) -> {
        got.add(list + "|" + text);
        return new NoteInbox.Reply(200, "Sent: 2 lines, 1 new", 2, 1);
    }, () -> now);

    @After
    public void stop() {
        inbox.stop();
    }

    private static NoteInbox.Request request(String raw) throws IOException {
        return NoteInbox.Request.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static String post(String auth, String type, String body) {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        return "POST /notes HTTP/1.1\r\nHost: tv\r\n" + (auth == null ? "" : "Authorization: Bearer " + auth + "\r\n")
                + "Content-Type: " + type + "\r\nContent-Length: " + b.length + "\r\n\r\n" + body;
    }

    private NoteInbox.Reply handle(String raw) throws IOException {
        return inbox.handle(request(raw));
    }

    @Test
    public void jsonNoteGoesToItsList() throws IOException {
        NoteInbox.Reply r = handle(post(CODE, "application/json", "{\"list\":\"Rebecca\",\"text\":\"☐ Dune\\n☑ Moon\"}"));
        assertEquals(200, r.status);
        assertEquals(List.of("Rebecca|☐ Dune\n☑ Moon"), got);
    }

    @Test
    public void plainTextTakesTheListFromTheQuery() throws IOException {
        String body = "Dune\nArrival";
        String raw = "POST /notes?list=Our%20picks HTTP/1.1\r\nAuthorization: Bearer k7qm 4pxt\r\n"
                + "Content-Type: text/plain\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;
        assertEquals(200, handle(raw).status);
        assertEquals(List.of("Our picks|Dune\nArrival"), got);
    }

    @Test
    public void wrongCodeIsRefusedAndRepeatedTriesLockTheInbox() throws IOException {
        for (int i = 0; i < NoteInbox.MAX_WRONG; i++) {
            assertEquals(401, handle(post("WRONG-CODE", "application/json", "{\"list\":\"A\",\"text\":\"Dune\"}")).status);
        }
        // Locked: even the right code waits.
        assertEquals(429, handle(post(CODE, "application/json", "{\"list\":\"A\",\"text\":\"Dune\"}")).status);
        now += NoteInbox.LOCKOUT_MS;
        assertEquals(200, handle(post(CODE, "application/json", "{\"list\":\"A\",\"text\":\"Dune\"}")).status);
        assertEquals(1, got.size());
    }

    @Test
    public void missingCodeListOrTextIsABadRequest() throws IOException {
        assertEquals(401, handle(post(null, "application/json", "{\"list\":\"A\",\"text\":\"Dune\"}")).status);
        assertEquals(400, handle(post(CODE, "application/json", "{\"text\":\"Dune\"}")).status);
        assertEquals(400, handle(post(CODE, "application/json", "{\"list\":\"A\",\"text\":\"  \"}")).status);
        assertEquals(400, handle(post(CODE, "application/json", "not json")).status);
        assertTrue(got.isEmpty());
    }

    @Test
    public void onlyPostToNotes() throws IOException {
        assertEquals(404, handle("GET /notes HTTP/1.1\r\nAuthorization: Bearer " + CODE + "\r\n\r\n").status);
        assertEquals(404, handle("POST /other HTTP/1.1\r\nAuthorization: Bearer " + CODE + "\r\nContent-Length: 0\r\n\r\n").status);
    }

    @Test
    public void bodiesAndHeadersAreCapped() {
        try {
            request("POST /notes HTTP/1.1\r\nContent-Length: " + (NoteInbox.MAX_BODY + 1) + "\r\n\r\n");
            fail("too large");
        } catch (NoteInbox.TooLarge expected) {
            // ok
        } catch (IOException e) {
            fail(e.toString());
        }
        StringBuilder big = new StringBuilder("POST /notes HTTP/1.1\r\n");
        while (big.length() <= NoteInbox.MAX_HEADERS) big.append("X-Pad: aaaaaaaaaaaaaaaaaaaaaaaa\r\n");
        try {
            request(big + "\r\n");
            fail("too large");
        } catch (NoteInbox.TooLarge expected) {
            // ok
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    @Test
    public void deeplyNestedJsonIsRefusedWithoutParsing() throws IOException {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 30_000; i++) b.append('[');
        assertEquals(400, handle(post(CODE, "application/json", b.toString())).status);
        assertEquals(2, NoteInbox.depth("{\"a\":[1],\"b\":\"[[[[[[[[[[\"}"));
        assertEquals("extra closers don't go below zero", 1, NoteInbox.depth("]]]]{}"));
        assertTrue(got.isEmpty());
    }

    @Test
    public void aRequestTricklingInPastTheDeadlineIsCutOff() {
        InputStream slow = new InputStream() {
            @Override
            public int read() {
                return 'x';
            }
        };
        try {
            NoteInbox.Request.read(new NoteInbox.Deadline(slow, System.nanoTime() - 1));
            fail("past the deadline");
        } catch (java.net.SocketTimeoutException expected) {
            // ok
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    @Test
    public void setupPageNeedsNoCodeAndHoldsNoSecret() throws IOException {
        NoteInbox withPage = new NoteInbox(CODE, (list, text) -> {
            got.add(list);
            return new NoteInbox.Reply(200, "ok", 0, 0);
        }, () -> now, "<html>setup</html>");
        NoteInbox.Reply r = withPage.handle(request("GET /setup HTTP/1.1\r\nHost: tv\r\n\r\n"));
        assertEquals(200, r.status);
        assertEquals("<html>setup</html>", r.html);
        assertTrue(got.isEmpty());
        // Only GET /setup: posting a note there, or any other GET, is still refused.
        assertEquals(404, withPage.handle(request("GET /notes HTTP/1.1\r\n\r\n")).status);
        assertEquals(404, withPage.handle(request(post(CODE, "application/json", "{\"list\":\"A\",\"text\":\"Dune\"}")
                .replace("POST /notes", "POST /setup"))).status);
        // Without a page (as in the other tests) there is no setup route at all.
        assertEquals(404, handle("GET /setup HTTP/1.1\r\n\r\n").status);
    }

    @Test
    public void setupPageIsServedAsHtmlOverLoopback() throws Exception {
        NoteInbox withPage = new NoteInbox(CODE, (list, text) -> new NoteInbox.Reply(200, "ok", 0, 0), () -> now,
                "<html>setup</html>");
        int port = withPage.start(0);
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
            s.getOutputStream().write("GET /setup HTTP/1.1\r\nHost: tv\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            String answer = readAll(s.getInputStream());
            assertTrue(answer, answer.startsWith("HTTP/1.1 200 OK\r\n"));
            assertTrue(answer, answer.contains("Content-Type: text/html; charset=utf-8\r\n"));
            assertTrue(answer, answer.contains("Content-Security-Policy: default-src 'none'"));
            assertTrue(answer, answer.endsWith("<html>setup</html>"));
        } finally {
            withPage.stop();
        }
    }

    @Test
    public void malformedRequestsAreRefused() {
        for (String raw : new String[] {"garbage\r\n\r\n", "POST /notes HTTP/1.1\r\nContent-Length: x\r\n\r\n",
                "POST /notes HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n", "POST /notes HTTP/1.1\r\nContent-Length: 10\r\n\r\nshort"}) {
            try {
                request(raw);
                fail(raw);
            } catch (IOException expected) {
                // ok
            }
        }
    }

    @Test
    public void onlyTheHomeNetworkCounts() throws IOException {
        assertTrue(NoteInbox.local(InetAddress.getByName("127.0.0.1")));
        assertTrue(NoteInbox.local(InetAddress.getByName("192.168.1.50")));
        assertTrue(NoteInbox.local(InetAddress.getByName("10.0.0.7")));
        assertTrue(NoteInbox.local(InetAddress.getByName("172.20.1.1")));
        assertTrue(NoteInbox.local(InetAddress.getByName("169.254.3.4")));
        assertTrue(NoteInbox.local(InetAddress.getByName("fd12:3456::1")));
        assertTrue(NoteInbox.local(InetAddress.getByName("fe80::1")));
        assertFalse(NoteInbox.local(InetAddress.getByName("8.8.8.8")));
        assertFalse(NoteInbox.local(InetAddress.getByName("2001:4860:4860::8888")));
        assertFalse(NoteInbox.local(null));
    }

    @Test
    public void codesAreEightUnambiguousCharacters() {
        SecureRandom r = new SecureRandom();
        for (int i = 0; i < 200; i++) {
            String c = NoteInbox.newCode(r);
            assertTrue(c, c.matches("[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}"));
        }
        assertEquals("K7QM4PXT", NoteInbox.normalize(" k7qm-4pxt "));
    }

    @Test
    public void servesOverLoopback() throws Exception {
        int port = inbox.start(0);
        assertTrue(inbox.running());
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
            OutputStream out = s.getOutputStream();
            out.write(post(CODE, "application/json; charset=utf-8", "{\"list\":\"Ours\",\"text\":\"Dune\"}")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            String answer = readAll(s.getInputStream());
            assertTrue(answer, answer.startsWith("HTTP/1.1 200 OK\r\n"));
            JSONObject o = new JSONObject(answer.substring(answer.indexOf("\r\n\r\n") + 4));
            assertTrue(o.getBoolean("ok"));
            assertEquals("Sent: 2 lines, 1 new", o.getString("message"));
            assertEquals(1, o.getInt("new"));
        }
        assertEquals(List.of("Ours|Dune"), got);
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
