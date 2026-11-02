package com.hometv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertSame;

/** F14: when Movies, TV Shows and Search show "needs a TMDB account" instead of themselves. */
public class TmdbGateTest {

    @Test
    public void switchedOffAlwaysSaysSo() {
        assertSame(TmdbGate.Need.SWITCHED_OFF, TmdbGate.need(false, true, true));
        assertSame(TmdbGate.Need.SWITCHED_OFF, TmdbGate.need(false, false, false));
    }

    @Test
    public void onButNotSignedInAsksToSignIn() {
        assertSame(TmdbGate.Need.SIGN_IN, TmdbGate.need(true, true, false));
        assertSame(TmdbGate.Need.NONE, TmdbGate.need(true, true, true));
    }

    @Test
    public void aBuildWithoutATokenRunsOnTheMockCatalogue() {
        // Nothing to sign in to (the emulator, CI): the pages show.
        assertSame(TmdbGate.Need.NONE, TmdbGate.need(true, false, false));
    }
}
