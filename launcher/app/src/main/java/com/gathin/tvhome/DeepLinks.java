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
package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Links that open a title inside a service's TV app rather than on its home screen (BACKLOG.md, Title deep links),
 * built from the ids {@link WikidataIds} found. Stage 2 sends each as an ACTION_VIEW intent limited to the service's
 * package (Netflix also wants the extra {@code source=30}) and falls back to opening the app when nothing takes it.
 * Wikidata has ids for Netflix, Disney+, Apple TV, HBO Max, Prime Video and SVT Play; TV4 Play, Viaplay, SkyShowtime
 * and the Swedish stores have no Wikidata property, so they open on their home screen. Every id is checked against
 * the shape Wikidata documents for it before it goes into a link. Pure Java, unit-tested (DeepLinksTest).
 */
final class DeepLinks {
    private DeepLinks() {}

    private static final Pattern DIGITS = Pattern.compile("\\d{4,10}");
    private static final Pattern DISNEY = Pattern.compile("[0-9A-Za-z]{12}");
    private static final Pattern DISNEY_BROWSE = Pattern.compile("entity-[0-9a-f-]{36}");
    private static final Pattern APPLE = Pattern.compile("umc\\.cmc\\.[a-z0-9]{20,30}");
    private static final Pattern MAX = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:/._-]{0,120}");
    private static final Pattern GTI = Pattern.compile("amzn1\\.dv\\.gti\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern ASIN = Pattern.compile("B[0-9A-Z]{9}");
    private static final Pattern PRIME_ID = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.-]{5,63}");
    private static final Pattern SVT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}");
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
                v = ok(ids.first(WikidataIds.MAX), MAX);
                return v == null || v.contains("..") ? null : "https://play.hbomax.com/" + v;
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
            default:
                return null;
        }
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

    /** "Le Fabuleux Destin d'Amélie Poulain" becomes "le-fabuleux-destin-d-amelie-poulain"; "x" for nothing left. */
    static String slug(String name) {
        String s = MARKS.matcher(Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "x" : s;
    }
}
