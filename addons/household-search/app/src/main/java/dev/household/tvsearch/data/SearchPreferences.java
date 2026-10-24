package dev.household.tvsearch.data;

import android.content.Context;
import android.content.SharedPreferences;

import dev.household.tvsearch.domain.Subscription;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Only non-sensitive demo settings; no API credentials, account data or telemetry. */
public final class SearchPreferences {
    private static final Set<String> COUNTRIES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(Locale.getISOCountries())));
    private final SharedPreferences preferences;

    public SearchPreferences(Context context) {
        preferences = context.getSharedPreferences("search_settings", Context.MODE_PRIVATE);
    }

    public String region() {
        String country = Locale.getDefault().getCountry();
        return preferences.getString("region", COUNTRIES.contains(country) ? country : "US");
    }

    public static boolean validRegion(String country) {
        return country != null && COUNTRIES.contains(country);
    }

    public void setRegion(String country) {
        if (!validRegion(country)) throw new IllegalArgumentException("Choose a two-letter ISO country code");
        preferences.edit().putString("region", country).apply();
    }

    public List<Subscription> subscriptions() {
        return Arrays.asList(
                new Subscription(FakeCatalogService.CINEMA, null, "shared",
                        preferences.getBoolean("cinema", true)),
                new Subscription(FakeCatalogService.SERIES, null, "shared",
                        preferences.getBoolean("series", false)));
    }

    public void setSubscriptions(boolean cinema, boolean series) {
        preferences.edit().putBoolean("cinema", cinema).putBoolean("series", series).apply();
    }
}
