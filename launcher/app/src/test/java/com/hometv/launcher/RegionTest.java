package com.hometv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** The region setting's codes, its default and the names each API gives it. */
public class RegionTest {

    @Test
    public void swedenIsTheDefault() {
        assertSame(Region.SE, Region.DEFAULT);
        assertSame(Region.SE, Region.orDefault(null));
        assertSame(Region.SE, Region.orDefault(""));
        assertSame(Region.SE, Region.orDefault("XX"));
    }

    @Test
    public void codesParseInAnyCase() {
        assertSame(Region.US, Region.parse("US"));
        assertSame(Region.US, Region.parse(" us "));
        assertSame(Region.SE, Region.parse("se"));
        assertSame(Region.US, Region.orDefault("us"));
        assertNull(Region.parse("GB"));
        assertNull(Region.parse(null));
    }

    @Test
    public void eachApiGetsItsSpelling() {
        assertEquals("SE", Region.SE.code);
        assertEquals("se", Region.SE.country);
        assertEquals("US", Region.US.code);
        assertEquals("us", Region.US.country);
    }

    @Test
    public void swedenKeepsItsSettingsNamesTheUsAddsItsCountry() {
        assertEquals("feed_read_at", Region.SE.key("feed_read_at"));
        assertEquals("feed_read_at_us", Region.US.key("feed_read_at"));
        assertEquals("stale_marks_us", Region.US.key("stale_marks"));
    }

    @Test
    public void currenciesAgreeWithTheProviderTable() {
        for (Region r : Region.values()) assertEquals(r.code, WatchProviders.currency(r.code), r.currency);
        assertEquals("SEK", Region.SE.currency);
        assertEquals("USD", Region.US.currency);
    }
}
