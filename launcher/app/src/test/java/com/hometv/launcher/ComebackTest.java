package com.hometv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComebackTest {
    private static final long MIN = 60 * 1000L;

    @Test
    public void aTripToAnAppGoesBack() {
        assertTrue("a rebuild right away", Comeback.fresh(1000, 0, 1000, 0));
        assertTrue("a film", Comeback.fresh(1000, 5000, 1000 + 59 * MIN, 5000));
        assertTrue("an hour exactly", Comeback.fresh(1000, 0, 1000 + Comeback.AWAY_MS, 0));
    }

    @Test
    public void moreThanAnHourAwayStartsAgain() {
        assertFalse(Comeback.fresh(1000, 0, 1001 + Comeback.AWAY_MS, 0));
    }

    @Test
    public void standbyStartsAgain() {
        // The uptime clock stopped for two minutes while the elapsed one went on: the TV slept.
        assertFalse(Comeback.fresh(1000, 5000, 1000 + 3 * MIN, 5000 + 2 * MIN));
        // A moment of deep sleep is not standby.
        assertTrue(Comeback.fresh(1000, 5000, 1000 + 3 * MIN, 5000 + 30 * 1000));
    }

    @Test
    public void aRestartedClockStartsAgain() {
        assertFalse(Comeback.fresh(50 * MIN, 0, 10 * MIN, 0));
    }

    @Test
    public void onlyAPlaceFromThisBootInstallAndTaskComesBack() {
        String apk = "/data/app/com.hometv.launcher-abc==/base.apk";
        assertTrue(Comeback.sameRun(12, 12, apk, apk, 88, 88));
        assertTrue("a TV without a boot count", Comeback.sameRun(-1, -1, apk, apk, 88, 88));
        assertFalse("restarted", Comeback.sameRun(12, 13, apk, apk, 88, 88));
        assertFalse("installed again", Comeback.sameRun(12, 12, apk, "/data/app/com.hometv.launcher-xyz==/base.apk", 88, 88));
        assertFalse("force-stopped or crashed: a new Home task", Comeback.sameRun(12, 12, apk, apk, 88, 107));
        assertFalse(Comeback.sameRun(12, 12, null, apk, 88, 88));
    }

    @Test
    public void theSleepReadingMayWobbleAMillisecond() {
        // elapsedRealtime and uptimeMillis are read and rounded separately: the gap can read 1 ms lower.
        assertTrue(Comeback.fresh(1000, 5000, 2000, 4999));
        assertFalse(Comeback.fresh(1000, 5000, 2000, 5000 - 2 * Comeback.JITTER_MS));
    }
}
