package com.gathin.tvhome;

/**
 * One way to watch a title on one service (design_preview/src/types.ts, Offer). Immutable. {@link #price} is 0 when
 * the offer has none (SUB and FREE, or a store no price source priced); {@link #quality} ("4K · HDR", "HD") may be
 * null. A mock offer's price is in dollars in {@link #price}; a real one (V2, {@link #real}) from TMDB's availability
 * has its price, when known, in {@link #money} too, in kronor, and {@link #from} when it has several (qualities or
 * seasons), so it reads "from 39 kr". {@link Offers#price(Offer)} turns either into text.
 */
final class Offer {
    final ServiceId service;
    final OfferType type;
    final double price;
    final String quality;
    final Money money;
    final boolean from;
    final boolean real;

    /** A mock offer, priced in dollars. */
    Offer(ServiceId service, OfferType type, double price, String quality) {
        this(service, type, price, quality, null, false, false);
    }

    Offer(ServiceId service, OfferType type, double price, String quality, Money money, boolean from, boolean real) {
        this.service = service;
        this.type = type;
        this.price = price;
        this.quality = quality;
        this.money = money;
        this.from = from;
        this.real = real;
    }

    @Override
    public String toString() {
        return service.id + " " + type + (price > 0 ? " " + price : "") + (quality != null ? " " + quality : "");
    }
}
