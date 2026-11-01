package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every theme the picker offers, by id. A Classic theme is one fixed look; a Seasonal theme is a style (Vector art,
 * 8-bit) whose colours, name and backdrop follow {@link SeasonCalendar}, and whose picture follows the part of the
 * day ({@link DayPhase}; the colours stay the period's, so the change needs no rebuild). Nature was removed for now
 * (2026-11-01); ThemeStore shows Vector art on a TV that had it. Unknown ids fall back to
 * Midnight, so a TV never ends up without a look.
 *
 * <p>The data is the Figma file's variables (lwShqRaciMpDUDW5dYljNw): the Theme collection's modes for the Classic
 * themes, and for the Seasonal ones its Calendar mode, which takes shape and type from the Calendar style collection
 * (one mode per style) and colours from the Calendar collection (one mode per period, the same for every style).
 * Midnight is {@link Theme#midnight()}, today's look, not rebuilt from Figma. Figma names fewer values than
 * {@link Theme} holds; {@link #build} derives the rest from the ones it has, in the proportions Midnight uses:
 * <ul>
 *   <li>Taken as they are: bg, the sky, surface and surface2, line, the three texts, accent and onAccent, active
 *   and onActive, good, star, the glow, the resting shadow, the text shadow, lineWidth, focusWidth, the overlays.
 *   Figma's shade/scrim (Midnight 60%) is homeDim, and shade/shelf (92%) is the last stop of homeShade.</li>
 *   <li>bgRaised is surface over bg at half surface's alpha; menuBg and toastBg are surface over bg, opaque, at 96%
 *   and 97% (100% for a theme with solid surfaces: High Contrast).</li>
 *   <li>surface3 steps on from surface2 as far again as Midnight's (1.3 times the step from surface to surface2, in
 *   alpha, or in colour when surface2 is solid); lineStrong is line at twice its alpha.</li>
 *   <li>ghost is text3 26% of the way to bg; accentSoft and goodSoft are accent at 14% and good at 16%.</li>
 *   <li>The popup scrim is bg at 57% brightness over 58%, or at shade/scrim's alpha when that is higher.</li>
 *   <li>homeShade's first two stops are bg at 25% and 55% scaled by shade/scrim against Midnight's 60%, so the
 *   Seasonal styles' 26% scrim lets the picture through. shadeTop and shadeBottom, behind every other page, are bg
 *   at 86% and 90% scaled half as much (62% and 65% for the Seasonal styles), since pages are mostly text.</li>
 *   <li>radiusL is radius/tile at Midnight's 16/20, and radiusS, radiusM and radiusXl keep Midnight's 8, 12 and 24
 *   against its 16; radiusPill is radius/pill.</li>
 *   <li>Fonts: display is the display family at its one weight. The UI font's regular is ui/regular; Midnight shows
 *   Figma's strong weight (Inter Semi Bold, 600) as Roboto Medium and Bold, so medium and bold are strong − 100
 *   and strong + 100. Inter is Roboto, the system font.</li>
 * </ul>
 */
final class Themes {
    private Themes() {}

    static final String DEFAULT_ID = "midnight";

    /** The picker's choices in order, by group. */
    static final List<String> CLASSIC = Collections.unmodifiableList(Arrays.asList(
            DEFAULT_ID, "daylight", "oled", "high-contrast", "cinema", "landscapes"));
    static final List<String> SEASONAL = Collections.unmodifiableList(Arrays.asList(
            "seasonal-vector", "seasonal-8bit"));

    static boolean exists(String id) {
        return CLASSIC.contains(id) || SEASONAL.contains(id);
    }

    /** The theme for an id on a given day; Seasonal ids resolve to that day's period. */
    static Theme resolve(String id, LocalDate day) {
        if (id == null) return Theme.midnight();
        switch (id) {
            case "daylight":
                return build(id, Theme.Group.CLASSIC, "Daylight", DAYLIGHT, DAYLIGHT_STYLE, "url:classic/daylight.webp");
            case "oled":
                return build(id, Theme.Group.CLASSIC, "OLED Black", OLED, OLED_STYLE, "none");
            case "high-contrast":
                return build(id, Theme.Group.CLASSIC, "High Contrast", HIGH_CONTRAST, HIGH_CONTRAST_STYLE, "none");
            case "cinema":
                return build(id, Theme.Group.CLASSIC, "Cinema", CINEMA, CINEMA_STYLE, "url:classic/cinema.webp");
            case "landscapes":
                return build(id, Theme.Group.CLASSIC, "Landscapes", LANDSCAPES, LANDSCAPES_STYLE,
                        "url:classic/landscapes-1.webp,classic/landscapes-2.webp,classic/landscapes-3.webp,"
                                + "classic/landscapes-4.webp");
            case "seasonal-vector":
            case "seasonal-8bit":
                return seasonal(id, SeasonCalendar.at(day));
            default:
                return Theme.midnight();
        }
    }

    /**
     * A Seasonal style in one period, named after the period, with the id {@code <choice>:<period>} so a change of
     * period shows as a change of theme. The picker can use it to preview a period too.
     */
    static Theme seasonal(String id, SeasonCalendar.Period period) {
        String slug = slug(period);
        Style style;
        String backdrop;
        switch (id) {
            case "seasonal-vector":
                style = VECTOR;
                backdrop = "url:seasonal/vector-5/" + slug + "-" + BackdropKeys.PHASE + ".webp";
                break;
            case "seasonal-8bit":
                style = PIXEL;
                backdrop = "pixel:" + slug + "-" + BackdropKeys.PHASE;
                break;
            default:
                throw new IllegalArgumentException("Not a Seasonal theme: " + id);
        }
        return build(id + ":" + slug, Theme.Group.SEASONAL, periodName(period), palette(period), style, backdrop);
    }

    /** The saved choice behind a theme id: a resolved Seasonal id without its period. */
    static String choice(String themeId) {
        if (themeId == null) return DEFAULT_ID;
        int colon = themeId.indexOf(':');
        return colon < 0 ? themeId : themeId.substring(0, colon);
    }

    /** A period's file and id name: spring-rain, late-summer. */
    static String slug(SeasonCalendar.Period period) {
        return period.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static Theme.Group group(String id) {
        return SEASONAL.contains(choice(id)) ? Theme.Group.SEASONAL : Theme.Group.CLASSIC;
    }

    /** The English name of a choice; the picker shows ThemeWords.nameRes. */
    static String name(String id) {
        switch (choice(id)) {
            case "daylight": return "Daylight";
            case "oled": return "OLED Black";
            case "high-contrast": return "High Contrast";
            case "cinema": return "Cinema";
            case "landscapes": return "Landscapes";
            case "seasonal-vector": return "Vector art";
            case "seasonal-8bit": return "8-bit";
            default: return "Midnight";
        }
    }


    /** The English name of a period, a resolved Seasonal theme's name; the picker shows ThemeWords.periodRes. */
    static String periodName(SeasonCalendar.Period period) {
        switch (period) {
            case SNOW: return "Snow";
            case THAW: return "Thaw";
            case SPRING_RAIN: return "Spring rain";
            case BLOOM: return "Bloom";
            case PRIDE: return "Pride";
            case MIDSUMMER: return "Midsummer";
            case LATE_SUMMER: return "Late summer";
            case AUTUMN: return "Autumn";
            case HALLOWEEN: return "Halloween";
            default: return "Christmas";
        }
    }


    private static Palette palette(SeasonCalendar.Period period) {
        switch (period) {
            case SNOW: return SNOW;
            case THAW: return THAW;
            case SPRING_RAIN: return SPRING_RAIN;
            case BLOOM: return BLOOM;
            case PRIDE: return PRIDE;
            case MIDSUMMER: return MIDSUMMER;
            case LATE_SUMMER: return LATE_SUMMER;
            case AUTUMN: return AUTUMN;
            case HALLOWEEN: return HALLOWEEN;
            default: return CHRISTMAS;
        }
    }

    // Midnight's proportions, for the values Figma does not name (see the class comment).
    private static final float SURFACE_STEP = 1.3f;
    private static final float GHOST_TO_BG = 0.26f;
    /** A portrait's fill, a little toward the text from the raised fill (Midnight: 1C2633 on 121A24). */
    private static final float PORTRAIT_TO_TEXT = 0.06f;
    private static final int ACCENT_SOFT_ALPHA = 0x24, GOOD_SOFT_ALPHA = 0x29;
    private static final int MENU_ALPHA = 0xF5, TOAST_ALPHA = 0xF7;
    private static final float SCRIM_BRIGHTNESS = 0.57f;
    private static final int SCRIM_ALPHA = 0x94;
    private static final int MIDNIGHT_DIM = 0x99;
    private static final int[] HOME_SHADE_ALPHA = {0x40, 0x8C};
    private static final int SHADE_TOP_ALPHA = 0xDB, SHADE_BOTTOM_ALPHA = 0xE6;
    private static final float RADIUS_PER_TILE = 16f / 20f;

    static Theme build(String id, Theme.Group group, String name, Palette p, Style s, String backdrop) {
        Theme.Builder b = new Theme.Builder(id, group, name);
        boolean solid = alpha(p.surface1) == 0xFF;
        b.bg = p.bg;
        b.bgRaised = over(withAlpha(p.surface1, alpha(p.surface1) / 2f), p.bg);
        b.surface = p.surface1;
        b.surface2 = p.surface2;
        b.surface3 = alpha(p.surface2) < 0xFF
                ? withAlpha(p.surface2, alpha(p.surface2) + SURFACE_STEP * (alpha(p.surface2) - alpha(p.surface1)))
                : mix(p.surface2, p.surface1, -SURFACE_STEP);
        b.line = p.line;
        b.lineStrong = withAlpha(p.line, alpha(p.line) * 2f);
        b.text1 = p.text1;
        b.text2 = p.text2;
        b.text3 = p.text3;
        b.ghost = mix(p.text3, p.bg, GHOST_TO_BG);
        b.accent = p.accent;
        b.accentSoft = withAlpha(p.accent, ACCENT_SOFT_ALPHA);
        b.onAccent = p.onAccent;
        b.active = p.active;
        b.onActive = p.onActive;
        b.good = p.good;
        b.goodSoft = withAlpha(p.good, GOOD_SOFT_ALPHA);
        b.star = p.star;
        int raised = over(p.surface1, p.bg);
        b.menuBg = withAlpha(raised, solid ? 0xFF : MENU_ALPHA);
        b.toastBg = withAlpha(raised, solid ? 0xFF : TOAST_ALPHA);
        b.scrim = withAlpha(mix(p.bg, 0xFF000000, 1f - SCRIM_BRIGHTNESS), Math.max(SCRIM_ALPHA, alpha(p.scrim)));
        float dim = alpha(p.scrim) / (float) MIDNIGHT_DIM;
        float page = 0.5f + 0.5f * dim;
        b.shadeTop = withAlpha(p.bg, SHADE_TOP_ALPHA * page);
        b.shadeBottom = withAlpha(p.bg, SHADE_BOTTOM_ALPHA * page);
        b.homeShade = new int[]{withAlpha(p.bg, HOME_SHADE_ALPHA[0] * dim), withAlpha(p.bg, HOME_SHADE_ALPHA[1] * dim),
                p.shelf};
        b.homeDim = p.scrim;
        b.skyTop = p.skyTop;
        b.skyMid = p.skyMid;
        b.horizon = p.horizon;
        float l = s.tile * RADIUS_PER_TILE;
        b.radiusS = Math.round(l * 0.5f);
        b.radiusM = Math.round(l * 0.75f);
        b.radiusL = Math.round(l);
        b.radiusXl = Math.round(l * 1.5f);
        b.radiusPill = s.pill;
        b.lineWidth = s.line;
        b.focusWidth = s.focus;
        b.glowColor = p.glow;
        b.glowBlur = s.glowBlur;
        b.glowOffset = s.glowOffset;
        b.glowSpread = s.glowSpread;
        b.shadowColor = p.shadow;
        b.shadowBlur = s.shadowBlur;
        b.shadowOffset = s.shadowOffset;
        b.textShadowColor = p.textShadow;
        b.textShadowBlur = s.textShadowBlur;
        b.textShadowOffset = s.textShadowOffset;
        b.displayFont = s.display;
        b.uiFont = s.ui;
        b.scanlines = s.scanlines;
        b.vignette = s.vignette;
        b.backdrop = backdrop;
        // Track A's fills, in Midnight's proportions to its surface (tile 11, panel 8, card 15, selected 31 of 13);
        // a theme with solid surfaces (High Contrast) keeps them solid, and its selected item takes surface/2.
        float sa = alpha(p.surface1);
        b.tile = solid ? p.surface1 : withAlpha(p.surface1, sa * 11 / 13f);
        b.panel = solid ? p.surface1 : withAlpha(p.surface1, sa * 8 / 13f);
        b.card = solid ? p.surface1 : withAlpha(p.surface1, sa * 15 / 13f);
        b.selected = solid ? p.surface2 : withAlpha(p.surface1, sa * 31 / 13f);
        b.dialogBg = raised;
        b.portrait = mix(raised, p.text1, PORTRAIT_TO_TEXT);
        return b.build();
    }

    private static int alpha(int c) {
        return c >>> 24;
    }

    private static int withAlpha(int c, float a) {
        return clamp(a) << 24 | (c & 0xFFFFFF);
    }

    /** a's colour moved toward b's by t (beyond b, or away from it, outside 0..1), keeping a's alpha. */
    private static int mix(int a, int b, float t) {
        int c = a & 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int ca = a >> shift & 0xFF, cb = b >> shift & 0xFF;
            c |= clamp(ca + (cb - ca) * t) << shift;
        }
        return c;
    }

    /** top drawn over an opaque bottom. */
    private static int over(int top, int bottom) {
        return mix(bottom, top, alpha(top) / 255f) | 0xFF000000;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    /** The Theme collection's colours for one mode (one period, for the Seasonal styles), as ARGB. */
    static final class Palette {
        final int bg, skyTop, skyMid, horizon, scrim, shelf;
        final int surface1, surface2, active, line;
        final int text1, text2, text3, onActive;
        final int accent, onAccent, glow;
        final int good, star, shadow, textShadow;

        /**
         * One row per group, as written below: bg/base, bg/sky-top, bg/sky-mid, bg/horizon, shade/scrim,
         * shade/shelf; surface/1, surface/2, surface/active, line/1; text/1, text/2, text/3, text/on-active;
         * accent/base, accent/on, focus/glow; status/good, status/star, shadow/color, shadow/text.
         */
        Palette(int bg, int skyTop, int skyMid, int horizon, int scrim, int shelf,
                int surface1, int surface2, int active, int line,
                int text1, int text2, int text3, int onActive,
                int accent, int onAccent, int glow,
                int good, int star, int shadow, int textShadow) {
            this.bg = bg;
            this.skyTop = skyTop;
            this.skyMid = skyMid;
            this.horizon = horizon;
            this.scrim = scrim;
            this.shelf = shelf;
            this.surface1 = surface1;
            this.surface2 = surface2;
            this.active = active;
            this.line = line;
            this.text1 = text1;
            this.text2 = text2;
            this.text3 = text3;
            this.onActive = onActive;
            this.accent = accent;
            this.onAccent = onAccent;
            this.glow = glow;
            this.good = good;
            this.star = star;
            this.shadow = shadow;
            this.textShadow = textShadow;
        }
    }

    /** The Theme collection's shape, type and overlays for one mode (one Calendar style), in design pixels. */
    static final class Style {
        final float tile, pill, line, focus;
        final float glowBlur, glowOffset, glowSpread, shadowBlur, shadowOffset, textShadowBlur, textShadowOffset;
        final Theme.Font display, ui;
        final boolean scanlines, vignette;

        /**
         * radius/tile, radius/pill, stroke/line, stroke/focus, glow/blur, glow/offset, glow/spread, shadow/blur,
         * shadow/offset, textshadow/blur, textshadow/offset, the fonts, overlay/scanlines, overlay/vignette.
         */
        Style(float tile, float pill, float line, float focus, float glowBlur, float glowOffset, float glowSpread,
              float shadowBlur, float shadowOffset, float textShadowBlur, float textShadowOffset,
              Theme.Font display, Theme.Font ui, boolean scanlines, boolean vignette) {
            this.tile = tile;
            this.pill = pill;
            this.line = line;
            this.focus = focus;
            this.glowBlur = glowBlur;
            this.glowOffset = glowOffset;
            this.glowSpread = glowSpread;
            this.shadowBlur = shadowBlur;
            this.shadowOffset = shadowOffset;
            this.textShadowBlur = textShadowBlur;
            this.textShadowOffset = textShadowOffset;
            this.display = display;
            this.ui = ui;
            this.scanlines = scanlines;
            this.vignette = vignette;
        }
    }

    // Figma's style names as weights.
    static final int LIGHT = 300, REGULAR = 400, MEDIUM = 500, SEMIBOLD = 600, BOLD = 700, EXTRABOLD = 800;

    static Theme.Font display(String family, int weight) {
        return "Inter".equals(family) ? Theme.Font.SYSTEM : new Theme.Font(family, weight, weight, weight);
    }

    static Theme.Font ui(String family, int regular, int strong) {
        return "Inter".equals(family) ? Theme.Font.SYSTEM
                : new Theme.Font(family, regular, Math.max(regular, strong - 100), Math.min(900, strong + 100));
    }

    // Generated from the Figma variables, one block per mode: the Classic themes' palettes and styles, the Calendar
    // styles, then the Calendar periods' palettes.
    // Daylight
    private static final Palette DAYLIGHT = new Palette(
            0xFFEEF1F5, 0xFFCFE0F2, 0xFFE9EEF5, 0xFFFBE3C8, 0x7FF4F6F9, 0xF0EEF1F5,
            0xB3FFFFFF, 0xE0FFFFFF, 0xEB111821, 0x1F0D1520,
            0xFF111821, 0xFF46505E, 0xFF5F6A7A, 0xFFF4F5F7,
            0xFF1F5FBF, 0xFFFFFFFF, 0x4D1F5FBF,
            0xFF1D7A45, 0xFFA8670F, 0x291A2A40, 0x00FFFFFF);
    private static final Style DAYLIGHT_STYLE = new Style(16, 100, 1, 4, 20, 0, 0, 20, 6, 0, 0,
            display("Figtree", LIGHT), ui("Figtree", REGULAR, SEMIBOLD), false, false);

    // OLED Black
    private static final Palette OLED = new Palette(
            0xFF000000, 0xFF000000, 0xFF000000, 0xFF06080B, 0x00000000, 0xFF000000,
            0x00FFFFFF, 0x12FFFFFF, 0xFFFFFFFF, 0x2EFFFFFF,
            0xFFFFFFFF, 0xFFA3A3A3, 0xFF7A7A7A, 0xFF000000,
            0xFFFFFFFF, 0xFF000000, 0x00FFFFFF,
            0xFF4ADE80, 0xFFFACC15, 0x00000000, 0x00000000);
    private static final Style OLED_STYLE = new Style(12, 100, 1, 3, 0, 0, 0, 0, 0, 0, 0,
            display("Manrope", LIGHT), ui("Manrope", REGULAR, SEMIBOLD), false, false);

    // High Contrast
    private static final Palette HIGH_CONTRAST = new Palette(
            0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xD9000000, 0xFF000000,
            0xFF000000, 0xFF1F1F1F, 0xFFFFE600, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFE6E6E6, 0xFF000000,
            0xFFFFE600, 0xFF000000, 0x00FFE600,
            0xFF7CFFB2, 0xFFFFE600, 0x00000000, 0x00000000);
    private static final Style HIGH_CONTRAST_STYLE = new Style(12, 100, 2, 6, 0, 0, 0, 0, 0, 0, 0,
            display("Atkinson Hyperlegible Next", REGULAR), ui("Atkinson Hyperlegible Next", REGULAR, BOLD),
            false, false);

    // Cinema
    private static final Palette CINEMA = new Palette(
            0xFF120C08, 0xFF1B120C, 0xFF2A1A10, 0xFF6B3A1C, 0x8C120C08, 0xF00E0906,
            0x0FF6E7D0, 0x1CF6E7D0, 0xFFE8C27A, 0x21F6E7D0,
            0xFFF6EAD8, 0xFFC9B49A, 0xFF96836D, 0xFF1A1006,
            0xFFE8C27A, 0xFF1A1006, 0x66E8A24A,
            0xFFA9D18E, 0xFFE8C27A, 0x73000000, 0x66000000);
    private static final Style CINEMA_STYLE = new Style(4, 8, 1, 3, 32, 0, 0, 24, 8, 10, 2,
            display("DM Serif Display", REGULAR), ui("DM Sans", REGULAR, SEMIBOLD), false, true);

    // Landscapes
    private static final Palette LANDSCAPES = new Palette(
            0xFF0A0F14, 0xFF2B4A6B, 0xFF6E8AA3, 0xFFE9B98A, 0x4D0A0F14, 0xE005080B,
            0x1AFFFFFF, 0x26FFFFFF, 0xF2FFFFFF, 0x2EFFFFFF,
            0xFFFFFFFF, 0xFFE3E8ED, 0xFFBCC6CF, 0xFF0B1218,
            0xFFFFFFFF, 0xFF0B1218, 0x59FFFFFF,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x7F000000);
    private static final Style LANDSCAPES_STYLE = new Style(20, 100, 1, 4, 28, 0, 0, 18, 6, 14, 2,
            display("Outfit", LIGHT), ui("Outfit", REGULAR, SEMIBOLD), false, false);

    private static final Style VECTOR = new Style(24, 100, 1, 5, 0, 0, 3, 0, 0, 0, 0,
            display("Baloo 2", MEDIUM), ui("Nunito", REGULAR, BOLD), false, false);
    private static final Style PIXEL = new Style(0, 0, 4, 4, 0, 6, 0, 0, 6, 0, 4,
            display("Press Start 2P", REGULAR), ui("Pixelify Sans", REGULAR, SEMIBOLD), false, false);

    private static final Palette SNOW = new Palette(
            0xFF0A1424, 0xFF1B3A66, 0xFF6D93C2, 0xFFDFEAF5, 0x420A1424, 0xEB0A1424,
            0x14FFFFFF, 0x21FFFFFF, 0xEBFFFFFF, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF0A1424,
            0xFFBFE0FF, 0xFF0A1424, 0x73BFE0FF,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette THAW = new Palette(
            0xFF11161F, 0xFF3D6FA8, 0xFFA9C8E6, 0xFFFDE7B8, 0x4211161F, 0xEB11161F,
            0x14FFFFFF, 0x21FFFFFF, 0xEBFFFFFF, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF11161F,
            0xFFFFD98A, 0xFF11161F, 0x73FFD98A,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette SPRING_RAIN = new Palette(
            0xFF0F1716, 0xFF4C5B66, 0xFF8B9AA1, 0xFFB9D4A8, 0x420F1716, 0xEB0F1716,
            0x14FFFFFF, 0x21FFFFFF, 0xEBFFFFFF, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF0F1716,
            0xFFA8E0B0, 0xFF0F1716, 0x73A8E0B0,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette BLOOM = new Palette(
            0xFF0F1A12, 0xFF5AA3D6, 0xFFBFE3F0, 0xFFF4C6D6, 0x420F1A12, 0xEB0F1A12,
            0x14FFFFFF, 0x21FFFFFF, 0xEBFFFFFF, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF0F1A12,
            0xFFF7B6CF, 0xFF0F1A12, 0x73F7B6CF,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette PRIDE = new Palette(
            0xFF0C1A20, 0xFF3FA7E0, 0xFFA9E2F2, 0xFFFFF1B8, 0x420C1A20, 0xEB0C1A20,
            0x14FFFFFF, 0x21FFFFFF, 0xEBFFFFFF, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF0C1A20,
            0xFFFF7A6E, 0xFF0C1A20, 0x73FF7A6E,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette MIDSUMMER = new Palette(
            0xFF10180C, 0xFF2F8FD8, 0xFF9FD8F5, 0xFFFFE88A, 0x4210180C, 0xEB10180C,
            0x14FFFFFF, 0x21FFFFFF, 0xEBFFFFFF, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF10180C,
            0xFFFFC93C, 0xFF10180C, 0x73FFC93C,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette LATE_SUMMER = new Palette(
            0xFF1A140A, 0xFF78A9D2, 0xFFD9D8B4, 0xFFF6E4B0, 0x421A140A, 0xEB1A140A,
            0x14FFFFFF, 0x21FFFFFF, 0xEBFFFFFF, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF1A140A,
            0xFFF2A541, 0xFF1A140A, 0x73F2A541,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette AUTUMN = new Palette(
            0xFF140F0C, 0xFF3B3046, 0xFF8A5A40, 0xFFD98A4A, 0x42140F0C, 0xEB140F0C,
            0x14FFFFFF, 0x21FFFFFF, 0xEBFFFFFF, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF140F0C,
            0xFFE8964A, 0xFF140F0C, 0x73E8964A,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette HALLOWEEN = new Palette(
            0xFF120A10, 0xFF2D1240, 0xFF6A2A4A, 0xFFFF7A1A, 0x42120A10, 0xEB120A10,
            0x14FFFFFF, 0x21FFFFFF, 0xFFFF7A1A, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFF1A0800,
            0xFFFF8A2A, 0xFF120A10, 0x73FF8A2A,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
    private static final Palette CHRISTMAS = new Palette(
            0xFF0B1410, 0xFF0E1D30, 0xFF3D5A78, 0xFFE8B04A, 0x420B1410, 0xEB0B1410,
            0x14FFFFFF, 0x21FFFFFF, 0xFFD4373B, 0x24FFFFFF,
            0xFFF7F9FB, 0xFFD2D9E1, 0xFF9AA6B4, 0xFFFFF5E1,
            0xFFE8B04A, 0xFF0B1410, 0x73E8B04A,
            0xFF9BE7B4, 0xFFFFD479, 0x59000000, 0x66000000);
}