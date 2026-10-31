package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;

public class SeasonCalendarTest {
    private static SeasonCalendar.Period at(int month, int day) {
        return SeasonCalendar.at(LocalDate.of(2026, month, day));
    }

    @Test
    public void monthsMapToTheirPeriods() {
        assertEquals(SeasonCalendar.Period.SNOW, at(1, 1));
        assertEquals(SeasonCalendar.Period.SNOW, at(2, 28));
        assertEquals(SeasonCalendar.Period.SNOW, SeasonCalendar.at(LocalDate.of(2028, 2, 29)));
        assertEquals(SeasonCalendar.Period.THAW, at(3, 1));
        assertEquals(SeasonCalendar.Period.SPRING_RAIN, at(4, 30));
        assertEquals(SeasonCalendar.Period.BLOOM, at(5, 15));
        assertEquals(SeasonCalendar.Period.PRIDE, at(6, 1));
        assertEquals(SeasonCalendar.Period.MIDSUMMER, at(7, 31));
        assertEquals(SeasonCalendar.Period.LATE_SUMMER, at(8, 1));
        assertEquals(SeasonCalendar.Period.AUTUMN, at(9, 1));
        assertEquals(SeasonCalendar.Period.CHRISTMAS, at(12, 31));
    }

    @Test
    public void halloweenTakesOverInsideAutumn() {
        assertEquals(SeasonCalendar.Period.AUTUMN, at(10, 14));
        assertEquals(SeasonCalendar.Period.HALLOWEEN, at(10, 15));
        assertEquals(SeasonCalendar.Period.HALLOWEEN, at(10, 31));
        assertEquals(SeasonCalendar.Period.HALLOWEEN, at(11, 2));
        assertEquals(SeasonCalendar.Period.AUTUMN, at(11, 3));
        assertEquals(SeasonCalendar.Period.AUTUMN, at(11, 30));
    }
}
