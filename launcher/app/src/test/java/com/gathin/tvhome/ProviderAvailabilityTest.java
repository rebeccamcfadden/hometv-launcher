package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TMDB's watch providers for a title, per region, from recorded answers (Arrival, Interstellar, Breaking Bad). */
public class ProviderAvailabilityTest {

    private static List<String> ids(List<ProviderAvailability.Item> items) {
        List<String> out = new ArrayList<>();
        for (ProviderAvailability.Item i : items) out.add(i.toString());
        return out;
    }

    @Test
    public void arrivalInSwedenIsRentAndBuyOnly() throws JSONException {
        ProviderAvailability a = ProviderAvailability.parse(Fixtures.read("tmdb_wp_movie_329865.json"), "SE");
        assertEquals("SE", a.region);
        assertEquals("https://www.themoviedb.org/movie/329865-arrival/watch?locale=SE", a.link);
        assertTrue(a.of(ProviderAvailability.Type.FLATRATE).isEmpty());
        // In TMDB's display priority order: Apple TV Store 5, Viaplay 6, Rakuten 12, Blockbuster 13, SF Anytime 14,
        // Amazon Video 36.
        assertEquals(Arrays.asList("rent:2", "rent:76", "rent:35", "rent:423", "rent:426", "rent:10"),
                ids(a.of(ProviderAvailability.Type.RENT)));
        assertEquals("Apple TV Store", a.of(ProviderAvailability.Type.BUY).get(0).name);
    }

    @Test
    public void listsComeInFlatrateFreeAdsRentBuyOrder() throws JSONException {
        ProviderAvailability a = ProviderAvailability.parse(Fixtures.read("tmdb_wp_movie_157336.json"), "se");
        assertEquals(Arrays.asList("flatrate:119", "flatrate:76", "flatrate:497", "flatrate:1899", "flatrate:1825"),
                ids(a.of(ProviderAvailability.Type.FLATRATE)));
        assertEquals(ProviderAvailability.Type.FLATRATE, a.items.get(0).type);
        assertEquals(ProviderAvailability.Type.BUY, a.items.get(a.items.size() - 1).type);
    }

    @Test
    public void aShowWorksTheSame() throws JSONException {
        ProviderAvailability a = ProviderAvailability.parse(Fixtures.read("tmdb_wp_tv_1396.json"), "SE");
        assertEquals(Arrays.asList("flatrate:8"), ids(a.of(ProviderAvailability.Type.FLATRATE)));
        assertEquals(Arrays.asList("rent:426"), ids(a.of(ProviderAvailability.Type.RENT)));
    }

    @Test
    public void aRegionWithNothingIsEmpty() throws JSONException {
        ProviderAvailability a = ProviderAvailability.parse(Fixtures.read("tmdb_wp_movie_329865.json"), "XX");
        assertTrue(a.isEmpty());
        assertNull(a.link);
        assertTrue(ProviderAvailability.parse("{\"id\":1}", "SE").isEmpty());
    }

    @Test
    public void everyRegionAtOnce() throws JSONException {
        Map<String, ProviderAvailability> all = ProviderAvailability.parseAll(
                new JSONObject(Fixtures.read("tmdb_wp_movie_329865.json")));
        assertEquals(new java.util.HashSet<>(Arrays.asList("SE", "US", "GB")), all.keySet());
        assertTrue(!all.get("US").isEmpty());
    }

    @Test
    public void freeAndAdsListsAndBrokenEntries() throws JSONException {
        String json = "{\"id\":1,\"results\":{\"SE\":{"
                + "\"ads\":[{\"provider_id\":1944,\"provider_name\":\"TV4 Play\",\"display_priority\":37}],"
                + "\"free\":[{\"provider_id\":493,\"provider_name\":\"SVT Play\",\"display_priority\":16},"
                + "{\"provider_name\":\"no id\"},{\"provider_id\":493,\"provider_name\":\"SVT Play again\"},"
                + "{\"provider_id\":300,\"provider_name\":\"Pluto TV\"}]}}}";
        ProviderAvailability a = ProviderAvailability.parse(json, "SE");
        assertNull(a.link);
        // Free before ads; a duplicate and an entry without an id are skipped; no priority sorts last.
        assertEquals(Arrays.asList("free:493", "free:300", "ads:1944"), ids(a.items));
    }
}
