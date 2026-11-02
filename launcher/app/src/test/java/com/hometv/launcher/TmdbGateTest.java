package com.hometv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertSame;

/** F14: when Movies, TV Shows and Search show "needs a TMDB account" (or, F16, "needs a TMDB key") instead of themselves. */
public class TmdbGateTest {

    @Test
    public void switchedOffAlwaysSaysSo() {
        assertSame(TmdbGate.Need.SWITCHED_OFF, TmdbGate.need(false, true, true, false));
        assertSame(TmdbGate.Need.SWITCHED_OFF, TmdbGate.need(false, false, false, false));
        assertSame(TmdbGate.Need.SWITCHED_OFF, TmdbGate.need(false, false, false, true));
    }

    @Test
    public void onButNotSignedInAsksToSignIn() {
        assertSame(TmdbGate.Need.SIGN_IN, TmdbGate.need(true, true, false, false));
        assertSame(TmdbGate.Need.SIGN_IN, TmdbGate.need(true, true, false, true));
        assertSame(TmdbGate.Need.NONE, TmdbGate.need(true, true, true, true));
    }

    @Test
    public void aDebugBuildWithoutAKeyRunsOnTheMockCatalogue() {
        // Nothing to sign in to (the emulator, CI): the pages show.
        assertSame(TmdbGate.Need.NONE, TmdbGate.need(true, false, false, false));
    }

    @Test
    public void aReleaseBuildWithoutAKeyAsksForOne() {
        // A family TV before its key goes in, or after Remove (F16-6 (a), 2026-11-02).
        assertSame(TmdbGate.Need.KEY, TmdbGate.need(true, false, false, true));
    }
}
