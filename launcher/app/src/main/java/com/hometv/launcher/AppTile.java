package com.hometv.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An app on Home's row or in All Apps (design_preview/src/styles/screens.css, .app): the app's banner in a rounded
 * tile, or its icon and name where it has no banner, with the name under the tile. Focused, the tile scales up 5%
 * inside the accent focus ring and the name brightens. While being moved it is lifted, with arrows on its sides.
 * Minimal (F43, Theme › App logos): the app's wordmark (AppMarks) in off-white on the glass tile, and no banner is
 * loaded.
 */
@SuppressLint("ViewConstructor") // built in code only, never inflated
final class AppTile extends LinearLayout {
    /**
     * The room the ring has outside the tile, as the prototype's box-shadow does; the column is that much wider. The
     * ring itself is the theme's focus width (Ui.FOCUS_W, 4 in Midnight).
     */
    static final int RING = 4;

    final String pkg;
    private final FrameLayout frame;
    private final View arrows;
    private boolean moving;

    /**
     * width and height are the tile's own size in design px, without the ring; the view is 2 × RING wider and has
     * the name under it.
     */
    AppTile(Context ctx, String pkg, CharSequence label, float width, float height, float labelGap) {
        this(ctx, pkg, label, width, height, labelGap, false);
    }

    /** As above; {@code minimal} draws the app's wordmark on the glass tile instead of its banner (F43). */
    AppTile(Context ctx, String pkg, CharSequence label, float width, float height, float labelGap, boolean minimal) {
        super(ctx);
        this.pkg = pkg;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);
        setContentDescription(label);

        frame = new FrameLayout(ctx);
        frame.setPadding(Ui.px(RING), Ui.px(RING), Ui.px(RING), Ui.px(RING));
        frame.setBackground(Ui.states(TileFrame.resting(RING, Ui.RADIUS_L), null,
                TileFrame.focused(RING, Ui.RADIUS_L)));
        frame.setDuplicateParentStateEnabled(true);
        frame.setClipChildren(false);

        FrameLayout tile = new FrameLayout(ctx);
        // Minimal: Figma's glass tile, white at 3% inside a white 9% line; focused, the surface as any tile.
        int rest = minimal ? Ui.alpha(0xFFFFFFFF, 0x08) : Ui.TILE;
        int line = minimal ? Ui.alpha(0xFFFFFFFF, 0x17) : Ui.LINE;
        tile.setBackground(Ui.states(Ui.round(rest, Ui.RADIUS_L, line, Ui.LINE_W), null,
                Ui.round(Ui.SURFACE_2, Ui.RADIUS_L, line, Ui.LINE_W)));
        tile.setDuplicateParentStateEnabled(true);
        tile.setClipToOutline(true);
        int w = Ui.px(width), h = Ui.px(height);
        frame.addView(tile, new FrameLayout.LayoutParams(w, h));

        ImageView banner = new ImageView(ctx);
        banner.setScaleType(ImageView.ScaleType.FIT_XY);
        tile.addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout fallback = new LinearLayout(ctx);
        fallback.setOrientation(VERTICAL);
        fallback.setGravity(Gravity.CENTER);
        fallback.setVisibility(GONE);
        int iconSize = Ui.px(height * 0.42f);
        ImageView icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fallback.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView name = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_1, Ui.MEDIUM);
        name.setText(label);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.px(10);
        nlp.leftMargin = nlp.rightMargin = Ui.px(12);
        fallback.addView(name, nlp);
        tile.addView(fallback, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        arrows = arrows(ctx);
        arrows.setVisibility(GONE);
        frame.addView(arrows, new FrameLayout.LayoutParams(w, h));

        addView(frame, new LayoutParams(w + 2 * Ui.px(RING), h + 2 * Ui.px(RING)));

        TextView under = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
        under.setText(label);
        under.setGravity(Gravity.CENTER);
        under.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.TEXT_1));
        under.setDuplicateParentStateEnabled(true);
        LayoutParams ulp = new LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = Ui.px(labelGap - RING);
        addView(under, ulp);

        setOnFocusChangeListener((v, focused) -> {
            float s = Ui.focusScale(focused || moving, 1.05f);
            frame.animate().scaleX(s).scaleY(s).setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
        });

        if (minimal) {
            tile.addView(wordmark(ctx, AppMarks.of(pkg, label), height), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        AppArt.load(ctx, pkg, w, h, iconSize, (b, i) -> {
            if (b != null) {
                banner.setImageBitmap(b);
                fallback.setVisibility(GONE);
            } else {
                banner.setImageDrawable(null);
                icon.setImageBitmap(i);
                fallback.setVisibility(VISIBLE);
            }
        });
    }

    /**
     * The app's wordmark, centred, one line, shrinking to fit a long name and then ending in "…" (Figma's #f4f5f7 is
     * TEXT_1). Sized for Home's 136 px tile, and in proportion on smaller ones (Home row apps' 84 px).
     */
    private static TextView wordmark(Context ctx, AppMarks.Mark m, float tileH) {
        int face = AppMarks.face(m.weight);
        float size = AppMarks.scaled(m.size, tileH);
        TextView t = Ui.text(ctx, size, Ui.TEXT_1, face >= 700 ? Ui.BOLD : face >= 500 ? Ui.MEDIUM : Ui.REGULAR);
        // maxLines, not singleLine: a single-line TextView scrolls sideways and never auto-sizes.
        t.setMaxLines(1);
        t.setText(m.text);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.TEXT_1));
        t.setDuplicateParentStateEnabled(true);
        int pad = Ui.px(14);
        t.setPadding(pad, 0, pad, 0);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int min = Math.min(Ui.px(size), Ui.px(AppMarks.scaled(AppMarks.MIN_SIZE, tileH)));
        t.setAutoSizeTextTypeUniformWithConfiguration(min, Ui.px(size), 1, android.util.TypedValue.COMPLEX_UNIT_PX);
        return t;
    }

    /** Picked up with Move: lifted 14 px with arrows on its sides, until it is put down. */
    void setMoving(boolean on) {
        moving = on;
        arrows.setVisibility(on ? VISIBLE : GONE);
        float s = Ui.focusScale(on || isFocused(), 1.05f);
        // Motion Reduced drops the lift too: the arrows say it is picked up.
        frame.animate().translationY(on && !Ui.reducedMotion() ? -Ui.px(14) : 0f).scaleX(s).scaleY(s)
                .setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
    }

    private static View arrows(Context ctx) {
        FrameLayout box = new FrameLayout(ctx);
        box.setBackground(Ui.round(Ui.alpha(Ui.BG, 0x66), Ui.RADIUS_L));
        int[] icons = {R.drawable.ic_ui_chev_left, R.drawable.ic_ui_chev_right};
        int[] gravity = {Gravity.START | Gravity.CENTER_VERTICAL, Gravity.END | Gravity.CENTER_VERTICAL};
        for (int i = 0; i < 2; i++) {
            ImageView a = Ui.icon(ctx, icons[i], 26, Ui.ON_ACCENT);
            int pad = Ui.px(5);
            a.setPadding(pad, pad, pad, pad);
            GradientDrawable dot = Ui.oval(Ui.ACCENT);
            a.setBackground(dot);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(36), Ui.px(36), gravity[i]);
            lp.leftMargin = lp.rightMargin = Ui.px(10);
            box.addView(a, lp);
        }
        return box;
    }
}
