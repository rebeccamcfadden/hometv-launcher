package com.hometv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
    public void swedenKeepsItsOldStoresKey() {
        assertEquals("ticked_stores", ServiceChoices.storesKey(Region.SE));
        assertEquals("ticked_stores_us", ServiceChoices.storesKey(Region.US));
    }
}
