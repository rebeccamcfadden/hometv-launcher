package dev.household.tvsearch.launch;

import dev.household.tvsearch.domain.LaunchTarget;
import dev.household.tvsearch.domain.MediaTitle;
import dev.household.tvsearch.domain.ProviderAvailability;

/** Null means a capability is unsupported, not that an invented URI should be tried. */
public interface ProviderAdapter {
    String providerId();
    String packageName();
    LaunchTarget buildTitleLaunchTarget(MediaTitle title, ProviderAvailability offer);
    LaunchTarget buildSearchLaunchTarget(MediaTitle title);
    LaunchTarget buildAppHomeTarget();
    LaunchTarget buildWebTarget(MediaTitle title, ProviderAvailability offer);
}
