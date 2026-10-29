package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Sunrise/sunset maths. */
public class SunTest {

    private static final ZoneId VILNIUS = ZoneId.of("Europe/Vilnius");

    @Test
    public void vilniusWinterDayIsShortAndLate() {
        int[] t = Sun.riseAndSet(LocalDate.of(2026, 12, 21), 54.69, 25.28, VILNIUS);
        assertTrue("sunrise " + t[0], t[0] > 8 * 60 + 30 && t[0] < 9 * 60);      // about 08:40
        assertTrue("sunset " + t[1], t[1] > 15 * 60 + 40 && t[1] < 16 * 60 + 5);  // about 15:55
    }

    @Test
    public void vilniusSummerDayIsLongInLocalSummerTime() {
        int[] t = Sun.riseAndSet(LocalDate.of(2026, 6, 21), 54.69, 25.28, VILNIUS);
        assertTrue("sunrise " + t[0], t[0] > 4 * 60 + 30 && t[0] < 4 * 60 + 55);  // about 04:42
        assertTrue("sunset " + t[1], t[1] > 21 * 60 + 50 && t[1] < 22 * 60 + 10); // about 22:00
    }

    @Test
    public void noSunriseOrSunsetAtThePoles() {
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 12, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")));
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 6, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")));
    }
}
