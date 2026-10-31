package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** The Home row apps pane: pick up, move, put down, take off, add and Back (src/screens/panes/apps.ts). */
public class HomeRowEditTest {

    private static List<String> list(String... pkgs) {
        return Arrays.asList(pkgs);
    }

    private static Predicate<String> installed(String... pkgs) {
        Set<String> set = new HashSet<>(Arrays.asList(pkgs));
        return set::contains;
    }

    @Test
    public void movesThePickedUpAppPastItsVisibleNeighbours() {
        // b.gone isn't installed: it keeps its place in the saved row and is stepped over.
        HomeRowEdit e = new HomeRowEdit(list("a", "b.gone", "c", "d"), installed("a", "c", "d"));
        assertEquals(list("a", "c", "d"), e.visible());
        assertFalse("nothing picked up", e.step(1));
        assertTrue(e.pickUp("a"));
        assertEquals("a", e.moving());
        assertTrue(e.step(1));
        assertEquals(list("c", "a", "d"), e.visible());
        assertTrue(e.step(1));
        assertFalse("at the end", e.step(1));
        assertEquals(list("c", "d", "a"), e.visible());
        assertEquals(list("b.gone", "c", "d", "a"), e.full());
        e.putDown();
        assertNull(e.moving());
        assertFalse(e.step(-1));
    }

    @Test
    public void backPutsTheRowBackAsItWas() {
        HomeRowEdit e = new HomeRowEdit(list("a", "b", "c"), installed("a", "b", "c"));
        e.pickUp("c");
        e.step(-1);
        e.step(-1);
        assertEquals(list("c", "a", "b"), e.visible());
        assertTrue(e.cancel());
        assertEquals(list("a", "b", "c"), e.full());
        assertNull(e.moving());
        assertFalse("nothing to cancel", e.cancel());
    }

    @Test
    public void downTakesItOffButOneMustStay() {
        HomeRowEdit e = new HomeRowEdit(list("a", "b"), installed("a", "b"));
        e.pickUp("a");
        assertTrue(e.takeOff());
        assertEquals(list("b"), e.visible());
        assertNull(e.moving());
        e.pickUp("b");
        assertFalse(e.takeOff());
        assertEquals(list("b"), e.visible());
        assertEquals("still picked up", "b", e.moving());
    }

    @Test
    public void anotherAppJoinsAtTheEndPickedUpAndBackTakesItOffAgain() {
        HomeRowEdit e = new HomeRowEdit(list("a", "b"), installed("a", "b", "x"));
        assertFalse("already on the row", e.add("a"));
        assertTrue(e.add("x"));
        assertEquals(list("a", "b", "x"), e.visible());
        assertEquals("x", e.moving());
        assertTrue(e.step(-1));
        assertEquals(list("a", "x", "b"), e.visible());
        e.cancel();
        assertEquals(list("a", "b"), e.full());
    }

    @Test
    public void onlyAnAppOnTheRowCanBePickedUp() {
        HomeRowEdit e = new HomeRowEdit(list("a", "b.gone"), installed("a", "x"));
        assertFalse(e.pickUp("x"));
        assertFalse(e.pickUp("b.gone"));
        assertNull(e.moving());
    }

    @Test
    public void addingStopsAtSeven() {
        HomeRowEdit e = new HomeRowEdit(list("a", "b", "c", "d", "e", "f", "g"),
                installed("a", "b", "c", "d", "e", "f", "g", "x"));
        assertFalse(e.add("x"));
        assertEquals(7, e.visible().size());
        assertNull(e.moving());
    }
}