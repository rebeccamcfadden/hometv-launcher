package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * A theme's 156×100 swatch in the Theme pane (Figma "Swatch" component, frame 18:10), drawn from that theme's own
 * values, not Ui's current tokens: its sky (or plain bg when it has no backdrop), Home's shelf with one focused mini
 * tile and two resting ones, and "Aa" in its display face. Shapes and text only, no bitmaps: a TV with 1 GB of RAM
 * draws nine of them. Built in code only, never inflated from XML, so it has no XML constructors.
 */
@SuppressLint("ViewConstructor")
final class ThemeSwatch extends View {
    static final float W = 156, H = 100;
    private static final float RADIUS = 14;
    private static final float SHELF_TOP = 62;
    private static final float TILE_W = 40, TILE_H = 24, TILE_TOP = 66;
    private static final float[] TILE_LEFT = {12, 60, 108};

    private final Theme theme;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sky = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private float unit = 1f;

    /** face: the theme's display face when it is on the TV, else Roboto Light (Figma's Inter Light). */
    ThemeSwatch(Context ctx, Theme theme, Typeface face) {
        super(ctx);
        this.theme = theme;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(face);
        text.setColor(theme.text1);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        unit = w / W;
        clip.reset();
        rect.set(0, 0, w, h);
        clip.addRoundRect(rect, RADIUS * unit, RADIUS * unit, Path.Direction.CW);
        sky.setShader(new LinearGradient(0, 0, 0, h, new int[]{theme.skyTop, theme.skyMid, theme.horizon},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        text.setTextSize(34 * unit);
        if (Color.alpha(theme.textShadowColor) > 0) {
            text.setShadowLayer(Math.max(1f, theme.textShadowBlur * unit / 2), 0, theme.textShadowOffset * unit,
                    theme.textShadowColor);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        c.save();
        c.clipPath(clip);
        c.drawColor(theme.bg);
        if (!"none".equals(theme.backdrop)) c.drawRect(0, 0, w, h, sky);
        fill.setColor(shelf(theme));
        c.drawRect(0, SHELF_TOP * unit, w, h, fill);

        float baseline = 8 * unit - text.ascent();
        c.drawText("Aa", 12 * unit, baseline, text);

        float r = tileRadius(theme) * unit;
        for (int i = 0; i < TILE_LEFT.length; i++) {
            boolean focused = i == 0;
            float sw = (focused ? 2.5f : 1f) * unit;
            rect.set(TILE_LEFT[i] * unit, TILE_TOP * unit, (TILE_LEFT[i] + TILE_W) * unit, (TILE_TOP + TILE_H) * unit);
            fill.setColor(focused ? theme.surface2 : theme.surface);
            c.drawRoundRect(rect, r, r, fill);
            rect.inset(sw / 2, sw / 2);
            stroke.setStrokeWidth(sw);
            stroke.setColor(focused ? theme.accent : theme.line);
            c.drawRoundRect(rect, r, r, stroke);
        }
        c.restore();
    }

    /** Home's shade at the shelf, where the apps sit: the bottom stop of the theme's Home shade. */
    private static int shelf(Theme t) {
        return t.homeShade.length > 0 ? t.homeShade[t.homeShade.length - 1] : t.shadeBottom;
    }

    /** The mini tile's corners follow the theme's small radius: 5 at Midnight's 8, square in a square theme. */
    private static float tileRadius(Theme t) {
        return Math.min(t.radiusS, 16) * 5f / 8f;
    }
}
