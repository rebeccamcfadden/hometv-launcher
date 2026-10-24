package dev.household.tvsearch.domain;

import java.util.Objects;

public final class LaunchTarget {
    public enum Level { TITLE, SEARCH, APP_HOME, WEB }
    public enum Verification { DEMO, DEVICE_VERIFIED, NEEDS_DEVICE_TESTING }

    public final Level level;
    public final Verification verification;
    public final String providerId;
    public final String packageName;
    public final String uri;
    public final String contentId;
    public final String query;

    public LaunchTarget(Level level, Verification verification, String providerId, String packageName,
                        String uri, String contentId, String query) {
        this.level = Objects.requireNonNull(level);
        this.verification = Objects.requireNonNull(verification);
        this.providerId = Objects.requireNonNull(providerId);
        this.packageName = packageName;
        this.uri = uri;
        this.contentId = contentId;
        this.query = query;
    }
}
