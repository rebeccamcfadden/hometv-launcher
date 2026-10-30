package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The mock catalogue (design_preview/src/data/catalog.ts): ids, artwork names, lists, genres, offers and seasons. */
public class MockCatalogTest {

    private final MockCatalog catalog = new MockCatalog();

    /** Gradle runs unit tests from the module directory, launcher/app. */
    private static final File ART_DIR = new File("src/main/res/drawable-nodpi");

    @Test
    public void hasEveryPrototypeTitle() {
        assertEquals(43, catalog.all().size());
        assertEquals("arrival", catalog.all().get(0).id);
        assertEquals("911-lone-star", catalog.all().get(42).id);
    }

    @Test
    public void idsAreUniqueAndFound() {
        Set<String> ids = new HashSet<>();
        for (Title t : catalog.all()) {
            assertTrue("duplicate id " + t.id, ids.add(t.id));
            assertSame(t, catalog.get(t.id));
        }
        assertNull(catalog.get("nope"));
        assertNull(catalog.get(null));
    }

    @Test
    public void mockArtIsNamesNotImages() {
        // The bundled art left the APK with the real catalogue: a mock title's art name shows the placeholder.
        for (Title t : catalog.all()) {
            assertFalse(t.id + " art " + t.art, TmdbApi.isImagePath(t.art));
            assertNull(t.id, TmdbApi.backdrop(t.art, 417));
        }
        File[] files = ART_DIR.listFiles((dir, name) -> name.startsWith("art_"));
        assertTrue(files == null || files.length == 0);
    }

    @Test
    public void listsMatchTheLibrary() {
        int movies = 0, shows = 0, my = 0, household = 0, friends = 0, catalogueOnly = 0;
        for (Title t : catalog.all()) {
            if (t.lists.isEmpty()) {
                catalogueOnly++;
                continue;
            }
            if (t.isMovie()) movies++;
            else shows++;
            if (t.isOn(ListId.MY)) my++;
            if (t.isOn(ListId.HOUSEHOLD)) household++;
            if (t.isOn(ListId.FRIENDS)) friends++;
        }
        assertEquals(16, movies);
        assertEquals(12, shows);
        assertEquals(15, catalogueOnly);
        assertEquals(28, my + household + friends);
        assertEquals(11, my);
        assertEquals(10, household);
        assertEquals(7, friends);
    }

    @Test
    public void genresAreSortedDistinctAndCoverEveryTitle() {
        TreeSet<String> all = new TreeSet<>();
        for (Title t : catalog.all()) {
            assertFalse(t.id + " has no genre", t.genres.isEmpty());
            all.addAll(t.genres);
        }
        assertEquals(new ArrayList<>(all), catalog.genres());
        assertEquals(18, catalog.genres().size());
        assertEquals("Action", catalog.genres().get(0));
        assertEquals("Western", catalog.genres().get(17));
    }

    @Test
    public void moviesAndShowsAreShapedRight() {
        for (Title t : catalog.all()) {
            if (t.isMovie()) {
                assertTrue(t.id, t.runtime > 0);
                assertTrue(t.id, t.seasons.isEmpty());
                assertFalse(t.id, t.hasNext());
                continue;
            }
            assertEquals(t.id, 0, t.runtime);
            assertFalse(t.id, t.seasons.isEmpty());
            assertTrue(t.id, t.hasNext());
            assertTrue(t.id + " next season", t.nextSeason <= t.seasons.size());
            assertTrue(t.id + " next episode", t.nextEpisode <= t.seasons.get(t.nextSeason - 1).episodes.size());
            for (int i = 0; i < t.seasons.size(); i++) {
                Season s = t.seasons.get(i);
                assertEquals(i + 1, s.n);
                for (int k = 0; k < s.episodes.size(); k++) assertEquals(k + 1, s.episodes.get(k).n);
            }
        }
    }

    @Test
    public void episodesAreNamedAndTimedAsInThePrototype() {
        Season s1 = catalog.get("severance").seasons.get(0);
        assertEquals(9, s1.episodes.size());
        assertEquals("Good News About Hell", s1.episodes.get(0).name);
        assertEquals("What’s for Dinner?", s1.episodes.get(7).name);
        // runtime + ((k * 7) % 9) - 4: 52 → 48, 55, 53, …
        assertEquals(48, s1.episodes.get(0).runtime);
        assertEquals(55, s1.episodes.get(1).runtime);
        assertEquals(53, s1.episodes.get(2).runtime);
        Season s2 = catalog.get("severance").seasons.get(1);
        assertEquals(10, s2.episodes.size());
        assertEquals("Episode 10", s2.episodes.get(9).name);
    }

    @Test
    public void offersArePricedOnlyWhenPaid() {
        for (Title t : catalog.all()) {
            assertFalse(t.id + " has no offer", t.offers.isEmpty());
            for (Offer o : t.offers) {
                boolean paid = o.type == OfferType.RENT || o.type == OfferType.BUY;
                assertEquals(t.id + " " + o, paid, o.price > 0);
                assertTrue(t.id + " " + o, o.quality != null);
            }
        }
        Title arrival = catalog.get("arrival");
        assertEquals(7, arrival.offers.size());
        assertEquals(12.99, arrival.offers.get(4).price, 1e-9);
    }

    @Test
    public void catalogueOnlyTitlesAreThereForSearch() {
        assertTrue(catalog.get("carnival-row").lists.isEmpty());
        assertTrue(catalog.get("marvel-one-shots").cast.isEmpty());
        assertEquals("Marvel’s Daredevil", catalog.get("daredevil").name);
        assertEquals("Shōgun", catalog.get("shogun").name);
    }

    @Test
    public void titlesAreImmutable() {
        List<String> genres = catalog.get("arrival").genres;
        try {
            genres.add("Horror");
            fail("genres can be changed");
        } catch (UnsupportedOperationException expected) {
            // immutable, as the contract says
        }
        try {
            catalog.all().clear();
            fail("the catalogue can be changed");
        } catch (UnsupportedOperationException expected) {
            // immutable
        }
    }
}
