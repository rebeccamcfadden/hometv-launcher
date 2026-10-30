package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Movie of the Night's key under either name in tv.properties, the project lead's winning. */
public class PriceKeysTest {

    @Test
    public void theProjectLeadsNameWins() {
        assertEquals("motn", PriceKeys.pick("motn", "legacy"));
        assertEquals("legacy", PriceKeys.pick("", "legacy"));
        assertEquals("legacy", PriceKeys.pick("  ", " legacy "));
        assertEquals("", PriceKeys.pick("", ""));
        assertEquals("", PriceKeys.pick(null, null));
    }
}
