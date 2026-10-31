package com.gathin.tvhome;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** The shipped themes: every id resolves, Seasonal ones per period, Midnight stays today's look, and text is legible. */
public class ThemesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    private static List<String> allIds() {
        List<String> ids = new ArrayList<>(Themes.CLASSIC);
        ids.addAll(Themes.SEASONAL);
        return ids;
    }

    /** Every theme the TV can show: the Classic ones and each Seasonal style in each period. */
    private static List<Theme> allThemes() {
        List<Theme> themes = new ArrayList<>();
        for (String id : Themes.CLASSIC) themes.add(Themes.resolve(id, DAY));
        for (String id : Themes.SEASONAL) {
            for (SeasonCalendar.Period p : SeasonCalendar.Period.values()) themes.add(Themes.seasonal(id, p));
        }
        return themes;
    }

    @Test
    public void thePickersChoicesInOrder() {
        assertEquals(Arrays.asList("midnight", "daylight", "oled", "high-contrast", "cinema", "landscapes"),
                Themes.CLASSIC);
        assertEquals(Arrays.asList("seasonal-nature", "seasonal-vector", "seasonal-8bit"), Themes.SEASONAL);
        assertEquals("midnight", Themes.DEFAULT_ID);
    }

    /**
     * Every colour field is the theme's own: one left at the Builder's Midnight default would put Midnight's navy or
     * white-on-dark fills into a light theme (Daylight's dialogs were navy before track A's fills were derived).
     */
    @Test
    public void lightThemesDoNotKeepMidnightsFills() {
        Theme daylight = Themes.resolve("daylight", DAY);
        Theme midnight = Theme.midnight();
        for (Field f : Theme.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
            try {
                int d = f.getInt(daylight), m = f.getInt(midnight);
                // Colours that are fine to share: transparent ones and status colours the palette may repeat.
                if (d >>> 24 == 0 || f.getName().startsWith("shadow") || f.getName().startsWith("textShadow")) continue;
                assertTrue(f.getName() + " is still Midnight's", d != m);
            } catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    /** A dialog is read on its own fill: text1 on dialogBg passes AA in every theme and period. */
    @Test
    public void dialogTextIsReadable() {
        for (Theme t : allThemes()) {
            assertTrue(t.id + " dialog " + contrast(t.text1, t.dialogBg), contrast(t.text1, t.dialogBg) >= 4.5);
        }
    }

    @Test
    public void everyIdResolvesToItself() {
        Set<Integer> names = new HashSet<>();
        for (String id : allIds()) {
            assertTrue(id, Themes.exists(id));
            Theme t = Themes.resolve(id, DAY);
            assertEquals(id, Themes.choice(t.id));
            assertEquals(id, Themes.group(id), t.group);
            assertFalse(id, Themes.name(id).isEmpty());
            assertTrue(id, names.add(Themes.nameRes(id)));
        }
        for (String id : Themes.CLASSIC) {
            Theme t = Themes.resolve(id, DAY);
            assertEquals(id, t.id);
            assertEquals(Theme.Group.CLASSIC, t.group);
            assertEquals(Themes.name(id), t.name);
        }
    }

    @Test
    public void seasonalThemesFollowTheDate() {
        Theme t = Themes.resolve("seasonal-nature", DAY);
        assertEquals("seasonal-nature:halloween", t.id);
        assertEquals("Halloween", t.name);
        assertEquals(Theme.Group.SEASONAL, t.group);
        assertEquals("url:seasonal/nature/halloween.webp", t.backdrop);

        t = Themes.resolve("seasonal-vector", LocalDate.of(2026, 4, 10));
        assertEquals("seasonal-vector:spring-rain", t.id);
        assertEquals("Spring rain", t.name);
        assertEquals("url:seasonal/vector-2/spring-rain.webp", t.backdrop);

        t = Themes.resolve("seasonal-8bit", LocalDate.of(2026, 8, 1));
        assertEquals("seasonal-8bit:late-summer", t.id);
        assertEquals("pixel:late-summer", t.backdrop);
    }

    @Test
    public void eachStyleAndPeriodHasItsOwnId() {
        Set<String> ids = new HashSet<>();
        Set<Integer> periods = new HashSet<>();
        for (SeasonCalendar.Period p : SeasonCalendar.Period.values()) {
            assertTrue(p.name(), periods.add(Themes.periodRes(p)));
            for (String id : Themes.SEASONAL) {
                Theme t = Themes.seasonal(id, p);
                assertEquals(id + ":" + Themes.slug(p), t.id);
                assertEquals(Themes.periodName(p), t.name);
                assertEquals(id, Themes.choice(t.id));
                assertEquals(Theme.Group.SEASONAL, Themes.group(t.id));
                assertTrue(t.id, ids.add(t.id));
            }
        }
        assertEquals(30, ids.size());
        assertEquals("spring-rain", Themes.slug(SeasonCalendar.Period.SPRING_RAIN));
        assertEquals("christmas", Themes.slug(SeasonCalendar.Period.CHRISTMAS));
    }

    @Test
    public void unknownIdsFallBackToMidnight() {
        for (String id : new String[]{null, "", "nope", "retro", "seasonal-nature:snow", "Midnight"}) {
            assertFalse(String.valueOf(id), Themes.exists(id));
            assertSameValues(Theme.midnight(), Themes.resolve(id, DAY));
        }
        assertEquals("Midnight", Themes.name("nope"));
        assertEquals(Theme.Group.CLASSIC, Themes.group("nope"));
    }

    @Test
    public void midnightIsTodaysLook() {
        assertSameValues(Theme.midnight(), Themes.resolve("midnight", DAY));
        assertEquals("res:bg_evening", Themes.resolve("midnight", DAY).backdrop);
    }

    @Test
    public void backdropsByTheme() {
        assertEquals("url:classic/daylight.webp", Themes.resolve("daylight", DAY).backdrop);
        assertEquals("none", Themes.resolve("oled", DAY).backdrop);
        assertEquals("none", Themes.resolve("high-contrast", DAY).backdrop);
        assertEquals("url:classic/cinema.webp", Themes.resolve("cinema", DAY).backdrop);
        assertEquals("url:classic/landscapes-1.webp,classic/landscapes-2.webp,classic/landscapes-3.webp,"
                + "classic/landscapes-4.webp", Themes.resolve("landscapes", DAY).backdrop);
    }

    /**
     * The derivation rules, run on Figma's Midnight mode, land on today's Midnight: what Figma names exactly, and the
     * derived values within a few steps per channel.
     */
    @Test
    public void theMappingKeepsMidnightsProportions() {
        Themes.Palette figma = new Themes.Palette(
                0xFF090E15, 0xFF0D1622, 0xFF1A2433, 0xFF3A3F52, 0x99090E15, 0xEB090E15,
                0x0DFFFFFF, 0x17FFFFFF, 0xE8FFFFFF, 0x17FFFFFF,
                0xFFF4F5F7, 0xFFADB5C0, 0xFF7D8999, 0xFF101820,
                0xFFC4DDF7, 0xFF101820, 0x59C4DDF7,
                0xFF8FD9A8, 0xFFF2C46D, 0x4D000000, 0x00000000);
        Themes.Style style = new Themes.Style(20, 100, 1, 4, 24, 0, 0, 16, 6, 0, 0,
                Themes.display("Inter", Themes.LIGHT), Themes.ui("Inter", Themes.REGULAR, Themes.SEMIBOLD),
                false, false);
        Theme built = Themes.build("midnight", Theme.Group.CLASSIC, "Midnight", figma, style, "res:bg_evening");
        Theme m = Theme.midnight();
        int[][] pairs = {
                {built.bg, m.bg}, {built.bgRaised, m.bgRaised}, {built.surface, m.surface},
                {built.surface2, m.surface2}, {built.surface3, m.surface3}, {built.line, m.line},
                {built.lineStrong, m.lineStrong}, {built.text1, m.text1}, {built.text2, m.text2},
                {built.text3, m.text3}, {built.ghost, m.ghost}, {built.accent, m.accent},
                {built.accentSoft, m.accentSoft}, {built.onAccent, m.onAccent}, {built.active, m.active},
                {built.onActive, m.onActive}, {built.good, m.good}, {built.goodSoft, m.goodSoft},
                {built.star, m.star}, {built.menuBg, m.menuBg}, {built.toastBg, m.toastBg}, {built.scrim, m.scrim},
                {built.shadeTop, m.shadeTop}, {built.shadeBottom, m.shadeBottom},
                {built.homeShade[0], m.homeShade[0]}, {built.homeShade[1], m.homeShade[1]},
                {built.homeShade[2], m.homeShade[2]}, {built.homeDim, m.homeDim}, {built.skyTop, m.skyTop},
                {built.skyMid, m.skyMid}, {built.horizon, m.horizon}, {built.glowColor, m.glowColor},
                {built.shadowColor, m.shadowColor}, {built.textShadowColor, m.textShadowColor},
        };
        for (int i = 0; i < pairs.length; i++) {
            for (int shift = 0; shift <= 24; shift += 8) {
                int a = pairs[i][0] >>> shift & 0xFF, b = pairs[i][1] >>> shift & 0xFF;
                assertTrue("value " + i + String.format(": #%08X vs #%08X", pairs[i][0], pairs[i][1]),
                        Math.abs(a - b) <= 8);
            }
        }
        assertEquals(m.radiusS, built.radiusS, 0f);
        assertEquals(m.radiusM, built.radiusM, 0f);
        assertEquals(m.radiusL, built.radiusL, 0f);
        assertEquals(m.radiusXl, built.radiusXl, 0f);
        assertEquals(m.radiusPill, built.radiusPill, 0f);
        assertEquals(Theme.Font.SYSTEM, built.displayFont);
        assertEquals(Theme.Font.SYSTEM, built.uiFont);
    }

    @Test
    public void figmaWeightsBecomeThemeFonts() {
        Theme.Font ui = Themes.ui("Figtree", Themes.REGULAR, Themes.SEMIBOLD);
        assertEquals("Figtree", ui.family);
        assertEquals(400, ui.regular);
        assertEquals(500, ui.medium);
        assertEquals(700, ui.bold);
        Theme.Font display = Themes.display("DM Serif Display", Themes.REGULAR);
        assertEquals(400, display.regular);
        Theme cinema = Themes.resolve("cinema", DAY);
        assertEquals("DM Serif Display", cinema.displayFont.family);
        assertEquals("DM Sans", cinema.uiFont.family);
        assertTrue(cinema.vignette);
        Theme pixel = Themes.seasonal("seasonal-8bit", SeasonCalendar.Period.SNOW);
        assertEquals("Press Start 2P", pixel.displayFont.family);
        assertEquals(0f, pixel.radiusL, 0f);
        assertEquals(6f, pixel.glowOffset, 0f);
    }

    @Test
    public void textIsLegibleOnEveryBackground() {
        for (Theme t : allThemes()) {
            double text1 = contrast(t.text1, t.bg);
            double text2 = contrast(t.text2, t.bg);
            double minText1 = "high-contrast".equals(t.id) ? 7 : 4.5;
            assertTrue(t.id + " text1 " + text1, text1 >= minText1);
            assertTrue(t.id + " text2 " + text2, text2 >= 3);
            int accent = over(t.accent, t.bg);
            double onAccent = contrast(t.onAccent, accent);
            assertTrue(t.id + " onAccent " + onAccent, onAccent >= 4.5);
            // The focused pill's label is medium or bold; Christmas's red pill with cream text is Figma's 4.4:1.
            int active = over(t.active, t.bg);
            double onActive = contrast(t.onActive, active);
            assertTrue(t.id + " onActive " + onActive, onActive >= 4.3);
        }
    }

    @Test
    public void derivedValuesStayInRange() {
        for (Theme t : allThemes()) {
            assertEquals(t.id, 0xFF, t.bg >>> 24);
            assertEquals(t.id, 3, t.homeShade.length);
            assertTrue(t.id, t.radiusS <= t.radiusM && t.radiusM <= t.radiusL && t.radiusL <= t.radiusXl);
            assertTrue(t.id, (t.surface3 >>> 24) >= (t.surface2 >>> 24));
            assertTrue(t.id, (t.lineStrong >>> 24) >= (t.line >>> 24));
            assertNotEquals(t.id, t.text3, t.ghost);
        }
        Theme hc = Themes.resolve("high-contrast", DAY);
        assertEquals(0xFF, hc.menuBg >>> 24);
        assertEquals(0xFF, hc.toastBg >>> 24);
    }

    // WCAG 2 contrast, with a translucent colour first drawn over the opaque one behind it.

    private static double contrast(int fg, int bg) {
        double a = luminance(over(fg, bg)), b = luminance(bg);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static int over(int top, int bottom) {
        float a = (top >>> 24) / 255f;
        int c = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int t = top >> shift & 0xFF, b = bottom >> shift & 0xFF;
            c |= Math.round(t * a + b * (1 - a)) << shift;
        }
        return c;
    }

    private static double luminance(int c) {
        return 0.2126 * channel(c >> 16 & 0xFF) + 0.7152 * channel(c >> 8 & 0xFF) + 0.0722 * channel(c & 0xFF);
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static void assertSameValues(Theme expected, Theme actual) {
        for (Field f : Theme.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                Object e = f.get(expected), a = f.get(actual);
                if (e instanceof int[]) {
                    assertTrue(f.getName(), Arrays.equals((int[]) e, (int[]) a));
                } else if (e instanceof Theme.Font) {
                    Theme.Font fe = (Theme.Font) e, fa = (Theme.Font) a;
                    assertTrue(f.getName(), Objects.equals(fe.family, fa.family) && fe.regular == fa.regular
                            && fe.medium == fa.medium && fe.bold == fa.bold);
                } else {
                    assertEquals(f.getName(), e, a);
                }
            } catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }
    }
}
