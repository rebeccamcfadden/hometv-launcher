package com.gathin.tvhome;

import java.util.Locale;

/**
 * The streaming services and stores the catalogue knows (design_preview/src/types.ts, ServiceId). {@link #id} is the
 * prototype's lowercase id, the one config.json's {@code subscribed_services} uses.
 */
enum ServiceId {
    NETFLIX, PRIME, DISNEY, MAX, APPLETVPLUS, HULU, YOUTUBE, APPLETV, GOOGLETV;

    final String id = name().toLowerCase(Locale.ROOT);

    /** The service with this lowercase id (case and surrounding spaces ignored), or null. */
    static ServiceId parse(String id) {
        if (id == null) return null;
        String want = id.trim().toLowerCase(Locale.ROOT);
        for (ServiceId s : values()) if (s.id.equals(want)) return s;
        return null;
    }
}
