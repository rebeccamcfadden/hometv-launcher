package com.hometv.launcher;

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
import java.util.function.Consumer;

/**
 * The installed apps the launcher can open, and Home's app row stored on the TV (AppRow is its pure logic). An app
 * counts when it has a TV entry point (the leanback launcher category) or, failing that, a phone launcher entry;
 * this launcher and hidden_apps never show.
 */
final class HomeApps {
    private static final String PREFS = "home_apps";
    private static final String KEY_ORDER = "order";
    /** F35: apps moved into or out of All Apps' TV Utilities folder by hand (TvUtilities.encode). */
    private static final String KEY_UTILITY_MOVES = "utility_moves";

    static final class App {
        final String pkg;
        final String label;
        /** It came on the TV's system image (also when updated since). */
        final boolean system;

        App(String pkg, String label, boolean system) {
            this.pkg = pkg;
            this.label = label;
            this.system = system;
        }
    }

    private final Context ctx;
    private final List<String> dockApps;
    private final Set<String> hidden;
    private final Map<String, String> labels;
    private Set<String> watchApps;

    HomeApps(Context ctx, Config cfg) {
        this.ctx = ctx;
        dockApps = cfg.dockApps;
        hidden = new HashSet<>(cfg.hiddenApps);
        labels = cfg.appLabels;
    }

    /** Whether an app can be on the row or in All Apps: openable, not hidden and not this launcher. */
    boolean shown(String pkg) {
        return !hidden.contains(pkg) && !pkg.equals(ctx.getPackageName()) && launchable(ctx).test(pkg);
    }

    /** One per process, so it outlives a recreate(); MainActivity's package receiver keeps it current. Main thread. */
    private static Launchable launchable;

    private static Launchable launchable(Context ctx) {
        if (launchable == null) {
            PackageManager pm = ctx.getApplicationContext().getPackageManager();
            launchable = new Launchable(pkg -> launchIntent(pm, pkg) != null, pkg -> systemLabel(pm, pkg));
        }
        return launchable;
    }

    /** An app was installed, updated, enabled, disabled or removed. */
    static void forget(Context ctx, String pkg) {
        launchable(ctx).forget(pkg);
    }

    /** Apps may have changed while no receiver was listening, or a whole storage's apps came or went. */
    static void forgetAll(Context ctx) {
        launchable(ctx).forgetAll();
    }

    /** Tells l which app's launchability or name may have changed (null: any); Home listens while it is on screen. */
    static void listen(Context ctx, Consumer<String> l) {
        launchable(ctx).listen(l);
    }

    static void unlisten(Context ctx, Consumer<String> l) {
        launchable(ctx).unlisten(l);
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
        Map<String, App> found = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (found.containsKey(pkg) || hidden.contains(pkg) || pkg.equals(ctx.getPackageName())) continue;
                boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                found.put(pkg, new App(pkg, label(pkg, ri.loadLabel(pm)), system));
            }
        }
        List<App> out = new ArrayList<>(found.values());
        Collator collator = Collator.getInstance();
        Collections.sort(out, (a, b) -> {
            int c = collator.compare(a.label, b.label);
            return c != 0 ? c : a.pkg.compareTo(b.pkg);
        });
        return out;
    }

    /** The packages of these apps that are in All Apps' TV Utilities folder (TvUtilities). */
    Set<String> utilities(List<App> list) {
        Map<String, Boolean> moves = utilityMoves();
        Set<String> out = new HashSet<>();
        for (App a : list) {
            if (TvUtilities.inFolder(a.pkg, a.system, moves.get(a.pkg), watchApps())) out.add(a.pkg);
        }
        return out;
    }

    /** Moves an app into the folder or out of it by hand; a move the rule would make anyway is forgotten. */
    void moveUtility(App a, boolean in) {
        boolean byRule = TvUtilities.byRule(a.pkg, a.system, watchApps());
        String text = TvUtilities.encode(TvUtilities.move(utilityMoves(), a.pkg, in, byRule));
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_UTILITY_MOVES, text).apply();
    }

    private Map<String, Boolean> utilityMoves() {
        return TvUtilities.decode(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_UTILITY_MOVES, null));
    }

    private Set<String> watchApps() {
        if (watchApps == null) {
            List<String> providers = new ArrayList<>();
            for (WatchProviders.Entry e : new WatchProviders().all()) providers.addAll(e.packages);
            watchApps = TvUtilities.watchApps(providers);
        }
        return watchApps;
    }

    /** The app's name, or the one app_labels gives it. */
    String label(String pkg) {
        String own = labels.get(pkg);
        return own != null ? own : launchable(ctx).label(pkg);
    }

    private static String systemLabel(PackageManager pm, String pkg) {
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
        return launchIntent(ctx.getPackageManager(), pkg);
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }
}
