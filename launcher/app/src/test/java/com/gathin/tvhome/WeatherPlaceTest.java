package com.gathin.tvhome;

import org.json.JSONException;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The weather place chosen on the TV: Open-Meteo's place search, the override of tv.properties, and the units. */
public class WeatherPlaceTest {

    /** Trimmed from a real answer for "Gothenburg" (language=en, count=10); ids and extra fields left out. */
    private static final String GOTHENBURG = "{\"results\":["
            + "{\"id\":2711537,\"name\":\"Gothenburg\",\"latitude\":57.70716,\"longitude\":11.96679,"
            + "\"feature_code\":\"PPLA\",\"country_code\":\"SE\",\"timezone\":\"America/New_York\","
            + "\"population\":608462,\"country\":\"Sweden\",\"admin1\":\"Västra Götaland County\","
            + "\"admin2\":\"Gothenburg Municipality\"},"
            + "{\"id\":5695743,\"name\":\"Gothenburg\",\"latitude\":40.92945,\"longitude\":-100.16068,"
            + "\"country\":\"United States\",\"admin1\":\"Nebraska\",\"admin2\":\"Dawson\"},"
            // Svalbard: no country in the answer.
            + "{\"id\":2730934,\"name\":\"Göteborgnuten\",\"latitude\":79.63333,\"longitude\":11.33333,"
            + "\"country_code\":\"SJ\",\"admin1\":\"Svalbard\"},"
            // Neither region nor country.
            + "{\"name\":\"Nowhere Island\",\"latitude\":-10.5,\"longitude\":170.25},"
            // Broken entries, skipped.
            + "{\"name\":\"No position\",\"country\":\"Sweden\"},"
            + "{\"name\":\"\",\"latitude\":1,\"longitude\":2},"
            + "{\"name\":\"Off the map\",\"latitude\":95,\"longitude\":2},"
            + "{\"name\":\"Null region\",\"latitude\":1,\"longitude\":2,\"admin1\":null,\"country\":null}"
            + "],\"generationtime_ms\":0.12}";

    @Test
    public void parsesTheSearchAnswer() throws JSONException {
        List<WeatherPlace> places = WeatherPlace.parseSearch(GOTHENBURG);
        assertEquals(5, places.size());
        WeatherPlace g = places.get(0);
        assertEquals("Gothenburg", g.name);
        assertEquals("Västra Götaland County", g.region);
        assertEquals("Sweden", g.country);
        assertEquals(57.70716, g.latitude, 1e-9);
        assertEquals(11.96679, g.longitude, 1e-9);
        assertEquals(-100.16068, places.get(1).longitude, 1e-9);
        assertEquals("Göteborgnuten", places.get(2).name);
        assertEquals("", places.get(2).country);
        assertEquals("Null region", places.get(4).name);
        assertEquals("", places.get(4).region);
        assertEquals("", places.get(4).country);
    }

    @Test
    public void noMatchIsAnEmptyList() throws JSONException {
        assertTrue(WeatherPlace.parseSearch("{\"generationtime_ms\":0.06}").isEmpty());
        assertTrue(WeatherPlace.parseSearch("{\"results\":[]}").isEmpty());
    }

    @Test(expected = JSONException.class)
    public void anErrorAnswerIsRejected() throws JSONException {
        WeatherPlace.parseSearch("{\"error\":true,\"reason\":\"Parameter count must be between 1 and 100.\"}");
    }

    @Test(expected = JSONException.class)
    public void notJsonIsRejected() throws JSONException {
        WeatherPlace.parseSearch("<html>Bad gateway</html>");
    }

    @Test
    public void detailAndLabel() {
        WeatherPlace g = new WeatherPlace("Gothenburg", "Västra Götaland County", "Sweden", 57.7, 11.97);
        assertEquals("Västra Götaland County, Sweden", g.detail());
        assertEquals("Gothenburg, Sweden", g.label());
        WeatherPlace svalbard = new WeatherPlace("Göteborgnuten", "Svalbard", "", 79.6, 11.3);
        assertEquals("Svalbard", svalbard.detail());
        assertEquals("Göteborgnuten", svalbard.label());
        WeatherPlace bare = new WeatherPlace("Nowhere", null, null, 0, 0);
        assertEquals("", bare.detail());
        assertEquals("Nowhere", bare.label());
        assertEquals("Monaco", new WeatherPlace("Monaco", "", "Monaco", 43.7, 7.4).detail());
    }

    @Test
    public void searchUrlEncodesTheNameAndAsksInTheTvsLanguage() {
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=G%C3%B6teborg&count=10&language=sv"
                + "&format=json", WeatherPlace.searchUrl("  Göteborg ", "sv"));
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=New+York&count=10&language=en"
                + "&format=json", WeatherPlace.searchUrl("New York", ""));
        assertTrue(WeatherPlace.searchUrl("a&b=c", null).contains("name=a%26b%3Dc&"));
        assertTrue(WeatherPlace.searchUrl("x", "EN").contains("&language=en&"));
    }

    @Test
    public void searchWaitsForTwoLetters() {
        assertFalse(WeatherPlace.searchable(null));
        assertFalse(WeatherPlace.searchable(""));
        assertFalse(WeatherPlace.searchable(" G  "));
        assertTrue(WeatherPlace.searchable("Gö"));
    }

    // ---- WeatherSettings: what is saved on the TV over tv.properties -------------------------------------------

    private static final double NEW_YORK_LAT = 40.7128;
    private static final double NEW_YORK_LON = -74.0060;

    private static Map<String, Object> gothenburg() {
        Map<String, Object> m = new HashMap<>();
        m.put(WeatherSettings.KEY_NAME, "Gothenburg");
        m.put(WeatherSettings.KEY_REGION, "Västra Götaland County");
        m.put(WeatherSettings.KEY_COUNTRY, "Sweden");
        m.put(WeatherSettings.KEY_LATITUDE, "57.70716");
        m.put(WeatherSettings.KEY_LONGITUDE, "11.96679");
        return m;
    }

    @Test
    public void nothingSavedUsesTvProperties() {
        WeatherSettings s = WeatherSettings.resolve(new HashMap<>(), NEW_YORK_LAT, NEW_YORK_LON, true);
        assertNull(s.place);
        assertNull(s.fahrenheitChoice);
        assertEquals(NEW_YORK_LAT, s.latitude, 0);
        assertEquals(NEW_YORK_LON, s.longitude, 0);
        assertTrue(s.fahrenheit);
        assertTrue(s.defaultFahrenheit);
        assertNull(WeatherSettings.resolve(null, 1, 2, false).place);
    }

    @Test
    public void aSavedPlaceOverridesTheLocationOnly() {
        WeatherSettings s = WeatherSettings.resolve(gothenburg(), NEW_YORK_LAT, NEW_YORK_LON, true);
        assertEquals("Gothenburg", s.place.name);
        assertEquals("Sweden", s.place.country);
        assertEquals(57.70716, s.latitude, 1e-9);
        assertEquals(11.96679, s.longitude, 1e-9);
        // The unit still comes from tv.properties.
        assertNull(s.fahrenheitChoice);
        assertTrue(s.fahrenheit);
    }

    @Test
    public void aSavedUnitOverridesTheUnitOnly() {
        Map<String, Object> m = new HashMap<>();
        m.put(WeatherSettings.KEY_UNIT, "C");
        WeatherSettings c = WeatherSettings.resolve(m, NEW_YORK_LAT, NEW_YORK_LON, true);
        assertSame(Boolean.FALSE, c.fahrenheitChoice);
        assertFalse(c.fahrenheit);
        assertTrue(c.defaultFahrenheit);
        assertNull(c.place);
        assertEquals(NEW_YORK_LAT, c.latitude, 0);
        m.put(WeatherSettings.KEY_UNIT, "f");
        assertTrue(WeatherSettings.resolve(m, 0, 0, false).fahrenheit);
        m.put(WeatherSettings.KEY_UNIT, "K");
        WeatherSettings unknown = WeatherSettings.resolve(m, 0, 0, false);
        assertNull(unknown.fahrenheitChoice);
        assertFalse(unknown.fahrenheit);
    }

    @Test
    public void aBrokenPlaceFallsBackToTvProperties() {
        Map<String, Object> noName = gothenburg();
        noName.remove(WeatherSettings.KEY_NAME);
        Map<String, Object> badLat = gothenburg();
        badLat.put(WeatherSettings.KEY_LATITUDE, "north");
        Map<String, Object> offMap = gothenburg();
        offMap.put(WeatherSettings.KEY_LONGITUDE, "181");
        Map<String, Object> nan = gothenburg();
        nan.put(WeatherSettings.KEY_LATITUDE, "NaN");
        Map<String, Object> half = gothenburg();
        half.remove(WeatherSettings.KEY_LONGITUDE);
        for (Map<String, Object> m : java.util.Arrays.asList(noName, badLat, offMap, nan, half)) {
            WeatherSettings s = WeatherSettings.resolve(m, NEW_YORK_LAT, NEW_YORK_LON, false);
            assertNull(s.place);
            assertEquals(NEW_YORK_LAT, s.latitude, 0);
            assertEquals(NEW_YORK_LON, s.longitude, 0);
        }
    }

    @Test
    public void aPlaceWithoutRegionOrCountryIsKept() {
        Map<String, Object> m = gothenburg();
        m.remove(WeatherSettings.KEY_REGION);
        m.remove(WeatherSettings.KEY_COUNTRY);
        WeatherSettings s = WeatherSettings.resolve(m, 0, 0, false);
        assertEquals("Gothenburg", s.place.label());
    }

    @Test
    public void coordinatesRoundTripAsPlainText() {
        assertEquals("57.70716", WeatherSettings.coordinate(57.70716));
        assertEquals("-100.16068", WeatherSettings.coordinate(-100.16068));
        Map<String, Object> m = gothenburg();
        m.put(WeatherSettings.KEY_LATITUDE, WeatherSettings.coordinate(-33.86785));
        m.put(WeatherSettings.KEY_LONGITUDE, WeatherSettings.coordinate(151.20732));
        WeatherSettings s = WeatherSettings.resolve(m, 0, 0, false);
        assertEquals(-33.86785, s.latitude, 0);
        assertEquals(151.20732, s.longitude, 0);
    }

    @Test
    public void theWeatherRequestFollowsTheResolvedSettings() {
        Map<String, Object> m = gothenburg();
        m.put(WeatherSettings.KEY_UNIT, "C");
        WeatherSettings s = WeatherSettings.resolve(m, NEW_YORK_LAT, NEW_YORK_LON, true);
        String q = WeatherStore.query(s.latitude, s.longitude, s.fahrenheit);
        assertTrue(q.contains("latitude=57.7072&longitude=11.9668&"));
        assertFalse(q.contains("fahrenheit"));
        WeatherSettings d = WeatherSettings.resolve(new HashMap<>(), NEW_YORK_LAT, NEW_YORK_LON, true);
        String dq = WeatherStore.query(d.latitude, d.longitude, d.fahrenheit);
        assertTrue(dq.contains("latitude=40.7128&longitude=-74.0060&"));
        assertTrue(dq.endsWith("&temperature_unit=fahrenheit"));
    }

    @Test
    public void units() {
        assertEquals("C", WeatherSettings.unitCode(false));
        assertEquals("F", WeatherSettings.unitCode(true));
        assertEquals("\u00B0C", WeatherSettings.unitSymbol(false));
        assertEquals("\u00B0F", WeatherSettings.unitSymbol(true));
        assertEquals("12\u00B0", Weather.degrees(12));
        assertEquals("-3\u00B0", Weather.degrees(-3));
        assertEquals("12\u00B0C", Weather.degrees(12, false));
        assertEquals("54\u00B0F", Weather.degrees(54, true));
    }
}
