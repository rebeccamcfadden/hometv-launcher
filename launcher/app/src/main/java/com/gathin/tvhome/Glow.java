package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.Arrays;

/**
 * The theme's glow around a focused tile and shadow under a resting one (Theme glow* and shadow*), drawn behind the
 * tile the way CSS box-shadow draws them: never under the tile itself, so a see-through tile shows none of it.
 * Nothing is blurred while drawing, a 1 GB TV can't afford that on every frame: the blurred shape is drawn once into
 * a small alpha mask with the tile cut out of it, and each draw stretches the mask's middle row and column along the
 * tile, nine-patch fashion. A glow with an offset (8-bit) is a hard shadow: the same mask without blur, moved down and
 * right. Main thread only: the paint and rects are shared.
 */
final class Glow {
    private Glow() {}

    private static final int MAX_MASKS = 8;
    private static final Mask[] MASKS = new Mask[MAX_MASKS];
    private static int nextSlot;

    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Rect SRC = new Rect();
    private static final RectF DST = new RectF();

    /**
     * A shadow shape (the tile grown by spread, moved by dx, dy, blurred) with the tile cut out, for a tile whose
     * corners take {@code core} px each way; the mask is {@code margin} px bigger than that tile all round.
     */
    private static final class Mask {
        final int radius, blur, spread, dx, dy, core, margin;
        final Bitmap bitmap;

        Mask(int radius, int blur, int spread, int dx, int dy, int core) {
            this.radius = radius;
            this.blur = blur;
            this.spread = spread;
            this.dx = dx;
            this.dy = dy;
            this.core = core;
            // CSS blur B is a Gaussian of sigma B/2, visible to about 3 sigma.
            int pad = (int) Math.ceil(blur * 1.5f);
            margin = pad + spread + Math.max(dx, dy);
            int tile = 2 * core + 1;
            int size = 2 * margin + tile;
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            Canvas c = new Canvas(bitmap);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF000000);
            // Skia turns a blur radius into sigma = 0.57735 r + 0.5.
            if (blur > 0) p.setMaskFilter(new BlurMaskFilter(Math.max(0.1f, (blur / 2f - 0.5f) / 0.57735f),
                    BlurMaskFilter.Blur.NORMAL));
            RectF r = new RectF(margin - spread + dx, margin - spread + dy,
                    margin + tile + spread + dx, margin + tile + spread + dy);
            c.drawRoundRect(r, radius + spread, radius + spread, p);
            Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            r.set(margin, margin, margin + tile, margin + tile);
            c.drawRoundRect(r, radius, radius, clear);
        }

        boolean is(int radius, int blur, int spread, int dx, int dy, int core) {
            return this.radius == radius && this.blur == blur && this.spread == spread && this.dx == dx
                    && this.dy == dy && this.core == core;
        }
    }

    /** Whether a focused tile draws anything here; false in Midnight. */
    static boolean focusOn() {
        Theme t = Ui.theme();
        return t.tileGlow && (t.glowColor >>> 24) != 0;
    }

    /** Whether a resting tile draws a shadow; false in Midnight. */
    static boolean restOn() {
        Theme t = Ui.theme();
        return t.tileShadow && (t.shadowColor >>> 24) != 0;
    }

    /** The focused tile's glow (or hard shadow) around tile, in screen px; alpha fades it with its state. */
    static void focus(Canvas c, RectF tile, float radius, int alpha) {
        if (!focusOn()) return;
        Theme t = Ui.theme();
        int spread = Ui.px(t.glowSpread);
        if (t.glowOffset > 0) {
            int o = Ui.px(t.glowOffset);
            draw(c, tile, radius, 0, spread, o, o, t.glowColor, alpha);
        } else {
            draw(c, tile, radius, Ui.px(t.glowBlur), spread, 0, 0, t.glowColor, alpha);
        }
    }

    /** A resting tile's shadow under tile, in screen px. */
    static void rest(Canvas c, RectF tile, float radius, int alpha) {
        if (!restOn()) return;
        Theme t = Ui.theme();
        draw(c, tile, radius, Ui.px(t.shadowBlur), 0, 0, Ui.px(t.shadowOffset), t.shadowColor, alpha);
    }

    private static void draw(Canvas c, RectF tile, float radiusPx, int blur, int spread, int dx, int dy, int color,
                             int alpha) {
        int radius = Math.max(0, Math.round(radiusPx));
        blur = Math.max(0, blur);
        spread = Math.max(0, spread);
        dx = Math.max(0, dx);
        dy = Math.max(0, dy);
        // The middle row and column are taken where the shape and the cut-out are both straight, past the blur of
        // their corners; a tile too small for that (a round portrait) gets a mask its own size.
        int ideal = radius + spread + Math.max(dx, dy) + (int) Math.ceil(blur * 1.5f);
        int fits = (int) Math.floor((Math.min(tile.width(), tile.height()) - 1) / 2f);
        int core = Math.max(0, Math.min(ideal, fits));
        Mask m = mask(radius, blur, spread, dx, dy, core);
        PAINT.setColor(color);
        PAINT.setAlpha(Math.round((color >>> 24) * alpha / 255f));
        float l = tile.left - m.margin, t = tile.top - m.margin, r = tile.right + m.margin, b = tile.bottom + m.margin;
        int n = m.margin + m.core, s = m.bitmap.getWidth();
        // A tile a fraction of a pixel smaller than the mask's squeezes its corners rather than overlapping them.
        float cx = Math.min(n, (r - l) / 2f), cy = Math.min(n, (b - t) / 2f);
        // The middle piece is skipped: it lies under the tile, where the mask is empty.
        piece(c, m, 0, 0, n, n, l, t, l + cx, t + cy);
        piece(c, m, n, 0, n + 1, n, l + cx, t, r - cx, t + cy);
        piece(c, m, n + 1, 0, s, n, r - cx, t, r, t + cy);
        piece(c, m, 0, n, n, n + 1, l, t + cy, l + cx, b - cy);
        piece(c, m, n + 1, n, s, n + 1, r - cx, t + cy, r, b - cy);
        piece(c, m, 0, n + 1, n, s, l, b - cy, l + cx, b);
        piece(c, m, n, n + 1, n + 1, s, l + cx, b - cy, r - cx, b);
        piece(c, m, n + 1, n + 1, s, s, r - cx, b - cy, r, b);
    }

    private static void piece(Canvas c, Mask m, int sl, int st, int sr, int sb, float dl, float dt, float dr, float db) {
        if (dr <= dl || db <= dt) return;
        SRC.set(sl, st, sr, sb);
        DST.set(dl, dt, dr, db);
        c.drawBitmap(m.bitmap, SRC, DST, PAINT);
    }

    /** The mask for a shape, made once: a theme uses a handful of tile radii and sizes. */
    private static Mask mask(int radius, int blur, int spread, int dx, int dy, int core) {
        for (Mask m : MASKS) {
            if (m != null && m.is(radius, blur, spread, dx, dy, core)) return m;
        }
        Mask m = new Mask(radius, blur, spread, dx, dy, core);
        MASKS[nextSlot] = m;
        nextSlot = (nextSlot + 1) % MAX_MASKS;
        return m;
    }

    /** Lets the masks go (a theme change); they are made again on the next draw. */
    static void clear() {
        Arrays.fill(MASKS, null);
        nextSlot = 0;
    }
}
