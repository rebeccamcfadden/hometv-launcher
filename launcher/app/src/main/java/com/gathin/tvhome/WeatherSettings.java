package com.gathin.tvhome;

import java.util.Map;

/**
 * Where and in which unit Home's weather is fetched: the place and the unit chosen in Launcher settings when there
 * are any, else launcher/tv.properties (BuildConfig). Each overrides on its own, and clearing one goes back to the
 * build's value. Read from the settings map so the precedence is tested on the JVM; WeatherPrefs writes it.
 */
final class WeatherSettings {
    static final String KEY_NAME = "weather_place_name";
    static final String KEY_REGION = "weather_place_region";
    static final String KEY_COUNTRY = "weather_place_country";
    /** Saved as text ("57.70716"), so the stored file reads plainly. */
    static final String KEY_LATITUDE = "weather_latitude";
    static final String KEY_LONGITUDE = "weather_longitude";
    /** "C" or "F"; absent for tv.properties' temperature_unit. */
    static final String KEY_UNIT = "temperature_unit";

    static final String[] PLACE_KEYS = {KEY_NAME, KEY_REGION, KEY_COUNTRY, KEY_LATITUDE, KEY_LONGITUDE};

    /** The chosen place, or null for the build's latitude and longitude. */
    final WeatherPlace place;
    /** The chosen unit, or null for the build's. */
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
     * The settings saved on the TV over the build's defaults. A place is used only when its name and a valid
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
