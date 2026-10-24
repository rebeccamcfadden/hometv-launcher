package dev.household.tvsearch.data;

import dev.household.tvsearch.domain.MediaTitle;
import dev.household.tvsearch.domain.ProviderAvailability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Original fictional titles and offers. None describes actual streaming availability. */
public final class FakeCatalogService implements MetadataSearchService, AvailabilityService {
    public static final String CINEMA = "fixture:cinema";
    public static final String SERIES = "fixture:series";
    public static final String UNSUPPORTED = "fixture:unsupported";
    private final List<MediaTitle> titles;
    private volatile boolean failRequests;

    public FakeCatalogService() {
        titles = Collections.unmodifiableList(Arrays.asList(
                title("1", MediaTitle.Type.MOVIE, "The Lantern Journey", 2024, 8.2, 104,
                        "A cartographer follows a trail of floating lanterns to a village missing from every map.",
                        Arrays.asList("Adventure", "Fantasy")),
                title("1", MediaTitle.Type.TV, "The Lantern Journey", 2025, 7.8, null,
                        "Two lighthouse keepers investigate the letters delivered with each new tide.",
                        Arrays.asList("Mystery", "Drama")),
                title("2", MediaTitle.Type.MOVIE, "Orbit Tea House", 2023, 7.4, 92,
                        "The last cafe on a quiet space station prepares for an unexpected visitor.",
                        Arrays.asList("Science fiction", "Comedy")),
                title("3", MediaTitle.Type.TV, "Small Town Signals", 2022, 8.1, null,
                        "A community radio crew solves little mysteries between the weather reports.",
                        Arrays.asList("Comedy", "Mystery")),
                title("4", MediaTitle.Type.MOVIE, "The Extremely Long and Entirely Fictional Story of the Last Midnight Train",
                        null, null, null, null, Collections.emptyList()),
                title("5", MediaTitle.Type.TV, "Paper Mountains", null, null, null,
                        "An animator discovers that her paper landscapes change while she sleeps.",
                        Collections.singletonList("Fantasy"))
        ));
    }

    private static MediaTitle title(String id, MediaTitle.Type type, String name, Integer year,
                                    Double rating, Integer runtime, String description, List<String> genres) {
        return new MediaTitle("fixture", id, type, name, null, "1".equals(id) ? "fixture:lantern" : null, year, rating,
                rating == null ? null : "Demo / 10", runtime, description, genres);
    }

    public void setFailRequests(boolean failRequests) {
        this.failRequests = failRequests;
    }

    @Override public List<MediaTitle> search(String query) throws CatalogException {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return Collections.emptyList();
        checkFailure();
        List<MediaTitle> found = new ArrayList<>();
        for (MediaTitle title : titles) {
            if (title.title.toLowerCase(Locale.ROOT).contains(normalized)) found.add(title);
        }
        return Collections.unmodifiableList(found);
    }

    @Override public MediaTitle getTitleDetails(String contentId, MediaTitle.Type type) throws CatalogException {
        checkFailure();
        for (MediaTitle title : titles) {
            if (title.id.equals(contentId) && title.type == type) return title;
        }
        throw new CatalogException("This demo title is no longer in the catalog. Search again.");
    }

    @Override public List<ProviderAvailability> getAvailability(String contentId, MediaTitle.Type type, String region)
            throws CatalogException {
        MediaTitle title = getTitleDetails(contentId, type);
        List<ProviderAvailability> offers = new ArrayList<>();
        long checked = System.currentTimeMillis();
        offers.add(new ProviderAvailability(CINEMA, "Demo Cinema", ProviderAvailability.Access.SUBSCRIPTION,
                region, null, null, title.type == MediaTitle.Type.MOVIE ? title.id : null, checked));
        offers.add(new ProviderAvailability(SERIES, "Demo Series", ProviderAvailability.Access.FREE_WITH_ADS,
                region, null, null, null, checked));
        offers.add(new ProviderAvailability(CINEMA, "Demo Cinema", ProviderAvailability.Access.RENT,
                region, null, null, title.id, checked));
        offers.add(new ProviderAvailability(UNSUPPORTED, "Unsupported Demo", ProviderAvailability.Access.UNKNOWN,
                region, null, null, null, checked));
        return Collections.unmodifiableList(offers);
    }

    private void checkFailure() throws CatalogException {
        if (failRequests) throw new CatalogException(
                "Simulated network error. Turn off 'Simulate error' and choose Retry.");
    }
}
