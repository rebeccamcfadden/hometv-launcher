package com.hometv.launcher;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * F41 (Rebecca, 2026-11-02): the TV Utilities tile's shape, a folder (a tab on its top-left edge) with a gear in the
 * middle, drawn in code (design_preview/src/ui/folderArt.ts, the same geometry). Sizes are in px at the scale given.
 * The gear's points are plain numbers, so they are tested on the JVM (FolderArtTest).
 */
final class FolderArt {
    private FolderArt() {}

    /** In design px: the tab's height and width, the corner radii and the curve where the tab meets the body. */
    static final float TAB_H = 14, TAB_W = 104, RADIUS = 16, TAB_RADIUS = 8, CURVE = 6;
    static final int TEETH = 8;
    /** The teeth's roots, and the hole, as parts of the gear's outer radius. */
    static final float ROOT = 0.78f, HOLE = 0.34f;

    /** The folder's outline in a w × h box at (left, top); s is px per design px. */
    static Path folder(float left, float top, float w, float h, float s) {
        float t = TAB_H * s, tw = TAB_W * s, r = RADIUS * s, tr = TAB_RADIUS * s, c = CURVE * s;
        Path p = new Path();
        p.moveTo(left, top + h - r);
        p.lineTo(left, top + tr);
        p.arcTo(new RectF(left, top, left + 2 * tr, top + 2 * tr), 180, 90);
        p.lineTo(left + tw - tr, top);
        p.arcTo(new RectF(left + tw - 2 * tr, top, left + tw, top + 2 * tr), 270, 90);
        p.lineTo(left + tw, top + t - c);
        p.quadTo(left + tw, top + t, left + tw + c, top + t);
        p.lineTo(left + w - r, top + t);
        p.arcTo(new RectF(left + w - 2 * r, top + t, left + w, top + t + 2 * r), 270, 90);
        p.lineTo(left + w, top + h - r);
        p.arcTo(new RectF(left + w - 2 * r, top + h - 2 * r, left + w, top + h), 0, 90);
        p.lineTo(left + r, top + h);
        p.arcTo(new RectF(left, top + h - 2 * r, left + 2 * r, top + h), 90, 90);
        p.close();
        return p;
    }

    /** The gear's outline as x, y pairs around (cx, cy), four points a tooth, starting at the top. */
    static float[] gearPoints(float cx, float cy, float r) {
        float root = r * ROOT;
        double step = 2 * Math.PI / TEETH;
        float[] out = new float[TEETH * 8];
        int i = 0;
        for (int k = 0; k < TEETH; k++) {
            double a = k * step - Math.PI / 2;
            double[][] pts = {{root, a - 0.3 * step}, {r, a - 0.17 * step}, {r, a + 0.17 * step}, {root, a + 0.3 * step}};
            for (double[] p : pts) {
                out[i++] = cx + (float) (p[0] * Math.cos(p[1]));
                out[i++] = cy + (float) (p[0] * Math.sin(p[1]));
            }
        }
        return out;
    }

    /** The gear, with its hole (even-odd). */
    static Path gear(float cx, float cy, float r) {
        float[] pts = gearPoints(cx, cy, r);
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        p.close();
        p.addCircle(cx, cy, r * HOLE, Path.Direction.CW);
        return p;
    }

    /** The gear's outer radius for a folder w × h (design px): a fifth of the body's smaller side. */
    static float gearRadius(float w, float h) {
        return Math.min(w, h - TAB_H) * 0.21f;
    }
}
