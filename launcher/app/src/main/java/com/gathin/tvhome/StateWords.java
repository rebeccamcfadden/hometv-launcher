package com.gathin.tvhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * When a saved copy was made, in the offline states' words (V2 design 06-states A): the lists "as saved today 19:52",
 * the weather "updated 19:52". Today's copies say the time, yesterday's "yesterday" and the time, older ones the
 * date. The words around the pieces are string resources; the pieces are chosen here, without Android, so they are
 * tested on the JVM (StateWordsTest).
 */
final class StateWords {
    private StateWords() {}

    enum Day { TODAY, YESTERDAY, EARLIER }

    /** When a copy was saved: which day, and the time ("19:52", or "7:52 PM") or, for EARLIER, the date ("28 Oct"). */
    static final class When {
        final Day day;
        final String text;

        When(Day day, String text) {
            this.day = day;
            this.text = text;
        }
    }

    // English on purpose: the fork's launcher is English only, like its strings.
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    /**
     * {@code saved} and {@code now} in epoch milliseconds, in the TV's zone. A copy dated in the future (the clock was
     * set back since) counts as today. Null when nothing was saved ({@code saved} 0 or less).
     */
    static When when(long saved, long now, ZoneId zone, boolean is24) {
        if (saved <= 0) return null;
        ZonedDateTime s = Instant.ofEpochMilli(saved).atZone(zone);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        LocalDate day = s.toLocalDate();
        if (!day.isBefore(today)) return new When(Day.TODAY, time(s, is24));
        if (day.equals(today.minusDays(1))) return new When(Day.YESTERDAY, time(s, is24));
        return new When(Day.EARLIER, DATE.format(s));
    }

    private static String time(ZonedDateTime t, boolean is24) {
        return (is24 ? H24 : H12).format(t);
    }

    /**
     * The oldest of the times the titles' copies were saved, ignoring unknown ones (0), or 0 when none is known: the
     * lists shown offline are at least that fresh.
     */
    static long oldest(long[] savedAt) {
        long oldest = 0;
        for (long t : savedAt) if (t > 0 && (oldest == 0 || t < oldest)) oldest = t;
        return oldest;
    }
}
