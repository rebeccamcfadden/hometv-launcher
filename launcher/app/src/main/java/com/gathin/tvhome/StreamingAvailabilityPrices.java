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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Store prices from Movie of the Night's Streaming Availability API ({@code GET /v4/shows/movie/{tmdb id}?country=se}),
 * which looks titles up by TMDB id and gives each price with its currency. Its free plan allows 1,000 requests a
 * month; its terms ask for attribution with a link to https://www.movieofthenight.com/about/api. The key is the
 * optional {@code movie_of_the_night_api_key} in tv.properties (from developers.movieofthenight.com; see
 * {@link PriceKeys}), sent as the X-API-Key header. In Sweden it prices only Apple TV and Amazon. A show is asked for by season, since a series
 * often has no price of its own; an option through an add-on channel is skipped.
 */
final class StreamingAvailabilityPrices implements PriceSource {
    static final String ID = "streamingavailability";
    private static final String BASE = "https://api.movieofthenight.com/v4/shows/";
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,3})");

    private final String key;

    StreamingAvailabilityPrices(String key) {
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
        return 1;
    }

    static String url(PriceTitle t) {
        return BASE + t.tmdbPath() + "?country=" + WatchProviders.REGION.toLowerCase(Locale.ROOT)
                + (t.isMovie() ? "" : "&series_granularity=season");
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
            throw new IOException("Streaming Availability: unexpected answer", e);
        }
    }

    /** The show's own rent and buy prices in {@code region} (season 0), then each season's, numbered from its title. */
    static List<StorePrice> parse(String json, String region, WatchProviders table) throws JSONException {
        if (json == null) return Collections.emptyList();
        JSONObject show = new JSONObject(json);
        String country = region.toLowerCase(Locale.ROOT);
        List<StorePrice> out = new ArrayList<>();
        options(show.optJSONObject("streamingOptions"), country, 0, table, out);
        JSONArray seasons = show.optJSONArray("seasons");
        for (int i = 0; seasons != null && i < seasons.length(); i++) {
            JSONObject s = seasons.optJSONObject(i);
            if (s == null) continue;
            Matcher m = NUMBER.matcher(s.optString("title", ""));
            int number = m.find() ? Integer.parseInt(m.group(1)) : i + 1;
            options(s.optJSONObject("streamingOptions"), country, number, table, out);
        }
        return out;
    }

    private static void options(JSONObject byCountry, String country, int season, WatchProviders table,
                                List<StorePrice> out) {
        JSONArray list = byCountry == null ? null : byCountry.optJSONArray(country);
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null || o.has("addon")) continue;
            OfferType type = "rent".equals(o.optString("type")) ? OfferType.RENT
                    : "buy".equals(o.optString("type")) ? OfferType.BUY : null;
            JSONObject price = o.optJSONObject("price");
            if (type == null || price == null) continue;
            JSONObject service = o.optJSONObject("service");
            WatchProviders.Entry store = service == null ? null : table.storeForName(service.optString("id", ""));
            if (store == null && service != null) store = table.storeForName(service.optString("name", ""));
            if (store == null) continue;
            Money m = Money.of(price.optString("amount", ""), price.optString("currency", ""));
            if (m == null || m.minor <= 0) continue;
            Quality q = Quality.parse(o.optString("quality", ""));
            out.add(new StorePrice(store.key, type, q != null ? q : Quality.HD, m, season, ID));
        }
    }
}
