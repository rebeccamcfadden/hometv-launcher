package com.gathin.tvhome;

import java.util.Collection;

/**
 * The values the quick-settings dropdown shows on its household rows (design_preview/src/screens/quickSettings.ts):
 * the TMDB account's state, how many streaming services are ticked, and the weather's place and unit. Pure Java,
 * unit-tested (QuickWordsTest); QuickSettings turns them into strings.
 */
final class QuickWords {
    private QuickWords() {}

    /** What the TMDB account row says, as the prototype's account.value() does. */
    enum Account {
        NOT_SIGNED_IN,
        GETTING_CODE,
        WAITING,
        SIGNING_IN,
        /** The account's name shows. */
        SIGNED_IN,
        EXPIRED
    }

    static Account account(SignInFlow.Phase phase, boolean signedIn) {
        if (signedIn) return Account.SIGNED_IN;
        if (phase == null) return Account.NOT_SIGNED_IN;
        switch (phase) {
            case REQUESTING:
                return Account.GETTING_CODE;
            case WAITING:
                return Account.WAITING;
            case FINISHING:
                return Account.SIGNING_IN;
            case EXPIRED:
                return Account.EXPIRED;
            default:
                return Account.NOT_SIGNED_IN;
        }
    }

    /**
     * The household's streaming services, for "8 ticked": stores alone (Amazon Video, SF Anytime) have their own
     * section and don't count, YouTube does (ServiceTable#subscribed counts it as subscribed).
     */
    static int servicesTicked(Collection<ServiceId> subscribed, ServiceTable table) {
        int n = 0;
        for (ServiceId s : subscribed) if (!table.isStore(s) || s == ServiceId.YOUTUBE) n++;
        return n;
    }

    /**
     * "New York · °C": the chosen place; while tv.properties' position is used, its {@code weather_city}
     * ({@code buildCity}), or {@code fallback} ("Default") when the build names none.
     */
    static String weather(String place, String buildCity, String fallback, String unit) {
        String name = blank(place) ? blank(buildCity) ? fallback : buildCity.trim() : place.trim();
        return name + " \u00B7 " + unit;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
