package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Decoding artwork at the size it is shown (ArtSize), and how far a ScrollBox scrolls to reveal an item. */
public class ArtSizeTest {

    @Test
    public void sampleKeepsTheImageAtLeastAsBigAsTheBox() {
        // A 960 × 536 source in a 417 × 172 library card: half size, 480 × 268.
        assertEquals(2, ArtSize.sampleSize(960, 536, 417, 172));
        // In a 271 × 152 small card a quarter (240 wide) would be too small.
        assertEquals(2, ArtSize.sampleSize(960, 536, 271, 152));
        assertEquals(4, ArtSize.sampleSize(960, 536, 200, 100));
        // The title preview's art is wider than the source: no sampling.
        assertEquals(1, ArtSize.sampleSize(960, 407, 1728, 520));
        assertEquals(1, ArtSize.sampleSize(960, 536, 0, 0));
        assertEquals(1, ArtSize.sampleSize(0, 0, 100, 100));
    }

    @Test
    public void coverScalesOnlyWhenItSavesMemory() {
        assertNull(ArtSize.cover(480, 268, 417, 172));
        int[] small = ArtSize.cover(480, 268, 271, 152);
        assertArrayEquals(new int[]{272, 152}, small);
        int[] wide = ArtSize.cover(480, 204, 271, 152);
        assertTrue(wide[0] >= 271 && wide[1] >= 152);
        assertEquals(152, wide[1]);
        assertNull(ArtSize.cover(480, 268, 0, 152));
    }

    @Test
    public void scrollBoxScrollsJustEnough() {
        // A 1000 px box with 48 px to spare at each end.
        assertEquals(0, ScrollBox.delta(100, 300, 1000, 48));
        assertEquals(-28, ScrollBox.delta(20, 220, 1000, 48));
        assertEquals(148, ScrollBox.delta(800, 1100, 1000, 48));
        // Rows use no padding: an item flush with the edge stays put.
        assertEquals(0, ScrollBox.delta(0, 1000, 1000, 0));
        assertEquals(-5, ScrollBox.delta(-5, 200, 1000, 0));
    }
}
