package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** F4: the Sort chip's orders, the article and accent rule of A–Z, and Recently added read from the lists. */
public class LibrarySortTest {

    private static Title t(String id, String name, int year, double score) {
        return new Title(id, Kind.MOVIE, name, year, null, 0, null, score, null, null, null, null, null, null, null, 0,
                0);
    }

    private static String ids(List<Title> titles) {
        return LibraryFiltersTest.ids(titles);
    }

    private static final List<Title> TITLES = Arrays.asList(
            t("holdovers", "The Holdovers", 2023, 7.9),
            t("arrival", "Arrival", 2016, 7.6),
            t("amelie", "Amélie", 2001, 7.9),
            t("quiet-place", "A Quiet Place", 2018, 7.4),
            t("apollo", "Apollo 13", 1995, 0),
            t("unknown", "An Unknown Film", 0, 6.0),
            t("500", "(500) Days of Summer", 2009, 0));

    @Test
    public void sortNameLeavesOutTheArticleAccentsCaseAndLeadingMarks() {
        assertEquals("holdovers", LibrarySort.sortName("The Holdovers"));
        assertEquals("quiet place", LibrarySort.sortName("A Quiet Place"));
        assertEquals("unknown film", LibrarySort.sortName("An Unknown Film"));
        assertEquals("amelie", LibrarySort.sortName("Amélie"));
        assertEquals("500) days of summer", LibrarySort.sortName("(500) Days of Summer"));
        assertEquals("theory of everything", LibrarySort.sortName("The Theory of Everything"));
        // Only a whole leading word: "Theodore", "Anatomy", "Avatar" keep theirs.
        assertEquals("theodore", LibrarySort.sortName("Theodore"));
        assertEquals("anatomy of a fall", LibrarySort.sortName("Anatomy of a Fall"));
        assertEquals("avatar", LibrarySort.sortName("Avatar"));
        // An article alone is the whole name.
        assertEquals("the", LibrarySort.sortName("The"));
        assertEquals("a", LibrarySort.sortName("A"));
        assertEquals("", LibrarySort.sortName(null));
        assertEquals("", LibrarySort.sortName(""));
    }

    @Test
    public void azIgnoresLeadingArticles() {
        assertSame(LibrarySort.AZ, LibrarySort.values()[0]);
        assertEquals("500,amelie,apollo,arrival,holdovers,quiet-place,unknown",
                ids(LibrarySort.AZ.sort(TITLES, null)));
    }

    @Test
    public void azBreaksTiesByFullNameThenId() {
        List<Title> same = Arrays.asList(t("b", "The Office", 2005, 0), t("a", "Office", 2001, 0),
                t("c", "The Office", 2001, 0));
        // "Office" before "The Office"; two "The Office" by id.
        assertEquals("a,b,c", ids(LibrarySort.AZ.sort(same, null)));
    }

    @Test
    public void newestAndOldestPutAMissingYearLastInAz() {
        assertEquals("holdovers,quiet-place,arrival,500,amelie,apollo,unknown",
                ids(LibrarySort.NEWEST.sort(TITLES, null)));
        assertEquals("apollo,amelie,500,arrival,quiet-place,holdovers,unknown",
                ids(LibrarySort.OLDEST.sort(TITLES, null)));
        List<Title> sameYear = Arrays.asList(t("z", "Zodiac", 2007, 0), t("n", "No Country for Old Men", 2007, 0),
                t("x", "Unknown X", 0, 0), t("b", "Unknown B", 0, 0));
        assertEquals("n,z,b,x", ids(LibrarySort.NEWEST.sort(sameYear, null)));
        assertEquals("n,z,b,x", ids(LibrarySort.OLDEST.sort(sameYear, null)));
    }

    @Test
    public void highestRatedPutsNoScoreLastAndTiesInAz() {
        // Amélie and The Holdovers tie at 7.9: A–Z between them.
        assertEquals("amelie,holdovers,arrival,quiet-place,unknown,500,apollo",
                ids(LibrarySort.RATED.sort(TITLES, null)));
        List<Title> odd = Arrays.asList(t("nan", "NaN", 2000, Double.NaN), t("neg", "Negative", 2000, -1),
                t("ok", "Okay", 2000, 5));
        assertEquals("ok,nan,neg", ids(LibrarySort.RATED.sort(odd, null)));
    }

    @Test
    public void recentlyAddedFollowsTheRanksAndPutsTheUnrankedLastInAz() {
        Map<String, Integer> ranks = new HashMap<>();
        ranks.put("arrival", 0);
        ranks.put("apollo", 1);
        ranks.put("holdovers", 2);
        assertEquals("arrival,apollo,holdovers,500,amelie,quiet-place,unknown",
                ids(LibrarySort.RECENT.sort(TITLES, ranks)));
        // No ranks at all: A–Z.
        assertEquals(ids(LibrarySort.AZ.sort(TITLES, null)), ids(LibrarySort.RECENT.sort(TITLES, null)));
        // The other orders ignore the ranks.
        assertEquals(ids(LibrarySort.NEWEST.sort(TITLES, null)), ids(LibrarySort.NEWEST.sort(TITLES, ranks)));
    }

    @Test
    public void sortMakesANewListAndLeavesTheGivenOneAlone() {
        List<Title> in = new ArrayList<>(TITLES);
        List<Title> out = LibrarySort.AZ.sort(in, null);
        assertNotSame(in, out);
        assertEquals(ids(TITLES), ids(in));
        assertEquals(0, LibrarySort.AZ.sort(Collections.<Title>emptyList(), null).size());
    }

    @Test
    public void recentRanksReadEachListFromItsEnd() {
        // Each list oldest first; the chips' order: My list, then Family.
        List<String> my = Arrays.asList("a", "b", "c");
        List<String> family = Arrays.asList("x", "b", "y", "z");
        Map<String, Integer> r = LibrarySort.recentRanks(Arrays.asList(my, family));
        // The lists' last titles first (c, then z), then their second-to-last (b on My list, y), and so on; b takes
        // its best place.
        List<String> order = new ArrayList<>(r.keySet());
        Collections.sort(order, (p, q) -> Integer.compare(r.get(p), r.get(q)));
        assertEquals(Arrays.asList("c", "z", "b", "y", "a", "x"), order);
        assertEquals(Integer.valueOf(0), r.get("c"));
        assertEquals(6, r.size());
    }

    @Test
    public void recentRanksSkipNullsAndASingleListIsItsOwnOrderReversed() {
        Map<String, Integer> r = LibrarySort.recentRanks(Arrays.asList(null, Arrays.asList("a", null, "b")));
        assertEquals(Integer.valueOf(0), r.get("b"));
        assertEquals(Integer.valueOf(1), r.get("a"));
        assertEquals(2, r.size());
        assertEquals(0, LibrarySort.recentRanks(Collections.<List<String>>emptyList()).size());
    }

    @Test
    public void recentlyAddedOnAPageReadsMyListsOrderAndTheMockListsInCatalogueOrder() {
        MockCatalog catalog = new MockCatalog();
        HouseholdState household = HouseholdState.seed(catalog);
        List<String> mine = household.myListIds();
        ListMembership lists = new ListMembership() {
            @Override
            public boolean inMyList(String id) {
                return household.inMyList(id);
            }

            @Override
            public List<String> order(ShownList list, List<Title> pool) {
                return list.isMy() ? mine : ListMembership.super.order(list, pool);
            }
        };
        List<Title> pool = LibraryLists.pool(catalog.all(), Kind.MOVIE, lists);
        // My list alone: the title added last first.
        List<Title> onMy = LibraryLists.under(pool, ShownList.MY, lists);
        List<Title> sorted = LibrarySort.RECENT.sort(onMy, LibraryLists.recentRanks(pool, ShownList.MY, lists));
        List<String> expected = new ArrayList<>();
        for (String id : mine) for (Title t : onMy) if (t.id.equals(id)) expected.add(0, id);
        assertEquals(String.join(",", expected), ids(sorted));
        // All lists: every title on a list gets a rank.
        Map<String, Integer> all = LibraryLists.recentRanks(pool, ShownList.ALL, lists);
        for (Title t : pool) assertTrue(t.id, all.containsKey(t.id));
    }
}
