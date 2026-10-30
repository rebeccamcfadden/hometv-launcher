package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The saved Wi-Fi networks the Wi-Fi dropdown offers to switch to (design_preview/src/screens/dropdown.ts, wifiMenu:
 * the current network in the header, the other saved ones below it). Chosen without Android so it is tested on the
 * JVM; WifiNetworks reads them from the TV. Up to Android 9 a normal app may list the networks saved in the TV's
 * settings and ask to connect to one; from Android 10 it may do neither for an app targeting it (BACKLOG.md, Wi-Fi
 * networks), so the dropdown keeps opening Wi-Fi settings there.
 */
final class SavedNetworks {
    private SavedNetworks() {}

    /** Android 10: getConfiguredNetworks() is empty and enableNetwork() refused for apps targeting it. */
    static final int FIRST_UNSUPPORTED_SDK = 29;

    static final class Net {
        final int id;
        final String ssid;
        final boolean secured;

        Net(int id, String ssid, boolean secured) {
            this.id = id;
            this.ssid = ssid;
            this.secured = secured;
        }
    }

    static boolean supported(int sdk) {
        return sdk < FIRST_UNSUPPORTED_SDK;
    }

    /**
     * The name of the saved network with this id, or null. Android 9 hides the connected network's name from an app
     * without location access but not its id, so the dropdown's header can still name it.
     */
    static String nameOf(List<Net> saved, int id) {
        if (id == -1) return null;
        for (Net n : saved) if (n != null && n.id == id) return n.ssid;
        return null;
    }

    /**
     * The saved networks to offer: not the one connected now (by its id, and any other entry with its name), each
     * name once, without nameless ones, sorted by name. currentId is -1 and currentSsid null when not on Wi-Fi.
     */
    static List<Net> others(List<Net> saved, int currentId, String currentSsid) {
        Set<String> skip = new HashSet<>();
        if (currentSsid != null) skip.add(currentSsid);
        for (Net n : saved) if (n != null && n.id == currentId && n.ssid != null) skip.add(n.ssid);
        List<Net> out = new ArrayList<>();
        for (Net n : saved) {
            if (n == null || n.ssid == null || n.ssid.trim().isEmpty() || n.id == currentId) continue;
            if (skip.add(n.ssid)) out.add(n);
        }
        Collections.sort(out, (a, b) -> {
            int c = a.ssid.toLowerCase(Locale.ROOT).compareTo(b.ssid.toLowerCase(Locale.ROOT));
            return c != 0 ? c : a.ssid.compareTo(b.ssid);
        });
        return out;
    }
}
