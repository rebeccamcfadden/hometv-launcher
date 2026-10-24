package dev.household.tvsearch;

import dev.household.tvsearch.data.CatalogException;
import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.data.MetadataSearchService;
import dev.household.tvsearch.domain.MediaTitle;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;

public class SearchControllerTest {
    private final FakeCatalogService catalog = new FakeCatalogService();
    private final List<Runnable> pending = new ArrayList<>();
    private final List<SearchController.Status> observed = new ArrayList<>();
    private final Executor queued = pending::add;

    private SearchController controller(Executor executor) {
        return new SearchController(catalog, catalog, "GB", executor, Runnable::run,
                state -> observed.add(state.status));
    }

    @Test public void blankQueryNeverSchedulesARequest() {
        SearchController controller = controller(queued);
        controller.search("  ");
        assertEquals(SearchController.Status.EMPTY_QUERY, controller.state().status);
        assertTrue(pending.isEmpty());
    }

    @Test public void loadingThenResultsAreObservable() {
        SearchController controller = controller(queued);
        controller.search("Lantern");
        assertEquals(SearchController.Status.LOADING, controller.state().status);
        pending.remove(0).run();
        assertEquals(SearchController.Status.RESULTS, controller.state().status);
        assertEquals(2, controller.state().results.size());
        assertEquals("GB", controller.state().results.get(0).offers.get(0).region);
        assertEquals(java.util.Arrays.asList(SearchController.Status.LOADING, SearchController.Status.RESULTS), observed);
    }

    @Test public void noResultsHasItsOwnState() {
        SearchController controller = controller(Runnable::run);
        controller.search("not a fixture");
        assertEquals(SearchController.Status.NO_RESULTS, controller.state().status);
        assertNull(controller.state().error);
    }

    @Test public void failureKeepsQueryAndRecovers() {
        SearchController controller = controller(Runnable::run);
        catalog.setFailRequests(true);
        controller.search("Lantern");
        assertEquals(SearchController.Status.ERROR, controller.state().status);
        assertTrue(controller.state().error.contains("Retry"));
        catalog.setFailRequests(false);
        controller.search(controller.state().query);
        assertEquals(SearchController.Status.RESULTS, controller.state().status);
    }

    @Test public void olderResponseCannotReplaceNewerResults() {
        SearchController controller = controller(queued);
        controller.search("Lantern");
        controller.search("Orbit");
        pending.get(1).run();
        pending.get(0).run();
        assertEquals("Orbit", controller.state().query);
        assertEquals("Orbit Tea House", controller.state().results.get(0).title.title);
    }

    @Test public void clearingQueryInvalidatesPendingResults() {
        SearchController controller = controller(queued);
        controller.search("Lantern");
        controller.search("");
        pending.get(0).run();
        assertEquals(SearchController.Status.EMPTY_QUERY, controller.state().status);
    }

    @Test public void closedControllerDoesNotPublishOrSchedule() {
        SearchController controller = controller(queued);
        controller.search("Lantern");
        controller.close();
        pending.get(0).run();
        controller.search("Orbit");
        assertEquals(1, observed.size());
        assertEquals(1, pending.size());
    }

    @Test public void availabilityFailureIsNotMisrepresentedAsNoProviders() {
        SearchController controller = new SearchController(catalog,
                (id, type, region) -> { throw new CatalogException("Availability failed; retry"); },
                "US", Runnable::run, Runnable::run, state -> {});
        controller.search("Lantern");
        assertEquals(SearchController.Status.ERROR, controller.state().status);
        assertEquals("Availability failed; retry", controller.state().error);
    }

    @Test public void blankQueryDoesNotReachMetadataService() {
        MetadataSearchService service = new MetadataSearchService() {
            @Override public List<MediaTitle> search(String query) {
                throw new AssertionError("Blank query reached service");
            }
            @Override public MediaTitle getTitleDetails(String id, MediaTitle.Type type) {
                throw new AssertionError("Unexpected details request");
            }
        };
        SearchController controller = new SearchController(service, catalog, "US",
                Runnable::run, Runnable::run, state -> {});
        controller.search(null);
        assertEquals(SearchController.Status.EMPTY_QUERY, controller.state().status);
    }
}
