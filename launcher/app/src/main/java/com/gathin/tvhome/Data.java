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
    /** V2 Phase 3: the optional TMDB account, which keeps My list and Favorites in step (Accounts). */
    final Accounts accounts;
    /** Loads the titles the household keeps; held here because the catalogue holds its listeners weakly. */
    private final Household.Listener wantHousehold;

    private Data(Catalog catalog, Services services, Household household, ServiceTable table, Accounts accounts) {
        this.catalog = catalog;
        this.services = services;
        this.household = household;
        this.table = table;
        this.accounts = accounts;
        wantHousehold = () -> {
            catalog.want(household.titleIds());
            catalog.keepPriced(household.myListIds());
        };
        household.addListener(wantHousehold);
        wantHousehold.onHouseholdChanged();
        // The lists are priced in the background only for a signed-in account (Catalog.setListPricing).
        accountChanged = () -> catalog.setListPricing(accounts.signedIn());
        accounts.addListener(accountChanged);
        accountChanged.run();
    }

    /** Switches list pricing with the account; held here like wantHousehold. */
    private final Runnable accountChanged;

    static Data create(Context ctx, Config cfg) {
        String token = BuildConfig.TMDB_TOKEN;
        Catalog catalog = token.isEmpty() ? new MockCatalog() : new TmdbCatalog(ctx, token);
        // The real catalogue names services as they are branded now; the mock one keeps the prototype's names.
        ServiceTable table = token.isEmpty() ? new ServiceTable() : ServiceTable.branded();
        Household household = new Household(ctx, catalog);
        return new Data(catalog, Services.from(ctx, cfg, table), household, table,
                new Accounts(ctx, household, token));
    }

    /** The offer a title's Watch button uses on this TV (Offers.bestOffer with the subscribed services). */
    Offer bestOffer(Title t) {
        return Offers.bestOffer(t, services.subscribed());
    }

    /** "Prime Video · Included", "Apple TV · Rent $3.99" ("Apple TV Store · Rent 39 kr") or "Not available". */
    String offerLabel(Offer o) {
        return Offers.offerLabel(o, services.subscribed(), table);
    }

    /**
     * A card's offer line: {@link #offerLabel} of the best offer, or "" for a title whose details (and so its
     * availability) haven't arrived, rather than "Not available".
     */
    String offerLine(Title t) {
        Offer o = shownOffer(t);
        return o == null && !t.detailed ? "" : offerLabel(o);
    }

    /**
     * The offer a card names: the best one, else a subscription the household doesn't have, so a title on a service
     * that isn't set up reads "Apple TV · Subscription" rather than "Not available".
     */
    Offer shownOffer(Title t) {
        Offer o = bestOffer(t);
        if (o != null) return o;
        for (Offer s : t.offers) if (s.type == OfferType.SUB) return s;
        return null;
    }

    boolean isIncluded(Offer o) {
        return Offers.isIncludedOffer(o, services.subscribed());
    }
}
