package com.gathin.tvhome;

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

    /** The first watchlist the title is on, or "Catalogue", and its first genre: "My list · Sci-fi". */
    static String provenance(Title t) {
        String list = t.lists.isEmpty() ? "Catalogue" : ListNames.of(t.lists.get(0));
        return t.genres.isEmpty() ? list : list + " · " + t.genres.get(0);
    }

    /** "Movie · 2016", "TV show · 2022"; just the kind when the year isn't known. */
    static String kindYear(Title t) {
        String kind = t.isMovie() ? "Movie" : "TV show";
        return t.year > 0 ? kind + " · " + t.year : kind;
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
