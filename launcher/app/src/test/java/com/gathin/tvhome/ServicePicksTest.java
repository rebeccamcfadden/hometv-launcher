package com.gathin.tvhome;

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
        assertEquals(11, ServicePicks.MAIN.size());
        assertEquals(ServiceId.NETFLIX, ServicePicks.MAIN.get(0));
        assertEquals(ServiceId.TELIAPLAY, ServicePicks.MAIN.get(10));
        assertEquals(7, ServicePicks.MORE.size());
        assertEquals(ServiceId.CINEASTERNA, ServicePicks.MORE.get(6));
        assertEquals(Arrays.asList(ServiceId.APPLETV, ServiceId.AMAZONVIDEO, ServiceId.VIAPLAY, ServiceId.SFANYTIME,
                ServiceId.RAKUTEN), ServicePicks.STORES);
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
        assertFalse(ServicePicks.moreShown(false, set(ServiceId.NETFLIX)));
        assertTrue(ServicePicks.moreShown(true, set()));
        assertTrue(ServicePicks.moreShown(false, set(ServiceId.MUBI)));
    }

    @Test
    public void aTickedServiceOutsideTheGridGetsATileBehindMore() {
        // subscribed_services may name Hulu; a store alone and free YouTube never get a tile.
        List<ServiceId> more = ServicePicks.more(set(ServiceId.HULU, ServiceId.YOUTUBE, ServiceId.APPLETV));
        assertEquals(8, more.size());
        assertEquals(ServiceId.HULU, more.get(7));
        assertTrue(ServicePicks.moreShown(false, set(ServiceId.HULU)));
        assertEquals(19, ServicePicks.listed(set(ServiceId.HULU)).size());
    }

    @Test
    public void theRowCountsTickedServicesTheGridLists() {
        Set<ServiceId> ticked = set(ServiceId.NETFLIX, ServiceId.PRIME, ServiceId.MUBI, ServiceId.YOUTUBE);
        assertEquals(2, ServicePicks.count(ticked, ServicePicks.MAIN));
        assertEquals(3, ServicePicks.count(ticked, ServicePicks.listed(ticked)));
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

        assertSame(offers, ServicePicks.keep(offers, EnumSet.copyOf(ServicePicks.STORES)));
        assertEquals(Arrays.asList(sub, appleBuy, youtubeRent, tele2Rent),
                ServicePicks.keep(offers, set(ServiceId.APPLETV)));
        assertEquals(Arrays.asList(sub, youtubeRent, tele2Rent), ServicePicks.keep(offers, set()));
        assertEquals(Collections.emptyList(), ServicePicks.keep(Collections.emptyList(), set()));
    }

    @Test
    public void storesThePaneDoesNotListAlwaysCount() {
        Set<ServiceId> counted = ServicePicks.counted(EnumSet.of(ServiceId.APPLETV));
        assertTrue(counted.contains(ServiceId.APPLETV));
        assertFalse(counted.contains(ServiceId.RAKUTEN));
        assertTrue(counted.contains(ServiceId.TELE2PLAY));
        assertNull(ServicePicks.counted(null));
    }}
