package com.gathin.tvhome;

import android.content.Context;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher settings › Performance (the project lead's decision, 2026-11-01; design_preview/src/screens/panes/misc.ts
 * performancePane): what the launcher draws and moves, each a segmented choice on its own row. Motion (Full, Reduced
 * or Off, MotionChoice, the quick-settings Motion row's choice too), card shadows (On, Focused card only, Off), the
 * focus glow, the backdrop picture (Photo or the theme's plain colour) and the animated details (the Theme pane's old
 * Moving details). A Light mode button at the top (the project lead, 2026-11-01) sets every choice to its lightest,
 * Motion Off included; each stays a choice of its own afterwards. A TV that starts at the light end (Config#lightDefaults: low-RAM, or config's
 * light_defaults) starts with PerfChoices.LIGHT and Motion Reduced, and the pane says so; a choice saved here wins.
 *
 * <p>Motion keeps its rules: a TV whose config sets reduced_motion is never Full, so Full isn't offered there; with
 * Android's animations off, motion stays reduced whatever is chosen. Everything takes effect when Home is rebuilt, as
 * the page closes (Host#valueChanged moves the change count).
 */
final class PerformanceSection implements SettingsSection {
    private Config config;
    /** Whether config was read with no Motion saved, so it holds config.xml's and config.json's own values. */
    private boolean unsaved;
    private View first;
    private Host host;
    /** The controls' column: the widest one, Card shadows' On / Focused card only / Off. */
    private static final float CONTROL_W = 350;
    /** The pane's controls, which the Light mode button moves to their lightest option. */
    private View motionSeg, shadowSeg, glowSeg, backdropSeg, detailsSeg;

    PerformanceSection(Config config) {
        this.config = config;
    }

    /**
     * The Motion in effect. Config folds a saved Motion into its Animations switch, so once About's Follow theme drops
     * that choice the config is read again, once, to get the file's own value back.
     */
    private MotionChoice motion(Context ctx) {
        if (!unsaved && !ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .contains(MotionChoice.KEY)) {
            config = Config.load(ctx);
            unsaved = true;
        }
        return MotionPrefs.load(ctx, config);
    }

    @Override
    public String id() {
        return "performance";
    }

    @Override
    public int label() {
        return R.string.settings_performance;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_gauge;
    }

    /** "All on", "Light mode" at the lightest, "Lighter" while a light-start TV has nothing saved, else "Custom". */
    @Override
    public CharSequence value(Context ctx) {
        MotionChoice m = motion(ctx);
        // Android's animations off keep motion reduced: Full shows as Reduced, Off stays Off.
        if (m == MotionChoice.FULL && systemOff(ctx)) m = MotionChoice.REDUCED;
        PerfChoices p = PerfPrefs.load(ctx, config);
        if (m == MotionChoice.FULL && p.same(PerfChoices.FULL)) return ctx.getString(R.string.perf_value_all_on);
        if (m == MotionChoice.OFF && p.same(PerfChoices.MINIMUM)) return ctx.getString(R.string.perf_value_light_mode);
        if (config.lightDefaults && !saved(ctx)) return ctx.getString(R.string.perf_value_lighter);
        return ctx.getString(R.string.perf_value_custom);
    }

    /** Whether the household saved any of this page's choices (Motion included). */
    private static boolean saved(Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(MotionChoice.KEY)) return true;
        for (String key : PerfChoices.KEYS) if (prefs.contains(key)) return true;
        return false;
    }

    @Override
    public View pane(Context ctx, Host host) {
        this.host = host;
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_performance)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.settings_performance), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(R.string.perf_sub)), 8);
        // Only while the light start is what the TV shows: once the household chooses, its choices are the page.
        if (config.lightDefaults && !saved(ctx)) {
            SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.perf_light_note)), 14);
        }
        View light = SettingsKit.button(ctx, ctx.getString(R.string.perf_light_mode), R.drawable.ic_ui_gauge, true,
                "perf-light", "perf-light", false, v -> lightMode(ctx, host));
        SettingsKit.add(pane, SettingsKit.actions(ctx, light), 20);
        SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.perf_light_mode_sub)), 10);

        // Motion: Full isn't offered where the TV's config keeps motion reduced.
        MotionChoice now = motion(ctx);
        boolean configReduced = config.reducedMotion;
        List<MotionChoice> offered = new ArrayList<>();
        for (MotionChoice m : MotionChoice.values()) if (!configReduced || m != MotionChoice.FULL) offered.add(m);
        String[] codes = new String[offered.size()];
        CharSequence[] labels = new CharSequence[offered.size()];
        for (int i = 0; i < offered.size(); i++) {
            codes[i] = offered.get(i).code;
            labels[i] = ctx.getString(motionLabel(offered.get(i)));
        }
        View motion = motionSeg = SettingsKit.segmented(ctx, "perf-motion", codes, labels, now.code, true, value -> {
            MotionPrefs.save(ctx, MotionChoice.parse(value));
            host.valueChanged();
        });
        SettingsKit.addWide(pane, row(ctx, R.string.perf_motion, R.string.perf_motion_sub, motion), 22);
        first = light;
        if (configReduced) SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.motion_forced_config)), 8);
        if (systemOff(ctx)) SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.motion_forced_system)), 8);

        PerfChoices p = PerfPrefs.load(ctx, config);
        PerfChoices.Shadows[] shadows = PerfChoices.Shadows.values();
        String[] shadowCodes = new String[shadows.length];
        for (int i = 0; i < shadows.length; i++) shadowCodes[i] = shadows[i].code;
        View shadow = shadowSeg = SettingsKit.segmented(ctx, "perf-shadows", shadowCodes, new CharSequence[]{
                ctx.getString(R.string.perf_on), ctx.getString(R.string.perf_shadows_focus),
                ctx.getString(R.string.perf_off)}, p.shadows.code, true, value -> {
            PerfPrefs.saveShadows(ctx, PerfChoices.Shadows.parse(value));
            host.valueChanged();
        });
        SettingsKit.addWide(pane, row(ctx, R.string.perf_shadows, R.string.perf_shadows_sub, shadow), 12);

        View glow = glowSeg = onOff(ctx, "perf-glow", p.glow, on -> PerfPrefs.saveGlow(ctx, on), host);
        SettingsKit.addWide(pane, row(ctx, R.string.perf_glow, R.string.perf_glow_sub, glow), 12);

        View backdrop = backdropSeg = SettingsKit.segmented(ctx, "perf-backdrop",
                new String[]{PerfChoices.BACKDROP_PHOTO, PerfChoices.BACKDROP_PLAIN},
                new CharSequence[]{ctx.getString(R.string.perf_backdrop_photo),
                        ctx.getString(R.string.perf_backdrop_plain)},
                p.plainBackdrop ? PerfChoices.BACKDROP_PLAIN : PerfChoices.BACKDROP_PHOTO, true, value -> {
                    PerfPrefs.saveBackdrop(ctx, PerfChoices.BACKDROP_PLAIN.equals(value));
                    host.valueChanged();
                });
        SettingsKit.addWide(pane, row(ctx, R.string.perf_backdrop, R.string.perf_backdrop_sub, backdrop), 12);

        View details = detailsSeg = onOff(ctx, "perf-details", p.details, on -> PerfPrefs.saveDetails(ctx, on), host);
        SettingsKit.addWide(pane, row(ctx, R.string.perf_details, R.string.perf_details_sub, details), 12);
        return pane;
    }

    /** Light mode: every choice at its lightest, saved at once, and the controls moved to show it. */
    private void lightMode(Context ctx, Host host) {
        MotionPrefs.save(ctx, MotionChoice.OFF);
        PerfPrefs.saveAll(ctx, PerfChoices.MINIMUM);
        SettingsKit.select(motionSeg, MotionChoice.OFF.code);
        SettingsKit.select(shadowSeg, PerfChoices.Shadows.OFF.code);
        SettingsKit.select(glowSeg, "off");
        SettingsKit.select(backdropSeg, PerfChoices.BACKDROP_PLAIN);
        SettingsKit.select(detailsSeg, "off");
        host.valueChanged();
        host.shell().toast(ctx.getString(R.string.perf_light_mode_done));
    }

    /**
     * ↓ goes to the next row's control, on its selected option, and → stays within its control. The controls have
     * different widths, so the spatial D-pad would skip a narrow one or jump rows. (↑ already goes row by row: the
     * controls start at the same x.)
     */
    @Override
    public boolean onKey(Screen.Key key, View focused) {
        if (focused == null) return false;
        // → stays in its own control: at a narrow control's last option, the page's spatial D-pad would jump to a
        // wider control on the next row.
        if (key == Screen.Key.RIGHT && segOf(Focus.group(focused)) != null) {
            ViewGroup seg = (ViewGroup) focused.getParent();
            int i = seg.indexOfChild(focused);
            if (i >= 0 && i + 1 < seg.getChildCount()) host.shell().focus(seg.getChildAt(i + 1));
            return true;
        }
        if (key != Screen.Key.DOWN) return false;
        String below = below(Focus.group(focused));
        View seg = below == null ? null : segOf(below);
        View next = seg == null ? null : SettingsKit.selectedOption(seg);
        if (next == null) return false;
        host.shell().focus(next);
        return true;
    }

    /** The pane's rows top to bottom, by their focus group. */
    static final String[] ROWS = {"perf-light", "perf-motion", "perf-shadows", "perf-glow", "perf-backdrop",
            "perf-details"};

    /** The row under the one whose focus group is {@code group}, or null for the last row or any other group. */
    static String below(String group) {
        for (int i = 0; i < ROWS.length - 1; i++) if (ROWS[i].equals(group)) return ROWS[i + 1];
        return null;
    }

    private View segOf(String group) {
        if (group == null) return null;
        switch (group) {
            case "perf-motion":
                return motionSeg;
            case "perf-shadows":
                return shadowSeg;
            case "perf-glow":
                return glowSeg;
            case "perf-backdrop":
                return backdropSeg;
            case "perf-details":
                return detailsSeg;
            default:
                return null;
        }
    }

    @Override
    public View firstFocus() {
        return first;
    }

    private interface Save {
        void save(boolean on);
    }

    private static View onOff(Context ctx, String group, boolean on, Save save, Host host) {
        return SettingsKit.segmented(ctx, group, new String[]{"on", "off"},
                new CharSequence[]{ctx.getString(R.string.perf_on), ctx.getString(R.string.perf_off)},
                on ? "on" : "off", true, value -> {
                    save.save("on".equals(value));
                    host.valueChanged();
                });
    }

    /** A choice's row: its name over a line saying what it is, and the segmented control on the right. */
    private static View row(Context ctx, int title, int sub, View control) {
        LinearLayout row = Kit.unclip(new LinearLayout(ctx));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(84));
        row.setPadding(Ui.px(26), Ui.px(12), Ui.px(20), Ui.px(12));
        row.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L));
        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.MEDIUM);
        name.setText(title);
        text.addView(name);
        TextView line = Ui.text(ctx, 18, Ui.TEXT_2, Ui.REGULAR);
        line.setText(sub);
        line.setMaxLines(2);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.px(4);
        text.addView(line, llp);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // Every control starts at the same x, so ↑/↓ go row by row: right-aligned controls of different widths left
        // Motion's further right than Card shadows', and the D-pad skipped it.
        LinearLayout column = Kit.unclip(new LinearLayout(ctx));
        column.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        column.addView(control, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.px(CONTROL_W),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.px(16);
        row.addView(column, clp);
        return row;
    }

    private static int motionLabel(MotionChoice m) {
        switch (m) {
            case REDUCED:
                return R.string.motion_reduced;
            case OFF:
                return R.string.motion_off;
            default:
                return R.string.motion_full;
        }
    }

    private static boolean systemOff(Context ctx) {
        return Settings.Global.getFloat(ctx.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }
}
