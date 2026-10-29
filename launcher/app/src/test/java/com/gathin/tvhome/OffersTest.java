package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The offer rules against design_preview/src/data/offers.ts, with the subscribed services passed in. */
public class OffersTest {

    /** The services design_preview/src/data/services.ts marks subscribed. */
    static final Set<ServiceId> PROTOTYPE = Collections.unmodifiableSet(EnumSet.of(ServiceId.NETFLIX, ServiceId.PRIME,
            ServiceId.DISNEY, ServiceId.MAX, ServiceId.APPLETVPLUS, ServiceId.HULU, ServiceId.YOUTUBE));
    private static final Set<ServiceId> NONE = Collections.emptySet();

    private final MockCatalog catalog = new MockCatalog();
    private final ServiceTable table = new ServiceTable();

    private Title t(String id) {
        Title t = catalog.get(id);
        assertNotNull(id, t);
        return t;
    }

    private static String format(Offer o) {
        return o == null ? "-" : o.service.id + "/" + o.type.name().toLowerCase(Locale.ROOT) + "/" + String.format(Locale.US, "%.2f", o.price);
    }

    private static String format(List<ServiceOffers> list) {
        StringBuilder b = new StringBuilder();
        for (ServiceOffers s : list) {
            if (b.length() > 0) b.append(' ');
            b.append(s.service.id).append(':').append(s.included ? "i" : "").append(s.free ? "f" : "").append(':')
                    .append(s.rent == null ? "" : String.format(Locale.US, "%.2f", s.rent.price)).append(':')
                    .append(s.buy == null ? "" : String.format(Locale.US, "%.2f", s.buy.price)).append(':')
                    .append(s.quality);
        }
        return b.toString();
    }

    @Test
    public void everyTitleMatchesThePrototype() throws Exception {
        List<PrototypeTitles.Row> rows = PrototypeTitles.rows();
        assertEquals(catalog.all().size(), rows.size());
        for (PrototypeTitles.Row row : rows) {
            Title t = t(row.id);
            Offer best = Offers.bestOffer(t, PROTOTYPE);
            assertEquals(row.id, row.included, Offers.isIncluded(t, PROTOTYPE));
            assertEquals(row.id, row.bestOffer, format(best));
            assertEquals(row.id, row.offerLabel, Offers.offerLabel(best, PROTOTYPE, table));
            assertEquals(row.id, row.availability, Offers.availability(best, PROTOTYPE));
            assertEquals(row.id, row.byService, format(Offers.byService(t, PROTOTYPE)));
        }
    }

    @Test
    public void pricesHaveTwoDecimals() {
        assertEquals("$3.99", Offers.price(3.99));
        assertEquals("$12.99", Offers.price(9.99 + 3));
        assertEquals("$0.00", Offers.price(0));
        assertEquals("$20.00", Offers.price(19.999));
    }

    @Test
    public void runtimes() {
        assertEquals("1h 56m", Offers.runtimeLabel(116));
        assertEquals("52m", Offers.runtimeLabel(52));
        assertEquals("1h 0m", Offers.runtimeLabel(60));
        assertEquals("3h 26m", Offers.runtimeLabel(206));
        assertEquals("0m", Offers.runtimeLabel(0));
    }

    @Test
    public void aSubscriptionCountsOnlyWhenSubscribed() {
        Offer prime = t("arrival").offers.get(0);
        assertTrue(Offers.isIncludedOffer(prime, EnumSet.of(ServiceId.PRIME)));
        assertFalse(Offers.isIncludedOffer(prime, NONE));
        assertFalse(Offers.isIncludedOffer(prime, EnumSet.of(ServiceId.NETFLIX)));
        assertFalse(Offers.isIncludedOffer(null, PROTOTYPE));
    }

    @Test
    public void freeIsAlwaysIncluded() {
        Title microcosmos = t("microcosmos");
        assertTrue(Offers.isIncluded(microcosmos, NONE));
        Offer best = Offers.bestOffer(microcosmos, NONE);
        assertEquals(OfferType.FREE, best.type);
        assertEquals("YouTube · Free", Offers.offerLabel(best, NONE, table));
        assertEquals("Free", Offers.availability(best, NONE));
    }

    @Test
    public void withoutTheServiceTheCheapestRentalWins() {
        Offer best = Offers.bestOffer(t("arrival"), NONE);
        // Apple TV, Google TV and YouTube all rent at 3.99: the first one listed wins, as Array.sort keeps order.
        assertEquals(ServiceId.APPLETV, best.service);
        assertEquals(OfferType.RENT, best.type);
        assertEquals("Apple TV · Rent $3.99", Offers.offerLabel(best, NONE, table));
        assertEquals("Rent $3.99", Offers.availability(best, NONE));
        assertEquals(2.99, Offers.bestOffer(t("the-arrival-1996"), NONE).price, 0);
    }

    @Test
    public void onlyPurchasesGiveTheCheapestPurchase() {
        Title t = title(new Offer(ServiceId.GOOGLETV, OfferType.BUY, 14.99, "4K"),
                new Offer(ServiceId.APPLETV, OfferType.BUY, 9.99, "HD"),
                new Offer(ServiceId.YOUTUBE, OfferType.BUY, 9.99, "HD"));
        Offer best = Offers.bestOffer(t, PROTOTYPE);
        assertEquals(ServiceId.APPLETV, best.service);
        assertEquals("Apple TV · Buy $9.99", Offers.offerLabel(best, PROTOTYPE, table));
        assertEquals("Buy $9.99", Offers.availability(best, PROTOTYPE));
    }

    @Test
    public void aSubscriptionTheHouseholdLacksIsNotAvailable() {
        Title mitchells = t("mitchells");
        assertFalse(Offers.isIncluded(mitchells, NONE));
        assertNull(Offers.bestOffer(mitchells, NONE));
        assertEquals("Not available", Offers.offerLabel(null, NONE, table));
        assertEquals("", Offers.availability(null, NONE));
        // Asked directly, such an offer says what it needs rather than "Included".
        assertEquals("Netflix · Subscription", Offers.offerLabel(mitchells.offers.get(0), NONE, table));
    }

    @Test
    public void includedServicesComeFirstOtherwiseInOrder() {
        Title t = title(new Offer(ServiceId.APPLETV, OfferType.RENT, 3.99, "4K"),
                new Offer(ServiceId.APPLETV, OfferType.BUY, 9.99, "4K"),
                new Offer(ServiceId.GOOGLETV, OfferType.RENT, 2.99, null),
                new Offer(ServiceId.NETFLIX, OfferType.SUB, 0, "4K · HDR"),
                new Offer(ServiceId.YOUTUBE, OfferType.FREE, 0, "HD"));
        List<ServiceOffers> by = Offers.byService(t, EnumSet.of(ServiceId.NETFLIX));
        assertEquals("netflix:i:::4K · HDR youtube:f:::HD appletv::3.99:9.99:4K googletv::2.99::HD", format(by));
        assertTrue(by.get(0).watchable());
        assertFalse(by.get(2).watchable());
        assertSame(t.offers.get(0), by.get(2).rent);
        // Without Netflix only the free service moves up.
        assertEquals("youtube:f:::HD appletv::3.99:9.99:4K googletv::2.99::HD netflix::::4K · HDR",
                format(Offers.byService(t, NONE)));
    }

    @Test
    public void cheapestKeepsTheFirstOfEqualPrices() {
        Title t = title(new Offer(ServiceId.APPLETV, OfferType.RENT, 3.99, null),
                new Offer(ServiceId.GOOGLETV, OfferType.RENT, 3.99, null));
        assertEquals(ServiceId.APPLETV, Offers.cheapest(t, OfferType.RENT).service);
        assertNull(Offers.cheapest(t, OfferType.BUY));
    }

    private static Title title(Offer... offers) {
        return new Title("test", Kind.MOVIE, "Test", 2020, Collections.singletonList("Drama"), 90, "PG", 7, "",
                "arrival", Arrays.asList(offers), null, null, null, null, 0, 0);
    }
}
