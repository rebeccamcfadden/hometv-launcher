package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The rows of the Wi-Fi dropdown and of the quick-settings panel the remote's Settings key opens
 * (design_preview/README.md, Top-bar dropdowns). The Inputs rows come from TvInputs.
 */
final class Menus {
    private Menus() {}

    interface Opener {
        void open(Intent intent);
    }

    /** TV system settings, Picture, Network, Apps (to re-enable one) and Launcher settings. */
    static List<MenuPanel.Row> quickSettings(Context ctx, Opener open, Runnable launcherSettings) {
        List<MenuPanel.Row> rows = new ArrayList<>();
        rows.add(new MenuPanel.Row(ctx.getString(R.string.tv_settings), R.drawable.ic_ui_settings,
                () -> open.open(SystemSettings.intent(ctx, Settings.ACTION_SETTINGS))));
        rows.add(new MenuPanel.Row(ctx.getString(R.string.quick_picture), R.drawable.ic_ui_picture,
                () -> open.open(SystemSettings.intent(ctx, Settings.ACTION_DISPLAY_SETTINGS))));
        rows.add(new MenuPanel.Row(ctx.getString(R.string.quick_network), R.drawable.ic_ui_wifi,
                () -> open.open(SystemSettings.intent(ctx, Settings.ACTION_WIFI_SETTINGS))));
        rows.add(new MenuPanel.Row(ctx.getString(R.string.quick_apps), R.drawable.ic_ui_grid,
                () -> open.open(SystemSettings.intent(ctx, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)))
                .status(ctx.getString(R.string.quick_apps_hint), false));
        rows.add(new MenuPanel.Row(ctx.getString(R.string.launcher_settings), R.drawable.ic_ui_home, launcherSettings));
        return rows;
    }

    /** The network and its state, e.g. "Home Wi-Fi" over "Connected · Internet OK · Strong signal". */
    static View networkHeader(Context ctx, NetworkState s) {
        NetworkWords.State state = NetworkWords.state(s.online, s.validated);
        String title;
        if (!s.online) title = ctx.getString(R.string.net_not_connected);
        else if (s.ethernet) title = ctx.getString(R.string.ethernet);
        else title = s.ssid != null ? s.ssid : ctx.getString(R.string.wifi);
        StringBuilder sub = new StringBuilder();
        int color;
        switch (state) {
            case OFFLINE:
                sub.append(ctx.getString(R.string.offline));
                color = Ui.TEXT_3;
                break;
            case NO_INTERNET:
                sub.append(ctx.getString(R.string.net_connected)).append(" \u00B7 ")
                        .append(ctx.getString(R.string.net_no_internet));
                color = Ui.STAR;
                break;
            default:
                sub.append(ctx.getString(R.string.net_connected)).append(" \u00B7 ")
                        .append(ctx.getString(R.string.net_internet_ok));
                color = Ui.GOOD;
                break;
        }
        if (s.online && s.wifi && !s.ethernet) {
            int word = signalWord(NetworkWords.signal(s.level));
            if (word != 0) sub.append(" \u00B7 ").append(ctx.getString(word));
        }
        return MenuPanel.header(ctx, s.icon(), color, title, sub);
    }

    private static int signalWord(NetworkWords.Signal signal) {
        switch (signal) {
            case STRONG:
                return R.string.signal_strong;
            case FAIR:
                return R.string.signal_fair;
            case WEAK:
                return R.string.signal_weak;
            default:
                return 0;
        }
    }

    interface Switcher {
        void connect(SavedNetworks.Net net);
    }

    /**
     * The VPN app when vpn_app names one that is installed (the base's network pill opened it), the other saved
     * networks to switch to where Android lets the launcher (up to Android 9; see SavedNetworks), then Other networks
     * and Wi-Fi settings. Joining a new network waits for its design (BACKLOG.md, Wi-Fi networks), so Other networks
     * opens the TV's Wi-Fi settings.
     */
    static List<MenuPanel.Row> network(Context ctx, NetworkState s, String vpnApp, Opener open,
                                       List<SavedNetworks.Net> saved, Switcher switcher) {
        List<MenuPanel.Row> rows = new ArrayList<>();
        Intent vpn = vpnApp.isEmpty() ? null : launchIntent(ctx, vpnApp);
        if (vpn != null) {
            rows.add(new MenuPanel.Row(label(ctx, vpnApp), R.drawable.ic_ui_lock, () -> open.open(vpn))
                    .status(ctx.getString(s.vpn ? R.string.vpn_on : R.string.vpn_off), s.vpn));
        }
        // On Ethernet the TV ignores Wi-Fi, so switching there would do nothing visible.
        if (!s.ethernet) {
            boolean first = true;
            for (SavedNetworks.Net n : SavedNetworks.others(saved, s.networkId, s.ssid)) {
                MenuPanel.Row row = new MenuPanel.Row(n.ssid, n.secured ? R.drawable.ic_ui_lock : R.drawable.ic_ui_wifi,
                        () -> switcher.connect(n)).status(ctx.getString(R.string.wifi_saved), false);
                if (first && !rows.isEmpty()) row.divider();
                first = false;
                rows.add(row);
            }
        }
        Intent wifi = SystemSettings.intent(ctx, Settings.ACTION_WIFI_SETTINGS);
        MenuPanel.Row others = new MenuPanel.Row(ctx.getString(R.string.other_networks), R.drawable.ic_ui_wifi,
                () -> open.open(new Intent(wifi)));
        if (!rows.isEmpty()) others.divider();
        rows.add(others);
        rows.add(new MenuPanel.Row(ctx.getString(R.string.wifi_settings), R.drawable.ic_ui_settings,
                () -> open.open(new Intent(wifi))));
        return rows;
    }

    private static Intent launchIntent(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private static String label(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return ctx.getString(R.string.vpn);
        }
    }
}
