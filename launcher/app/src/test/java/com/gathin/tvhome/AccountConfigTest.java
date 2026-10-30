package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountConfigTest {
    @Test
    public void defaultsKeepEverythingAsBefore() {
        AccountConfig c = AccountConfig.of("", "", "", false);
        assertTrue(c.household.isNone());
        assertTrue(c.friends.isNone());
        assertFalse("Seen stays on the TV", c.syncsSeen());
        assertFalse(c.createSeen);
        AccountSync.Settings s = c.settings();
        assertTrue(s.seen.isNone());
        assertFalse(s.createSeen);
    }

    @Test
    public void listsByNumberOrName() {
        AccountConfig c = AccountConfig.of("8221116", " Friends ", "Seen", true);
        assertEquals(8221116, c.household.id);
        assertEquals(8221116, c.imported(ListId.HOUSEHOLD).id);
        assertEquals("Friends", c.imported(ListId.FRIENDS).name);
        assertTrue(c.imported(ListId.MY).isNone());
        assertTrue(c.syncsSeen());
        assertTrue(c.createSeen);
        assertEquals("Seen", c.settings().seen.name);
    }

    @Test
    public void makingSeenNeedsAName() {
        assertFalse(AccountConfig.of("", "", "", true).createSeen);
    }
}
