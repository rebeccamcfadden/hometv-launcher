package com.gathin.tvhome;

import android.content.Context;

/**
 * The data every screen reads, made once in MainActivity.onCreate and handed out through {@link Router#data()}: the
 * catalogue (mock in V1), the services on this TV, the household's lists stored on the TV, and the service table.
 */
final class Data {
    final Catalog catalog;
    final Services services;
    final Household household;
    final ServiceTable table;

    private Data(Catalog catalog, Services services, Household household, ServiceTable table) {
        this.catalog = catalog;
        this.services = services;
        this.household = household;
        this.table = table;
    }

    static Data create(Context ctx, Config cfg) {
        Catalog catalog = new MockCatalog();
        ServiceTable table = new ServiceTable();
        return new Data(catalog, Services.from(ctx, cfg, table), new Household(ctx, catalog), table);
    }

    /** The offer a title's Watch button uses on this TV (Offers.bestOffer with the subscribed services). */
    Offer bestOffer(Title t) {
        return Offers.bestOffer(t, services.subscribed());
    }

    /** "Prime Video · Included", "Apple TV · Rent $3.99" or "Not available", for this TV's services. */
    String offerLabel(Offer o) {
        return Offers.offerLabel(o, services.subscribed(), table);
    }

    boolean isIncluded(Offer o) {
        return Offers.isIncludedOffer(o, services.subscribed());
    }
}
