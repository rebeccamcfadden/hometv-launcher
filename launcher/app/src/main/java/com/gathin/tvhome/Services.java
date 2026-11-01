package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * The household's services on this TV (docs/rework.md, Services): which ones are subscribed, so their offers read
 * "Included", and the intent that opens each one's app. Subscribed comes from the installed apps
 * ({@link ServiceTable#subscribed}), unless config.xml or a pushed config.json lists them in
 * {@code subscribed_services}; the ticks in Launcher settings' Streaming services ({@link ServiceChoices}) win over
 * both, and change it at once ({@link #choose}). Otherwise read once when {@link Data} is made.
 */
final class Services {
    private final Context ctx;
    private final ServiceTable table;
    private final Set<String> installed;
    private final Set<ServiceId> installedServices;
    private volatile Set<ServiceId> subscribed;

    private Services(Context ctx, ServiceTable table, Set<String> installed, Set<ServiceId> subscribed) {
        this.ctx = ctx;
        this.table = table;
        this.installed = installed;
        this.installedServices = table.installed(installed);
        this.subscribed = subscribed;
    }

    static Services from(Context ctx, Config cfg, ServiceTable table) {
        Context app = ctx.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        Set<String> installed = new HashSet<>();
        for (ServiceId s : ServiceId.values()) {
            for (String pkg : table.packages(s)) if (isInstalled(pm, pkg)) installed.add(pkg);
        }
        Set<ServiceId> ticked = ServiceChoices.services(app);
        return new Services(app, table, installed,
                ticked != null ? ticked : table.subscribed(installed, cfg.subscribedServices));
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

    /** The services, subscriptions and stores, whose app is installed and enabled, as read when Data was made. */
    Set<ServiceId> installed() {
        return installedServices;
    }

    /**
     * The household ticked or unticked a service in Launcher settings: saved on the TV, and the subscribed set from
     * now on. Screens built before keep what they read until they are built again.
     */
    void choose(Set<ServiceId> ticked) {
        subscribed = Collections.unmodifiableSet(ticked.isEmpty() ? EnumSet.noneOf(ServiceId.class)
                : EnumSet.copyOf(ticked));
        ServiceChoices.setServices(ctx, subscribed);
    }

    /** Whether one of the service's apps was installed (and enabled) when Home started. */
    boolean isInstalled(ServiceId id) {
        return table.isInstalled(id, installed);
    }

    String name(ServiceId id) {
        return table.name(id);
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

    /**
     * The intent that opens {@code url} (a title's page, {@link DeepLinks}) in the service's own app, limited to its
     * package so no browser picks it up; null when there is no link or no installed app of the service takes it.
     * Netflix's TV app wants to be told the link is a deep link (cleanyourtv-ui's DeepLinks.java).
     */
    Intent linkIntent(ServiceId id, String url) {
        if (url == null) return null;
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : table.packages(id)) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg);
            if (id == ServiceId.NETFLIX) i.putExtra("source", "30");
            if (i.resolveActivity(pm) != null) return i;
        }
        return null;
    }
}
