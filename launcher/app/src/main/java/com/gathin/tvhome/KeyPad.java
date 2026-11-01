package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.UnaryOperator;

/**
 * The on-screen keyboard of Search (design_preview/src/screens/search.ts, {@code .kbp}): a panel of 6 × 6 letter and
 * digit keys, then Space, ⌫ and Clear, each 56 px on the surface and, focused, the accent fill scaled 1.08. Each key
 * hands the current text to {@code edit} and gets the new text back ({@link SearchQuery}'s rules). Used by Launcher
 * settings' Weather search and by Phone notes' picker; Search draws its own inside its panel.
 */
final class KeyPad {
    private KeyPad() {}

    static final String[] ROWS = {"abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890"};
    static final float KEY = 56, GAP = 8;

    /** What the keys do to the text; the keyboard calls it with the edit to make. */
    interface Editor {
        void edit(UnaryOperator<String> change);
    }

    /**
     * The panel. Keys are focusable in {@code group}, keyed {@code prefix + "a"} … {@code prefix + "clear"}; "a" is
     * the group's default.
     */
    static LinearLayout panel(Context ctx, String group, String prefix, Editor editor) {
        LinearLayout panel = Kit.unclip(new LinearLayout(ctx));
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.px(24), Ui.px(22), Ui.px(24), Ui.px(22));
        panel.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_XL, Ui.LINE, Ui.LINE_W));
        for (String row : ROWS) {
            LinearLayout r = row(ctx, panel);
            for (char ch : row.toCharArray()) {
                key(ctx, r, String.valueOf(ch), group, prefix + ch, KEY, ch == 'a',
                        v -> editor.edit(q -> SearchQuery.type(q, ch)));
            }
        }
        LinearLayout last = row(ctx, panel);
        key(ctx, last, ctx.getString(R.string.search_key_space), group, prefix + "space", 184, false,
                v -> editor.edit(q -> SearchQuery.clean(q + " ")));
        FrameLayout del = new FrameLayout(ctx);
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_ui_backspace);
        icon.setImageTintList(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        icon.setDuplicateParentStateEnabled(true);
        del.addView(icon, new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        del.setContentDescription(ctx.getString(R.string.search_key_delete));
        look(del);
        Focus.make(del, group, prefix + "del", false, v -> editor.edit(SearchQuery::delete));
        last.addView(del, params(last, KEY));
        key(ctx, last, ctx.getString(R.string.search_key_clear), group, prefix + "clear", 120, false,
                v -> editor.edit(q -> ""));
        return panel;
    }

    private static LinearLayout row(Context ctx, LinearLayout panel) {
        LinearLayout r = Kit.unclip(new LinearLayout(ctx));
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = panel.getChildCount() == 0 ? 0 : Ui.px(GAP);
        panel.addView(r, lp);
        return r;
    }

    private static void key(Context ctx, LinearLayout row, String label, String group, String key, float width,
                            boolean isDefault, View.OnClickListener onOk) {
        TextView k = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        k.setOnFocusChangeListener((v, focused) -> k.setTypeface(focused ? Ui.MEDIUM : Ui.REGULAR));
        look(k);
        Focus.make(k, group, key, isDefault, onOk);
        row.addView(k, params(row, width));
    }

    private static void look(View k) {
        k.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_M, Ui.LINE, Ui.LINE_W), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_M, Ui.ACCENT, Ui.LINE_W)));
        Ui.scaleOnFocus(k, 1.08f);
    }

    private static LinearLayout.LayoutParams params(LinearLayout row, float width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(width), Ui.px(KEY));
        lp.leftMargin = row.getChildCount() == 0 ? 0 : Ui.px(GAP);
        return lp;
    }
}
