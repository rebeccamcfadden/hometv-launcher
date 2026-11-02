package com.hometv.launcher;

import android.content.Context;

/**
 * The price sources' keys: the ones entered on this TV's keys page (F16, {@link ApiKeys}), else the build's
 * (launcher/tv.properties, BuildConfig), unless removed. Movie of the Night's build key is
 * {@code movie_of_the_night_api_key}, as the project lead named it; {@code streaming_availability_api_key}, its API's
 * other name, is read too, the first one winning ({@link #pick}, PriceKeysTest).
 */
final class PriceKeys {
    private PriceKeys() {}

    static String watchmode(Context ctx) {
        return ApiKeys.get(ctx).key(KeyRules.Id.WATCHMODE);
    }

    static String movieOfTheNight(Context ctx) {
        return ApiKeys.get(ctx).key(KeyRules.Id.MOTN);
    }

    /** Price lookup is set up (First run): the Watchmode key works; Movie of the Night only backs it up (F16). */
    static boolean ready(Context ctx) {
        return ApiKeys.get(ctx).pricesReady();
    }

    /** The first key that is set, trimmed; "" for none. */
    static String pick(String preferred, String alias) {
        String a = preferred == null ? "" : preferred.trim();
        return a.isEmpty() ? (alias == null ? "" : alias.trim()) : a;
    }
}
