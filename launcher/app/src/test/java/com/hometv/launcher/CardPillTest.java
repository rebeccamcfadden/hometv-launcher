package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** F46: the service pill on a C card (CardPill) and the focused card's line (TitleText.yearScore). */
public class CardPillTest {

    @Test
    public void includedShowsATickAndTheName() {
        CardPill p = CardPill.of(new Offer(ServiceId.NETFLIX, OfferType.SUB, 0, ""), "Netflix", Region.SE);
        assertFalse(p.paid);
        assertEquals("", p.currency);
        assertEquals("Netflix", p.name);
        assertEquals("Netflix, included", p.spoken());
    }

    @Test
    public void freeIsATickToo() {
        CardPill p = CardPill.of(new Offer(ServiceId.YOUTUBE, OfferType.FREE, 0, ""), "YouTube", Region.US);
        assertFalse(p.paid);
        assertEquals("YouTube, free", p.spoken());
    }

    @Test
    public void rentOrBuyShowsTheRegionsCurrencyAndNoPrice() {
        Offer rent = new Offer(ServiceId.APPLETV, OfferType.RENT, 39, "HD");
        CardPill se = CardPill.of(rent, "Apple TV Store", Region.SE);
        assertTrue(se.paid);
        assertEquals("kr", se.currency);
        assertEquals("Apple TV Store", se.name);
        assertEquals("Apple TV Store, rent or buy", se.spoken());
        CardPill us = CardPill.of(new Offer(ServiceId.APPLETV, OfferType.BUY, 9.99, "HD"), "Apple TV Store", Region.US);
        assertEquals("$", us.currency);
        // Sweden when the region isn't known.
        assertEquals("kr", CardPill.currencyMark(null));
    }

    @Test
    public void noOfferOrNoNameNoPill() {
        assertNull(CardPill.of(null, "Netflix", Region.SE));
        assertNull(CardPill.of(new Offer(ServiceId.NETFLIX, OfferType.SUB, 0, ""), "", Region.SE));
        assertNull(CardPill.of(new Offer(ServiceId.NETFLIX, OfferType.SUB, 0, ""), null, Region.SE));
    }

    private static Title t(int year, double score, boolean detailed) {
        return new Title("x", Kind.MOVIE, "X", year, null, 0, null, score, null, null, null, null, null, null, null, 0,
                0, detailed, 0, null);
    }

    @Test
    public void theFocusedLineIsYearAndScore() {
        assertEquals("2024 · 8.5/10", TitleText.yearScore(t(2024, 8.5, true)));
        // Either part alone while the other isn't known; a detailed title's 0 score is a real score.
        assertEquals("2024", TitleText.yearScore(t(2024, 0, false)));
        assertEquals("0.0/10", TitleText.yearScore(t(0, 0, true)));
        assertEquals("", TitleText.yearScore(t(0, 0, false)));
    }
}
