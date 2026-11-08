/*
 * The link formats for Netflix, Disney+, Apple TV, Max and Prime Video are adapted from cleanyourtv-ui's
 * DeepLinks.java (https://github.com/matheus-caldeira/cleanyourtv-ui):
 *
 * MIT License
 *
 * Copyright (c) 2026 Gathin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.hometv.launcher;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links that open a title inside a service's TV app rather than on its home screen (docs/how-it-works.md, Title deep links),
 * built from the ids {@link WikidataIds} found. Stage 2 sends each as an ACTION_VIEW intent limited to the service's
 * package (Netflix also wants the extra {@code source=30}) and falls back to opening the app when nothing takes it.
 * Wikidata has ids for Netflix, Disney+, Apple TV, HBO Max, Prime Video and SVT Play, and in the US for Hulu, Peacock,
 * Paramount+, Tubi and Fandango at Home; TV4 Play, Viaplay, SkyShowtime, the Swedish stores and The Roku Channel have
 * no Wikidata property for a title, so they open on their home screen. Every id is checked against the shape Wikidata
 * documents for it before it goes into a link. Pure Java, unit-tested (WikidataLinksTest).
 */
final class DeepLinks {
    private DeepLinks() {}

    private static final Pattern DIGITS = Pattern.compile("\\d{4,10}");
    private static final Pattern DISNEY = Pattern.compile("[0-9A-Za-z]{12}");
    private static final Pattern DISNEY_BROWSE = Pattern.compile("entity-[0-9a-f-]{36}");
    private static final Pattern APPLE = Pattern.compile("umc\\.cmc\\.[a-z0-9]{20,30}");
    /**
     * HBO Max: only the current app's ids, {@code show/}, {@code movie/} or {@code mini-series/} and a UUID. Wikidata's
     * P8298 also holds the old HBO Max app's {@code series/urn:hbo:...} ids, which today's app takes but answers with
     * "Content Not Available" (checked on the TV, 2026-11-02): with only those, the app opens on its home screen.
     */
    private static final Pattern MAX = Pattern.compile("(show|movie|mini-series)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern GTI = Pattern.compile("amzn1\\.dv\\.gti\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern ASIN = Pattern.compile("B[0-9A-Z]{9}");
    private static final Pattern PRIME_ID = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.-]{5,63}");
    private static final Pattern SVT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}");
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    /**
     * Hulu: a UUID, as Wikidata documents it, or Hulu's own address form with the name in front
     * ("the-testaments-9e859f3c-…"); a few values also carry a stray "movie/" or "series/", which is dropped.
     */
    private static final Pattern HULU = Pattern.compile("(?:(?:movie|series)/)?((?:[a-z0-9]+-)*" + UUID.pattern() + ")");
    /**
     * Peacock: Wikidata holds "tv/name", "movies/name" and, for some, "stream-tv/name" (its formatter adds the
     * "stream-" itself), so a leading "stream-" is dropped before it goes into the link.
     */
    private static final Pattern PEACOCK = Pattern.compile("(?:stream-)?((?:tv|movies)/[a-z0-9][a-z0-9-]{0,127})");
    private static final Pattern PARAMOUNT = Pattern.compile("[A-Za-z0-9_-]{32}");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,10}");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    /** The link that opens the title in a service ({@link WatchProviders} key), or null when there is none. */
    static String link(String service, Kind kind, WikidataIds ids, String name) {
        boolean movie = kind == Kind.MOVIE;
        String v;
        switch (service) {
            case "netflix":
                v = ok(ids.first(WikidataIds.NETFLIX), DIGITS);
                return v == null ? null : "https://www.netflix.com/title/" + v;
            case "disney":
                v = ok(ids.first(WikidataIds.DISNEY_BROWSE), DISNEY_BROWSE);
                if (v != null) return "https://www.disneyplus.com/browse/" + v;
                v = ok(ids.first(movie ? WikidataIds.DISNEY_MOVIE : WikidataIds.DISNEY_SERIES), DISNEY);
                return v == null ? null : "https://www.disneyplus.com/" + (movie ? "movies/" : "series/") + slug(name) + "/" + v;
            case "appletv":
            case "appletvplus":
                v = ok(ids.first(movie ? WikidataIds.APPLE_MOVIE : WikidataIds.APPLE_SHOW), APPLE);
                return v == null ? null : "https://tv.apple.com/" + (movie ? "movie/" : "show/") + slug(name) + "/" + v;
            case "max":
                v = firstOk(ids.all(WikidataIds.MAX), MAX);
                return v == null ? null : "https://play.hbomax.com/" + v;
            case "prime":
            case "amazonvideo":
                v = ok(ids.first(WikidataIds.PRIME_GTI), GTI);
                if (v == null) v = ok(ids.first(WikidataIds.PRIME_ID), GTI);
                if (v == null) v = ok(ids.first(WikidataIds.AMAZON_ASIN), ASIN);
                if (v != null) return "https://app.primevideo.com/detail?gti=" + v;
                v = ok(ids.first(WikidataIds.PRIME_ID), PRIME_ID);
                return v == null ? null : "https://www.primevideo.com/detail/" + v;
            case "svtplay":
                v = ok(ids.first(WikidataIds.SVT_PLAY), SVT);
                return v == null || v.contains("..") ? null : "https://www.svtplay.se/" + v;
            // The US's: each the service's web address for the title, as Wikidata's formatter URL gives it
            // (Paramount+'s movies under /movies/). Checked on paper only: a TV with each app checks them later.
            case "hulu":
                v = firstGroup(ids.all(movie ? WikidataIds.HULU_MOVIE : WikidataIds.HULU_SERIES), HULU);
                return v == null ? null : "https://www.hulu.com/" + (movie ? "movie/" : "series/") + v;
            case "peacock":
                v = firstGroup(ids.all(WikidataIds.PEACOCK), PEACOCK);
                return v == null ? null : "https://www.peacocktv.com/stream-" + v;
            case "paramountplus":
                v = ok(ids.first(WikidataIds.PARAMOUNT), PARAMOUNT);
                return v == null ? null : "https://www.paramountplus.com/" + (movie ? "movies" : "shows") + "/video/" + v + "/";
            case "tubi":
                v = ok(ids.first(movie ? WikidataIds.TUBI_MOVIE : WikidataIds.TUBI_SERIES), NUMBER);
                return v == null ? null : "https://tubitv.com/" + (movie ? "movies/" : "series/") + v;
            case "fandango":
                v = ok(ids.first(WikidataIds.FANDANGO), NUMBER);
                return v == null ? null : "https://athome.fandango.com/content/browse/details/wd/" + v;
            default:
                return null;
        }
    }

    /** Group 1 of the first value that has the shape (Hulu's id without a stray prefix, Peacock's without "stream-"). */
    private static String firstGroup(List<String> values, Pattern shape) {
        for (String v : values) {
            Matcher m = shape.matcher(v);
            if (m.matches()) return m.group(1);
        }
        return null;
    }

    /** Every service with a link for the title, by {@link WatchProviders} key, in the table's order. */
    static Map<String, String> all(WatchProviders table, Kind kind, WikidataIds ids, String name) {
        Map<String, String> out = new LinkedHashMap<>();
        for (WatchProviders.Entry e : table.all()) {
            String url = link(e.key, kind, ids, name);
            if (url != null) out.put(e.key, url);
        }
        return out;
    }

    private static String ok(String value, Pattern shape) {
        return value != null && shape.matcher(value).matches() ? value : null;
    }

    /** The first of a property's values that has the shape, or null (a title can carry an old and a new id). */
    private static String firstOk(List<String> values, Pattern shape) {
        for (String v : values) if (ok(v, shape) != null) return v;
        return null;
    }

    /** "Le Fabuleux Destin d'Amélie Poulain" becomes "le-fabuleux-destin-d-amelie-poulain"; "x" for nothing left. */
    static String slug(String name) {
        String s = MARKS.matcher(Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "x" : s;
    }
}
