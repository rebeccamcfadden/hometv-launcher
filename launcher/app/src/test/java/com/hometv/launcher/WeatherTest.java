package com.hometv.launcher;

import org.json.JSONException;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Home's weather: Open-Meteo's answer, the WMO codes as icons and words, and which hours and days show. */
public class WeatherTest {

    /** Shaped like Open-Meteo's answer for New York (timezone=auto, so times are local and the offset is -4 h). */
    private static final String ANSWER = "{"
            + "\"latitude\":40.71,\"longitude\":-74.01,\"utc_offset_seconds\":-14400,\"timezone\":\"America/New_York\","
            + "\"current\":{\"time\":\"2026-10-29T20:45\",\"interval\":900,\"temperature_2m\":13.6,"
            + "\"weather_code\":2,\"is_day\":0},"
            + "\"hourly\":{\"time\":[\"2026-10-29T19:00\",\"2026-10-29T20:00\",\"2026-10-29T21:00\","
            + "\"2026-10-29T22:00\",\"2026-10-29T23:00\",\"2026-10-30T00:00\",\"2026-10-30T01:00\","
            + "\"2026-10-30T02:00\",\"2026-10-30T03:00\"],"
            + "\"temperature_2m\":[15.1,14.2,13.8,13.5,12.6,12.9,12.4,null,11.6],"
            + "\"weather_code\":[1,2,0,0,3,3,0,0,61],"
            + "\"is_day\":[1,0,0,0,0,0,0,0,0]},"
            + "\"daily\":{\"time\":[\"2026-10-29\",\"2026-10-30\",\"2026-10-31\"],"
            + "\"weather_code\":[2,3,61],\"temperature_2m_max\":[16.4,13.2,11.0],"
            + "\"temperature_2m_min\":[8.6,7.9,6.5]}}";

    private static LocalDateTime t(String s) {
        return LocalDateTime.parse(s);
    }

    @Test
    public void parsesCurrentHoursAndDays() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        assertEquals(-14400, w.utcOffsetSeconds);
        assertEquals(14, w.current.temp);
        assertEquals(2, w.current.code);
        assertFalse(w.current.day);
        // The 02:00 hour has no temperature and is skipped.
        assertEquals(8, w.hours.size());
        assertEquals(t("2026-10-29T19:00"), w.hours.get(0).time);
        assertEquals(15, w.hours.get(0).temp);
        assertTrue(w.hours.get(0).day);
        assertEquals(3, w.days.size());
        assertEquals(16, w.days.get(0).max);
        assertEquals(9, w.days.get(0).min);
    }

    @Test(expected = JSONException.class)
    public void anAnswerWithoutWeatherIsRejected() throws JSONException {
        Weather.parse("{\"error\":true,\"reason\":\"Latitude must be in range of -90 to 90°.\"}");
    }

    @Test(expected = JSONException.class)
    public void garbageIsRejected() throws JSONException {
        Weather.parse("<html>");
    }

    @Test
    public void hoursWithoutIsDayCountAsDay() throws JSONException {
        Weather w = Weather.parse("{\"hourly\":{\"time\":[\"2026-10-29T12:00\"],\"temperature_2m\":[-0.4],"
                + "\"weather_code\":[0]}}");
        assertNull(w.current);
        assertEquals(1, w.hours.size());
        assertEquals(0, w.hours.get(0).temp);
        assertEquals(Weather.Icon.SUN, w.hours.get(0).icon());
    }

    @Test
    public void localTimeFollowsTheLocationsOffset() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        // 2026-10-30 00:45 UTC is 20:45 on the 29th in New York (-4 h: US summer time runs until 1 November).
        long utc = java.time.LocalDateTime.of(2026, 10, 30, 0, 45).toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
        assertEquals(t("2026-10-29T20:45"), w.localTime(utc));
    }

    @Test
    public void nowIsTheCurrentReadingWhileItIsFresh() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        assertSame(w.current, w.now(t("2026-10-29T20:50")));
        assertSame(w.current, w.now(t("2026-10-29T21:30")));
    }

    @Test
    public void nowFallsBackToThisHoursForecast() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        Weather.Reading r = w.now(t("2026-10-29T23:10"));
        assertNotNull(r);
        assertEquals(t("2026-10-29T23:00"), r.time);
        assertEquals(13, r.temp);
        assertEquals(Weather.Icon.CLOUD, r.icon());
    }

    @Test
    public void nowIsNothingOnceTheDataNoLongerCoversIt() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        assertNull(w.now(t("2026-11-02T12:00")));
    }

    @Test
    public void aClockBehindTheDataStillShowsTheCurrentReading() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        assertSame(w.current, w.now(t("2026-10-29T18:10")));
    }

    @Test
    public void theNextHoursStartAfterNow() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        List<Weather.Reading> next = w.nextHours(t("2026-10-29T20:49"), 6);
        assertEquals(6, next.size());
        assertEquals(t("2026-10-29T21:00"), next.get(0).time);
        // 02:00 had no temperature, so the sixth is 03:00.
        assertEquals(t("2026-10-30T03:00"), next.get(5).time);
        assertEquals(Weather.Icon.MOON, next.get(0).icon());
    }

    @Test
    public void onTheHourTheNextHourIsTheFollowingOne() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        assertEquals(t("2026-10-29T22:00"), w.nextHours(t("2026-10-29T21:00"), 6).get(0).time);
    }

    @Test
    public void anOldCacheShowsFewerHours() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        assertEquals(2, w.nextHours(t("2026-10-30T00:30"), 6).size());
        assertTrue(w.nextHours(t("2026-10-31T00:00"), 6).isEmpty());
    }

    @Test
    public void daysByDate() throws JSONException {
        Weather w = Weather.parse(ANSWER);
        assertEquals(13, w.day(LocalDate.parse("2026-10-30")).max);
        assertNull(w.day(LocalDate.parse("2026-11-05")));
    }

    @Test
    public void codesAsConditions() {
        assertEquals(Weather.Condition.CLEAR, Weather.condition(0));
        assertEquals(Weather.Condition.MAINLY_CLEAR, Weather.condition(1));
        assertEquals(Weather.Condition.PARTLY_CLOUDY, Weather.condition(2));
        assertEquals(Weather.Condition.OVERCAST, Weather.condition(3));
        assertEquals(Weather.Condition.FOG, Weather.condition(48));
        assertEquals(Weather.Condition.DRIZZLE, Weather.condition(53));
        assertEquals(Weather.Condition.FREEZING_DRIZZLE, Weather.condition(57));
        assertEquals(Weather.Condition.RAIN, Weather.condition(65));
        assertEquals(Weather.Condition.FREEZING_RAIN, Weather.condition(66));
        assertEquals(Weather.Condition.SNOW, Weather.condition(71));
        assertEquals(Weather.Condition.SNOW_GRAINS, Weather.condition(77));
        assertEquals(Weather.Condition.RAIN_SHOWERS, Weather.condition(82));
        assertEquals(Weather.Condition.SNOW_SHOWERS, Weather.condition(85));
        assertEquals(Weather.Condition.THUNDERSTORM, Weather.condition(95));
        assertEquals(Weather.Condition.THUNDER_HAIL, Weather.condition(99));
        assertEquals(Weather.Condition.UNKNOWN, Weather.condition(4));
        assertEquals(Weather.Condition.UNKNOWN, Weather.condition(-1));
    }

    @Test
    public void codesAsThePrototypesIcons() {
        assertEquals(Weather.Icon.SUN, Weather.icon(0, true));
        assertEquals(Weather.Icon.MOON, Weather.icon(0, false));
        assertEquals(Weather.Icon.SUN, Weather.icon(2, true));
        assertEquals(Weather.Icon.MOON, Weather.icon(1, false));
        assertEquals(Weather.Icon.CLOUD, Weather.icon(3, true));
        assertEquals(Weather.Icon.CLOUD, Weather.icon(45, false));
        assertEquals(Weather.Icon.CLOUD, Weather.icon(61, true));
        assertEquals(Weather.Icon.CLOUD, Weather.icon(95, false));
    }

    @Test
    public void hourLabels() {
        assertEquals("9 PM", Weather.hourLabel(21, false));
        assertEquals("12 AM", Weather.hourLabel(0, false));
        assertEquals("12 PM", Weather.hourLabel(12, false));
        assertEquals("1 AM", Weather.hourLabel(1, false));
        assertEquals("21:00", Weather.hourLabel(21, true));
        assertEquals("00:00", Weather.hourLabel(0, true));
    }

    @Test
    public void theQueryAsksForWhatTheParserReads() {
        String q = WeatherStore.query(40.7128, -74.006, false);
        assertTrue(q.startsWith("https://api.open-meteo.com/v1/forecast?latitude=40.7128&longitude=-74.0060"));
        assertTrue(q.contains("current=temperature_2m,weather_code,is_day"));
        assertTrue(q.contains("hourly=temperature_2m,weather_code,is_day"));
        assertTrue(q.contains("daily=weather_code,temperature_2m_max,temperature_2m_min"));
        assertTrue(q.contains("timezone=auto"));
        assertFalse(q.contains("fahrenheit"));
        assertTrue(WeatherStore.query(40.7, -74.0, true).contains("longitude=-74.0000"));
        assertTrue(WeatherStore.query(40.7, -74.0, true).endsWith("&temperature_unit=fahrenheit"));
    }

    @Test
    public void theWeatherIsTriedAgainWhenTheInternetComesBack() {
        int wifi = 1, ethernet = 2, off = 3;
        // The first state a listener is told is not a change.
        assertFalse(WeatherStore.networkBack(0, wifi, false, true));
        // The same Wi-Fi network, validated again (a router whose uplink came back): the icon does not change.
        assertTrue(WeatherStore.networkBack(wifi, wifi, false, true));
        assertFalse(WeatherStore.networkBack(wifi, wifi, true, true));
        assertFalse(WeatherStore.networkBack(wifi, wifi, true, false));
        // Another network, or back from offline.
        assertTrue(WeatherStore.networkBack(wifi, ethernet, true, true));
        assertTrue(WeatherStore.networkBack(off, wifi, false, false));
    }
}
