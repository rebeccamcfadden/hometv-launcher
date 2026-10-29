package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The installed apps the launcher can open, and Home's app row stored on the TV (AppRow is its pure logic). An app
 * counts when it has a TV entry point (the leanback launcher category) or, failing that, a phone launcher entry;
 * this launcher and hidden_apps never show.
 */
final class HomeApps {
    private static final String PREFS = "home_apps";
    private static final String KEY_ORDER = "order";

    static final class App {
        final String pkg;
        final String label;

        App(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    private final Context ctx;
    private final List<String> dockApps;
    private final Set<String> hidden;
    private final Map<String, String> labels;

    HomeApps(Context ctx, Config cfg) {
        this.ctx = ctx;
        dockApps = cfg.dockApps;
        hidden = new HashSet<>(cfg.hiddenApps);
        labels = cfg.appLabels;
    }

    /** Whether an app can be on the row or in All Apps: openable, not hidden and not this launcher. */
    boolean shown(String pkg) {
        return !hidden.contains(pkg) && !pkg.equals(ctx.getPackageName()) && launchIntent(pkg) != null;
    }

    /** The row's full list (with apps not installed now), the household's once saved, else the seed. */
    List<String> full() {
        String text = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ORDER, null);
        return AppRow.full(AppRow.decode(text), dockApps, this::shown);
    }

    List<String> visible() {
        return AppRow.visible(full(), this::shown);
    }

    /** Saves the household's order; from now on the seed no longer applies. */
    void save(List<String> full) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ORDER, AppRow.encode(full)).apply();
    }

    boolean onRow(String pkg) {
        return visible().contains(pkg);
    }

    /** Every app with a TV entry point, then the phone apps that have none, as one list sorted by name. */
    List<App> all() {
        PackageManager pm = ctx.getPackageManager();
        Map<String, String> found = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (found.containsKey(pkg) || hidden.contains(pkg) || pkg.equals(ctx.getPackageName())) continue;
                found.put(pkg, label(pkg, ri.loadLabel(pm)));
            }
        }
        List<App> out = new ArrayList<>();
        for (Map.Entry<String, String> e : found.entrySet()) out.add(new App(e.getKey(), e.getValue()));
        Collator collator = Collator.getInstance();
        Collections.sort(out, (a, b) -> {
            int c = collator.compare(a.label, b.label);
            return c != 0 ? c : a.pkg.compareTo(b.pkg);
        });
        return out;
    }

    /** The app's name, or the one app_labels gives it. */
    String label(String pkg) {
        String own = labels.get(pkg);
        if (own != null) return own;
        PackageManager pm = ctx.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private String label(String pkg, CharSequence loaded) {
        String own = labels.get(pkg);
        return own != null ? own : loaded == null ? pkg : loaded.toString();
    }

    /** The app's TV entry point, else its phone one; null when it has neither. */
    Intent launchIntent(String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }
}
