package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * The connection the Wi-Fi dropdown describes, read the way the old network pill did: every network rather than
 * only the active one (with a VPN up, the active network is the VPN itself), validated meaning Android reached the
 * internet through it (a captive portal stays unvalidated until login). The name needs ACCESS_FINE_LOCATION from
 * Android 10, which the install script grants.
 */
final class NetworkState {
    boolean online;
    boolean validated;
    boolean wifi;
    boolean ethernet;
    boolean vpn;
    /** The Wi-Fi network's name, or null when it can't be read. */
    String ssid;
    /** 0 to 4, or -1 when not on Wi-Fi. */
    int level = -1;

    /** The network Android last reported lost; skipped while getAllNetworks() still lists it. */
    private static volatile Network lostNetwork;

    static void lost(Network n) {
        lostNetwork = n;
    }

    static void found(Network n) {
        if (n != null && n.equals(lostNetwork)) lostNetwork = null;
    }

    static NetworkState read(Context ctx) {
        NetworkState s = new NetworkState();
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network gone = lostNetwork;
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                if (net.equals(gone)) continue;
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    s.vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                s.online = true;
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) s.validated = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) s.ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s.wifi = true;
            }
            if (!s.online) s.vpn = false;
            if (s.wifi && !s.ethernet) {
                WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                if (info != null) {
                    s.level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
                    s.ssid = NetworkWords.ssid(info.getSSID());
                }
            }
        } catch (RuntimeException ignored) {
            // A firmware that throws here gets the offline wording rather than a crash on Home.
        }
        return s;
    }

    /** The nav's network icon: Ethernet, Wi-Fi, or Wi-Fi crossed out while offline. */
    int icon() {
        if (!online) return R.drawable.ic_ui_wifi_off;
        return ethernet ? R.drawable.ic_ui_ethernet : R.drawable.ic_ui_wifi;
    }
}
