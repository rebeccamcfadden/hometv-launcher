package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The D-pad moves as design_preview/src/core/focus.ts does. */
public class SpatialNavTest {

    private static float[] r(float left, float top, float w, float h) {
        return new float[]{left, top, left + w, top + h};
    }

    @Test
    public void rightPicksTheNextItemInTheRow() {
        float[] from = r(0, 0, 100, 60);
        List<float[]> rects = Arrays.asList(r(220, 0, 100, 60), r(110, 0, 100, 60), r(110, 200, 100, 60));
        assertEquals(1, SpatialNav.nearest(from, rects, SpatialNav.Dir.RIGHT));
    }

    @Test
    public void nothingBehindTheDirectionCounts() {
        float[] from = r(200, 0, 100, 60);
        assertEquals(-1, SpatialNav.nearest(from, Arrays.asList(r(0, 0, 100, 60)), SpatialNav.Dir.RIGHT));
        assertEquals(-1, SpatialNav.nearest(from, Arrays.asList(r(200, -100, 100, 60)), SpatialNav.Dir.DOWN));
        assertEquals(-1f, SpatialNav.score(from, r(200, 0, 100, 60), SpatialNav.Dir.LEFT), 0f);
    }

    @Test
    public void downKeepsTheColumnByLeftEdge() {
        // The nav's Movies pill, then a grid row below: the card under its left edge wins over a nearer-centred one.
        float[] from = r(240, 48, 140, 60);
        List<float[]> rects = Arrays.asList(r(96, 300, 417, 172), r(533, 300, 417, 172));
        assertEquals(0, SpatialNav.nearest(from, rects, SpatialNav.Dir.DOWN));
    }

    @Test
    public void overlappingItemsBeatOffsetOnes() {
        float[] from = r(0, 100, 100, 60);
        List<float[]> rects = Arrays.asList(r(150, 0, 100, 60), r(400, 110, 100, 60));
        assertEquals(1, SpatialNav.nearest(from, rects, SpatialNav.Dir.RIGHT));
    }

    @Test
    public void upFromTheContentReachesTheNav() {
        float[] from = r(96, 300, 417, 172);
        List<float[]> nav = Arrays.asList(r(102, 54, 132, 60), r(240, 54, 145, 60), r(1630, 56, 56, 56));
        assertEquals(0, SpatialNav.nearest(from, nav, SpatialNav.Dir.UP));
        assertTrue(SpatialNav.score(from, nav.get(2), SpatialNav.Dir.UP) > SpatialNav.score(from, nav.get(0), SpatialNav.Dir.UP));
    }
}
