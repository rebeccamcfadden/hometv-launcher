package com.hometv.launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackKeyTest {
    @Test public void aBackPressedAndReleasedHereGoesBack() {
        assertTrue(BackKey.acts(true, false));
    }

    /** YouTube closed on the press; only the release came back to Home. */
    @Test public void aReleaseWhosePressWentToAnotherAppDoesNothing() {
        assertFalse(BackKey.acts(false, false));
    }

    @Test public void aCancelledReleaseDoesNothing() {
        assertFalse(BackKey.acts(true, true));
    }
}
