package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class AccountConfigTest {
    @Test
    public void defaultsKeepEverythingAsBefore() {
        AccountConfig c = AccountConfig.of("", "", "", "", false);
        assertTrue(c.shown.isEmpty());
        assertFalse(c.overridesShown());
        assertFalse("Seen stays on the TV", c.syncsSeen());
        assertFalse(c.createSeen);
    }

    @Test
    public void shownListsByNumberLinkOrName() {
        AccountConfig c = AccountConfig.of("8221116, Our picks\nhttps://www.themoviedb.org/list/9001-friends\n\n,",
                "1", "2", "Seen", true);
        List<ListRef> s = c.shown;
        assertEquals(3, s.size());
        assertEquals(8221116, s.get(0).id);
        assertEquals("Our picks", s.get(1).name);
        assertEquals(9001, s.get(2).id);
        assertTrue(c.overridesShown());
        assertTrue(c.syncsSeen());
        assertTrue(c.createSeen);
        assertEquals("Seen", c.seen.name);
    }

    @Test
    public void repeatsAndBlanksGo() {
        List<ListRef> s = AccountConfig.parseList(" 7 ,\r\n7, ours, OURS ,https://www.themoviedb.org/list/7");
        assertEquals(2, s.size());
        assertEquals(7, s.get(0).id);
        assertEquals("ours", s.get(1).name);
        assertTrue(AccountConfig.parseList(null).isEmpty());
        assertTrue(AccountConfig.parseList(" , \n ").isEmpty());
    }

    /** The older keys still work: when tmdb_shown_lists is empty, Household then Friends are the shown lists. */
    @Test
    public void householdAndFriendsKeysStillWork() {
        AccountConfig c = AccountConfig.of("", "8221116", " Friends, of ours ", "Seen", true);
        assertEquals(2, c.shown.size());
        assertEquals(8221116, c.shown.get(0).id);
        assertEquals("one list each: a comma stays in the name", "Friends, of ours", c.shown.get(1).name);
        assertEquals(1, AccountConfig.of("", "", "42", "", false).shown.size());
        assertEquals(1, AccountConfig.of("", "42", "42", "", false).shown.size());
        assertEquals("tmdb_shown_lists wins", 1, AccountConfig.of("5", "8221116", "Friends", "", false).shown.size());
    }

    @Test
    public void makingSeenNeedsAName() {
        assertFalse(AccountConfig.of("", "", "", "", true).createSeen);
    }
}
