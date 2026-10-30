package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * TMDB's genres in the launcher's words (BACKLOG.md, Real catalogue). The screens use the prototype's short names
 * ("Sci-fi", not "Science Fiction"), and Pick for me's pairs and Search's Browse by genre are written in them, so
 * TMDB's ids are mapped onto that vocabulary. TMDB's combined TV genres split in two ("Sci-Fi &amp; Fantasy" is
 * Sci-fi and Fantasy). Pure Java, unit-tested (GenreMapTest).
 */
final class GenreMap {
    private GenreMap() {}

    private static final Map<Integer, String[]> MOVIE = new LinkedHashMap<>();
    private static final Map<Integer, String[]> TV = new LinkedHashMap<>();

    static {
        movie(28, "Action");
        movie(12, "Adventure");
        movie(16, "Animation");
        movie(35, "Comedy");
        movie(80, "Crime");
        movie(99, "Documentary");
        movie(18, "Drama");
        movie(10751, "Family");
        movie(14, "Fantasy");
        movie(36, "History");
        movie(27, "Horror");
        movie(10402, "Music");
        movie(9648, "Mystery");
        movie(10749, "Romance");
        movie(878, "Sci-fi");
        movie(10770, "TV Movie");
        movie(53, "Thriller");
        movie(10752, "War");
        movie(37, "Western");

        tv(10759, "Action", "Adventure");
        tv(16, "Animation");
        tv(35, "Comedy");
        tv(80, "Crime");
        tv(99, "Documentary");
        tv(18, "Drama");
        tv(10751, "Family");
        tv(10762, "Kids");
        tv(9648, "Mystery");
        tv(10763, "News");
        tv(10764, "Reality");
        tv(10765, "Sci-fi", "Fantasy");
        tv(10766, "Soap");
        tv(10767, "Talk");
        tv(10768, "War", "Politics");
        tv(37, "Western");
    }

    private static void movie(int id, String... names) {
        MOVIE.put(id, names);
    }

    private static void tv(int id, String... names) {
        TV.put(id, names);
    }

    private static Map<Integer, String[]> of(Kind kind) {
        return kind == Kind.MOVIE ? MOVIE : TV;
    }

    /** The launcher's names for TMDB genre ids, in TMDB's order, without repeats; unknown ids are skipped. */
    static List<String> names(Kind kind, int[] ids) {
        List<String> out = new ArrayList<>();
        if (ids == null) return out;
        for (int id : ids) {
            String[] names = of(kind).get(id);
            if (names == null) continue;
            for (String n : names) if (!out.contains(n)) out.add(n);
        }
        return out;
    }

    /** Every genre name a kind of title can have, sorted. */
    static List<String> all(Kind kind) {
        TreeSet<String> out = new TreeSet<>();
        for (String[] names : of(kind).values()) Collections.addAll(out, names);
        return new ArrayList<>(out);
    }

    /** Every genre name, movies and shows together, sorted. */
    static List<String> all() {
        TreeSet<String> out = new TreeSet<>(all(Kind.MOVIE));
        out.addAll(all(Kind.SHOW));
        return new ArrayList<>(out);
    }

    /** The TMDB genre id to discover a genre name with, for that kind; 0 when the kind has no such genre. */
    static int idFor(Kind kind, String name) {
        for (Map.Entry<Integer, String[]> e : of(kind).entrySet()) {
            for (String n : e.getValue()) if (n.equalsIgnoreCase(name)) return e.getKey();
        }
        return 0;
    }
}
