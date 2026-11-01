package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/** Launcher settings › Performance's choices as saved on the TV (PerfChoices decides what they mean). */
final class PerfPrefs {
    private PerfPrefs() {}

    static PerfChoices load(Context ctx, Config cfg) {
        // Only this page's keys, read one by one rather than copying every preference with getAll().
        SharedPreferences p = prefs(ctx);
        Map<String, Object> saved = new HashMap<>();
        if (p.contains(PerfChoices.KEY_SHADOWS)) saved.put(PerfChoices.KEY_SHADOWS, p.getString(PerfChoices.KEY_SHADOWS, null));
        if (p.contains(PerfChoices.KEY_GLOW)) saved.put(PerfChoices.KEY_GLOW, p.getBoolean(PerfChoices.KEY_GLOW, true));
        if (p.contains(PerfChoices.KEY_BACKDROP)) saved.put(PerfChoices.KEY_BACKDROP, p.getString(PerfChoices.KEY_BACKDROP, null));
        if (p.contains(PerfChoices.KEY_DETAILS)) saved.put(PerfChoices.KEY_DETAILS, p.getBoolean(PerfChoices.KEY_DETAILS, true));
        return PerfChoices.resolve(saved, cfg.lightDefaults);
    }

    static void saveShadows(Context ctx, PerfChoices.Shadows s) {
        commit(ctx, prefs(ctx).edit().putString(PerfChoices.KEY_SHADOWS, s.code));
    }

    static void saveGlow(Context ctx, boolean on) {
        commit(ctx, prefs(ctx).edit().putBoolean(PerfChoices.KEY_GLOW, on));
    }

    static void saveBackdrop(Context ctx, boolean plain) {
        commit(ctx, prefs(ctx).edit().putString(PerfChoices.KEY_BACKDROP,
                plain ? PerfChoices.BACKDROP_PLAIN : PerfChoices.BACKDROP_PHOTO));
    }

    static void saveDetails(Context ctx, boolean on) {
        commit(ctx, prefs(ctx).edit().putBoolean(PerfChoices.KEY_DETAILS, on));
    }

    /** Saves every choice at once (the pane's Light mode button). */
    static void saveAll(Context ctx, PerfChoices c) {
        commit(ctx, prefs(ctx).edit().putString(PerfChoices.KEY_SHADOWS, c.shadows.code)
                .putBoolean(PerfChoices.KEY_GLOW, c.glow)
                .putString(PerfChoices.KEY_BACKDROP, c.plainBackdrop ? PerfChoices.BACKDROP_PLAIN : PerfChoices.BACKDROP_PHOTO)
                .putBoolean(PerfChoices.KEY_DETAILS, c.details));
    }

    /** Saves, and moves Launcher settings' change count so Home is rebuilt with it. */
    private static void commit(Context ctx, SharedPreferences.Editor e) {
        SharedPreferences p = prefs(ctx);
        e.putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
