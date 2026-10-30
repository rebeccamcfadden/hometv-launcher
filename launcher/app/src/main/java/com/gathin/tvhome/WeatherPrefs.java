package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Home's weather place and unit as saved in Launcher settings' preferences (WeatherSettings decides what they mean).
 * The callers move Launcher settings' change count after every change, so Home rebuilds and its WeatherStore fetches
 * for the new place or unit at once: the cached answer is kept per request, so the old place's weather never shows
 * for the new.
 */
final class WeatherPrefs {
    private WeatherPrefs() {}

    static WeatherSettings load(Context ctx) {
        return WeatherSettings.resolve(prefs(ctx).getAll(), BuildConfig.WEATHER_LATITUDE,
                BuildConfig.WEATHER_LONGITUDE, BuildConfig.FAHRENHEIT);
    }

    /** Saves the place, or with null goes back to tv.properties' latitude and longitude. */
    static void setPlace(Context ctx, WeatherPlace place) {
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
        e.apply();
    }

    /** Saves °F (true) or °C (false), or with null goes back to tv.properties' temperature_unit. */
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
