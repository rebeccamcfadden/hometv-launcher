package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Where the hold-OK tile menu opens, on the 1920 × 1080 stage with the 96/54 safe area. */
public class MenuPlaceTest {
    private static final int W = 1920, H = 1080, SX = 96, SY = 54, GAP = 14, PW = 420, PH = 236;
    /** Under Home's forecast, the lowest line of the hero. */
    private static final int HERO = 700 + GAP;

    private static MenuPlace at(float l, float t, float r, float b, int ceiling) {
        return MenuPlace.of(new float[]{l, t, r, b}, PW, PH, W, H, SX, SY, GAP, ceiling);
    }

    private static void insideSafeArea(MenuPlace p) {
        assertTrue(p.left >= SX && p.left + PW <= W - SX);
        assertTrue(p.top >= SY && p.top + PH <= H - SY);
    }

    @Test
    public void underTheTileWhenThereIsRoom() {
        MenuPlace p = at(346, 300, 574, 436, HERO);
        assertEquals(MenuPlace.Side.BELOW, p.side);
        assertEquals(346, p.left);
        assertEquals(450, p.top);
    }

    @Test
    public void overTheTileWhenThatStaysUnderTheCeiling() {
        MenuPlace p = at(346, 777, 574, 913, 0);
        assertEquals(MenuPlace.Side.ABOVE, p.side);
        assertEquals(777 - GAP - PH, p.top);
    }

    @Test
    public void homesSecondAppOpensBesideItUnderTheGreeting() {
        MenuPlace p = at(346, 777, 574, 913, HERO);
        assertEquals(MenuPlace.Side.RIGHT, p.side);
        assertEquals(574 + GAP, p.left);
        assertTrue(p.top >= HERO);
        // Level with the tile: it overlaps the tile's height.
        assertTrue(p.top < 913 && p.top + PH > 777);
        insideSafeArea(p);
    }

    @Test
    public void everyColumnOfHomesRowStaysUnderTheHeroAndInsideTheSafeArea() {
        for (int col = 0; col < 7; col++) {
            float l = 96 + col * (228.6f + 22);
            MenuPlace p = at(l, 777, l + 228.6f, 913, HERO);
            assertTrue("column " + col, p.top >= HERO);
            insideSafeArea(p);
        }
    }

    @Test
    public void theLastColumnOpensToTheLeft() {
        MenuPlace p = at(1596, 777, 1824, 913, HERO);
        assertEquals(MenuPlace.Side.LEFT, p.side);
        assertEquals(1596 - GAP - PW, p.left);
        insideSafeArea(p);
    }

    @Test
    public void aTileScrolledPastTheSafeEdgeStillOpensInsideIt() {
        MenuPlace p = at(1846, 777, 2074, 913, HERO);
        assertEquals(MenuPlace.Side.LEFT, p.side);
        insideSafeArea(p);
    }

    @Test
    public void withoutAHeroTheScrolledRowOpensUnderTheTile() {
        MenuPlace p = at(346, 200, 574, 336, 0);
        assertEquals(MenuPlace.Side.BELOW, p.side);
        insideSafeArea(p);
    }

    @Test
    public void aTileWithNoRoomAroundItGetsTheMenuUnderTheCeilingAndInsideTheSafeArea() {
        MenuPlace p = at(SX, 500, W - SX, 900, HERO);
        assertTrue(p.top >= HERO);
        insideSafeArea(p);
    }

    @Test
    public void aGoneTileHandsFocusToItsNearestNeighbourAfterItFirst() {
        java.util.List<String> row = java.util.Arrays.asList("a", "b", "c", "d", "e");
        assertEquals(java.util.Arrays.asList("d", "b", "e", "a"), MenuPlace.nearest(row, "c"));
        assertEquals(java.util.Arrays.asList("d", "c", "b", "a"), MenuPlace.nearest(row, "e"));
        assertEquals(java.util.Arrays.asList("b", "c", "d", "e"), MenuPlace.nearest(row, "a"));
    }

    @Test
    public void aTileAloneOrNotInTheRowHasNoNeighbour() {
        assertTrue(MenuPlace.nearest(java.util.Collections.singletonList("a"), "a").isEmpty());
        assertTrue(MenuPlace.nearest(java.util.Arrays.asList("a", "b"), "x").isEmpty());
    }
}
