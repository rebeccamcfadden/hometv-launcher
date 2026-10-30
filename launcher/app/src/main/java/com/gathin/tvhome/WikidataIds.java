package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The streaming services' own ids for a title, from Wikidata, found by its TMDB id (P4947 for a movie, P4983 for a
 * show) with one SPARQL query to query.wikidata.org. {@link DeepLinks} turns them into links that open the title in
 * each app, and {@link ItunesPrices} looks up Apple's prices by the iTunes ids. Kept a month
 * ({@link PriceCachePolicy#DEEP_LINK_TTL}): Wikidata gains ids slowly. No key; Wikimedia asks for a User-Agent that
 * says who is asking. Pure Java, unit-tested with a recorded answer (WikidataIdsTest).
 */
final class WikidataIds {
    static final String ID = "wikidata";
    static final String ENDPOINT = "https://query.wikidata.org/sparql";
    static final String USER_AGENT = "CleanYourTV-launcher/2 (https://github.com/rebeccamcfadden/cleanyourtv)";

    static final String NETFLIX = "P1874";
    static final String DISNEY_MOVIE = "P7595";
    static final String DISNEY_SERIES = "P7596";
    static final String DISNEY_BROWSE = "P13902";
    static final String APPLE_MOVIE = "P9586";
    static final String APPLE_SHOW = "P9751";
    static final String MAX = "P8298";
    static final String AMAZON_ASIN = "P8055";
    static final String PRIME_ID = "P14440";
    static final String PRIME_GTI = "P14462";
    static final String SVT_PLAY = "P6817";
    static final String ITUNES_MOVIE = "P6398";
    static final String ITUNES_SEASON = "P6381";

    private static final String[] MOVIE = {
        NETFLIX, DISNEY_MOVIE, DISNEY_BROWSE, APPLE_MOVIE, MAX, AMAZON_ASIN, PRIME_ID, PRIME_GTI, SVT_PLAY, ITUNES_MOVIE
    };
    private static final String[] SHOW = {
        NETFLIX, DISNEY_SERIES, DISNEY_BROWSE, APPLE_SHOW, MAX, AMAZON_ASIN, PRIME_ID, PRIME_GTI, SVT_PLAY, ITUNES_SEASON
    };

    /** The Wikidata item ("Q20382729"), or null when Wikidata has none for the TMDB id. */
    final String item;
    private final Map<String, List<String>> values;

    WikidataIds(String item, Map<String, List<String>> values) {
        this.item = item;
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> e : values.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        this.values = Collections.unmodifiableMap(copy);
    }

    static WikidataIds none() {
        return new WikidataIds(null, Collections.emptyMap());
    }

    /** Every value of a property, in Wikidata's order; empty when it has none. */
    List<String> all(String property) {
        List<String> v = values.get(property);
        return v == null ? Collections.emptyList() : v;
    }

    /** The first value of a property, or null. */
    String first(String property) {
        List<String> v = all(property);
        return v.isEmpty() ? null : v.get(0);
    }

    /** The iTunes ids for {@link PriceTitle#itunesIds}: the movie's, or the show's seasons'. */
    List<String> itunesIds(Kind kind) {
        return all(kind == Kind.MOVIE ? ITUNES_MOVIE : ITUNES_SEASON);
    }

    /** The properties asked for a movie or a show. */
    static String[] properties(Kind kind) {
        return (kind == Kind.MOVIE ? MOVIE : SHOW).clone();
    }

    /**
     * The SPARQL query: the item with that TMDB id, and each property's values joined with "|" into a variable named
     * after the property.
     */
    static String query(Kind kind, int tmdbId) {
        StringBuilder select = new StringBuilder("SELECT ?item");
        StringBuilder where = new StringBuilder(" WHERE { ?item wdt:")
                .append(kind == Kind.MOVIE ? "P4947" : "P4983").append(" \"").append(tmdbId).append("\" .");
        for (String p : properties(kind)) {
            select.append(" (GROUP_CONCAT(DISTINCT ?v").append(p).append(";separator=\"|\") AS ?").append(p).append(')');
            where.append(" OPTIONAL { ?item wdt:").append(p).append(" ?v").append(p).append(" . }");
        }
        return select.append(where).append(" } GROUP BY ?item").toString();
    }

    static String url(Kind kind, int tmdbId) {
        try {
            return ENDPOINT + "?format=json&query=" + URLEncoder.encode(query(kind, tmdbId), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    static Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", USER_AGENT);
        h.put("Accept", "application/sparql-results+json");
        return h;
    }

    static WikidataIds fetch(Kind kind, int tmdbId, HttpGet http) throws IOException {
        try {
            return parse(http.get(url(kind, tmdbId), headers()), kind);
        } catch (JSONException e) {
            throw new IOException("Wikidata: unexpected answer", e);
        }
    }

    /**
     * The first item's ids from a SPARQL JSON answer. When two items share the TMDB id (a duplicate Wikidata hasn't
     * merged yet) the first one is used. A null answer or no rows gives {@link #none()}.
     */
    static WikidataIds parse(String json, Kind kind) throws JSONException {
        if (json == null) return none();
        JSONArray rows = new JSONObject(json).getJSONObject("results").getJSONArray("bindings");
        if (rows.length() == 0) return none();
        JSONObject row = rows.getJSONObject(0);
        String uri = cell(row, "item");
        if (uri.isEmpty()) return none();
        String item = uri.substring(uri.lastIndexOf('/') + 1);
        Map<String, List<String>> values = new HashMap<>();
        for (String p : properties(kind)) {
            String joined = cell(row, p);
            List<String> list = new ArrayList<>();
            for (String v : joined.split("\\|")) if (!v.trim().isEmpty()) list.add(v.trim());
            if (!list.isEmpty()) values.put(p, list);
        }
        return new WikidataIds(item, values);
    }

    private static String cell(JSONObject row, String name) {
        JSONObject c = row.optJSONObject(name);
        return c == null ? "" : c.optString("value", "");
    }
}
