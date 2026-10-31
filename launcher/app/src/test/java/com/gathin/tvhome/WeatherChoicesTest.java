package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Weather pane's choices: the TV default unit, Change city's typing and matches, and the recent places. */
public class WeatherChoicesTest {
    private static final WeatherPlace GOTHENBURG =
            new WeatherPlace("Gothenburg", "Västra Götaland County", "Sweden", 57.70716, 11.96679);
    private static final WeatherPlace MALMO = new WeatherPlace("Malmö", "Skåne County", "Sweden", 55.60587, 13.00073);
    private static final WeatherPlace UMEA = new WeatherPlace("Umeå", "Västerbotten County", "Sweden", 63.82842, 20.25972);
    private static final WeatherPlace KIRUNA = new WeatherPlace("Kiruna", "Norrbotten County", "Sweden", 67.85507, 20.22282);

    // ---- The TV default unit follows the TV's region -------------------------------------------------------------

    @Test
    public void theUsAndItsTerritoriesAreFahrenheit() {
        assertTrue(WeatherSettings.regionFahrenheit("US", null, false));
        assertTrue(WeatherSettings.regionFahrenheit("pr", "", false));
        assertTrue(WeatherSettings.regionFahrenheit("LR", null, false));
    }

    @Test
    public void elsewhereIsCelsiusWhateverTvPropertiesSays() {
        assertFalse(WeatherSettings.regionFahrenheit("SE", null, true));
        assertFalse(WeatherSettings.regionFahrenheit("GB", null, true));
        assertFalse(WeatherSettings.regionFahrenheit("CA", null, true));
    }

    @Test
    public void aTvWithoutARegionFallsBackToTvProperties() {
        assertTrue(WeatherSettings.regionFahrenheit("", null, true));
        assertFalse(WeatherSettings.regionFahrenheit(null, null, false));
    }

    @Test
    public void androidsRegionalPreferenceWins() {
        assertTrue(WeatherSettings.regionFahrenheit("SE", "fahrenhe", false));
        assertFalse(WeatherSettings.regionFahrenheit("US", "celsius", true));
        // Kelvin isn't offered: the region decides.
        assertTrue(WeatherSettings.regionFahrenheit("US", "kelvin", false));
    }

    // ---- Change city's typing -------------------------------------------------------------------------------------

    @Test
    public void anyAlphabetsLettersCanBeTyped() {
        for (char c : "aZ0 -'åäöéüñ".toCharArray()) assertTrue(String.valueOf(c), WeatherPlace.typeable(c));
        for (char c : "/?.,;:!@#\t".toCharArray()) assertFalse(String.valueOf(c), WeatherPlace.typeable(c));
    }

    @Test
    public void theMatchIsTheTypedStartOfTheNameIgnoringCase() {
        assertEquals(5, WeatherPlace.matched("Gothenburg", "gothe"));
        assertEquals(5, WeatherPlace.matched("Gothenburg", " GOTHE "));
        assertEquals(0, WeatherPlace.matched("Gothenburg", "göte"));
        assertEquals(0, WeatherPlace.matched("Göteborgnuten", ""));
        assertEquals(0, WeatherPlace.matched("Ume", "umeå"));
    }

    @Test
    public void theGhostCompletesTheTopMatch() {
        List<WeatherPlace> found = Arrays.asList(GOTHENBURG, MALMO);
        assertEquals("nburg", WeatherPlace.ghost("gothe", found));
        assertNull(WeatherPlace.ghost("gothenburg", found));
        assertNull(WeatherPlace.ghost("malm", found));
        assertNull(WeatherPlace.ghost("gothe ", found));
        assertNull(WeatherPlace.ghost("gothe", Collections.emptyList()));
        assertNull(WeatherPlace.ghost(null, found));
    }

    @Test
    public void theSamePlaceIsTheSameNameAndPosition() {
        assertTrue(WeatherPlace.same(GOTHENBURG, new WeatherPlace("GOTHENBURG", "", "", 57.709, 11.97)));
        assertFalse(WeatherPlace.same(GOTHENBURG, new WeatherPlace("Gothenburg", "Nebraska", "United States",
                40.92945, -100.16068)));
        assertTrue(WeatherPlace.same(null, null));
        assertFalse(WeatherPlace.same(GOTHENBURG, null));
    }

    // ---- Recent places, the quick picks ---------------------------------------------------------------------------

    @Test
    public void thePlaceReplacedComesFirstAndTheChosenOneLeaves() {
        List<WeatherPlace> recent = WeatherSettings.remember(Arrays.asList(MALMO, UMEA), GOTHENBURG, MALMO);
        assertEquals(Arrays.asList(GOTHENBURG, UMEA), recent);
    }

    @Test
    public void theDefaultIsNeverRememberedAndTheListIsCut() {
        assertEquals(Arrays.asList(MALMO, UMEA), WeatherSettings.remember(Arrays.asList(MALMO, UMEA), null, GOTHENBURG));
        List<WeatherPlace> recent = WeatherSettings.remember(Arrays.asList(MALMO, UMEA, KIRUNA), GOTHENBURG, null);
        assertEquals(WeatherSettings.RECENT, recent.size());
        assertEquals(Arrays.asList(GOTHENBURG, MALMO, UMEA), recent);
        assertEquals(Collections.singletonList(GOTHENBURG), WeatherSettings.remember(null, GOTHENBURG, null));
    }

    @Test
    public void repeatsAreRememberedOnce() {
        WeatherPlace again = new WeatherPlace("Malmö", "", "", 55.606, 13.0);
        assertEquals(Arrays.asList(MALMO, UMEA), WeatherSettings.remember(Arrays.asList(again, UMEA), MALMO, null));
    }

    @Test
    public void recentPlacesSurviveBeingSaved() {
        List<WeatherPlace> back = WeatherSettings.recent(WeatherSettings.recentJson(Arrays.asList(GOTHENBURG, MALMO)));
        assertEquals(2, back.size());
        assertEquals("Gothenburg", back.get(0).name);
        assertEquals("Västra Götaland County", back.get(0).region);
        assertEquals("Sweden", back.get(0).country);
        assertEquals(57.70716, back.get(0).latitude, 1e-9);
        assertEquals(13.00073, back.get(1).longitude, 1e-9);
    }

    @Test
    public void damagedRecentPlacesAreLeftOut() {
        assertTrue(WeatherSettings.recent(null).isEmpty());
        assertTrue(WeatherSettings.recent("").isEmpty());
        assertTrue(WeatherSettings.recent("not json").isEmpty());
        List<WeatherPlace> back = WeatherSettings.recent("[{\"name\":\"\",\"latitude\":1,\"longitude\":2},"
                + "{\"name\":\"Nowhere\",\"latitude\":95,\"longitude\":2},3,"
                + "{\"name\":\"Kiruna\",\"latitude\":67.85507,\"longitude\":20.22282}]");
        assertEquals(1, back.size());
        assertEquals("Kiruna", back.get(0).name);
        assertEquals("", back.get(0).region);
    }

    @Test
    public void tvPropertiesUnitWinsWhenSet() {
        // temperature_unit set: it is the TV default, whatever the TV's region says.
        assertFalse(WeatherSettings.defaultFahrenheit("C", "US", null));
        assertTrue(WeatherSettings.defaultFahrenheit("F", "SE", null));
        assertFalse(WeatherSettings.defaultFahrenheit("C", "US", "fahrenhe"));
        // Left out: the region decides, and °C without one.
        assertTrue(WeatherSettings.defaultFahrenheit("", "US", null));
        assertFalse(WeatherSettings.defaultFahrenheit("", "SE", null));
        assertFalse(WeatherSettings.defaultFahrenheit("", "", null));
        assertTrue(WeatherSettings.defaultFahrenheit(null, "SE", "fahrenhe"));
    }
}