package com.gathin.tvhome;

import java.util.Locale;

/**
 * The picture a store sells or rents a title in. Stores price SD, HD and 4K separately (Apple TV's HD price usually
 * includes 4K), so the merged offers keep them apart. {@link #label} is what a card or the rent dialog shows.
 */
enum Quality {
    SD("SD"), HD("HD"), UHD("4K");

    final String label;

    Quality(String label) {
        this.label = label;
    }

    /**
     * From a source's own word: "sd", "HD", "4K", "uhd", "qhd" (Streaming Availability's 1440p, sold as HD), "UHD";
     * null for anything else.
     */
    static Quality parse(String s) {
        if (s == null) return null;
        switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "SD":
                return SD;
            case "HD":
            case "QHD":
            case "FHD":
                return HD;
            case "4K":
            case "UHD":
                return UHD;
            default:
                return null;
        }
    }
}
