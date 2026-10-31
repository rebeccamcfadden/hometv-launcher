package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where and in which unit Home's weather is fetched: the place and the unit chosen in Launcher settings when there
 * are any, else the defaults: launcher/tv.properties' place (BuildConfig) and the unit of the TV's region. Each
 * overrides on its own, and clearing one goes back to the default. Read from the settings map so the precedence is
 * tested on the JVM; WeatherPrefs writes it.
 */
final class WeatherSettings {
    static final String KEY_NAME = "weather_place_name";
    static final String KEY_REGION = "weather_place_region";
    static final String KEY_COUNTRY = "weather_place_country";
    /** Saved as text ("57.70716"), so the stored file reads plainly. */
    static final String KEY_LATITUDE = "weather_latitude";
    static final String KEY_LONGITUDE = "weather_longitude";
    /** "C" or "F"; absent for the TV default ({@link #defaultFahrenheit}). */
    static final String KEY_UNIT = "temperature_unit";
    /** The places picked before the current one, as JSON ({@link #recentJson}). */
    static final String KEY_RECENT = "weather_recent";

    static final String[] PLACE_KEYS = {KEY_NAME, KEY_REGION, KEY_COUNTRY, KEY_LATITUDE, KEY_LONGITUDE};

    /** The chosen place, or null for the build's latitude and longitude. */
    final WeatherPlace place;
    /** The chosen unit, or null for the TV default. */
    final Boolean fahrenheitChoice;
    final double latitude;
    final double longitude;
    final boolean fahrenheit;
    final boolean defaultFahrenheit;

    private WeatherSettings(WeatherPlace place, Boolean fahrenheitChoice, double defaultLatitude,
                            double defaultLongitude, boolean defaultFahrenheit) {
        this.place = place;
        this.fahrenheitChoice = fahrenheitChoice;
        this.latitude = place != null ? place.latitude : defaultLatitude;
        this.longitude = place != null ? place.longitude : defaultLongitude;
        this.fahrenheit = fahrenheitChoice != null ? fahrenheitChoice : defaultFahrenheit;
        this.defaultFahrenheit = defaultFahrenheit;
    }

    /**
     * The settings saved on the TV over the defaults. A place is used only when its name and a valid
     * position are all there (a half-written or hand-edited entry falls back to the default); an unknown unit is
     * the default too.
     */
    static WeatherSettings resolve(Map<String, ?> saved, double defaultLatitude, double defaultLongitude,
                                   boolean defaultFahrenheit) {
        WeatherPlace place = null;
        String name = string(saved, KEY_NAME);
        double lat = number(saved, KEY_LATITUDE);
        double lon = number(saved, KEY_LONGITUDE);
        if (!name.isEmpty() && WeatherPlace.valid(lat, lon)) {
            place = new WeatherPlace(name, string(saved, KEY_REGION), string(saved, KEY_COUNTRY), lat, lon);
        }
        Boolean unit = null;
        String u = string(saved, KEY_UNIT);
        if (u.equalsIgnoreCase("F")) unit = Boolean.TRUE;
        else if (u.equalsIgnoreCase("C")) unit = Boolean.FALSE;
        return new WeatherSettings(place, unit, defaultLatitude, defaultLongitude, defaultFahrenheit);
    }

    /** "C" or "F", as saved under {@link #KEY_UNIT}. */
    static String unitCode(boolean fahrenheit) {
        return fahrenheit ? "F" : "C";
    }

    /** Where °F is the everyday unit (CLDR's temperature preference): the US and its territories, and a few more. */
    private static final List<String> FAHRENHEIT_REGIONS = Arrays.asList(
            "US", "AS", "GU", "MP", "PR", "UM", "VI", "BS", "BZ", "KY", "LR", "FM", "MH", "PW");

    /**
     * The TV default unit (the project lead, 2026-10-31): tv.properties' temperature_unit ("C" or "F") when it is set;
     * otherwise the TV's region: the unit its locale asks for (the "mu" extension Android 14's regional preferences
     * set), else °F in {@link #FAHRENHEIT_REGIONS} and °C elsewhere, and °C on a TV without a region.
     */
    static boolean defaultFahrenheit(String configured, String region, String unitExtension) {
        if ("F".equals(configured)) return true;
        if ("C".equals(configured)) return false;
        return regionFahrenheit(region, unitExtension, false);
    }

    /** The TV's region's unit (see {@link #defaultFahrenheit}); fallback when the TV has no region. */
    static boolean regionFahrenheit(String region, String unitExtension, boolean fallback) {
        if ("fahrenhe".equals(unitExtension)) return true;
        if ("celsius".equals(unitExtension)) return false;
        String r = region == null ? "" : region.trim().toUpperCase(Locale.ROOT);
        if (r.isEmpty()) return fallback;
        return FAHRENHEIT_REGIONS.contains(r);
    }

    /** At most this many places picked before the current one, for Change city's quick picks. */
    static final int RECENT = 3;

    /**
     * The places picked before, newest first, after picking chosen: the place it replaces comes first, chosen and
     * repeats go, and the list is cut to {@link #RECENT}. Null stands for the default place, which is never listed.
     */
    static List<WeatherPlace> remember(List<WeatherPlace> recent, WeatherPlace previous, WeatherPlace chosen) {
        List<WeatherPlace> out = new ArrayList<>();
        List<WeatherPlace> all = new ArrayList<>();
        all.add(previous);
        if (recent != null) all.addAll(recent);
        for (WeatherPlace p : all) {
            if (p == null || WeatherPlace.same(p, chosen) || out.size() >= RECENT) continue;
            boolean seen = false;
            for (WeatherPlace q : out) seen |= WeatherPlace.same(p, q);
            if (!seen) out.add(p);
        }
        return out;
    }

    /** The recent places as saved under {@link #KEY_RECENT}. */
    static String recentJson(List<WeatherPlace> places) {
        JSONArray a = new JSONArray();
        try {
            for (WeatherPlace p : places) {
                a.put(new JSONObject().put("name", p.name).put("region", p.region).put("country", p.country)
                        .put("latitude", p.latitude).put("longitude", p.longitude));
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return a.toString();
    }

    /** The recent places saved under {@link #KEY_RECENT}; a damaged entry, or a damaged list, is left out. */
    static List<WeatherPlace> recent(String json) {
        List<WeatherPlace> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length() && out.size() < RECENT; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "").trim();
                double lat = o.optDouble("latitude", Double.NaN);
                double lon = o.optDouble("longitude", Double.NaN);
                if (name.isEmpty() || !WeatherPlace.valid(lat, lon)) continue;
                out.add(new WeatherPlace(name, o.optString("region", ""), o.optString("country", ""), lat, lon));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return out;
    }

    /** "°C" or "°F". */
    static String unitSymbol(boolean fahrenheit) {
        return "\u00B0" + unitCode(fahrenheit);
    }

    /** A coordinate as saved: plain decimal text, the same on every locale. */
    static String coordinate(double v) {
        return Double.toString(v);
    }

    private static String string(Map<String, ?> saved, String key) {
        Object v = saved == null ? null : saved.get(key);
        return v instanceof String ? ((String) v).trim() : "";
    }

    private static double number(Map<String, ?> saved, String key) {
        Object v = saved == null ? null : saved.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (!(v instanceof String)) return Double.NaN;
        try {
            return Double.parseDouble(((String) v).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
