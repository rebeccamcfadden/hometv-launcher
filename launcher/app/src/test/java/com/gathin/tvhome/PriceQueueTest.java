package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The price thread's queue: previews first, and a preview moves its title's waiting list lookup ahead. */
public class PriceQueueTest {

    private static final PriceCachePolicy.Trigger BACKGROUND = PriceCachePolicy.Trigger.BACKGROUND;
    private static final PriceCachePolicy.Trigger PREVIEW = PriceCachePolicy.Trigger.PREVIEW;

    private static void list(PriceQueue q, String id) {
        assertTrue(q.add(id, Kind.MOVIE, id, 2020, true, BACKGROUND));
    }

    @Test
    public void previewsGoFirstThenInOrder() {
        PriceQueue q = new PriceQueue();
        list(q, "movie:1");
        list(q, "movie:2");
        assertTrue(q.add("movie:3", Kind.MOVIE, "c", 2020, false, PREVIEW));
        assertEquals("movie:3", q.poll().id);
        assertEquals("movie:1", q.poll().id);
        assertEquals("movie:2", q.poll().id);
        assertNull(q.poll());
    }

    @Test
    public void aQueuedListLookupIsBoostedWhenItsPreviewOpens() {
        PriceQueue q = new PriceQueue();
        for (int i = 1; i <= 30; i++) list(q, "movie:" + i);
        // The card for movie:25 queued it; now its preview opens.
        assertFalse(q.add("movie:25", Kind.MOVIE, "x", 2020, true, PREVIEW));
        assertTrue(q.boost("movie:25", true));
        PriceQueue.Job next = q.poll();
        assertEquals("movie:25", next.id);
        assertEquals(PREVIEW, next.trigger);
        assertTrue(next.needPrices);
        assertEquals("movie:1", q.poll().id);
        assertFalse(q.isQueued("movie:25"));
    }

    @Test
    public void runningOrUnknownLookupsAreLeftAlone() {
        PriceQueue q = new PriceQueue();
        list(q, "movie:1");
        list(q, "movie:2");
        PriceQueue.Job running = q.poll();
        assertEquals("movie:1", running.id);
        // movie:1 is running now: nothing to boost, and the queue keeps its order.
        assertFalse(q.boost("movie:1", true));
        assertFalse(q.boost("movie:9", true));
        assertEquals("movie:2", q.poll().id);
    }

    @Test
    public void aPreviewThatWantsPricesUpgradesALinksOnlyPreview() {
        PriceQueue q = new PriceQueue();
        assertTrue(q.add("tv:1", Kind.SHOW, "a", 2020, false, PREVIEW));
        // Already a preview's and nothing more to ask: no change.
        assertFalse(q.boost("tv:1", false));
        assertTrue(q.boost("tv:1", true));
        PriceQueue.Job j = q.poll();
        assertTrue(j.needPrices);
        assertEquals(Kind.SHOW, j.kind);
    }

    @Test
    public void takeWaitsForWork() throws Exception {
        PriceQueue q = new PriceQueue();
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                // The test fails on its own timeout.
            }
            list(q, "movie:7");
        });
        t.start();
        assertEquals("movie:7", q.take().id);
        t.join();
    }
}
