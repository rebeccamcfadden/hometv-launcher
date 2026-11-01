package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class BackdropKeysTest {
    @Test
    public void parsesEveryKind() {
        BackdropKeys.Key res = BackdropKeys.parse("res:bg_evening");
        assertEquals(BackdropKeys.Kind.RES, res.kind);
        assertEquals("bg_evening", res.name);

        assertSame(BackdropKeys.Key.NONE, BackdropKeys.parse("none"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("sky"));

        BackdropKeys.Key pixel = BackdropKeys.parse("pixel:spring-rain");
        assertEquals(BackdropKeys.Kind.PIXEL, pixel.kind);
        assertEquals("spring-rain", pixel.name);

        BackdropKeys.Key one = BackdropKeys.parse("url:classic/daylight.webp");
        assertEquals(BackdropKeys.Kind.URL, one.kind);
        assertEquals(Arrays.asList("classic/daylight.webp"), one.paths);

        BackdropKeys.Key list = BackdropKeys.parse(
                "url:classic/landscapes-1.webp, classic/landscapes-2.webp,classic/landscapes-3.webp");
        assertEquals(Arrays.asList("classic/landscapes-1.webp", "classic/landscapes-2.webp",
                "classic/landscapes-3.webp"), list.paths);
    }

    @Test
    public void anythingElseIsTheSky() {
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse(null));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse(""));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("photo:x"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("res:"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("res:Bg-Evening"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("pixel:spring_rain"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("pixel:easter"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("url:"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("url:a.webp,,b.webp"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("url:../secrets.xml"));
    }

    @Test
    public void pathsStayInsideTheFolder() {
        assertTrue(BackdropKeys.safePath("seasonal/nature/late-summer.webp"));
        assertTrue(BackdropKeys.safePath("a_b.c"));
        assertFalse(BackdropKeys.safePath(null));
        assertFalse(BackdropKeys.safePath(""));
        assertFalse(BackdropKeys.safePath("/classic/cinema.webp"));
        assertFalse(BackdropKeys.safePath("classic/"));
        assertFalse(BackdropKeys.safePath("classic//cinema.webp"));
        assertFalse(BackdropKeys.safePath("classic/../../shared_prefs/x.xml"));
        assertFalse(BackdropKeys.safePath("./cinema.webp"));
        assertFalse(BackdropKeys.safePath("classic\\cinema.webp"));
        assertFalse(BackdropKeys.safePath("https://example.com/x.webp"));
        assertFalse(BackdropKeys.safePath("cinema.webp?x=1"));
        assertFalse(BackdropKeys.safePath(new String(new char[201]).replace('\0', 'a')));
    }

    @Test
    public void anHourlyListGoesRoundByTheHour() {
        assertEquals(0, BackdropKeys.rotation(0, 4));
        assertEquals(3, BackdropKeys.rotation(3, 4));
        assertEquals(0, BackdropKeys.rotation(4, 4));
        assertEquals(3, BackdropKeys.rotation(23, 4));
        assertEquals(0, BackdropKeys.rotation(17, 1));
        assertEquals(0, BackdropKeys.rotation(17, 0));
        assertEquals(2, BackdropKeys.rotation(-1, 3));

        BackdropKeys.Key list = BackdropKeys.parse("url:a.webp,b.webp,c.webp");
        assertEquals("a.webp", BackdropKeys.pick(list, 9, DayPhase.NIGHT));
        assertEquals("b.webp", BackdropKeys.pick(list, 10, DayPhase.DAY));
        assertEquals("c.webp", BackdropKeys.pick(list, 23, DayPhase.DAY));
        assertFalse(list.phased);
        assertNull(BackdropKeys.pick(BackdropKeys.parse("sky"), 9, DayPhase.DAY));
    }

    @Test
    public void phasedKeysFillInThePartOfTheDay() {
        BackdropKeys.Key vector = BackdropKeys.parse("url:seasonal/vector-5/autumn-{phase}.webp");
        assertEquals(BackdropKeys.Kind.URL, vector.kind);
        assertTrue(vector.phased);
        assertEquals("seasonal/vector-5/autumn-dawn.webp", BackdropKeys.pick(vector, 7, DayPhase.DAWN));
        assertEquals("seasonal/vector-5/autumn-night.webp", BackdropKeys.pick(vector, 23, DayPhase.NIGHT));

        BackdropKeys.Key pixel = BackdropKeys.parse("pixel:late-summer-{phase}");
        assertEquals(BackdropKeys.Kind.PIXEL, pixel.kind);
        assertEquals("late-summer", pixel.name);
        assertTrue(pixel.phased);
        assertFalse(BackdropKeys.parse("pixel:late-summer").phased);

        // The placeholder never opens a way out of the folder.
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("url:../{phase}.webp"));
        assertSame(BackdropKeys.Key.SKY, BackdropKeys.parse("pixel:easter-{phase}"));
    }

    @Test
    public void joinsAndPlacesFiles() {
        String base = "https://cdn.jsdelivr.net/gh/rebeccamcfadden/cleanyourtv@main/launcher/backdrops/";
        assertEquals(base + "classic/cinema.webp", BackdropKeys.join(base, "classic/cinema.webp"));
        assertEquals(base + "classic/cinema.webp",
                BackdropKeys.join(base.substring(0, base.length() - 1), "classic/cinema.webp"));
        assertEquals(base + "classic/cinema.webp", BackdropKeys.join(base, "/classic/cinema.webp"));
        assertEquals("backdrops/seasonal/vector-2/snow.webp", BackdropKeys.cachePath("seasonal/vector-2/snow.webp"));
    }

    @Test
    public void slugsMatchTheFileNames() {
        assertEquals("spring-rain", BackdropKeys.slug(SeasonCalendar.Period.SPRING_RAIN));
        assertEquals("late-summer", BackdropKeys.slug(SeasonCalendar.Period.LATE_SUMMER));
        for (SeasonCalendar.Period p : SeasonCalendar.Period.values()) {
            assertEquals(p, BackdropKeys.period(BackdropKeys.slug(p)));
        }
        assertNull(BackdropKeys.period("SNOW"));
    }

    @Test
    public void nextPeriodIsTheFirstDifferentOne() {
        assertEquals(SeasonCalendar.Period.THAW, BackdropKeys.next(LocalDate.of(2026, 1, 10)));
        assertEquals(SeasonCalendar.Period.HALLOWEEN, BackdropKeys.next(LocalDate.of(2026, 9, 30)));
        assertEquals(SeasonCalendar.Period.HALLOWEEN, BackdropKeys.next(LocalDate.of(2026, 10, 14)));
        assertEquals(SeasonCalendar.Period.AUTUMN, BackdropKeys.next(LocalDate.of(2026, 10, 30)));
        assertEquals(SeasonCalendar.Period.CHRISTMAS, BackdropKeys.next(LocalDate.of(2026, 11, 3)));
        assertEquals(SeasonCalendar.Period.SNOW, BackdropKeys.next(LocalDate.of(2026, 12, 31)));
    }

    @Test
    public void swapsThePeriodInAPath() {
        assertEquals("seasonal/vector-5/halloween-{phase}.webp", BackdropKeys.forPeriod(
                "seasonal/vector-5/autumn-{phase}.webp", SeasonCalendar.Period.AUTUMN, SeasonCalendar.Period.HALLOWEEN));
        assertNull(BackdropKeys.forPeriod("seasonal/vector-5/autumn-night.webp",
                SeasonCalendar.Period.AUTUMN, SeasonCalendar.Period.HALLOWEEN));
        assertEquals("seasonal/vector-2/thaw.webp", BackdropKeys.forPeriod("seasonal/vector-2/snow.webp",
                SeasonCalendar.Period.SNOW, SeasonCalendar.Period.THAW));
        assertNull(BackdropKeys.forPeriod("classic/cinema.webp",
                SeasonCalendar.Period.SNOW, SeasonCalendar.Period.THAW));
        assertNull(BackdropKeys.forPeriod("seasonal/nature/snow",
                SeasonCalendar.Period.SNOW, SeasonCalendar.Period.THAW));
    }

    @Test
    public void prefetchesTheRestOfAListAndTheNextPeriod() {
        List<String> seasonal = BackdropKeys.prefetch(BackdropKeys.parse("url:seasonal/vector-2/autumn.webp"),
                LocalDate.of(2026, 10, 1), DayPhase.DAY, DayPhase.DAY, DayPhase.DAY);
        assertEquals(Arrays.asList("seasonal/vector-2/autumn.webp", "seasonal/vector-2/halloween.webp"), seasonal);

        // A phased picture: now, the next part of the day, and the next period at night, when it starts.
        List<String> phased = BackdropKeys.prefetch(BackdropKeys.parse("url:seasonal/vector-5/autumn-{phase}.webp"),
                LocalDate.of(2026, 10, 1), DayPhase.DAY, DayPhase.SUNSET, DayPhase.NIGHT);
        assertEquals(Arrays.asList("seasonal/vector-5/autumn-day.webp", "seasonal/vector-5/autumn-sunset.webp",
                "seasonal/vector-5/halloween-night.webp"), phased);
        // Always day: the next period starts on its day picture.
        assertEquals(Arrays.asList("seasonal/vector-5/autumn-day.webp", "seasonal/vector-5/halloween-day.webp"),
                BackdropKeys.prefetch(BackdropKeys.parse("url:seasonal/vector-5/autumn-{phase}.webp"),
                        LocalDate.of(2026, 10, 1), DayPhase.DAY, DayPhase.DAY, DayPhase.DAY));
        assertEquals(LocalDate.of(2026, 10, 15), BackdropKeys.nextPeriodStart(LocalDate.of(2026, 10, 1)));

        List<String> landscapes = BackdropKeys.prefetch(BackdropKeys.parse(
                "url:classic/landscapes-1.webp,classic/landscapes-2.webp,classic/landscapes-3.webp,"
                        + "classic/landscapes-4.webp"), LocalDate.of(2026, 10, 1), DayPhase.DAY, DayPhase.DAY,
                DayPhase.DAY);
        assertEquals(Arrays.asList("classic/landscapes-1.webp", "classic/landscapes-2.webp",
                "classic/landscapes-3.webp", "classic/landscapes-4.webp"), landscapes);

        assertTrue(BackdropKeys.prefetch(BackdropKeys.parse("pixel:snow"), LocalDate.of(2026, 1, 1), DayPhase.DAY,
                DayPhase.DAY, DayPhase.DAY).isEmpty());
    }

    @Test
    public void decodesNoBiggerThanTheScreenNeeds() {
        assertEquals(1280, BackdropKeys.decodeWidth(1280, 720, 1920, 1080));
        assertEquals(1920, BackdropKeys.decodeWidth(1920, 1080, 1920, 1080));
        assertEquals(1280, BackdropKeys.decodeWidth(1920, 1080, 1280, 720));
        assertEquals(1920, BackdropKeys.decodeWidth(3840, 2160, 1920, 1080));
        // A square picture is held to the screen's width, the side the centre-crop needs whole.
        assertEquals(1920, BackdropKeys.decodeWidth(3000, 3000, 1920, 1080));
        assertEquals(800, BackdropKeys.decodeWidth(800, 0, 1920, 1080));
    }
}
