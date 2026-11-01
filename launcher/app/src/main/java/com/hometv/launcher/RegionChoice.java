package com.hometv.launcher;

import android.content.Context;

import org.json.JSONObject;

/**
 * The TV's region ({@link Region}; Family install step 2): the one chosen on the TV wins, saved in {@link
 * Config#SETTINGS_NAME}; then config.xml's {@code region}, overlaid by a pushed config.json like every other key; then
 * Sweden. Read once when Home's data is made ({@link Data#create}), so a change shows once Home is rebuilt. {@link
 * #pick} is pure and unit-tested (RegionChoiceTest).
 */
final class RegionChoice {
    private RegionChoice() {}

    static final String KEY = "region";

    /** The saved choice when it names a region, else the configured one, else {@link Region#DEFAULT}. */
    static Region pick(String saved, String configured) {
        Region r = Region.parse(saved);
        return r != null ? r : Region.orDefault(configured);
    }

    static Region get(Context ctx) {
        String saved = ctx.getApplicationContext().getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, null);
        String configured = ctx.getResources().getString(R.string.region);
        JSONObject json = Config.overlay(ctx);
        if (json != null) configured = json.optString(KEY, configured);
        return pick(saved, configured);
    }

    /** Saves the TV's choice; Home shows it once rebuilt. */
    static void set(Context ctx, Region region) {
        ctx.getApplicationContext().getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY, region.code).apply();
    }
}
