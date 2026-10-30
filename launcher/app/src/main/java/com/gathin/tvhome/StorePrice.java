package com.gathin.tvhome;

/**
 * One store's price to rent or buy a title (or one season of a show) in one picture quality, as a
 * {@link PriceSource} found it. Immutable. {@link #store} is a {@link WatchProviders} key ("appletv", "sfanytime").
 */
final class StorePrice {
    final String store;
    /** RENT or BUY. */
    final OfferType type;
    final Quality quality;
    final Money price;
    /** The season this price is for, 0 for a movie or a whole show. */
    final int season;
    /** The {@link PriceSource#id} that found it. */
    final String source;

    StorePrice(String store, OfferType type, Quality quality, Money price, int season, String source) {
        if (type != OfferType.RENT && type != OfferType.BUY) throw new IllegalArgumentException("not a price: " + type);
        this.store = store;
        this.type = type;
        this.quality = quality;
        this.price = price;
        this.season = season;
        this.source = source;
    }

    @Override
    public String toString() {
        return store + " " + type + " " + quality + " " + price + (season > 0 ? " S" + season : "") + " (" + source + ")";
    }
}
