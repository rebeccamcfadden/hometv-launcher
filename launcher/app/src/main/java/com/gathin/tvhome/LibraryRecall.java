package com.gathin.tvhome;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * What Movies and TV Shows keep while they are left (the project lead, 2026-11-01, F2): the filters, the chosen list
 * chip and the sort start cleared each time the TV is turned on, are kept while the household leaves the page and
 * comes back, and are cleared again after an hour away. Each page keeps its own.
 *
 * <p>"Turned on" is the launcher's process starting, which a boot always does, or the screen coming back from standby,
 * which on most TVs keeps the process: {@link LibraryPage} holds one of these in a static field, so it lasts as long as
 * the process and no longer, and calls {@link #turnedOff} when the screen goes off. That also carries a page's choices
 * across the activity being made again (a theme or a setting changing), within the hour. The clock is injected; the
 * page's is {@code SystemClock.elapsedRealtime()}, which counts on through standby. Pure Java, unit-tested.
 */
final class LibraryRecall {

    /** How long away still keeps the page's choices: an hour; longer clears them. */
    static final long AWAY_MS = 60 * 60 * 1000L;

    /** A page's choices as they were when it was left. */
    static final class Saved {
        /** {@link LibraryFilters#save}. */
        final String filters;
        /** {@link ShownList#key}. */
        final String list;
        final LibrarySort sort;
        final long leftAt;
        /** {@link #turnedOff} calls before it was left: a later one makes it stale. */
        final int offs;

        Saved(String filters, String list, LibrarySort sort, long leftAt, int offs) {
            this.filters = filters;
            this.list = list;
            this.sort = sort;
            this.leftAt = leftAt;
            this.offs = offs;
        }
    }

    private final LongSupplier clock;
    private final Map<Kind, Saved> saved = new EnumMap<>(Kind.class);
    private int offs;

    LibraryRecall(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * The page is left: keep its choices and the time. Leaving again before coming back (the tab, then standby) keeps
     * the first time, so the hour counts from when it was last on screen.
     */
    void left(Kind kind, String filters, String list, LibrarySort sort) {
        Saved was = saved.get(kind);
        long at = was != null ? was.leftAt : clock.getAsLong();
        int off = was != null ? was.offs : offs;
        saved.put(kind, new Saved(filters, list, sort, at, off));
    }

    /**
     * The TV went to standby (the screen off): every page left before now starts again when it is back. A page on
     * screen at the time compares {@link #offs} when the TV is back.
     */
    void turnedOff() {
        offs++;
    }

    /** How many times the TV went to standby while the process ran: a page that saw another count starts again. */
    int offs() {
        return offs;
    }

    /**
     * Back on the page, or the page made again: what it had set if it was left an hour ago or less and the TV wasn't
     * turned off since, else null (start again: no filters, All lists, A–Z). Either way the page is on screen again, so
     * nothing stays kept.
     */
    Saved back(Kind kind) {
        Saved s = saved.remove(kind);
        if (s == null || s.offs != offs) return null;
        long away = clock.getAsLong() - s.leftAt;
        return away >= 0 && away <= AWAY_MS ? s : null;
    }

    /** Whether the page was left and not yet back. */
    boolean isLeft(Kind kind) {
        return saved.containsKey(kind);
    }
}
