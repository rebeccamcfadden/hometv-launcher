package com.hometv.launcher;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/**
 * A Launcher settings section whose feature can be switched off in Features (F14, the project lead 2026-11-01): while
 * the feature is off the section stays in the list, reads Off, and its pane says the feature is off, with Turn on (first
 * focus; the project lead, 2026-11-02), which switches it on and goes straight into its setup (Host#turnOn). On, it is the section itself. Read from the saved switches each time, so a
 * change in Features shows here at once (the launcher itself is rebuilt with it as the page closes).
 */
final class FeatureOffSection implements SettingsSection {
    private final SettingsSection inner;
    private final Features.Feature feature;
    private boolean offPane;
    private View open;

    FeatureOffSection(SettingsSection inner, Features.Feature feature) {
        this.inner = inner;
        this.feature = feature;
    }

    /** Whether the feature is off by the switches saved now. */
    boolean off(Context ctx) {
        return !FeaturePrefs.live(ctx).on(feature);
    }

    @Override
    public String id() {
        return inner.id();
    }

    @Override
    public int label() {
        return inner.label();
    }

    @Override
    public int icon() {
        return inner.icon();
    }

    @Override
    public CharSequence value(Context ctx) {
        return off(ctx) ? ctx.getString(R.string.feature_off_value) : inner.value(ctx);
    }

    @Override
    public View pane(Context ctx, Host host) {
        Features now = FeaturePrefs.live(ctx);
        offPane = !now.on(feature);
        if (!offPane) return inner.pane(ctx, host);
        String name = ctx.getString(FeaturesSection.label(feature));
        Features.Feature needs = now.blockedBy(feature);
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(inner.label())));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.feature_off_title, name), false), 10);
        String why = needs != null
                ? ctx.getString(R.string.feature_off_needs, name, ctx.getString(FeaturesSection.label(needs)))
                : ctx.getString(R.string.feature_off_sub, name);
        SettingsKit.add(pane, SettingsKit.sub(ctx, why), 8);
        open = SettingsKit.button(ctx, ctx.getString(R.string.feature_turn_on), R.drawable.ic_ui_power, true, null,
                "turn-on", true, v -> host.turnOn(feature));
        SettingsKit.add(pane, open, 22);
        return pane;
    }

    @Override
    public View firstFocus() {
        return offPane ? open : inner.firstFocus();
    }

    @Override
    public boolean onBack() {
        return !offPane && inner.onBack();
    }

    @Override
    public boolean onKey(Screen.Key key, View focused) {
        return !offPane && inner.onKey(key, focused);
    }

    @Override
    public void onClose() {
        inner.onClose();
    }

    @Override
    public boolean onText(char c) {
        return !offPane && inner.onText(c);
    }
}
