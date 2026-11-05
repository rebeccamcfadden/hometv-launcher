package com.hometv.launcher;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** Which region a TV shows, and where each region's store ticks are kept. */
public class RegionChoiceTest {

    @Test
    public void theTvsChoiceWinsThenTheConfigThenSweden() {
        assertSame(Region.US, RegionChoice.pick("US", "SE"));
        assertSame(Region.SE, RegionChoice.pick("SE", "US"));
        assertSame(Region.US, RegionChoice.pick(null, "us"));
        assertSame(Region.SE, RegionChoice.pick(null, ""));
        assertSame(Region.SE, RegionChoice.pick(null, null));
        // A saved value the launcher no longer knows falls through to the config.
        assertSame(Region.US, RegionChoice.pick("GB", "US"));
        assertSame(Region.SE, RegionChoice.pick("GB", "XX"));
    }

    @Test
    public void firstRunSuggestsTheTvsOwnCountryWhenNothingNamesOne() {
        assertSame(Region.US, RegionChoice.suggestion(null, "", Locale.US));
        assertSame(Region.US, RegionChoice.suggestion(null, null, new Locale("es", "us")));
        // Sweden, or a country the launcher doesn't have, is what pick gives already: nothing to save.
        assertNull(RegionChoice.suggestion(null, "", new Locale("sv", "SE")));
        assertNull(RegionChoice.suggestion(null, "", Locale.UK));
        assertNull(RegionChoice.suggestion(null, "", new Locale("en")));
        assertNull(RegionChoice.suggestion(null, "", null));
        // A saved region or a configured one still wins; an unknown one doesn't count.
        assertNull(RegionChoice.suggestion("SE", "", Locale.US));
        assertNull(RegionChoice.suggestion(null, "SE", Locale.US));
        assertSame(Region.US, RegionChoice.suggestion("GB", "XX", Locale.US));
    }

    @Test
    public void swedenKeepsItsOldStoresKey() {
        assertEquals("ticked_stores", ServiceChoices.storesKey(Region.SE));
        assertEquals("ticked_stores_us", ServiceChoices.storesKey(Region.US));
    }
}
