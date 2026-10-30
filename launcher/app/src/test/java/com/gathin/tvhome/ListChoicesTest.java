package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Household and Friends chosen on the TV (docs/rework.md, Accounts, 2026-10-30), with config.xml overriding. */
public class ListChoicesTest {
    @Test
    public void okCyclesThroughTheThreeChoices() {
        assertEquals(ListChoices.Role.FRIENDS, ListChoices.Role.HOUSEHOLD.next());
        assertEquals(ListChoices.Role.NOT_USED, ListChoices.Role.FRIENDS.next());
        assertEquals(ListChoices.Role.HOUSEHOLD, ListChoices.Role.NOT_USED.next());
    }

    @Test
    public void eachRoleHoldsOneList() {
        ListChoices c = new ListChoices(0, 0);
        assertEquals(ListChoices.Role.NOT_USED, c.role(7));
        c.choose(7, ListChoices.Role.HOUSEHOLD);
        assertEquals(ListChoices.Role.HOUSEHOLD, c.role(7));
        c.choose(9, ListChoices.Role.HOUSEHOLD);
        assertEquals("the role left list 7", ListChoices.Role.NOT_USED, c.role(7));
        assertEquals(9, c.household());
        c.choose(9, ListChoices.Role.FRIENDS);
        assertEquals("a list has one role", 0, c.household());
        assertEquals(9, c.friends());
        c.choose(9, ListChoices.Role.NOT_USED);
        assertEquals(0, c.friends());
        c.choose(0, ListChoices.Role.HOUSEHOLD);
        assertEquals(0, c.household());
    }

    @Test
    public void savedNumbersAreCleanedUp() {
        ListChoices c = new ListChoices(5, 5);
        assertEquals(5, c.household());
        assertEquals(0, c.friends());
        assertEquals(0, new ListChoices(-3, 0).household());
    }

    @Test
    public void refsFromTheChoiceOrTheOverride() {
        ListChoices c = new ListChoices(7, 0);
        assertEquals(7, c.ref(ListId.HOUSEHOLD, ListRef.NONE).id);
        assertTrue(c.ref(ListId.FRIENDS, ListRef.NONE).isNone());
        assertTrue(c.ref(ListId.MY, ListRef.NONE).isNone());
        assertEquals("config.xml wins", "Ours", c.ref(ListId.HOUSEHOLD, ListRef.parse("Ours")).name);
        assertEquals(42, c.ref(ListId.FRIENDS, ListRef.parse("42")).id);
        assertEquals(7, c.ref(ListId.HOUSEHOLD, null).id);
    }
}
