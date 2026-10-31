package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * A theme's overlays over the backdrop and under the pages (Figma, Components &amp; backdrops): Cinema's vignette, one
 * radial gradient, and Retro's scanlines, one tiled bitmap. Both are still, drawn once per frame like any background,
 * and absent in themes that don't ask for them.
 */
final class Overlays {
    private Overlays() {}

    /** Black at 0% out to 55% of the way to the edge, then up to 70% at the edge of the inscribed ellipse. */
    private static final int VIGNETTE_EDGE = 0xB3000000;
    private static final float VIGNETTE_CLEAR = 0.55f;
    /** A 2 px line at 32% black every 4 px. */
    private static final int SCANLINE = 0x52000000;
    private static final int SCANLINE_PERIOD = 4;

    /** Adds the theme's overlays to parent, in order; nothing when it has none. */
    static void add(ViewGroup parent, Context ctx, Theme t) {
        if (t.vignette) parent.addView(still(ctx, new Vignette()), match());
        if (t.scanlines) parent.addView(still(ctx, scanlines(ctx)), match());
    }

    private static View still(Context ctx, Drawable d) {
        View v = new View(ctx);
        v.setBackground(d);
        v.setFocusable(false);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static Drawable scanlines(Context ctx) {
        int period = Math.max(2, Ui.px(SCANLINE_PERIOD));
        Bitmap b = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888);
        for (int y = period / 2; y < period; y++) b.setPixel(0, y, SCANLINE);
        BitmapDrawable d = new BitmapDrawable(ctx.getResources(), b);
        d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return d;
    }

    /** Figma's radial gradient with an identity transform: an ellipse that just fits the screen, clamped beyond. */
    private static final class Vignette extends Drawable {
        private final Paint paint = new Paint();
        private final Matrix matrix = new Matrix();

        @Override
        protected void onBoundsChange(Rect b) {
            float rx = Math.max(1, b.width() / 2f), ry = Math.max(1, b.height() / 2f);
            RadialGradient g = new RadialGradient(0, 0, rx, new int[]{0, 0, VIGNETTE_EDGE},
                    new float[]{0f, VIGNETTE_CLEAR, 1f}, Shader.TileMode.CLAMP);
            matrix.setScale(1f, ry / rx);
            matrix.postTranslate(b.exactCenterX(), b.exactCenterY());
            g.setLocalMatrix(matrix);
            paint.setShader(g);
        }

        @Override
        public void draw(Canvas c) {
            c.drawRect(getBounds(), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
