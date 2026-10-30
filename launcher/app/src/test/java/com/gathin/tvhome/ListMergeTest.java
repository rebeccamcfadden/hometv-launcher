package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListMergeTest {
    private static final long PULLED = 1000;

    private static SyncLedger.Op op(String id, boolean present, long at) {
        return new SyncLedger.Op(SyncList.MY_LIST, id, present, at, 0, 0);
    }

    private static List<String> l(String... ids) {
        return Arrays.asList(ids);
    }

    @Test
    public void nothingQueuedTakesTmdb() {
        ListMerge.Result r = ListMerge.merge(l("movie:1", "movie:2"), l("movie:1", "movie:2"), PULLED,
                l("movie:2", "tv:3"), Collections.<SyncLedger.Op>emptyList());
        assertEquals(l("movie:2", "tv:3"), r.local);
        assertTrue(r.keep.isEmpty());
    }

    @Test
    public void queuedChangeIsKeptWhileTmdbIsUnchanged() {
        SyncLedger.Op add = op("tv:9", true, 500);
        SyncLedger.Op remove = op("movie:1", false, 600);
        ListMerge.Result r = ListMerge.merge(l("movie:2", "tv:9"), l("movie:1", "movie:2"), PULLED,
                l("movie:1", "movie:2"), Arrays.asList(remove, add));
        assertEquals(l("movie:2", "tv:9"), r.local);
        assertEquals("oldest first", Arrays.asList(add, remove), r.keep);
    }

    @Test
    public void changeTmdbAlreadyHasIsDone() {
        ListMerge.Result r = ListMerge.merge(l("movie:1"), l(), PULLED, l("movie:1"),
                Collections.singletonList(op("movie:1", true, 2000)));
        assertEquals(l("movie:1"), r.local);
        assertTrue(r.keep.isEmpty());
    }

    @Test
    public void tvChangeAfterTheLastReadWins() {
        // Base had movie:1; on the phone it was removed; on the TV it was taken off and put back after the last read.
        SyncLedger.Op back = op("movie:1", true, PULLED + 1);
        ListMerge.Result r = ListMerge.merge(l("movie:1"), l("movie:1"), PULLED, l(),
                Collections.singletonList(back));
        assertEquals(l("movie:1"), r.local);
        assertEquals(Collections.singletonList(back), r.keep);
    }

    @Test
    public void olderTvChangeLosesToTmdb() {
        // Queued before the last read (it kept failing); TMDB changed since, so TMDB is newer.
        ListMerge.Result r = ListMerge.merge(l("movie:1"), l("movie:1"), PULLED, l(),
                Collections.singletonList(op("movie:1", true, PULLED - 1)));
        assertEquals(l(), r.local);
        assertTrue(r.keep.isEmpty());
    }

    @Test
    public void firstSyncWithoutBase() {
        // No base: what only the TV has goes unless it is queued.
        SyncLedger.Op mine = op("movie:5", true, 10);
        ListMerge.Result r = ListMerge.merge(l("movie:4", "movie:5"), null, 0, l("tv:1"),
                Collections.singletonList(mine));
        assertEquals(l("movie:5", "tv:1"), r.local);
        assertEquals(Collections.singletonList(mine), r.keep);
    }

    @Test
    public void ordersTvFirstThenNewFromTmdb() {
        ListMerge.Result r = ListMerge.merge(l("movie:3", "movie:1"), l("movie:3", "movie:1"), PULLED,
                l("movie:1", "tv:7", "movie:3", "tv:8"), Collections.<SyncLedger.Op>emptyList());
        assertEquals(l("movie:3", "movie:1", "tv:7", "tv:8"), r.local);
    }

    @Test
    public void queuedAddNotInLocalStillShows() {
        ListMerge.Result r = ListMerge.merge(l(), l(), PULLED, l(),
                Collections.singletonList(op("movie:2", true, 2000)));
        assertEquals(l("movie:2"), r.local);
    }

    @Test
    public void newestOfTwoOpsForOneTitleCounts() {
        List<SyncLedger.Op> ops = new ArrayList<>();
        ops.add(op("movie:1", false, 2000));
        ops.add(op("movie:1", true, 1500));
        ListMerge.Result r = ListMerge.merge(l("movie:1"), l("movie:1"), PULLED, l("movie:1"), ops);
        assertEquals(l(), r.local);
        assertEquals(1, r.keep.size());
        assertEquals(2000, r.keep.get(0).at);
    }

    @Test
    public void addedOnBothSidesIsOne() {
        ListMerge.Result r = ListMerge.merge(l("tv:4"), l(), PULLED, l("tv:4"),
                Collections.singletonList(op("tv:4", true, 2000)));
        assertEquals(l("tv:4"), r.local);
        assertTrue(r.keep.isEmpty());
    }
}
