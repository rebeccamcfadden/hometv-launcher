package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A change made on the TV survives the process dying before the account's worker queued it: the household saves the
 * change and its unsent copy in one JSON, and the next sync moves the copy into the ledger before merging, so the
 * merge keeps the TV's change instead of taking TMDB's.
 */
public class UnsentChangesTest {
    private final MockCatalog catalog = new MockCatalog();

    @Test
    public void unsentChangesRoundTripWithTheLists() {
        HouseholdState s = HouseholdState.defaults(catalog);
        s.queue(SyncList.MY_LIST, "movie:1", true, 10);
        s.queue(SyncList.FAVORITES, "tv:2", false, 11);
        s.queue(SyncList.MY_LIST, "movie:1", false, 12);
        s.queue(SyncList.HOUSEHOLD, "movie:3", true, 13);
        s.queue(SyncList.MY_LIST, "arrival", true, 14);
        List<SyncLedger.Op> ops = HouseholdState.fromJson(s.toJson(), catalog).unsent();
        assertEquals(2, ops.size());
        assertEquals(SyncList.FAVORITES, ops.get(0).list);
        assertEquals("movie:1", ops.get(1).id);
        assertFalse("the newer change to movie:1 replaced the older", ops.get(1).present);
        assertEquals(12, ops.get(1).at);
    }

    @Test
    public void noUnsentChangesKeepsTheJsonAsBefore() {
        HouseholdState s = HouseholdState.defaults(catalog);
        assertFalse(s.toJson().contains("unsent"));
        assertTrue(HouseholdState.fromJson("{\"unsent\":[\"NOPE|movie:1|1|5\",\"MY_LIST|movie:1|x\",3]}", catalog)
                .unsent().isEmpty());
    }

    @Test
    public void droppingKeepsANewerChangeMadeMeanwhile() {
        HouseholdState s = HouseholdState.defaults(catalog);
        s.queue(SyncList.MY_LIST, "movie:1", true, 10);
        List<SyncLedger.Op> taken = s.unsent();
        s.queue(SyncList.MY_LIST, "movie:1", false, 20);
        assertFalse(s.dropUnsent(taken));
        assertEquals(1, s.unsent().size());
        assertTrue(s.dropUnsent(s.unsent()));
        assertTrue(s.unsent().isEmpty());
    }

    @Test
    public void aRemovalSavedBeforeTheProcessDiedIsNotResurrected() {
        // Synced: movie:1 and tv:2 on the watchlist, base read at 1000.
        SyncLedger ledger = new SyncLedger();
        ledger.reset("obj");
        ledger.setBase(SyncList.MY_LIST, Arrays.asList("movie:1", "tv:2"), 1000);
        String ledgerOnDisk = ledger.toJson();
        HouseholdState tv = HouseholdState.fromJson("{\"myList\":[\"movie:1\",\"tv:2\"]}", catalog);

        // movie:1 is taken off on the TV: the list and its unsent copy are saved together...
        tv.toggleMyList("movie:1");
        tv.queue(SyncList.MY_LIST, "movie:1", false, 2000);
        String householdOnDisk = tv.toJson();
        // ...and the process dies before the worker queued it: only what is on disk is left.
        HouseholdState after = HouseholdState.fromJson(householdOnDisk, catalog);
        SyncLedger l = SyncLedger.fromJson(ledgerOnDisk);
        assertTrue(l.pending().isEmpty());

        // The next sync moves the unsent copy into the ledger first, then merges with TMDB (still both titles).
        l.recordAll(after.unsent());
        ListMerge.Result r = ListMerge.merge(after.ids(SyncList.MY_LIST), l.base(SyncList.MY_LIST),
                l.pulledAt(SyncList.MY_LIST), Arrays.asList("movie:1", "tv:2"), l.pending(SyncList.MY_LIST));
        assertEquals(Collections.singletonList("tv:2"), r.local);
        assertEquals(1, r.keep.size());
        assertFalse(r.keep.get(0).present);
    }

    @Test
    public void anAdditionSavedBeforeTheProcessDiedIsNotLost() {
        SyncLedger ledger = new SyncLedger();
        ledger.reset("obj");
        ledger.setBase(SyncList.FAVORITES, Collections.<String>emptyList(), 1000);
        HouseholdState tv = HouseholdState.defaults(catalog);
        tv.toggleFavorite("tv:9");
        tv.queue(SyncList.FAVORITES, "tv:9", true, 2000);
        HouseholdState after = HouseholdState.fromJson(tv.toJson(), catalog);
        SyncLedger l = SyncLedger.fromJson(ledger.toJson());
        l.recordAll(after.unsent());
        ListMerge.Result r = ListMerge.merge(after.ids(SyncList.FAVORITES), l.base(SyncList.FAVORITES),
                l.pulledAt(SyncList.FAVORITES), Collections.<String>emptyList(), l.pending(SyncList.FAVORITES));
        assertEquals(Collections.singletonList("tv:9"), r.local);
        assertEquals(1, r.keep.size());
    }

    @Test
    public void anOlderCopyArrivingLateDoesNotOverrideANewerChange() {
        SyncLedger l = new SyncLedger();
        l.record(SyncList.MY_LIST, "movie:1", false, 50);
        l.recordAll(Collections.singletonList(new SyncLedger.Op(SyncList.MY_LIST, "movie:1", true, 40, 0, 0)));
        assertEquals(1, l.pending().size());
        assertFalse(l.pending().get(0).present);
        assertEquals(50, l.pending().get(0).at);
        // Moved in twice (the process died after the ledger was saved but before the copy left): one op.
        SyncLedger.Op twice = new SyncLedger.Op(SyncList.MY_LIST, "tv:2", true, 60, 0, 0);
        l.recordAll(Arrays.asList(twice, twice));
        l.recordAll(Collections.singletonList(twice));
        assertEquals(2, l.pending().size());
    }
}
