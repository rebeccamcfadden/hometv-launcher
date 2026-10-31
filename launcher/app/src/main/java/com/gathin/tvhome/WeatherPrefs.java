package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.Locale;

/**
 * Home's weather place and unit as saved in Launcher settings' preferences (WeatherSettings decides what they mean).
 * The Weather pane moves Launcher settings' change count after every change, so Home rebuilds and its WeatherStore
 * fetches for the new place or unit: the cached answer is kept per request, so the old place's weather never shows
 * for the new.
 */
final class WeatherPrefs {
    private WeatherPrefs() {}

    static WeatherSettings load(Context ctx) {
        return WeatherSettings.resolve(prefs(ctx).getAll(), BuildConfig.WEATHER_LATITUDE,
                BuildConfig.WEATHER_LONGITUDE, defaultFahrenheit());
    }

    /** The TV default unit: tv.properties' temperature_unit when set, else the TV's region's. */
    static boolean defaultFahrenheit() {
        Locale l = Locale.getDefault();
        return WeatherSettings.defaultFahrenheit(BuildConfig.TEMPERATURE_UNIT, l.getCountry(), l.getUnicodeLocaleType("mu"));
    }

    /** What the place from tv.properties is called: its weather_city when set, else the string fallback. */
    static String defaultName(Context ctx, int fallback) {
        String city = BuildConfig.WEATHER_CITY.trim();
        return city.isEmpty() ? ctx.getString(fallback) : city;
    }

    /**
     * Saves the place, or with null goes back to tv.properties' latitude and longitude. The place it replaces joins
     * the recent places ({@link #recent}).
     */
    static void setPlace(Context ctx, WeatherPlace place) {
        WeatherPlace previous = load(ctx).place;
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (place == null) {
            for (String key : WeatherSettings.PLACE_KEYS) e.remove(key);
        } else {
            e.putString(WeatherSettings.KEY_NAME, place.name)
                    .putString(WeatherSettings.KEY_REGION, place.region)
                    .putString(WeatherSettings.KEY_COUNTRY, place.country)
                    .putString(WeatherSettings.KEY_LATITUDE, WeatherSettings.coordinate(place.latitude))
                    .putString(WeatherSettings.KEY_LONGITUDE, WeatherSettings.coordinate(place.longitude));
        }
        e.putString(WeatherSettings.KEY_RECENT,
                WeatherSettings.recentJson(WeatherSettings.remember(recent(ctx), previous, place)));
        e.apply();
    }

    /** The places picked before the current one, newest first: Change city's quick picks. */
    static List<WeatherPlace> recent(Context ctx) {
        return WeatherSettings.recent(prefs(ctx).getString(WeatherSettings.KEY_RECENT, null));
    }

    /** Saves °F (true) or °C (false), or with null goes back to the TV default. */
    static void setFahrenheit(Context ctx, Boolean fahrenheit) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (fahrenheit == null) e.remove(WeatherSettings.KEY_UNIT);
        else e.putString(WeatherSettings.KEY_UNIT, WeatherSettings.unitCode(fahrenheit));
        e.apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
