package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules for deciding that a store's title is the catalogue's title when the store only has names and dates
 * (Apple's iTunes search). Names match when they are equal after {@link #normalise}; years match within one year,
 * since a store's release date is often the Swedish release, months after the premiere. A TV season's name ("Succession,
 * Season 1") is split into the show and the season. Pure Java, unit-tested (TitleMatchTest).
 */
final class TitleMatch {
    private TitleMatch() {}

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern YEAR_SUFFIX = Pattern.compile("\\s*[(\\[]\\s*(19|20)\\d\\d\\s*[)\\]]\\s*$");
    private static final Pattern NOT_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern ARTICLE = Pattern.compile("^(the|a|an) ");
    private static final Pattern SEASON = Pattern.compile(
            "^(.*?)\\s*(?:[,:\\-–]\\s*|\\(\\s*)(?:season|säsong|series|staffel|saison)\\s+(\\d{1,3})\\s*\\)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Lowercase, accents and punctuation gone, "&" read as "and", a trailing "(2014)" and a leading "The", "A" or
     * "An" dropped: "The Lord of the Rings: The Return of the King (2003)" becomes
     * "lord of the rings the return of the king".
     */
    static String normalise(String s) {
        if (s == null) return "";
        String t = YEAR_SUFFIX.matcher(s).replaceAll("");
        t = MARKS.matcher(Normalizer.normalize(t, Normalizer.Form.NFD)).replaceAll("");
        t = t.toLowerCase(Locale.ROOT).replace("&", " and ");
        t = NOT_ALNUM.matcher(t).replaceAll(" ").trim();
        return ARTICLE.matcher(t).replaceFirst("").trim();
    }

    static boolean sameName(String a, String b) {
        String na = normalise(a);
        return !na.isEmpty() && na.equals(normalise(b));
    }

    /** Within one year; an unknown year (0) on either side doesn't rule a match out. */
    static boolean yearClose(int want, int got) {
        return want <= 0 || got <= 0 || Math.abs(want - got) <= 1;
    }

    /** The year of an ISO date ("2016-12-02T08:00:00Z"), or 0. */
    static int yearOf(String isoDate) {
        if (isoDate == null || isoDate.length() < 4) return 0;
        try {
            return Integer.parseInt(isoDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A TV season's show name and number, split from the store's name for it. */
    static final class SeasonName {
        final String show;
        final int season;

        SeasonName(String show, int season) {
            this.show = show;
            this.season = season;
        }
    }

    /**
     * "Succession, Season 1", "Succession: Season 2", "Succession - Säsong 3", "Succession (Season 4)"; null for a
     * name that names no season.
     */
    static SeasonName season(String collectionName) {
        if (collectionName == null) return null;
        Matcher m = SEASON.matcher(collectionName.trim());
        if (!m.matches() || m.group(1).isEmpty()) return null;
        return new SeasonName(m.group(1), Integer.parseInt(m.group(2)));
    }

    /**
     * How well a store's movie matches: -1 for no match, else 2 for the same year and 1 for a year either side.
     * {@code byId} is for a store item looked up by an id Wikidata gave: then the name may be the Swedish one, so only
     * the year is checked.
     */
    static int movieScore(PriceTitle t, String storeName, int storeYear, boolean byId) {
        if (!yearClose(t.year, storeYear)) return -1;
        if (!byId) {
            boolean named = false;
            for (String n : t.names()) named |= sameName(n, storeName);
            if (!named) return -1;
        }
        return t.year <= 0 || storeYear <= 0 || t.year == storeYear ? 2 : 1;
    }

    /**
     * Whether a store's season belongs to the show: the show's name matches and the season came out no earlier than
     * a year before the show's first air date.
     */
    static boolean seasonMatches(PriceTitle t, SeasonName s, int storeYear) {
        if (s == null) return false;
        boolean named = false;
        for (String n : t.names()) named |= sameName(n, s.show);
        return named && (t.year <= 0 || storeYear <= 0 || storeYear >= t.year - 1);
    }
}
