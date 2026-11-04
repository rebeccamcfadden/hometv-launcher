package com.hometv.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The short lines cards and screens print about a title (design_preview/src/ui/widgets.ts, titleMeta and
 * provenance; core/dom.ts, initials). Pure Java, English only, unit-tested.
 */
final class TitleText {
    private TitleText() {}

    /**
     * "1h 56m · PG-13 · 7.9/10" for a movie, "2 seasons · TV-MA · 8.7/10" for a show. What isn't known yet (a
     * title whose details are still loading) or has no US rating is left out: "7.9/10".
     */
    static String meta(Title t) {
        List<String> parts = new ArrayList<>(3);
        if (t.isMovie() ? t.runtime > 0 : !t.seasons.isEmpty()) {
            parts.add(t.isMovie() ? Offers.runtimeLabel(t.runtime) : seasons(t.seasons.size()));
        }
        if (!t.rating.isEmpty()) parts.add(t.rating);
        if (t.score > 0 || t.detailed) parts.add(score(t.score) + "/10");
        return String.join(" · ", parts);
    }

    /** "1 season", "3 seasons". */
    static String seasons(int n) {
        return n + (n == 1 ? " season" : " seasons");
    }

    /** One decimal, as the prototype's toFixed(1): "8.0". */
    static String score(double score) {
        return String.format(Locale.US, "%.1f", score);
    }

    /**
     * The first of the household's lists in chip order that holds the title (My list first), or "Catalogue", and its
     * first genre: "My list · Sci-fi", "Family picks · Drama" (lists.ts, provenance).
     */
    static String provenance(Title t, ListMembership lists) {
        ShownList on = lists.firstListOf(t);
        String list = on == null ? "Catalogue" : on.name;
        return t.genres.isEmpty() ? list : list + " · " + t.genres.get(0);
    }

    /** F46 P1: Pick for me's list tag, "From My list"; "From Catalogue" for a title on no list. */
    static String fromList(Title t, ListMembership lists) {
        ShownList on = lists.firstListOf(t);
        return "From " + (on == null ? "Catalogue" : on.name);
    }

    /** F46 P1: "2023 · 1h 46m" (a show: "2019 · 3 seasons"), whichever parts are known. */
    static String yearLength(Title t) {
        List<String> parts = new ArrayList<>(2);
        if (t.year > 0) parts.add(String.valueOf(t.year));
        if (t.isMovie() ? t.runtime > 0 : !t.seasons.isEmpty()) {
            parts.add(t.isMovie() ? Offers.runtimeLabel(t.runtime) : seasons(t.seasons.size()));
        }
        return join(parts);
    }

    /** F46 P1: "PG-13 · 7.9/10", whichever parts are known. */
    static String ratingScore(Title t) {
        List<String> parts = new ArrayList<>(2);
        if (!t.rating.isEmpty()) parts.add(t.rating);
        if (t.score > 0 || t.detailed) parts.add(score(t.score) + "/10");
        return join(parts);
    }

    /** F46 P1: the first two genres, "Drama · Romance". */
    static String genres(Title t) {
        return join(t.genres.size() > 2 ? t.genres.subList(0, 2) : t.genres);
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (b.length() > 0) b.append(" · ");
            b.append(p);
        }
        return b.toString();
    }

    /**
     * The poster card's service line (F40): "Netflix · Included", "Apple TV Store · Rent 39 kr"; just the
     * availability when no service of the household's has the title, and nothing when there is neither.
     */
    static String serviceLine(String service, String availability) {
        String s = service == null ? "" : service.trim(), a = availability == null ? "" : availability.trim();
        if (s.isEmpty()) return a;
        return a.isEmpty() ? s : s + " · " + a;
    }

    /** "Movie · 2016", "TV show · 2022"; just the kind when the year isn't known. */
    static String kindYear(Title t) {
        String kind = t.isMovie() ? "Movie" : "TV show";
        return t.year > 0 ? kind + " · " + t.year : kind;
    }

    /**
     * F46: the focused C card's line, "2024 · 8.5/10" (design_preview miniCard); either part alone when the other isn't
     * known yet, and empty with neither.
     */
    static String yearScore(Title t) {
        String year = t.year > 0 ? String.valueOf(t.year) : "";
        String score = t.score > 0 || t.detailed ? score(t.score) + "/10" : "";
        if (year.isEmpty()) return score;
        return score.isEmpty() ? year : year + " · " + score;
    }

    /** Up to two initials, upper case, for a portrait without a photo: "Amy Adams" gives "AA". */
    static String initials(String name) {
        StringBuilder out = new StringBuilder(2);
        if (name == null) return "";
        for (String word : name.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            out.appendCodePoint(word.codePointAt(0));
            if (out.codePointCount(0, out.length()) == 2) break;
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }
}
