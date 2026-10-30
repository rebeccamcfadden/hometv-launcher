package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Apple TV store prices from Apple's public iTunes Search API (no key), in kronor with {@code country=se}. Since late
 * 2025 its search finds no movies in any country, and no TV seasons in Sweden; its lookup by id still answers. So a
 * title is looked up by the iTunes ids Wikidata keeps (P6398 for a movie, several per film, one per storefront: the
 * Swedish one is the one that answers; P6381 for a show's seasons), and only without an answer is it searched for by
 * name and year ({@link TitleMatch}). Apple prices rentals and purchases in SD and HD; HD includes 4K where Apple has
 * it. Seasons are bought, never rented. Apple asks for no more than about 20 requests a minute.
 */
final class ItunesPrices implements PriceSource {
    static final String ID = "itunes";
    static final String STORE = "appletv";
    private static final String BASE = "https://itunes.apple.com/";
    private static final Pattern ITUNES_ID = Pattern.compile("[1-9]\\d{0,11}");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean configured() {
        return true;
    }

    /** A lookup, and a search when the lookup finds nothing. */
    @Override
    public int cost(PriceTitle t) {
        return lookupUrl(t) != null ? 2 : 1;
    }

    /** The lookup for the title's iTunes ids in the Swedish store, or null when it has none. */
    static String lookupUrl(PriceTitle t) {
        List<String> ids = new ArrayList<>();
        for (String id : t.itunesIds) if (ITUNES_ID.matcher(id).matches() && !ids.contains(id)) ids.add(id);
        if (ids.isEmpty()) return null;
        return BASE + "lookup?id=" + String.join(",", ids) + "&country=" + country();
    }

    /** The search by name: movies, or a show's seasons. */
    static String searchUrl(PriceTitle t) {
        return BASE + "search?term=" + encode(t.name) + "&country=" + country()
                + (t.isMovie() ? "&media=movie&entity=movie&limit=10" : "&media=tvShow&entity=tvSeason&limit=25");
    }

    private static String country() {
        return WatchProviders.REGION.toLowerCase(Locale.ROOT);
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public List<StorePrice> prices(PriceTitle t, HttpGet http) throws IOException {
        try {
            String lookup = lookupUrl(t);
            if (t.isMovie()) {
                if (lookup != null) {
                    List<StorePrice> found = parseMovies(http.get(lookup, Collections.emptyMap()), t, true);
                    if (!found.isEmpty()) return found;
                }
                return parseMovies(http.get(searchUrl(t), Collections.emptyMap()), t, false);
            }
            if (lookup != null) {
                List<StorePrice> found = parseSeasons(http.get(lookup, Collections.emptyMap()), t);
                if (!found.isEmpty()) return found;
            }
            return parseSeasons(http.get(searchUrl(t), Collections.emptyMap()), t);
        } catch (JSONException e) {
            throw new IOException("iTunes: unexpected answer", e);
        }
    }

    /**
     * The best-matching movie's prices ({@link TitleMatch#movieScore}: the same year beats a year either side, then
     * the first); empty when none matches. {@code byId} for a lookup answer.
     */
    static List<StorePrice> parseMovies(String json, PriceTitle t, boolean byId) throws JSONException {
        if (json == null) return Collections.emptyList();
        JSONArray results = new JSONObject(json).optJSONArray("results");
        JSONObject best = null;
        int bestScore = -1;
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null || !"feature-movie".equals(r.optString("kind"))) continue;
            int score = TitleMatch.movieScore(t, r.optString("trackName"), TitleMatch.yearOf(r.optString("releaseDate")), byId);
            if (score > bestScore) {
                best = r;
                bestScore = score;
            }
        }
        if (best == null) return Collections.emptyList();
        String currency = best.optString("currency", "");
        List<StorePrice> out = new ArrayList<>(4);
        add(out, best, "trackRentalPrice", OfferType.RENT, Quality.SD, currency, 0);
        add(out, best, "trackHdRentalPrice", OfferType.RENT, Quality.HD, currency, 0);
        add(out, best, "trackPrice", OfferType.BUY, Quality.SD, currency, 0);
        add(out, best, "trackHdPrice", OfferType.BUY, Quality.HD, currency, 0);
        return out;
    }

    /** Every matching season's purchase prices (Apple rents no seasons), by season number. */
    static List<StorePrice> parseSeasons(String json, PriceTitle t) throws JSONException {
        if (json == null) return Collections.emptyList();
        JSONArray results = new JSONObject(json).optJSONArray("results");
        List<StorePrice> out = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null || !"TV Season".equals(r.optString("collectionType"))) continue;
            TitleMatch.SeasonName s = TitleMatch.season(r.optString("collectionName"));
            if (!TitleMatch.seasonMatches(t, s, TitleMatch.yearOf(r.optString("releaseDate")))) continue;
            if (seen.contains(s.season)) continue;
            seen.add(s.season);
            String currency = r.optString("currency", "");
            add(out, r, "collectionPrice", OfferType.BUY, Quality.SD, currency, s.season);
            add(out, r, "collectionHdPrice", OfferType.BUY, Quality.HD, currency, s.season);
        }
        return out;
    }

    private static void add(List<StorePrice> out, JSONObject r, String field, OfferType type, Quality q,
                            String currency, int season) {
        if (!r.has(field) || r.isNull(field)) return;
        double amount = r.optDouble(field, Double.NaN);
        if (!(amount > 0)) return;
        Money m = Money.of(amount, currency);
        if (m != null) out.add(new StorePrice(STORE, type, q, m, season, ID));
    }
}
