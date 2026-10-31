package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Launcher settings' Open on start section, with open_on_start_choice on (OpenOnStart): Home, or an app that opens
 * instead of Home the first time Home comes up after the TV starts. One row each, Home first, then every app with a
 * TV entry point by name; OK ticks one. Saved at once and read on the next boot, so a new pick never opens right away.
 */
final class OpenOnStartSection implements SettingsSection {
    private View first;

    @Override
    public String id() {
        return "start";
    }

    @Override
    public int label() {
        return R.string.settings_start;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_power;
    }

    @Override
    public CharSequence value(Context ctx) {
        String pkg = prefs(ctx).getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return ctx.getString(R.string.start_home);
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    @Override
    public View pane(Context ctx, Host host) {
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_start)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.start_title), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(R.string.start_sub)), 8);
        String current = prefs(ctx).getString(OpenOnStart.KEY, "");
        List<String[]> apps = new ArrayList<>();
        apps.add(new String[]{ctx.getString(R.string.start_home), ""});
        apps.addAll(tvApps(ctx));
        if (!current.isEmpty() && !contains(apps, current)) apps.add(new String[]{value(ctx).toString(), current});
        List<View> rows = new ArrayList<>();
        first = null;
        for (String[] app : apps) {
            String pkg = app[1];
            boolean on = pkg.equals(current);
            View row = SettingsKit.checkRow(ctx, app[0], null, null, on, "start", "start-" + (pkg.isEmpty() ? "home" : pkg),
                    on, v -> {
                        if (SettingsKit.isChecked(v)) return;
                        if (pkg.isEmpty()) {
                            prefs(ctx).edit().remove(OpenOnStart.KEY).apply();
                        } else {
                            OpenOnStart.skipThisBoot(ctx);
                            prefs(ctx).edit().putString(OpenOnStart.KEY, pkg).apply();
                        }
                        for (View r : rows) SettingsKit.setChecked(r, r == v);
                        host.valueChanged();
                    });
            rows.add(row);
            SettingsKit.addWide(pane, row, rows.size() == 1 ? 26 : 8);
            if (on) first = row;
        }
        if (first == null) first = rows.get(0);
        return pane;
    }

    @Override
    public View firstFocus() {
        return first;
    }

    /** Every other app with a TV entry point, by name, once each. */
    private static List<String[]> tvApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || contains(apps, pkg)) continue;
            apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        return apps;
    }

    private static boolean contains(List<String[]> apps, String pkg) {
        for (String[] a : apps) if (a[1].equals(pkg)) return true;
        return false;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
