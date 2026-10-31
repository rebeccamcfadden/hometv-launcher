package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A place for Home's weather, chosen on the TV in Launcher settings (BACKLOG.md, Weather location on the TV), and
 * Open-Meteo's place search that finds it (no key; the only thing sent is what was typed). Adapted from the weather
 * add-on's Forecast.search, copied rather than shared: the launcher never depends on an add-on. Pure Java so the
 * parsing is tested on the JVM.
 */
final class WeatherPlace {
    /** Fewer letters than this match half the world; the picker waits for more. */
    static final int MIN_QUERY = 2;
    static final int RESULTS = 10;

    final String name;
    /** The region (Open-Meteo's admin1), or "" when the answer has none. */
    final String region;
    /** The country, or "" (Open-Meteo leaves it out for places such as Svalbard). */
    final String country;
    final double latitude;
    final double longitude;

    WeatherPlace(String name, String region, String country, double latitude, double longitude) {
        this.name = name;
        this.region = region == null ? "" : region;
        this.country = country == null ? "" : country;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** "Västra Götaland County, Sweden": the line under the name in the results. */
    String detail() {
        if (region.isEmpty()) return country;
        if (country.isEmpty()) return region;
        return region + ", " + country;
    }

    /** "Gothenburg, Sweden": how Launcher settings names the chosen place. */
    String label() {
        return country.isEmpty() ? name : name + ", " + country;
    }

    static boolean valid(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude) && Math.abs(latitude) <= 90
                && Math.abs(longitude) <= 180;
    }

    /** Whether what was typed is worth a search: at least {@link #MIN_QUERY} letters once trimmed. */
    static boolean searchable(String typed) {
        return typed != null && typed.trim().length() >= MIN_QUERY;
    }

    /**
     * Whether a keyboard character goes into Change city's search (design_preview weather.ts TYPEABLE): letters of any
     * alphabet, so "Göteborg" and "Malmö" can be typed, digits, a space, a hyphen and an apostrophe.
     */
    static boolean typeable(char c) {
        return Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'';
    }

    /** How many of the name's first letters are what was typed, ignoring case: the highlighted part of a match. */
    static int matched(String name, String typed) {
        String t = typed == null ? "" : typed.trim();
        if (t.isEmpty() || name.length() < t.length()) return 0;
        return name.regionMatches(true, 0, t, 0, t.length()) ? t.length() : 0;
    }

    /**
     * The ghost completion after what was typed (the search bar's grey rest of the word): the top match's name past
     * the typed letters, in lower case, when it begins with them; null otherwise.
     */
    static String ghost(String typed, List<WeatherPlace> places) {
        if (typed == null || places == null || places.isEmpty()) return null;
        String name = places.get(0).name;
        int n = matched(name, typed);
        if (n == 0 || n != typed.length() || n >= name.length()) return null;
        return name.substring(n).toLowerCase(Locale.ROOT);
    }

    /** The same place: the same name and, to about a kilometre, the same position. */
    static boolean same(WeatherPlace a, WeatherPlace b) {
        if (a == null || b == null) return a == b;
        return a.name.equalsIgnoreCase(b.name) && Math.abs(a.latitude - b.latitude) < 0.01
                && Math.abs(a.longitude - b.longitude) < 0.01;
    }

    /**
     * The search request in one language. Open-Meteo matches names in the language asked for (and returns them in it):
     * in English it finds "Gothenburg" but not "Göteborg", so a search asks in each of {@link #searchLanguages}.
     */
    static String searchUrl(String typed, String language) {
        String lang = language == null || language.trim().isEmpty() ? "en" : language.trim().toLowerCase(Locale.ROOT);
        try {
            return "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(typed.trim(), "UTF-8")
                    + "&count=" + RESULTS + "&language=" + URLEncoder.encode(lang, "UTF-8") + "&format=json";
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * The languages a search asks in: the TV's (English when it has none), then English and Swedish. Open-Meteo only
     * matches a city's name in the language asked for, so "Göteborg" is found on an English TV only by asking in
     * Swedish too.
     */
    static List<String> searchLanguages(String tvLanguage) {
        List<String> out = new ArrayList<>();
        String tv = tvLanguage == null || tvLanguage.trim().isEmpty() ? "en" : tvLanguage.trim().toLowerCase(Locale.ROOT);
        for (String l : new String[] {tv, "en", "sv"}) if (!out.contains(l)) out.add(l);
        return out;
    }

    /**
     * One list from the answers in each language, the TV's first: a place already listed (within about a kilometre,
     * whatever its name in that language) isn't listed again, places named exactly what was typed come first, and
     * there are at most {@link #RESULTS}. A null answer (that language's search failed) is skipped.
     */
    static List<WeatherPlace> merge(String typed, List<List<WeatherPlace>> answers) {
        List<WeatherPlace> all = new ArrayList<>();
        for (List<WeatherPlace> answer : answers) {
            if (answer == null) continue;
            for (WeatherPlace p : answer) {
                boolean listed = false;
                for (WeatherPlace q : all) {
                    if (Math.abs(p.latitude - q.latitude) < 0.01 && Math.abs(p.longitude - q.longitude) < 0.01) {
                        listed = true;
                        break;
                    }
                }
                if (!listed) all.add(p);
            }
        }
        String t = fold(typed);
        List<WeatherPlace> out = new ArrayList<>();
        for (WeatherPlace p : all) if (fold(p.name).equals(t)) out.add(p);
        for (WeatherPlace p : all) if (!fold(p.name).equals(t)) out.add(p);
        return out.size() > RESULTS ? new ArrayList<>(out.subList(0, RESULTS)) : out;
    }

    /** Trimmed, lower case and without accents, so "goteborg" names Göteborg exactly. */
    private static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * The places in a search answer, best match first. An answer with no "results" (nothing matched) is an empty
     * list; a result without a name or a usable position is skipped.
     */
    static List<WeatherPlace> parseSearch(String json) throws JSONException {
        return parseSearch(json, null);
    }

    /**
     * As {@link #parseSearch(String)}, with each country named in countriesIn's language from its country code when
     * the answer has one (an answer in Swedish says "Sverige"; an English TV shows "Sweden").
     */
    static List<WeatherPlace> parseSearch(String json, Locale countriesIn) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (o.optBoolean("error")) throw new JSONException(o.optString("reason", "error"));
        List<WeatherPlace> out = new ArrayList<>();
        JSONArray results = o.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            String name = text(r, "name");
            double lat = r.optDouble("latitude", Double.NaN);
            double lon = r.optDouble("longitude", Double.NaN);
            if (name.isEmpty() || !valid(lat, lon)) continue;
            out.add(new WeatherPlace(name, text(r, "admin1"), country(r, countriesIn), lat, lon));
        }
        return out;
    }

    private static String country(JSONObject r, Locale in) {
        String given = text(r, "country");
        String code = text(r, "country_code");
        if (in == null || code.length() != 2) return given;
        String named = new Locale("", code).getDisplayCountry(in);
        // An unknown code comes back as itself: keep the answer's name then.
        return named.isEmpty() || named.equalsIgnoreCase(code) ? given : named;
    }

    /** A string field, "" when it is missing or null (optString gives "null" for a JSON null). */
    private static String text(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key).trim();
    }
}
