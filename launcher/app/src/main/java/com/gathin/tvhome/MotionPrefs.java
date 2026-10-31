package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Motion as saved in Launcher settings' preferences (MotionChoice decides what it means). Saving writes the old
 * Animations switch too, so LauncherSettingsActivity shows the same, and moves the settings change count, so Home
 * rebuilds with it when it next resumes (MainActivity#onResume).
 */
final class MotionPrefs {
    private MotionPrefs() {}

    /**
     * The Motion in effect: the saved choice, else what the Animations switch makes it, never Full on a TV whose
     * config sets reduced_motion (MotionChoice.resolve).
     */
    static MotionChoice load(Context ctx, Config cfg) {
        return MotionChoice.resolve(prefs(ctx).getString(MotionChoice.KEY, null), cfg.animations, cfg.reducedMotion);
    }

    static void save(Context ctx, MotionChoice m) {
        SharedPreferences p = prefs(ctx);
        p.edit().putString(MotionChoice.KEY, m.code)
                .putBoolean("animations", m.animations())
                .putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
