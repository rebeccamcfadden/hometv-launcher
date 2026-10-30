package com.gathin.tvhome;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The services the catalogue knows (design_preview/src/data/services.ts, and Sweden's in {@link WatchProviders}):
 * each one's name, whether it is a rent/buy store, and the TV app package(s) that open it, in the order they are
 * tried. Pure Java: {@link Services} adds what the TV has installed. Apple's subscription and its store are one app,
 * as are Prime Video and Amazon's store. A service that both streams and sells (Viaplay) counts as a subscription.
 *
 * <p>Two sets of names: {@link #branded()}, current branding ("HBO Max", "Apple TV" and the "Apple TV Store"), for
 * the real catalogue; {@code new ServiceTable()}, the prototype's ("Max", "Apple TV+" and "Apple TV"), for the mock
 * catalogue, so it still reads as the prototype does and its parity tests hold.
 */
final class ServiceTable {

    private static final class Entry {
        final String name;
        final boolean store;
        final List<String> packages;

        Entry(String name, boolean store, List<String> packages) {
            this.name = name;
            this.store = store;
            this.packages = packages;
        }
    }

    private final Map<ServiceId, Entry> entries = new EnumMap<>(ServiceId.class);
    private final boolean branded;

    /** The prototype's names. */
    ServiceTable() {
        this(false);
    }

    private ServiceTable(boolean branded) {
        this.branded = branded;
        for (WatchProviders.Entry e : WatchProviders.SE.all()) {
            String name = e.name;
            if (!branded) {
                if (e.service == ServiceId.MAX) name = "Max";
                else if (e.service == ServiceId.APPLETVPLUS) name = "Apple TV+";
                else if (e.service == ServiceId.APPLETV) name = "Apple TV";
            }
            entries.put(e.service, new Entry(name, e.store && !e.subscription, e.packages));
        }
    }

    /** Current branding, for the real catalogue. */
    static ServiceTable branded() {
        return new ServiceTable(true);
    }

    String name(ServiceId id) {
        return entries.get(id).name;
    }

    /**
     * The name of the app that opens the service: with current branding a store opens in its subscription's app
     * ("Apple TV Store" in the Apple TV app, "Amazon Video" in Prime Video); otherwise the service's own name.
     */
    String appName(ServiceId id) {
        Entry e = entries.get(id);
        if (!branded || !e.store) return e.name;
        for (Entry other : entries.values()) {
            if (!other.store && other.packages.get(0).equals(e.packages.get(0))) return other.name;
        }
        return e.name;
    }

    /** A rent/buy store rather than a subscription. */
    boolean isStore(ServiceId id) {
        return entries.get(id).store;
    }

    /** The TV app packages that open the service, most likely first. */
    List<String> packages(ServiceId id) {
        return entries.get(id).packages;
    }

    /** Whether any of the service's apps is in {@code installed} (a set of package names). */
    boolean isInstalled(ServiceId id, Collection<String> installed) {
        for (String pkg : packages(id)) if (installed.contains(pkg)) return true;
        return false;
    }

    /**
     * The services the household has, so their SUB offers count as included. With an {@code override} (the
     * subscribed_services config key, lowercase ids) exactly the services it names, unknown ids ignored. Without
     * one, every subscription service whose app is installed, plus YouTube when installed: its FREE offers are
     * included anyway, and the prototype counts it as subscribed.
     */
    Set<ServiceId> subscribed(Collection<String> installed, List<String> override) {
        Set<ServiceId> out = EnumSet.noneOf(ServiceId.class);
        if (override != null && !override.isEmpty()) {
            for (String id : override) {
                ServiceId s = ServiceId.parse(id);
                if (s != null) out.add(s);
            }
            return Collections.unmodifiableSet(out);
        }
        for (ServiceId s : ServiceId.values()) {
            if ((!isStore(s) || s == ServiceId.YOUTUBE) && isInstalled(s, installed)) out.add(s);
        }
        return Collections.unmodifiableSet(out);
    }
}
