package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The price and availability fixtures in src/test/resources/prices, and a fake {@link HttpGet} that answers from them.
 * Files named *_handmade.json were written by hand from each API's documented schema (no key was available); the
 * others are real answers recorded on 2026-10-30 and trimmed, with no token or key in them.
 */
final class Fixtures {
    private Fixtures() {}

    static String read(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/prices/" + name)) {
            if (in == null) throw new IllegalArgumentException("no fixture " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Answers URLs that start with a registered prefix; anything else is a 404. Records every request. */
    static final class FakeHttp implements HttpGet {
        final Map<String, String> answers = new HashMap<>();
        final List<String> urls = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();

        FakeHttp answer(String urlPrefix, String fixture) {
            answers.put(urlPrefix, fixture == null ? null : read(fixture));
            return this;
        }

        @Override
        public String get(String url, Map<String, String> h) {
            urls.add(url);
            headers.add(h);
            String best = null;
            for (String prefix : answers.keySet()) {
                if (url.startsWith(prefix) && (best == null || prefix.length() > best.length())) best = prefix;
            }
            return best == null ? null : answers.get(best);
        }
    }
}
