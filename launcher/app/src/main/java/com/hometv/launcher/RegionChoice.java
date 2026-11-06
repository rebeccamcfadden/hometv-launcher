package com.hometv.launcher;

import android.content.Context;

import org.json.JSONObject;

import java.util.Locale;

/**
 * The TV's region ({@link Region}; Family install step 2): the one chosen on the TV wins, saved in {@link
 * Config#SETTINGS_NAME}; then config.xml's {@code region}, overlaid by a pushed config.json like every other key; then
 * Sweden. First run, opened afresh, saves the TV's own country when neither names one ({@link #suggest}). Read once
 * when Home's data is made ({@link Data#create}), so a change shows once Home is rebuilt. {@link #pick} and {@link
 * #suggestion} are pure and unit-tested (RegionChoiceTest).
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

    /**
     * First run's suggestion (Rebecca, 2026-11-06): the TV's own country ({@code locale}) when the launcher has it, or
     * null for none to save. Only when nothing saved or configured names a region, and only when it differs from what
     * {@link #pick} gives already, so a TV in Sweden or elsewhere saves nothing and stays as it was. Pure; RegionChoiceTest.
     */
    static Region suggestion(String saved, String configured, Locale locale) {
        if (Region.parse(saved) != null || Region.parse(configured) != null || locale == null) return null;
        Region guess = Region.parse(locale.getCountry());
        return guess == null || guess == pick(saved, configured) ? null : guess;
    }

    /** Saves {@link #suggestion} for this TV, if there is one; true when it did, so Home is rebuilt with it. */
    static boolean suggest(Context ctx) {
        String saved = ctx.getApplicationContext().getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, null);
        String configured = ctx.getResources().getString(R.string.region);
        JSONObject json = Config.overlay(ctx);
        if (json != null) configured = json.optString(KEY, configured);
        Region guess = suggestion(saved, configured, Locale.getDefault());
        if (guess == null) return false;
        set(ctx, guess);
        return true;
    }
}
