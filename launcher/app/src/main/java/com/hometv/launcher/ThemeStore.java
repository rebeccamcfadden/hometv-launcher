package com.hometv.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** The theme choices saved on the TV, next to the other Launcher settings choices. */
final class ThemeStore {
    private ThemeStore() {}

    static final String KEY_THEME = "theme";
    /** "theme" for the theme's own fonts, "system" for Roboto in every theme. */
    static final String KEY_FONT = "theme_font";
    /** "sun" (or absent) for a Seasonal picture that follows the part of the day, "day" or "night" to fix it (F29). */
    static final String KEY_TIME_OF_DAY = "time_of_day";
    /** Seasonal · Nature, removed for now (the project lead's decision of 2026-11-01); a TV that had it shows Vector art. */
    static final String REMOVED_NATURE = "seasonal-nature";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static String themeId(Context ctx) {
        String id = prefs(ctx).getString(KEY_THEME, Themes.DEFAULT_ID);
        if (REMOVED_NATURE.equals(id)) return "seasonal-vector";
        return Themes.exists(id) ? id : Themes.DEFAULT_ID;
    }

    static void setThemeId(Context ctx, String id) {
        prefs(ctx).edit().putString(KEY_THEME, id).apply();
    }

    static boolean systemFont(Context ctx) {
        return "system".equals(prefs(ctx).getString(KEY_FONT, "theme"));
    }

    static void setSystemFont(Context ctx, boolean system) {
        prefs(ctx).edit().putString(KEY_FONT, system ? "system" : "theme").apply();
    }

    /** Whether a Seasonal theme's picture follows the sun (the default) or always shows the day (Theme › Time of day). */
    static boolean followSun(Context ctx) {
        return DayPhase.fixed(timeOfDay(ctx)) == null;
    }

    /** Theme › Time of day: "sun" (the default), "day" or "night" (F29); anything else, even a value of another type,
     *  reads as "sun". */
    static String timeOfDay(Context ctx) {
        return DayPhase.timeOfDay(prefs(ctx).getAll().get(KEY_TIME_OF_DAY));
    }

    static void setTimeOfDay(Context ctx, String value) {
        prefs(ctx).edit().putString(KEY_TIME_OF_DAY, value).apply();
    }

    /** The part of the day a phased backdrop shows now: from the sun at the Weather place, or the fixed Day or Night. */
    static DayPhase phase(Context ctx, ZonedDateTime now) {
        DayPhase fixed = DayPhase.fixed(timeOfDay(ctx));
        if (fixed != null) return fixed;
        WeatherSettings w = WeatherPrefs.load(ctx);
        return DayPhase.at(now, w.latitude, w.longitude);
    }

    /** When the part of the day next changes; null while the picture is fixed on Day or Night. */
    static ZonedDateTime nextPhaseChange(Context ctx, ZonedDateTime now) {
        if (!followSun(ctx)) return null;
        WeatherSettings w = WeatherPrefs.load(ctx);
        return DayPhase.nextChange(now, w.latitude, w.longitude);
    }

    /** Today's theme: the saved choice, resolved to today's period when it is Seasonal. */
    static Theme current(Context ctx) {
        return Themes.resolve(themeId(ctx), LocalDate.now());
    }
}
