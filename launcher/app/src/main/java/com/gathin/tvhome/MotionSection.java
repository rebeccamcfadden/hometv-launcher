package com.gathin.tvhome;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher settings' Motion section (design_preview/src/screens/panes/misc.ts motionPane): Full, Reduced or Off, the
 * same choice as the quick-settings dropdown's Motion row (MotionChoice, saved by MotionPrefs). Reduced and Off are
 * Config#reducedMotion for now: pages, menus, Home's scroll and focus switch at once, without moving (Ui.ms); the
 * spec's Reduced keeps the fades (BACKLOG.md). A saved choice wins over the old Animations switch. A TV whose config
 * sets reduced_motion is never Full (MotionChoice.resolve), so the pane offers only Reduced and Off there, as the
 * quick-settings row does, and says why; Android's animations turned off keep motion reduced whatever is chosen here,
 * and the pane says so too. Takes effect when Home is rebuilt, as the page closes.
 */
final class MotionSection implements SettingsSection {
    private Config config;
    /** Whether config was read with no Motion saved, so it holds config.xml's and config.json's own values. */
    private boolean unsaved;
    private View first;

    MotionSection(Config config) {
        this.config = config;
    }

    /**
     * The config a choice resolves against. Config folds a saved Motion into its Animations switch, so once About's
     * Follow theme drops that choice the config is read again, once, to get the file's own value back.
     */
    private MotionChoice choice(Context ctx) {
        if (!unsaved && !ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .contains(MotionChoice.KEY)) {
            config = Config.load(ctx);
            unsaved = true;
        }
        return MotionPrefs.load(ctx, config);
    }

    @Override
    public String id() {
        return "motion";
    }

    @Override
    public int label() {
        return R.string.settings_motion;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_motion;
    }

    @Override
    public CharSequence value(Context ctx) {
        return ctx.getString(systemOff(ctx) ? R.string.motion_reduced : label(choice(ctx)));
    }

    @Override
    public View pane(Context ctx, Host host) {
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_motion)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.settings_motion), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(R.string.motion_sub)), 8);
        MotionChoice now = choice(ctx);
        boolean configReduced = config.reducedMotion;
        List<MotionChoice> offered = new ArrayList<>();
        for (MotionChoice m : MotionChoice.values()) if (!configReduced || m != MotionChoice.FULL) offered.add(m);
        String[] codes = new String[offered.size()];
        CharSequence[] labels = new CharSequence[offered.size()];
        for (int i = 0; i < offered.size(); i++) {
            codes[i] = offered.get(i).code;
            labels[i] = ctx.getString(label(offered.get(i)));
        }
        View seg = SettingsKit.segmented(ctx, "seg-motion", codes, labels, now.code, false,
                value -> {
                    MotionPrefs.save(ctx, MotionChoice.parse(value));
                    host.valueChanged();
                });
        SettingsKit.add(pane, seg, 22);
        first = SettingsKit.selectedOption(seg);
        if (configReduced) SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.motion_forced_config)), 22);
        if (systemOff(ctx)) SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.motion_forced_system)), 22);
        return pane;
    }

    @Override
    public View firstFocus() {
        return first;
    }

    private static int label(MotionChoice m) {
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
