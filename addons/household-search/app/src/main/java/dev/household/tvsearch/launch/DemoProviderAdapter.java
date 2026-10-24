package dev.household.tvsearch.launch;

import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.domain.LaunchTarget;
import dev.household.tvsearch.domain.MediaTitle;
import dev.household.tvsearch.domain.ProviderAvailability;

/** Explicit in-app destinations only. These are not streaming-provider URI formats. */
public final class DemoProviderAdapter implements ProviderAdapter {
    private final String id;
    private final String packageName;

    public DemoProviderAdapter(String id, String packageName) {
        if (!FakeCatalogService.CINEMA.equals(id) && !FakeCatalogService.SERIES.equals(id))
            throw new IllegalArgumentException("Not a demo provider");
        this.id = id;
        this.packageName = packageName;
    }

    @Override public String providerId() { return id; }
    @Override public String packageName() { return packageName; }

    @Override public LaunchTarget buildTitleLaunchTarget(MediaTitle title, ProviderAvailability offer) {
        return FakeCatalogService.CINEMA.equals(id) && offer.providerContentId != null
                ? target(LaunchTarget.Level.TITLE, offer.providerContentId, title.title) : null;
    }

    @Override public LaunchTarget buildSearchLaunchTarget(MediaTitle title) {
        return FakeCatalogService.SERIES.equals(id) ? target(LaunchTarget.Level.SEARCH, null, title.title) : null;
    }

    @Override public LaunchTarget buildAppHomeTarget() {
        return target(LaunchTarget.Level.APP_HOME, null, null);
    }

    @Override public LaunchTarget buildWebTarget(MediaTitle title, ProviderAvailability offer) {
        return null;
    }

    private LaunchTarget target(LaunchTarget.Level level, String contentId, String query) {
        return new LaunchTarget(level, LaunchTarget.Verification.DEMO, id, packageName, null, contentId, query);
    }
}
