package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.HashSet;
import java.util.Set;

/**
 * The household's services on this TV (docs/rework.md, Services): which ones are subscribed, so their offers read
 * "Included", and the intent that opens each one's app. Subscribed comes from the installed apps
 * ({@link ServiceTable#subscribed}), unless config.xml or a pushed config.json lists them in
 * {@code subscribed_services}. Read once when {@link Data} is made; a settings screen is in BACKLOG.md.
 */
final class Services {
    private final Context ctx;
    private final ServiceTable table;
    private final Set<ServiceId> subscribed;

    private Services(Context ctx, ServiceTable table, Set<ServiceId> subscribed) {
        this.ctx = ctx;
        this.table = table;
        this.subscribed = subscribed;
    }

    static Services from(Context ctx, Config cfg) {
        return from(ctx, cfg, new ServiceTable());
    }

    static Services from(Context ctx, Config cfg, ServiceTable table) {
        Context app = ctx.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        Set<String> installed = new HashSet<>();
        for (ServiceId s : ServiceId.values()) {
            for (String pkg : table.packages(s)) if (isInstalled(pm, pkg)) installed.add(pkg);
        }
        return new Services(app, table, table.subscribed(installed, cfg.subscribedServices));
    }

    private static boolean isInstalled(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    Set<ServiceId> subscribed() {
        return subscribed;
    }

    boolean isSubscribed(ServiceId id) {
        return subscribed.contains(id);
    }

    String name(ServiceId id) {
        return table.name(id);
    }

    boolean isStore(ServiceId id) {
        return table.isStore(id);
    }

    ServiceTable table() {
        return table;
    }

    /** The intent that opens the service's app (its TV entry first, then the phone one), or null if not installed. */
    Intent launchIntent(ServiceId id) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : table.packages(id)) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        return null;
    }
}
