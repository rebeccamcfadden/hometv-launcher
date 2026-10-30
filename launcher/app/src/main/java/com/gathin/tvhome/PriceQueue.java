package com.gathin.tvhome;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The price thread's queue (PriceQueueTest): previews before lists, then first asked first. A title is queued once;
 * when a preview asks for a title whose list lookup is still waiting, {@link #boost} moves it ahead with the preview's
 * trigger, so the open preview's prices and links don't wait behind every card. A lookup already running is left
 * alone. Thread-safe; {@link #take} blocks.
 */
final class PriceQueue {

    /** One title to look up. */
    static final class Job implements Comparable<Job> {
        final String id;
        final Kind kind;
        final String name;
        final int year;
        final boolean needPrices;
        final PriceCachePolicy.Trigger trigger;
        final long seq;

        Job(String id, Kind kind, String name, int year, boolean needPrices, PriceCachePolicy.Trigger trigger, long seq) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.year = year;
            this.needPrices = needPrices;
            this.trigger = trigger;
            this.seq = seq;
        }

        boolean preview() {
            return trigger == PriceCachePolicy.Trigger.PREVIEW;
        }

        @Override
        public int compareTo(Job o) {
            if (preview() != o.preview()) return preview() ? -1 : 1;
            return Long.compare(seq, o.seq);
        }
    }

    private final PriorityQueue<Job> queue = new PriorityQueue<>();
    private final Map<String, Job> queued = new HashMap<>();
    private long seq;

    /** Queues a lookup; false when the title is already queued (see {@link #boost}). */
    synchronized boolean add(String id, Kind kind, String name, int year, boolean needPrices,
                             PriceCachePolicy.Trigger trigger) {
        if (queued.containsKey(id)) return false;
        Job j = new Job(id, kind, name, year, needPrices, trigger, ++seq);
        queue.add(j);
        queued.put(id, j);
        notifyAll();
        return true;
    }

    /**
     * A preview wants a title: its waiting list lookup becomes a preview one (asking for prices if either did), ahead
     * of every list lookup. False when the title isn't waiting (running, done, or never queued) or already is a
     * preview's.
     */
    synchronized boolean boost(String id, boolean needPrices) {
        Job old = queued.get(id);
        if (old == null || (old.preview() && (old.needPrices || !needPrices))) return false;
        queue.remove(old);
        Job j = new Job(id, old.kind, old.name, old.year, old.needPrices || needPrices,
                PriceCachePolicy.Trigger.PREVIEW, ++seq);
        queue.add(j);
        queued.put(id, j);
        return true;
    }

    synchronized boolean isQueued(String id) {
        return queued.containsKey(id);
    }

    /** The next job without waiting, or null. */
    synchronized Job poll() {
        Job j = queue.poll();
        if (j != null) queued.remove(j.id);
        return j;
    }

    /** The next job, waiting for one. */
    synchronized Job take() throws InterruptedException {
        while (queue.isEmpty()) wait();
        return poll();
    }
}
