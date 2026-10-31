package com.gathin.tvhome;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Launcher settings' TV settings section (design_preview/src/screens/panes/misc.ts tvPane): a note that the TV's own
 * settings hold picture, sound, network and apps, and buttons into them (SystemSettings, with the vendor fallback):
 * Open TV settings, Picture, Network and Apps, as in the quick-settings dropdown.
 */
final class TvSettingsSection implements SettingsSection {
    private View first;

    @Override
    public String id() {
        return "tv";
    }

    @Override
    public int label() {
        return R.string.settings_tv;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_settings;
    }

    @Override
    public CharSequence value(Context ctx) {
        return "";
    }

    @Override
    public View pane(Context ctx, Host host) {
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.tv_eyebrow)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.settings_tv), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(R.string.tv_sub)), 8);
        first = button(ctx, host, R.string.tv_open, R.drawable.ic_ui_settings, "tv-0", Settings.ACTION_SETTINGS);
        SettingsKit.add(pane, SettingsKit.actions(ctx, first,
                button(ctx, host, R.string.quick_picture, R.drawable.ic_ui_picture, "tv-1", Settings.ACTION_DISPLAY_SETTINGS),
                button(ctx, host, R.string.quick_network, R.drawable.ic_ui_wifi, "tv-2", Settings.ACTION_WIFI_SETTINGS),
                button(ctx, host, R.string.quick_apps, R.drawable.ic_ui_grid, "tv-3",
                        Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)), 36);
        return pane;
    }

    private static View button(Context ctx, Host host, int label, int icon, String key, String action) {
        return SettingsKit.button(ctx, ctx.getString(label), icon, false, "tv", key, "tv-0".equals(key),
                v -> SettingsSections.open(ctx, host, SystemSettings.intent(ctx, action)));
    }

    @Override
    public View firstFocus() {
        return first;
    }
}
