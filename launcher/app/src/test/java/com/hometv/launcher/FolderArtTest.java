package com.hometv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The TV Utilities folder's gear (F41): eight teeth, their tips on the outer radius and their roots inside it. */
public class FolderArtTest {

    private static double dist(float[] p, int i, float cx, float cy) {
        return Math.hypot(p[i] - cx, p[i + 1] - cy);
    }

    @Test
    public void eightTeethOfFourPoints() {
        assertEquals(FolderArt.TEETH * 4 * 2, FolderArt.gearPoints(0, 0, 10).length);
    }

    @Test
    public void tipsOnTheRadiusAndRootsInside() {
        float cx = 132, cy = 81, r = 28;
        float[] p = FolderArt.gearPoints(cx, cy, r);
        for (int k = 0; k < FolderArt.TEETH; k++) {
            int i = k * 8;
            assertEquals(r * FolderArt.ROOT, dist(p, i, cx, cy), 1e-3);
            assertEquals(r, dist(p, i + 2, cx, cy), 1e-3);
            assertEquals(r, dist(p, i + 4, cx, cy), 1e-3);
            assertEquals(r * FolderArt.ROOT, dist(p, i + 6, cx, cy), 1e-3);
        }
        // The first tooth points up, straight above the centre.
        assertEquals(cx, (p[2] + p[4]) / 2, 1e-3);
        assertTrue(p[3] < cy);
    }

    @Test
    public void theGearFitsTheBodyUnderTheTab() {
        float r = FolderArt.gearRadius(264, 148.5f);
        assertTrue(r > 20 && 2 * r < 148.5f - FolderArt.TAB_H);
        assertTrue(FolderArt.TAB_H >= FolderArt.TAB_RADIUS + FolderArt.CURVE);
    }
}
