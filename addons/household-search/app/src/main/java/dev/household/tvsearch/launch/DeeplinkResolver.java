package dev.household.tvsearch.launch;

import dev.household.tvsearch.domain.LaunchTarget;
import dev.household.tvsearch.domain.MediaTitle;
import dev.household.tvsearch.domain.ProviderAvailability;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DeeplinkResolver {
    public interface Launcher {
        boolean isAppInstalled(String packageName);
        Attempt launch(LaunchTarget target);
    }

    public static final class Attempt {
        public final boolean opened;
        public final String message;

        public Attempt(boolean opened, String message) {
            this.opened = opened;
            this.message = Objects.requireNonNull(message);
        }
    }

    public static final class Outcome {
        public final LaunchTarget target;
        public final List<String> failures;

        Outcome(LaunchTarget target, List<String> failures) {
            this.target = target;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        public boolean opened() { return target != null; }
    }

    private final Map<String, ProviderAdapter> adapters = new HashMap<>();

    public DeeplinkResolver(List<ProviderAdapter> adapters) {
        for (ProviderAdapter adapter : adapters) {
            if (this.adapters.put(adapter.providerId(), adapter) != null)
                throw new IllegalArgumentException("Duplicate provider adapter: " + adapter.providerId());
        }
    }

    public List<LaunchTarget> resolve(MediaTitle title, ProviderAvailability offer, Launcher launcher) {
        ProviderAdapter adapter = adapters.get(offer.providerId);
        if (adapter == null) return Collections.emptyList();
        List<LaunchTarget> targets = new ArrayList<>();
        if (launcher.isAppInstalled(adapter.packageName())) {
            add(targets, adapter.buildTitleLaunchTarget(title, offer), offer.providerId, LaunchTarget.Level.TITLE);
            add(targets, adapter.buildSearchLaunchTarget(title), offer.providerId, LaunchTarget.Level.SEARCH);
            add(targets, adapter.buildAppHomeTarget(), offer.providerId, LaunchTarget.Level.APP_HOME);
        }
        LaunchTarget web = adapter.buildWebTarget(title, offer);
        if (web != null && isHttps(web.uri))
            add(targets, web, offer.providerId, LaunchTarget.Level.WEB);
        return Collections.unmodifiableList(targets);
    }

    private static void add(List<LaunchTarget> targets, LaunchTarget target, String providerId, LaunchTarget.Level level) {
        if (target != null && target.level == level && target.providerId.equals(providerId)
                && target.verification != LaunchTarget.Verification.NEEDS_DEVICE_TESTING) targets.add(target);
    }

    static boolean isHttps(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public Outcome launch(MediaTitle title, ProviderAvailability offer, Launcher launcher) {
        List<String> failures = new ArrayList<>();
        for (LaunchTarget target : resolve(title, offer, launcher)) {
            Attempt attempt = launcher.launch(target);
            if (attempt.opened) return new Outcome(target, failures);
            failures.add(attempt.message);
        }
        if (failures.isEmpty()) failures.add(adapters.containsKey(offer.providerId)
                ? "No supported destination is available on this device."
                : "This provider has no supported adapter or launch destination.");
        return new Outcome(null, failures);
    }
}
