package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The glass dropdown's panel (design_preview/src/screens/dropdown.ts and the .menu styles): rows with an icon, a
 * label and an optional status with a green dot, dividers between groups, and an optional header. Built here so the
 * dropdowns on Home and QuickSettingsActivity, which the remote's Settings key opens over any app, look the same.
 */
final class MenuPanel {
    private MenuPanel() {}

    static final String GROUP = "menu";

    static final class Row {
        final CharSequence label;
        final int icon;
        final Runnable action;
        CharSequence status;
        boolean good;
        boolean divider;

        Row(CharSequence label, int icon, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.action = action;
        }

        /** The right-hand note; good draws it with the green dot ("Connected"). */
        Row status(CharSequence text, boolean isGood) {
            status = text;
            good = isGood;
            return this;
        }

        /** A line between groups, drawn above this row. */
        Row divider() {
            divider = true;
            return this;
        }
    }

    interface OnRow {
        void picked(Row row);
    }

    /**
     * A 1 px line between groups. A plain View asks for all the width it is offered, which would stretch a
     * wrap-content panel across the screen; this one takes none until the panel has settled its width.
     */
    static View hairline(Context ctx) {
        View line = new View(ctx) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec) : 0;
                setMeasuredDimension(w, Math.max(1, Ui.px(1)));
            }
        };
        line.setBackgroundColor(Ui.LINE_STRONG);
        return line;
    }

    static LinearLayout build(Context ctx, View header, List<Row> rows, OnRow onRow) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.px(10);
        panel.setPadding(pad, pad, pad, pad);
        panel.setMinimumWidth(Ui.px(420));
        panel.setBackground(Ui.round(Ui.MENU_BG, 28, Ui.LINE_STRONG, 1));
        panel.setElevation(Ui.px(30));
        if (header != null) panel.addView(header);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.divider && panel.getChildCount() > 0) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Ui.px(1)));
                lp.setMargins(Ui.px(22), Ui.px(8), Ui.px(22), Ui.px(8));
                panel.addView(hairline(ctx), lp);
            }
            panel.addView(row(ctx, r, i, onRow), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.px(72)));
        }
        return panel;
    }

    private static View row(Context ctx, Row r, int index, OnRow onRow) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        row.setBackground(Ui.states(Ui.round(0, 18), null, Ui.round(Ui.ACTIVE, 18)));
        if (r.icon != 0) {
            ImageView icon = new ImageView(ctx);
            icon.setImageResource(r.icon);
            icon.setImageTintList(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
            icon.setDuplicateParentStateEnabled(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28));
            lp.rightMargin = Ui.px(18);
            row.addView(icon, lp);
        }
        TextView label = Ui.line(ctx, 25, Ui.TEXT_1, Ui.REGULAR);
        label.setText(r.label);
        label.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        label.setDuplicateParentStateEnabled(true);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (r.status != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.px(24);
            row.addView(status(ctx, r.status, r.good ? Ui.GOOD : 0, Ui.TEXT_3, Ui.ON_ACCENT), lp);
        }
        Focus.make(row, GROUP, "m-" + index, index == 0, v -> onRow.picked(r));
        row.setContentDescription(r.status == null ? r.label : r.label + ", " + r.status);
        return row;
    }

    /** A status line: an optional coloured dot, then the text, both following the row's focus. */
    static View status(Context ctx, CharSequence text, int dotColor, int color, int focusedColor) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setDuplicateParentStateEnabled(true);
        if (dotColor != 0) {
            View dot = new View(ctx);
            dot.setBackground(Ui.oval(dotColor));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(10), Ui.px(10));
            lp.rightMargin = Ui.px(8);
            box.addView(dot, lp);
        }
        TextView t = Ui.line(ctx, Ui.T_MIN, color, Ui.REGULAR);
        t.setText(text);
        t.setTextColor(Ui.colors(color, color, focusedColor));
        t.setDuplicateParentStateEnabled(true);
        box.addView(t);
        return box;
    }

    /** The Wi-Fi dropdown's header: the network, then its state with a dot in the state's colour. */
    static View header(Context ctx, int icon, int color, CharSequence title, CharSequence sub) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(20));
        ImageView i = Ui.icon(ctx, icon, 34, color);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34));
        ilp.rightMargin = Ui.px(18);
        top.addView(i, ilp);
        LinearLayout lines = new LinearLayout(ctx);
        lines.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.line(ctx, 28, Ui.TEXT_1, Ui.MEDIUM);
        t.setText(title);
        // Wrap, not the default match-parent: a wrapping column sized itself to the status line and cut the title.
        lines.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        View s = status(ctx, sub, color, Ui.TEXT_2, Ui.TEXT_2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.px(6);
        lines.addView(s, slp);
        top.addView(lines);
        box.addView(top);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Ui.px(1)));
        llp.bottomMargin = Ui.px(8);
        box.addView(hairline(ctx), llp);
        return box;
    }

    /** Hangs the panel at (top, right) in a full-screen frame and lets it grow inward from the right edge. */
    static FrameLayout.LayoutParams hang(int top, int right) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = top;
        lp.rightMargin = right;
        return lp;
    }

    /** The drop-in: a short fade and grow from the top right corner (200 ms). */
    static void dropIn(View panel) {
        if (Ui.reducedMotion()) return;
        panel.setAlpha(0f);
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.setTranslationY(-Ui.px(8));
        panel.post(() -> {
            panel.setPivotX(panel.getWidth());
            panel.setPivotY(0);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(200)
                    .setInterpolator(Ui.EASE).start();
        });
    }
}
