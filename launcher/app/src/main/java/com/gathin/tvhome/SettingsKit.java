package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The controls and text of Launcher settings' panes (design_preview/src/ui/widgets.ts segmented, and the
 * {@code .seg}, {@code .lrow}, {@code .toggle}, {@code .cbox} and {@code .pane-*} rules in src/styles/screens.css),
 * shared by every SettingsSection so they look and move alike. Sizes are design px; colours come from Ui, so a theme
 * restyles them.
 *
 * <ul>
 * <li>{@link #segmented}: two to four options in a pill. Moving only highlights an option (the accent fill); OK
 * selects it (the accent ring) and calls back. Entering the control lands on the selected option.</li>
 * <li>{@link #toggleRow}: a row with a title, an optional line under it and a switch; OK flips it.</li>
 * <li>{@link #checkRow}: a row with a check box, a title, a line under it and a value on the right; OK calls back
 * and the caller ticks with {@link #setChecked} (one or many, as the pane decides).</li>
 * <li>{@link #listRow}: a row with an icon, a title, a line under it and a value; OK calls back.</li>
 * <li>{@link #column}, {@link #eyebrow}, {@link #title}, {@link #sub}, {@link #note}, {@link #divider},
 * {@link #actions} and {@link #add}: a pane's furniture, top to bottom.</li>
 * </ul>
 */
final class SettingsKit {
    private SettingsKit() {}

    /** The width a pane's content gets: the pane (1194) less its padding (52 on each side). */
    static final float PANE_W = 1090;

    interface OnPick {
        void picked(String value);
    }

    interface OnToggle {
        void toggled(boolean on);
    }

    // ---- Segmented control ----------------------------------------------------------------------------------------

    /**
     * {@code .seg}: the options in a pill on the surface (6 px inside, 6 px apart). An option is 54 px tall, 22 px
     * text, 26 px each side; small is 46 px, 19 px text, 18 px. The selected one has the accent-soft fill, an accent
     * ring and accent text; the focused one the accent fill and dark text, scaled 1.05. Each option is focusable in
     * {@code group} with the key {@code group + "-" + value}, and the selected one is the group's default.
     */
    static LinearLayout segmented(Context ctx, String group, String[] values, CharSequence[] labels, String selected,
                                  boolean small, OnPick onPick) {
        LinearLayout seg = Kit.unclip(new LinearLayout(ctx));
        seg.setOrientation(LinearLayout.HORIZONTAL);
        int inset = Ui.px(small ? 4 : 6);
        seg.setPadding(inset, inset, inset, inset);
        seg.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, Ui.LINE_W));
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            TextView o = Ui.line(ctx, small ? 19 : 22, Ui.TEXT_2, Ui.REGULAR);
            o.setText(labels[i]);
            o.setGravity(Gravity.CENTER);
            int pad = Ui.px(small ? 18 : 26);
            o.setPadding(pad, 0, pad, 0);
            o.setTextColor(Ui.colors(Ui.TEXT_2, Ui.ACCENT, Ui.ON_ACCENT));
            o.setBackground(Ui.states(Ui.round(0, Ui.RADIUS_PILL),
                    Ui.round(Ui.ACCENT_SOFT, Ui.RADIUS_PILL, Ui.ACCENT, Ui.LINE_W),
                    Ui.round(Ui.ACCENT, Ui.RADIUS_PILL, Ui.ACCENT, Ui.LINE_W)));
            o.setTag(R.id.settings_seg_value, value);
            o.setActivated(value.equals(selected));
            Kit.reportsSelected(o);
            o.setOnFocusChangeListener((v, focused) -> face((TextView) v));
            face(o);
            Focus.make(o, group, group + "-" + value, value.equals(selected), v -> {
                if (v.isActivated()) return;
                select(seg, value);
                onPick.picked(value);
            });
            Ui.scaleOnFocus(o, 1.05f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.px(small ? 46 : 54));
            if (i > 0) lp.leftMargin = inset;
            seg.addView(o, lp);
        }
        return seg;
    }

    /** Semibold while selected or focused (.seg-opt.is-selected, .is-focused). */
    private static void face(TextView o) {
        o.setTypeface(o.isActivated() || o.isFocused() ? Ui.MEDIUM : Ui.REGULAR);
    }

    /** Moves the ring to value's option, without calling back; also the group's default from now on. */
    static void select(View seg, String value) {
        ViewGroup g = (ViewGroup) seg;
        for (int i = 0; i < g.getChildCount(); i++) {
            View o = g.getChildAt(i);
            boolean on = value.equals(o.getTag(R.id.settings_seg_value));
            o.setActivated(on);
            o.setTag(R.id.ui_focus_default, on ? Boolean.TRUE : null);
            face((TextView) o);
        }
    }

    /** The selected option: where → or OK enters a pane whose first control this is. */
    static View selectedOption(View seg) {
        ViewGroup g = (ViewGroup) seg;
        for (int i = 0; i < g.getChildCount(); i++) if (g.getChildAt(i).isActivated()) return g.getChildAt(i);
        return g.getChildCount() > 0 ? g.getChildAt(0) : null;
    }

    /** The selected option's value, or null. */
    static String selected(View seg) {
        View o = selectedOption(seg);
        return o == null || !o.isActivated() ? null : (String) o.getTag(R.id.settings_seg_value);
    }

    // ---- Rows -----------------------------------------------------------------------------------------------------

    /**
     * {@code .lrow} with a {@code .toggle}: 84 px or taller, the title (24 px) over an optional line (18 px), and the
     * 64 × 36 switch on the right, green when on. Focused: the accent fill, dark text, scale 1.02. OK flips the
     * switch and calls back with the new state.
     */
    static View toggleRow(Context ctx, CharSequence title, CharSequence sub, boolean on, String group, String key,
                          boolean isDefault, OnToggle onToggle) {
        LinearLayout row = row(ctx, 84, 26);
        addMain(ctx, row, title, sub, Ui.T_BODY, 0);
        Toggle toggle = new Toggle(ctx);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.px(Toggle.W), Ui.px(Toggle.H));
        tlp.leftMargin = Ui.px(22);
        row.addView(toggle, tlp);
        row.setTag(R.id.settings_row_mark, toggle);
        setOn(row, on);
        row.setAccessibilityDelegate(CHECKABLE);
        Focus.make(row, group, key, isDefault, v -> {
            boolean now = !isOn(v);
            setOn(v, now);
            onToggle.toggled(now);
        });
        Ui.scaleOnFocus(row, 1.02f);
        return row;
    }

    static void setOn(View row, boolean on) {
        row.setTag(R.id.settings_row_on, on);
        Object mark = row.getTag(R.id.settings_row_mark);
        if (mark instanceof Toggle) ((Toggle) mark).set(on);
        else if (mark instanceof View) checkLook((View) mark, on);
    }

    static boolean isOn(View row) {
        return Boolean.TRUE.equals(row.getTag(R.id.settings_row_on));
    }

    /**
     * {@code .lrow--svc} with a {@code .cbox}: 66 px or taller, the 34 px check box (an accent fill and a tick when
     * checked), the title (22 px) over an optional line, and an optional value on the right (18 px). OK runs onOk;
     * the caller ticks the row with {@link #setChecked}.
     */
    static View checkRow(Context ctx, CharSequence title, CharSequence sub, CharSequence value, boolean checked,
                         String group, String key, boolean isDefault, View.OnClickListener onOk) {
        LinearLayout row = row(ctx, 66, 20);
        FrameLayout box = new FrameLayout(ctx);
        box.setDuplicateParentStateEnabled(true);
        ImageView tick = new ImageView(ctx);
        tick.setImageResource(R.drawable.ic_ui_check);
        tick.setDuplicateParentStateEnabled(true);
        box.addView(tick, new FrameLayout.LayoutParams(Ui.px(22), Ui.px(22), Gravity.CENTER));
        row.addView(box, new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34)));
        row.setTag(R.id.settings_row_mark, box);
        addMain(ctx, row, title, sub, 22, 16);
        addValue(ctx, row, value, Ui.T_MIN, Ui.TEXT_3);
        setChecked(row, checked);
        row.setAccessibilityDelegate(CHECKABLE);
        Focus.make(row, group, key, isDefault, onOk);
        Ui.scaleOnFocus(row, 1.02f);
        return row;
    }

    static void setChecked(View row, boolean checked) {
        setOn(row, checked);
    }

    static boolean isChecked(View row) {
        return isOn(row);
    }

    /** A check box's look: line-strong outline; checked, the accent fill and a dark tick; inverted while focused. */
    private static void checkLook(View box, boolean checked) {
        float r = 9;
        box.setBackground(Ui.states(
                checked ? Ui.round(Ui.ACCENT, r, Ui.ACCENT, 2) : Ui.round(0, r, Ui.LINE_STRONG, 2), null,
                checked ? Ui.round(Ui.ON_ACCENT, r, Ui.ON_ACCENT, 2) : Ui.round(0, r, Ui.ON_ACCENT, 2)));
        ImageView tick = (ImageView) ((ViewGroup) box).getChildAt(0);
        tick.setImageTintList(Ui.colors(Ui.ON_ACCENT, Ui.ON_ACCENT, Ui.ACCENT));
        tick.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * {@code .lrow}: 84 px or taller, an optional 26 px icon, the title (24 px) over an optional line (18 px), and an
     * optional value on the right (22 px). Focused: the accent fill, dark text, scale 1.02. OK runs onOk.
     */
    static View listRow(Context ctx, int iconRes, CharSequence title, CharSequence sub, CharSequence value,
                        String group, String key, boolean isDefault, View.OnClickListener onOk) {
        LinearLayout row = row(ctx, 84, 26);
        if (iconRes != 0) {
            ImageView icon = new ImageView(ctx);
            icon.setImageResource(iconRes);
            icon.setImageTintList(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.ON_ACCENT));
            icon.setDuplicateParentStateEnabled(true);
            row.addView(icon, new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26)));
        }
        addMain(ctx, row, title, sub, Ui.T_BODY, iconRes != 0 ? 22 : 0);
        addValue(ctx, row, value, 22, Ui.TEXT_2);
        Focus.make(row, group, key, isDefault, onOk);
        Ui.scaleOnFocus(row, 1.02f);
        return row;
    }

    /** Changes a row's title. */
    static void setTitle(View row, CharSequence title) {
        setText(row, R.id.settings_row_title, title);
    }

    /** Changes the line under a row's title; empty hides it. */
    static void setSub(View row, CharSequence sub) {
        setText(row, R.id.settings_row_sub, sub);
    }

    /** Changes a row's value on the right; empty hides it. */
    static void setValue(View row, CharSequence value) {
        setText(row, R.id.settings_row_value, value);
    }

    private static void setText(View row, int tag, CharSequence text) {
        Object v = row.getTag(tag);
        if (!(v instanceof TextView)) return;
        TextView t = (TextView) v;
        t.setText(text);
        if (tag != R.id.settings_row_title) t.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** A row's frame: the surface fill and line, the accent fill while focused. */
    private static LinearLayout row(Context ctx, float minHeight, float padX) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(minHeight));
        row.setPadding(Ui.px(padX), Ui.px(12), Ui.px(padX), Ui.px(12));
        row.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_L, Ui.ACCENT, Ui.LINE_W)));
        return row;
    }

    private static void addMain(Context ctx, LinearLayout row, CharSequence title, CharSequence sub, float size,
                                float left) {
        LinearLayout main = new LinearLayout(ctx);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setDuplicateParentStateEnabled(true);
        TextView t = Ui.text(ctx, size, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        t.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        t.setDuplicateParentStateEnabled(true);
        main.addView(t);
        TextView s = Ui.text(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        s.setTextColor(Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT));
        s.setDuplicateParentStateEnabled(true);
        s.setLineSpacing(0f, 1.3f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.px(4);
        main.addView(s, slp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.px(left);
        row.addView(main, lp);
        row.setTag(R.id.settings_row_title, t);
        row.setTag(R.id.settings_row_sub, s);
        row.setContentDescription(title);
        setSub(row, sub);
    }

    private static void addValue(Context ctx, LinearLayout row, CharSequence value, float size, int color) {
        TextView v = Ui.line(ctx, size, color, Ui.REGULAR);
        v.setTextColor(Ui.colors(color, color, Ui.ON_ACCENT));
        v.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(16);
        row.addView(v, lp);
        row.setTag(R.id.settings_row_value, v);
        setValue(row, value);
    }

    /** Toggle and check rows keep their state in a tag, which accessibility services are told as checked. */
    private static final View.AccessibilityDelegate CHECKABLE = new View.AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setCheckable(true);
            info.setChecked(isOn(host));
        }
    };

    /**
     * {@code .toggle}: a 64 × 36 track with a 28 px knob 4 px in. Off: a faint track, a light knob; on: the green
     * track and a dark knob on the right. In a focused row: off, a darker track; on, the dark track and an accent knob.
     */
    private static final class Toggle extends View {
        static final float W = 64, H = 36;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private boolean on;
        /** 0 is the knob on the left, 1 on the right. */
        private float at;
        private ValueAnimator slide;

        Toggle(Context ctx) {
            super(ctx);
            setDuplicateParentStateEnabled(true);
        }

        void set(boolean value) {
            if (slide != null) slide.cancel();
            boolean animate = isLaidOut() && value != on && Ui.ms(Ui.FAST_MS) > 0;
            on = value;
            float to = on ? 1f : 0f;
            if (!animate) {
                at = to;
                invalidate();
                return;
            }
            slide = ValueAnimator.ofFloat(at, to);
            slide.setDuration(Ui.ms(Ui.FAST_MS));
            slide.setInterpolator(Ui.EASE);
            slide.addUpdateListener(a -> {
                at = (Float) a.getAnimatedValue();
                invalidate();
            });
            slide.start();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        private boolean focused() {
            for (int s : getDrawableState()) if (s == android.R.attr.state_focused) return true;
            return false;
        }

        @Override
        protected void onDraw(Canvas c) {
            boolean f = focused();
            int trackColor = f ? (on ? Ui.ON_ACCENT : withAlpha(Ui.ON_ACCENT, 0x40)) : (on ? Ui.GOOD : Ui.SURFACE_3);
            int knobColor = on ? (f ? Ui.ACCENT : Ui.BG_RAISED) : Ui.TEXT_2;
            float h = getHeight();
            track.set(0, 0, getWidth(), h);
            paint.setColor(trackColor);
            c.drawRoundRect(track, h / 2, h / 2, paint);
            float inset = h * 4 / H;
            float r = h * 14 / H;
            float x = inset + r + at * (getWidth() - 2 * inset - 2 * r);
            paint.setColor(knobColor);
            c.drawCircle(x, h / 2, r, paint);
        }

        private static int withAlpha(int color, int alpha) {
            return (color & 0x00FFFFFF) | (alpha << 24);
        }
    }

    // ---- A pane's furniture ---------------------------------------------------------------------------------------

    /** A pane's content: a column that lets its items' focus scale-up draw past it. */
    static LinearLayout column(Context ctx) {
        LinearLayout c = Kit.unclip(new LinearLayout(ctx));
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    /** Adds v under what parent holds, top design px below it, as wide as it wants. */
    static <T extends View> T add(LinearLayout parent, T v, float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        parent.addView(v, lp);
        return v;
    }

    /** Adds v under what parent holds, top design px below it, across the whole width. */
    static <T extends View> T addWide(LinearLayout parent, T v, float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        parent.addView(v, lp);
        return v;
    }

    /** {@code .eyebrow}: the pane's small capitals over its title (18 px, medium, tertiary). */
    static TextView eyebrow(Context ctx, CharSequence text) {
        TextView t = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        t.setText(text);
        t.setGravity(Gravity.START);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    /** {@code .pane-title}: 40 px; small ({@code .pane-title--small}) is 30 px, for a second part of a pane. */
    static TextView title(Context ctx, CharSequence text, boolean small) {
        TextView t = Ui.text(ctx, small ? Ui.T_H2 : 40, Ui.TEXT_1, Ui.REGULAR);
        t.setText(text);
        t.setLineSpacing(0f, 1.2f);
        return t;
    }

    /** {@code .pane-sub}: the lines under a title (22 px, secondary), at most 1000 px wide. */
    static TextView sub(Context ctx, CharSequence text) {
        TextView t = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        t.setText(text);
        t.setLineSpacing(0f, 1.45f);
        t.setMaxWidth(Ui.px(1000));
        return t;
    }

    /** {@code .pane-note}: a quieter note (20 px, tertiary), e.g. where the data comes from. */
    static TextView note(Context ctx, CharSequence text) {
        TextView t = Ui.text(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        t.setText(text);
        t.setLineSpacing(0f, 1.45f);
        t.setMaxWidth(Ui.px(1000));
        return t;
    }

    /** {@code .pane-good}: a green tick and a line saying something is as it should be. */
    static View good(Context ctx, CharSequence text) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(Ui.icon(ctx, R.drawable.ic_ui_check, 22, Ui.GOOD),
                new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22)));
        TextView t = Ui.line(ctx, Ui.T_SMALL, Ui.GOOD, Ui.REGULAR);
        t.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(8);
        l.addView(t, lp);
        return l;
    }

    /** {@code .pane-divider}: a 1 px line across the pane. */
    static View divider(Context ctx) {
        View d = new View(ctx);
        d.setBackgroundColor(Ui.LINE);
        d.setMinimumHeight(Math.max(1, Ui.px(1)));
        d.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1))));
        return d;
    }

    /** {@code .pane-actions}: buttons side by side, 16 px apart, with room for their focus scale-up. */
    static LinearLayout actions(Context ctx, View... buttons) {
        LinearLayout row = Kit.unclip(new LinearLayout(ctx));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (View b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row.getChildCount() > 0) lp.leftMargin = Ui.px(16);
            row.addView(b, lp);
        }
        return row;
    }

    /** A plain button for a pane (Kit.button, NORMAL or PRIMARY), without a toggle state. */
    static View button(Context ctx, CharSequence label, int iconRes, boolean primary, String group, String key,
                       boolean isDefault, View.OnClickListener onOk) {
        return Kit.button(ctx, label, iconRes, primary ? Kit.PRIMARY : Kit.NORMAL, false, group, key, isDefault, onOk);
    }
}
