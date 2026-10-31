package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The quick-settings dropdown's panel (screens.css, .menu--settings, and dropdown.ts's rows): MenuPanel's glass rows,
 * 600 px wide on an opaque panel, whose right-hand status can hold a value, position dots and a chevron. A stepping
 * row redraws its status in place, so focus stays on it.
 */
final class QuickPanel {
    private QuickPanel() {}

    static final int WIDTH = 600;
    private static final float STATUS_TEXT = 19;
    private static final int DOT = 7;
    private static final int DOT_GAP = 5;

    interface OnOpen {
        void open(QuickSettings.Row row);
    }

    static LinearLayout build(Context ctx, List<QuickSettings.Row> rows, OnOpen onOpen) {
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.px(10);
        panel.setPadding(pad, pad, pad, pad);
        // .menu--settings is opaque, the other dropdowns' colour at full strength.
        panel.setBackground(Ui.round(Ui.MENU_BG | 0xFF000000, 28, Ui.LINE_STRONG, Ui.LINE_W));
        panel.setElevation(Ui.px(30));
        for (int i = 0; i < rows.size(); i++) {
            QuickSettings.Row r = rows.get(i);
            if (r.divider && panel.getChildCount() > 0) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Ui.px(1)));
                lp.setMargins(Ui.px(22), Ui.px(8), Ui.px(22), Ui.px(8));
                panel.addView(MenuPanel.hairline(ctx), lp);
            }
            panel.addView(row(ctx, r, i, onOpen), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.px(72)));
        }
        return panel;
    }

    private static View row(Context ctx, QuickSettings.Row r, int index, OnOpen onOpen) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        row.setBackground(Ui.states(Ui.round(0, 18), null, Ui.round(Ui.ACTIVE, 18)));
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(r.icon);
        icon.setImageTintList(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        icon.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28));
        ilp.rightMargin = Ui.px(18);
        row.addView(icon, ilp);
        CharSequence label = ctx.getString(r.label);
        TextView name = Ui.line(ctx, 25, Ui.TEXT_1, Ui.REGULAR);
        name.setText(label);
        name.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        name.setDuplicateParentStateEnabled(true);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Status status = r.value != null || r.chevron || r.stepper != null ? new Status(ctx, r) : null;
        if (status != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.px(24);
            row.addView(status.box, lp);
        }
        Runnable describe = () -> row.setContentDescription(status == null || status.text == null ? label
                : label + ", " + status.text.getText());
        describe.run();
        Focus.make(row, MenuPanel.GROUP, "m-" + index, index == 0, v -> {
            if (r.stepper == null) {
                onOpen.open(r);
                return;
            }
            r.stepper.step();
            if (status != null) status.update(ctx, r);
            describe.run();
        });
        return row;
    }

    /** The right-hand side: the value, the position dots of a stepping row, and a chevron for a settings section. */
    private static final class Status {
        final LinearLayout box;
        final TextView text;
        final LinearLayout dots;

        Status(Context ctx, QuickSettings.Row r) {
            box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setDuplicateParentStateEnabled(true);
            if (r.value != null) {
                text = Ui.line(ctx, STATUS_TEXT, Ui.TEXT_2, Ui.REGULAR);
                text.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.ON_ACCENT));
                text.setDuplicateParentStateEnabled(true);
                box.addView(text);
            } else {
                text = null;
            }
            if (r.stepper != null) {
                dots = new LinearLayout(ctx);
                dots.setOrientation(LinearLayout.HORIZONTAL);
                dots.setGravity(Gravity.CENTER_VERTICAL);
                dots.setDuplicateParentStateEnabled(true);
                for (int i = 0; i < r.stepper.count(); i++) {
                    View dot = new View(ctx);
                    dot.setDuplicateParentStateEnabled(true);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(DOT), Ui.px(DOT));
                    if (i > 0) lp.leftMargin = Ui.px(DOT_GAP);
                    dots.addView(dot, lp);
                }
                // The status's 10 px gap plus .dots' own 2 px.
                box.addView(dots, gap(text != null ? 12 : 0));
            } else {
                dots = null;
            }
            if (r.chevron) {
                ImageView chev = new ImageView(ctx);
                chev.setImageResource(R.drawable.ic_ui_chev_right);
                chev.setImageTintList(Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT));
                chev.setDuplicateParentStateEnabled(true);
                LinearLayout.LayoutParams lp = gap(text != null ? 14 : 0);
                lp.width = Ui.px(22);
                lp.height = Ui.px(22);
                box.addView(chev, lp);
            }
            update(ctx, r);
        }

        private static LinearLayout.LayoutParams gap(int left) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.px(left);
            return lp;
        }

        void update(Context ctx, QuickSettings.Row r) {
            if (text != null) text.setText(r.value.value(ctx));
            if (dots == null) return;
            int on = r.stepper.index();
            // .dots i: the text at 22%, and the focused row's dark at 25%.
            for (int i = 0; i < dots.getChildCount(); i++) {
                boolean lit = i == on;
                View dot = dots.getChildAt(i);
                dot.setBackground(Ui.states(Ui.oval(lit ? Ui.ACCENT : Ui.alpha(Ui.TEXT_1, 0x38)), null,
                        Ui.oval(lit ? Ui.ON_ACCENT : Ui.alpha(Ui.ON_ACCENT, 0x40))));
                // A new background would otherwise fade in from its resting look and, set on a focused row, stop there.
                dot.jumpDrawablesToCurrentState();
            }
        }
    }
}
