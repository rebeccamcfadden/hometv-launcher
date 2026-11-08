package com.hometv.launcher;

import java.util.Locale;

/**
 * The country whose services, stores, prices and TMDB lists the launcher shows (docs/how-it-works.md, Two regions, and Family
 * install step 2). One region at a time; Sweden unless a TV is set to another, so no TV's default changes.
 *
 * <p>{@link #code} is how TMDB ({@code region}, {@code watch_region}) and Watchmode ({@code regions}) name it,
 * {@link #country} how iTunes and Movie of the Night do. Pure Java, unit-tested (RegionTest).
 */
enum Region {
    SE("SEK"),
    US("USD");

    static final Region DEFAULT = SE;

    /** "SE": TMDB's and Watchmode's name for it. */
    final String code = name();
    /** "se": iTunes' and Movie of the Night's. */
    final String country = name().toLowerCase(Locale.ROOT);
    /** The ISO 4217 code its prices are in. */
    final String currency;

    Region(String currency) {
        this.currency = currency;
    }

    /** The region with this code (case and surrounding spaces ignored), or null for one the launcher doesn't know. */
    static Region parse(String code) {
        if (code == null) return null;
        String want = code.trim().toUpperCase(Locale.ROOT);
        for (Region r : values()) if (r.code.equals(want)) return r;
        return null;
    }

    /** {@link #parse}, or {@link #DEFAULT} for nothing or an unknown code. */
    static Region orDefault(String code) {
        Region r = parse(code);
        return r == null ? DEFAULT : r;
    }

    /**
     * A setting kept per region: Sweden keeps {@code base}, the name it had before there were regions, so nothing
     * saved is lost; another region adds its country ("ticked_stores_us").
     */
    String key(String base) {
        return this == SE ? base : base + "_" + country;
    }
}
