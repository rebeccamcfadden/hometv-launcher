package com.hometv.launcher;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * A section whose feature can be switched off (F14, the project lead 2026-11-01; F34, Rebecca 2026-11-02): in Launcher
 * settings and as a First run setup tab it never goes away. On, its pane has the feature's own On/Off on top, above the
 * section's own pane; Off switches the feature off there and then, and the pane shows again saying so. Off (or off
 * because what it needs is off), the section reads Off and its pane says the feature is off above that same switch
 * (first focus), whose On switches it on and goes straight into its setup (Host#turnOn). Phone notes has its own
 * On/Off in its pane, which is the feature's switch ({@code ownSwitch}), so it gets no second one. Read from the saved
 * switches each time; the launcher itself is rebuilt with them as the page closes.
 */
final class FeatureOffSection implements SettingsSection {
    private final SettingsSection inner;
    private final Features.Feature feature;
    private final boolean ownSwitch;
    private boolean offPane;
    /** The feature's On/Off, or null when the section's own pane has it. */
    private View seg;
    private View bar;
    /** Turn on, while the feature is switched on but what it needs is off: switches that on (Host#turnOn). */
    private View turnOn;

    FeatureOffSection(SettingsSection inner, Features.Feature feature) {
        this(inner, feature, false);
    }

    FeatureOffSection(SettingsSection inner, Features.Feature feature, boolean ownSwitch) {
        this.inner = inner;
        this.feature = feature;
        this.ownSwitch = ownSwitch;
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
        seg = null;
        bar = null;
        turnOn = null;
        if (!offPane) {
            View own = inner.pane(ctx, host);
            if (ownSwitch) return own;
            LinearLayout pane = SettingsKit.column(ctx);
            bar = bar(ctx, host, true);
            SettingsKit.addWide(pane, bar, 0);
            SettingsKit.addWide(pane, own, 16);
            return pane;
        }
        String name = ctx.getString(FeaturesSection.label(feature));
        Features.Feature needs = now.blockedBy(feature);
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(inner.label())));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.feature_off_title, name), false), 10);
        // Its own switch on, what it needs off (Phone notes without TMDB account): the switch reads On, so Off still
        // works, and Turn on switches on what it needs.
        boolean own = now.switchedOn(feature);
        String needsName = needs == null ? null : ctx.getString(FeaturesSection.label(needs));
        String why = needs == null ? ctx.getString(R.string.feature_off_sub)
                : own ? ctx.getString(R.string.feature_off_blocked, name, needsName)
                : ctx.getString(R.string.feature_off_needs, name, needsName);
        SettingsKit.add(pane, SettingsKit.sub(ctx, why), 8);
        bar = bar(ctx, host, own);
        SettingsKit.addWide(pane, bar, 22);
        if (needs != null && own) {
            turnOn = SettingsKit.button(ctx, ctx.getString(R.string.feature_turn_on_needs, needsName),
                    R.drawable.ic_ui_power, true, null, "turn-on", true, v -> host.turnOn(feature));
            SettingsKit.add(pane, turnOn, 22);
        }
        return pane;
    }

    /**
     * The feature's row: its name, what it does (or what is off without it), and On/Off, inset like the panes' own
     * rows. The pick is handled after it returns, since it rebuilds the pane the option is in.
     */
    private View bar(Context ctx, Host host, boolean on) {
        seg = SettingsKit.segmented(ctx, "feat-" + feature.id, new String[]{"on", "off"},
                new CharSequence[]{ctx.getString(R.string.perf_on), ctx.getString(R.string.perf_off)},
                on ? "on" : "off", true, value -> seg.post(() -> {
                    if ("on".equals(value)) {
                        host.turnOn(feature);
                        return;
                    }
                    FeaturePrefs.save(ctx, feature, false);
                    host.valueChanged();
                    host.featureSwitchedOff(feature);
                }));
        View row = PerformanceSection.row(ctx, FeaturesSection.label(feature),
                on ? FeaturesSection.sub(feature) : FeaturesSection.off(feature), seg);
        FrameLayout inset = Kit.unclip(new FrameLayout(ctx));
        int room = Ui.px(Kit.RING_ROOM);
        inset.setPadding(room, room, room, 0);
        inset.addView(row);
        return inset;
    }

    @Override
    public View firstFocus() {
        if (offPane) return turnOn != null ? turnOn : SettingsKit.selectedOption(seg);
        View own = inner.firstFocus();
        return own != null || seg == null ? own : SettingsKit.selectedOption(seg);
    }

    @Override
    public boolean onBack() {
        return !offPane && inner.onBack();
    }

    @Override
    public boolean onKey(Screen.Key key, View focused) {
        if (offPane || (bar != null && Focus.isInside(bar, focused))) return false;
        return inner.onKey(key, focused);
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