package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Launcher settings' Default Home section (design_preview/src/screens/panes/misc.ts homePane): whether this launcher
 * is the TV's Home, and Choose Home app, which opens Android's Home app setting, where the household can hand Home
 * back to the TV's own launcher or return to this one.
 */
final class DefaultHomeSection implements SettingsSection {
    private View first;

    @Override
    public String id() {
        return "home";
    }

    @Override
    public int label() {
        return R.string.settings_home;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_home;
    }

    @Override
    public CharSequence value(Context ctx) {
        return ctx.getString(isHome(ctx) ? R.string.on : R.string.off);
    }

    @Override
    public View pane(Context ctx, Host host) {
        boolean home = isHome(ctx);
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_home)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(home ? R.string.home_is : R.string.home_is_not),
                false), 10);
        if (home) SettingsKit.add(pane, SettingsKit.good(ctx, ctx.getString(R.string.home_good)), 6);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(R.string.home_settings_help)), 20);
        first = SettingsKit.button(ctx, ctx.getString(R.string.home_choose), R.drawable.ic_ui_home, false, "home",
                "choose-home", true, v -> SettingsSections.open(ctx, host, new Intent(Settings.ACTION_HOME_SETTINGS)));
        SettingsKit.add(pane, SettingsKit.actions(ctx, first), 36);
        return pane;
    }

    @Override
    public View firstFocus() {
        return first;
    }

    /** Whether the remote's Home button opens this launcher: it is the TV's default Home app. */
    private static boolean isHome(Context ctx) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = ctx.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && ctx.getPackageName().equals(r.activityInfo.packageName);
    }
}
