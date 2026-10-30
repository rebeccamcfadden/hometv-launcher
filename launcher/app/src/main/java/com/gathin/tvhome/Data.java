package com.gathin.tvhome;

import android.content.Context;

/**
 * The data every screen reads, made once in MainActivity.onCreate and handed out through {@link Router#data()}: the
 * catalogue (TMDB with a token in the build, else the mock one), the services on this TV, the household's lists
 * stored on the TV, and the service table.
 */
final class Data {
    final Catalog catalog;
    final Services services;
    final Household household;
    final ServiceTable table;
    /** Loads the titles the household keeps; held here because the catalogue holds its listeners weakly. */
    private final Household.Listener wantHousehold;

    private Data(Catalog catalog, Services services, Household household, ServiceTable table) {
        this.catalog = catalog;
        this.services = services;
        this.household = household;
        this.table = table;
        wantHousehold = () -> catalog.want(household.titleIds());
        household.addListener(wantHousehold);
        wantHousehold.onHouseholdChanged();
    }

    static Data create(Context ctx, Config cfg) {
        String token = BuildConfig.TMDB_TOKEN;
        Catalog catalog = token.isEmpty() ? new MockCatalog() : new TmdbCatalog(ctx, token);
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
