package com.gathin.tvhome;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The new UI's design tokens and the helpers that turn them into views. Everything is laid out in design pixels on
 * a 1920×1080 stage (design_preview/src/styles/tokens.css, from Figma) and scaled to the screen's real width, so a
 * TV at an unusual density draws the same layout as one at the usual 320 dpi.
 */
final class Ui {
    private Ui() {}

    static final int STAGE_W = 1920;
    static final int STAGE_H = 1080;

    // Colours (tokens.css).
    static final int BG = 0xFF090E15;
    static final int BG_RAISED = 0xFF0D141D;
    static final int SURFACE = 0x0DFFFFFF;
    static final int SURFACE_2 = 0x17FFFFFF;
    static final int SURFACE_3 = 0x24FFFFFF;
    static final int LINE = 0x17FFFFFF;
    static final int LINE_STRONG = 0x2EFFFFFF;
    static final int TEXT_1 = 0xFFF4F5F7;
    static final int TEXT_2 = 0xFFADB5C0;
    static final int TEXT_3 = 0xFF7D8999;
    static final int GHOST = 0xFF5E6878;
    static final int ACCENT = 0xFFC4DDF7;
    static final int ACCENT_SOFT = 0x24C4DDF7;
    static final int ON_ACCENT = 0xFF101820;
    static final int ACTIVE = 0xE8FFFFFF;
    static final int GOOD = 0xFF8FD9A8;
    static final int GOOD_SOFT = 0x298FD9A8;
    static final int STAR = 0xFFF2C46D;
    /** Dropdown menus: no backdrop blur, the launcher targets TVs with 1 GB of RAM. */
    static final int MENU_BG = 0xF5161C25;
    static final int TOAST_BG = 0xF7161E29;
    /** The 58% scrim a popup draws over the whole page behind it. */
    static final int SCRIM = 0x9405080C;
    /** Pages sit on the evening landscape under an 86–90% shade. */
    static final int SHADE_TOP = 0xDB090E15;
    static final int SHADE_BOTTOM = 0xE6090E15;

    // TV title-safe area: 5% of 1920 × 1080.
    static final int SAFE_X = 96;
    static final int SAFE_Y = 54;

    // Radii.
    static final int RADIUS_S = 8;
    static final int RADIUS_M = 12;
    static final int RADIUS_L = 16;
    static final int RADIUS_XL = 24;
    static final int RADIUS_PILL = 100;

    // Type scale. Nothing below 18 px: it is read from the couch.
    static final int T_DISPLAY = 84;
    static final int T_H1 = 44;
    static final int T_H2 = 30;
    static final int T_H3 = 26;
    static final int T_BODY = 24;
    static final int T_SMALL = 20;
    static final int T_MIN = 18;

    // Motion.
    static final int FAST_MS = 140;
    static final int SLOW_MS = 320;
    static final TimeInterpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    /** Roboto, the system font (docs/rework.md); Figma's Inter weights map to the nearest installed one. */
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    private static float scale = 1f;
    private static boolean reducedMotion;

    /** Reads the screen once per activity: the stage is as wide as the screen's long side. */
    static void init(Context ctx, boolean reduced) {
        DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        scale = Math.max(m.widthPixels, m.heightPixels) / (float) STAGE_W;
        float animator = Settings.Global.getFloat(ctx.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        reducedMotion = reduced || animator == 0f;
    }

    static float scale() {
        return scale;
    }

    /** Reduced motion (config reduced_motion, animations off, or the system's animator scale at 0). */
    static boolean reducedMotion() {
        return reducedMotion;
    }

    /** A duration, or none with reduced motion: pages and menus then switch without animating. */
    static long ms(long duration) {
        return reducedMotion ? 0 : duration;
    }

    /** Design pixels to screen pixels. */
    static int px(float design) {
        return Math.round(design * scale);
    }

    static TextView text(Context ctx, float size, int color, Typeface face) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView line(Context ctx, float size, int color, Typeface face) {
        TextView t = text(ctx, size, color, face);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    static ImageView icon(Context ctx, int res, float size, int color) {
        ImageView v = new ImageView(ctx);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(color));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setMinimumWidth(px(size));
        v.setMinimumHeight(px(size));
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(radius));
        return d;
    }

    static GradientDrawable round(int color, float radius, int stroke, float strokeWidth) {
        GradientDrawable d = round(color, radius);
        d.setStroke(Math.max(1, px(strokeWidth)), stroke);
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** Focused first, then activated (the current page's nav item), then the resting look. */
    static StateListDrawable states(Drawable normal, Drawable activated, Drawable focused) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        if (activated != null) s.addState(new int[]{android.R.attr.state_activated}, activated);
        s.addState(new int[]{}, normal);
        s.setEnterFadeDuration((int) ms(FAST_MS));
        s.setExitFadeDuration((int) ms(FAST_MS));
        return s;
    }

    static ColorStateList colors(int normal, int activated, int focused) {
        return new ColorStateList(new int[][]{
                {android.R.attr.state_focused}, {android.R.attr.state_activated}, {}},
                new int[]{focused, activated, normal});
    }

    /** The small scale-up a focused item gets (3–8% in the spec), animated with the focus change. */
    static void scaleOnFocus(View v, float scaleTo) {
        View.OnFocusChangeListener previous = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener((view, focused) -> {
            if (previous != null) previous.onFocusChange(view, focused);
            float s = focused ? scaleTo : 1f;
            view.animate().scaleX(s).scaleY(s).setDuration(ms(FAST_MS)).setInterpolator(EASE).start();
        });
    }
}
