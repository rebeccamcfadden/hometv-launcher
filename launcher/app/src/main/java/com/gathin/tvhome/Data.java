package com.gathin.tvhome;

import android.content.Context;

import java.util.Set;

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
            catalog.keepPriced(household.listedIds());
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
        catalog.setPriceStores(ServiceChoices.stores(ctx));
        // The real catalogue names services as they are branded now; the mock one keeps the prototype's names.
        ServiceTable table = token.isEmpty() ? new ServiceTable() : ServiceTable.branded();
        Household household = new Household(ctx, catalog);
        Data data = new Data(catalog, Services.from(ctx, cfg, table), household, table,
                new Accounts(ctx, household, token));
        Context app = ctx.getApplicationContext();
        data.tickedStores = () -> ServiceChoices.stores(app);
        return data;
    }

    /** Launcher settings' Stores ticks (ServiceChoices), read each time so a change shows at once; null if never set. */
    private java.util.function.Supplier<Set<ServiceId>> tickedStores;

    /** The offer a title's Watch button uses on this TV (Offers.bestOffer with the subscribed services). */
    Offer bestOffer(Title t) {
        return Offers.bestOffer(PriceCards.ticked(t, stores()), services.subscribed());
    }

    /**
     * The stores whose rent and buy offers count on the title page (design 07-prices A, Ticks): the ticked ones plus
     * every store the Stores pane doesn't list ({@link ServicePicks#counted}); null means every store.
     */
    Set<ServiceId> stores() {
        return tickedStores == null ? null : ServicePicks.counted(tickedStores.get());
    }

    /**
     * The stores a card counts (F1): only the ones ticked in the Stores pane, so a store the pane doesn't list never
     * names a card; null (no Stores choice made yet) means every store.
     */
    private Set<ServiceId> cardStores() {
        return tickedStores == null ? null : tickedStores.get();
    }

    /**
     * A small card's third line ({@link PriceCards#cardLine}): "Prime Video · Included", "Rent from 39 kr · 4
     * stores", with the prices already known, from the household's ticked services, its installed free ones and its
     * ticked stores; "" when none of those has the title, or its details (and so its availability) haven't arrived.
     */
    String offerLine(Title t) {
        if (!t.detailed && t.offers.isEmpty()) return "";
        return PriceCards.cardLine(t, services.subscribed(), services.installed(), cardStores(), table);
    }

    /**
     * The offer a library card names ({@link PriceCards#cardOffer}): the best of the household's own offers, or null
     * (a blank provider) when none of its services or stores has the title. The title page uses {@link #bestOffer}.
     */
    Offer cardOffer(Title t) {
        return PriceCards.cardOffer(t, services.subscribed(), services.installed(), cardStores());
    }

    boolean isIncluded(Offer o) {
        return Offers.isIncludedOffer(o, services.subscribed());
    }

    /**
     * MainActivity is gone (destroyed, or rebuilt with a new Data by recreate()): the catalogue's threads end and this
     * Data stops listening, so nothing keeps it alive.
     */
    void close() {
        household.removeListener(wantHousehold);
        accounts.removeListener(accountChanged);
        catalog.close();
    }
}
