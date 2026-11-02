package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherFallTest {
    private static WeatherFall.Fall of(Weather.Condition c, SeasonCalendar.Period p) {
        return WeatherFall.of(c, p);
    }

    @Test
    public void theRealWeatherFirst() {
        assertEquals(WeatherFall.Fall.SNOW, of(Weather.Condition.SNOW, SeasonCalendar.Period.SNOW));
        assertEquals(WeatherFall.Fall.SNOW, of(Weather.Condition.SNOW_SHOWERS, SeasonCalendar.Period.CHRISTMAS));
        assertEquals(WeatherFall.Fall.RAIN, of(Weather.Condition.RAIN, SeasonCalendar.Period.MIDSUMMER));
        assertEquals(WeatherFall.Fall.RAIN, of(Weather.Condition.DRIZZLE, SeasonCalendar.Period.SNOW));
        assertEquals(WeatherFall.Fall.RAIN, of(Weather.Condition.THUNDERSTORM, SeasonCalendar.Period.BLOOM));
        // A clear day in a snowy period: nothing falls, the picture's own still flakes are enough.
        assertEquals(WeatherFall.Fall.NONE, of(Weather.Condition.CLEAR, SeasonCalendar.Period.SNOW));
        assertEquals(WeatherFall.Fall.NONE, of(Weather.Condition.OVERCAST, SeasonCalendar.Period.SPRING_RAIN));
        assertEquals(WeatherFall.Fall.NONE, of(Weather.Condition.UNKNOWN, SeasonCalendar.Period.SNOW));
    }

    @Test
    public void noSnowOverASummerPicture() {
        assertEquals(WeatherFall.Fall.NONE, of(Weather.Condition.SNOW, SeasonCalendar.Period.MIDSUMMER));
        assertEquals(WeatherFall.Fall.NONE, of(Weather.Condition.SNOW, SeasonCalendar.Period.BLOOM));
        assertEquals(WeatherFall.Fall.SNOW, of(Weather.Condition.SNOW, SeasonCalendar.Period.AUTUMN));
        assertFalse(WeatherFall.cold(SeasonCalendar.Period.PRIDE));
    }

    @Test
    public void theSeasonWithoutTheWeather() {
        assertEquals(WeatherFall.Fall.SNOW, of(null, SeasonCalendar.Period.SNOW));
        assertEquals(WeatherFall.Fall.SNOW, of(null, SeasonCalendar.Period.CHRISTMAS));
        assertEquals(WeatherFall.Fall.RAIN, of(null, SeasonCalendar.Period.SPRING_RAIN));
        assertEquals(WeatherFall.Fall.NONE, of(null, SeasonCalendar.Period.LATE_SUMMER));
        assertEquals(WeatherFall.Fall.NONE, of(null, SeasonCalendar.Period.HALLOWEEN));
    }

    @Test
    public void onlyAChangeInWhatFallsRearms() {
        SeasonCalendar.Period snow = SeasonCalendar.Period.SNOW;
        // The rain clears, or arrives: stop, or start once Home is idle.
        assertTrue(WeatherFall.changes(Weather.Condition.RAIN, Weather.Condition.CLEAR, snow));
        assertTrue(WeatherFall.changes(Weather.Condition.OVERCAST, Weather.Condition.RAIN, SeasonCalendar.Period.BLOOM));
        // Back online with clear skies over the Snow period's offline snow.
        assertTrue(WeatherFall.changes(null, Weather.Condition.CLEAR, snow));
        // The same reading each minute, or another kind of rain: the fall goes on uninterrupted.
        assertFalse(WeatherFall.changes(Weather.Condition.RAIN, Weather.Condition.RAIN, snow));
        assertFalse(WeatherFall.changes(Weather.Condition.DRIZZLE, Weather.Condition.RAIN_SHOWERS, snow));
        assertFalse(WeatherFall.changes(null, Weather.Condition.SNOW, snow));
        // Snow over a summer picture falls as nothing, so it changes nothing.
        assertFalse(WeatherFall.changes(Weather.Condition.CLEAR, Weather.Condition.SNOW, SeasonCalendar.Period.MIDSUMMER));
    }

    @Test
    public void aPreviewIsSnowOrRainOnly() {
        assertEquals(WeatherFall.Fall.SNOW, WeatherFall.preview("snow"));
        assertEquals(WeatherFall.Fall.RAIN, WeatherFall.preview(" Rain "));
        assertNull(WeatherFall.preview(null));
        assertNull(WeatherFall.preview(""));
        assertNull(WeatherFall.preview("none"));
        assertNull(WeatherFall.preview("hail"));
        assertNull(WeatherFall.preview(true));
    }
}
