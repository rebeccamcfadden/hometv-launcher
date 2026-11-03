package com.hometv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** AppArt keeps art at 16 bits only when no pixel shows through (F15 P3-5). */
public class ArtPixelsTest {

    @Test
    public void fullyOpaquePixelsAreOpaque() {
        assertTrue(ArtPixels.opaque(new int[]{0xFF000000, 0xFFFFFFFF, 0xFF123456}));
        assertTrue("an empty row", ArtPixels.opaque(new int[0]));
    }

    @Test
    public void anyAlphaBelowFullShowsThrough() {
        assertFalse(ArtPixels.opaque(new int[]{0xFF000000, 0xFEFFFFFF}));
        assertFalse("a transparent corner", ArtPixels.opaque(new int[]{0x00000000, 0xFF000000}));
        assertFalse(ArtPixels.opaque(new int[]{0x80FFFFFF}));
    }
}
