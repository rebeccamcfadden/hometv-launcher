package com.gathin.tvhome;

import java.io.IOException;
import java.util.Map;

/**
 * One HTTPS GET, the only network the price sources and the Wikidata lookup need. Kept this small so the parsing and
 * request building stay pure and tested with fixtures, and Stage 2 binds it to the catalogue's client and disk cache
 * (BACKLOG.md, Streaming availability and prices). Blocking: call it from a background thread.
 */
interface HttpGet {
    /**
     * The body of a 200 answer, or null for a 404 (the service doesn't know the title). Headers carry API keys, so
     * an implementation must never log them or use them in a cache key. Anything else throws.
     */
    String get(String url, Map<String, String> headers) throws IOException;
}
