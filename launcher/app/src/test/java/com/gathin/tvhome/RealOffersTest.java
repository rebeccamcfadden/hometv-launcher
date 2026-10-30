package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The preview's and Pick for me's wording with real offers (V2 Phase 2): prices in kronor, "from" prices, and stores
 * no source priced, which show "Rent" without an amount. The mock catalogue's wording is OffersTest's and
 * PreviewRulesTest's.
 */
public class RealOffersTest {

    private final ServiceTable table = ServiceTable.branded();
    private static final Set<ServiceId> NETFLIX_ONLY = EnumSet.of(ServiceId.NETFLIX);

    private static Offer real(ServiceId s, OfferType type, Money money, boolean from, String quality) {
        return new Offer(s, type, money == null ? 0 : money.major(), quality, money, from, true);
    }

    private static Title movie(Offer... offers) {
        Title.Draft d = new Title.Draft("movie:329865", Kind.MOVIE, "Arrival");
        d.year = 2016;
        d.offers = Arrays.asList(offers);
        d.detailed = true;
        return d.build();
    }

    private static final Offer SF_RENT = real(ServiceId.SFANYTIME, OfferType.RENT, null, false, null);
    private static final Offer APPLE_RENT = real(ServiceId.APPLETV, OfferType.RENT, new Money(3900, "SEK"), false, "HD");
    private static final Offer AMAZON_RENT = real(ServiceId.AMAZONVIDEO, OfferType.RENT, new Money(4900, "SEK"), true, "HD");
    private static final Offer APPLE_BUY = real(ServiceId.APPLETV, OfferType.BUY, new Money(7900, "SEK"), false, "4K");
    private static final Offer NETFLIX = real(ServiceId.NETFLIX, OfferType.SUB, null, false, null);

    @Test
    public void aPricedStoreBeatsOneWithoutAPrice() {
        Title t = movie(SF_RENT, AMAZON_RENT, APPLE_RENT);
        assertSame(APPLE_RENT, Offers.cheapest(t, OfferType.RENT));
        assertSame(SF_RENT, Offers.cheapest(movie(SF_RENT), OfferType.RENT));
        assertSame(APPLE_RENT, Offers.bestOffer(t, Collections.emptySet()));
    }

    @Test
    public void watchLabels() {
        assertEquals("Rent on Apple TV Store · 39 kr",
                PreviewRules.watchLabel(movie(APPLE_RENT), APPLE_RENT, NETFLIX_ONLY, table));
        assertEquals("Rent on SF Anytime", PreviewRules.watchLabel(movie(SF_RENT), SF_RENT, NETFLIX_ONLY, table));
        assertEquals("Rent on Amazon Video · from 49 kr",
                PreviewRules.watchLabel(movie(AMAZON_RENT), AMAZON_RENT, NETFLIX_ONLY, table));
        assertEquals("Watch on Netflix", PreviewRules.watchLabel(movie(NETFLIX), NETFLIX, NETFLIX_ONLY, table));
    }

    @Test
    public void theOverviewLine() {
        assertEquals("Included on Netflix · Rent from 39 kr elsewhere",
                PreviewRules.availability(movie(NETFLIX, SF_RENT, AMAZON_RENT, APPLE_RENT), NETFLIX_ONLY, table).toString());
        // Only unpriced rentals: nothing to say about the price.
        assertEquals("Included on Netflix", PreviewRules.availability(movie(NETFLIX, SF_RENT), NETFLIX_ONLY, table).toString());
        assertEquals("Rent on SF Anytime", PreviewRules.availability(movie(SF_RENT), NETFLIX_ONLY, table).toString());
    }

    @Test
    public void whereToWatchCards() {
        List<ServiceOffers> by = Offers.byService(movie(NETFLIX, SF_RENT, APPLE_RENT, APPLE_BUY), NETFLIX_ONLY);
        assertEquals("With your subscription", PreviewRules.serviceOffer(by.get(0)));
        // A real offer without a quality shows none, rather than the prototype's "HD".
        assertEquals("", by.get(0).quality);
        assertEquals("Rent", PreviewRules.serviceOffer(by.get(1)));
        assertEquals("", by.get(1).quality);
        assertEquals("Rent 39 kr · Buy 79 kr", PreviewRules.serviceOffer(by.get(2)));
        assertEquals("HD", by.get(2).quality);
    }

    @Test
    public void detailsCarryTheirSwedishAvailability() throws Exception {
        org.json.JSONObject o = new org.json.JSONObject("{\"id\":329865,\"title\":\"Arrival\",\"release_date\":\"2016-11-10\"}");
        o.put("watch/providers", new org.json.JSONObject(Fixtures.read("tmdb_wp_movie_329865.json")));
        Title t = TmdbParse.details(o, Kind.MOVIE);
        // Blockbuster (closed) is dropped; nothing is priced until a price source answers.
        assertEquals(10, t.offers.size());
        assertEquals(ServiceId.APPLETV, t.offers.get(0).service);
        assertEquals(OfferType.RENT, t.offers.get(0).type);
        assertNull(Offers.price(t.offers.get(0)));
        assertEquals(5, TmdbParse.availability(o).of(ProviderAvailability.Type.BUY).size() - 1);
        // A details answer without watch providers (the cache from before V2 Phase 2) has no offers.
        assertEquals(0, TmdbParse.details(new org.json.JSONObject("{\"id\":1,\"title\":\"X\"}"), Kind.MOVIE).offers.size());
    }

    @Test
    public void cardsAndPickForMe() {
        assertEquals("Rent 39 kr", Offers.availability(APPLE_RENT, NETFLIX_ONLY));
        assertEquals("Rent", Offers.availability(SF_RENT, NETFLIX_ONLY));
        assertEquals("SF Anytime · Rent", Offers.offerLabel(SF_RENT, NETFLIX_ONLY, table));
        assertEquals("39 kr rent", PickRules.tileAvailability(APPLE_RENT, NETFLIX_ONLY));
        assertEquals("49 kr rent", PickRules.tileAvailability(AMAZON_RENT, NETFLIX_ONLY));
        assertEquals("Rent", PickRules.tileAvailability(SF_RENT, NETFLIX_ONLY));
        assertEquals("Rent on SF Anytime", PickRules.openLabel(SF_RENT, NETFLIX_ONLY, table));
        assertEquals("Buy on Apple TV Store · 79 kr", PickRules.openLabel(APPLE_BUY, NETFLIX_ONLY, table));
        assertNull(Offers.price(NETFLIX));
    }
}
