package com.gathin.tvhome;

import org.json.JSONException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Apple TV store prices from the iTunes lookup and search, with recorded and hand-made answers. */
public class ItunesPricesTest {

    private static final PriceTitle ARRIVAL = new PriceTitle(329865, Kind.MOVIE, "Arrival", null, 2016,
            Arrays.asList("1166395905", "1174618339", "1274926574", "1476438441"));
    private static final PriceTitle INTERSTELLAR = new PriceTitle(157336, Kind.MOVIE, "Interstellar", null, 2014,
            Arrays.asList("960891136", "965491522", "968877587"));

    private static List<String> lines(List<StorePrice> prices) {
        List<String> out = new ArrayList<>();
        for (StorePrice p : prices) out.add(p.type + " " + p.quality + " " + p.price.format() + (p.season > 0 ? " S" + p.season : ""));
        return out;
    }

    @Test
    public void requests() {
        assertEquals("https://itunes.apple.com/lookup?id=1166395905,1174618339,1274926574,1476438441&country=se",
                ItunesPrices.lookupUrl(ARRIVAL));
        assertNull(ItunesPrices.lookupUrl(new PriceTitle(1, Kind.MOVIE, "X", 2000)));
        // Ids that aren't numbers never reach the URL.
        assertNull(ItunesPrices.lookupUrl(new PriceTitle(1, Kind.MOVIE, "X", null, 2000, Arrays.asList("1&x=2", "0"))));
        assertEquals("https://itunes.apple.com/search?term=Dune%3A+Part+Two&country=se&media=movie&entity=movie&limit=10",
                ItunesPrices.searchUrl(new PriceTitle(693134, Kind.MOVIE, "Dune: Part Two", 2024)));
        assertEquals("https://itunes.apple.com/search?term=Succession&country=se&media=tvShow&entity=tvSeason&limit=25",
                ItunesPrices.searchUrl(new PriceTitle(76331, Kind.SHOW, "Succession", 2018)));
    }

    @Test
    public void theSwedishLookupAnswersInKronor() throws JSONException {
        String json = Fixtures.read("itunes_lookup_se.json");
        assertEquals(Arrays.asList("RENT SD 39 kr", "RENT HD 39 kr", "BUY SD 79 kr", "BUY HD 79 kr"),
                lines(ItunesPrices.parseMovies(json, ARRIVAL, true)));
        // The same answer holds both films; each title takes its own by year.
        assertEquals(Arrays.asList("RENT SD 39 kr", "RENT HD 39 kr", "BUY SD 99 kr", "BUY HD 99 kr"),
                lines(ItunesPrices.parseMovies(json, INTERSTELLAR, true)));
        StorePrice p = ItunesPrices.parseMovies(json, ARRIVAL, true).get(0);
        assertEquals("appletv", p.store);
        assertEquals("itunes", p.source);
        assertEquals(new Money(3900, "SEK"), p.price);
    }

    @Test
    public void searchPicksTheSameYearOverAYearOff() throws JSONException {
        String json = Fixtures.read("itunes_search_movies_handmade.json");
        assertEquals(Arrays.asList("RENT SD 39 kr", "RENT HD 39 kr", "BUY SD 79 kr", "BUY HD 79 kr"),
                lines(ItunesPrices.parseMovies(json, new PriceTitle(329865, Kind.MOVIE, "Arrival", 2016), false)));
        // "The Arrival" (1996) is a different film; "Arrival (2016)" released 2017 is the only near one for 2017.
        assertEquals(Arrays.asList("RENT SD 45 kr", "RENT HD 45 kr", "BUY SD 99 kr", "BUY HD 99 kr"),
                lines(ItunesPrices.parseMovies(json, new PriceTitle(1, Kind.MOVIE, "Arrival", 2018), false)));
        assertTrue(ItunesPrices.parseMovies(json, new PriceTitle(1, Kind.MOVIE, "Arrival", 2005), false).isEmpty());
        // A missing HD price is left out, not zero.
        assertEquals(Arrays.asList("RENT SD 19 kr", "BUY SD 39 kr"),
                lines(ItunesPrices.parseMovies(json, new PriceTitle(1, Kind.MOVIE, "Arrival Day", 2016), false)));
    }

    @Test
    public void seasonsAreBoughtPerSeason() throws JSONException {
        // Recorded in the US store (Sweden's search finds no seasons today), so in dollars.
        String json = Fixtures.read("itunes_search_tvseason_us.json");
        PriceTitle succession = new PriceTitle(76331, Kind.SHOW, "Succession", 2018);
        assertEquals(Arrays.asList("BUY SD $24.99 S1", "BUY HD $24.99 S1"), lines(ItunesPrices.parseSeasons(json, succession)));
        assertTrue(ItunesPrices.parseSeasons(json, new PriceTitle(1, Kind.SHOW, "Billions", 2016)).isEmpty());
    }

    @Test
    public void emptyAndMissingAnswers() throws JSONException {
        assertTrue(ItunesPrices.parseMovies(Fixtures.read("itunes_search_empty.json"), ARRIVAL, false).isEmpty());
        assertTrue(ItunesPrices.parseMovies(null, ARRIVAL, false).isEmpty());
        assertTrue(ItunesPrices.parseSeasons(null, ARRIVAL).isEmpty());
    }

    @Test
    public void looksUpFirstAndSearchesOnlyWhenTheLookupFindsNothing() throws Exception {
        Fixtures.FakeHttp http = new Fixtures.FakeHttp().answer("https://itunes.apple.com/lookup", "itunes_lookup_se.json");
        assertEquals(4, new ItunesPrices().prices(ARRIVAL, http).size());
        assertEquals(1, http.urls.size());
        assertEquals(Collections.emptyMap(), http.headers.get(0));

        Fixtures.FakeHttp empty = new Fixtures.FakeHttp()
                .answer("https://itunes.apple.com/lookup", "itunes_search_empty.json")
                .answer("https://itunes.apple.com/search", "itunes_search_empty.json");
        assertTrue(new ItunesPrices().prices(ARRIVAL, empty).isEmpty());
        assertEquals(2, empty.urls.size());
        assertTrue(empty.urls.get(1).startsWith("https://itunes.apple.com/search?term=Arrival"));
        assertEquals(2, new ItunesPrices().cost(ARRIVAL));
    }
}
