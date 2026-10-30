package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One way to watch a title on one Swedish service, with real prices: what {@link OfferMerge} makes from TMDB's
 * availability and the price sources. Immutable. A rent or buy offer keeps the cheapest price per picture quality
 * ({@link #prices}, empty when no source priced it); SUB and FREE offers have none. {@link #toOffer} gives the
 * launcher's existing {@link Offer} for a service {@link ServiceId} knows.
 */
final class PricedOffer {
    final WatchProviders.Entry provider;
    final OfferType type;
    /** The cheapest price per quality, SD to 4K. */
    final Map<Quality, Money> prices;
    /** The prices are per season (a show), so the cheapest is a "from" price. */
    final boolean seasonal;

    PricedOffer(WatchProviders.Entry provider, OfferType type, Map<Quality, Money> prices, boolean seasonal) {
        this.provider = provider;
        this.type = type;
        Map<Quality, Money> copy = new EnumMap<>(Quality.class);
        if (prices != null) copy.putAll(prices);
        this.prices = Collections.unmodifiableMap(copy);
        this.seasonal = seasonal;
    }

    boolean hasPrice() {
        return !prices.isEmpty();
    }

    /** The cheapest price in any quality (the best quality of equal prices), or null. */
    Money cheapest() {
        Quality q = cheapestQuality();
        return q == null ? null : prices.get(q);
    }

    /** The quality of the cheapest price (the best of equal ones), or null when there is no price. */
    Quality cheapestQuality() {
        Quality best = null;
        for (Map.Entry<Quality, Money> e : prices.entrySet()) {
            if (best == null || e.getValue().compareTo(prices.get(best)) <= 0) best = e.getKey();
        }
        return best;
    }

    /**
     * "49 kr" when there is one price, "from 39 kr" when qualities cost different amounts or the price is per
     * season, null without a price.
     */
    String priceLabel() {
        Money min = cheapest();
        if (min == null) return null;
        return isFrom() ? min.formatFrom() : min.format();
    }

    /** Several prices: qualities that cost different amounts, or per-season prices. */
    boolean isFrom() {
        Money min = cheapest();
        if (min == null) return false;
        boolean several = seasonal;
        for (Money m : prices.values()) several |= !m.equals(min);
        return several;
    }

    /** "Rent 49 kr", "Buy from 99 kr", or just "Rent" when no source priced it. SUB and FREE: "". */
    String label() {
        if (type != OfferType.RENT && type != OfferType.BUY) return "";
        String verb = type == OfferType.RENT ? "Rent" : "Buy";
        String price = priceLabel();
        return price == null ? verb : verb + " " + price;
    }

    /** One line per quality for the rent dialog, SD first: "HD · 49 kr". */
    List<String> qualityLines() {
        List<String> out = new ArrayList<>(prices.size());
        for (Map.Entry<Quality, Money> e : prices.entrySet()) {
            out.add(e.getKey().label + " · " + (seasonal ? e.getValue().formatFrom() : e.getValue().format()));
        }
        return out;
    }

    /**
     * The launcher's real {@link Offer}: the cheapest price, in major units and as money, with "from" when there are
     * several, and that price's quality ("HD"); price 0, no money and no quality when nothing is priced, as for SUB
     * and FREE.
     */
    Offer toOffer() {
        Money min = cheapest();
        Quality q = cheapestQuality();
        return new Offer(provider.service, type, min == null ? 0 : min.major(), q == null ? null : q.label, min,
                isFrom(), true);
    }

    @Override
    public String toString() {
        return provider.key + " " + type + (prices.isEmpty() ? "" : " " + prices) + (seasonal ? " seasonal" : "");
    }
}
