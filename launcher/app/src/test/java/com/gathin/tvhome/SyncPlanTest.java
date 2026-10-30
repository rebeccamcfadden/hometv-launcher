package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SyncPlanTest {
    private static final long NOW = 100 * SyncPlan.STALE;

    @Test
    public void signedInShowSyncsFullyWhenStale() {
        assertEquals(SyncPlan.Run.FULL, SyncPlan.onShow(true, NOW, 0, false));
        assertEquals(SyncPlan.Run.FULL, SyncPlan.onShow(true, NOW, NOW - SyncPlan.STALE, false));
        assertEquals(SyncPlan.Run.PUSH, SyncPlan.onShow(true, NOW, NOW - SyncPlan.STALE + 1, false));
        assertEquals(SyncPlan.Run.PUSH, SyncPlan.onShow(true, NOW, NOW, true));
    }

    @Test
    public void signedOutReadsOnlyPublicLists() {
        assertEquals(SyncPlan.Run.NONE, SyncPlan.onShow(false, NOW, 0, false));
        assertEquals(SyncPlan.Run.PUBLIC, SyncPlan.onShow(false, NOW, 0, true));
        assertEquals(SyncPlan.Run.NONE, SyncPlan.onShow(false, NOW, NOW - 1000, true));
        assertEquals(SyncPlan.Run.PUBLIC, SyncPlan.onShow(false, NOW, NOW - SyncPlan.STALE, true));
    }

    @Test
    public void changesGoSoonOnlyWhenSignedInAndOnScreen() {
        assertEquals(SyncPlan.Run.PUSH, SyncPlan.onChange(true, true));
        assertEquals(SyncPlan.Run.NONE, SyncPlan.onChange(true, false));
        assertEquals(SyncPlan.Run.NONE, SyncPlan.onChange(false, true));
        assertTrue(SyncPlan.DEBOUNCE >= 5_000);
        assertFalse(SyncPlan.STALE < 10 * 60_000L);
    }

    @Test
    public void theBiggerRequestWins() {
        assertEquals(SyncPlan.Run.FULL, SyncPlan.combine(SyncPlan.Run.PUSH, SyncPlan.Run.FULL));
        assertEquals(SyncPlan.Run.FULL, SyncPlan.combine(SyncPlan.Run.FULL, SyncPlan.Run.NONE));
        assertEquals(SyncPlan.Run.PUSH, SyncPlan.combine(SyncPlan.Run.NONE, SyncPlan.Run.PUSH));
        assertEquals(SyncPlan.Run.PUBLIC, SyncPlan.combine(SyncPlan.Run.PUBLIC, SyncPlan.Run.NONE));
        assertEquals(SyncPlan.Run.NONE, SyncPlan.combine(SyncPlan.Run.NONE, SyncPlan.Run.NONE));
    }
}
