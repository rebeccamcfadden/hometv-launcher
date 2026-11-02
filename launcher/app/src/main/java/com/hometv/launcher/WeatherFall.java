package com.hometv.launcher;

/**
 * What falls over a Seasonal picture (F25, the project lead's decision of 2026-11-01): the real weather at the
 * Weather place, and the season when there is none to go by (offline, or no forecast for now). Snow only falls in the
 * cold periods, so a summer picture never snows; rain falls in any. Leaves (F31) fall in Autumn and Halloween while
 * it is dry: rain or snow at the place wins, and offline they fall. Pure Java, so it is tested on the JVM.
 */
final class WeatherFall {
    private WeatherFall() {}

    enum Fall { NONE, SNOW, RAIN, LEAVES }

    /**
     * @param condition the conditions now at the Weather place, or null when there are none to go by
     * @param period    the Seasonal period the picture shows
     */
    static Fall of(Weather.Condition condition, SeasonCalendar.Period period) {
        Fall dry = leafy(period) ? Fall.LEAVES : Fall.NONE;
        if (condition == null) {
            switch (period) {
                case SNOW:
                case CHRISTMAS:
                    return Fall.SNOW;
                case SPRING_RAIN:
                    return Fall.RAIN;
                default:
                    return dry;
            }
        }
        switch (condition) {
            case SNOW:
            case SNOW_GRAINS:
            case SNOW_SHOWERS:
                return cold(period) ? Fall.SNOW : dry;
            case DRIZZLE:
            case FREEZING_DRIZZLE:
            case RAIN:
            case FREEZING_RAIN:
            case RAIN_SHOWERS:
            case THUNDERSTORM:
            case THUNDER_HAIL:
                return Fall.RAIN;
            default:
                return dry;
        }
    }

    /** Whether a new reading changes what falls, so a running fall stops or a still Home starts one; the same is no change. */
    static boolean changes(Weather.Condition before, Weather.Condition after, SeasonCalendar.Period period) {
        return of(before, period) != of(after, period);
    }

    /**
     * The TV bench's test-only force (MainActivity's FALL_PREVIEW action, extra {@code fall}): {@code "snow"},
     * {@code "rain"} or {@code "leaves"} forces what falls, whatever the weather and the season; anything else
     * ({@code "off"}), or none, is null (the real rule). Everything else stays real: Animated details, Motion, the
     * backdrop picture and the idle rule.
     */
    static Fall preview(Object value) {
        if (!(value instanceof String)) return null;
        switch (((String) value).trim().toLowerCase(java.util.Locale.ROOT)) {
            case "snow":
                return Fall.SNOW;
            case "rain":
                return Fall.RAIN;
            case "leaves":
                return Fall.LEAVES;
            default:
                return null;
        }
    }

    /** The periods whose pictures can carry snow. */
    static boolean cold(SeasonCalendar.Period period) {
        switch (period) {
            case SNOW:
            case THAW:
            case AUTUMN:
            case HALLOWEEN:
            case CHRISTMAS:
                return true;
            default:
                return false;
        }
    }

    /** The periods whose pictures shed leaves while it is dry (F31). */
    static boolean leafy(SeasonCalendar.Period period) {
        return period == SeasonCalendar.Period.AUTUMN || period == SeasonCalendar.Period.HALLOWEEN;
    }
}