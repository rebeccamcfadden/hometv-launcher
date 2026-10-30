package com.gathin.tvhome;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One title's lookup with fake networks, and when it is due again: a price step that fails is retried after an hour
 * even when the ids came, and the background's spent budget doesn't keep the preview from asking.
 */
public class PriceLookupTest {

    private static final long H = PriceCachePolicy.HOUR, D = PriceCachePolicy.DAY;
    private static final long NOW = 1_800_000_000_000L;
    private static final PriceCachePolicy.Trigger BACKGROUND = PriceCachePolicy.Trigger.BACKGROUND;
    private static final PriceCachePolicy.Trigger PREVIEW = PriceCachePolicy.Trigger.PREVIEW;

    private final List<String> asked = new ArrayList<>();

    /** Wikidata answers from "its cache" (the recorded Arrival answer). */
    private final HttpGet wikidata = (url, headers) -> {
        asked.add("wikidata");
        return Fixtures.read("wikidata_movie_329865.json");
    };
    private final HttpGet itunesOk = (url, headers) -> {
        asked.add("itunes");
        return Fixtures.read("itunes_lookup_se.json");
    };
    private final HttpGet itunesDown = (url, headers) -> {
        asked.add("itunes");
        throw new IOException("offline");
    };
    private final HttpGet watchmodeSpent = (url, headers) -> {
        asked.add("watchmode");
        throw new CachedHttp.OverBudget(WatchmodePrices.ID);
    };
    private final HttpGet watchmodeOk = (url, headers) -> {
        asked.add("watchmode");
        return Fixtures.read("watchmode_movie_handmade.json");
    };
    private final HttpGet never = (url, headers) -> {
        throw new AssertionError("not asked: " + url);
    };

    private PriceLookup lookup(String watchmodeKey, HttpGet watchmodeHttp, HttpGet itunesHttp) {
        return new PriceLookup(wikidata, new WatchmodePrices(watchmodeKey), watchmodeHttp,
                new StreamingAvailabilityPrices(""), never, new ItunesPrices(), itunesHttp);
    }

    private PriceLookup.Result arrival(PriceLookup l) {
        return l.run(Kind.MOVIE, 329865, "Arrival", 2016, true);
    }

    @Test
    public void allWell() {
        PriceLookup.Result r = arrival(lookup("", never, itunesOk));
        assertTrue(r.idsOk && r.pricesOk && !r.budgetBlocked);
        assertEquals(4, r.prices.size());
        PriceLookup.Entry e = PriceLookup.Entry.after(null, r, true, BACKGROUND, NOW);
        assertEquals(NOW, e.idsAt);
        assertEquals(NOW, e.pricedAt);
        assertEquals(0, e.failedAt);
        assertFalse(PriceLookup.needsLookup(e, true, true, NOW + 6 * D, 0));
        assertTrue(PriceLookup.needsLookup(e, true, false, NOW + 7 * D, 0));
    }

    @Test
    public void idsFromTheCacheButItunesDownIsRetriedAfterAnHourNotADay() {
        PriceLookup.Result r = arrival(lookup("", never, itunesDown));
        assertTrue(r.idsOk);
        assertFalse(r.pricesOk);
        PriceLookup.Entry e = PriceLookup.Entry.after(null, r, true, BACKGROUND, NOW);
        // The ids count as fresh; the prices don't.
        assertEquals(NOW, e.idsAt);
        assertEquals(0, e.pricedAt);
        assertEquals(NOW, e.failedAt);
        assertFalse(PriceLookup.needsLookup(e, true, false, NOW + 59 * 60_000L, 0));
        assertFalse(PriceLookup.needsLookup(e, true, true, NOW + 59 * 60_000L, 0));
        assertTrue(PriceLookup.needsLookup(e, true, false, NOW + H, 0));
        // A preview that only wants the links is satisfied by the ids.
        assertFalse(PriceLookup.needsLookup(e, false, true, NOW + 10 * 60_000L, 0));

        // The retry an hour later succeeds: prices and both stamps are current, and the failure is gone.
        PriceLookup.Entry next = PriceLookup.Entry.after(e, arrival(lookup("", never, itunesOk)), true, BACKGROUND, NOW + H);
        assertEquals(NOW + H, next.pricedAt);
        assertEquals(0, next.failedAt);
        assertEquals(4, next.prices.size());
    }

    @Test
    public void aListsSpentBudgetLetsThePreviewAskAgain() {
        PriceLookup.Result r = arrival(lookup("key", watchmodeSpent, itunesOk));
        assertTrue(r.budgetBlocked);
        assertFalse(r.pricesOk);
        // iTunes still answered, so the Apple prices are kept meanwhile.
        assertEquals(4, r.prices.size());
        PriceLookup.Entry e = PriceLookup.Entry.after(null, r, true, BACKGROUND, NOW);
        assertTrue(e.listBlocked);
        assertEquals(0, e.pricedAt);
        assertEquals(4, e.prices.size());
        // Another background lookup waits for the retry; the preview asks now, under its own allowance.
        assertFalse(PriceLookup.needsLookup(e, true, false, NOW + 60_000L, 0));
        assertTrue(PriceLookup.needsLookup(e, true, true, NOW + 60_000L, 0));

        asked.clear();
        PriceLookup.Result again = arrival(lookup("key", watchmodeOk, never));
        assertEquals(java.util.Arrays.asList("wikidata", "watchmode"), asked);
        assertTrue(again.pricesOk);
        PriceLookup.Entry priced = PriceLookup.Entry.after(e, again, true, PREVIEW, NOW + 60_000L);
        assertFalse(priced.listBlocked);
        assertEquals(NOW + 60_000L, priced.pricedAt);
        assertFalse(PriceLookup.needsLookup(priced, true, true, NOW + 2 * H, 0));
    }

    @Test
    public void aPreviewsOwnSpentBudgetWaitsAnHour() {
        PriceLookup.Entry e = PriceLookup.Entry.after(null, arrival(lookup("key", watchmodeSpent, itunesOk)), true,
                PREVIEW, NOW);
        assertFalse(e.listBlocked);
        assertFalse(PriceLookup.needsLookup(e, true, true, NOW + 60_000L, 0));
        assertTrue(PriceLookup.needsLookup(e, true, true, NOW + H, 0));
    }

    @Test
    public void wikidataDownKeepsTheIdsItHad() {
        PriceLookup.Entry old = PriceLookup.Entry.after(null, arrival(lookup("", never, itunesOk)), true, BACKGROUND, NOW);
        PriceLookup down = new PriceLookup((url, headers) -> {
            throw new IOException("offline");
        }, new WatchmodePrices(""), never, new StreamingAvailabilityPrices(""), never, new ItunesPrices(), itunesDown);
        PriceLookup.Result r = down.run(Kind.MOVIE, 329865, "Arrival", 2016, true);
        assertFalse(r.idsOk);
        PriceLookup.Entry e = PriceLookup.Entry.after(old, r, true, BACKGROUND, NOW + 8 * D);
        assertEquals("80117799", e.ids.first(WikidataIds.NETFLIX));
        assertEquals(NOW, e.idsAt);
        assertEquals(4, e.prices.size());
        assertEquals(NOW + 8 * D, e.failedAt);
        assertFalse(PriceLookup.needsLookup(e, true, true, NOW + 8 * D + 60_000L, 0));
    }

    @Test
    public void watchmodeFirstAndAppleFromItIfItHasIt() {
        HttpGet watchmodeReal = (url, headers) -> {
            asked.add("watchmode");
            return Fixtures.read("watchmode_movie_157336.json");
        };
        PriceLookup l = new PriceLookup(wikidata, new WatchmodePrices("key"), watchmodeReal,
                new StreamingAvailabilityPrices("key"), never, new ItunesPrices(), never);
        PriceLookup.Result r = l.run(Kind.MOVIE, 157336, "Interstellar", 2014, true);
        // Watchmode priced every store, Apple included: neither Movie of the Night nor iTunes is asked.
        assertEquals(java.util.Arrays.asList("wikidata", "watchmode"), asked);
        assertTrue(r.pricesOk);
        java.util.Set<String> stores = new java.util.TreeSet<>();
        for (StorePrice p : r.prices) stores.add(p.store);
        // Blockbuster (closed) is dropped.
        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList("amazonvideo", "appletv", "rakuten", "sfanytime")),
                stores);
    }

    @Test
    public void movieOfTheNightOnlyWhenWatchmodeHasNoPrice() {
        HttpGet watchmodeEmpty = (url, headers) -> {
            asked.add("watchmode");
            return "[]";
        };
        HttpGet motn = (url, headers) -> {
            asked.add("motn");
            return Fixtures.read("motn_movie_157336.json");
        };
        PriceLookup l = new PriceLookup(wikidata, new WatchmodePrices("key"), watchmodeEmpty,
                new StreamingAvailabilityPrices("key"), motn, new ItunesPrices(), never);
        PriceLookup.Result r = l.run(Kind.MOVIE, 157336, "Interstellar", 2014, true);
        assertEquals(java.util.Arrays.asList("wikidata", "watchmode", "motn"), asked);
        assertTrue(r.pricesOk);
        assertEquals(4, r.prices.size());
    }

    @Test
    public void bothBudgetsSpentLeaveOnlyApple() {
        HttpGet motnSpent = (url, headers) -> {
            asked.add("motn");
            throw new CachedHttp.OverBudget(StreamingAvailabilityPrices.ID);
        };
        PriceLookup l = new PriceLookup(wikidata, new WatchmodePrices("key"), watchmodeSpent,
                new StreamingAvailabilityPrices("key"), motnSpent, new ItunesPrices(), itunesOk);
        PriceLookup.Result r = arrival(l);
        assertEquals(java.util.Arrays.asList("wikidata", "watchmode", "motn", "itunes"), asked);
        assertTrue(r.budgetBlocked);
        for (StorePrice p : r.prices) assertEquals("appletv", p.store);
        // Older prices from the spent sources are not kept as current: only Apple's remain.
        StorePrice sf = new StorePrice("sfanytime", OfferType.RENT, Quality.HD, new Money(4900, "SEK"), 0, "watchmode");
        PriceLookup.Entry old = new PriceLookup.Entry(WikidataIds.none(), java.util.Collections.singletonList(sf),
                NOW - 8 * D, NOW - 8 * D, 0, false);
        PriceLookup.Entry e = PriceLookup.Entry.after(old, r, true, PREVIEW, NOW);
        for (StorePrice p : e.prices) assertEquals("appletv", p.store);
        assertEquals(4, e.prices.size());
    }

    @Test
    public void theChangeFeedsMarkMakesPricesDueEarly() {
        PriceLookup.Entry e = PriceLookup.Entry.after(null, arrival(lookup("", never, itunesOk)), true, BACKGROUND, NOW);
        assertFalse(PriceLookup.needsLookup(e, true, false, NOW + D, 0));
        // Marked stale after the prices came: due now, for the background too.
        assertTrue(PriceLookup.needsLookup(e, true, false, NOW + D, NOW + H));
        // A mark older than the prices changes nothing; nor does one for a preview that only wants links.
        assertFalse(PriceLookup.needsLookup(e, true, false, NOW + D, NOW - H));
        assertFalse(PriceLookup.needsLookup(e, false, true, NOW + D, NOW + H));
    }

    @Test
    public void noLookupYetMeansOneIsDue() {
        assertTrue(PriceLookup.needsLookup(null, false, false, NOW, 0));
    }
}
