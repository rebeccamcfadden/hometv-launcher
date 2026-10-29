package com.gathin.tvhome;

import java.util.List;

/**
 * Spatial D-pad navigation, ported from design_preview/src/core/focus.ts so the TV moves exactly as the prototype
 * does: the nearest item in the pressed direction wins, and moving up or down keeps left edges aligned so a column
 * feels like a column. Plain Java on {left, top, right, bottom} rectangles, so it is tested on the JVM.
 */
final class SpatialNav {
    private SpatialNav() {}

    enum Dir { UP, DOWN, LEFT, RIGHT }

    /** How far b is from a when moving in dir (lower is nearer), or -1 when b is not in that direction. */
    static float score(float[] a, float[] b, Dir dir) {
        float ax = (a[0] + a[2]) / 2, ay = (a[1] + a[3]) / 2;
        float bx = (b[0] + b[2]) / 2, by = (b[1] + b[3]) / 2;
        boolean horizontal = dir == Dir.LEFT || dir == Dir.RIGHT;
        float primary;
        switch (dir) {
            case RIGHT:
                if (bx <= ax + 1 || b[2] <= a[2]) return -1;
                primary = Math.max(0, b[0] - a[2]);
                break;
            case LEFT:
                if (bx >= ax - 1 || b[0] >= a[0]) return -1;
                primary = Math.max(0, a[0] - b[2]);
                break;
            case DOWN:
                if (by <= ay + 1 || b[3] <= a[3]) return -1;
                primary = Math.max(0, b[1] - a[3]);
                break;
            default:
                if (by >= ay - 1 || b[1] >= a[1]) return -1;
                primary = Math.max(0, a[1] - b[3]);
                break;
        }
        float overlap = horizontal
                ? Math.min(a[3], b[3]) - Math.max(a[1], b[1])
                : Math.min(a[2], b[2]) - Math.max(a[0], b[0]);
        float offAxis = horizontal ? Math.abs(by - ay) : Math.abs(bx - ax);
        float secondary = overlap > 0 ? 0 : offAxis;
        float align = horizontal ? offAxis : Math.abs(b[0] - a[0]);
        return primary + secondary * 2.5f + align * 0.08f;
    }

    /** The index of the nearest rectangle in dir, or -1 when there is none. */
    static int nearest(float[] from, List<float[]> rects, Dir dir) {
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < rects.size(); i++) {
            float s = score(from, rects.get(i), dir);
            if (s >= 0 && s < bestScore) {
                bestScore = s;
                best = i;
            }
        }
        return best;
    }
}
