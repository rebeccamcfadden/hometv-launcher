package com.gathin.tvhome;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** How long prices and links are kept, and the keyed sources' quotas: stops, the daily share, server sync, resets. */
public class PriceCachePolicyTest {

    private static final long H = PriceCachePolicy.HOUR, D = PriceCachePolicy.DAY;
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final PriceCachePolicy.Trigger PREVIEW = PriceCachePolicy.Trigger.PREVIEW;
    private static final PriceCachePolicy.Trigger BACKGROUND = PriceCachePolicy.Trigger.BACKGROUND;
    /** 2026-10-01 12:00 UTC. */
    private static final long OCT1 = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli();

    private static Map<String, String> headers(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    public void pricesAreKeptAWeekAndLinksAMonth() {
        assertTrue(PriceCachePolicy.fresh(OCT1 - 6 * D, OCT1, PriceCachePolicy.PRICE_TTL));
        assertFalse(PriceCachePolicy.fresh(OCT1 - 7 * D, OCT1, PriceCachePolicy.PRICE_TTL));
        assertFalse(PriceCachePolicy.fresh(0, OCT1, PriceCachePolicy.PRICE_TTL));
        // A clock that went backwards doesn't keep a stale answer forever.
        assertFalse(PriceCachePolicy.fresh(OCT1 + H, OCT1, PriceCachePolicy.PRICE_TTL));
        assertTrue(PriceCachePolicy.fresh(OCT1 - 29 * D, OCT1, PriceCachePolicy.DEEP_LINK_TTL));
        assertFalse(PriceCachePolicy.fresh(OCT1 - 30 * D, OCT1, PriceCachePolicy.DEEP_LINK_TTL));
    }

    @Test
    public void eachKeyedSourceStopsBelowItsFreeQuota() {
        assertEquals(2300, PriceCachePolicy.stopAt(WatchmodePrices.ID, 2500));
        assertEquals(2300, PriceCachePolicy.stopAt(WatchmodePrices.ID, 0));
        assertEquals(800, PriceCachePolicy.stopAt(WatchmodePrices.ID, 1000));
        assertEquals(900, PriceCachePolicy.stopAt(StreamingAvailabilityPrices.ID, 1000));
        assertEquals(900, PriceCachePolicy.stopAt(StreamingAvailabilityPrices.ID, 0));
        assertEquals(400, PriceCachePolicy.stopAt(StreamingAvailabilityPrices.ID, 500));
        assertEquals(0, PriceCachePolicy.stopAt(ItunesPrices.ID, 0));
        assertEquals(2, PriceCachePolicy.requestCost(WatchmodePrices.ID));
        assertEquals(1, PriceCachePolicy.requestCost(StreamingAvailabilityPrices.ID));
        assertEquals(3000, PriceCachePolicy.minIntervalMs(ItunesPrices.ID));
    }

    @Test
    public void theBudgetIsSpreadToTheResetAndTheBackgroundGetsHalf() {
        PriceCachePolicy.Quota q = new PriceCachePolicy.Quota(WatchmodePrices.ID);
        q.record(0, OCT1, UTC);
        // 2,300 over the 31 days to a reset a month from first use: 74 a day.
        assertEquals(74, q.dailyAllowance(OCT1, UTC));
        q.record(72, OCT1, UTC);
        assertTrue(q.allowed(2, PREVIEW, OCT1, UTC));
        assertFalse(q.allowed(4, PREVIEW, OCT1, UTC));
        assertFalse(q.allowed(2, BACKGROUND, OCT1, UTC));
        PriceCachePolicy.Quota fresh = new PriceCachePolicy.Quota(WatchmodePrices.ID);
        fresh.record(34, OCT1, UTC);
        assertTrue(fresh.allowed(2, BACKGROUND, OCT1, UTC));
        assertFalse(fresh.allowed(4, BACKGROUND, OCT1, UTC));
        // The next day has its own share, with yesterday's leftovers carried over.
        assertTrue(fresh.allowed(36, PREVIEW, OCT1 + D, UTC));
    }

    @Test
    public void theServersCountWinsSoAReinstallCantOverspend() {
        // A reinstall starts at 0, but Watchmode's reply says 2,299 credits are used.
        PriceCachePolicy.Quota q = new PriceCachePolicy.Quota(WatchmodePrices.ID);
        assertTrue(q.allowed(2, PREVIEW, OCT1, UTC));
        q.sync(headers("x-account-quota", "2500", "X-Account-Quota-Used", "2299"), OCT1);
        assertEquals(2500, q.granted);
        assertEquals(2299, q.used);
        assertEquals(OCT1, q.syncedAt);
        assertFalse(q.allowed(2, PREVIEW, OCT1, UTC));
        // A smaller grant lowers the stop too.
        PriceCachePolicy.Quota small = new PriceCachePolicy.Quota(WatchmodePrices.ID);
        small.sync(headers("x-account-quota", "1000", "x-account-quota-used", "799"), OCT1);
        assertEquals(800, small.stop());
        assertFalse(small.allowed(2, PREVIEW, OCT1, UTC));
    }

    @Test
    public void movieOfTheNightResetsWhenItsServerSays() {
        long oct31 = Instant.parse("2026-10-31T12:00:00Z").toEpochMilli();
        PriceCachePolicy.Quota q = new PriceCachePolicy.Quota(StreamingAvailabilityPrices.ID);
        q.sync(headers("X-Quota-Granted", "1000", "X-Quota-Used", "899", "X-Quota-Reset", "2026-11-01T00:00:00Z"), oct31);
        assertEquals(Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(), q.resetAt);
        // One request left below the stop of 900, on the last day.
        assertTrue(q.allowed(1, PREVIEW, oct31, UTC));
        q.record(1, oct31, UTC);
        assertFalse(q.allowed(1, PREVIEW, oct31, UTC));
        long nov1 = Instant.parse("2026-11-01T00:00:01Z").toEpochMilli();
        assertTrue(q.allowed(1, PREVIEW, nov1, UTC));
        assertEquals(0, q.used);
        assertEquals(Instant.parse("2026-12-01T00:00:00Z").toEpochMilli(), q.resetAt);
    }

    @Test
    public void watchmodesPeriodRunsAMonthFromFirstUseAndFollowsADrop() {
        PriceCachePolicy.Quota q = new PriceCachePolicy.Quota(WatchmodePrices.ID);
        q.sync(headers("x-account-quota", "2500", "x-account-quota-used", "4"), OCT1);
        assertEquals(Instant.parse("2026-11-01T12:00:00Z").toEpochMilli(), q.resetAt);
        q.sync(headers("x-account-quota-used", "1200"), OCT1 + 20 * D);
        assertEquals(1200, q.used);
        // The server's count dropped on the 25th: its period started again.
        long oct26 = OCT1 + 25 * D;
        q.sync(headers("x-account-quota-used", "2"), oct26);
        assertEquals(2, q.used);
        assertEquals(Instant.ofEpochMilli(oct26).atZone(UTC).plusMonths(1).toInstant().toEpochMilli(), q.resetAt);
    }

    @Test
    public void keylessSourcesAreAlwaysAllowed() {
        PriceCachePolicy.Quota q = new PriceCachePolicy.Quota(ItunesPrices.ID);
        q.used = 99999;
        assertTrue(q.allowed(1, BACKGROUND, OCT1, UTC));
    }

    @Test
    public void theQuotaSurvivesAsText() {
        PriceCachePolicy.Quota q = new PriceCachePolicy.Quota(StreamingAvailabilityPrices.ID);
        q.sync(headers("X-Quota-Granted", "1000", "X-Quota-Used", "12", "X-Quota-Reset", "1793491200"), OCT1);
        q.record(1, OCT1, UTC);
        PriceCachePolicy.Quota back = PriceCachePolicy.Quota.decode(StreamingAvailabilityPrices.ID, q.encode());
        assertEquals(1000, back.granted);
        assertEquals(13, back.used);
        assertEquals(1793491200000L, back.resetAt);
        assertEquals(OCT1, back.syncedAt);
        assertEquals(1, back.dayUsed);
        assertEquals(0, PriceCachePolicy.Quota.decode(WatchmodePrices.ID, "garbage").used);
        assertEquals(0, PriceCachePolicy.Quota.decode(WatchmodePrices.ID, null).used);
    }

    @Test
    public void itunesOnlyWhenNoApplePriceYet() {
        assertTrue(PriceCachePolicy.needsItunes(Collections.emptyList()));
        assertFalse(PriceCachePolicy.needsItunes(Collections.singletonList(
                new StorePrice("appletv", OfferType.RENT, Quality.HD, new Money(3900, "SEK"), 0, "watchmode"))));
        assertTrue(PriceCachePolicy.needsItunes(Collections.singletonList(
                new StorePrice("sfanytime", OfferType.RENT, Quality.HD, new Money(3900, "SEK"), 0, "watchmode"))));
    }
}
