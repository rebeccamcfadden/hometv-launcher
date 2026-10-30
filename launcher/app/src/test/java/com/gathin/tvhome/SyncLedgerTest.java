package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SyncLedgerTest {
    @Test
    public void lastChangeToATitleWins() {
        SyncLedger l = new SyncLedger();
        l.record(SyncList.MY_LIST, "movie:1", true, 10);
        l.record(SyncList.MY_LIST, "movie:2", true, 11);
        l.record(SyncList.MY_LIST, "movie:1", false, 12);
        l.record(SyncList.FAVORITES, "movie:1", true, 13);
        List<SyncLedger.Op> ops = l.pending();
        assertEquals(3, ops.size());
        assertEquals("movie:2", ops.get(0).id);
        assertEquals("movie:1", ops.get(1).id);
        assertFalse(ops.get(1).present);
        assertEquals(12, ops.get(1).at);
        assertEquals(SyncList.FAVORITES, ops.get(2).list);
        assertEquals(2, l.pending(SyncList.MY_LIST).size());
    }

    @Test
    public void readOnlyListsAndMockIdsAreNotQueued() {
        SyncLedger l = new SyncLedger();
        l.record(SyncList.HOUSEHOLD, "movie:1", true, 1);
        l.record(SyncList.FRIENDS, "movie:1", true, 1);
        l.record(SyncList.MY_LIST, "arrival", true, 1);
        assertTrue(l.pending().isEmpty());
    }

    @Test
    public void queueIsCapped() {
        SyncLedger l = new SyncLedger();
        for (int i = 1; i <= SyncLedger.MAX_PENDING + 3; i++) l.record(SyncList.SEEN, "movie:" + i, true, i);
        assertEquals(SyncLedger.MAX_PENDING, l.pending().size());
        assertEquals("movie:4", l.pending().get(0).id);
    }

    @Test
    public void retryDelaysGrowToAnHour() {
        assertEquals(30_000, SyncLedger.retryDelay(1));
        assertEquals(60_000, SyncLedger.retryDelay(2));
        assertEquals(120_000, SyncLedger.retryDelay(3));
        assertEquals(1_920_000, SyncLedger.retryDelay(7));
        assertEquals(3_600_000, SyncLedger.retryDelay(8));
        assertEquals(3_600_000, SyncLedger.retryDelay(100));
    }

    @Test
    public void retryWaitsThenComesDue() {
        SyncLedger l = new SyncLedger();
        l.record(SyncList.MY_LIST, "movie:1", true, 0);
        SyncLedger.Op op = l.due(0).get(0);
        l.retry(op, 1000);
        assertTrue(l.due(1000).isEmpty());
        assertTrue(l.due(30_999).isEmpty());
        SyncLedger.Op again = l.due(31_000).get(0);
        assertEquals(1, again.attempts);
        l.retry(again, 31_000);
        assertTrue(l.due(31_000 + 59_999).isEmpty());
        assertEquals(2, l.due(91_000).get(0).attempts);
    }

    @Test
    public void pushedJoinsTheBase() {
        SyncLedger l = new SyncLedger();
        l.setBase(SyncList.MY_LIST, Arrays.asList("movie:1", "movie:2"), 5);
        l.record(SyncList.MY_LIST, "movie:1", false, 6);
        l.record(SyncList.MY_LIST, "tv:3", true, 7);
        for (SyncLedger.Op op : l.due(10)) l.pushed(op);
        assertEquals(Arrays.asList("movie:2", "tv:3"), l.base(SyncList.MY_LIST));
        assertTrue(l.pending().isEmpty());
        assertNull(l.base(SyncList.SEEN));
        assertEquals(0, l.pulledAt(SyncList.SEEN));
        assertEquals(5, l.pulledAt(SyncList.MY_LIST));
    }

    @Test
    public void dropAndKeepOnly() {
        SyncLedger l = new SyncLedger();
        l.record(SyncList.MY_LIST, "movie:1", true, 1);
        l.record(SyncList.MY_LIST, "movie:2", true, 2);
        l.record(SyncList.SEEN, "movie:3", true, 3);
        List<SyncLedger.Op> my = l.pending(SyncList.MY_LIST);
        l.keepOnly(SyncList.MY_LIST, Collections.singletonList(my.get(1)));
        assertEquals(2, l.pending().size());
        l.drop(my.get(1));
        assertEquals(1, l.pending().size());
        assertEquals(SyncList.SEEN, l.pending().get(0).list);
    }

    @Test
    public void resetForgetsEverything() {
        SyncLedger l = filled();
        l.reset("other");
        assertEquals("other", l.account());
        assertTrue(l.pending().isEmpty());
        assertNull(l.base(SyncList.MY_LIST));
        assertEquals(0, l.listId(SyncList.SEEN));
    }

    @Test
    public void jsonRoundTrip() {
        SyncLedger l = filled();
        SyncLedger r = SyncLedger.fromJson(l.toJson());
        assertEquals("obj", r.account());
        assertEquals(Arrays.asList("movie:1", "tv:2"), r.base(SyncList.MY_LIST));
        assertEquals(99, r.pulledAt(SyncList.MY_LIST));
        assertEquals(Collections.emptyList(), r.base(SyncList.SEEN));
        assertNull(r.base(SyncList.FAVORITES));
        assertEquals(77, r.listId(SyncList.SEEN));
        assertEquals(0, r.listId(SyncList.FRIENDS));
        List<SyncLedger.Op> ops = r.pending();
        assertEquals(2, ops.size());
        assertEquals(SyncList.FAVORITES, ops.get(0).list);
        assertEquals("movie:5", ops.get(0).id);
        assertTrue(ops.get(0).present);
        assertEquals(100, ops.get(0).at);
        assertEquals(1, ops.get(0).attempts);
        assertEquals(30_200, ops.get(0).notBefore);
        assertFalse(ops.get(1).present);
    }

    @Test
    public void unreadableJsonGivesAnEmptyLedger() {
        assertEquals("", SyncLedger.fromJson(null).account());
        assertEquals("", SyncLedger.fromJson("not json").account());
        assertTrue(SyncLedger.fromJson("{\"pending\":[{\"list\":\"NOPE\"}]}").pending().isEmpty());
        SyncLedger l = SyncLedger.fromJson("{\"account\":\"a\",\"bases\":{\"MY_LIST\":{\"at\":1,"
                + "\"ids\":[\"movie:1\",\"arrival\",\"movie:1\",3]}},\"pending\":[{\"list\":\"HOUSEHOLD\","
                + "\"id\":\"movie:1\",\"present\":true,\"at\":1}]}");
        assertEquals(Collections.singletonList("movie:1"), l.base(SyncList.MY_LIST));
        assertTrue(l.pending().isEmpty());
    }

    private static SyncLedger filled() {
        SyncLedger l = new SyncLedger();
        l.reset("obj");
        l.setBase(SyncList.MY_LIST, Arrays.asList("movie:1", "tv:2"), 99);
        l.setBase(SyncList.SEEN, Collections.<String>emptyList(), 98);
        l.setListId(SyncList.SEEN, 77);
        l.record(SyncList.FAVORITES, "movie:5", true, 100);
        l.retry(l.pending().get(0), 200);
        l.record(SyncList.SEEN, "tv:6", false, 101);
        return l;
    }
}
