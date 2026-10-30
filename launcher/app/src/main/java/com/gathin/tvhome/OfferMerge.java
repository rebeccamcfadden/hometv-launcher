package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges TMDB's availability for a region with prices from any {@link PriceSource}s into {@link PricedOffer}s, the
 * offers a title preview shows. The rules, unit-tested in OfferMergeTest:
 *
 * <ul>
 *   <li>TMDB's lists become offers in its order: subscription (flatrate) as SUB, free and free-with-ads as FREE, then
 *       RENT and BUY. Providers {@link WatchProviders} leaves out are dropped, and each service has one offer per
 *       type.</li>
 *   <li>Every rent or buy offer takes the cheapest price per quality that any source found for that store; prices in
 *       another currency than the region's are ignored. A store keeps its offer without a price when none is known.</li>
 *   <li>A price for a store and type TMDB doesn't list adds that offer after TMDB's: TMDB misses some stores (it has
 *       no Google Play in Sweden), and the sources look titles up by id.</li>
 *   <li>Season prices of a show are folded into one "from" price per quality.</li>
 * </ul>
 */
final class OfferMerge {
    private OfferMerge() {}

    /** The merged offers and what they came from, for the attribution line. */
    static final class Result {
        final List<PricedOffer> offers;
        /** TMDB's watch page for the title (JustWatch), or null. */
        final String link;
        /** The {@link PriceSource#id}s whose prices are used, in the order of the offers they price. */
        final Set<String> priceSources;

        Result(List<PricedOffer> offers, String link, Set<String> priceSources) {
            this.offers = Collections.unmodifiableList(offers);
            this.link = link;
            this.priceSources = Collections.unmodifiableSet(priceSources);
        }

        /** The offers as the launcher's {@link Offer}s, in the same order. */
        List<Offer> toOffers() {
            List<Offer> out = new ArrayList<>(offers.size());
            for (PricedOffer p : offers) out.add(p.toOffer());
            return Collections.unmodifiableList(out);
        }
    }

    private static final class Prices {
        final Map<Quality, Money> best = new EnumMap<>(Quality.class);
        boolean seasonal;
    }

    static Result merge(ProviderAvailability availability, List<StorePrice> prices, WatchProviders table) {
        String currency = WatchProviders.currency(availability.region);
        Map<String, Prices> priced = new LinkedHashMap<>();
        Set<String> sources = new LinkedHashSet<>();
        Map<String, String> sourceOf = new LinkedHashMap<>();
        for (StorePrice p : prices) {
            if (currency == null || !currency.equals(p.price.currency) || table.byKey(p.store) == null) continue;
            String k = key(p.store, p.type);
            Prices e = priced.get(k);
            if (e == null) priced.put(k, e = new Prices());
            Money old = e.best.get(p.quality);
            if (old == null || p.price.compareTo(old) < 0) {
                e.best.put(p.quality, p.price);
                sourceOf.put(k + "/" + p.quality, p.source);
            }
            e.seasonal |= p.season > 0;
        }

        Map<String, PricedOffer> out = new LinkedHashMap<>();
        for (ProviderAvailability.Item i : availability.items) {
            WatchProviders.Entry e = table.byTmdbId(i.providerId);
            if (e == null) continue;
            OfferType type = type(i.type);
            String k = key(e.key, type);
            if (out.containsKey(k)) continue;
            Prices p = type == OfferType.RENT || type == OfferType.BUY ? priced.remove(k) : null;
            out.put(k, new PricedOffer(e, type, p == null ? null : p.best, p != null && p.seasonal));
        }
        for (Map.Entry<String, Prices> left : priced.entrySet()) {
            String k = left.getKey();
            int bar = k.indexOf('|');
            WatchProviders.Entry e = table.byKey(k.substring(0, bar));
            OfferType type = OfferType.valueOf(k.substring(bar + 1));
            out.put(k, new PricedOffer(e, type, left.getValue().best, left.getValue().seasonal));
        }
        // Credit only the sources whose prices survived as a cheapest one.
        for (PricedOffer o : out.values()) {
            for (Quality q : o.prices.keySet()) {
                String s = sourceOf.get(key(o.provider.key, o.type) + "/" + q);
                if (s != null) sources.add(s);
            }
        }
        return new Result(new ArrayList<>(out.values()), availability.link, sources);
    }

    /**
     * The Where to watch caption: "Availability: JustWatch · Prices: Apple / Watchmode", naming only the price sources
     * whose prices show; just the availability with none, and null without TMDB's availability (the mock catalogue).
     */
    static String attribution(boolean availability, Set<String> priceSources) {
        if (!availability) return null;
        StringBuilder b = new StringBuilder("Availability: JustWatch");
        List<String> names = new ArrayList<>();
        for (String s : priceSources) {
            String name = ItunesPrices.ID.equals(s) ? "Apple" : WatchmodePrices.ID.equals(s) ? "Watchmode"
                    : StreamingAvailabilityPrices.ID.equals(s) ? "Movie of the Night" : null;
            if (name != null && !names.contains(name)) names.add(name);
        }
        if (!names.isEmpty()) b.append(" · Prices: ").append(String.join(" / ", names));
        return b.toString();
    }

    private static String key(String store, OfferType type) {
        return store + "|" + type.name();
    }

    private static OfferType type(ProviderAvailability.Type t) {
        switch (t) {
            case FLATRATE:
                return OfferType.SUB;
            case FREE:
            case ADS:
                return OfferType.FREE;
            case RENT:
                return OfferType.RENT;
            default:
                return OfferType.BUY;
        }
    }
}
