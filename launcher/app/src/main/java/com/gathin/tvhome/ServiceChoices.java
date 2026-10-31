package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The household's ticks from Launcher settings, kept on the TV in {@link Config#SETTINGS_NAME}: the streaming services
 * it pays for (Streaming services) and the stores whose prices may show (Stores for rent and buy). Both as lowercase
 * ids ({@link ServicePicks#encode}). Saved ticks win over config.xml's {@code subscribed_services} and the installed
 * apps ({@link Services#from}); with none saved, those still decide, and every store shows.
 */
final class ServiceChoices {
    private ServiceChoices() {}

    static final String KEY_SERVICES = "ticked_services";
    static final String KEY_STORES = "ticked_stores";

    /** The ticked services, or null when the household hasn't ticked any yet. */
    static Set<ServiceId> services(Context ctx) {
        return ServicePicks.decode(prefs(ctx).getString(KEY_SERVICES, null));
    }

    static void setServices(Context ctx, Set<ServiceId> ticked) {
        prefs(ctx).edit().putString(KEY_SERVICES, ServicePicks.encode(ticked)).apply();
    }

    /** The ticked stores; every store in the pane until the household unticks one. */
    static Set<ServiceId> stores(Context ctx) {
        Set<ServiceId> saved = ServicePicks.decode(prefs(ctx).getString(KEY_STORES, null));
        return saved != null ? saved : Collections.unmodifiableSet(EnumSet.copyOf(ServicePicks.STORES));
    }

    static void setStores(Context ctx, Set<ServiceId> ticked) {
        prefs(ctx).edit().putString(KEY_STORES, ServicePicks.encode(ticked)).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
