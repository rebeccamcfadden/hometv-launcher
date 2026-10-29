package com.gathin.tvhome;

/**
 * One service's offers for a title, rent and buy merged (design_preview/src/data/offers.ts, ServiceOffers): the
 * title preview's Where to watch cards. {@link #rent} and {@link #buy} are null when the service has none;
 * {@link #included} means a SUB offer on a subscribed service, {@link #free} a FREE offer.
 */
final class ServiceOffers {
    final ServiceId service;
    final boolean included;
    final boolean free;
    final Offer rent;
    final Offer buy;
    final String quality;

    ServiceOffers(ServiceId service, boolean included, boolean free, Offer rent, Offer buy, String quality) {
        this.service = service;
        this.included = included;
        this.free = free;
        this.rent = rent;
        this.buy = buy;
        this.quality = quality;
    }

    /** Included or free: OK opens the service's app rather than the rent/buy dialog. */
    boolean watchable() {
        return included || free;
    }
}
