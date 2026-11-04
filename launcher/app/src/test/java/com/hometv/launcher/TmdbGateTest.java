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
        assertSame(TmdbGate.Need.NONE, TmdbGate.need(true, true, true, false));
        assertSame(TmdbGate.Need.NONE, TmdbGate.need(true, true, true, true));
    }

    @Test
    public void aMockCatalogueBuildWithoutAKeyShowsThePages() {
        // -PmockCatalogue=true (the emulator, tests): nothing to sign in to, the mock catalogue shows.
        assertSame(TmdbGate.Need.NONE, TmdbGate.need(true, false, false, true));
    }

    @Test
    public void anyOtherBuildWithoutAKeyAsksForOne() {
        // A family TV before its key goes in, or after Remove (F16-6 (a), 2026-11-02); any build since Rebecca,
        // 2026-11-04: a TV without a key never shows the mock catalogue.
        assertSame(TmdbGate.Need.KEY, TmdbGate.need(true, false, false, false));
    }
}
