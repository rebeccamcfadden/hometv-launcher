package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a title can be watched in one region, parsed from TMDB's {@code /movie/{id}/watch/providers} or
 * {@code /tv/{id}/watch/providers} (JustWatch's data; TMDB asks for "Powered by JustWatch" next to it). Which services
 * stream it with a subscription, free, free with ads, rent it or sell it; no prices. Pure Java with Android's
 * org.json, unit-tested (ProviderAvailabilityTest).
 */
final class ProviderAvailability {

    /** TMDB's lists, in the order the merged offers use them. */
    enum Type {
        FLATRATE("flatrate"), FREE("free"), ADS("ads"), RENT("rent"), BUY("buy");

        final String json;

        Type(String json) {
            this.json = json;
        }
    }

    /** One provider in one of the lists. */
    static final class Item {
        final int providerId;
        final String name;
        final Type type;
        final int priority;

        Item(int providerId, String name, Type type, int priority) {
            this.providerId = providerId;
            this.name = name;
            this.type = type;
            this.priority = priority;
        }

        @Override
        public String toString() {
            return type.json + ":" + providerId;
        }
    }

    final String region;
    /** TMDB's watch page for the title in the region (JustWatch's links live there), or null. */
    final String link;
    /** Every list's providers: FLATRATE first, then FREE, ADS, RENT and BUY, each by TMDB's display priority. */
    final List<Item> items;

    ProviderAvailability(String region, String link, List<Item> items) {
        this.region = region;
        this.link = link;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    static ProviderAvailability empty(String region) {
        return new ProviderAvailability(region, null, Collections.emptyList());
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    /** That list's providers, in display priority order. */
    List<Item> of(Type type) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (i.type == type) out.add(i);
        return out;
    }

    static ProviderAvailability parse(String json, String region) throws JSONException {
        return parse(new JSONObject(json), region);
    }

    /** The region's availability ("SE", any case); empty when TMDB has nothing there. */
    static ProviderAvailability parse(JSONObject root, String region) {
        String want = region.trim().toUpperCase(Locale.ROOT);
        JSONObject results = root.optJSONObject("results");
        JSONObject r = results == null ? null : results.optJSONObject(want);
        return r == null ? empty(want) : region(want, r);
    }

    /** Every region in the answer, by its upper-case code. */
    static Map<String, ProviderAvailability> parseAll(JSONObject root) {
        Map<String, ProviderAvailability> out = new LinkedHashMap<>();
        JSONObject results = root.optJSONObject("results");
        if (results == null) return out;
        for (Iterator<String> it = results.keys(); it.hasNext(); ) {
            String code = it.next();
            JSONObject r = results.optJSONObject(code);
            if (r != null) out.put(code.toUpperCase(Locale.ROOT), region(code.toUpperCase(Locale.ROOT), r));
        }
        return out;
    }

    private static ProviderAvailability region(String code, JSONObject r) {
        List<Item> items = new ArrayList<>();
        for (Type type : Type.values()) {
            JSONArray list = r.optJSONArray(type.json);
            if (list == null) continue;
            List<Item> part = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                if (p == null || !p.has("provider_id")) continue;
                int id = p.optInt("provider_id", -1);
                if (id < 0 || seen(part, id)) continue;
                part.add(new Item(id, p.optString("provider_name", ""), type, p.optInt("display_priority", Integer.MAX_VALUE)));
            }
            // List.sort is stable: equal priorities keep TMDB's order.
            part.sort((a, b) -> Integer.compare(a.priority, b.priority));
            items.addAll(part);
        }
        String link = r.optString("link", "");
        return new ProviderAvailability(code, link.isEmpty() ? null : link, items);
    }

    private static boolean seen(List<Item> items, int id) {
        for (Item i : items) if (i.providerId == id) return true;
        return false;
    }
}
