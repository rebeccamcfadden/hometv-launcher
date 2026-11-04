package com.hometv.launcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** F36: the clock screensaver's choices and rules (Screensaver), and its pane's rows (ScreensaverSection). */
public class ScreensaverTest {

    @Test
    public void offUnlessATimeIsSaved() {
        assertEquals(Screensaver.After.OFF, Screensaver.After.parse(null));
        assertEquals(Screensaver.After.OFF, Screensaver.After.parse(""));
        assertEquals(Screensaver.After.OFF, Screensaver.After.parse("10"));
        assertEquals(Screensaver.After.FIVE, Screensaver.After.parse("5"));
        assertEquals(Screensaver.After.FIFTEEN, Screensaver.After.parse("15"));
        assertEquals(Screensaver.After.THIRTY, Screensaver.After.parse("30"));
        assertEquals(15 * 60_000L, Screensaver.After.FIFTEEN.ms());
        assertEquals(0L, Screensaver.After.OFF.ms());
    }

    @Test
    public void theOptionsAreInThePrototypesOrder() {
        // Off first, as in design_preview's SCREENSAVER_AFTER.
        StringBuilder b = new StringBuilder();
        for (Screensaver.After a : Screensaver.After.values()) b.append(a.code).append(' ');
        assertEquals("off 5 15 30 ", b.toString());
    }

    @Test
    public void glowUnlessAnotherBackIsSaved() {
        assertEquals(Screensaver.Back.GLOW, Screensaver.Back.parse(null));
        assertEquals(Screensaver.Back.GLOW, Screensaver.Back.parse("wallpaper"));
        assertEquals(Screensaver.Back.PICTURE, Screensaver.Back.parse("picture"));
        assertEquals(Screensaver.Back.BLACK, Screensaver.Back.parse("black"));
    }

    @Test
    public void startsOnlyOnHomeInFrontWithATimeChosen() {
        assertTrue(Screensaver.mayStart(Screensaver.After.FIVE, true, true, true));
        // Off: never.
        assertFalse(Screensaver.mayStart(Screensaver.After.OFF, true, true, true));
        // Another page, a dropdown or a popup over Home.
        assertFalse(Screensaver.mayStart(Screensaver.After.FIVE, false, true, true));
        // An app or Live TV in front.
        assertFalse(Screensaver.mayStart(Screensaver.After.FIVE, true, false, true));
        // Quick settings or another window over Home.
        assertFalse(Screensaver.mayStart(Screensaver.After.FIVE, true, true, false));
    }

    @Test
    public void theClockStaysInsideTenToNinetyPercentOfTheRoom() {
        assertEquals(100, Screensaver.place(0f, 1000));
        assertEquals(900, Screensaver.place(1f, 1000));
        assertEquals(500, Screensaver.place(0.5f, 1000));
        // Out-of-range randoms and no room at all stay on screen.
        assertEquals(900, Screensaver.place(2f, 1000));
        assertEquals(100, Screensaver.place(-1f, 1000));
        assertEquals(0, Screensaver.place(0.7f, -40));
    }

    @Test
    public void theGlowMixesChannelByChannel() {
        assertEquals(0xFF2B5CFF, Screensaver.mix(0xFF2B5CFF, 0xFF00A39A, 0f));
        assertEquals(0xFF00A39A, Screensaver.mix(0xFF2B5CFF, 0xFF00A39A, 1f));
        assertEquals(0xFF808080, Screensaver.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x80000000, Screensaver.mix(0x00000000, 0xFF000000, 0.5f));
    }

    @Test
    public void theGlowFadesFromAQuarterToNothing() {
        assertArrayEquals(new int[]{0x402B5CFF, 0x142B5CFF, 0x002B5CFF}, Screensaver.glowStops(0xFF2B5CFF));
    }

    @Test
    public void theTimeChangesOnTheMinute() {
        assertEquals(60_000L, Screensaver.toNextMinute(120_000L));
        assertEquals(1L, Screensaver.toNextMinute(119_999L));
        assertEquals(30_000L, Screensaver.toNextMinute(1_735_689_630_000L));
    }

    @Test
    public void downGoesRowByRow() {
        assertEquals(ScreensaverSection.ROW_BACK, ScreensaverSection.below(ScreensaverSection.ROW_AFTER));
        assertEquals(ScreensaverSection.ROW_NOW, ScreensaverSection.below(ScreensaverSection.ROW_BACK));
        assertNull(ScreensaverSection.below(ScreensaverSection.ROW_NOW));
        assertNull(ScreensaverSection.below("perf-motion"));
        assertNull(ScreensaverSection.below(null));
    }

    @Test
    public void overTheClockAHelpersButtonOnlyWakesIt() {
        int yellow = android.view.KeyEvent.KEYCODE_PROG_YELLOW;
        int down = android.view.KeyEvent.ACTION_DOWN, up = android.view.KeyEvent.ACTION_UP;
        int[] woken = {0};
        try {
            Screensaver.setShown(() -> {
                woken[0]++;
                Screensaver.setShown(null);
            });
            // The press wakes the clock and is swallowed, then its repeats and its release.
            assertTrue(Screensaver.helperKey(yellow, down, 0));
            assertEquals(1, woken[0]);
            assertTrue(Screensaver.helperKey(yellow, down, 1));
            assertTrue(Screensaver.helperKey(yellow, up, 0));
            // The next press of it is the button again.
            assertFalse(Screensaver.helperKey(yellow, down, 0));
            assertFalse(Screensaver.helperKey(yellow, up, 0));
            // Volume works over the clock and leaves it up.
            Screensaver.setShown(() -> woken[0]++);
            assertFalse(Screensaver.helperKey(android.view.KeyEvent.KEYCODE_VOLUME_UP, down, 0));
            assertEquals(1, woken[0]);
        } finally {
            Screensaver.setShown(null);
            Screensaver.helperKey(yellow, up, 0);
        }
    }

    @Test
    public void noClockNoChange() {
        assertFalse(Screensaver.helperKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.ACTION_DOWN, 0));
        assertFalse(Screensaver.helperKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.ACTION_UP, 0));
    }

    @Test
    public void theClockIsLightWhateverTheTheme() {
        // Daylight's text-1 is #111821: on black it would be 1.2:1. The clock keeps Midnight's light text.
        assertEquals(0xFFF4F5F7, Screensaver.TEXT_1);
        assertEquals(0xFFADB5C0, Screensaver.TEXT_2);
    }
}