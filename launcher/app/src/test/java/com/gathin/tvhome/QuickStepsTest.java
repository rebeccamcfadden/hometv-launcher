package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The quick-settings dropdown's stepping rows: wrapping round. */
public class QuickStepsTest {

    @Test
    public void nextWrapsRound() {
        assertEquals(1, QuickSteps.next(0, 3));
        assertEquals(2, QuickSteps.next(1, 3));
        assertEquals(0, QuickSteps.next(2, 3));
    }

    @Test
    public void aBadIndexStartsAgain() {
        assertEquals(0, QuickSteps.next(-1, 3));
        assertEquals(0, QuickSteps.next(7, 3));
        assertEquals(0, QuickSteps.next(0, 0));
        assertEquals(0, QuickSteps.next(0, 1));
    }
}
