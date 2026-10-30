package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The mock titles on TMDB (MockIds), what stays mock on them (MockOverlay), and the household's move to TMDB ids. */
public class MockIdsTest {

    private final MockCatalog mock = new MockCatalog();

    @Test
    public void everyMockTitleButOneIsMapped() {
        assertEquals(42, MockIds.all().size());
        for (Title t : mock.all()) {
            String id = MockIds.tmdbId(t.id);
            if (t.id.equals("marvel-one-shots")) {
                assertNull(id);
                continue;
            }
            assertNotNull(t.id, id);
            assertEquals(t.id + " kind", t.kind, TmdbApi.kindOf(id));
            MockIds.Entry e = MockIds.all().get(t.id);
            assertEquals(t.id + " year", t.year, e.year);
            assertEquals(t.id, MockIds.mockId(id));
        }
    }

    @Test
    public void tmdbIdsAreUniqueAndKnown() {
        Set<String> ids = new HashSet<>();
        for (MockIds.Entry e : MockIds.all().values()) assertTrue(e.id, ids.add(e.id));
        assertEquals("movie:329865", MockIds.tmdbId("arrival"));
        assertEquals("movie:693134", MockIds.tmdbId("dune"));
        assertEquals("tv:95396", MockIds.tmdbId("severance"));
        assertEquals("tv:185", MockIds.tmdbId("carnivale"));
        assertEquals("tv:75219", MockIds.tmdbId("911"));
    }

    @Test
    public void storedIdsMigrate() {
        assertEquals("movie:329865", MockIds.migrate("arrival"));
        assertEquals("tv:1399", MockIds.migrate("tv:1399"));
        assertNull(MockIds.migrate("marvel-one-shots"));
        assertNull(MockIds.migrate("nope"));
    }

    @Test
    public void theSeedMovesToTmdbIds() {
        Catalog tmdbIds = new Catalog() {
            @Override
            public java.util.List<Title> all() {
                return Collections.emptyList();
            }

            @Override
            public Title get(String id) {
                return null;
            }

            @Override
            public java.util.List<String> genres() {
                return Collections.emptyList();
            }

            @Override
            public String fromMockId(String mockId) {
                return MockIds.migrate(mockId);
            }
        };
        HouseholdState s = HouseholdState.defaults(tmdbIds);
        assertTrue(s.isSeen("movie:546554"));
        assertTrue(s.isFavorite("movie:329865"));
        assertEquals(Arrays.asList("movie:329865", "movie:693134", "movie:666277", "movie:776503", "movie:17431",
                "tv:95396"), s.viewed());
        HouseholdState stored = HouseholdState.fromJson("{\"myList\":[\"arrival\",\"marvel-one-shots\",\"tv:1399\"],"
                + "\"seen\":[\"dune\"],\"favorites\":[],\"recent\":[\"dune\"],\"viewed\":[\"coda\",\"movie:329865\","
                + "\"arrival\"]}", tmdbIds);
        assertEquals(Arrays.asList("movie:329865", "tv:1399"), stored.myListIds());
        assertTrue(stored.isSeen("movie:693134"));
        // Two ids for one title become one.
        assertEquals(Arrays.asList("movie:776503", "movie:329865"), stored.viewed());
        assertEquals(Collections.singletonList("dune"), stored.recent());
    }

    @Test
    public void overlayKeepsTheMockListsAndNextEpisodeButNotItsOffers() {
        MockOverlay overlay = new MockOverlay(mock);
        Title stub = new Title.Draft("tv:95396", Kind.SHOW, "Severance").build();
        Title on = overlay.apply(stub);
        Title m = mock.get("severance");
        // Offers are real since V2 Phase 2: TMDB's availability, not the mock's.
        assertTrue(on.offers.isEmpty());
        assertEquals(m.lists, on.lists);
        assertEquals(1, on.nextSeason);
        assertEquals(3, on.nextEpisode);
        assertSame(m, overlay.mock("tv:95396"));
        Title other = new Title.Draft("movie:1", Kind.MOVIE, "Not In The Mock").build();
        assertSame(other, overlay.apply(other));
    }

    @Test
    public void overlayDropsANextEpisodeTheRealShowHasNot() {
        MockOverlay overlay = new MockOverlay(mock);
        Title.Draft d = new Title.Draft("tv:65494", Kind.SHOW, "The Crown");
        // The mock's next episode is S3 E2; a show with two seasons can't have it.
        d.seasons = Arrays.asList(new Season(1, 10, Collections.emptyList()), new Season(2, 10, Collections.emptyList()));
        Title on = overlay.apply(d.build());
        assertFalse(on.hasNext());
        d.seasons = Arrays.asList(new Season(1, 10, Collections.emptyList()), new Season(2, 10, Collections.emptyList()),
                new Season(3, 10, Collections.emptyList()));
        assertTrue(overlay.apply(d.build()).hasNext());
    }
}
