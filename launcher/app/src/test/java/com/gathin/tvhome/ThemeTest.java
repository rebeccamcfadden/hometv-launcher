package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Midnight is today's look: its values are the ones Ui had before themes. */
public class ThemeTest {
    @Test
    public void midnightKeepsTodaysTokens() {
        Theme t = Theme.midnight();
        assertEquals(0xFF090E15, t.bg);
        assertEquals(0xFF0D141D, t.bgRaised);
        assertEquals(0x0DFFFFFF, t.surface);
        assertEquals(0x17FFFFFF, t.surface2);
        assertEquals(0x24FFFFFF, t.surface3);
        assertEquals(0x17FFFFFF, t.line);
        assertEquals(0x2EFFFFFF, t.lineStrong);
        assertEquals(0xFFF4F5F7, t.text1);
        assertEquals(0xFFADB5C0, t.text2);
        assertEquals(0xFF7D8999, t.text3);
        assertEquals(0xFF5E6878, t.ghost);
        assertEquals(0xFFC4DDF7, t.accent);
        assertEquals(0x24C4DDF7, t.accentSoft);
        assertEquals(0xFF101820, t.onAccent);
        assertEquals(0xE8FFFFFF, t.active);
        assertEquals(0xFF8FD9A8, t.good);
        assertEquals(0x298FD9A8, t.goodSoft);
        assertEquals(0xFFF2C46D, t.star);
        assertEquals(0xF5161C25, t.menuBg);
        assertEquals(0xF7161E29, t.toastBg);
        assertEquals(0x9405080C, t.scrim);
        assertEquals(0xDB090E15, t.shadeTop);
        assertEquals(0xE6090E15, t.shadeBottom);
        assertEquals(8f, t.radiusS, 0f);
        assertEquals(12f, t.radiusM, 0f);
        assertEquals(16f, t.radiusL, 0f);
        assertEquals(24f, t.radiusXl, 0f);
        assertEquals(100f, t.radiusPill, 0f);
    }

    /** The colours that were literals in the screens before themes, and the shape and type of today's look. */
    @Test
    public void midnightKeepsTodaysLiterals() {
        Theme t = Theme.midnight();
        assertEquals(0xFF101820, t.onActive);
        assertEquals(0x0BFFFFFF, t.tile);
        assertEquals(0x08FFFFFF, t.panel);
        assertEquals(0x0FFFFFFF, t.card);
        assertEquals(0x1FFFFFFF, t.selected);
        assertEquals(0xFF121A24, t.dialogBg);
        assertEquals(0xFF1C2633, t.portrait);
        assertArrayEquals(new int[]{0x40090E15, 0x8C090E15, 0xEB090E15}, t.homeShade);
        assertEquals(0x99090E15, t.homeDim);
        assertEquals(1f, t.lineWidth, 0f);
        assertEquals(4f, t.focusWidth, 0f);
        assertFalse(t.tileGlow);
        assertFalse(t.tileShadow);
        assertEquals(0, t.textShadowColor >>> 24);
        assertFalse(t.vignette);
        assertFalse(t.scanlines);
        assertSame(Theme.Font.SYSTEM, t.displayFont);
        assertSame(Theme.Font.SYSTEM, t.uiFont);
    }

    /** Only Midnight leaves the glow and the tile shadow out; a theme built from the defaults draws them. */
    @Test
    public void otherThemesDrawTheirGlowAndShadow() {
        Theme other = new Theme.Builder("daylight", Theme.Group.CLASSIC, "Daylight").build();
        assertTrue(other.tileGlow);
        assertTrue(other.tileShadow);
        Theme.Builder b = Theme.midnight().toBuilder();
        b.tile = 0x12345678;
        b.dialogBg = 0xFF000001;
        Theme copy = b.build();
        assertEquals(0x12345678, copy.tile);
        assertEquals(0xFF000001, copy.dialogBg);
        assertEquals(0xFF1C2633, copy.portrait);
        assertFalse(copy.tileGlow);
    }

    @Test
    public void unknownIdsFallBackToMidnight() {
        assertEquals("midnight", Themes.resolve("no-such-theme", java.time.LocalDate.of(2026, 10, 30)).id);
    }
}
