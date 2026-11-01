package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The TMDB lists shown as chips, chosen on the TV with Choose lists' Show / Hidden (docs/rework.md, Lists by name,
 * 2026-10-31), with config.xml overriding, the Household and Friends choice carried over, and the mock-or-account rule.
 */
public class ListChoicesTest {
    private static final List<Long> ACCOUNT = Arrays.asList(30L, 10L, 20L, 40L);

    @Test
    public void anyNumberShowInTheAccountsOrder() {
        ListChoices c = new ListChoices(Collections.<Long>emptyList());
        assertFalse(c.isShown(10));
        assertTrue(c.setShown(20, true, ACCOUNT));
        assertTrue(c.setShown(40, true, ACCOUNT));
        assertTrue(c.setShown(30, true, ACCOUNT));
        assertFalse("already shown", c.setShown(30, true, ACCOUNT));
        assertEquals(Arrays.asList(30L, 20L, 40L), c.ids());
        assertTrue(c.isShown(20));
        assertTrue(c.setShown(20, false, ACCOUNT));
        assertFalse(c.isShown(20));
        assertEquals(Arrays.asList(30L, 40L), c.ids());
        assertFalse(c.setShown(0, true, ACCOUNT));
        assertFalse(c.setShown(-1, true, ACCOUNT));
    }

    @Test
    public void savedAndLoaded() {
        ListChoices c = new ListChoices(Arrays.asList(7L, 9L, 7L, 0L, -3L));
        assertEquals(Arrays.asList(7L, 9L), c.ids());
        assertEquals("7,9", c.save());
        assertEquals(Arrays.asList(7L, 9L), ListChoices.load("7, 9,x", 1, 2).ids());
        assertTrue("saved as none: none", ListChoices.load("", 1, 2).ids().isEmpty());
    }

    /** A TV that chose Household and Friends starts with exactly those two shown, in that order. */
    @Test
    public void householdAndFriendsCarryOver() {
        assertEquals(Arrays.asList(50L, 9001L), ListChoices.load(null, 50, 9001).ids());
        assertEquals(Collections.singletonList(9001L), ListChoices.load(null, 0, 9001).ids());
        assertEquals(Collections.singletonList(50L), ListChoices.load(null, 50, 50).ids());
        assertTrue(ListChoices.load(null, 0, 0).ids().isEmpty());
    }

    @Test
    public void theChoiceFollowsTheAccount() {
        ListChoices c = new ListChoices(Arrays.asList(20L, 10L, 99L));
        assertTrue("a list gone from the account leaves, the rest take its order", c.follow(ACCOUNT));
        assertEquals(Arrays.asList(10L, 20L), c.ids());
        assertFalse(c.follow(ACCOUNT));
    }

    @Test
    public void refsFromTheChoiceOrTheOverride() {
        ListChoices c = new ListChoices(Arrays.asList(7L, 9L));
        List<ListRef> mine = c.refs(Collections.<ListRef>emptyList(), true);
        assertEquals(2, mine.size());
        assertEquals(7, mine.get(0).id);
        assertEquals(9, mine.get(1).id);
        assertEquals(2, c.refs(null, true).size());
        List<ListRef> config = Arrays.asList(ListRef.parse("Ours"), ListRef.parse("42"));
        assertEquals("config.xml wins", config, c.refs(config, true));
        assertEquals("config.xml's show signed out too", config, c.refs(config, false));
        assertTrue("signed out, the account's choice waits", c.refs(Collections.<ListRef>emptyList(), false).isEmpty());
    }

    /** Before any sign-in and with nothing set, the mock lists; once the TV has an account, only the shown lists. */
    @Test
    public void mockBeforeAnyAccount() {
        List<ListRef> none = Collections.emptyList();
        List<ListRef> one = Collections.singletonList(ListRef.parse("7"));
        assertTrue(ListChoices.mock(true, false, none));
        assertFalse("a list set in config.xml replaces the mock ones", ListChoices.mock(true, false, one));
        assertFalse("an account with none shown: only My list", ListChoices.mock(true, true, none));
        assertFalse(ListChoices.mock(true, true, one));
        assertTrue("a build that can't sign in keeps the mock lists", ListChoices.mock(false, true, one));
    }
}
