package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Filters drawer of Movies and TV Shows (design_preview/src/screens/filters.ts, the .drawer and .setting styles
 * in screens.css): a dialog that slides in from the right over a light scrim, one row per filter. ← and → change a
 * row's value (OK steps forward), and every change applies at once to the page behind it; Clear all puts every row
 * back to its first option; Done or Back closes it. With reduced motion it appears without sliding.
 */
final class FiltersDrawer extends Screen {

    private static final String GROUP_ROWS = "settings";
    private static final int WIDTH = 660;
    /** The drawer's shadow to its left (box-shadow -30px 0 80px), drawn as a gradient: no blur on a 1 GB TV. */
    private static final int SHADOW = 110;
    private static final int SCRIM_LIGHT = 0x5905080C;

    private final LibraryFilters filters;
    private final Runnable onChange;
    private final View scrim;
    private final View panel;
    private final List<View> rows = new ArrayList<>();
    private boolean shown;

    FiltersDrawer(Context ctx, Shell shell, LibraryFilters filters, String pageLabel, Runnable onChange) {
        super(ctx.getString(R.string.filters_title), new FrameLayout(ctx), Layer.DIALOG);
        this.filters = filters;
        this.onChange = onChange;
        FrameLayout root = (FrameLayout) view;

        scrim = new View(ctx);
        scrim.setBackgroundColor(SCRIM_LIGHT);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout slide = new FrameLayout(ctx);
        panel = slide;
        View shadow = new View(ctx);
        shadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, 0x40000000}));
        slide.addView(shadow, new FrameLayout.LayoutParams(Ui.px(SHADOW), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START));

        LinearLayout drawer = Kit.unclip(new LinearLayout(ctx));
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(Ui.BG_RAISED);
        drawer.setPadding(Ui.px(56), Ui.px(72), Ui.px(64), Ui.px(Ui.SAFE_Y));
        slide.addView(drawer, new FrameLayout.LayoutParams(Ui.px(WIDTH), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END));
        View border = new View(ctx);
        border.setBackgroundColor(Ui.LINE);
        FrameLayout.LayoutParams edge = new FrameLayout.LayoutParams(Math.max(1, Ui.px(1)),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.TOP);
        edge.leftMargin = Ui.px(SHADOW);
        slide.addView(border, edge);
        root.addView(slide, new FrameLayout.LayoutParams(Ui.px(WIDTH + SHADOW), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END));

        TextView title = Ui.line(ctx, 40, Ui.TEXT_1, Ui.REGULAR);
        title.setText(R.string.filters_title);
        drawer.addView(title, below(0));
        TextView note = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        note.setText(ctx.getString(R.string.filters_note, pageLabel));
        drawer.addView(note, below(8));

        for (int i = 0; i < filters.defs.size(); i++) {
            View row = row(ctx, filters.defs.get(i));
            rows.add(row);
            drawer.addView(row, below(i == 0 ? 36 : 12));
        }

        LinearLayout actions = Kit.unclip(new LinearLayout(ctx));
        actions.setOrientation(LinearLayout.HORIZONTAL);
        View clear = Kit.button(ctx, ctx.getString(R.string.filters_clear), 0, Kit.SMALL, false, null, "f-clear",
                false, v -> {
                    filters.clear();
                    for (View r : rows) update(r);
                    onChange.run();
                });
        actions.addView(clear);
        View done = Kit.button(ctx, ctx.getString(R.string.filters_done), 0, Kit.SMALL | Kit.PRIMARY, false, null,
                "f-done", false, v -> {
                    if (shell.top() == this) shell.pop();
                });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = Ui.px(16);
        actions.addView(done, dlp);
        drawer.addView(actions, below(40));
    }

    /**
     * {@code .setting}: an 84 px row, the label (22 px) on the left, ‹ value › on the right (the value 24 px, centred in
     * 230). Focused: the accent fill, everything on it dark.
     */
    private View row(Context ctx, LibraryFilters.Def d) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(84));
        row.setPadding(Ui.px(26), 0, Ui.px(18), 0);
        row.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, 1), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_L, Ui.ACCENT, 1)));

        TextView label = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        label.setText(d.label);
        label.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.ON_ACCENT));
        label.setDuplicateParentStateEnabled(true);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ColorStateList arrows = Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT);
        row.addView(arrow(ctx, R.drawable.ic_ui_chev_left, arrows));
        TextView value = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(Ui.px(230));
        value.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        value.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = Ui.px(8);
        vlp.rightMargin = Ui.px(8);
        row.addView(value, vlp);
        row.addView(arrow(ctx, R.drawable.ic_ui_chev_right, arrows));

        row.setTag(R.id.kit_label, value);
        row.setTag(R.id.filters_def, d);
        update(row);
        return Focus.make(row, GROUP_ROWS, "f-" + d.id, false, v -> cycle(v, 1));
    }

    private static ImageView arrow(Context ctx, int res, ColorStateList tint) {
        ImageView v = new ImageView(ctx);
        v.setImageResource(res);
        v.setImageTintList(tint);
        v.setDuplicateParentStateEnabled(true);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22)));
        return v;
    }

    private void update(View row) {
        LibraryFilters.Def d = (LibraryFilters.Def) row.getTag(R.id.filters_def);
        String value = filters.valueLabel(d);
        ((TextView) row.getTag(R.id.kit_label)).setText(value);
        row.setContentDescription(d.label + ", " + value);
    }

    private void cycle(View row, int step) {
        LibraryFilters.Def d = (LibraryFilters.Def) row.getTag(R.id.filters_def);
        if (d == null) return;
        filters.cycle(d, step);
        update(row);
        onChange.run();
    }

    @Override
    View defaultFocus() {
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    boolean onKey(Key key, View focused) {
        if ((key == Key.LEFT || key == Key.RIGHT) && focused != null && focused.getTag(R.id.filters_def) != null) {
            cycle(focused, key == Key.LEFT ? -1 : 1);
            return true;
        }
        return false;
    }

    /** Slides in once, the first time it shows (240–320 ms; at once with reduced motion). */
    @Override
    void onShow() {
        if (shown) return;
        shown = true;
        long ms = Ui.ms(Ui.SLOW_MS);
        if (ms == 0) return;
        panel.setTranslationX(Ui.px(WIDTH + SHADOW));
        panel.animate().translationX(0f).setDuration(ms).setInterpolator(Ui.EASE).start();
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(Ui.ms(Ui.FAST_MS)).start();
    }

    private static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }
}
