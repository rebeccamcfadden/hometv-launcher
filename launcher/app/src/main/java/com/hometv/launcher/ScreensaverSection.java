package com.hometv.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * F36: Launcher settings › Screensaver (Rebecca chose the prototype's shape, 2026-11-04; design_preview
 * screens/panes/misc.ts screensaverPane). Start after (Off, 5, 15 or 30 min; Off on every TV until the household picks
 * a time), Behind the clock (Glow, Theme picture or Black), Show it now, and a note that the TV's own screensaver can
 * be the same clock (ClockDream). Laid out as Performance: each choice on its own row, ↓ to the next row's selected
 * option, → within its control. Choices save at once and need no rebuild: Home reads them each time it arms the clock.
 */
final class ScreensaverSection implements SettingsSection {
    /** The widest control: Start after's four options. */
    private static final float CONTROL_W = 430;
    static final String ROW_AFTER = "saver-after";
    static final String ROW_BACK = "saver-back";
    static final String ROW_NOW = "saver-now";
    /** The pane's rows top to bottom, by their focus group. */
    static final String[] ROWS = {ROW_AFTER, ROW_BACK, ROW_NOW};

    private View first;
    private View afterSeg, backSeg, now;
    private Host host;

    @Override
    public String id() {
        return "screensaver";
    }

    @Override
    public int label() {
        return R.string.settings_screensaver;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_clock;
    }

    /** "Off", or the time it waits: "15 min". */
    @Override
    public CharSequence value(Context ctx) {
        return afterLabel(ctx, Screensaver.after(ctx));
    }

    private static CharSequence afterLabel(Context ctx, Screensaver.After a) {
        return a == Screensaver.After.OFF ? ctx.getString(R.string.saver_off)
                : ctx.getString(R.string.saver_minutes, a.minutes);
    }

    @Override
    public View pane(Context ctx, Host host) {
        this.host = host;
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_screensaver)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.saver_title), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(R.string.saver_sub)), 8);

        Screensaver.After[] afters = Screensaver.After.values();
        String[] codes = new String[afters.length];
        CharSequence[] labels = new CharSequence[afters.length];
        for (int i = 0; i < afters.length; i++) {
            codes[i] = afters[i].code;
            labels[i] = afterLabel(ctx, afters[i]);
        }
        afterSeg = SettingsKit.segmented(ctx, ROW_AFTER, codes, labels, Screensaver.after(ctx).code, true, value -> {
            Screensaver.saveAfter(ctx, Screensaver.After.parse(value));
            host.valueChanged();
        });
        SettingsKit.addWide(pane, PerformanceSection.row(ctx, R.string.saver_after, R.string.saver_after_sub, afterSeg,
                CONTROL_W), 22);

        Screensaver.Back[] backs = Screensaver.Back.values();
        String[] backCodes = new String[backs.length];
        for (int i = 0; i < backs.length; i++) backCodes[i] = backs[i].code;
        backSeg = SettingsKit.segmented(ctx, ROW_BACK, backCodes, new CharSequence[]{
                        ctx.getString(R.string.saver_back_glow), ctx.getString(R.string.saver_back_picture),
                        ctx.getString(R.string.saver_back_black)},
                Screensaver.back(ctx).code, true, value -> Screensaver.saveBack(ctx, Screensaver.Back.parse(value)));
        SettingsKit.addWide(pane, PerformanceSection.row(ctx, R.string.saver_back, R.string.saver_back_sub, backSeg,
                CONTROL_W), 12);

        now = SettingsKit.button(ctx, ctx.getString(R.string.saver_show_now), R.drawable.ic_ui_clock, false, ROW_NOW,
                ROW_NOW, false, v -> {
                    if (ctx instanceof MainActivity) ((MainActivity) ctx).showScreensaver();
                });
        SettingsKit.add(pane, SettingsKit.actions(ctx, now), 22);
        SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.saver_system_note)), 14);
        first = SettingsKit.selectedOption(afterSeg);
        return pane;
    }

    /** ↓ to the next row (a control's selected option), → within its own control, as in Performance. */
    @Override
    public boolean onKey(Screen.Key key, View focused) {
        if (focused == null || host == null) return false;
        String group = Focus.group(focused);
        if (key == Screen.Key.RIGHT && (ROW_AFTER.equals(group) || ROW_BACK.equals(group))) {
            ViewGroup seg = (ViewGroup) focused.getParent();
            int i = seg.indexOfChild(focused);
            if (i >= 0 && i + 1 < seg.getChildCount()) host.shell().focus(seg.getChildAt(i + 1));
            return true;
        }
        if (key != Screen.Key.DOWN) return false;
        String below = below(group);
        View next = below == null ? null : ROW_BACK.equals(below) ? SettingsKit.selectedOption(backSeg)
                : ROW_NOW.equals(below) ? now : null;
        if (next == null) return false;
        host.shell().focus(next);
        return true;
    }

    /** The row under the one whose focus group is {@code group}, or null for the last row or any other group. */
    static String below(String group) {
        for (int i = 0; i < ROWS.length - 1; i++) if (ROWS[i].equals(group)) return ROWS[i + 1];
        return null;
    }

    @Override
    public View firstFocus() {
        return first;
    }
}
