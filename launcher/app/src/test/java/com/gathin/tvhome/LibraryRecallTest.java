package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** F2: when Movies and TV Shows keep what was set on them, and when they start again. */
public class LibraryRecallTest {

    private static final long MIN = 60 * 1000L;

    /** The injected clock. */
    private long now = 5 * 60 * MIN;
    private final LibraryRecall recall = new LibraryRecall(() -> now);

    private void leaveMovies() {
        recall.left(Kind.MOVIE, "genre=Drama", "my", LibrarySort.NEWEST);
    }

    @Test
    public void aTvTurnedOnStartsWithNothingKept() {
        // A boot starts the launcher's process again, and with it a new, empty recall.
        assertNull(recall.back(Kind.MOVIE));
        assertNull(recall.back(Kind.SHOW));
        assertFalse(recall.isLeft(Kind.MOVIE));
        leaveMovies();
        LibraryRecall afterBoot = new LibraryRecall(() -> now);
        assertFalse(afterBoot.isLeft(Kind.MOVIE));
        assertNull(afterBoot.back(Kind.MOVIE));
    }

    @Test
    public void keptWhenBackWithinTheHour() {
        leaveMovies();
        assertTrue(recall.isLeft(Kind.MOVIE));
        now += 59 * MIN;
        LibraryRecall.Saved s = recall.back(Kind.MOVIE);
        assertNotNull(s);
        assertEquals("genre=Drama", s.filters);
        assertEquals("my", s.list);
        assertSame(LibrarySort.NEWEST, s.sort);
        // Back on the page, nothing stays kept until it is left again.
        assertFalse(recall.isLeft(Kind.MOVIE));
        assertNull(recall.back(Kind.MOVIE));
    }

    @Test
    public void anHourExactlyIsKeptAndAMomentMoreClears() {
        leaveMovies();
        now += LibraryRecall.AWAY_MS;
        assertNotNull(recall.back(Kind.MOVIE));
        leaveMovies();
        now += LibraryRecall.AWAY_MS + 1;
        assertNull(recall.back(Kind.MOVIE));
        assertFalse(recall.isLeft(Kind.MOVIE));
        leaveMovies();
        now += 3 * 60 * MIN;
        assertNull(recall.back(Kind.MOVIE));
    }

    @Test
    public void theHourCountsFromTheFirstLeaving() {
        // The tab is left, then the page is told again 50 minutes later (its window hidden): the hour runs from the tab.
        leaveMovies();
        now += 50 * MIN;
        recall.left(Kind.MOVIE, "genre=Drama", "my", LibrarySort.NEWEST);
        now += 20 * MIN;
        assertNull(recall.back(Kind.MOVIE));
    }

    @Test
    public void standbyClearsWhatWasLeftBefore() {
        // Movies left for an app, TV Shows left for Home; the TV off and on again ten minutes later.
        leaveMovies();
        recall.left(Kind.SHOW, "", "all", LibrarySort.RATED);
        now += 5 * MIN;
        recall.turnedOff();
        now += 5 * MIN;
        assertNull(recall.back(Kind.MOVIE));
        assertNull(recall.back(Kind.SHOW));
    }

    @Test
    public void standbyCountsForThePageOnScreen() {
        // The page on screen isn't left by standby (its window stays visible); it compares the count instead.
        int seen = recall.offs();
        recall.turnedOff();
        assertTrue(recall.offs() != seen);
        assertFalse(recall.isLeft(Kind.MOVIE));
    }

    @Test
    public void leftAgainAfterStandbyStaysCleared() {
        // Left before standby, then told again after it (onLeave, then the window hidden): still from before.
        leaveMovies();
        recall.turnedOff();
        leaveMovies();
        assertNull(recall.back(Kind.MOVIE));
    }

    @Test
    public void leftAfterStandbyIsKept() {
        // The TV was off earlier; the page was shown since, and left now.
        recall.turnedOff();
        leaveMovies();
        now += 10 * MIN;
        assertNotNull(recall.back(Kind.MOVIE));
    }

    @Test
    public void aClockGoneBackClears() {
        leaveMovies();
        now -= MIN;
        assertNull(recall.back(Kind.MOVIE));
    }

    @Test
    public void eachPageKeepsItsOwn() {
        leaveMovies();
        now += 30 * MIN;
        recall.left(Kind.SHOW, "", "all", LibrarySort.RATED);
        now += 40 * MIN;
        // Movies were left 70 minutes ago, TV Shows 40.
        assertNull(recall.back(Kind.MOVIE));
        LibraryRecall.Saved shows = recall.back(Kind.SHOW);
        assertNotNull(shows);
        assertSame(LibrarySort.RATED, shows.sort);
    }
}
