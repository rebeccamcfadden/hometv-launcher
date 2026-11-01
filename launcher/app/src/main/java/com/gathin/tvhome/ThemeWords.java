package com.gathin.tvhome;

import java.time.LocalDate;

/**
 * The Theme pane's wording, chosen without Android so it is tested on the JVM: each theme's and each period's name,
 * and what the Theme section's row says ("Midnight", or "Vector art · Halloween" for a Seasonal theme on 30 October).
 * The names live here only (strings_theme_pane.xml); Themes keeps the English names a resolved Theme carries.
 */
final class ThemeWords {
    private ThemeWords() {}

    /** The theme's name, or 0 for an id the picker doesn't know (the pane then shows Theme.name). */
    static int nameRes(String id) {
        if (id == null) return 0;
        switch (id) {
            case "midnight":
                return R.string.theme_name_midnight;
            case "daylight":
                return R.string.theme_name_daylight;
            case "oled":
                return R.string.theme_name_oled;
            case "high-contrast":
                return R.string.theme_name_high_contrast;
            case "cinema":
                return R.string.theme_name_cinema;
            case "landscapes":
                return R.string.theme_name_landscapes;
            case "seasonal-vector":
                return R.string.theme_name_vector;
            case "seasonal-8bit":
                return R.string.theme_name_8bit;
            default:
                return 0;
        }
    }

    static int periodRes(SeasonCalendar.Period period) {
        switch (period) {
            case SNOW:
                return R.string.theme_period_snow;
            case THAW:
                return R.string.theme_period_thaw;
            case SPRING_RAIN:
                return R.string.theme_period_spring_rain;
            case BLOOM:
                return R.string.theme_period_bloom;
            case PRIDE:
                return R.string.theme_period_pride;
            case MIDSUMMER:
                return R.string.theme_period_midsummer;
            case LATE_SUMMER:
                return R.string.theme_period_late_summer;
            case AUTUMN:
                return R.string.theme_period_autumn;
            case HALLOWEEN:
                return R.string.theme_period_halloween;
            default:
                return R.string.theme_period_christmas;
        }
    }

    /**
     * The period a Seasonal choice shows on that day, or null for a Classic one: the section's row then reads the
     * theme's name alone, else "name · period".
     */
    static SeasonCalendar.Period shownPeriod(boolean seasonal, LocalDate day) {
        return seasonal ? SeasonCalendar.at(day) : null;
    }
}
