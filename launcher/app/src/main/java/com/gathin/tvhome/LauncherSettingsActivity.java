package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Remote-friendly settings owned by the launcher; system settings stay one row away. Kept from the old launcher
 * until the new Launcher settings page is designed (docs/rework.md), without the rows for the old Home's grid, dock
 * and wallpapers. V2 Phase 4 adds the weather place and unit here as plain rows until that design lands; their logic
 * is in WeatherPlace, WeatherSettings and WeatherPrefs so the new page can reuse it.
 */
public final class LauncherSettingsActivity extends Activity {
    /** On-TV choices the old Home's rows saved; Follow theme still clears them. */
    private static final String[] OLD_CHOICES = Config.OLD_CHOICES;
    private SharedPreferences choices;
    private TextView animationsChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private TextView weatherPlaceChoice;
    private TextView temperatureUnitChoice;
    private boolean animations;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        animations = config.animations;

        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            choices.edit().putBoolean("animations", animations).apply();
            changed();
            refresh();
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        weatherPlaceChoice = findViewById(R.id.weatherPlaceChoice);
        weatherPlaceChoice.setOnClickListener(v ->
                WeatherPlacePicker.show(this, WeatherPrefs.load(this).place != null, place -> {
                    WeatherPrefs.setPlace(this, place);
                    changed();
                    refresh();
                    if (place != null) {
                        Toast.makeText(this, getString(R.string.weather_place_saved, place.label()),
                                Toast.LENGTH_SHORT).show();
                    }
                }));
        temperatureUnitChoice = findViewById(R.id.temperatureUnitChoice);
        temperatureUnitChoice.setOnClickListener(v -> showUnitPicker());
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            SharedPreferences.Editor edit = choices.edit().remove("animations").remove(OpenOnStart.KEY);
            for (String key : OLD_CHOICES) edit.remove(key);
            edit.commit();
            animations = Config.load(this).animations;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        // TMDB's notice, wherever its data shows: only in builds with a TMDB token.
        if (!BuildConfig.TMDB_TOKEN.isEmpty()) {
            findViewById(R.id.tmdbNotice).setVisibility(View.VISIBLE);
            // And where its Where to watch comes from: JustWatch through TMDB, Apple's prices, and any keyed source.
            StringBuilder notice = new StringBuilder(getString(R.string.prices_notice_justwatch))
                    .append('\n').append(getString(R.string.prices_notice_itunes));
            if (!PriceKeys.watchmode().isEmpty()) notice.append('\n').append(getString(R.string.prices_notice_watchmode));
            if (!PriceKeys.movieOfTheNight().isEmpty()) {
                notice.append('\n').append(getString(R.string.prices_notice_motn));
            }
            TextView prices = findViewById(R.id.priceNotice);
            prices.setText(notice);
            prices.setVisibility(View.VISIBLE);
        }
        refresh();
        animationsChoice.requestFocus();
    }

    /** Default (tv.properties' temperature_unit), °C or °F. Home's weather is fetched again in the new unit. */
    private void showUnitPicker() {
        WeatherSettings s = WeatherPrefs.load(this);
        String[] labels = {
                getString(R.string.temperature_unit_default, WeatherSettings.unitSymbol(s.defaultFahrenheit)),
                WeatherSettings.unitSymbol(false),
                WeatherSettings.unitSymbol(true)};
        int checked = s.fahrenheitChoice == null ? 0 : s.fahrenheitChoice ? 2 : 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.temperature_unit_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == checked) return;
                    WeatherPrefs.setFahrenheit(this, which == 0 ? null : which == 2);
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button.
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void refresh() {
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        WeatherSettings weather = WeatherPrefs.load(this);
        weatherPlaceChoice.setText(getString(R.string.weather_place_state, weather.place != null
                ? weather.place.label() : getString(R.string.weather_place_default_state)));
        String unit = WeatherSettings.unitSymbol(weather.fahrenheit);
        temperatureUnitChoice.setText(getString(R.string.temperature_unit_state, weather.fahrenheitChoice != null
                ? unit : getString(R.string.temperature_unit_default, unit)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        if (choices.contains("animations") || choices.contains(OpenOnStart.KEY)) return true;
        for (String key : OLD_CHOICES) if (choices.contains(key)) return true;
        return false;
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
