package com.gathin.tvhome;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The TV's saved Wi-Fi networks and the switch between them, for the Wi-Fi dropdown (SavedNetworks picks which to
 * offer). Checked on the Android 9 emulator: getConfiguredNetworks() lists the networks saved in the TV's settings
 * with ACCESS_WIFI_STATE alone, and enableNetwork(id, true) with CHANGE_WIFI_STATE starts a connection even to a
 * network another app saved ("Allowing uid ... with insufficient permissions to connect"); that network's settings
 * stay as they were. Anything refused, and every Android from 10, gives an empty list or false, and the dropdown
 * opens Wi-Fi settings as before.
 */
final class WifiNetworks {
    private WifiNetworks() {}

    private static final String TAG = "TvHome.Wifi";

    /** The saved networks, or an empty list where they can't be read (Android 10+, Wi-Fi off, refused). */
    @SuppressWarnings("deprecation")
    static List<SavedNetworks.Net> saved(Context ctx) {
        if (!SavedNetworks.supported(Build.VERSION.SDK_INT)) return Collections.emptyList();
        try {
            WifiManager wm = manager(ctx);
            if (wm == null || !wm.isWifiEnabled()) return Collections.emptyList();
            List<WifiConfiguration> configs = wm.getConfiguredNetworks();
            List<SavedNetworks.Net> out = new ArrayList<>();
            if (configs == null) return out;
            for (WifiConfiguration c : configs) {
                String ssid = NetworkWords.ssid(c.SSID);
                if (ssid != null) out.add(new SavedNetworks.Net(c.networkId, ssid, secured(c)));
            }
            return out;
        } catch (SecurityException e) {
            // A firmware that wants more than the documented permissions.
            Log.w(TAG, "saved networks refused", e);
            return Collections.emptyList();
        } catch (RuntimeException e) {
            Log.w(TAG, "saved networks unreadable", e);
            return Collections.emptyList();
        }
    }

    /** Asks Android to connect to a saved network. False when refused; the TV's Wi-Fi settings can do it then. */
    @SuppressWarnings("deprecation")
    static boolean connect(Context ctx, int networkId) {
        if (!SavedNetworks.supported(Build.VERSION.SDK_INT)) return false;
        try {
            WifiManager wm = manager(ctx);
            boolean ok = wm != null && wm.enableNetwork(networkId, true);
            Log.i(TAG, "enableNetwork(" + networkId + ") = " + ok);
            return ok;
        } catch (SecurityException e) {
            Log.w(TAG, "switch refused", e);
            return false;
        } catch (RuntimeException e) {
            Log.w(TAG, "switch failed", e);
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean secured(WifiConfiguration c) {
        if (c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)
                || c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP)
                || c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) {
            return true;
        }
        return c.wepKeys != null && c.wepKeys[0] != null;
    }

    private static WifiManager manager(Context ctx) {
        return (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }
}
