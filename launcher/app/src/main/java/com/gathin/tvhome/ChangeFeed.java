package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Movie of the Night's change feed (docs.movieofthenight.com, Changes): which titles' Apple TV Store or Amazon rent
 * and buy options changed in Sweden lately, read at most once a day while Home shows, a few pages at most, each page
 * one request of its budget. A kept title it names is marked stale ({@link Marks}), so its prices are asked again
 * before their 7 days are up. Pure Java with org.json, unit-tested with a recorded answer (ChangeFeedTest).
 */
final class ChangeFeed {
    private ChangeFeed() {}

    static final String ID = StreamingAvailabilityPrices.ID;
    private static final String BASE = "https://api.movieofthenight.com/v4/changes";
    /** The rent and buy catalogues it prices in Sweden. */
    static final String CATALOGS = "apple.rent,apple.buy,prime.rent,prime.buy";
    /** Read at most once a day, and at most this many pages (25 changes each). */
    static final long EVERY = PriceCachePolicy.DAY;
    static final int MAX_PAGES = 3;
    /** The feed goes back 31 days at most; a week is as far back as kept prices go. */
    static final long LOOK_BACK = PriceCachePolicy.PRICE_TTL;

    /** Whether the feed is due: never read, or read a day ago or more. */
    static boolean due(long lastReadAt, long now) {
        return !PriceCachePolicy.fresh(lastReadAt, now, EVERY);
    }

    /** The changes since the last read (a week back at most), then the page after {@code cursor} (null for the first). */
    static String url(long lastReadAt, long now, String cursor) {
        long from = Math.max(lastReadAt, now - LOOK_BACK) / 1000;
        StringBuilder b = new StringBuilder(BASE)
                .append("?country=").append(WatchProviders.REGION.toLowerCase(java.util.Locale.ROOT))
                .append("&change_type=updated&item_type=show&catalogs=").append(encode(CATALOGS))
                .append("&from=").append(from);
        if (cursor != null) b.append("&cursor=").append(encode(cursor));
        return b.toString();
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /** One page: the launcher's ids of the titles it names ("movie:345887"), and the next page's cursor or null. */
    static final class Page {
        final Set<String> ids;
        final String nextCursor;

        Page(Set<String> ids, String nextCursor) {
            this.ids = Collections.unmodifiableSet(ids);
            this.nextCursor = nextCursor;
        }
    }

    static Page parse(String json) throws JSONException {
        if (json == null) return new Page(Collections.emptySet(), null);
        JSONObject o = new JSONObject(json);
        JSONObject shows = o.optJSONObject("shows");
        Set<String> ids = new LinkedHashSet<>();
        JSONArray changes = o.optJSONArray("changes");
        for (int i = 0; changes != null && i < changes.length(); i++) {
            JSONObject c = changes.optJSONObject(i);
            JSONObject show = c == null || shows == null ? null : shows.optJSONObject(c.optString("showId"));
            String id = show == null ? null : launcherId(show.optString("tmdbId", ""));
            if (id != null) ids.add(id);
        }
        String next = o.optBoolean("hasMore") ? o.optString("nextCursor", "") : "";
        return new Page(ids, next.isEmpty() ? null : next);
    }

    /** "movie/597" or "tv/1396" as the launcher's "movie:597" or "tv:1396"; null for anything else. */
    static String launcherId(String tmdbId) {
        int slash = tmdbId.indexOf('/');
        if (slash < 0) return null;
        String id = tmdbId.substring(0, slash) + ":" + tmdbId.substring(slash + 1);
        return TmdbApi.isTmdbId(id) ? id : null;
    }

    /**
     * Titles the feed marked stale, each with when (epoch ms), kept in the app's preferences between runs. A mark
     * older than {@link PriceCachePolicy#PRICE_TTL} goes: by then the kept prices have run out anyway. At most
     * {@link #MAX} marks, the newest kept. Thread-safe: the price thread reads it.
     */
    static final class Marks {
        static final int MAX = 500;
        private final Map<String, Long> at = new LinkedHashMap<>();

        /** When the title was marked stale, or 0. */
        synchronized long staleAt(String id) {
            Long t = at.get(id);
            return t == null ? 0 : t;
        }

        synchronized void mark(Set<String> ids, long now) {
            for (String id : ids) {
                at.remove(id);
                at.put(id, now);
            }
            while (at.size() > MAX) at.remove(at.keySet().iterator().next());
        }

        /** The title's prices came in again: no longer stale. */
        synchronized void clear(String id) {
            at.remove(id);
        }

        synchronized void expire(long now) {
            at.values().removeIf(t -> !PriceCachePolicy.fresh(t, now, PriceCachePolicy.PRICE_TTL));
        }

        synchronized List<String> ids() {
            return new ArrayList<>(at.keySet());
        }

        /** "movie:1=1790000000000,tv:2=1790000000001". */
        synchronized String encode() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Long> e : at.entrySet()) {
                if (b.length() > 0) b.append(',');
                b.append(e.getKey()).append('=').append(e.getValue());
            }
            return b.toString();
        }

        static Marks decode(String s) {
            Marks m = new Marks();
            if (s == null || s.isEmpty()) return m;
            for (String part : s.split(",")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                try {
                    String id = part.substring(0, eq);
                    if (TmdbApi.isTmdbId(id)) m.at.put(id, Long.parseLong(part.substring(eq + 1)));
                } catch (NumberFormatException ignored) {
                    // A broken mark is no mark.
                }
            }
            return m;
        }
    }
}
