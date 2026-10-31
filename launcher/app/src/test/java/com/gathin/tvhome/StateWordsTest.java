package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** StateWords: "today 19:52", "yesterday 19:52", "on 28 Oct" and the oldest saved copy (06-states A). */
public class StateWordsTest {
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static long at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, NEW_YORK).toInstant().toEpochMilli();
    }

    private static final long NOW = at(10, 30, 21, 5);

    @Test
    public void todayIsTheTime() {
        StateWords.When w = StateWords.when(at(10, 30, 19, 52), NOW, NEW_YORK, true);
        assertEquals(StateWords.Day.TODAY, w.day);
        assertEquals("19:52", w.text);
        assertEquals("07:05", StateWords.when(at(10, 30, 7, 5), NOW, NEW_YORK, true).text);
    }

    @Test
    public void twelveHourClocks() {
        assertEquals("7:52 PM", StateWords.when(at(10, 30, 19, 52), NOW, NEW_YORK, false).text);
        assertEquals("12:05 AM", StateWords.when(at(10, 30, 0, 5), NOW, NEW_YORK, false).text);
    }

    @Test
    public void yesterdayIsTheDayBeforeInTheTvsZone() {
        StateWords.When w = StateWords.when(at(10, 29, 23, 59), NOW, NEW_YORK, true);
        assertEquals(StateWords.Day.YESTERDAY, w.day);
        assertEquals("23:59", w.text);
        // Just after midnight, a copy from late last night is yesterday's.
        StateWords.When late = StateWords.when(at(10, 29, 23, 0), at(10, 30, 0, 10), NEW_YORK, true);
        assertEquals(StateWords.Day.YESTERDAY, late.day);
    }

    @Test
    public void olderCopiesAreTheDate() {
        StateWords.When w = StateWords.when(at(10, 28, 19, 52), NOW, NEW_YORK, true);
        assertEquals(StateWords.Day.EARLIER, w.day);
        assertEquals("28 Oct", w.text);
        assertEquals("3 Sep", StateWords.when(at(9, 3, 8, 0), NOW, NEW_YORK, true).text);
    }

    @Test
    public void aCopyFromTheFutureCountsAsToday() {
        assertEquals(StateWords.Day.TODAY, StateWords.when(at(11, 2, 9, 0), NOW, NEW_YORK, true).day);
    }

    @Test
    public void nothingSavedHasNoWords() {
        assertNull(StateWords.when(0, NOW, NEW_YORK, true));
        assertNull(StateWords.when(-1, NOW, NEW_YORK, true));
    }

    @Test
    public void theOldestKnownCopy() {
        assertEquals(0, StateWords.oldest(new long[0]));
        assertEquals(0, StateWords.oldest(new long[]{0, 0}));
        assertEquals(5, StateWords.oldest(new long[]{9, 0, 5, 7}));
    }
}
