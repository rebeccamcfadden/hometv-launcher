package com.gathin.tvhome;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The {@link HttpGet} the price sources and the Wikidata lookup use on the TV, one per source: answers are kept on
 * disk ({@link DiskCache}, keyed by URL only, so an API key in a header never reaches a file name) for as long as
 * {@link PriceCachePolicy} says, and a stale copy is used when the network fails, so what was seen once still shows
 * offline. Before a keyed source goes to the network it checks its {@link PriceCachePolicy.Quota}, synced from every
 * reply's quota headers (and, for Watchmode, from its free status endpoint once a day, so a reinstall starts from the
 * server's count); a spent budget gives no stale copy, so stores fall back to Rent or Buy without an amount. It keeps
 * each source's minimum gap between requests. A 404 is kept too, as "not known". Blocking: the price thread only.
 * Pure Java (java.net), unit-tested with a fake network (CachedHttpTest).
 */
final class CachedHttp implements HttpGet {

    /** What the network answered, with the reply's headers (names in lower case). */
    static final class Response {
        final int code;
        final byte[] body;
        final Map<String, String> headers;

        Response(int code, byte[] body) {
            this(code, body, Collections.emptyMap());
        }

        Response(int code, byte[] body, Map<String, String> headers) {
            this.code = code;
            this.body = body;
            this.headers = headers;
        }
    }

    interface Fetcher {
        Response fetch(String url, Map<String, String> headers) throws IOException;
    }

    /** Where the quotas live between runs: the app's preferences on the TV. */
    interface LedgerStore {
        String load(String sourceId);

        void save(String sourceId, String encoded);
    }

    /** No room left in the source's budget today, or this period: the caller tries another source or none. */
    static final class OverBudget extends IOException {
        OverBudget(String sourceId) {
            super(sourceId + ": over budget");
        }
    }

    /** The provider answered with a code but its body was lost (a read timeout, a dropped link): the request counts. */
    static final class Unread extends IOException {
        Unread(IOException cause) {
            super("reply unread", cause);
        }
    }

    /** Watchmode's account status: the quota and the count used, for no credits. */
    static final String WATCHMODE_STATUS = "https://api.watchmode.com/v1/status/";

    private static final int CONNECT_MS = 8000, READ_MS = 12000;

    private final String sourceId;
    private final DiskCache cache;
    private final long ttl;
    private final long minInterval;
    private final Fetcher fetcher;
    private final LedgerStore ledgers;
    private final LongSupplier clock;
    private final ZoneId zone;
    private long lastRequest;
    /** Why the lookup is made; set by the price thread before each one. */
    volatile PriceCachePolicy.Trigger trigger = PriceCachePolicy.Trigger.PREVIEW;
    /** Ask again even when the kept answer is fresh (the change feed marked the title stale); set like trigger. */
    volatile boolean refresh;
    /**
     * When the oldest answer handed out since the price thread last reset it was fetched: a kept copy's time, or now
     * for one from the network (a 404 included); {@link Long#MAX_VALUE} for none. Where to watch says it offline ("Saved today 19:52").
     */
    volatile long oldestServed = Long.MAX_VALUE;

    CachedHttp(String sourceId, DiskCache cache, long ttl, long minInterval, Fetcher fetcher, LedgerStore ledgers,
               LongSupplier clock, ZoneId zone) {
        this.sourceId = sourceId;
        this.cache = cache;
        this.ttl = ttl;
        this.minInterval = minInterval;
        this.fetcher = fetcher;
        this.ledgers = ledgers;
        this.clock = clock;
        this.zone = zone;
    }

    /** The source's quota as kept now. */
    synchronized PriceCachePolicy.Quota quota() {
        return PriceCachePolicy.Quota.decode(sourceId, ledgers.load(sourceId));
    }

    @Override
    public synchronized String get(String url, Map<String, String> headers) throws IOException {
        DiskCache.Entry kept = cache.read(url, ttl);
        if (kept != null && kept.fresh && !refresh) return served(kept);
        PriceCachePolicy.Quota quota = quota();
        if (WatchmodePrices.ID.equals(sourceId)
                && !PriceCachePolicy.fresh(quota.syncedAt, clock.getAsLong(), PriceCachePolicy.DAY)) {
            syncStatus(quota, headers);
        }
        int cost = PriceCachePolicy.requestCost(sourceId);
        if (!quota.allowed(cost, trigger, clock.getAsLong(), zone)) {
            ledgers.save(sourceId, quota.encode());
            throw new OverBudget(sourceId);
        }
        pause();
        Response r;
        try {
            r = fetcher.fetch(url, headers);
        } catch (IOException e) {
            lastRequest = clock.getAsLong();
            // Offline, DNS and timeouts before a reply cost nothing; a reply whose body was lost still counts.
            if (e instanceof Unread) charge(quota, cost);
            if (kept != null) return served(kept);
            throw e;
        }
        lastRequest = clock.getAsLong();
        // Counted whatever the code: an error reply may still have cost something. The server's own count, when the
        // reply has one, replaces ours just below.
        charge(quota, cost);
        quota.sync(r.headers, clock.getAsLong());
        ledgers.save(sourceId, quota.encode());
        if (r.code == 200 && r.body != null && r.body.length > 0) {
            cache.write(url, r.body);
            oldestServed = Math.min(oldestServed, clock.getAsLong());
            return new String(r.body, StandardCharsets.UTF_8);
        }
        if (r.code == 404) {
            cache.write(url, new byte[0]);
            oldestServed = Math.min(oldestServed, clock.getAsLong());
            return null;
        }
        if (kept != null) return served(kept);
        throw new IOException(sourceId + " answered " + r.code);
    }

    /** A kept copy handed out, noting when it was fetched. */
    private String served(DiskCache.Entry kept) {
        oldestServed = Math.min(oldestServed, kept.savedAt);
        return text(kept.bytes);
    }

    private void charge(PriceCachePolicy.Quota quota, int cost) {
        quota.record(cost, lastRequest, zone);
        ledgers.save(sourceId, quota.encode());
    }

    /** Watchmode's status costs no credits: its headers (and body) tell the server's count. Failures don't matter. */
    private void syncStatus(PriceCachePolicy.Quota quota, Map<String, String> headers) {
        try {
            pause();
            Response r = fetcher.fetch(WATCHMODE_STATUS, headers);
            lastRequest = clock.getAsLong();
            quota.sync(r.headers, lastRequest);
            ledgers.save(sourceId, quota.encode());
        } catch (IOException e) {
            // Offline: the kept count will do.
        }
    }

    private void pause() throws IOException {
        long wait = lastRequest + minInterval - clock.getAsLong();
        if (wait <= 0) return;
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }

    /** A kept answer: an empty file is a kept 404. */
    private static String text(byte[] bytes) {
        return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /** The real network: HttpURLConnection with short timeouts. */
    static final Fetcher NET = (url, headers) -> {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        try {
            int code = c.getResponseCode();
            Map<String, String> replyHeaders = new HashMap<>();
            for (Map.Entry<String, List<String>> h : c.getHeaderFields().entrySet()) {
                if (h.getKey() != null && !h.getValue().isEmpty()) {
                    replyHeaders.put(h.getKey().toLowerCase(Locale.ROOT), h.getValue().get(0));
                }
            }
            try {
                if (code != HttpURLConnection.HTTP_OK) {
                    InputStream err = c.getErrorStream();
                    if (err != null) err.close();
                    return new Response(code, null, replyHeaders);
                }
                try (InputStream in = c.getInputStream()) {
                    return new Response(code, DiskCache.readAll(in), replyHeaders);
                }
            } catch (IOException e) {
                throw new Unread(e);
            }
        } finally {
            c.disconnect();
        }
    };
}
