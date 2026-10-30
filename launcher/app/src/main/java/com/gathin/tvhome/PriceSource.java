package com.gathin.tvhome;

import java.io.IOException;
import java.util.List;

/**
 * Somewhere that knows what stores charge to rent or buy a title in Sweden: Apple's iTunes lookup
 * ({@link ItunesPrices}), Watchmode ({@link WatchmodePrices}) or the Streaming Availability API
 * ({@link StreamingAvailabilityPrices}). Each one builds its requests and parses the answers in pure Java; the network
 * goes through {@link HttpGet}. {@link OfferMerge} combines the prices with TMDB's availability.
 */
interface PriceSource {
    /** A short stable id ("itunes"), for its quota in {@link PriceCachePolicy}. */
    String id();

    /** Whether it can be asked at all: false for a source whose key isn't in tv.properties. */
    boolean configured();

    /** What one lookup of this title costs against the source's monthly quota (requests or credits). */
    int cost(PriceTitle t);

    /** The title's store prices in {@link WatchProviders#REGION}; empty when the source has none. */
    List<StorePrice> prices(PriceTitle t, HttpGet http) throws IOException;
}
