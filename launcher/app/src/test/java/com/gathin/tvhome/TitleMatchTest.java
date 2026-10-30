package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The rules for matching a store's title by name and year (iTunes search). */
public class TitleMatchTest {

    @Test
    public void namesAreNormalised() {
        assertEquals("lord of the rings the return of the king",
                TitleMatch.normalise("The Lord of the Rings: The Return of the King (2003)"));
        assertEquals("amelie", TitleMatch.normalise("Amélie"));
        assertEquals("fast and furious", TitleMatch.normalise("Fast & Furious"));
        assertEquals("wall e", TitleMatch.normalise("WALL·E"));
        assertEquals("interstellar", TitleMatch.normalise("Interstellar [2014]"));
        assertEquals("2001 a space odyssey", TitleMatch.normalise("2001: A Space Odyssey"));
        assertEquals("", TitleMatch.normalise(null));
    }

    @Test
    public void sameNameIgnoresCaseArticlesAndPunctuation() {
        assertTrue(TitleMatch.sameName("Arrival", "The Arrival"));
        assertTrue(TitleMatch.sameName("Dune: Part Two", "Dune - Part Two"));
        assertTrue(TitleMatch.sameName("Interstellar", "Interstellar (2014)"));
        assertFalse(TitleMatch.sameName("Dune", "Dune: Part Two"));
        assertFalse(TitleMatch.sameName("", ""));
    }

    @Test
    public void yearsWithinOne() {
        assertTrue(TitleMatch.yearClose(2016, 2016));
        assertTrue(TitleMatch.yearClose(2016, 2017));
        assertTrue(TitleMatch.yearClose(2016, 2015));
        assertFalse(TitleMatch.yearClose(2016, 2018));
        assertTrue(TitleMatch.yearClose(0, 2018));
        assertEquals(2016, TitleMatch.yearOf("2016-12-02T08:00:00Z"));
        assertEquals(0, TitleMatch.yearOf("soon"));
        assertEquals(0, TitleMatch.yearOf(null));
    }

    @Test
    public void movieScores() {
        PriceTitle arrival = new PriceTitle(329865, Kind.MOVIE, "Arrival", 2016);
        assertEquals(2, TitleMatch.movieScore(arrival, "Arrival", 2016, false));
        assertEquals(1, TitleMatch.movieScore(arrival, "Arrival (2016)", 2017, false));
        assertEquals(-1, TitleMatch.movieScore(arrival, "The Arrival", 1996, false));
        assertEquals(-1, TitleMatch.movieScore(arrival, "Arrival Day", 2016, false));
        // Looked up by id, a Swedish name still counts.
        assertEquals(2, TitleMatch.movieScore(arrival, "Ankomsten", 2016, true));
        assertEquals(-1, TitleMatch.movieScore(arrival, "Ankomsten", 2010, true));
    }

    @Test
    public void theOriginalNameMatchesToo() {
        PriceTitle amelie = new PriceTitle(194, Kind.MOVIE, "Amélie", "Le Fabuleux Destin d'Amélie Poulain", 2001, null);
        assertEquals(2, TitleMatch.movieScore(amelie, "Le fabuleux destin d'Amélie Poulain", 2001, false));
        assertEquals(1, TitleMatch.movieScore(amelie, "Amelie", 2002, false));
    }

    @Test
    public void seasonNamesSplit() {
        TitleMatch.SeasonName s = TitleMatch.season("Succession, Season 1");
        assertEquals("Succession", s.show);
        assertEquals(1, s.season);
        assertEquals(2, TitleMatch.season("Succession: Season 2").season);
        assertEquals(3, TitleMatch.season("Bron - Säsong 3").season);
        assertEquals("Bron", TitleMatch.season("Bron - Säsong 3").show);
        assertEquals(4, TitleMatch.season("Succession (Season 4)").season);
        assertEquals(12, TitleMatch.season("Doctor Who, Series 12").season);
        assertNull(TitleMatch.season("Succession"));
        assertNull(TitleMatch.season("Season 1"));
        assertNull(TitleMatch.season(null));
    }

    @Test
    public void seasonsMatchTheirShow() {
        PriceTitle succession = new PriceTitle(76331, Kind.SHOW, "Succession", 2018);
        assertTrue(TitleMatch.seasonMatches(succession, TitleMatch.season("Succession, Season 1"), 2018));
        assertTrue(TitleMatch.seasonMatches(succession, TitleMatch.season("Succession, Season 4"), 2023));
        assertFalse(TitleMatch.seasonMatches(succession, TitleMatch.season("Succession, Season 1"), 2005));
        assertFalse(TitleMatch.seasonMatches(succession, TitleMatch.season("Billions, Season 1"), 2018));
        assertFalse(TitleMatch.seasonMatches(succession, null, 2018));
    }
}
