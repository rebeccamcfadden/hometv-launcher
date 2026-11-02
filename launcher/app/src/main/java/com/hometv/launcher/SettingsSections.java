package com.hometv.launcher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every section of Launcher settings, in {@link SettingsNav#ORDER}: the one registry the page reads. A section whose
 * feature this build leaves out is not listed: Phone notes while the launcher runs without a TMDB key, Open on start
 * unless open_on_start_choice is on. Account is listed either way, and without a key says how to add one
 * (AccountKeyGate, F16). A feature switched off in Features (F14) keeps its section, which says so
 * (FeatureOffSection). First run's TMDB key and Price keys tabs are {@link #setupOnly}.
 */
final class SettingsSections {
    private SettingsSections() {}

    static List<SettingsSection> all(Context ctx) {
        MainActivity home = ctx instanceof MainActivity ? (MainActivity) ctx : null;
        Config config = home != null ? home.config() : Config.load(ctx);
        List<SettingsSection> out = new ArrayList<>();
        // A launcher built with a TMDB key (the TV's own, F16, or the build's) has Phone notes; Features can switch
        // Account and Phone notes off, and then they say so.
        boolean tmdbKey = home != null && home.data().accounts.available();
        if (home != null) {
            out.add(new FeatureOffSection(new AccountKeyGate(new AccountSection(home), home), Features.Feature.TMDB));
        }
        if (tmdbKey) out.add(new FeatureOffSection(new NotesSection(home), Features.Feature.NOTES));
        if (home != null) out.add(new ServicesSection(home));
        // Price lookup off: Stores says so (the project lead, 2026-11-02), as API keys' price part does.
        if (home != null) out.add(new FeatureOffSection(new StoresSection(home), Features.Feature.PRICES));
        // Never switched off itself: the catalogue needs the TMDB key whatever Price lookup is (F16).
        out.add(KeysSection.all());
        out.add(new FeatureOffSection(new WeatherSection(), Features.Feature.WEATHER));
        if (home != null) out.add(new HomeAppsSection(new HomeApps(home, config)));
        out.add(new ThemeSection());
        out.add(new PerformanceSection(config));
        out.add(new FeaturesSection());
        if (config.openOnStartChoice) out.add(new OpenOnStartSection());
        out.add(new DefaultHomeSection());
        out.add(new RegionSection());
        out.add(new TvSettingsSection());
        out.add(new AboutSection());
        out.sort(Comparator.comparingInt(s -> SettingsNav.ORDER.indexOf(s.id())));
        return out;
    }

    /**
     * First run's sections that Launcher settings doesn't list (F16): the TMDB key tab, before Sign in on a TV
     * without a working key, and the Price keys tab, Price lookup's setup. Launcher settings has both in API keys.
     */
    static List<SettingsSection> setupOnly() {
        List<SettingsSection> out = new ArrayList<>();
        out.add(KeysSection.tmdb());
        out.add(KeysSection.prices());
        return out;
    }

    /** Opens another app's screen from a pane (the TV's settings, Android's Home app picker), or says it can't. */
    static void open(Context ctx, SettingsSection.Host host, Intent intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            host.shell().toast(ctx.getString(R.string.not_available));
        }
    }
}
