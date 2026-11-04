package com.hometv.launcher;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Search past TMDB's first 20 results: which page comes next, when to ask, and how pages merge. */
public class SearchPagesTest {

    private static Title title(String id) {
        return new Title.Draft(id, id.startsWith("tv:") ? Kind.SHOW : Kind.MOVIE, id).build();
    }

    private static Catalog.Found found(int page, int total, List<String> titles, List<Integer> people) {
        Map<Integer, List<Title>> known = new HashMap<>();
        java.util.ArrayList<Title> ts = new java.util.ArrayList<>();
        for (String t : titles) ts.add(title(t));
        java.util.ArrayList<Person> ps = new java.util.ArrayList<>();
        for (int p : people) {
            ps.add(new Person("P" + p, "Acting", p, "/p.jpg"));
            known.put(p, Collections.singletonList(title("known-" + p + "-" + page)));
        }
        return new Catalog.Found(ts, ps, known, page, total);
    }

    @Test
    public void theNextPageUntilTheLastOrTheCap() {
        assertEquals(2, SearchPages.next(found(1, 3, Collections.emptyList(), Collections.emptyList())));
        assertEquals(3, SearchPages.next(found(2, 3, Collections.emptyList(), Collections.emptyList())));
        assertEquals(0, SearchPages.next(found(3, 3, Collections.emptyList(), Collections.emptyList())));
        assertEquals(0, SearchPages.next(found(1, 1, Collections.emptyList(), Collections.emptyList())));
        assertEquals(0, SearchPages.next(found(SearchPages.MAX_PAGES, 40, Collections.emptyList(), Collections.emptyList())));
        assertEquals(0, SearchPages.next(null));
    }

    @Test
    public void theLastRow() {
        // 14 cards in rows of 6: the last row is cards 12 and 13.
        assertTrue(SearchPages.onLastRow(12, 14, 6));
        assertTrue(SearchPages.onLastRow(13, 14, 6));
        assertFalse(SearchPages.onLastRow(11, 14, 6));
        // A full last row.
        assertTrue(SearchPages.onLastRow(6, 12, 6));
        assertFalse(SearchPages.onLastRow(5, 12, 6));
        assertTrue(SearchPages.onLastRow(0, 3, 6));
        assertFalse(SearchPages.onLastRow(-1, 3, 6));
        assertFalse(SearchPages.onLastRow(3, 3, 6));
    }

    @Test
    public void theSameCardAsksAgainOnlyAfterFocusLeftTheLastRow() {
        // A redraw puts focus back on the card that asked: it keeps the guard and doesn't ask.
        String g = SearchPages.guard("r-title-movie:1", true);
        assertFalse(SearchPages.asks(g, "r-title-movie:1", false));
        // Another last-row card asks.
        assertTrue(SearchPages.asks(g, "r-title-movie:2", false));
        // Focus went up a row (or to a filter chip) and came back: the guard is gone, the same card asks again.
        g = SearchPages.guard(g, false);
        assertTrue(SearchPages.asks(g, "r-title-movie:1", false));
        // While a page is on its way, askMorePages' own retry rule decides.
        assertTrue(SearchPages.asks("r-title-movie:1", "r-title-movie:1", true));
    }

    @Test
    public void askingOnItsOwn() {
        Catalog.Found more = found(1, 3, Collections.emptyList(), Collections.emptyList());
        // Nothing shows (the art filter, or a filter chip): ask.
        assertTrue(SearchPages.askOnItsOwn(0, 0, more));
        // The page just merged showed nothing new: ask.
        assertTrue(SearchPages.askOnItsOwn(7, 7, more));
        // It showed new cards: wait for focus to reach the new last row.
        assertFalse(SearchPages.askOnItsOwn(7, 12, more));
        // No page left, or the cap reached: never.
        assertFalse(SearchPages.askOnItsOwn(0, 0, found(3, 3, Collections.emptyList(), Collections.emptyList())));
        assertFalse(SearchPages.askOnItsOwn(0, 0,
                found(SearchPages.MAX_PAGES, 40, Collections.emptyList(), Collections.emptyList())));
        assertFalse(SearchPages.askOnItsOwn(0, 0, null));
    }

    @Test
    public void pagesMergeInOrderWithoutRepeats() {
        Catalog.Found one = found(1, 4, Arrays.asList("movie:1", "movie:2"), Collections.singletonList(7));
        Catalog.Found two = found(2, 4, Arrays.asList("movie:2", "tv:3"), Arrays.asList(7, 8));
        Catalog.Found both = Catalog.Found.merge(one, two);
        assertEquals(Arrays.asList("movie:1", "movie:2", "tv:3"), ids(both.titles));
        assertEquals(2, both.people.size());
        assertEquals(7, both.people.get(0).id);
        assertEquals(8, both.people.get(1).id);
        // A person found on both pages keeps page one's known-for; one new on page two brings theirs.
        assertEquals("known-7-1", both.knownFor(both.people.get(0)).get(0).id);
        assertEquals("known-8-2", both.knownFor(both.people.get(1)).get(0).id);
        assertEquals(2, both.page);
        assertEquals(4, both.totalPages);
        assertEquals(3, SearchPages.next(both));
    }

    @Test
    public void tmdbsPageAndTotalAreRead() throws Exception {
        Catalog.Found f = TmdbParse.search(new JSONObject("{\"page\":2,\"total_pages\":7,\"results\":[]}"));
        assertEquals(2, f.page);
        assertEquals(7, f.totalPages);
        Catalog.Found none = TmdbParse.search(new JSONObject("{\"results\":[]}"));
        assertEquals(1, none.page);
        assertEquals(1, none.totalPages);
    }

    @Test
    public void theRequestAsksForThePage() {
        assertTrue(TmdbApi.search("dune", 3).endsWith("&page=3"));
        assertEquals(TmdbApi.search("dune", 1), TmdbApi.search("dune"));
        assertTrue(TmdbApi.search("dune", 0).endsWith("&page=1"));
    }

    private static List<String> ids(List<Title> ts) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Title t : ts) out.add(t.id);
        return out;
    }

    @Test
    public void theTypingPreviewBuildsOnlyWhatShows() {
        // F46: two rows of seven beside the keyboard; the full results build all.
        assertEquals(14, SearchPages.built(124, false));
        assertEquals(5, SearchPages.built(5, false));
        assertEquals(124, SearchPages.built(124, true));
        // Art on the six whole cards of each preview row; the seventh is a sliver at the TV's edge.
        assertTrue(SearchPages.withArt(0, 7, false));
        assertTrue(SearchPages.withArt(5, 7, false));
        assertFalse(SearchPages.withArt(6, 7, false));
        assertTrue(SearchPages.withArt(7, 7, false));
        assertFalse(SearchPages.withArt(13, 7, false));
        assertTrue(SearchPages.withArt(7, 8, true));
    }
}