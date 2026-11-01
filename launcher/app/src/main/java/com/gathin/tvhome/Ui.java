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
    static int BG = 0xFF090E15;
    static int BG_RAISED = 0xFF0D141D;
    static int SURFACE = 0x0DFFFFFF;
    static int SURFACE_2 = 0x17FFFFFF;
    static int SURFACE_3 = 0x24FFFFFF;
    static int LINE = 0x17FFFFFF;
    static int LINE_STRONG = 0x2EFFFFFF;
    static int TEXT_1 = 0xFFF4F5F7;
    static int TEXT_2 = 0xFFADB5C0;
    static int TEXT_3 = 0xFF7D8999;
    static int GHOST = 0xFF5E6878;
    static int ACCENT = 0xFFC4DDF7;
    static int ACCENT_SOFT = 0x24C4DDF7;
    static int ON_ACCENT = 0xFF101820;
    static int ACTIVE = 0xE8FFFFFF;
    /** Text and icons on the ACTIVE fill (the current page's nav item, a focused menu row). */
    static int ON_ACTIVE = 0xFF101820;
    static int GOOD = 0xFF8FD9A8;
    static int GOOD_SOFT = 0x298FD9A8;
    static int STAR = 0xFFF2C46D;
    /** Dropdown menus: no backdrop blur, the launcher targets TVs with 1 GB of RAM. */
    static int MENU_BG = 0xF5161C25;
    static int TOAST_BG = 0xF7161E29;
    /** The 58% scrim a popup draws over the whole page behind it. */
    static int SCRIM = 0x9405080C;
    /** Pages sit on the evening landscape under an 86–90% shade. */
    static int SHADE_TOP = 0xDB090E15;
    static int SHADE_BOTTOM = 0xE6090E15;
    /** Tile, panel and card fills, the active tab, a dialog's fill and a portrait's (Theme has each one's use). */
    static int TILE = 0x0BFFFFFF;
    static int PANEL = 0x08FFFFFF;
    static int CARD = 0x0FFFFFFF;
    static int SELECTED = 0x1FFFFFFF;
    static int DIALOG_BG = 0xFF121A24;
    static int PORTRAIT = 0xFF1C2633;

    // TV title-safe area: 5% of 1920 × 1080.
    static final int SAFE_X = 96;
    static final int SAFE_Y = 54;

    // Radii.
    static int RADIUS_S = 8;
    static int RADIUS_M = 12;
    static int RADIUS_L = 16;
    static int RADIUS_XL = 24;
    static int RADIUS_PILL = 100;

    /** Hairlines (the 1 px edge of tiles, panels and pills) and the focus ring, in design px. */
    static float LINE_W = 1;
    static float FOCUS_W = 4;

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
    private static final Typeface ROBOTO_REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface ROBOTO_BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    /** The UI faces: Roboto, or the theme's UI font once ThemeFonts has it on the TV. */
    static Typeface REGULAR = ROBOTO_REGULAR;
    static Typeface MEDIUM = ROBOTO_MEDIUM;
    static Typeface BOLD = ROBOTO_BOLD;

    /** The display face: greeting, clock and page titles. Roboto unless the theme brings its own. */
    static Typeface DISPLAY = REGULAR;
    /** Whether DISPLAY is the theme's own face rather than Roboto (see {@link #display}). */
    private static boolean themedDisplay;

    /** The theme the tokens above were last set from (Midnight until {@link #apply(Theme)} runs). */
    private static Theme theme = Theme.midnight();

    private static float scale = 1f;
    private static boolean reducedMotion;

    static Theme theme() {
        return theme;
    }

    /**
     * Sets every token from a theme. Call before building views: screens read the tokens when they are built, so a
     * theme change rebuilds them (MainActivity recreates). Fonts are not set here: ThemeFonts sets
     * REGULAR, MEDIUM, BOLD and DISPLAY once the theme's faces are on the TV (Roboto until then).
     */
    static void apply(Theme t) {
        theme = t;
        BG = t.bg;
        BG_RAISED = t.bgRaised;
        SURFACE = t.surface;
        SURFACE_2 = t.surface2;
        SURFACE_3 = t.surface3;
        LINE = t.line;
        LINE_STRONG = t.lineStrong;
        TEXT_1 = t.text1;
        TEXT_2 = t.text2;
        TEXT_3 = t.text3;
        GHOST = t.ghost;
        ACCENT = t.accent;
        ACCENT_SOFT = t.accentSoft;
        ON_ACCENT = t.onAccent;
        ACTIVE = t.active;
        ON_ACTIVE = t.onActive;
        GOOD = t.good;
        GOOD_SOFT = t.goodSoft;
        STAR = t.star;
        MENU_BG = t.menuBg;
        TOAST_BG = t.toastBg;
        SCRIM = t.scrim;
        SHADE_TOP = t.shadeTop;
        SHADE_BOTTOM = t.shadeBottom;
        TILE = t.tile;
        PANEL = t.panel;
        CARD = t.card;
        SELECTED = t.selected;
        DIALOG_BG = t.dialogBg;
        PORTRAIT = t.portrait;
        RADIUS_S = Math.round(t.radiusS);
        RADIUS_M = Math.round(t.radiusM);
        RADIUS_L = Math.round(t.radiusL);
        RADIUS_XL = Math.round(t.radiusXl);
        RADIUS_PILL = Math.round(t.radiusPill);
        LINE_W = t.lineWidth;
        FOCUS_W = t.focusWidth;
        Glow.clear();
    }

    /**
     * Sets the faces (ThemeFonts): the UI font's three weights and the display face; a null one is Roboto's. A null
     * display face leaves each big text on its own Roboto weight ({@link #display}).
     */
    static void fonts(Typeface regular, Typeface medium, Typeface bold, Typeface display) {
        REGULAR = regular != null ? regular : ROBOTO_REGULAR;
        MEDIUM = medium != null ? medium : ROBOTO_MEDIUM;
        BOLD = bold != null ? bold : ROBOTO_BOLD;
        themedDisplay = display != null;
        DISPLAY = themedDisplay ? display : REGULAR;
    }

    /**
     * The face for a display text (greeting, clock, page titles): the theme's display font, or, while that is Roboto,
     * the Roboto weight the text had before themes, so Midnight looks as it always did.
     */
    static Typeface display(Typeface roboto) {
        return themedDisplay ? DISPLAY : roboto;
    }

    /** A colour with its alpha replaced: a token's colour at another strength (fades, scrims, outlines). */
    static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** The theme's shadow behind big text on the backdrop (Home's greeting, date, clock); none when transparent. */
    static void textShadow(TextView t) {
        Theme th = theme;
        if ((th.textShadowColor >>> 24) == 0) return;
        // A radius of 0 removes the layer: a hard shadow (blur 0) takes the smallest blur instead.
        t.setShadowLayer(Math.max(0.01f, th.textShadowBlur * scale), 0, th.textShadowOffset * scale,
                th.textShadowColor);
    }

    /** Reads the screen once per activity: the stage is as wide as the screen's long side. */
    static void init(Context ctx, boolean reduced) {
        DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        scale = Math.max(m.widthPixels, m.heightPixels) / (float) STAGE_W;
        float animator = Settings.Global.getFloat(ctx.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        reducedMotion = reduced || animator == 0f;
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
        // A theme without hairlines (lineWidth 0) draws none.
        if (strokeWidth > 0) d.setStroke(Math.max(1, px(strokeWidth)), stroke);
        return d;
    }

    /** A hairline's width in screen px: at least 1 while the theme has hairlines, 0 without. */
    static int linePx() {
        return LINE_W > 0 ? Math.max(1, px(LINE_W)) : 0;
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
