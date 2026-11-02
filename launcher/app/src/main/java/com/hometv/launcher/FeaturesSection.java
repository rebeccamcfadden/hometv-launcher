package com.hometv.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Launcher settings › Features (F14, the project lead 2026-11-01; design_preview/src/screens/panes/features.ts, no
 * Figma frame): a row per feature, laid out as Performance's, each On or Off. A feature that is off never starts; the
 * launcher is rebuilt with the new switches as the page closes (Host#valueChanged moves the change count). A row whose
 * feature needs another that is off (Phone notes without TMDB account) says so instead of offering a control, and ↑/↓
 * pass it by. The switches are read from FeaturePrefs each time, so the page shows what was just chosen.
 *
 * <p>It is also First run's Features step (the project lead, 2026-11-01): every feature starts off, and turning one on
 * there opens its setup at once (Host#featureTurnedOn; FirstRunPage decides what that is).
 */
final class FeaturesSection implements SettingsSection {
    static final String ID = "features";

    private LinearLayout rows;
    private Host host;
    private View first;
    /** The row to focus first instead of the first control: the feature whose setup First run just came back from. */
    private Features.Feature focusOn;

    /** Focus starts on this feature's switch (back from its setup, after a rebuild). */
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
        this.host = host;
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_features)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.settings_features), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(host.inSetup() ? R.string.features_sub_setup : R.string.features_sub)), 8);
        rows = SettingsKit.column(ctx);
        SettingsKit.addWide(pane, rows, 22);
        paintRows(ctx);
        SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.features_note)), 16);
        return pane;
    }

    /** The rows from the saved switches; after a change, built again with focus kept on the same row. */
    private void paintRows(Context ctx) {
        rows.removeAllViews();
        Features now = FeaturePrefs.live(ctx);
        first = null;
        for (Features.Feature f : Features.Feature.values()) {
            Features.Feature blocked = now.blockedBy(f);
            View control;
            if (blocked != null) {
                TextView why = Ui.line(ctx, 20, Ui.TEXT_3, Ui.REGULAR);
                why.setText(ctx.getString(R.string.features_needs, ctx.getString(label(blocked))));
                control = why;
            } else {
                boolean on = now.switchedOn(f);
                control = SettingsKit.segmented(ctx, group(f), new String[]{"on", "off"},
                        new CharSequence[]{ctx.getString(R.string.perf_on), ctx.getString(R.string.perf_off)},
                        on ? "on" : "off", true, value -> changed(ctx, f, "on".equals(value)));
                if (first == null || f == focusOn) first = SettingsKit.selectedOption(control);
            }
            int sub = blocked == null && now.switchedOn(f) ? sub(f) : off(f);
            View row = PerformanceSection.row(ctx, label(f), sub, control);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rows.getChildCount() > 0) lp.topMargin = Ui.px(12);
            rows.addView(row, lp);
        }
    }

    private void changed(Context ctx, Features.Feature f, boolean on) {
        boolean was = FeaturePrefs.live(ctx).switchedOn(f);
        FeaturePrefs.save(ctx, f, on);
        host.valueChanged();
        // Rebuilt after the pick returns: Phone notes' row changes with TMDB account, and every row's line with it.
        rows.post(() -> {
            paintRows(ctx);
            View seg = segOf(f);
            View again = seg == null ? null : SettingsKit.selectedOption(seg);
            if (again != null) host.shell().focus(again);
            if (on && !was) host.featureTurnedOn(f);
        });
    }

    /**
     * ↓ and ↑ go to the next row's control on its selected option, passing a row with no control; → stays within its
     * control, as in Performance.
     */
    @Override
    public boolean onKey(Screen.Key key, View focused) {
        if (focused == null) return false;
        Features.Feature at = featureOf(Focus.group(focused));
        if (at == null) return false;
        if (key == Screen.Key.RIGHT) {
            ViewGroup seg = (ViewGroup) focused.getParent();
            int i = seg.indexOfChild(focused);
            if (i >= 0 && i + 1 < seg.getChildCount()) host.shell().focus(seg.getChildAt(i + 1));
            return true;
        }
        if (key != Screen.Key.DOWN && key != Screen.Key.UP) return false;
        Features.Feature[] all = Features.Feature.values();
        int step = key == Screen.Key.DOWN ? 1 : -1;
        for (int i = at.ordinal() + step; i >= 0 && i < all.length; i += step) {
            View seg = segOf(all[i]);
            if (seg == null) continue;
            host.shell().focus(SettingsKit.selectedOption(seg));
            return true;
        }
        // Past the last row ↓ stays (the note has nothing to focus), except in First run, where it goes on to the footer's
        // Next; past the first ↑ leaves for the page.
        return key == Screen.Key.DOWN && !host.inSetup();
    }


    private View segOf(Features.Feature f) {
        if (rows == null) return null;
        View option = Focus.byKey(rows, group(f) + "-on");
        return option == null ? null : (View) option.getParent();
    }

    private static Features.Feature featureOf(String group) {
        if (group == null || !group.startsWith("feat-")) return null;
        return Features.Feature.parse(group.substring(5));
    }

    private static String group(Features.Feature f) {
        return "feat-" + f.id;
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
            default: return R.string.feature_weather;
        }
    }

    private static int sub(Features.Feature f) {
        switch (f) {
            case TMDB: return R.string.feature_tmdb_sub;
            case NOTES: return R.string.feature_notes_sub;
            case PRICES: return R.string.feature_prices_sub;
            default: return R.string.feature_weather_sub;
        }
    }

    private static int off(Features.Feature f) {
        switch (f) {
            case TMDB: return R.string.feature_tmdb_off;
            case NOTES: return R.string.feature_notes_off;
            case PRICES: return R.string.feature_prices_off;
            default: return R.string.feature_weather_off;
        }
    }
}
