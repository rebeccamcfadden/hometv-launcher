package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Store prices from Watchmode ({@code GET /v1/title/movie-{tmdb id}/sources/?regions=SE}), which looks titles up by
 * TMDB id and prices the most Swedish stores: Apple TV ("AppleTV"), Amazon, Rakuten TV and SF Anytime, in SD, HD and
 * 4K. Its free plan is for non-commercial use with attribution, 2,500 credits a month, a TMDB-id lookup costing 2, and
 * answers may be kept at most 30 days. The key is the optional {@code watchmode_api_key} in tv.properties, sent as the
 * X-API-Key header. Watchmode gives no currency; its Swedish prices are in kronor (checked 2026-10-30), so a price is
 * taken to be in the region's own ({@link WatchProviders#currency}). Stores are matched by name
 * ({@link WatchProviders#storeForName}); one the launcher doesn't know is dropped, Blockbuster too (closed, though
 * Watchmode still lists it).
 */
final class WatchmodePrices implements PriceSource {
    static final String ID = "watchmode";
    private static final String BASE = "https://api.watchmode.com/v1/title/";

    private final String key;

    WatchmodePrices(String key) {
        this.key = key == null ? "" : key.trim();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean configured() {
        return !key.isEmpty();
    }

    @Override
    public int cost(PriceTitle t) {
        return 2;
    }

    static String url(PriceTitle t) {
        return BASE + (t.isMovie() ? "movie-" : "tv-") + t.tmdbId + "/sources/?regions=" + WatchProviders.REGION;
    }

    Map<String, String> headers() {
        return Collections.singletonMap("X-API-Key", key);
    }

    @Override
    public List<StorePrice> prices(PriceTitle t, HttpGet http) throws IOException {
        if (!configured()) return Collections.emptyList();
        try {
            return parse(http.get(url(t), headers()), WatchProviders.REGION, WatchProviders.SE);
        } catch (JSONException e) {
            throw new IOException("Watchmode: unexpected answer", e);
        }
    }

    /**
     * The rent and buy prices in {@code region}; a source without a price, in another region or at an unknown store
     * is skipped. A missing quality counts as HD, what Watchmode lists most.
     */
    static List<StorePrice> parse(String json, String region, WatchProviders table) throws JSONException {
        if (json == null) return Collections.emptyList();
        String currency = WatchProviders.currency(region);
        if (currency == null) return Collections.emptyList();
        JSONArray sources = new JSONArray(json);
        List<StorePrice> out = new ArrayList<>();
        for (int i = 0; i < sources.length(); i++) {
            JSONObject s = sources.optJSONObject(i);
            if (s == null || !region.equalsIgnoreCase(s.optString("region", ""))) continue;
            OfferType type = type(s.optString("type", ""));
            if (type == null || s.isNull("price")) continue;
            double amount = s.optDouble("price", Double.NaN);
            if (!(amount > 0)) continue;
            WatchProviders.Entry store = table.storeForName(s.optString("name", ""));
            if (store == null) continue;
            Quality q = s.isNull("format") ? null : Quality.parse(s.optString("format", ""));
            Money m = Money.of(amount, currency);
            if (m != null) out.add(new StorePrice(store.key, type, q != null ? q : Quality.HD, m, 0, ID));
        }
        return out;
    }

    private static OfferType type(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "rent":
                return OfferType.RENT;
            case "buy":
                return OfferType.BUY;
            default:
                return null;
        }
    }
}
