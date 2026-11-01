package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One of the household's lists as Movies, TV Shows and Pick for me show it (design_preview/src/core/lists.ts,
 * ShownList; docs/rework.md, Lists by name, 2026-10-31): My list, then one per TMDB list shown in Choose lists (or
 * set in config.xml), by its TMDB name; before the TV has had an account, the mock Household and Friends instead.
 * {@link #ALL} stands for the All lists chip. {@link #key} is stable per list, so a chip keeps its focus and
 * selection across rebuilds: "my", "tmdb-8221116", a name not found yet ("name-our picks"), or the mock "household"
 * and "friends". Immutable; pure Java.
 */
final class ShownList {
    static final ShownList ALL = new ShownList("all", "", 0, null);
    static final ShownList MY = new ShownList("my", ListNames.of(ListId.MY), 0, null);
    /** The mock catalogue's lists, shown before the TV has had a TMDB account. */
    static final List<ShownList> MOCK = Collections.unmodifiableList(Arrays.asList(
            new ShownList("household", ListNames.of(ListId.HOUSEHOLD), 0, ListId.HOUSEHOLD),
            new ShownList("friends", ListNames.of(ListId.FRIENDS), 0, ListId.FRIENDS)));

    final String key;
    final String name;
    /** The TMDB list's number; 0 for My list, a mock list, or a list set by a name not found yet. */
    final long tmdbId;
    /** The mock catalogue's list behind it, or null. */
    final ListId mock;

    private ShownList(String key, String name, long tmdbId, ListId mock) {
        this.key = key;
        this.name = name;
        this.tmdbId = tmdbId;
        this.mock = mock;
    }

    /** A TMDB list: by its number, or by the name it was set with while its number isn't known yet. */
    static ShownList tmdb(long id, String setName, String name) {
        String key = id > 0 ? "tmdb-" + id : "name-" + (setName == null ? "" : setName.trim().toLowerCase(Locale.ROOT));
        return new ShownList(key, name == null ? "" : name, Math.max(0, id), null);
    }

    boolean isMy() {
        return this == MY || "my".equals(key);
    }

    boolean isAll() {
        return this == ALL || "all".equals(key);
    }

    @Override
    public String toString() {
        return key;
    }
}
