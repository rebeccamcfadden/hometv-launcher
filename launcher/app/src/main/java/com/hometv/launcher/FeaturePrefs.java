package com.hometv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Launcher settings › Features' switches as saved on the TV, over this TV's config (Features decides what they mean). */
final class FeaturePrefs {
    private FeaturePrefs() {}

    /** The features First run switched off as it ended, "tmdb,notes": they start on again when it opens (F34). */
    private static final String KEY_FIRST_RUN_OFF = "features_first_run_off";
    /** Phone notes' own switch before F34, in Notes.PREFS. */
    private static final String OLD_NOTES_ON = "on";
    /** migrateNotesSwitch has run on this TV. */
    private static final String KEY_NOTES_MIGRATED = "features_notes_migrated";

    /**
     * This TV's config as last read (config.xml with a pushed config.json): read once as the launcher is built, since
     * reading config.json means a file read (and a copy) each time. Main thread.
     */
    private static Map<String, Boolean> configRead;

    /** The switches as the launcher is built with them: the config read again, and the household's choices. */
    static Features load(Context ctx) {
        configRead = config(ctx);
        migrateNotesSwitch(ctx);
        return Features.resolve(saved(ctx), configRead);
    }

    /**
     * Phone notes' own On/Off, saved apart until F34, is now its feature's switch. On a TV updated from before, the
     * feature is saved as what ran then: its feature switch (saved, else a pushed config.json's, else on, the old
     * default) and its own switch (saved, else a pushed config.json's note_inbox, else off). So an update neither stops a
     * working inbox nor opens the port by itself. A fresh install has nothing to carry over. Once per TV.
     */
    private static void migrateNotesSwitch(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.getBoolean(KEY_NOTES_MIGRATED, false)) return;
        SharedPreferences.Editor e = p.edit().putBoolean(KEY_NOTES_MIGRATED, true);
        SharedPreferences old = ctx.getSharedPreferences(Notes.PREFS, Context.MODE_PRIVATE);
        if (!FirstRunPage.freshInstall(ctx)) {
            JSONObject json = Config.overlay(ctx);
            JSONObject pushed = json == null ? null : json.optJSONObject("features");
            String key = Features.Feature.NOTES.key();
            boolean feature = p.contains(key) ? p.getBoolean(key, true)
                    : pushed == null || !(pushed.opt(Features.Feature.NOTES.id) instanceof Boolean)
                    || pushed.optBoolean(Features.Feature.NOTES.id);
            boolean own = old.contains(OLD_NOTES_ON) ? old.getBoolean(OLD_NOTES_ON, false)
                    : json != null && json.optBoolean("note_inbox", false);
            e.putBoolean(key, feature && own);
        }
        // Nor does a First run opened again: Phone notes now starts off (#98 would switch it back on).
        Set<Features.Feature> firstRunOff = Features.decode(p.getString(KEY_FIRST_RUN_OFF, null));
        if (firstRunOff.remove(Features.Feature.NOTES)) e.putString(KEY_FIRST_RUN_OFF, Features.encode(firstRunOff));
        e.commit();
        if (old.contains(OLD_NOTES_ON)) old.edit().remove(OLD_NOTES_ON).apply();
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
        out.put(Features.Feature.REMOTE.id, res.getBoolean(R.bool.feature_remote));
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
        Set<Features.Feature> firstRunOff = Features.decode(p.getString(KEY_FIRST_RUN_OFF, null));
        SharedPreferences.Editor e = p.edit().putBoolean(f.key(), on)
                .putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1);
        // The household chose: First run no longer switches it back on.
        if (firstRunOff.remove(f)) e.putString(KEY_FIRST_RUN_OFF, Features.encode(firstRunOff));
        e.apply();
        stopNotesIfOff(ctx);
        // F27: the phone remote's server opens or closes with its switch at once, whichever page switched it.
        if (f == Features.Feature.REMOTE) PhoneRemote.get(ctx).sync();
    }

    /**
     * Phone notes switched off, or TMDB account it needs (F34): the inbox closes at once, not at the next rebuild, so the
     * TV stops taking notes as its pane says Off. Switching it on rebuilds the launcher, which opens it ({@link #runs}).
     */
    private static void stopNotesIfOff(Context ctx) {
        if (!Features.inboxAllowed(live(ctx))) Notes.get(ctx).allow(false);
    }

    /**
     * Whether a feature runs in the launcher now: as it was built, except Phone notes, which runs while its inbox is
     * allowed (switched off since, it closed at once). What a Turn on's rebuild is judged by.
     */
    static boolean runs(Context ctx, Features.Feature f) {
        return f == Features.Feature.NOTES ? Notes.get(ctx).on() : Features.isOn(f);
    }

    /**
     * First run ended without the feature's setup and switches it off (FirstRunPage.switchOffUnfinished): saved off, and
     * remembered as First run's doing, not the household's (F34).
     */
    static void switchOffUnfinished(Context ctx, Features.Feature f) {
        SharedPreferences p = prefs(ctx);
        Set<Features.Feature> firstRunOff = Features.decode(p.getString(KEY_FIRST_RUN_OFF, null));
        firstRunOff.add(f);
        p.edit().putBoolean(f.key(), false)
                .putString(KEY_FIRST_RUN_OFF, Features.encode(firstRunOff))
                .putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
        stopNotesIfOff(ctx);
    }

    /**
     * First run opens again (F34): every feature it switched off itself, as it ended, starts on again, so its Features
     * step shows them on and leads into their setups; one the household switched off stays off. Saved at once
     * (commit), since the page reads the switches as it is built. Returns whether any came back on.
     */
    static boolean restoreFirstRunOff(Context ctx) {
        SharedPreferences p = prefs(ctx);
        Set<Features.Feature> firstRunOff = Features.decode(p.getString(KEY_FIRST_RUN_OFF, null));
        if (firstRunOff.isEmpty()) return false;
        SharedPreferences.Editor e = p.edit().remove(KEY_FIRST_RUN_OFF)
                .putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1);
        for (Features.Feature f : firstRunOff) e.putBoolean(f.key(), true);
        e.commit();
        return true;
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
