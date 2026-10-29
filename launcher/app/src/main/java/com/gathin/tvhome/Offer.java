package com.gathin.tvhome;

/**
 * One way to watch a title on one service (design_preview/src/types.ts, Offer). Immutable. {@link #price} is 0 when
 * the offer has none (SUB and FREE); {@link #quality} ("4K · HDR", "HD") may be null.
 */
final class Offer {
    final ServiceId service;
    final OfferType type;
    final double price;
    final String quality;

    Offer(ServiceId service, OfferType type, double price, String quality) {
        this.service = service;
        this.type = type;
        this.price = price;
        this.quality = quality;
    }

    @Override
    public String toString() {
        return service.id + " " + type + (price > 0 ? " " + price : "") + (quality != null ? " " + quality : "");
    }
}
