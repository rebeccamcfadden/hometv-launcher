package com.gathin.tvhome;

/**
 * How far a scrolling row or grid moves to keep its focused item in view (Home's app and Inputs rows, the All Apps
 * grid). The row scrolls by its translation, so SpatialNav sees items where they are drawn. Pure, tested on the JVM.
 */
final class Reveal {
    private Reveal() {}

    /**
     * The row's new offset (zero or negative) so the item, whose edges are start and end at offset zero, sits inside
     * viewStart..viewEnd. Moves only as far as needed, and never past the row's beginning.
     */
    static float offset(float current, float start, float end, float viewStart, float viewEnd) {
        float next = current;
        if (end + next > viewEnd) next = viewEnd - end;
        if (start + next < viewStart) next = viewStart - start;
        return Math.min(0f, next);
    }
}
