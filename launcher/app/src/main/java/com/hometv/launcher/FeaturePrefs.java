package com.hometv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** Launcher settings › Features' switches as saved on the TV, over this TV's config (Features decides what they mean). */
final class FeaturePrefs {
    private FeaturePrefs() {}


    /**
     * This TV's config as last read (config.xml with a pushed config.json): read once as the launcher is built, since
     * reading config.json means a file read (and a copy) each time. Main thread.
     */
    private static Map<String, Boolean> configRead;

    /** The switches as the launcher is built with them: the config read again, and the household's choices. */
    static Features load(Context ctx) {
        configRead = config(ctx);
        return Features.resolve(saved(ctx), configRead);
    }

    /**
     * The switches as saved now, for Launcher settings' panes and rows while the page is open (the launcher itself runs
     * with what it was built with until the page closes): the household's choices over the config read at build time.
     */
    static Features live(Context ctx) {
        if (configRead == null) configRead = config(ctx);
        return Features.resolve(saved(ctx), configRead);
    }

    private static Map<String, Object> saved(Context ctx) {
        SharedPreferences p = prefs(ctx);
        Map<String, Object> saved = new HashMap<>();
        for (Features.Feature f : Features.Feature.values()) {
            if (p.contains(f.key())) saved.put(f.key(), p.getBoolean(f.key(), true));
        }
        return saved;
    }

    /**
     * This TV's config for each feature: config.xml's {@code feature_<id>}, then a pushed config.json's
     * {@code "features": {"<id>": true|false}}, which wins.
     */
    static Map<String, Boolean> config(Context ctx) {
        Resources res = ctx.getResources();
        Map<String, Boolean> out = new HashMap<>();
        out.put(Features.Feature.TMDB.id, res.getBoolean(R.bool.feature_tmdb));
        out.put(Features.Feature.NOTES.id, res.getBoolean(R.bool.feature_notes));
        out.put(Features.Feature.PRICES.id, res.getBoolean(R.bool.feature_prices));
        out.put(Features.Feature.WEATHER.id, res.getBoolean(R.bool.feature_weather));
        JSONObject json = Config.overlay(ctx);
        JSONObject pushed = json == null ? null : json.optJSONObject("features");
        if (pushed != null) {
            for (Features.Feature f : Features.Feature.values()) {
                Object v = pushed.opt(f.id);
                if (v instanceof Boolean) out.put(f.id, (Boolean) v);
            }
        }
        return out;
    }

    /** Saves the household's switch, and moves Launcher settings' change count so the launcher is rebuilt with it. */
    static void save(Context ctx, Features.Feature f, boolean on) {
        SharedPreferences p = prefs(ctx);
        p.edit().putBoolean(f.key(), on)
                .putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
    }

    /**
     * An empty state's Turn on (the project lead, 2026-11-02): the feature on, and the one it needs when that is off
     * (Phone notes needs TMDB account). Returns the feature to set up first: the one it needed, else itself.
     */
    static Features.Feature turnOn(Context ctx, Features.Feature f) {
        Features.Feature first = f.needs != null && !live(ctx).on(f.needs) ? f.needs : f;
        if (first != f) save(ctx, first, true);
        save(ctx, f, true);
        return first;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
