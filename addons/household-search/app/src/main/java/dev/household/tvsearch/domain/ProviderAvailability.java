package dev.household.tvsearch.domain;

import java.util.Objects;

public final class ProviderAvailability {
    public enum Access { SUBSCRIPTION, FREE, FREE_WITH_ADS, RENT, BUY, UNKNOWN }

    public final String providerId;
    public final String providerName;
    public final Access access;
    public final String region;
    public final String requiredPlan;
    public final String webUrl;
    public final String providerContentId;
    public final long checkedAtMillis;

    public ProviderAvailability(String providerId, String providerName, Access access, String region,
                                String requiredPlan, String webUrl, String providerContentId, long checkedAtMillis) {
        this.providerId = Objects.requireNonNull(providerId);
        this.providerName = Objects.requireNonNull(providerName);
        this.access = Objects.requireNonNull(access);
        this.region = Objects.requireNonNull(region);
        this.requiredPlan = requiredPlan;
        this.webUrl = webUrl;
        this.providerContentId = providerContentId;
        this.checkedAtMillis = checkedAtMillis;
    }
}
