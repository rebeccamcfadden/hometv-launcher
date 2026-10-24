package dev.household.tvsearch;

import dev.household.tvsearch.data.CatalogException;
import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.domain.MediaTitle;
import dev.household.tvsearch.domain.ProviderAvailability;
import dev.household.tvsearch.domain.Subscription;

import org.junit.Test;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CatalogTest {
    private final FakeCatalogService catalog = new FakeCatalogService();

    @Test public void moviesAndShowsNormalizeWithDifferentStableIds() throws Exception {
        List<MediaTitle> found = catalog.search("  LANTERN ");
        assertEquals(2, found.size());
        assertEquals(MediaTitle.Type.MOVIE, found.get(0).type);
        assertEquals(MediaTitle.Type.TV, found.get(1).type);
        assertEquals("fixture:movie:1", found.get(0).id);
        assertEquals("fixture:tv:1", found.get(1).id);
        assertEquals(Integer.valueOf(104), found.get(0).runtimeMinutes);
        assertNull(found.get(1).runtimeMinutes);
        assertEquals(found.get(0).id, catalog.search("Lantern").get(0).id);
        assertEquals(found.get(0), catalog.getTitleDetails(found.get(0).id, found.get(0).type));
    }

    @Test public void blankQueriesDoNotEvenTriggerTheSimulatedFailure() throws Exception {
        catalog.setFailRequests(true);
        assertTrue(catalog.search(null).isEmpty());
        assertTrue(catalog.search(" \t\n").isEmpty());
    }

    @Test public void noResultsIsNotAnError() throws Exception {
        assertTrue(catalog.search("not a title").isEmpty());
    }

    @Test public void errorCanBeRetried() throws Exception {
        catalog.setFailRequests(true);
        try {
            catalog.search("Lantern");
            fail("Expected simulated error");
        } catch (CatalogException e) {
            assertTrue(e.getMessage().contains("Simulated"));
        }
        catalog.setFailRequests(false);
        assertEquals(2, catalog.search("Lantern").size());
    }

    @Test public void missingMetadataHasSafePresentation() throws Exception {
        MediaTitle title = catalog.search("Train").get(0);
        assertNull(title.artwork);
        assertNull(title.year);
        assertNull(title.rating);
        assertNull(title.description);
        assertTrue(title.genres.isEmpty());
        assertEquals("Movie", TitlePresentation.metadata(title, "Movie", "TV show", "min"));
    }

    @Test public void moviePresentationContainsKnownMetadata() throws Exception {
        String text = TitlePresentation.metadata(catalog.search("Lantern").get(0), "Movie", "TV show", "min");
        assertTrue(text.contains("2024"));
        assertTrue(text.contains("104 min"));
        assertTrue(text.contains("Demo / 10"));
    }

    @Test public void originalFixtureArtworkIsOptional() throws Exception {
        assertEquals("fixture:lantern", catalog.search("Lantern").get(0).artwork);
        assertNull(catalog.search("Orbit").get(0).artwork);
    }

    @Test public void offersAreRegionAwareAndSeparateAccessTypes() throws Exception {
        MediaTitle title = catalog.search("Lantern").get(0);
        List<ProviderAvailability> offers = catalog.getAvailability(title.id, title.type, "GB");
        assertEquals(4, offers.size());
        assertEquals(ProviderAvailability.Access.SUBSCRIPTION, offers.get(0).access);
        assertEquals(ProviderAvailability.Access.FREE_WITH_ADS, offers.get(1).access);
        assertEquals(ProviderAvailability.Access.RENT, offers.get(2).access);
        assertEquals(ProviderAvailability.Access.UNKNOWN, offers.get(3).access);
        for (ProviderAvailability offer : offers) {
            assertEquals("GB", offer.region);
            assertTrue(offer.checkedAtMillis > 0);
            assertNull(offer.webUrl);
        }
        assertEquals("AU", catalog.getAvailability(title.id, title.type, "AU").get(0).region);
    }

    @Test public void subscriptionDoesNotTurnRentalOrAdsIntoIncluded() throws Exception {
        MediaTitle title = catalog.search("Lantern").get(0);
        List<ProviderAvailability> offers = catalog.getAvailability(title.id, title.type, "US");
        List<Subscription> subscriptions = Collections.singletonList(
                new Subscription(FakeCatalogService.CINEMA, null, "shared", true));
        assertTrue(Subscription.includes(offers.get(0), subscriptions));
        for (int i = 1; i < offers.size(); i++) assertFalse(Subscription.includes(offers.get(i), subscriptions));
        assertFalse(Subscription.includes(offers.get(0), Collections.emptyList()));
    }

    @Test public void disabledOrDifferentPlanSubscriptionDoesNotMatch() {
        ProviderAvailability offer = new ProviderAvailability("provider", "Provider",
                ProviderAvailability.Access.SUBSCRIPTION, "US", "extra-channel", null, null, 0);
        assertFalse(Subscription.includes(offer, Collections.singletonList(
                new Subscription("provider", null, "shared", true))));
        assertFalse(Subscription.includes(offer, Collections.singletonList(
                new Subscription("provider", "extra-channel", "shared", false))));
        assertTrue(Subscription.includes(offer, Collections.singletonList(
                new Subscription("provider", "extra-channel", "shared", true))));
    }

    @Test public void everyNonSubscriptionTypeStaysSeparate() {
        for (ProviderAvailability.Access access : ProviderAvailability.Access.values()) {
            ProviderAvailability offer = new ProviderAvailability("p", "P", access, "CA", null, null, null, 0);
            assertEquals(access == ProviderAvailability.Access.SUBSCRIPTION,
                    Subscription.includes(offer, Collections.singletonList(
                            new Subscription("p", null, "shared", true))));
        }
    }

    @Test(expected = CatalogException.class)
    public void mismatchedMediaTypeIsNotResolved() throws Exception {
        catalog.getTitleDetails("fixture:movie:1", MediaTitle.Type.TV);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void normalizedGenresCannotBeMutated() throws Exception {
        catalog.search("Lantern").get(0).genres.clear();
    }
}
