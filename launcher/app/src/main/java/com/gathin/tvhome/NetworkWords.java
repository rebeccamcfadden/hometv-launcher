package com.gathin.tvhome;

/**
 * The Wi-Fi dropdown's wording, chosen without Android so it is tested on the JVM: which signal word goes with a
 * level, and which state line ("Connected · Internet OK · Strong signal") goes with a connection.
 */
final class NetworkWords {
    private NetworkWords() {}

    enum Signal { STRONG, FAIR, WEAK, NONE }

    enum State { OFFLINE, NO_INTERNET, ONLINE }

    /** WifiManager.calculateSignalLevel(rssi, 5) gives 0 to 4. */
    static Signal signal(int level) {
        if (level < 0) return Signal.NONE;
        if (level >= 3) return Signal.STRONG;
        if (level == 2) return Signal.FAIR;
        return Signal.WEAK;
    }

    static State state(boolean online, boolean validated) {
        if (!online) return State.OFFLINE;
        return validated ? State.ONLINE : State.NO_INTERNET;
    }

    /** What WifiInfo.getSSID() returns when the name can't be read (no location permission, or not connected). */
    static final String UNKNOWN_SSID = "<unknown ssid>";

    /** The SSID WifiInfo reports, without its quotes; null for the placeholders firmware returns when it can't tell.
     *  Only the exact placeholders: a real network may well be named "unknown-network". */
    static String ssid(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.equalsIgnoreCase(UNKNOWN_SSID) || s.equals("0x")) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.trim().isEmpty() ? null : s;
    }
}
