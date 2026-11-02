package com.hometv.launcher;

import org.junit.After;
import org.junit.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** F14: which features run, from the household's switches over this TV's config. */
public class FeaturesTest {

    @After
    public void reset() {
        Features.apply(null);
    }

    private static Map<String, Object> saved(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, Boolean> config(Object... kv) {
        Map<String, Boolean> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Boolean) kv[i + 1]);
        return m;
    }

    @Test
    public void nothingSavedOrConfiguredRunsEverything() {
        // Every feature starts on; First run switches off what isn't set up when it ends (2026-11-02).
        Features f = Features.resolve(null, null);
        for (Features.Feature x : Features.Feature.values()) assertTrue(x.id, f.on(x));
        assertEquals(4, f.onCount());
        assertTrue(f.same(Features.ALL_ON));
    }

    @Test
    public void theConfigSwitchesOnAndTheHouseholdsChoiceWins() {
        Features f = Features.resolve(saved("feature_notes", false, "feature_weather", true),
                config("tmdb", true, "notes", true, "prices", false, "weather", false));
        assertTrue(f.on(Features.Feature.TMDB));
        assertFalse(f.on(Features.Feature.NOTES));
        assertTrue(f.on(Features.Feature.WEATHER));
        assertFalse(f.on(Features.Feature.PRICES));
        assertEquals(2, f.onCount());
        // Switches that are gone (Pick for me, Themes: the project lead, 2026-11-02) are ignored.
        assertNull(Features.Feature.parse("pick"));
        assertNull(Features.Feature.parse("themes"));
        assertEquals(4, Features.Feature.values().length);
    }

    @Test
    public void phoneNotesIsOffWhileTmdbAccountIs() {
        Features f = Features.resolve(saved("feature_tmdb", false, "feature_notes", true), null);
        assertTrue(f.switchedOn(Features.Feature.NOTES));
        assertFalse(f.on(Features.Feature.NOTES));
        assertSame(Features.Feature.TMDB, f.blockedBy(Features.Feature.NOTES));
        assertNull(f.blockedBy(Features.Feature.WEATHER));
        assertEquals(2, f.onCount());
        // Back on with TMDB account, Phone notes keeps its own switch.
        Features back = Features.resolve(saved("feature_tmdb", true, "feature_notes", true), null);
        assertTrue(back.on(Features.Feature.NOTES));
        assertNull(back.blockedBy(Features.Feature.NOTES));
        assertEquals(4, back.onCount());
    }

    @Test
    public void anythingButABooleanIsIgnored() {
        Features f = Features.resolve(saved("feature_prices", "off", "feature_weather", 0),
                config("prices", false));
        assertFalse(f.on(Features.Feature.PRICES));
        assertTrue(f.on(Features.Feature.WEATHER));
    }

    @Test
    public void parseReadsIdsAndKeysAreStable() {
        assertSame(Features.Feature.NOTES, Features.Feature.parse(" Notes "));
        assertNull(Features.Feature.parse("music"));
        assertNull(Features.Feature.parse(null));
        assertEquals("feature_tmdb", Features.Feature.TMDB.key());
    }

    @Test
    public void sameComparesWhatRuns() {
        Features a = Features.resolve(saved("feature_tmdb", false), config("notes", true));
        // Notes switched off too: what runs is the same, since TMDB account being off already stops it.
        Features b = Features.resolve(saved("feature_tmdb", false, "feature_notes", false), null);
        assertTrue(a.same(b));
        assertFalse(a.same(Features.ALL_OFF));
        assertFalse(a.same(Features.ALL_ON));
        assertEquals(0, Features.ALL_OFF.onCount());
        assertEquals(4, Features.ALL_ON.onCount());
    }

    @Test
    public void aSetOfFeaturesSavesInOrderAndDropsUnknownIds() {
        assertEquals("tmdb,notes", Features.encode(EnumSet.of(Features.Feature.NOTES, Features.Feature.TMDB)));
        assertEquals("", Features.encode(EnumSet.noneOf(Features.Feature.class)));
        assertEquals(EnumSet.of(Features.Feature.TMDB, Features.Feature.WEATHER),
                Features.decode(" weather,music,,TMDB "));
        assertTrue(Features.decode(null).isEmpty());
        assertTrue(Features.decode("").isEmpty());
        for (Features.Feature f : Features.Feature.values()) {
            assertEquals(EnumSet.of(f), Features.decode(Features.encode(EnumSet.of(f))));
        }
    }

    @Test
    public void currentIsWhatWasApplied() {
        assertTrue(Features.isOn(Features.Feature.WEATHER));
        Features.apply(Features.resolve(saved("feature_weather", false), null));
        assertFalse(Features.isOn(Features.Feature.WEATHER));
        Features.apply(null);
        assertSame(Features.ALL_ON, Features.current());
    }
}