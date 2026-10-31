package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class ThemeWordsTest {
    private static final String[] M9_IDS = {"midnight", "daylight", "oled", "high-contrast", "cinema", "landscapes",
            "seasonal-nature", "seasonal-vector", "seasonal-8bit"};

    @Test
    public void everyPickerThemeHasItsOwnName() {
        Set<Integer> seen = new HashSet<>();
        for (String id : M9_IDS) {
            int res = ThemeWords.nameRes(id);
            assertNotEquals(id, 0, res);
            seen.add(res);
        }
        assertEquals(M9_IDS.length, seen.size());
        assertEquals(R.string.theme_name_nature, ThemeWords.nameRes("seasonal-nature"));
    }

    @Test
    public void everyRegisteredThemeIsNamed() {
        for (String id : Themes.CLASSIC) assertNotEquals(id, 0, ThemeWords.nameRes(id));
        for (String id : Themes.SEASONAL) assertNotEquals(id, 0, ThemeWords.nameRes(id));
    }

    @Test
    public void unknownIdsHaveNoName() {
        assertEquals(0, ThemeWords.nameRes("retro-crt"));
        assertEquals(0, ThemeWords.nameRes("seasonal-nature:halloween"));
        assertEquals(0, ThemeWords.nameRes(null));
    }

    @Test
    public void everyPeriodHasItsOwnName() {
        Set<Integer> seen = new HashSet<>();
        for (SeasonCalendar.Period p : SeasonCalendar.Period.values()) seen.add(ThemeWords.periodRes(p));
        assertEquals(SeasonCalendar.Period.values().length, seen.size());
        assertEquals(R.string.theme_period_halloween, ThemeWords.periodRes(SeasonCalendar.Period.HALLOWEEN));
        assertEquals(R.string.theme_period_spring_rain, ThemeWords.periodRes(SeasonCalendar.Period.SPRING_RAIN));
    }

    @Test
    public void onlySeasonalChoicesShowTodaysPeriod() {
        LocalDate halloween = LocalDate.of(2026, 10, 30);
        assertEquals(SeasonCalendar.Period.HALLOWEEN, ThemeWords.shownPeriod(true, halloween));
        assertEquals(SeasonCalendar.Period.CHRISTMAS, ThemeWords.shownPeriod(true, LocalDate.of(2026, 12, 24)));
        assertNull(ThemeWords.shownPeriod(false, halloween));
    }
}
