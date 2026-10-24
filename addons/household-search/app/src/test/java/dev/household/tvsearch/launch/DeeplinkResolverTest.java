package dev.household.tvsearch.launch;

import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.domain.LaunchTarget;
import dev.household.tvsearch.domain.MediaTitle;
import dev.household.tvsearch.domain.ProviderAvailability;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DeeplinkResolverTest {
    private final MediaTitle title;
    private final ProviderAvailability offer;
    private final TestAdapter adapter = new TestAdapter();
    private final FakeLauncher launcher = new FakeLauncher();

    public DeeplinkResolverTest() throws Exception {
        FakeCatalogService catalog = new FakeCatalogService();
        title = catalog.search("Lantern").get(0);
        offer = new ProviderAvailability("test", "Test", ProviderAvailability.Access.SUBSCRIPTION,
                "US", null, "https://example.com/title", "fixture-id", 0);
    }

    private DeeplinkResolver resolver() { return new DeeplinkResolver(Collections.singletonList(adapter)); }

    @Test public void candidatesAreOrderedTitleSearchHomeWeb() {
        assertEquals(Arrays.asList(LaunchTarget.Level.TITLE, LaunchTarget.Level.SEARCH,
                LaunchTarget.Level.APP_HOME, LaunchTarget.Level.WEB),
                resolver().resolve(title, offer, launcher).stream().map(t -> t.level).collect(Collectors.toList()));
    }

    @Test public void firstSuccessfulCandidateStopsExecution() {
        launcher.succeedAt = LaunchTarget.Level.TITLE;
        DeeplinkResolver.Outcome outcome = resolver().launch(title, offer, launcher);
        assertTrue(outcome.opened());
        assertEquals(Collections.singletonList(LaunchTarget.Level.TITLE), launcher.attempted);
        assertTrue(outcome.failures.isEmpty());
    }

    @Test public void failedTitleFallsBackToSearch() {
        launcher.succeedAt = LaunchTarget.Level.SEARCH;
        DeeplinkResolver.Outcome outcome = resolver().launch(title, offer, launcher);
        assertEquals(LaunchTarget.Level.SEARCH, outcome.target.level);
        assertEquals(Arrays.asList(LaunchTarget.Level.TITLE, LaunchTarget.Level.SEARCH), launcher.attempted);
        assertEquals(1, outcome.failures.size());
    }

    @Test public void failedTitleAndSearchFallBackToHome() {
        launcher.succeedAt = LaunchTarget.Level.APP_HOME;
        assertEquals(LaunchTarget.Level.APP_HOME, resolver().launch(title, offer, launcher).target.level);
        assertEquals(3, launcher.attempted.size());
    }

    @Test public void executionFailuresReachWebLast() {
        launcher.succeedAt = LaunchTarget.Level.WEB;
        assertEquals(LaunchTarget.Level.WEB, resolver().launch(title, offer, launcher).target.level);
        assertEquals(4, launcher.attempted.size());
    }

    @Test public void appNotInstalledSkipsAllAppTargets() {
        launcher.installed = false;
        launcher.succeedAt = LaunchTarget.Level.WEB;
        assertEquals(LaunchTarget.Level.WEB, resolver().launch(title, offer, launcher).target.level);
        assertEquals(Collections.singletonList(LaunchTarget.Level.WEB), launcher.attempted);
    }

    @Test public void noAppAndNoWebIsAnExplicitFailure() {
        launcher.installed = false;
        adapter.web = null;
        DeeplinkResolver.Outcome outcome = resolver().launch(title, offer, launcher);
        assertFalse(outcome.opened());
        assertFalse(outcome.failures.isEmpty());
        assertTrue(launcher.attempted.isEmpty());
    }

    @Test public void unsupportedProviderDoesNotThrowOrOpenAnotherProvider() {
        DeeplinkResolver.Outcome outcome = new DeeplinkResolver(Collections.emptyList()).launch(title, offer, launcher);
        assertFalse(outcome.opened());
        assertTrue(outcome.failures.get(0).contains("no supported adapter"));
        assertTrue(launcher.attempted.isEmpty());
    }

    @Test public void missingTitleAndSearchPredictablyUseHome() {
        adapter.title = null;
        adapter.search = null;
        launcher.succeedAt = LaunchTarget.Level.APP_HOME;
        assertEquals(LaunchTarget.Level.APP_HOME, resolver().launch(title, offer, launcher).target.level);
        assertEquals(Collections.singletonList(LaunchTarget.Level.APP_HOME), launcher.attempted);
    }

    @Test public void unverifiedAppLinksAreNotTried() {
        adapter.title = new LaunchTarget(LaunchTarget.Level.TITLE, LaunchTarget.Verification.NEEDS_DEVICE_TESTING,
                "test", "test.package", null, "id", null);
        assertEquals(LaunchTarget.Level.SEARCH, resolver().resolve(title, offer, launcher).get(0).level);
    }

    @Test public void allFailuresRemainAvailableForTheUi() {
        DeeplinkResolver.Outcome outcome = resolver().launch(title, offer, launcher);
        assertFalse(outcome.opened());
        assertEquals(4, outcome.failures.size());
    }

    @Test public void malformedOrUnsafeWebAddressesAreSkipped() {
        for (String uri : Arrays.asList(null, "", "javascript:bad", "intent://bad", "http://example.com",
                "https://name:password@example.com", "https://", "not a URL")) {
            assertFalse(DeeplinkResolver.isHttps(uri));
        }
        assertTrue(DeeplinkResolver.isHttps("https://example.com/title"));
    }

    @Test public void wrongProviderTargetIsNotLaunched() {
        adapter.title = new LaunchTarget(LaunchTarget.Level.TITLE, LaunchTarget.Verification.DEVICE_VERIFIED,
                "another-provider", "test.package", null, null, null);
        assertEquals(LaunchTarget.Level.SEARCH, resolver().resolve(title, offer, launcher).get(0).level);
    }

    @Test public void demoCinemaUsesTitleForMoviesAndHomeForShows() throws Exception {
        FakeCatalogService catalog = new FakeCatalogService();
        DemoProviderAdapter cinema = new DemoProviderAdapter(FakeCatalogService.CINEMA, "demo.package");
        DeeplinkResolver resolver = new DeeplinkResolver(Collections.singletonList(cinema));
        for (MediaTitle media : catalog.search("Lantern")) {
            ProviderAvailability availability = catalog.getAvailability(media.id, media.type, "GB").get(0);
            List<LaunchTarget> targets = resolver.resolve(media, availability, launcher);
            assertEquals(media.type == MediaTitle.Type.MOVIE ? LaunchTarget.Level.TITLE : LaunchTarget.Level.APP_HOME,
                    targets.get(0).level);
            assertEquals(LaunchTarget.Verification.DEMO, targets.get(0).verification);
            assertNull(targets.get(0).uri);
        }
    }

    @Test public void demoSeriesProvidesSearchRatherThanAnInventedTitleLink() throws Exception {
        FakeCatalogService catalog = new FakeCatalogService();
        DemoProviderAdapter series = new DemoProviderAdapter(FakeCatalogService.SERIES, "demo.package");
        ProviderAvailability availability = catalog.getAvailability(title.id, title.type, "US").get(1);
        LaunchTarget target = new DeeplinkResolver(Collections.singletonList(series))
                .resolve(title, availability, launcher).get(0);
        assertEquals(LaunchTarget.Level.SEARCH, target.level);
        assertEquals(title.title, target.query);
        assertNull(target.uri);
    }

    private static LaunchTarget target(LaunchTarget.Level level) {
        return new LaunchTarget(level, LaunchTarget.Verification.DEVICE_VERIFIED, "test", "test.package",
                level == LaunchTarget.Level.WEB ? "https://example.com/title" : null, "fixture-id", "Fixture title");
    }

    private static final class TestAdapter implements ProviderAdapter {
        LaunchTarget title = target(LaunchTarget.Level.TITLE);
        LaunchTarget search = target(LaunchTarget.Level.SEARCH);
        LaunchTarget web = target(LaunchTarget.Level.WEB);
        @Override public String providerId() { return "test"; }
        @Override public String packageName() { return "test.package"; }
        @Override public LaunchTarget buildTitleLaunchTarget(MediaTitle media, ProviderAvailability offer) { return title; }
        @Override public LaunchTarget buildSearchLaunchTarget(MediaTitle media) { return search; }
        @Override public LaunchTarget buildAppHomeTarget() { return target(LaunchTarget.Level.APP_HOME); }
        @Override public LaunchTarget buildWebTarget(MediaTitle media, ProviderAvailability offer) { return web; }
    }

    private static final class FakeLauncher implements DeeplinkResolver.Launcher {
        boolean installed = true;
        LaunchTarget.Level succeedAt;
        final List<LaunchTarget.Level> attempted = new ArrayList<>();
        @Override public boolean isAppInstalled(String packageName) { return installed; }
        @Override public DeeplinkResolver.Attempt launch(LaunchTarget target) {
            attempted.add(target.level);
            return new DeeplinkResolver.Attempt(target.level == succeedAt, "Test failure: " + target.level);
        }
    }
}
