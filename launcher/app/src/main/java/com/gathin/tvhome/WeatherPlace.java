package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
     * The search request. Open-Meteo matches names in the language asked for (and returns them in it): an English TV
     * finds "Gothenburg", a Swedish one "Göteborg" too, so the TV's language is passed, English when it has none.
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
     * The places in a search answer, best match first. An answer with no "results" (nothing matched) is an empty
     * list; a result without a name or a usable position is skipped.
     */
    static List<WeatherPlace> parseSearch(String json) throws JSONException {
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
            out.add(new WeatherPlace(name, text(r, "admin1"), text(r, "country"), lat, lon));
        }
        return out;
    }

    /** A string field, "" when it is missing or null (optString gives "null" for a JSON null). */
    private static String text(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key).trim();
    }
}
