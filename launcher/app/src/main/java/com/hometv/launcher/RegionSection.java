package com.hometv.launcher;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Launcher settings › Region, and First run's Region step (F12; design_preview/src/screens/panes/region.ts; Family
 * install step 2): Sweden or the United States. The region decides which streaming services and stores Launcher
 * settings lists, the currency of prices, and the country TMDB's availability is read for ({@link Region}). Saved at
 * once ({@link RegionChoice#set}); Home's data reads it once as it is made, so the launcher is rebuilt with it at once
 * and comes back to this pane. Each region keeps its own ticks ({@link Region#key}).
 */
final class RegionSection implements SettingsSection {
    static final String ID = "region";
    private static final String GROUP = "seg-region";

    private View first;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int label() {
        return R.string.settings_region;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_pin;
    }

    @Override
    public CharSequence value(Context ctx) {
        return ctx.getString(name(RegionChoice.get(ctx)));
    }

    static int name(Region r) {
        return r == Region.US ? R.string.region_us : R.string.region_se;
    }

    @Override
    public View pane(Context ctx, Host host) {
        Region now = RegionChoice.get(ctx);
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_region)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.region_title), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(R.string.region_sub)), 8);
        Region[] all = Region.values();
        String[] keys = new String[all.length];
        CharSequence[] labels = new CharSequence[all.length];
        for (int i = 0; i < all.length; i++) {
            keys[i] = all[i].code;
            labels[i] = ctx.getString(name(all[i]));
        }
        View control = SettingsKit.segmented(ctx, GROUP, keys, labels, now.code, false, value -> {
            Region r = Region.parse(value);
            if (r == null || r == RegionChoice.get(ctx)) return;
            RegionChoice.set(ctx, r);
            // The services, stores and catalogue are made for one region: rebuilt with the new one, back on this pane.
            host.themeChanged();
        });
        first = SettingsKit.selectedOption(control);
        SettingsKit.add(pane, control, 26);
        SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(now == Region.US ? R.string.region_note_us
                : R.string.region_note_se)), 22);
        SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.region_note_ticks)), 12);
        return pane;
    }

    @Override
    public View firstFocus() {
        return first;
    }
}
