package com.gathin.tvhome;

import org.json.JSONException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Watchmode and Streaming Availability: their requests and their answers. The answers are hand-made from each API's
 * published schema (openapi.json, openapi.yaml), since no key was available to record real ones.
 */
public class KeyedPriceSourcesTest {

    private static final PriceTitle ARRIVAL = new PriceTitle(329865, Kind.MOVIE, "Arrival", 2016);
    private static final PriceTitle SUCCESSION = new PriceTitle(76331, Kind.SHOW, "Succession", 2018);
    private final WatchProviders table = new WatchProviders();

    private static List<String> lines(List<StorePrice> prices) {
        List<String> out = new ArrayList<>();
        for (StorePrice p : prices) {
            out.add(p.store + " " + p.type + " " + p.quality.label + " " + p.price.format() + (p.season > 0 ? " S" + p.season : ""));
        }
        return out;
    }

    // ---- Watchmode --------------------------------------------------------------------------------------------------

    @Test
    public void watchmodeRequests() {
        assertEquals("https://api.watchmode.com/v1/title/movie-329865/sources/?regions=SE", WatchmodePrices.url(ARRIVAL));
        assertEquals("https://api.watchmode.com/v1/title/tv-76331/sources/?regions=SE", WatchmodePrices.url(SUCCESSION));
        WatchmodePrices w = new WatchmodePrices(" key ");
        assertTrue(w.configured());
        assertEquals(Collections.singletonMap("X-API-Key", "key"), w.headers());
        assertEquals(2, w.cost(ARRIVAL));
        assertFalse(new WatchmodePrices(null).configured());
        assertFalse(new WatchmodePrices("  ").configured());
    }

    @Test
    public void watchmodeAnswersBecomeKronorPrices() throws JSONException {
        List<StorePrice> p = WatchmodePrices.parse(Fixtures.read("watchmode_movie_handmade.json"), "SE", table);
        // No price, another region, a subscription and Blockbuster are skipped; no format counts as HD.
        assertEquals(Arrays.asList(
                "appletv RENT SD 39 kr", "appletv RENT HD 39 kr", "appletv BUY 4K 79 kr",
                "amazonvideo RENT HD 49 kr", "amazonvideo RENT 4K 59.50 kr", "amazonvideo BUY HD 129 kr",
                "sfanytime RENT HD 49 kr", "viaplay BUY HD 149 kr", "googletv RENT HD 45 kr"), lines(p));
        assertEquals("watchmode", p.get(0).source);
        assertTrue(WatchmodePrices.parse(null, "SE", table).isEmpty());
        assertTrue(WatchmodePrices.parse("[]", "XX", table).isEmpty());
    }

    @Test
    public void watchmodesRecordedSwedishAnswerInKronorWithoutBlockbuster() throws JSONException {
        // Recorded 2026-10-30 for Interstellar: AppleTV, Amazon, Rakuten TV, SF Anytime and Blockbuster, SD to 4K.
        List<StorePrice> p = WatchmodePrices.parse(Fixtures.read("watchmode_movie_157336.json"), "SE", table);
        for (StorePrice s : p) {
            assertTrue(s.store, !s.store.contains("blockbuster"));
            assertEquals("SEK", s.price.currency);
        }
        assertEquals(21, p.size());
        ProviderAvailability a = ProviderAvailability.parse(Fixtures.read("tmdb_wp_movie_157336.json"), "SE");
        List<String> rent = new ArrayList<>();
        for (PricedOffer o : OfferMerge.merge(a, p, table).offers) {
            if (o.type == OfferType.RENT) rent.add(o.provider.key + " " + OfferMergeTest.label(o));
        }
        assertEquals(Arrays.asList("appletv Rent 39 kr", "rakuten Rent 39 kr", "sfanytime Rent 49 kr",
                "amazonvideo Rent 39 kr"), rent);
    }

    @Test
    public void watchmodeWithoutAKeyAsksNothing() throws Exception {
        Fixtures.FakeHttp http = new Fixtures.FakeHttp();
        assertTrue(new WatchmodePrices("").prices(ARRIVAL, http).isEmpty());
        assertTrue(http.urls.isEmpty());
        Fixtures.FakeHttp ok = new Fixtures.FakeHttp().answer("https://api.watchmode.com/", "watchmode_movie_handmade.json");
        assertEquals(9, new WatchmodePrices("k").prices(ARRIVAL, ok).size());
        assertEquals("k", ok.headers.get(0).get("X-API-Key"));
    }

    // ---- Streaming Availability -------------------------------------------------------------------------------------

    @Test
    public void streamingAvailabilityRequests() {
        assertEquals("https://api.movieofthenight.com/v4/shows/movie/329865?country=se",
                StreamingAvailabilityPrices.url(ARRIVAL));
        assertEquals("https://api.movieofthenight.com/v4/shows/tv/76331?country=se&series_granularity=season",
                StreamingAvailabilityPrices.url(SUCCESSION));
        StreamingAvailabilityPrices s = new StreamingAvailabilityPrices("k");
        assertEquals(Collections.singletonMap("X-API-Key", "k"), s.headers());
        assertEquals(1, s.cost(ARRIVAL));
        assertFalse(new StreamingAvailabilityPrices(null).configured());
    }

    @Test
    public void streamingAvailabilityMovie() throws JSONException {
        List<StorePrice> p = StreamingAvailabilityPrices.parse(Fixtures.read("sa_movie_handmade.json"), "SE", table);
        // Subscriptions and the add-on channel are skipped; qhd is sold as HD.
        assertEquals(Arrays.asList("appletv RENT 4K 39 kr", "appletv BUY 4K 79 kr",
                "amazonvideo RENT HD 49 kr", "amazonvideo BUY HD 129 kr"), lines(p));
        assertEquals("streamingavailability", p.get(0).source);
    }

    @Test
    public void streamingAvailabilitySeries() throws JSONException {
        List<StorePrice> p = StreamingAvailabilityPrices.parse(Fixtures.read("sa_tv_handmade.json"), "SE", table);
        // The series as a whole has no price; each season has its own.
        assertEquals(Arrays.asList("appletv BUY HD 199 kr S1", "amazonvideo BUY SD 149 kr S1", "appletv BUY HD 249 kr S2"),
                lines(p));
    }

    @Test
    public void streamingAvailabilityNotFound() throws Exception {
        assertTrue(new StreamingAvailabilityPrices("k").prices(ARRIVAL, new Fixtures.FakeHttp()).isEmpty());
        assertTrue(StreamingAvailabilityPrices.parse("{}", "SE", table).isEmpty());
    }
}
