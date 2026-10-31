package com.gathin.tvhome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One way to watch a title on one service (design_preview/src/types.ts, Offer). Immutable. {@link #price} is 0 when
 * the offer has none (SUB and FREE, or a store no price source priced); {@link #quality} ("4K · HDR", "HD") may be
 * null. A mock offer's price is in dollars in {@link #price}; a real one (V2, {@link #real}) from TMDB's availability
 * has its price, when known, in {@link #money} too, in kronor, and {@link #from} when it has several (qualities or
 * seasons), so it reads "from 39 kr". {@link Offers#price(Offer)} turns either into text. A real rent or buy offer
 * also keeps every quality's price ({@link #prices}, empty when unpriced) and whether they are per season, for Where
 * to watch's line per quality and the rent/buy dialog ({@link PriceCards}).
 */
final class Offer {
    final ServiceId service;
    final OfferType type;
    final double price;
    final String quality;
    final Money money;
    final boolean from;
    final boolean real;
    /** The cheapest price per quality, SD to 4K (a real offer's; empty for a mock one or without a price). */
    final Map<Quality, Money> prices;
    /** The prices are for one season of a show. */
    final boolean seasonal;

    /** A mock offer, priced in dollars. */
    Offer(ServiceId service, OfferType type, double price, String quality) {
        this(service, type, price, quality, null, false, false);
    }

    Offer(ServiceId service, OfferType type, double price, String quality, Money money, boolean from, boolean real) {
        this(service, type, price, quality, money, from, real, null, false);
    }

    Offer(ServiceId service, OfferType type, double price, String quality, Money money, boolean from, boolean real,
          Map<Quality, Money> prices, boolean seasonal) {
        this.service = service;
        this.type = type;
        this.price = price;
        this.quality = quality;
        this.money = money;
        this.from = from;
        this.real = real;
        Map<Quality, Money> copy = new EnumMap<>(Quality.class);
        if (prices != null) copy.putAll(prices);
        this.prices = Collections.unmodifiableMap(copy);
        this.seasonal = seasonal;
    }

    @Override
    public String toString() {
        return service.id + " " + type + (price > 0 ? " " + price : "") + (quality != null ? " " + quality : "");
    }
}
