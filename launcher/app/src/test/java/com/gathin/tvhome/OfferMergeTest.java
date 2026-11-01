package com.gathin.tvhome;

import org.json.JSONException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TMDB availability and the price sources merged into the offers a preview shows. */
public class OfferMergeTest {

    private final WatchProviders table = new WatchProviders();
    private static final PriceTitle ARRIVAL = new PriceTitle(329865, Kind.MOVIE, "Arrival", 2016);

    private static List<String> labels(OfferMerge.Result r) {
        List<String> out = new ArrayList<>();
        for (PricedOffer o : r.offers) {
            String label = label(o);
            out.add(o.provider.key + " " + o.type + (label.isEmpty() ? "" : " " + label));
        }
        return out;
    }

    /** "Rent 49 kr", "Buy from 99 kr", or just "Rent" when no source priced it; "" for SUB and FREE. */
    static String label(PricedOffer o) {
        if (o.type != OfferType.RENT && o.type != OfferType.BUY) return "";
        String verb = o.type == OfferType.RENT ? "Rent" : "Buy";
        Money min = o.cheapest();
        if (min == null) return verb;
        return verb + " " + (o.isFrom() ? min.formatFrom() : min.format());
    }

    private OfferMerge.Result arrival() throws JSONException {
        ProviderAvailability a = ProviderAvailability.parse(Fixtures.read("tmdb_wp_movie_329865.json"), "SE");
        List<StorePrice> prices = new ArrayList<>(WatchmodePrices.parse(Fixtures.read("watchmode_movie_handmade.json"), "SE", table));
        prices.addAll(ItunesPrices.parseMovies(Fixtures.read("itunes_lookup_se.json"), ARRIVAL, true));
        // A dollar price for the same store is ignored in Sweden.
        prices.add(new StorePrice("appletv", OfferType.RENT, Quality.HD, new Money(100, "USD"), 0, "itunes"));
        return OfferMerge.merge(a, prices, table);
    }

    @Test
    public void arrivalInSweden() throws JSONException {
        OfferMerge.Result r = arrival();
        assertEquals(Arrays.asList(
                "appletv RENT Rent 39 kr", "viaplay RENT Rent", "rakuten RENT Rent", "sfanytime RENT Rent 49 kr",
                "amazonvideo RENT Rent from 49 kr",
                "appletv BUY Buy 79 kr", "viaplay BUY Buy 149 kr", "rakuten BUY Buy", "sfanytime BUY Buy",
                "amazonvideo BUY Buy 129 kr",
                // Not in TMDB's Swedish data, but priced by a source that looked the title up by id.
                "googletv RENT Rent 45 kr"), labels(r));
        assertEquals("https://www.themoviedb.org/movie/329865-arrival/watch?locale=SE", r.link);
        assertEquals(Arrays.asList("watchmode", "itunes"), new ArrayList<>(r.priceSources));
    }

    @Test
    public void cheapestPerQualityIsKept() throws JSONException {
        OfferMerge.Result r = arrival();
        PricedOffer buy = r.offers.get(5);
        assertEquals("appletv", buy.provider.key);
        // Watchmode's 4K and iTunes' SD and HD, all 79 kr: the best quality at the cheapest price.
        assertEquals(Quality.UHD, buy.cheapestQuality());
        Money kr79 = new Money(7900, "SEK");
        assertEquals(kr79, buy.prices.get(Quality.SD));
        assertEquals(kr79, buy.prices.get(Quality.HD));
        assertEquals(kr79, buy.prices.get(Quality.UHD));
        PricedOffer amazon = r.offers.get(4);
        assertEquals(new Money(4900, "SEK"), amazon.cheapest());
        assertEquals(Quality.HD, amazon.cheapestQuality());
        assertTrue("qualities cost different amounts", amazon.isFrom());
        assertEquals(new Money(4900, "SEK"), amazon.prices.get(Quality.HD));
        assertEquals(new Money(5950, "SEK"), amazon.prices.get(Quality.UHD));
        assertEquals(2, amazon.prices.size());
    }

    @Test
    public void aCheaperSourceWins() {
        ProviderAvailability a = ProviderAvailability.empty("SE");
        List<StorePrice> prices = Arrays.asList(
                new StorePrice("appletv", OfferType.RENT, Quality.HD, new Money(4900, "SEK"), 0, "watchmode"),
                new StorePrice("appletv", OfferType.RENT, Quality.HD, new Money(3900, "SEK"), 0, "itunes"),
                new StorePrice("appletv", OfferType.RENT, Quality.HD, new Money(4500, "SEK"), 0, "streamingavailability"));
        OfferMerge.Result r = OfferMerge.merge(a, prices, table);
        assertEquals(Collections.singletonList("appletv RENT Rent 39 kr"), labels(r));
        assertEquals(Collections.singleton("itunes"), r.priceSources);
    }

    @Test
    public void subscriptionsFreeAndDroppedProviders() throws JSONException {
        ProviderAvailability a = ProviderAvailability.parse(Fixtures.read("tmdb_wp_movie_157336.json"), "SE");
        OfferMerge.Result r = OfferMerge.merge(a, Collections.emptyList(), table);
        // The HBO Max Amazon Channel and Blockbuster are dropped; Viaplay streams it and nobody prices the stores.
        assertEquals(Arrays.asList("prime SUB", "viaplay SUB", "tele2play SUB", "max SUB",
                "appletv RENT Rent", "rakuten RENT Rent", "sfanytime RENT Rent", "amazonvideo RENT Rent",
                "appletv BUY Buy", "rakuten BUY Buy", "sfanytime BUY Buy", "amazonvideo BUY Buy"), labels(r));
        assertTrue(r.priceSources.isEmpty());

        String free = "{\"results\":{\"SE\":{\"flatrate\":[{\"provider_id\":1944}],"
                + "\"free\":[{\"provider_id\":493}],\"ads\":[{\"provider_id\":1944},{\"provider_id\":300}]}}}";
        assertEquals(Arrays.asList("tv4play SUB", "svtplay FREE", "tv4play FREE", "plutotv FREE"),
                labels(OfferMerge.merge(ProviderAvailability.parse(free, "SE"), Collections.emptyList(), table)));
    }

    @Test
    public void seasonPricesReadFrom() throws JSONException {
        ProviderAvailability a = ProviderAvailability.empty("SE");
        List<StorePrice> prices = StreamingAvailabilityPrices.parse(Fixtures.read("sa_tv_handmade.json"), "SE", table);
        OfferMerge.Result r = OfferMerge.merge(a, prices, table);
        assertEquals(Arrays.asList("appletv BUY Buy from 199 kr", "amazonvideo BUY Buy from 149 kr"), labels(r));
        assertTrue(r.offers.get(0).seasonal);
        assertEquals(Collections.singleton(Quality.HD), r.offers.get(0).prices.keySet());
        assertEquals(new Money(19900, "SEK"), r.offers.get(0).prices.get(Quality.HD));
    }

    @Test
    public void unknownStoresAndRegionsWithoutACurrencyArePriceless() {
        List<StorePrice> prices = Arrays.asList(
                new StorePrice("blockbuster", OfferType.RENT, Quality.HD, new Money(3900, "SEK"), 0, "x"));
        assertTrue(OfferMerge.merge(ProviderAvailability.empty("SE"), prices, table).offers.isEmpty());
        List<StorePrice> sek = Collections.singletonList(
                new StorePrice("appletv", OfferType.RENT, Quality.HD, new Money(3900, "SEK"), 0, "x"));
        assertTrue(OfferMerge.merge(ProviderAvailability.empty("XX"), sek, table).offers.isEmpty());
    }

    @Test
    public void theLaunchersOffers() throws JSONException {
        List<Offer> offers = arrival().toOffers();
        List<String> s = new ArrayList<>();
        for (Offer o : offers) s.add(o + (o.money != null ? " " + Offers.price(o) : ""));
        assertEquals(Arrays.asList("appletv RENT 39.0 HD 39 kr", "viaplay RENT", "rakuten RENT",
                "sfanytime RENT 49.0 HD 49 kr", "amazonvideo RENT 49.0 HD from 49 kr", "appletv BUY 79.0 4K 79 kr",
                "viaplay BUY 149.0 HD 149 kr", "rakuten BUY", "sfanytime BUY", "amazonvideo BUY 129.0 HD 129 kr",
                "googletv RENT 45.0 HD 45 kr"), s);
        for (Offer o : offers) assertTrue(o.real);
        // A store with no known price reads "Rent", with no amount.
        assertNull(Offers.price(offers.get(1)));
        assertEquals("Rent", Offers.paid(offers.get(1)));
        assertEquals("Rent from 49 kr", Offers.paid(offers.get(4)));
        assertEquals("49 kr", Offers.amount(offers.get(4)));
    }

    @Test
    public void attributionNamesOnlyTheSourcesUsed() throws JSONException {
        assertEquals("Availability: JustWatch · Prices: Watchmode / Apple",
                OfferMerge.attribution(true, arrival().priceSources));
        assertEquals("Availability: JustWatch", OfferMerge.attribution(true, Collections.emptySet()));
        assertEquals("Availability: JustWatch · Prices: Movie of the Night",
                OfferMerge.attribution(true, Collections.singleton("streamingavailability")));
        assertNull(OfferMerge.attribution(false, Collections.singleton("itunes")));
    }

    @Test
    public void subscriptionOffersHaveNoPrice() {
        PricedOffer sub = new PricedOffer(table.byKey("netflix"), OfferType.SUB, null, false);
        assertTrue(sub.prices.isEmpty());
        assertEquals("", label(sub));
        assertNull(sub.cheapest());
        Offer o = sub.toOffer();
        assertEquals(ServiceId.NETFLIX, o.service);
        assertEquals(0, o.price, 0);
        assertNull(o.quality);
        assertTrue(o.real);
        assertNull(o.money);
        Map<Quality, Money> one = new EnumMap<>(Quality.class);
        one.put(Quality.HD, new Money(4900, "SEK"));
        Offer sf = new PricedOffer(table.byKey("sfanytime"), OfferType.RENT, one, false).toOffer();
        assertEquals(ServiceId.SFANYTIME, sf.service);
        assertEquals("49 kr", Offers.price(sf));
        assertFalse(sf.from);
    }
}
