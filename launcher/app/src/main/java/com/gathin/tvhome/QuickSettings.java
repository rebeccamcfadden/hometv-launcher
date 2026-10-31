package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The nav Settings icon's quick-settings dropdown (design_preview/src/screens/quickSettings.ts; README, Top-bar
 * dropdowns; V2 design final-quick-settings, 5A and 5C combined): the Wi-Fi and Inputs dropdowns' look, 600 px wide,
 * in four groups. TV settings and Launcher settings; the TV's own Picture (display settings), Sound, Network and
 * Inputs pages; the household rows, which show a value and open their Launcher settings section; then Motion, where
 * OK steps the value in place and the menu stays open. No Sleep timer row (the project lead's decision, 2026-10-31).
 * First focus is TV settings. The remote's Settings key keeps the TV maker's panel (QuickSettingsActivity,
 * Menus.quickSettings).
 */
final class QuickSettings {
    private QuickSettings() {}

    /** The dropdown's label on the Shell's stack (the prototype's). */
    static final String LABEL = "Settings";
    /** Which Launcher settings section to open (SettingsSection ids); LauncherSettingsActivity ignores it for now. */
    static final String EXTRA_SECTION = "section";

    /**
     * A row's value, read when the menu opens and after each step. The shape of SettingsSection.value, so the new
     * Launcher settings page's sections can supply it once they land.
     */
    interface Value {
        CharSequence value(Context ctx);
    }

    /** A row whose OK steps its value in place: how many values there are, which one is on, and the step. */
    interface Stepper {
        int count();

        int index();

        void step();
    }

    static final class Row {
        final int label;
        final int icon;
        Value value;
        boolean chevron;
        boolean divider;
        Runnable action;
        Stepper stepper;

        Row(int label, int icon) {
            this.label = label;
            this.icon = icon;
        }

        /** OK closes the menu, then runs action. */
        Row opens(Runnable action) {
            this.action = action;
            return this;
        }

        /** A Launcher settings row: its value, then a chevron. */
        Row pane(Value value) {
            this.value = value;
            chevron = true;
            return this;
        }

        /** OK steps the value in place; dots show which of the values is on. */
        Row steps(Stepper stepper, Value value) {
            this.stepper = stepper;
            this.value = value;
            return this;
        }

        /** A line between groups, drawn above this row. */
        Row divider() {
            divider = true;
            return this;
        }
    }

    static Screen create(MainActivity act, View anchor) {
        Shell shell = act.shell();
        LinearLayout panel = QuickPanel.build(act, rows(act), row -> {
            shell.pop();
            row.action.run();
        });
        FrameLayout.LayoutParams lp = MenuPanel.hang(0, 0);
        lp.width = Ui.px(QuickPanel.WIDTH);
        return Dropdown.create(shell, LABEL, anchor, panel, lp);
    }

    private static List<Row> rows(MainActivity act) {
        Data data = act.data();
        List<Row> rows = new ArrayList<>();
        rows.add(page(act, R.string.qs_tv_settings, R.drawable.ic_ui_settings, Settings.ACTION_SETTINGS));
        Row launcher = new Row(R.string.qs_launcher_settings, R.drawable.ic_ui_apps)
                .opens(() -> openLauncherSettings(act, null));
        launcher.chevron = true;
        rows.add(launcher);

        // Picture opens display settings, as the TV maker's panel does; Inputs has no public settings action and opens
        // the TV's settings (BACKLOG.md).
        rows.add(page(act, R.string.qs_picture, R.drawable.ic_ui_picture, Settings.ACTION_DISPLAY_SETTINGS).divider());
        rows.add(page(act, R.string.qs_sound, R.drawable.ic_ui_sound, Settings.ACTION_SOUND_SETTINGS));
        rows.add(page(act, R.string.qs_network, R.drawable.ic_ui_wifi, Settings.ACTION_WIFI_SETTINGS));
        rows.add(page(act, R.string.qs_inputs, R.drawable.ic_ui_input, null));

        List<Row> household = new ArrayList<>();
        // Without a TMDB token the build can't sign in, and Launcher settings has no account row either.
        if (data.accounts.available()) {
            household.add(section(act, R.string.qs_account, R.drawable.ic_ui_user, "account",
                    ctx -> accountValue(ctx, data.accounts)));
        }
        household.add(section(act, R.string.qs_services, R.drawable.ic_ui_tv, "services", ctx -> {
            int n = QuickWords.servicesTicked(data.services.subscribed(), data.table);
            return ctx.getResources().getQuantityString(R.plurals.qs_services_ticked, n, n);
        }));
        household.add(section(act, R.string.qs_weather, R.drawable.ic_ui_cloud, "weather",
                QuickSettings::weatherValue));
        household.get(0).divider();
        rows.addAll(household);

        // The effective Motion shows: a TV whose config sets reduced_motion steps Reduced and Off only.
        boolean configReduced = act.config().reducedMotion;
        MotionChoice[] motion = {MotionPrefs.load(act, act.config())};
        rows.add(new Row(R.string.qs_motion, R.drawable.ic_ui_motion).divider().steps(new Stepper() {
            @Override
            public int count() {
                return MotionChoice.values().length;
            }

            @Override
            public int index() {
                return motion[0].ordinal();
            }

            @Override
            public void step() {
                motion[0] = motion[0].next(configReduced);
                MotionPrefs.save(act, motion[0]);
                // At once for what animates from here on; the change count rebuilds Home on its next resume.
                Ui.init(act, motion[0].reduced());
            }
        }, ctx -> ctx.getString(motionLabel(motion[0]))));
        return rows;
    }

    /**
     * Launcher settings, asked for one section ("account", "services", "weather") or none. The one place the
     * dropdown opens it from, to point at the new split page and its panes once they land.
     */
    static void openLauncherSettings(MainActivity act, String section) {
        Intent i = new Intent(act, LauncherSettingsActivity.class);
        if (section != null) i.putExtra(EXTRA_SECTION, section);
        act.start(i);
    }

    private static Row section(MainActivity act, int label, int icon, String section, Value value) {
        return new Row(label, icon).pane(value).opens(() -> openLauncherSettings(act, section));
    }

    private static Row page(MainActivity act, int label, int icon, String action) {
        // A fresh settings task: left in the background on another page, the settings app would come back on that
        // page instead of this one (seen on the emulator: Inputs reopened Network).
        return new Row(label, icon).opens(() -> act.start(page(act, action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)));
    }

    /** One of the TV's own settings pages, or its settings (the vendor's where it has its own) when there is none. */
    static Intent page(Context ctx, String action) {
        if (action != null) {
            Intent i = SystemSettings.intent(ctx, action);
            if (i.resolveActivity(ctx.getPackageManager()) != null) return i;
        }
        return SystemSettings.intent(ctx, Settings.ACTION_SETTINGS);
    }

    private static CharSequence accountValue(Context ctx, Accounts accounts) {
        switch (QuickWords.account(accounts.flow().phase(), accounts.signedIn())) {
            case SIGNED_IN:
                String name = accounts.username();
                return name != null && !name.isEmpty() ? name : ctx.getString(R.string.qs_account_signed_in);
            case GETTING_CODE:
                return ctx.getString(R.string.qs_account_getting_code);
            case WAITING:
                return ctx.getString(R.string.qs_account_waiting);
            case SIGNING_IN:
                return ctx.getString(R.string.qs_account_signing_in);
            case EXPIRED:
                return ctx.getString(R.string.qs_account_expired);
            default:
                return ctx.getString(R.string.qs_account_not_signed_in);
        }
    }

    private static CharSequence weatherValue(Context ctx) {
        WeatherSettings w = WeatherPrefs.load(ctx);
        return QuickWords.weather(w.place != null ? w.place.name : null, BuildConfig.WEATHER_CITY,
                ctx.getString(R.string.qs_weather_default), WeatherSettings.unitSymbol(w.fahrenheit));
    }

    private static int motionLabel(MotionChoice m) {
        switch (m) {
            case REDUCED:
                return R.string.qs_motion_reduced;
            case OFF:
                return R.string.qs_motion_off;
            default:
                return R.string.qs_motion_full;
        }
    }
}
