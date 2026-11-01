package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every section of Launcher settings, in {@link SettingsNav#ORDER}: the one registry the page reads. A section
 * whose feature this build or TV leaves out is not listed: Account and Phone notes without a TMDB token, Open on start
 * unless open_on_start_choice is on.
 */
final class SettingsSections {
    private SettingsSections() {}

    static List<SettingsSection> all(Context ctx) {
        MainActivity home = ctx instanceof MainActivity ? (MainActivity) ctx : null;
        Config config = home != null ? home.config() : Config.load(ctx);
        List<SettingsSection> out = new ArrayList<>();
        if (home != null && home.data().accounts.available()) out.add(new AccountSection(home));
        if (home != null && home.data().accounts.available()) out.add(new NotesSection(home));
        if (home != null) out.add(new ServicesSection(home));
        if (home != null) out.add(new StoresSection(home));
        out.add(new WeatherSection());
        if (home != null) out.add(new HomeAppsSection(new HomeApps(home, config)));
        out.add(new ThemeSection());
        out.add(new PerformanceSection(config));
        if (config.openOnStartChoice) out.add(new OpenOnStartSection());
        out.add(new DefaultHomeSection());
        out.add(new TvSettingsSection());
        out.add(new AboutSection());
        out.sort(Comparator.comparingInt(s -> SettingsNav.ORDER.indexOf(s.id())));
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
