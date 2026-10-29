package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The services the catalogue knows (design_preview/src/data/services.ts): each one's name, whether it is a rent/buy
 * store, and the TV app package(s) that open it, in the order they are tried. Pure Java: {@link Services} adds what
 * the TV has installed. Apple TV+ and the Apple TV store are one app.
 */
final class ServiceTable {

    private static final class Entry {
        final String name;
        final boolean store;
        final List<String> packages;

        Entry(String name, boolean store, String... packages) {
            this.name = name;
            this.store = store;
            this.packages = Collections.unmodifiableList(Arrays.asList(packages));
        }
    }

    private final Map<ServiceId, Entry> entries = new EnumMap<>(ServiceId.class);

    ServiceTable() {
        put(ServiceId.NETFLIX, "Netflix", false, "com.netflix.ninja");
        put(ServiceId.PRIME, "Prime Video", false, "com.amazon.amazonvideo.livingroom");
        put(ServiceId.DISNEY, "Disney+", false, "com.disney.disneyplus");
        put(ServiceId.MAX, "Max", false, "com.wbd.stream", "com.hbo.hbonow");
        put(ServiceId.APPLETVPLUS, "Apple TV+", false, "com.apple.atve.androidtv.appletv");
        put(ServiceId.HULU, "Hulu", false, "com.hulu.livingroomplus");
        put(ServiceId.YOUTUBE, "YouTube", true, "com.google.android.youtube.tv");
        put(ServiceId.APPLETV, "Apple TV", true, "com.apple.atve.androidtv.appletv");
        put(ServiceId.GOOGLETV, "Google TV", true, "com.google.android.videos");
    }

    private void put(ServiceId id, String name, boolean store, String... packages) {
        entries.put(id, new Entry(name, store, packages));
    }

    String name(ServiceId id) {
        return entries.get(id).name;
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
