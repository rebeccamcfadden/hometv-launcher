package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.MonthDay;

/**
 * The ten periods a Seasonal theme follows through the year (Figma "Calendar" collection). They follow the months
 * except where a holiday takes over: Halloween runs 15 October to 2 November, inside Autumn.
 */
final class SeasonCalendar {
    private SeasonCalendar() {}

    enum Period { SNOW, THAW, SPRING_RAIN, BLOOM, PRIDE, MIDSUMMER, LATE_SUMMER, AUTUMN, HALLOWEEN, CHRISTMAS }

    private static final MonthDay HALLOWEEN_FROM = MonthDay.of(10, 15);
    private static final MonthDay HALLOWEEN_TO = MonthDay.of(11, 2);

    static Period at(LocalDate date) {
        MonthDay d = MonthDay.from(date);
        if (!d.isBefore(HALLOWEEN_FROM) && !d.isAfter(HALLOWEEN_TO)) return Period.HALLOWEEN;
        switch (date.getMonth()) {
            case JANUARY:
            case FEBRUARY:
                return Period.SNOW;
            case MARCH:
                return Period.THAW;
            case APRIL:
                return Period.SPRING_RAIN;
            case MAY:
                return Period.BLOOM;
            case JUNE:
                return Period.PRIDE;
            case JULY:
                return Period.MIDSUMMER;
            case AUGUST:
                return Period.LATE_SUMMER;
            case DECEMBER:
                return Period.CHRISTMAS;
            default:
                return Period.AUTUMN;
        }
    }
}
