package com.gathin.tvhome;

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
    /** "sun" (or absent) for a Seasonal picture that follows the part of the day, "day" for always day. */
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
        return !"day".equals(prefs(ctx).getString(KEY_TIME_OF_DAY, "sun"));
    }

    static void setFollowSun(Context ctx, boolean follow) {
        prefs(ctx).edit().putString(KEY_TIME_OF_DAY, follow ? "sun" : "day").apply();
    }

    /** The part of the day a phased backdrop shows now: from the sun at the Weather place, or always day. */
    static DayPhase phase(Context ctx, ZonedDateTime now) {
        if (!followSun(ctx)) return DayPhase.DAY;
        WeatherSettings w = WeatherPrefs.load(ctx);
        return DayPhase.at(now, w.latitude, w.longitude);
    }

    /** When the part of the day next changes; null while the picture always shows the day. */
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
