package com.gathin.tvhome;

/**
 * The price sources' keys from the build (launcher/tv.properties, BuildConfig). Movie of the Night's key is
 * {@code movie_of_the_night_api_key}, as the project lead named it; {@code streaming_availability_api_key}, its API's
 * other name, is read too, the first one winning. Pure apart from the BuildConfig reads (PriceKeysTest).
 */
final class PriceKeys {
    private PriceKeys() {}

    static String watchmode() {
        return BuildConfig.WATCHMODE_KEY;
    }

    static String movieOfTheNight() {
        return pick(BuildConfig.MOVIE_OF_THE_NIGHT_KEY, BuildConfig.STREAMING_AVAILABILITY_KEY);
    }

    /** The first key that is set, trimmed; "" for none. */
    static String pick(String preferred, String alias) {
        String a = preferred == null ? "" : preferred.trim();
        return a.isEmpty() ? (alias == null ? "" : alias.trim()) : a;
    }
}
