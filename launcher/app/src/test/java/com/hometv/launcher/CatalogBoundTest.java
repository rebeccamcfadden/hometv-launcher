package com.hometv.launcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The real catalogue forgets the least recently used titles beyond the household's (F15 P2-1). */
public class CatalogBoundTest {

    private static List<String> ids(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add("movie:" + i);
        return out;
    }

    @Test
    public void nothingGoesWhileWithinSpareAndSlack() {
        assertTrue(CatalogBound.victims(ids(15), id -> false, new HashMap<>(), 10, 5).isEmpty());
    }

    @Test
    public void pastTheSlackTheLeastRecentlyUsedGoDownToSpare() {
        List<String> all = ids(16);
        Map<String, Long> used = new HashMap<>();
        for (int i = 0; i < all.size(); i++) used.put(all.get(i), (long) (100 - i));
        // movie:15 was used longest ago, then movie:14 and so on.
        assertEquals(Arrays.asList("movie:15", "movie:14", "movie:13", "movie:12", "movie:11", "movie:10"),
                CatalogBound.victims(all, id -> false, used, 10, 5));
    }

    @Test
    public void neverUsedCountsAsOldestAndTiesKeepTheCatalogueOrder() {
        List<String> all = ids(17);
        Map<String, Long> used = new HashMap<>();
        for (int i = 0; i < 10; i++) used.put(all.get(i), 50L + i);
        assertEquals(Arrays.asList("movie:10", "movie:11", "movie:12", "movie:13", "movie:14", "movie:15", "movie:16"),
                CatalogBound.victims(all, id -> false, used, 10, 5));
    }

    @Test
    public void pinnedTitlesNeverGoAndDontCount() {
        List<String> all = ids(30);
        Set<String> household = new HashSet<>(all.subList(0, 20));
        assertTrue("20 pinned, 10 spare", CatalogBound.victims(all, household::contains, new HashMap<>(), 10, 0)
                .isEmpty());
        List<String> gone = CatalogBound.victims(all, household::contains, new HashMap<>(), 5, 0);
        assertEquals(5, gone.size());
        for (String id : gone) assertTrue(id, !household.contains(id));
    }

    @Test
    public void theRealBoundIsAFewHundred() {
        assertEquals(300, CatalogBound.SPARE);
        assertEquals(50, CatalogBound.SLACK);
    }
}
