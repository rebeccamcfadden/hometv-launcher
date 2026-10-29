package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Home's greeting by the hour, and the check that hides the clock until the TV has set its time. */
public class HomeClockTest {

    @Test
    public void greetingByTheHour() {
        assertEquals(HomeClock.Greeting.NIGHT, HomeClock.greeting(0));
        assertEquals(HomeClock.Greeting.NIGHT, HomeClock.greeting(4));
        assertEquals(HomeClock.Greeting.MORNING, HomeClock.greeting(5));
        assertEquals(HomeClock.Greeting.MORNING, HomeClock.greeting(11));
        assertEquals(HomeClock.Greeting.AFTERNOON, HomeClock.greeting(12));
        assertEquals(HomeClock.Greeting.AFTERNOON, HomeClock.greeting(17));
        assertEquals(HomeClock.Greeting.EVENING, HomeClock.greeting(18));
        assertEquals(HomeClock.Greeting.EVENING, HomeClock.greeting(21));
        assertEquals(HomeClock.Greeting.NIGHT, HomeClock.greeting(22));
        assertEquals(HomeClock.Greeting.NIGHT, HomeClock.greeting(23));
    }

    @Test
    public void aClockAtTheEpochIsNotPlausible() {
        assertFalse(HomeClock.plausible(0));
        // 31 December 2024, 23:59:59 UTC.
        assertFalse(HomeClock.plausible(1_735_689_599_000L));
        assertTrue(HomeClock.plausible(1_735_689_600_000L));
        // 28 October 2026.
        assertTrue(HomeClock.plausible(1_793_232_000_000L));
    }
}
