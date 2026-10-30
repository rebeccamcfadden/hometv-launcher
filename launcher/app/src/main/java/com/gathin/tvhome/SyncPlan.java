package com.gathin.tvhome;

/**
 * When {@link Accounts} talks to TMDB about the lists (BACKLOG.md, TMDB sign-in and watchlist sync): only while Home
 * is on screen, never on a timer of its own. A full sync (read every list, merge, send the queue) right after
 * signing in and when Home shows if the last one is {@link #STALE} old; otherwise showing Home only sends what is
 * queued. A change on the TV is sent {@link #DEBOUNCE} after the last of a quick run of changes. Signed out, Household
 * and Friends set by list number are read on the same schedule with the app's token. Pure Java, unit-tested
 * (SyncPlanTest).
 */
final class SyncPlan {
    private SyncPlan() {}

    static final long STALE = 10 * 60_000L;
    static final long DEBOUNCE = 5_000L;

    enum Run {
        NONE,
        /** Read every list, merge, send. */
        FULL,
        /** Send the queued changes that are due. */
        PUSH,
        /** Signed out: read the public lists set by number. */
        PUBLIC
    }

    /**
     * @param lastFull when the last full sync (or public read) read something; 0 when never
     * @param publicLists whether Household or Friends is set by list number
     */
    static Run onShow(boolean signedIn, long now, long lastFull, boolean publicLists) {
        boolean stale = lastFull <= 0 || now - lastFull >= STALE;
        if (signedIn) return stale ? Run.FULL : Run.PUSH;
        return publicLists && stale ? Run.PUBLIC : Run.NONE;
    }

    /** A change on the TV: sent soon while signed in and on screen, else when Home next shows. */
    static Run onChange(boolean signedIn, boolean visible) {
        return signedIn && visible ? Run.PUSH : Run.NONE;
    }

    /** Two requests while one runs: the bigger one is what runs next. */
    static Run combine(Run a, Run b) {
        if (a == Run.FULL || b == Run.FULL) return Run.FULL;
        if (a == Run.PUSH || b == Run.PUSH) return Run.PUSH;
        if (a == Run.PUBLIC || b == Run.PUBLIC) return Run.PUBLIC;
        return Run.NONE;
    }
}
