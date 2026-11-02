package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

public class DayPhaseTest {
    /** Sweden's and Norway's clocks are the same (CET, CEST in summer): Gothenburg is read on Oslo's. */
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final double OSLO_LAT = 59.91, OSLO_LON = 10.75;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final double BERLIN_LAT = 52.52, BERLIN_LON = 13.40;
    private static final double GOTHENBURG_LAT = 57.71, GOTHENBURG_LON = 11.97;

    private static ZonedDateTime at(int y, int m, int d, int h, int min, ZoneId zone) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, zone);
    }

    /** Sunrise is 40 minutes after dawn starts; sunset 50 minutes after sunset starts. */
    private static LocalTime sunrise(DayPhase.Day d, ZoneId zone) {
        return d.edges[0].plusSeconds(DayPhase.DAWN_BEFORE_MIN * 60).atZone(zone).toLocalTime();
    }

    private static LocalTime sunset(DayPhase.Day d, ZoneId zone) {
        return d.edges[2].plusSeconds(DayPhase.SUNSET_BEFORE_MIN * 60).atZone(zone).toLocalTime();
    }

    private static void near(LocalTime expected, LocalTime actual) {
        long off = Math.abs(Duration.between(expected, actual).toMinutes());
        assertTrue("expected about " + expected + " but was " + actual, off <= 3);
    }

    @Test
    public void midsummerInOslo() {
        DayPhase.Day d = DayPhase.day(LocalDate.of(2026, 6, 21), OSLO, OSLO_LAT, OSLO_LON);
        assertNull(d.always);
        near(LocalTime.of(3, 54), sunrise(d, OSLO));
        near(LocalTime.of(22, 43), sunset(d, OSLO));
    }

    @Test
    public void midwinterInGothenburg() {
        DayPhase.Day d = DayPhase.day(LocalDate.of(2026, 12, 21), OSLO, GOTHENBURG_LAT, GOTHENBURG_LON);
        near(LocalTime.of(8, 56), sunrise(d, OSLO));
        near(LocalTime.of(15, 23), sunset(d, OSLO));
    }

    @Test
    public void newYorkInSpring() {
        DayPhase.Day d = DayPhase.day(LocalDate.of(2026, 4, 1), NEW_YORK, 40.71, -74.01);
        near(LocalTime.of(6, 39), sunrise(d, NEW_YORK));
        near(LocalTime.of(19, 19), sunset(d, NEW_YORK));
    }

    @Test
    public void phasesThroughADay() {
        double lat = GOTHENBURG_LAT, lon = GOTHENBURG_LON;
        assertEquals(DayPhase.NIGHT, DayPhase.at(at(2026, 12, 21, 7, 0, OSLO), lat, lon));
        assertEquals(DayPhase.DAWN, DayPhase.at(at(2026, 12, 21, 8, 40, OSLO), lat, lon));
        assertEquals(DayPhase.DAY, DayPhase.at(at(2026, 12, 21, 12, 0, OSLO), lat, lon));
        assertEquals(DayPhase.SUNSET, DayPhase.at(at(2026, 12, 21, 15, 0, OSLO), lat, lon));
        assertEquals(DayPhase.NIGHT, DayPhase.at(at(2026, 12, 21, 16, 30, OSLO), lat, lon));
    }

    @Test
    public void nextChangeIsTheNextEdge() {
        double lat = GOTHENBURG_LAT, lon = GOTHENBURG_LON;
        ZonedDateTime noon = at(2026, 12, 21, 12, 0, OSLO);
        DayPhase.Day d = DayPhase.day(noon.toLocalDate(), OSLO, lat, lon);
        assertEquals(d.edges[2], DayPhase.nextChange(noon, lat, lon).toInstant());
        ZonedDateTime late = at(2026, 12, 21, 23, 0, OSLO);
        DayPhase.Day tomorrow = DayPhase.day(LocalDate.of(2026, 12, 22), OSLO, lat, lon);
        assertEquals(tomorrow.edges[0], DayPhase.nextChange(late, lat, lon).toInstant());
        // The edge itself belongs to the phase it starts.
        assertEquals(DayPhase.SUNSET, DayPhase.at(d.edges[2].atZone(OSLO), lat, lon));
    }

    @Test
    public void polarDayAndNight() {
        double tromsoLat = 69.65, tromsoLon = 18.96;
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        assertEquals(DayPhase.DAY, DayPhase.at(at(2026, 6, 21, 1, 0, oslo), tromsoLat, tromsoLon));
        assertEquals(DayPhase.NIGHT, DayPhase.at(at(2026, 12, 21, 12, 0, oslo), tromsoLat, tromsoLon));
        assertEquals(at(2026, 6, 22, 0, 0, oslo), DayPhase.nextChange(at(2026, 6, 21, 13, 0, oslo), tromsoLat,
                tromsoLon));
    }

    @Test
    public void aSunsetPastMidnightKeepsGoing() {
        // Reykjavík in midsummer: the sun sets just after midnight, so sunset runs on into the next date.
        ZoneId iceland = ZoneId.of("Atlantic/Reykjavik");
        double lat = 64.15, lon = -21.94;
        ZonedDateTime late = at(2026, 6, 23, 0, 20, iceland);
        assertEquals(DayPhase.SUNSET, DayPhase.at(late, lat, lon));
        ZonedDateTime next = DayPhase.nextChange(late, lat, lon);
        assertTrue(next.isAfter(late) && next.isBefore(at(2026, 6, 23, 1, 30, iceland)));
        assertEquals(DayPhase.NIGHT, DayPhase.at(next, lat, lon));
        // Every moment's phase holds until nextChange says otherwise.
        ZonedDateTime t = at(2026, 6, 20, 0, 0, iceland);
        while (t.isBefore(at(2026, 6, 26, 0, 0, iceland))) {
            DayPhase p = DayPhase.at(t, lat, lon);
            ZonedDateTime n = DayPhase.nextChange(t, lat, lon);
            assertTrue(n.isAfter(t));
            assertEquals(t.toString(), p, DayPhase.at(n.minusSeconds(1), lat, lon));
            t = t.plusMinutes(7);
        }
    }

    @Test
    public void aShortNightGoesStraightFromSunsetToDawn() {
        // 65.6 N on 22 June: yesterday's sunset window runs past the start of today's dawn.
        double lat = 65.6, lon = 18.07;
        DayPhase prev = null;
        boolean handedOver = false;
        ZonedDateTime t = at(2026, 6, 20, 12, 0, OSLO);
        while (t.isBefore(at(2026, 6, 25, 12, 0, OSLO))) {
            DayPhase p = DayPhase.at(t, lat, lon);
            if (prev != null && p != prev) {
                boolean forward = p == next(prev) || (prev == DayPhase.SUNSET && p == DayPhase.DAWN);
                assertTrue(t + ": " + prev + " -> " + p, forward);
                handedOver |= prev == DayPhase.SUNSET && p == DayPhase.DAWN;
            }
            ZonedDateTime n = DayPhase.nextChange(t, lat, lon);
            assertTrue(n.isAfter(t));
            assertEquals(t.toString(), p, DayPhase.at(n.minusSeconds(1), lat, lon));
            prev = p;
            t = t.plusMinutes(3);
        }
        assertTrue("the short nights hand sunset straight to dawn", handedOver);
    }

    private static DayPhase next(DayPhase p) {
        return DayPhase.values()[(p.ordinal() + 1) % 4];
    }

    @Test
    public void noonIsDayEveryDayAllRoundTheWorld() {
        // Each place's own solar cycle: rounding without the longitude picked the neighbouring one on some days far
        // from Greenwich, such as Auckland on 4 April 2026, when New Zealand's clocks go back.
        Object[][] places = {
                {"Pacific/Auckland", -36.85, 174.76}, {"Pacific/Kiritimati", 1.87, -157.4},
                {"Pacific/Apia", -13.83, -171.76}, {"Pacific/Honolulu", 21.31, -157.86},
                {"America/Los_Angeles", 34.05, -118.24}, {"America/New_York", 40.71, -74.01},
                {"Europe/Oslo", OSLO_LAT, OSLO_LON}, {"Asia/Tokyo", 35.68, 139.69}};
        for (Object[] place : places) {
            ZoneId zone = ZoneId.of((String) place[0]);
            double lat = (Double) place[1], lon = (Double) place[2];
            for (LocalDate d = LocalDate.of(2026, 1, 1); d.getYear() == 2026; d = d.plusDays(1)) {
                ZonedDateTime noon = d.atTime(13, 0).atZone(zone);
                assertEquals(place[0] + " " + d, DayPhase.DAY, DayPhase.at(noon, lat, lon));
            }
        }
        assertEquals(DayPhase.DAY, DayPhase.at(at(2026, 4, 4, 12, 0, ZoneId.of("Pacific/Auckland")), -36.85, 174.76));
    }

    @Test
    public void theTvsZoneNeverSkipsACycleAtThePlace() {
        // The TV in Pacific/Chatham (UTC+12:45, +13:45 in summer), the Weather place in Berlin: counting cycles by the
        // TV's local noon skipped one across Chatham's daylight-saving change, and the place's noon read NIGHT. It
        // shows only for a place east of about 11.3 E (the old rounding of Chatham's noon plus lon/360 crossed 0.5),
        // so not Oslo.
        ZoneId chatham = ZoneId.of("Pacific/Chatham");
        ZonedDateTime berlinNoon = Instant.parse("2026-04-04T10:00:00Z").atZone(chatham);
        assertEquals(DayPhase.DAY, DayPhase.at(berlinNoon, BERLIN_LAT, BERLIN_LON));
        for (String tv : new String[] {"Pacific/Chatham", "Pacific/Kiritimati", "Pacific/Apia", "America/Los_Angeles"}) {
            ZoneId zone = ZoneId.of(tv);
            for (LocalDate d = LocalDate.of(2026, 1, 1); d.getYear() == 2026; d = d.plusDays(1)) {
                ZonedDateTime noon = d.atTime(13, 0).atZone(BERLIN).withZoneSameInstant(zone);
                assertEquals(tv + " " + d, DayPhase.DAY, DayPhase.at(noon, BERLIN_LAT, BERLIN_LON));
            }
            // Across Chatham's change (5 April) and Berlin's (29 March): every phase holds until nextChange.
            ZonedDateTime t = at(2026, 3, 27, 0, 0, zone);
            while (t.isBefore(at(2026, 4, 8, 0, 0, zone))) {
                DayPhase p = DayPhase.at(t, BERLIN_LAT, BERLIN_LON);
                ZonedDateTime n = DayPhase.nextChange(t, BERLIN_LAT, BERLIN_LON);
                assertTrue(n.isAfter(t));
                assertEquals(tv + " " + t, p, DayPhase.at(n.minusSeconds(1), BERLIN_LAT, BERLIN_LON));
                t = t.plusMinutes(11);
            }
        }
    }

    @Test
    public void aPolarDayStartsAtItsMidnight() {
        // Pevek: the sun stops setting on 19 May.
        ZoneId magadan = ZoneId.of("Asia/Magadan");
        double lat = 69.7, lon = 170.3;
        assertEquals(DayPhase.DAY, DayPhase.at(at(2026, 5, 19, 0, 30, magadan), lat, lon));
        ZonedDateTime evening = at(2026, 5, 18, 23, 30, magadan);
        assertTrue(!DayPhase.nextChange(evening, lat, lon).isAfter(at(2026, 5, 19, 0, 0, magadan)));
    }

    @Test
    public void shortDayHandsDawnStraightToSunset() {
        // Just south of the polar circle in midwinter: about an hour of sun.
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        DayPhase.Day d = DayPhase.day(LocalDate.of(2026, 12, 21), oslo, 67.0, 14.1);
        assertNull(d.always);
        assertEquals(d.edges[1], d.edges[2]);
        assertTrue(d.edges[0].isBefore(d.edges[1]) && d.edges[2].isBefore(d.edges[3]));
    }

    @Test
    public void withoutAPlaceTheFixedTimes() {
        DayPhase.Day d = DayPhase.day(LocalDate.of(2026, 3, 1), OSLO, Double.NaN, Double.NaN);
        assertEquals(LocalTime.of(6, 20), d.edges[0].atZone(OSLO).toLocalTime());
        assertEquals(LocalTime.of(19, 40), d.edges[3].atZone(OSLO).toLocalTime());
        assertEquals(DayPhase.DAY, DayPhase.at(at(2026, 3, 1, 12, 0, OSLO), Double.NaN, Double.NaN));
    }

    @Test
    public void slugs() {
        assertEquals("dawn", DayPhase.DAWN.slug());
        assertEquals("sunset", DayPhase.SUNSET.slug());
    }

    @Test
    public void timeOfDayFixesDayOrNightOrFollowsTheSun() {
        assertEquals(DayPhase.DAY, DayPhase.fixed("day"));
        assertEquals(DayPhase.NIGHT, DayPhase.fixed("night"));
        assertNull(DayPhase.fixed("sun"));
        // Older or odd values follow the sun, as before F29.
        assertNull(DayPhase.fixed(null));
        assertNull(DayPhase.fixed("dusk"));
    }

    @Test
    public void anUnknownSavedTimeOfDayFollowsTheSun() {
        assertEquals("day", DayPhase.timeOfDay("day"));
        assertEquals("night", DayPhase.timeOfDay("night"));
        assertEquals("sun", DayPhase.timeOfDay("sun"));
        assertEquals("sun", DayPhase.timeOfDay(null));
        assertEquals("sun", DayPhase.timeOfDay("dusk"));
        // A value saved as another type (an old switch) never throws.
        assertEquals("sun", DayPhase.timeOfDay(Boolean.TRUE));
    }
}