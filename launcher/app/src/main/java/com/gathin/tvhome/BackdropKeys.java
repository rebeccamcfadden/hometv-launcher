package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The rules behind {@link Backdrops}, kept free of Android so they can be tested: what a theme's backdrop key asks
 * for ({@link Theme#backdrop}), which picture of an hourly list is up, where a download comes from and goes on disk,
 * and which picture a Seasonal theme shows next.
 */
final class BackdropKeys {
    private BackdropKeys() {}

    enum Kind { RES, NONE, SKY, PIXEL, URL }

    /** In a URL path or after a pixel scene's name: the part of the day, filled in when the picture is shown. */
    static final String PHASE = "{phase}";

    /** A parsed backdrop key: its kind and, by kind, the drawable name, the period's slug or the paths. */
    static final class Key {
        final Kind kind;
        /** The drawable name for RES, the period's slug for PIXEL, empty otherwise. */
        final String name;
        /** The paths for URL, under the assets base URL; empty otherwise. */
        final List<String> paths;
        /** The picture follows the part of the day ({@link DayPhase}): a {@link #PHASE} in a path or the scene. */
        final boolean phased;

        private Key(Kind kind, String name, List<String> paths, boolean phased) {
            this.kind = kind;
            this.name = name;
            this.paths = paths;
            this.phased = phased;
        }

        static final Key SKY = new Key(Kind.SKY, "", Collections.emptyList(), false);
        static final Key NONE = new Key(Kind.NONE, "", Collections.emptyList(), false);
    }

    /** Anything not understood, or a path that could leave the backdrops folder, falls back to the sky. */
    static Key parse(String backdrop) {
        if (backdrop == null) return Key.SKY;
        String s = backdrop.trim();
        if (s.equals("none")) return Key.NONE;
        if (s.startsWith("res:")) {
            String name = s.substring(4).trim();
            return name.matches("[a-z][a-z0-9_]*") ? new Key(Kind.RES, name, Collections.emptyList(), false)
                    : Key.SKY;
        }
        if (s.startsWith("pixel:")) {
            String slug = s.substring(6).trim();
            boolean phased = slug.endsWith("-" + PHASE);
            if (phased) slug = slug.substring(0, slug.length() - PHASE.length() - 1);
            return period(slug) != null ? new Key(Kind.PIXEL, slug, Collections.emptyList(), phased) : Key.SKY;
        }
        if (s.startsWith("url:")) {
            List<String> paths = new ArrayList<>();
            boolean phased = false;
            for (String p : s.substring(4).split(",")) {
                String path = p.trim();
                if (!safePath(path.replace(PHASE, DayPhase.DAY.slug()))) return Key.SKY;
                phased |= path.contains(PHASE);
                paths.add(path);
            }
            return new Key(Kind.URL, "", Collections.unmodifiableList(paths), phased);
        }
        return Key.SKY;
    }

    /** The picture of an hourly list shown in a given hour of the day (0–23): the list goes round once per n hours. */
    static int rotation(int hour, int n) {
        if (n <= 1) return 0;
        return Math.floorMod(hour, n);
    }

    /** The path a URL key shows in a given hour, in the day's part for a phased key. */
    static String pick(Key key, int hour, DayPhase phase) {
        if (key.kind != Kind.URL || key.paths.isEmpty()) return null;
        return phased(key.paths.get(rotation(hour, key.paths.size())), phase);
    }

    /** A path with its {@link #PHASE} filled in. */
    static String phased(String path, DayPhase phase) {
        return path.replace(PHASE, phase.slug());
    }

    /**
     * A relative path made of plain names: letters, digits, dot, dash and underscore, separated by single slashes,
     * with no "." or ".." part. It names both the download and the file under the TV's backdrops folder.
     */
    static boolean safePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 200) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
            if (!part.matches("[A-Za-z0-9._-]+")) return false;
        }
        return true;
    }

    /** The file's place under the app's files folder. */
    static String cachePath(String path) {
        return "backdrops/" + path;
    }

    /** The base URL and a path joined with exactly one slash. */
    static String join(String base, String path) {
        String b = base.endsWith("/") ? base : base + "/";
        return b + (path.startsWith("/") ? path.substring(1) : path);
    }

    /** A period's file and key name: lower case, dashes for underscores (spring-rain). */
    static String slug(SeasonCalendar.Period p) {
        return p.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static SeasonCalendar.Period period(String slug) {
        for (SeasonCalendar.Period p : SeasonCalendar.Period.values()) {
            if (slug(p).equals(slug)) return p;
        }
        return null;
    }

    /** The period after the one a day is in: the first different one in the days that follow. */
    static SeasonCalendar.Period next(LocalDate day) {
        return SeasonCalendar.at(nextPeriodStart(day));
    }

    /** The first day of the next period. */
    static LocalDate nextPeriodStart(LocalDate day) {
        SeasonCalendar.Period now = SeasonCalendar.at(day);
        for (int i = 1; i <= 366; i++) {
            if (SeasonCalendar.at(day.plusDays(i)) != now) return day.plusDays(i);
        }
        return day.plusDays(1);
    }

    /**
     * The same picture for another period when the path is named after the current one
     * ({@code seasonal/vector-5/autumn-{phase}.webp} → {@code seasonal/vector-5/halloween-{phase}.webp}, or a name
     * that is the period's alone); null for any other path.
     */
    static String forPeriod(String path, SeasonCalendar.Period current, SeasonCalendar.Period other) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash) return null;
        String name = path.substring(slash + 1, dot);
        String rest;
        if (name.equals(slug(current))) rest = "";
        else if (name.equals(slug(current) + "-" + PHASE)) rest = "-" + PHASE;
        else return null;
        return path.substring(0, slash + 1) + slug(other) + rest + path.substring(dot);
    }

    /**
     * Pictures worth fetching in the background besides the one on screen: the rest of an hourly list, the next part
     * of the day's picture of a phased theme, and the next period's picture of a Seasonal theme in the part of the day
     * that period starts in (its first midnight), so the changes need no download.
     */
    static List<String> prefetch(Key key, LocalDate day, DayPhase now, DayPhase next, DayPhase nextPeriodStarts) {
        List<String> out = new ArrayList<>();
        if (key.kind != Kind.URL) return out;
        SeasonCalendar.Period period = SeasonCalendar.at(day);
        SeasonCalendar.Period nextPeriod = next(day);
        for (String path : key.paths) {
            add(out, phased(path, now));
            add(out, phased(path, next));
            String upcoming = forPeriod(path, period, nextPeriod);
            if (upcoming != null) add(out, phased(upcoming, nextPeriodStarts));
        }
        return out;
    }

    private static void add(List<String> out, String path) {
        if (!out.contains(path)) out.add(path);
    }

    /**
     * The width to decode a picture at so it still covers the screen when centre-cropped: its own width when that is
     * no more than needed (never scaled up, the view stretches it), otherwise scaled down to what the screen needs.
     */
    static int decodeWidth(int srcW, int srcH, int screenW, int screenH) {
        if (srcW <= 0 || srcH <= 0 || screenW <= 0 || screenH <= 0) return srcW;
        double scale = Math.max((double) screenW / srcW, (double) screenH / srcH);
        if (scale >= 1) return srcW;
        return (int) Math.ceil(srcW * scale);
    }
}
