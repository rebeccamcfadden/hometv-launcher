package com.gathin.tvhome;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * The TMDB requests the catalogue makes (BACKLOG.md, Real catalogue): their v3 paths, how long each answer stays
 * good on disk, and the image URLs at the sizes the screens show. English (en-US) everywhere; the region is SE.
 * Pure Java, unit-tested (TmdbApiTest). The token is never part of a path: {@link TmdbClient} sends it as a header.
 */
final class TmdbApi {
    private TmdbApi() {}

    static final String BASE = "https://api.themoviedb.org/3";
    static final String IMAGES = "https://image.tmdb.org/t/p/";
    static final String LANGUAGE = "en-US";
    static final String REGION = "SE";

    static final long MINUTE = 60_000L, HOUR = 60 * MINUTE, DAY = 24 * HOUR;
    /** How long an answer stays fresh, after cleanyourtv-ui's Tmdb.ttl (BACKLOG.md, Findings). */
    static final long TTL_SEARCH = HOUR, TTL_LIST = 3 * HOUR, TTL_TITLE = 12 * HOUR, TTL_WEEK = 7 * DAY;

    /** What a title's details page asks for in one request: the cast, ratings, trailer and similar titles. */
    private static final String MOVIE_EXTRAS = "credits,release_dates,videos,recommendations,similar";
    private static final String TV_EXTRAS = "credits,content_ratings,videos,recommendations,similar";

    /** "movie:329865" for a movie, "tv:95396" for a show: the launcher's title ids for TMDB titles. */
    static String id(Kind kind, int tmdbId) {
        return (kind == Kind.MOVIE ? "movie:" : "tv:") + tmdbId;
    }

    /** The kind of a TMDB title id, or null when the id is not one ("arrival", from the mock catalogue). */
    static Kind kindOf(String id) {
        if (id == null) return null;
        if (id.startsWith("movie:") && digits(id.substring(6)) > 0) return Kind.MOVIE;
        if (id.startsWith("tv:") && digits(id.substring(3)) > 0) return Kind.SHOW;
        return null;
    }

    /** The TMDB number of a title id ("tv:95396" gives 95396), or 0 for anything else (the mock's "911"). */
    static int number(String id) {
        if (kindOf(id) == null) return 0;
        return digits(id.substring(id.indexOf(':') + 1));
    }

    private static int digits(String digits) {
        if (digits.isEmpty() || digits.length() > 9) return 0;
        for (int i = 0; i < digits.length(); i++) if (!Character.isDigit(digits.charAt(i))) return 0;
        return Integer.parseInt(digits);
    }

    static boolean isTmdbId(String id) {
        return kindOf(id) != null;
    }

    /** The details of a title, with its credits, rating, videos, recommendations and similar titles. */
    static String details(String id) {
        Kind kind = kindOf(id);
        if (kind == null) throw new IllegalArgumentException("not a TMDB id: " + id);
        String path = kind == Kind.MOVIE ? "/movie/" + number(id) + "?append_to_response=" + MOVIE_EXTRAS
                : "/tv/" + number(id) + "?append_to_response=" + TV_EXTRAS;
        return path + "&language=" + LANGUAGE;
    }

    /** One season of a show, with its episodes. */
    static String season(String id, int n) {
        return "/tv/" + number(id) + "/season/" + n + "?language=" + LANGUAGE;
    }

    /** A person, with everything they are in or made. */
    static String person(int personId) {
        return "/person/" + personId + "?append_to_response=combined_credits&language=" + LANGUAGE;
    }

    /** Movies, shows and people matching the query, the first page (20). */
    static String search(String query) {
        return "/search/multi?query=" + encode(query.trim().toLowerCase(Locale.ROOT)) + "&include_adult=false&language="
                + LANGUAGE + "&page=1";
    }

    /** The most popular titles of one TMDB genre, with enough votes for the score to mean something. */
    static String discover(Kind kind, int genreId) {
        return "/discover/" + (kind == Kind.MOVIE ? "movie" : "tv") + "?with_genres=" + genreId
                + "&sort_by=popularity.desc&vote_count.gte=200&include_adult=false&language=" + LANGUAGE
                + (kind == Kind.MOVIE ? "&region=" + REGION : "") + "&page=1";
    }

    /** How long the answer to a path stays fresh: search an hour, lists 3 hours, a title 12, the rest a week. */
    static long ttl(String path) {
        if (path.startsWith("/search/")) return TTL_SEARCH;
        if (path.startsWith("/discover/") || path.startsWith("/trending/")) return TTL_LIST;
        if (path.startsWith("/person/") || path.contains("/season/")) return TTL_WEEK;
        return TTL_TITLE;
    }

    // ---- Images ---------------------------------------------------------------------------------------------------

    private static final int[] BACKDROPS = {300, 780, 1280};
    private static final int[] PROFILES = {45, 185};

    /** The smallest TMDB width at least as wide as the box, else the widest. */
    static int pick(int[] widths, int boxWidthPx) {
        for (int w : widths) if (w >= boxWidthPx) return w;
        return widths[widths.length - 1];
    }

    /** A backdrop at a width close to the box: w300 for small cards, w780 for library cards, w1280 for the hero. */
    static String backdrop(String path, int boxWidthPx) {
        return image(path, "w" + pick(BACKDROPS, boxWidthPx));
    }

    /** A portrait for a box this wide: w185 for every portrait the launcher shows (104 to 160 px). */
    static String profile(String path, int boxWidthPx) {
        return image(path, "w" + pick(PROFILES, boxWidthPx));
    }

    /** Whether art is a TMDB image path ("/abc.jpg") rather than a mock name. */
    static boolean isImagePath(String art) {
        return art != null && art.length() > 1 && art.charAt(0) == '/' && art.indexOf("..") < 0;
    }

    private static String image(String path, String size) {
        return isImagePath(path) ? IMAGES + size + path : null;
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
