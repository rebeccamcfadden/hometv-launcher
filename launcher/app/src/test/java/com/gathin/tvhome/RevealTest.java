package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** How far a scrolling row moves to keep its focused item in view. */
public class RevealTest {

    @Test
    public void anItemInViewDoesNotMove() {
        assertEquals(0f, Reveal.offset(0f, 100, 300, 92, 1828), 0f);
        assertEquals(-200f, Reveal.offset(-200f, 400, 600, 92, 1828), 0f);
    }

    @Test
    public void anItemPastTheEndPullsTheRowJustEnough() {
        // The eighth app at 1744..1980 against a view ending at 1828.
        assertEquals(-152f, Reveal.offset(0f, 1744, 1980, 92, 1828), 0f);
    }

    @Test
    public void anItemBeforeTheStartPushesTheRowBack() {
        assertEquals(-8f, Reveal.offset(-200f, 100, 300, 92, 1828), 0f);
    }

    @Test
    public void neverPastTheRowsBeginning() {
        assertEquals(0f, Reveal.offset(-50f, 92, 300, 92, 1828), 0f);
        assertEquals(0f, Reveal.offset(-50f, 10, 300, 92, 1828), 0f);
    }
}
