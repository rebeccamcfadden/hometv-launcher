package com.hometv.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * Launcher settings › Features (F14; F34, Rebecca 2026-11-02; design_preview/src/screens/panes/features.ts, no Figma
 * frame): an overview with no switches. A row per feature, its line saying what it does (or what is off without it) and
 * its state on the right ("On · not set up yet", "Off · needs TMDB account"); OK opens the section that sets it up,
 * where its On/Off is (FeatureOffSection; Phone notes' is its own pane's). A feature that is off never starts; the
 * launcher is rebuilt with the switches as the page closes.
 *
 * <p>It is also First run's Features step, whose setups are tabs under it (Host#openFeature opens one).
 */
final class FeaturesSection implements SettingsSection {
    static final String ID = "features";
    private static final String GROUP = "feat-links";

    private LinearLayout rows;
    private View first;
    /** The row to focus first: the feature whose setup First run just came back from. */
    private Features.Feature focusOn;

    /** Focus starts on this feature's row (back from its setup, after a rebuild). */
    void focusOn(Features.Feature f) {
        focusOn = f;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int label() {
        return R.string.settings_features;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_list;
    }

    @Override
    public CharSequence value(Context ctx) {
        return ctx.getString(R.string.features_value, FeaturePrefs.live(ctx).onCount(), Features.Feature.values().length);
    }

    @Override
    public View pane(Context ctx, Host host) {
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_features)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.settings_features), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(host.inSetup() ? R.string.features_sub_setup : R.string.features_sub)), 8);
        LinearLayout rows = SettingsKit.column(ctx);
        SettingsKit.addWide(pane, rows, 22);
        Features now = FeaturePrefs.live(ctx);
        first = null;
        View top = null;
        for (Features.Feature f : Features.Feature.values()) {
            boolean runs = now.on(f);
            View row = SettingsKit.listRow(ctx, 0, ctx.getString(label(f)), ctx.getString(runs ? sub(f) : off(f)),
                    stateText(ctx, now, f), GROUP, GROUP + "-" + f.id, top == null, v -> host.openFeature(f));
            if (top == null) top = row;
            if (f == focusOn) first = row;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rows.getChildCount() > 0) lp.topMargin = Ui.px(12);
            rows.addView(row, lp);
        }
        if (first == null) first = top;
        SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.features_note)), 16);
        return pane;
    }

    /** "On · set up", "On · not set up yet", "Off", or "Off · needs TMDB account": the feature as it is now. */
    static String stateText(Context ctx, Features now, Features.Feature f) {
        boolean setUp = !(ctx instanceof MainActivity) || FirstRunPage.setUp((MainActivity) ctx, f);
        switch (FirstRunRules.state(now, f, setUp)) {
            case OFF: return ctx.getString(R.string.feature_state_off);
            case NEEDS: return ctx.getString(R.string.features_needs, ctx.getString(label(f.needs)));
            case NOT_SET_UP: return ctx.getString(R.string.feature_state_not_set_up);
            default: return ctx.getString(R.string.feature_state_set_up);
        }
    }

    @Override
    public View firstFocus() {
        return first;
    }

    static int label(Features.Feature f) {
        switch (f) {
            case TMDB: return R.string.feature_tmdb;
            case NOTES: return R.string.feature_notes;
            case PRICES: return R.string.feature_prices;
            case REMOTE: return R.string.feature_remote;
            default: return R.string.feature_weather;
        }
    }

    static int sub(Features.Feature f) {
        switch (f) {
            case TMDB: return R.string.feature_tmdb_sub;
            case NOTES: return R.string.feature_notes_sub;
            case PRICES: return R.string.feature_prices_sub;
            case REMOTE: return R.string.feature_remote_sub;
            default: return R.string.feature_weather_sub;
        }
    }

    static int off(Features.Feature f) {
        switch (f) {
            case TMDB: return R.string.feature_tmdb_off;
            case NOTES: return R.string.feature_notes_off;
            case PRICES: return R.string.feature_prices_off;
            case REMOTE: return R.string.feature_remote_off;
            default: return R.string.feature_weather_off;
        }
    }
}
