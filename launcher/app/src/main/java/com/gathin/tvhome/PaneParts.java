package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The pieces the Account, Streaming services, Stores and Home row apps panes of Launcher settings share
 * (design_preview/src/screens/panes/pane.ts, src/ui/widgets.ts and screens.css, "Shared rows and controls" and
 * "Launcher settings"): the scrolling column, the head lines, group headers, key-value rows, check rows, a segmented
 * control, a progress bar and the confirm dialog. Sizes in design px, colours from {@link Ui}. A stand-in until the
 * page's own kit lands (track D1); each piece is small so it can be swapped for that kit's.
 */
final class PaneParts {
    private PaneParts() {}

    private static final int[] FOCUSED_ON = {android.R.attr.state_focused, android.R.attr.state_activated};
    private static final int[] FOCUSED = {android.R.attr.state_focused};
    private static final int[] ON = {android.R.attr.state_activated};
    private static final int[] ANY = {};

    // ---- Layout -----------------------------------------------------------------------------------------------------

    static LinearLayout column(Context ctx) {
        LinearLayout c = Kit.unclip(new LinearLayout(ctx));
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    static LinearLayout across(Context ctx) {
        LinearLayout r = Kit.unclip(new LinearLayout(ctx));
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    /**
     * A pane's root: a box that scrolls to the focused item, holding the column with room around it for focus rings
     * and scale-ups (screens.css .pane-scroll). Inside a page that scrolls the pane itself it just grows.
     */
    static ViewGroup scroller(Context ctx, LinearLayout column) {
        ViewGroup box = ScrollBox.vertical(ctx, 40);
        int room = Ui.px(Kit.RING_ROOM);
        column.setPadding(room, room, room, room);
        box.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** Wrap-sized, {@code top} design px below the view before it. */
    static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }

    /** Full width, {@code top} design px below the view before it. */
    static LinearLayout.LayoutParams wide(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }

    /** {@code gap} design px after the view before it in a row. */
    static LinearLayout.LayoutParams after(float gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.leftMargin = Ui.px(gap);
        return lp;
    }

    /** Takes the row's spare width. */
    static LinearLayout.LayoutParams grow(float gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.leftMargin = Ui.px(gap);
        return lp;
    }

    // ---- Text -------------------------------------------------------------------------------------------------------

    /** .eyebrow: 18 px, medium, uppercase, spaced, text-3. */
    static TextView eyebrow(Context ctx, CharSequence s) {
        TextView t = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        t.setText(s);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    /** .pane-title: 40 px (36 beside the QR code). */
    static TextView title(Context ctx, CharSequence s, float size) {
        TextView t = Ui.text(ctx, size, Ui.TEXT_1, Ui.REGULAR);
        t.setText(s);
        t.setLineSpacing(0f, 1.2f);
        return t;
    }

    /** .pane-sub: 22 px, text-2, 1.45 line height, at most 1000 px wide. */
    static TextView sub(Context ctx, CharSequence s) {
        TextView t = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        t.setText(s);
        t.setLineSpacing(0f, 1.45f);
        t.setMaxWidth(Ui.px(1000));
        return t;
    }

    /** .pane-note: 20 px, text-3; {@code tiny} is 18 px and at most 500 px wide. */
    static TextView note(Context ctx, CharSequence s, boolean tiny) {
        TextView t = Ui.text(ctx, tiny ? Ui.T_MIN : Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        t.setText(s);
        t.setLineSpacing(0f, 1.45f);
        if (tiny) t.setMaxWidth(Ui.px(500));
        return t;
    }

    /** .grp-h: a group's header, 19 px uppercase. */
    static TextView groupHeader(Context ctx, CharSequence s) {
        TextView t = Ui.line(ctx, 19, Ui.TEXT_3, Ui.MEDIUM);
        t.setText(s);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        t.setPadding(Ui.px(4), 0, 0, 0);
        return t;
    }

    /** .hint: "Installed" in green, "No app", "Free". */
    static TextView hint(Context ctx, CharSequence s, boolean good) {
        TextView t = Ui.line(ctx, Ui.T_MIN, good ? Ui.GOOD : Ui.TEXT_3, Ui.REGULAR);
        t.setText(s);
        return t;
    }

    /**
     * .pane-headrow: the eyebrow and title on the left, a count on the right ("8 of 11 ticked"), then the sub line.
     * The count's TextView is returned through {@code count}[0], to change in place.
     */
    static void headCount(Context ctx, LinearLayout into, CharSequence eyebrow, CharSequence title, CharSequence sub,
                          TextView[] count) {
        LinearLayout row = across(ctx);
        row.setGravity(Gravity.BOTTOM);
        LinearLayout left = column(ctx);
        left.addView(eyebrow(ctx, eyebrow));
        left.addView(title(ctx, title, 40), below(10));
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView c = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        row.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        count[0] = c;
        into.addView(row, wide(0));
        into.addView(sub(ctx, sub), below(8));
    }

    /** .kv: a key on the left, its value in medium white on the right, over a hairline. */
    static View keyValue(Context ctx, CharSequence key, CharSequence value) {
        LinearLayout box = column(ctx);
        LinearLayout row = across(ctx);
        row.setPadding(0, Ui.px(18), 0, Ui.px(18));
        TextView k = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        k.setText(key);
        row.addView(k, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Ui.line(ctx, 22, Ui.TEXT_1, Ui.MEDIUM);
        v.setText(value);
        row.addView(v, after(18));
        box.addView(row, wide(0));
        View line = new View(ctx);
        line.setBackgroundColor(Ui.LINE);
        box.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1))));
        return box;
    }

    /** .pane-actions: buttons in a row, 16 px apart. */
    static LinearLayout actions(Context ctx, List<View> buttons) {
        LinearLayout row = across(ctx);
        for (View b : buttons) row.addView(b, row.getChildCount() == 0 ? after(0) : after(16));
        return row;
    }

    // ---- Shapes -----------------------------------------------------------------------------------------------------

    /** A filled circle of {@code size} design px with an icon in it (benefits, steps, the big state icons). */
    static FrameLayout disc(Context ctx, float size, int fill, int iconRes, float iconSize, int iconColor) {
        FrameLayout d = new FrameLayout(ctx);
        d.setBackground(Ui.oval(fill));
        if (iconRes != 0) {
            ImageView i = Ui.icon(ctx, iconRes, iconSize, iconColor);
            d.addView(i, new FrameLayout.LayoutParams(Ui.px(iconSize), Ui.px(iconSize), Gravity.CENTER));
        }
        d.setMinimumWidth(Ui.px(size));
        d.setMinimumHeight(Ui.px(size));
        return d;
    }

    /** A rounded box with a dashed border (screens.css .qr-hold, .stile.is-more). */
    static GradientDrawable dashed(int fill, float radius, int stroke) {
        GradientDrawable d = Ui.round(fill, radius);
        d.setStroke(Math.max(1, Ui.px(1.5f)), stroke, Ui.px(8), Ui.px(6));
        return d;
    }

    /** .bar: a 6 px track and its fill, set with {@link #setBar}. */
    static FrameLayout bar(Context ctx, int fill) {
        FrameLayout track = new FrameLayout(ctx);
        track.setBackground(Ui.round(Ui.SURFACE_2, 3));
        track.setClipToOutline(true);
        View i = new View(ctx);
        i.setBackground(Ui.round(fill, 3));
        i.setPivotX(0f);
        track.addView(i, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(6)));
        return track;
    }

    static void setBar(FrameLayout bar, float fraction) {
        bar.getChildAt(0).setScaleX(Math.max(0f, Math.min(1f, fraction)));
    }

    /** States for a view that is ticked (activated) and focused, focused first. */
    static StateListDrawable ticked(Drawable focusedOn, Drawable focused, Drawable on, Drawable off) {
        StateListDrawable s = new StateListDrawable();
        s.addState(FOCUSED_ON, focusedOn);
        s.addState(FOCUSED, focused);
        s.addState(ON, on);
        s.addState(ANY, off);
        return s;
    }

    // ---- Controls ---------------------------------------------------------------------------------------------------

    /**
     * .lrow.lrow--svc with a .cbox: a 66 px row with a checkbox, a title and what follows it ({@code extras}, right
     * of the title). Ticked is the row's activated state ({@link #setTicked}). Focused: the accent fill, every label
     * dark, scale 1.02. OK runs {@code onOk}.
     */
    static LinearLayout checkRow(Context ctx, CharSequence title, List<TextView> extras, boolean on, String group,
                                 String key, boolean isDefault, View.OnClickListener onOk) {
        LinearLayout row = across(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(66));
        row.setPadding(Ui.px(20), 0, Ui.px(20), 0);
        row.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_L, Ui.ACCENT, Ui.LINE_W)));
        row.setContentDescription(title);

        FrameLayout box = new FrameLayout(ctx);
        box.setBackground(ticked(Ui.round(Ui.ON_ACCENT, 9), Ui.round(Color.TRANSPARENT, 9, Ui.ON_ACCENT, 2),
                Ui.round(Ui.ACCENT, 9, Ui.ACCENT, 2), Ui.round(Color.TRANSPARENT, 9, Ui.LINE_STRONG, 2)));
        box.setDuplicateParentStateEnabled(true);
        ImageView check = new ImageView(ctx);
        check.setImageResource(R.drawable.ic_ui_check);
        check.setImageTintList(new android.content.res.ColorStateList(new int[][]{FOCUSED_ON, FOCUSED, ON, ANY},
                new int[]{Ui.ACCENT, Color.TRANSPARENT, Ui.ON_ACCENT, Color.TRANSPARENT}));
        check.setDuplicateParentStateEnabled(true);
        box.addView(check, new FrameLayout.LayoutParams(Ui.px(22), Ui.px(22), Gravity.CENTER));
        row.addView(box, new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34)));

        TextView name = Ui.line(ctx, 22, Ui.TEXT_1, Ui.REGULAR);
        name.setText(title);
        name.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        name.setDuplicateParentStateEnabled(true);
        row.addView(name, grow(16));
        for (TextView e : extras) {
            int c = e.getCurrentTextColor();
            e.setTextColor(Ui.colors(c, c, Ui.ON_ACCENT));
            e.setDuplicateParentStateEnabled(true);
            row.addView(e, after(16));
        }
        setTicked(row, on);
        Focus.make(row, group, key, isDefault, onOk);
        Ui.scaleOnFocus(row, 1.02f);
        return row;
    }

    /** A check row's or tile's tick, told to accessibility services too. */
    static void setTicked(View v, boolean on) {
        Kit.setOn(v, on);
    }

    /**
     * .seg: options in a pill; moving along them only highlights, OK selects ({@code onPick} with the option's
     * index), and the selected one has the accent ring. The group's default is the selected option, so entering the
     * row lands on it. {@code small} is .seg--small (Choose lists).
     */
    static LinearLayout segmented(Context ctx, CharSequence[] labels, int selected, boolean small, String group,
                                  boolean isDefault, IntConsumer onPick) {
        LinearLayout seg = across(ctx);
        int pad = Ui.px(small ? 4 : 6);
        seg.setPadding(pad, pad, pad, pad);
        seg.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, Ui.LINE_W));
        View[] options = new View[labels.length];
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            TextView o = Ui.line(ctx, small ? 19 : 22, Ui.TEXT_2, Ui.REGULAR);
            o.setText(labels[i]);
            o.setGravity(Gravity.CENTER);
            o.setPadding(Ui.px(small ? 18 : 26), 0, Ui.px(small ? 18 : 26), 0);
            o.setMinHeight(Ui.px(small ? 46 : 54));
            o.setTextColor(Ui.colors(Ui.TEXT_2, Ui.ACCENT, Ui.ON_ACCENT));
            o.setBackground(Ui.states(Ui.round(Color.TRANSPARENT, Ui.RADIUS_PILL),
                    Ui.round(Ui.ACCENT_SOFT, Ui.RADIUS_PILL, Ui.ACCENT, Ui.LINE_W), Ui.round(Ui.ACCENT, Ui.RADIUS_PILL)));
            Kit.reportsSelected(o);
            options[i] = o;
            Focus.make(o, group, group + "-" + i, isDefault && i == selected, v -> {
                for (int j = 0; j < options.length; j++) select((TextView) options[j], j == index);
                onPick.accept(index);
            });
            Ui.scaleOnFocus(o, 1.05f);
            select(o, i == selected);
            seg.addView(o, i == 0 ? after(0) : after(small ? 4 : 6));
        }
        return seg;
    }

    private static void select(TextView option, boolean on) {
        option.setActivated(on);
        option.setTypeface(on ? Ui.MEDIUM : Ui.REGULAR);
    }

    // ---- Confirm ----------------------------------------------------------------------------------------------------

    /**
     * The confirm dialog (design_preview/src/screens/confirm.ts, screens.css .dialog), as RentDialog draws it: a
     * title, a line, the action first and Cancel. Back or Cancel closes it; the action closes it and runs.
     */
    static Screen confirm(Context ctx, Shell shell, CharSequence title, CharSequence body, CharSequence action,
                          CharSequence cancel, Runnable onConfirm) {
        FrameLayout root = new FrameLayout(ctx);
        View scrim = new View(ctx);
        scrim.setBackgroundColor(Ui.SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout box = column(ctx);
        box.setPadding(Ui.px(56), Ui.px(52), Ui.px(56), Ui.px(52));
        box.setBackground(Ui.round(Ui.BG_RAISED, Ui.RADIUS_XL, Ui.LINE_STRONG, Ui.LINE_W));
        box.setElevation(Ui.px(40));
        TextView t = Ui.text(ctx, 38, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        box.addView(t);
        TextView b = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        b.setText(body);
        b.setLineSpacing(0f, 1.45f);
        box.addView(b, wide(16));
        View yes = Kit.button(ctx, action, 0, Kit.PRIMARY, false, "confirm", "confirm-yes", true, v -> {
            shell.pop();
            onConfirm.run();
        });
        View no = Kit.button(ctx, cancel, 0, Kit.NORMAL, false, "confirm", "confirm-no", false, v -> shell.pop());
        box.addView(actions(ctx, java.util.Arrays.asList(yes, no)), below(40));
        root.addView(box, new FrameLayout.LayoutParams(Ui.px(860), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return new Screen(title.toString(), root, Screen.Layer.DIALOG) {
            @Override
            View defaultFocus() {
                return yes;
            }
        };
    }
}
