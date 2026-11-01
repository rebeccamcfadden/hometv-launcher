package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

/**
 * What a tile shows around itself (AppTile, Home's weather strip): focused, the theme's glow and the accent focus ring
 * just outside the tile; resting, the theme's shadow, if it has one. The tile sits {@code room} design px inside the
 * drawable's bounds, the room its frame keeps for the ring. In Midnight the focused frame is exactly the 4 px ring
 * Ui.round drew before themes, and the resting one draws nothing.
 */
final class TileFrame extends Drawable {
    private final float room, radius;
    private final boolean focused, ring;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tile = new RectF();
    private final RectF edge = new RectF();
    private int alpha = 255;

    private TileFrame(float room, float radius, boolean focused, boolean ring) {
        this.room = room;
        this.radius = radius;
        this.focused = focused;
        this.ring = ring;
        ringPaint.setStyle(Paint.Style.STROKE);
    }

    /** The focused frame of a tile with corner radius {@code radius}, {@code room} design px inside the bounds. */
    static TileFrame focused(float room, float radius) {
        return new TileFrame(room, radius, true, true);
    }

    /** The resting frame: the theme's tile shadow, or nothing. */
    static TileFrame resting(float room, float radius) {
        return new TileFrame(room, radius, false, false);
    }

    /** A focused fill that is its own focus look (Home's Inputs and Shortcuts tiles), with the theme's glow behind. */
    static Drawable glowBehind(Drawable fill, float radius) {
        if (!Glow.focusOn() && !Glow.focusShadowOn()) return fill;
        return new LayerDrawable(new Drawable[]{new TileFrame(0, radius, true, false), fill});
    }

    /** A resting fill with the theme's tile shadow under it. */
    static Drawable shadowUnder(Drawable fill, float radius) {
        if (!Glow.restOn()) return fill;
        return new LayerDrawable(new Drawable[]{new TileFrame(0, radius, false, false), fill});
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float in = Ui.px(room);
        tile.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
        float r = Math.min(Ui.px(radius), Math.min(tile.width(), tile.height()) / 2f);
        if (!focused) {
            Glow.rest(c, tile, r, alpha);
            return;
        }
        Glow.focusShadow(c, tile, r, alpha);
        Glow.focus(c, tile, r, alpha);
        if (!ring || Ui.FOCUS_W <= 0) return;
        // As Ui.round(0, radius + ring, ACCENT, ring) drew it: the stroke's middle half a stroke outside the tile,
        // its corners as round as the tile's plus the ring.
        float w = Math.max(1, Ui.px(Ui.FOCUS_W));
        edge.set(tile);
        edge.inset(-w / 2f, -w / 2f);
        float er = Math.min(Ui.px(radius + Ui.FOCUS_W), Math.min(edge.width(), edge.height()) / 2f);
        ringPaint.setStrokeWidth(w);
        ringPaint.setColor(Ui.ACCENT);
        ringPaint.setAlpha(Math.round((Ui.ACCENT >>> 24) * alpha / 255f));
        c.drawRoundRect(edge, er, er, ringPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        ringPaint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    // Without a constant state, mutating the StateListDrawable or LayerDrawable around a frame logs a warning with a
    // stack trace for every tile.
    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                TileFrame copy = new TileFrame(room, radius, focused, ring);
                copy.alpha = alpha;
                return copy;
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }
}
