package com.gathin.tvhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The part of the day a Seasonal theme's picture shows (F22, the project lead's decision of 2026-11-01): dawn, day,
 * sunset or night, from the real sun at the Weather place. Sunrise and sunset are worked out offline with the
 * sunrise equation (NOAA's simplified form, to about a minute), so it needs no network. Dawn runs from 40 minutes
 * before sunrise to 50 after, sunset from 50 minutes before sunset to 40 after; day and night fill the rest. Where
 * the sun doesn't rise or set that day (inside the polar circles) the whole day is day or night. Without a place, the
 * fixed times are dawn 06:20, day 07:50, sunset 18:10 and night 19:40.
 */
enum DayPhase {
    DAWN, DAY, SUNSET, NIGHT;

    static final long DAWN_BEFORE_MIN = 40, DAWN_AFTER_MIN = 50, SUNSET_BEFORE_MIN = 50, SUNSET_AFTER_MIN = 40;
    private static final LocalTime[] FIXED = {
            LocalTime.of(6, 20), LocalTime.of(7, 50), LocalTime.of(18, 10), LocalTime.of(19, 40)};

    /** The picture name part: dawn, day, sunset, night. */
    String slug() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The phase at a moment, at a place (NaN for none: the fixed times): the one started by the last edge before it. A
     * sunset that runs past midnight, or a dawn that starts before it, belongs to its own date, so yesterday's and
     * tomorrow's edges count too.
     */
    static DayPhase at(ZonedDateTime now, double latitude, double longitude) {
        Instant t = now.toInstant();
        DayPhase phase = NIGHT;
        for (Edge e : edges(now.toLocalDate().minusDays(1), 3, now.getZone(), latitude, longitude)) {
            if (e.at.isAfter(t)) break;
            phase = e.starts;
        }
        return phase;
    }

    /** When the phase next changes after a moment (a polar day or night changes at midnight). */
    static ZonedDateTime nextChange(ZonedDateTime now, double latitude, double longitude) {
        Instant t = now.toInstant();
        ZoneId zone = now.getZone();
        for (Edge e : edges(now.toLocalDate().minusDays(1), 4, zone, latitude, longitude)) {
            if (e.at.isAfter(t)) return e.at.atZone(zone);
        }
        return now.toLocalDate().plusDays(1).atStartOfDay(zone);
    }

    /** A moment the phase changes to another. */
    static final class Edge {
        final Instant at;
        final DayPhase starts;

        Edge(Instant at, DayPhase starts) {
            this.at = at;
            this.starts = starts;
        }
    }

    /**
     * The edges of a run of dates, in time order. A polar date is one edge at its midnight, and no other date's edge
     * falls inside it: a sunset spilling past midnight into a polar day is dropped, the sun no longer sets.
     */
    static List<Edge> edges(LocalDate from, int days, ZoneId zone, double latitude, double longitude) {
        List<Edge> out = new ArrayList<>();
        List<Instant[]> polar = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            Day d = day(date, zone, latitude, longitude);
            if (d.always != null) {
                Instant start = date.atStartOfDay(zone).toInstant();
                polar.add(new Instant[] {start, date.plusDays(1).atStartOfDay(zone).toInstant()});
                out.add(new Edge(start, d.always));
            } else {
                for (int k = 0; k < 4; k++) out.add(new Edge(d.edges[k], values()[k]));
            }
        }
        out.removeIf(e -> {
            for (Instant[] span : polar) {
                if (e.at.isAfter(span[0]) && e.at.isBefore(span[1])) return true;
            }
            return false;
        });
        out.sort((a, b) -> a.at.compareTo(b.at));
        return handOver(out);
    }

    /**
     * On a short night (high latitudes in summer) today's dawn can start before yesterday's sunset is over. Then the
     * night is skipped: sunset hands straight over to dawn halfway between the two edges, so the order is always dawn,
     * day, sunset, night (or sunset, dawn) and never goes back.
     */
    static List<Edge> handOver(List<Edge> sorted) {
        List<Edge> out = new ArrayList<>();
        for (Edge e : sorted) {
            Edge last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.starts == DAWN && e.starts == NIGHT) {
                Instant mid = last.at.plusMillis((e.at.toEpochMilli() - last.at.toEpochMilli()) / 2);
                out.set(out.size() - 1, new Edge(mid, DAWN));
                continue;
            }
            if (last != null && last.starts == e.starts) continue;
            out.add(e);
        }
        return out;
    }

    /** The four edges of a date (dawn starts, day starts, sunset starts, night starts), or a phase all day long. */
    static final class Day {
        final Instant[] edges;
        final DayPhase always;

        Day(Instant[] edges, DayPhase always) {
            this.edges = edges;
            this.always = always;
        }
    }

    static Day day(LocalDate date, ZoneId zone, double latitude, double longitude) {
        if (!WeatherPlace.valid(latitude, longitude)) {
            Instant[] e = new Instant[4];
            for (int i = 0; i < 4; i++) e[i] = date.atTime(FIXED[i]).atZone(zone).toInstant();
            return new Day(e, null);
        }
        // Noon at the place, so the right sunrise is found on either side of the date line.
        double noon = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() / 86_400_000.0 + 2440587.5;
        double[] riseSet = sun(noon, latitude, longitude);
        if (riseSet.length == 1) return new Day(null, riseSet[0] > 0 ? DAY : NIGHT);
        Instant rise = julianToInstant(riseSet[0]), set = julianToInstant(riseSet[1]);
        Instant[] e = {
                rise.minusSeconds(DAWN_BEFORE_MIN * 60), rise.plusSeconds(DAWN_AFTER_MIN * 60),
                set.minusSeconds(SUNSET_BEFORE_MIN * 60), set.plusSeconds(SUNSET_AFTER_MIN * 60)};
        if (e[1].isAfter(e[2])) {
            // A day too short for both windows: dawn hands straight over to sunset at the sun's highest.
            Instant mid = rise.plusMillis((set.toEpochMilli() - rise.toEpochMilli()) / 2);
            e[1] = mid;
            e[2] = mid;
        }
        return new Day(e, null);
    }

    /**
     * Sunrise and sunset as Julian dates for the solar day around a Julian date; or one value, positive when the sun
     * stays up all day and negative when it stays down.
     */
    static double[] sun(double julian, double latitude, double longitude) {
        double n = Math.ceil(julian - 2451545.0 + 0.0008 - 0.5);
        double jStar = n - longitude / 360.0;
        double m = norm(357.5291 + 0.98560028 * jStar);
        double mr = Math.toRadians(m);
        double c = 1.9148 * Math.sin(mr) + 0.02 * Math.sin(2 * mr) + 0.0003 * Math.sin(3 * mr);
        double lambda = Math.toRadians(norm(m + c + 180 + 102.9372));
        double transit = 2451545.0 + jStar + 0.0053 * Math.sin(mr) - 0.0069 * Math.sin(2 * lambda);
        double sinDec = Math.sin(lambda) * Math.sin(Math.toRadians(23.4397));
        double cosDec = Math.cos(Math.asin(sinDec));
        double phi = Math.toRadians(latitude);
        double cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * sinDec) / (Math.cos(phi) * cosDec);
        if (cosW < -1) return new double[] {1};
        if (cosW > 1) return new double[] {-1};
        double w = Math.toDegrees(Math.acos(cosW)) / 360.0;
        return new double[] {transit - w, transit + w};
    }

    private static double norm(double deg) {
        double d = deg % 360;
        return d < 0 ? d + 360 : d;
    }

    private static Instant julianToInstant(double julian) {
        return Instant.ofEpochMilli(Math.round((julian - 2440587.5) * 86_400_000.0));
    }
}
