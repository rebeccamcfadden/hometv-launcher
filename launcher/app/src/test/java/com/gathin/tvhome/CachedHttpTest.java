package com.gathin.tvhome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The price sources' HTTP on the TV: the disk cache, stale copies offline, kept 404s, a change-feed refresh, and each
 * keyed source's quota, synced from the servers' headers.
 */
public class CachedHttpTest {

    private static final long D = PriceCachePolicy.DAY;
    private final File dir = new File("build/test-cached-http");
    /** 2026-10-01 12:00 UTC. */
    private long now = 1_790_856_000_000L;
    private final List<String> asked = new ArrayList<>();
    private final Map<String, String> ledgers = new HashMap<>();
    private CachedHttp.Response next;
    private Map<String, String> statusHeaders = Collections.emptyMap();
    private boolean offline;
    /** Thrown instead of answering, when set. */
    private IOException failure;

    private final CachedHttp.Fetcher fake = (url, headers) -> {
        asked.add(url);
        if (offline) throw new IOException("offline");
        if (failure != null) throw failure;
        if (url.equals(CachedHttp.WATCHMODE_STATUS)) return new CachedHttp.Response(200, "{}".getBytes(), statusHeaders);
        return next;
    };

    private final CachedHttp.LedgerStore store = new CachedHttp.LedgerStore() {
        @Override
        public String load(String sourceId) {
            return ledgers.get(sourceId);
        }

        @Override
        public void save(String sourceId, String encoded) {
            ledgers.put(sourceId, encoded);
        }
    };

    private DiskCache cache;

    @Before
    public void setUp() {
        clean();
        cache = new DiskCache(dir, 1 << 20, () -> now);
    }

    @After
    public void clean() {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    private CachedHttp http(String source) {
        return new CachedHttp(source, cache, PriceCachePolicy.PRICE_TTL, 0, fake, store, () -> now, ZoneOffset.UTC);
    }

    private static CachedHttp.Response ok(String body, String... headers) {
        Map<String, String> h = new HashMap<>();
        for (int i = 0; i < headers.length; i += 2) h.put(headers[i], headers[i + 1]);
        return new CachedHttp.Response(200, body.getBytes(StandardCharsets.UTF_8), h);
    }

    private PriceCachePolicy.Quota quota(String source) {
        return PriceCachePolicy.Quota.decode(source, ledgers.get(source));
    }

    @Test
    public void aFreshAnswerIsKeptAWeek() throws IOException {
        CachedHttp h = http(ItunesPrices.ID);
        next = ok("{\"a\":1}");
        assertEquals("{\"a\":1}", h.get("https://x/1", Collections.emptyMap()));
        now += 6 * D;
        assertEquals("{\"a\":1}", h.get("https://x/1", Collections.emptyMap()));
        assertEquals(1, asked.size());
        now += D;
        next = ok("{\"a\":2}");
        assertEquals("{\"a\":2}", h.get("https://x/1", Collections.emptyMap()));
        assertEquals(2, asked.size());
    }

    @Test
    public void aStaleMarkAsksAgainThoughTheAnswerIsFresh() throws IOException {
        CachedHttp h = http(ItunesPrices.ID);
        next = ok("{\"a\":1}");
        h.get("https://x/1", Collections.emptyMap());
        next = ok("{\"a\":2}");
        h.refresh = true;
        assertEquals("{\"a\":2}", h.get("https://x/1", Collections.emptyMap()));
        h.refresh = false;
        assertEquals("{\"a\":2}", h.get("https://x/1", Collections.emptyMap()));
        assertEquals(2, asked.size());
    }

    @Test
    public void offlineGivesTheStaleCopyOrFails() throws IOException {
        CachedHttp h = http(ItunesPrices.ID);
        next = ok("{\"a\":1}");
        h.get("https://x/1", Collections.emptyMap());
        now += 8 * D;
        offline = true;
        assertEquals("{\"a\":1}", h.get("https://x/1", Collections.emptyMap()));
        try {
            h.get("https://x/never", Collections.emptyMap());
            fail();
        } catch (IOException expected) {
            // Nothing kept and no network.
        }
        // A server error is a failure too, with the stale copy when there is one.
        offline = false;
        next = new CachedHttp.Response(500, null);
        assertEquals("{\"a\":1}", h.get("https://x/1", Collections.emptyMap()));
    }

    @Test
    public void aNotFoundIsKept() throws IOException {
        CachedHttp h = http(StreamingAvailabilityPrices.ID);
        next = new CachedHttp.Response(404, null);
        assertNull(h.get("https://x/404", Collections.emptyMap()));
        assertNull(h.get("https://x/404", Collections.emptyMap()));
        assertEquals(1, asked.size());
    }

    @Test
    public void everyReplySyncsTheServersCount() throws IOException {
        CachedHttp h = http(StreamingAvailabilityPrices.ID);
        next = ok("{}", "x-quota-granted", "1000", "x-quota-used", "41", "x-quota-reset", "2026-11-01T00:00:00Z");
        h.get("https://x/1", Collections.emptyMap());
        PriceCachePolicy.Quota q = quota(StreamingAvailabilityPrices.ID);
        // The server's 41 replaces our count of 1.
        assertEquals(41, q.used);
        assertEquals(1000, q.granted);
        assertEquals(1, q.dayUsed);
    }

    @Test
    public void watchmodeAsksItsFreeStatusFirstSoAReinstallStartsFromTheServer() throws IOException {
        CachedHttp h = http(WatchmodePrices.ID);
        statusHeaders = Collections.singletonMap("x-account-quota-used", "2299");
        statusHeaders = new HashMap<>(statusHeaders);
        statusHeaders.put("x-account-quota", "2500");
        next = ok("[]");
        try {
            h.get("https://x/sources", Collections.singletonMap("X-API-Key", "k"));
            fail();
        } catch (CachedHttp.OverBudget expected) {
            // 2,299 used: one more lookup (2 credits) would pass the stop at 2,300.
        }
        assertEquals(Collections.singletonList(CachedHttp.WATCHMODE_STATUS), asked);
        assertEquals(2299, quota(WatchmodePrices.ID).used);
        // The status is asked once a day, not before every lookup.
        try {
            h.get("https://x/sources2", Collections.singletonMap("X-API-Key", "k"));
            fail();
        } catch (CachedHttp.OverBudget expected) {
            // Still spent.
        }
        assertEquals(1, asked.size());
    }

    @Test
    public void aSpentBudgetGivesNoStaleCopy() throws IOException {
        CachedHttp h = http(StreamingAvailabilityPrices.ID);
        next = ok("{\"old\":1}", "x-quota-granted", "1000", "x-quota-used", "500", "x-quota-reset",
                "2026-11-01T00:00:00Z");
        h.get("https://x/1", Collections.emptyMap());
        now += 8 * D;
        next = ok("{}", "x-quota-used", "900");
        h.get("https://x/2", Collections.emptyMap());
        // Its week is up and the budget is spent: no old price passes for a current one.
        try {
            h.get("https://x/1", Collections.emptyMap());
            fail();
        } catch (CachedHttp.OverBudget expected) {
            // The caller falls back to Apple's prices only.
        }
    }

    @Test
    public void theDailyShareStopsTheBackgroundFirst() throws IOException {
        CachedHttp h = http(WatchmodePrices.ID);
        statusHeaders = new HashMap<>();
        statusHeaders.put("x-account-quota", "2500");
        statusHeaders.put("x-account-quota-used", "0");
        next = ok("[]");
        // 2,300 credits over the 31 days to the reset: 74 today, 37 of them for the background, 2 a lookup.
        h.trigger = PriceCachePolicy.Trigger.BACKGROUND;
        for (int i = 0; i < 18; i++) h.get("https://x/" + i, Collections.emptyMap());
        try {
            h.get("https://x/18", Collections.emptyMap());
            fail();
        } catch (CachedHttp.OverBudget expected) {
            // The background has used its half.
        }
        h.trigger = PriceCachePolicy.Trigger.PREVIEW;
        for (int i = 18; i < 37; i++) h.get("https://x/" + i, Collections.emptyMap());
        try {
            h.get("https://x/37", Collections.emptyMap());
            fail();
        } catch (CachedHttp.OverBudget expected) {
            // The day's share is gone.
        }
        assertEquals(74, quota(WatchmodePrices.ID).dayUsed);
    }

    @Test
    public void keylessSourcesHaveNoBudget() throws IOException {
        CachedHttp h = http(ItunesPrices.ID);
        next = ok("{}");
        for (int i = 0; i < 300; i++) h.get("https://x/" + i, Collections.emptyMap());
        assertEquals(300, asked.size());
        assertTrue(quota(ItunesPrices.ID).used >= 300);
    }

    @Test
    public void onlyARequestTheProviderAnsweredIsCounted() throws IOException {
        CachedHttp h = http(StreamingAvailabilityPrices.ID);
        offline = true;
        try {
            h.get("https://x/1", Collections.emptyMap());
            fail();
        } catch (IOException expected) {
            // Offline, DNS or a timeout before a reply: the budget is untouched.
        }
        assertEquals(0, quota(StreamingAvailabilityPrices.ID).used);
        offline = false;
        failure = new CachedHttp.Unread(new IOException("read timed out"));
        try {
            h.get("https://x/1", Collections.emptyMap());
            fail();
        } catch (IOException expected) {
            // The provider answered, then the body was lost: that request counts.
        }
        assertEquals(1, quota(StreamingAvailabilityPrices.ID).used);
        failure = null;
        next = new CachedHttp.Response(500, null);
        try {
            h.get("https://x/1", Collections.emptyMap());
            fail();
        } catch (IOException expected) {
            // An error reply counts too.
        }
        assertEquals(2, quota(StreamingAvailabilityPrices.ID).used);
    }
}