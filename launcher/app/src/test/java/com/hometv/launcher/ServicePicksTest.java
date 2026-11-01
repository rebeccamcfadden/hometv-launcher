package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The Streaming services and Stores panes' lists and ticks (design_preview/src/data/services.ts, PICK_*). */
public class ServicePicksTest {

    private static Set<ServiceId> set(ServiceId... ids) {
        return ids.length == 0 ? EnumSet.noneOf(ServiceId.class) : EnumSet.copyOf(Arrays.asList(ids));
    }

    @Test
    public void theGridIsThePrototypesOrder() {
        assertEquals(11, ServicePicks.SE_MAIN.size());
        assertEquals(ServiceId.NETFLIX, ServicePicks.SE_MAIN.get(0));
        assertEquals(ServiceId.TELIAPLAY, ServicePicks.SE_MAIN.get(10));
        assertEquals(7, ServicePicks.SE_MORE.size());
        assertEquals(ServiceId.CINEASTERNA, ServicePicks.SE_MORE.get(6));
        assertEquals(Arrays.asList(ServiceId.APPLETV, ServiceId.AMAZONVIDEO, ServiceId.VIAPLAY, ServiceId.SFANYTIME,
                ServiceId.RAKUTEN), ServicePicks.SE_STORES);
    }

    @Test
    public void freeServicesAreCaptionedAndTickable() {
        assertEquals(ServicePicks.Hint.FREE, ServicePicks.hint(ServiceId.SVTPLAY));
        assertEquals(ServicePicks.Hint.FREE_TIER, ServicePicks.hint(ServiceId.TV4PLAY));
        assertEquals(ServicePicks.Hint.FREE, ServicePicks.hint(ServiceId.PLEX));
        assertEquals(ServicePicks.Hint.NONE, ServicePicks.hint(ServiceId.NETFLIX));
        Set<ServiceId> ticked = ServicePicks.toggle(set(), ServiceId.SVTPLAY);
        assertTrue(ticked.contains(ServiceId.SVTPLAY));
        assertFalse(ServicePicks.toggle(ticked, ServiceId.SVTPLAY).contains(ServiceId.SVTPLAY));
    }

    @Test
    public void toggleTicksAndUnticksWithoutChangingTheOriginal() {
        Set<ServiceId> start = set(ServiceId.NETFLIX);
        Set<ServiceId> more = ServicePicks.toggle(start, ServiceId.MAX);
        assertEquals(set(ServiceId.NETFLIX, ServiceId.MAX), more);
        assertEquals(set(ServiceId.NETFLIX), start);
        assertEquals(set(ServiceId.MAX), ServicePicks.toggle(more, ServiceId.NETFLIX));
    }

    @Test
    public void moreStaysOpenWhileOneOfItsServicesIsTicked() {
        assertFalse(ServicePicks.moreShown(Region.SE, false, set(ServiceId.NETFLIX)));
        assertTrue(ServicePicks.moreShown(Region.SE, true, set()));
        assertTrue(ServicePicks.moreShown(Region.SE, false, set(ServiceId.MUBI)));
    }

    @Test
    public void aTickedServiceOutsideTheGridGetsATileBehindMore() {
        // subscribed_services may name Hulu; a store alone and free YouTube never get a tile.
        List<ServiceId> more = ServicePicks.more(Region.SE, set(ServiceId.HULU, ServiceId.YOUTUBE, ServiceId.APPLETV));
        assertEquals(8, more.size());
        assertEquals(ServiceId.HULU, more.get(7));
        assertTrue(ServicePicks.moreShown(Region.SE, false, set(ServiceId.HULU)));
        assertEquals(19, ServicePicks.listed(Region.SE, set(ServiceId.HULU)).size());
    }

    @Test
    public void theRowCountsTickedServicesTheGridLists() {
        Set<ServiceId> ticked = set(ServiceId.NETFLIX, ServiceId.PRIME, ServiceId.MUBI, ServiceId.YOUTUBE);
        assertEquals(2, ServicePicks.count(ticked, ServicePicks.SE_MAIN));
        assertEquals(3, ServicePicks.count(ticked, ServicePicks.listed(Region.SE, ticked)));
    }

    @Test
    public void ticksRoundTripAsLowercaseIds() {
        assertEquals("netflix,svtplay", ServicePicks.encode(set(ServiceId.SVTPLAY, ServiceId.NETFLIX)));
        assertEquals(set(ServiceId.NETFLIX, ServiceId.SVTPLAY), ServicePicks.decode("netflix,svtplay"));
        assertEquals(set(ServiceId.NETFLIX), ServicePicks.decode(" Netflix ,nope"));
        // Nothing ticked is a choice too, unlike nothing saved.
        assertEquals("", ServicePicks.encode(set()));
        assertEquals(set(), ServicePicks.decode(""));
        assertNull(ServicePicks.decode(null));
    }

    @Test
    public void unTickedStoresLoseTheirRentAndBuyOffersOnly() {
        Offer sub = new Offer(ServiceId.VIAPLAY, OfferType.SUB, 0, null);
        Offer viaplayRent = new Offer(ServiceId.VIAPLAY, OfferType.RENT, 49, null);
        Offer appleBuy = new Offer(ServiceId.APPLETV, OfferType.BUY, 99, null);
        Offer youtubeRent = new Offer(ServiceId.YOUTUBE, OfferType.RENT, 39, null);
        Offer tele2Rent = new Offer(ServiceId.TELE2PLAY, OfferType.RENT, 39, null);
        List<Offer> offers = Arrays.asList(sub, viaplayRent, appleBuy, youtubeRent, tele2Rent);

        assertSame(offers, ServicePicks.keep(Region.SE, offers, EnumSet.copyOf(ServicePicks.SE_STORES)));
        assertEquals(Arrays.asList(sub, appleBuy, youtubeRent, tele2Rent),
                ServicePicks.keep(Region.SE, offers, set(ServiceId.APPLETV)));
        assertEquals(Arrays.asList(sub, youtubeRent, tele2Rent), ServicePicks.keep(Region.SE, offers, set()));
        assertEquals(Collections.emptyList(), ServicePicks.keep(Region.SE, Collections.emptyList(), set()));
    }

    @Test
    public void storesThePaneDoesNotListAlwaysCount() {
        Set<ServiceId> counted = ServicePicks.counted(Region.SE, EnumSet.of(ServiceId.APPLETV));
        assertTrue(counted.contains(ServiceId.APPLETV));
        assertFalse(counted.contains(ServiceId.RAKUTEN));
        assertTrue(counted.contains(ServiceId.TELE2PLAY));
        assertNull(ServicePicks.counted(Region.SE, null));
    }

    @Test
    public void eachRegionHasItsOwnLists() {
        assertSame(ServicePicks.SE_MAIN, ServicePicks.main(Region.SE));
        assertSame(ServicePicks.SE_MORE, ServicePicks.moreServices(Region.SE));
        assertSame(ServicePicks.SE_STORES, ServicePicks.stores(Region.SE));
        assertEquals(Arrays.asList(ServiceId.NETFLIX, ServiceId.PRIME, ServiceId.DISNEY, ServiceId.MAX, ServiceId.HULU,
                ServiceId.PEACOCK, ServiceId.PARAMOUNTPLUS, ServiceId.APPLETVPLUS), ServicePicks.main(Region.US));
        assertEquals(Arrays.asList(ServiceId.APPLETV, ServiceId.AMAZONVIDEO, ServiceId.FANDANGO, ServiceId.GOOGLETV),
                ServicePicks.stores(Region.US));
        assertEquals(ServiceId.TUBI, ServicePicks.moreServices(Region.US).get(0));
    }

    @Test
    public void everyListedServiceIsInTheTableAsWhatItsListSays() {
        for (Region r : Region.values()) {
            for (ServiceId s : ServicePicks.main(r)) {
                WatchProviders.Entry e = WatchProviders.TABLE.of(s);
                assertTrue(r + " " + s, e != null && (e.subscription || e.free));
            }
            for (ServiceId s : ServicePicks.moreServices(r)) {
                WatchProviders.Entry e = WatchProviders.TABLE.of(s);
                assertTrue(r + " " + s, e != null && (e.subscription || e.free) && !(e.free && e.store));
                assertFalse(r + " " + s, ServicePicks.main(r).contains(s));
            }
            for (ServiceId s : ServicePicks.stores(r)) assertTrue(r + " " + s, WatchProviders.TABLE.of(s).store);
        }
    }

    @Test
    public void aSwedishTickShowsBehindMoreInTheUs() {
        // A TV moved from SE to US keeps its ticks: Viaplay, not in the US lists, still has a tile.
        List<ServiceId> more = ServicePicks.more(Region.US, set(ServiceId.VIAPLAY, ServiceId.NETFLIX));
        assertEquals(ServicePicks.US_MORE.size() + 1, more.size());
        assertEquals(ServiceId.VIAPLAY, more.get(more.size() - 1));
        assertTrue(ServicePicks.moreShown(Region.US, false, set(ServiceId.VIAPLAY)));
        assertFalse(ServicePicks.moreShown(Region.US, false, set(ServiceId.PEACOCK)));
        assertEquals(ServicePicks.US_MAIN.size() + ServicePicks.US_MORE.size(),
                ServicePicks.listed(Region.US, set()).size());
    }

    @Test
    public void usStoresFilterByTheUsList() {
        Offer fandangoRent = new Offer(ServiceId.FANDANGO, OfferType.RENT, 399, null);
        Offer sfRent = new Offer(ServiceId.SFANYTIME, OfferType.RENT, 49, null);
        List<Offer> offers = Arrays.asList(fandangoRent, sfRent);
        // SF Anytime isn't a US store, so the US pane can't untick it; Fandango is, and unticked it goes.
        assertEquals(Collections.singletonList(sfRent), ServicePicks.keep(Region.US, offers, set(ServiceId.APPLETV)));
        assertEquals(Collections.singletonList(fandangoRent), ServicePicks.keep(Region.SE, offers, set()));
        Set<ServiceId> counted = ServicePicks.counted(Region.US, set());
        assertFalse(counted.contains(ServiceId.FANDANGO));
        assertTrue(counted.contains(ServiceId.SFANYTIME));
    }
}
