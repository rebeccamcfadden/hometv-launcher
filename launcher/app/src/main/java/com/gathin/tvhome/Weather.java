package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Home's weather (design_preview/src/screens/home.ts: the forecast under the clock and the hourly strip), parsed
 * from an Open-Meteo forecast. Pure Java so it is tested on the JVM: the parsing, the WMO weather codes as the
 * prototype's three icons and a short word, and which hours and days to show for a given moment. The data may be a
 * cached answer hours old, so everything is picked by the location's own clock, not by where the answer started.
 */
final class Weather {

    enum Icon { SUN, MOON, CLOUD }

    /** The short words under the temperature ("Partly cloudy"); WeatherStrings turns them into text. */
    enum Condition {
        CLEAR, MAINLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, FREEZING_DRIZZLE, RAIN, FREEZING_RAIN, SNOW,
        SNOW_GRAINS, RAIN_SHOWERS, SNOW_SHOWERS, THUNDERSTORM, THUNDER_HAIL, UNKNOWN
    }

    /** A reading at one local time: the current conditions or one hour of the forecast. */
    static final class Reading {
        final LocalDateTime time;
        final int temp;
        final int code;
        final boolean day;

        Reading(LocalDateTime time, int temp, int code, boolean day) {
            this.time = time;
            this.temp = temp;
            this.code = code;
            this.day = day;
        }

        Icon icon() {
            return Weather.icon(code, day);
        }
    }

    static final class Day {
        final LocalDate date;
        final int max;
        final int min;
        final int code;

        Day(LocalDate date, int max, int min, int code) {
            this.date = date;
            this.max = max;
            this.min = min;
            this.code = code;
        }
    }

    /** How long the current conditions stand in for "now" before the hourly forecast takes over. */
    static final long CURRENT_VALID_MINUTES = 60;

    final int utcOffsetSeconds;
    final Reading current;
    final List<Reading> hours;
    final List<Day> days;

    Weather(int utcOffsetSeconds, Reading current, List<Reading> hours, List<Day> days) {
        this.utcOffsetSeconds = utcOffsetSeconds;
        this.current = current;
        this.hours = Collections.unmodifiableList(hours);
        this.days = Collections.unmodifiableList(days);
    }

    /**
     * An Open-Meteo /v1/forecast answer asked for current temperature_2m, weather_code and is_day; the same hourly;
     * daily weather_code, temperature_2m_max and temperature_2m_min; with timezone=auto. Hours and days with a missing
     * value are skipped; an answer with neither current conditions nor any hour is not weather.
     */
    static Weather parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        int offset = o.optInt("utc_offset_seconds", 0);
        Reading current = null;
        JSONObject c = o.optJSONObject("current");
        if (c != null && c.has("temperature_2m") && c.has("weather_code") && !c.isNull("temperature_2m")) {
            LocalDateTime t = time(c.optString("time"));
            if (t != null) {
                current = new Reading(t, round(c.getDouble("temperature_2m")), c.getInt("weather_code"),
                        c.optInt("is_day", 1) == 1);
            }
        }
        List<Reading> hours = new ArrayList<>();
        JSONObject h = o.optJSONObject("hourly");
        if (h != null) {
            JSONArray times = h.optJSONArray("time");
            JSONArray temps = h.optJSONArray("temperature_2m");
            JSONArray codes = h.optJSONArray("weather_code");
            JSONArray isDay = h.optJSONArray("is_day");
            int n = times == null || temps == null || codes == null ? 0
                    : Math.min(times.length(), Math.min(temps.length(), codes.length()));
            for (int i = 0; i < n; i++) {
                LocalDateTime t = time(times.optString(i));
                if (t == null || temps.isNull(i) || codes.isNull(i)) continue;
                boolean day = isDay == null || i >= isDay.length() || isDay.optInt(i, 1) == 1;
                hours.add(new Reading(t, round(temps.getDouble(i)), codes.getInt(i), day));
            }
        }
        List<Day> days = new ArrayList<>();
        JSONObject d = o.optJSONObject("daily");
        if (d != null) {
            JSONArray dates = d.optJSONArray("time");
            JSONArray max = d.optJSONArray("temperature_2m_max");
            JSONArray min = d.optJSONArray("temperature_2m_min");
            JSONArray codes = d.optJSONArray("weather_code");
            int n = dates == null || max == null || min == null || codes == null ? 0
                    : Math.min(Math.min(dates.length(), codes.length()), Math.min(max.length(), min.length()));
            for (int i = 0; i < n; i++) {
                LocalDate date = date(dates.optString(i));
                if (date == null || max.isNull(i) || min.isNull(i) || codes.isNull(i)) continue;
                days.add(new Day(date, round(max.getDouble(i)), round(min.getDouble(i)), codes.getInt(i)));
            }
        }
        if (current == null && hours.isEmpty()) throw new JSONException("no current conditions and no hours");
        return new Weather(offset, current, hours, days);
    }

    /** The location's wall-clock time at this instant. */
    LocalDateTime localTime(long utcMillis) {
        return LocalDateTime.ofEpochSecond(Math.floorDiv(utcMillis, 1000L), 0, ZoneOffset.ofTotalSeconds(utcOffsetSeconds));
    }

    /**
     * The conditions now: the current reading while it is under an hour old, else the forecast for this hour. Null
     * when the data no longer covers now (a cache from days ago).
     */
    Reading now(LocalDateTime now) {
        if (current != null) {
            long age = ChronoUnit.MINUTES.between(current.time, now);
            if (age >= 0 && age < CURRENT_VALID_MINUTES) return current;
        }
        LocalDateTime hour = now.truncatedTo(ChronoUnit.HOURS);
        for (Reading r : hours) if (r.time.equals(hour)) return r;
        // A clock behind the data (or data fetched a moment ago, in the same hour) still shows what was fetched.
        if (current != null && !now.isAfter(current.time)) return current;
        return null;
    }

    /** Up to count hours of the forecast, starting with the next full hour after now (the prototype's 9 PM at 8:49). */
    List<Reading> nextHours(LocalDateTime now, int count) {
        List<Reading> out = new ArrayList<>();
        for (Reading r : hours) {
            if (!r.time.isAfter(now)) continue;
            out.add(r);
            if (out.size() == count) break;
        }
        return out;
    }

    /** The forecast for a date, or null when the data has none. */
    Day day(LocalDate date) {
        for (Day d : days) if (d.date.equals(date)) return d;
        return null;
    }

    /** WMO weather interpretation codes (Open-Meteo's weather_code). */
    static Condition condition(int code) {
        switch (code) {
            case 0:
                return Condition.CLEAR;
            case 1:
                return Condition.MAINLY_CLEAR;
            case 2:
                return Condition.PARTLY_CLOUDY;
            case 3:
                return Condition.OVERCAST;
            case 45:
            case 48:
                return Condition.FOG;
            case 51:
            case 53:
            case 55:
                return Condition.DRIZZLE;
            case 56:
            case 57:
                return Condition.FREEZING_DRIZZLE;
            case 61:
            case 63:
            case 65:
                return Condition.RAIN;
            case 66:
            case 67:
                return Condition.FREEZING_RAIN;
            case 71:
            case 73:
            case 75:
                return Condition.SNOW;
            case 77:
                return Condition.SNOW_GRAINS;
            case 80:
            case 81:
            case 82:
                return Condition.RAIN_SHOWERS;
            case 85:
            case 86:
                return Condition.SNOW_SHOWERS;
            case 95:
                return Condition.THUNDERSTORM;
            case 96:
            case 99:
                return Condition.THUNDER_HAIL;
            default:
                return Condition.UNKNOWN;
        }
    }

    /**
     * The prototype has three weather icons: sun or moon for a clear to partly cloudy sky, by day or night, and a
     * cloud for everything greyer (overcast, fog, rain, snow, storms).
     */
    static Icon icon(int code, boolean day) {
        if (code >= 0 && code <= 2) return day ? Icon.SUN : Icon.MOON;
        return Icon.CLOUD;
    }

    /** An hour's label: "9 PM" on a 12-hour clock, "21:00" on a 24-hour one. */
    static String hourLabel(int hour, boolean is24) {
        if (is24) return String.format(Locale.ROOT, "%02d:00", hour);
        int h = hour % 12 == 0 ? 12 : hour % 12;
        return h + (hour < 12 ? " AM" : " PM");
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    private static LocalDateTime time(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate date(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
