package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Home's app row as package names (design_preview/README.md, Home; docs/rework.md, Home app row): the household's
 * ordered favourites. Until the household changes it the row is seeded from dock_apps, or from the installed
 * streaming apps in the prototype's order; after the first Move, Remove or Add the whole list is the household's.
 *
 * The saved list keeps apps that are not installed right now, so an app that is updated or reinstalled comes back
 * where it was; only the visible list leaves them out. Pure Java, tested on the JVM; HomeApps stores it.
 */
final class AppRow {
    private AppRow() {}

    /** The prototype's seven (src/data/services.ts): Netflix, YouTube, Prime Video, Disney+, Max, Apple TV+, Spotify. */
    static final List<String> STREAMING = Collections.unmodifiableList(Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android"));

    /** dock_apps when at least one of them can show, else the streaming apps. */
    static List<String> seed(List<String> dockApps, Predicate<String> shown) {
        for (String pkg : dockApps) if (shown.test(pkg)) return dedupe(dockApps);
        return STREAMING;
    }

    /** The list the household saved, or the seed while there is none. */
    static List<String> full(List<String> saved, List<String> dockApps, Predicate<String> shown) {
        return saved != null ? dedupe(saved) : seed(dockApps, shown);
    }

    /** The most apps Home's row holds (design 02-home: seven tiles across the stage). */
    static final int MAX = 7;

    /**
     * What the row shows: the full list without the apps that can't show (not installed, hidden_apps), at most
     * {@link #MAX}. Apps past the seventh stay in the saved list and show again when one before them goes.
     */
    static List<String> visible(List<String> full, Predicate<String> shown) {
        List<String> out = new ArrayList<>();
        for (String pkg : dedupe(full)) {
            if (out.size() == MAX) break;
            if (shown.test(pkg)) out.add(pkg);
        }
        return out;
    }

    /** Whether another app can join the row: fewer than {@link #MAX} show. */
    static boolean hasRoom(List<String> full, Predicate<String> shown) {
        return visible(full, shown).size() < MAX;
    }

    /**
     * Moves pkg one place left (delta -1) or right (+1) among the visible apps, past its visible neighbour. Apps that
     * can't show stay where they were relative to the others. Unchanged when pkg is at that end of the row.
     */
    static List<String> move(List<String> full, List<String> visible, String pkg, int delta) {
        int at = visible.indexOf(pkg);
        int to = at + Integer.signum(delta);
        List<String> out = dedupe(full);
        if (at < 0 || delta == 0 || to < 0 || to >= visible.size()) return out;
        String neighbour = visible.get(to);
        out.remove(pkg);
        int n = out.indexOf(neighbour);
        out.add(delta > 0 ? n + 1 : n, pkg);
        return out;
    }

    static List<String> remove(List<String> full, String pkg) {
        List<String> out = dedupe(full);
        out.remove(pkg);
        return out;
    }

    /** Adds pkg at the end of the row; unchanged when it is already on it. */
    static List<String> add(List<String> full, String pkg) {
        List<String> out = dedupe(full);
        if (!out.contains(pkg)) out.add(pkg);
        return out;
    }

    /** One package per line. */
    static String encode(List<String> full) {
        StringBuilder sb = new StringBuilder();
        for (String pkg : dedupe(full)) sb.append(pkg).append('\n');
        return sb.toString();
    }

    /** The saved list, or null for none; lines that are not package names are dropped. */
    static List<String> decode(String text) {
        if (text == null) return null;
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String pkg = line.trim();
            if (LibraryApps.validPackage(pkg)) out.add(pkg);
        }
        return dedupe(out);
    }

    private static List<String> dedupe(List<String> list) {
        return new ArrayList<>(new LinkedHashSet<>(list));
    }
}
