package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

/** The theme choices saved on the TV, next to the other Launcher settings choices. */
final class ThemeStore {
    private ThemeStore() {}

    static final String KEY_THEME = "theme";
    /** "theme" for the theme's own fonts, "system" for Roboto in every theme. */
    static final String KEY_FONT = "theme_font";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static String themeId(Context ctx) {
        String id = prefs(ctx).getString(KEY_THEME, Themes.DEFAULT_ID);
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

    /** Today's theme: the saved choice, resolved to today's period when it is Seasonal. */
    static Theme current(Context ctx) {
        return Themes.resolve(themeId(ctx), LocalDate.now());
    }
}
