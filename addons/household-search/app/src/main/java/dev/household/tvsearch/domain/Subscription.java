package dev.household.tvsearch.domain;

import java.util.List;
import java.util.Objects;

public final class Subscription {
    public final String providerId;
    public final String plan;
    public final String owner;
    public final boolean enabled;

    public Subscription(String providerId, String plan, String owner, boolean enabled) {
        this.providerId = Objects.requireNonNull(providerId);
        this.plan = plan;
        this.owner = Objects.requireNonNull(owner);
        this.enabled = enabled;
    }

    public static boolean includes(ProviderAvailability offer, List<Subscription> subscriptions) {
        if (offer.access != ProviderAvailability.Access.SUBSCRIPTION) return false;
        for (Subscription subscription : subscriptions) {
            if (subscription.enabled && subscription.providerId.equals(offer.providerId)
                    && Objects.equals(subscription.plan, offer.requiredPlan)) return true;
        }
        return false;
    }
}
