package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** OfflineSearch: Search offline looks only at the titles saved on the TV, without people or genres (06-states A). */
public class OfflineSearchTest {
    private static final MockCatalog CATALOG = new MockCatalog();
    private static final SearchIndex INDEX = new SearchIndex(CATALOG);
    /** Nothing on My list; the mock Household and Friends, as before the TV has had an account. */
    private static final ListMembership MOCK_LISTS = id -> false;

    /** A title on no list, which only the household's own ids (My list, viewed…) can make saved. */
    private static Title unlisted() {
        for (Title t : CATALOG.all()) if (t.lists.isEmpty()) return t;
        throw new AssertionError("the mock catalogue has a title on no list");
    }

    @Test
    public void savedIdsAreTheHouseholdsThenTheListed() {
        Set<String> saved = OfflineSearch.savedIds(Collections.singletonList("viewed-one"), CATALOG.all(), MOCK_LISTS);
        assertEquals("viewed-one", saved.iterator().next());
        for (Title t : CATALOG.all()) {
            assertEquals(t.id, t.isOn(ListId.HOUSEHOLD) || t.isOn(ListId.FRIENDS), saved.contains(t.id));
        }
    }

    /** Signed in: the titles on My list or on any shown TMDB list; a hidden list's titles are not saved. */
    @Test
    public void savedIdsFollowTheShownLists() {
        ImportedLists imported = new ImportedLists();
        imported.set(Collections.singletonList(new ImportedLists.Entry(ListRef.parse("7"), 7, "Family picks",
                Collections.singletonList("moon"))));
        ListMembership account = new ListMembership() {
            @Override
            public boolean inMyList(String id) {
                return id.equals("arrival");
            }

            @Override
            public List<ShownList> shownLists() {
                return imported.shown();
            }

            @Override
            public boolean isOn(Title t, ShownList list) {
                return list.isMy() ? inMyList(t.id) : imported.isOn(t, list);
            }
        };
        Set<String> saved = OfflineSearch.savedIds(Collections.<String>emptyList(), CATALOG.all(), account);
        assertEquals(new HashSet<>(java.util.Arrays.asList("arrival", "moon")), saved);
    }

    @Test
    public void resultsKeepOnlySavedTitlesAndNoPeopleOrGenres() {
        SearchIndex.Results online = INDEX.search("moon");
        assertTrue(online.hasMatches());
        Set<String> saved = OfflineSearch.savedIds(Collections.emptyList(), CATALOG.all(), MOCK_LISTS);
        SearchIndex.Results r = OfflineSearch.filter(online, saved);
        assertFalse(r.titles.isEmpty());
        for (SearchIndex.Hit h : r.titles) assertTrue(h.title.name, saved.contains(h.title.id));
        assertTrue(r.people.isEmpty());
        assertTrue(r.genres.isEmpty());
        assertTrue(r.genreTitles.isEmpty());
        for (SearchIndex.Hit h : r.suggestions) {
            assertEquals(SearchIndex.HitKind.TITLE, h.kind);
            assertTrue(saved.contains(h.title.id));
        }
        // The order stays the online search's.
        int last = -1;
        for (SearchIndex.Hit h : r.titles) {
            int at = online.titles.indexOf(h);
            assertTrue(at > last);
            last = at;
        }
    }

    @Test
    public void peopleAreLeftOut() {
        Person someone = null;
        for (Title t : CATALOG.all()) if (!t.cast.isEmpty()) someone = t.cast.get(0);
        assertNotNull(someone);
        SearchIndex.Results online = INDEX.search(someone.name);
        assertFalse(online.people.isEmpty());
        Set<String> all = new HashSet<>();
        for (Title t : CATALOG.all()) all.add(t.id);
        assertTrue(OfflineSearch.filter(online, all).people.isEmpty());
    }

    @Test
    public void aTitleOnNoListIsFoundOnlyWhenTheHouseholdKeepsIt() {
        Title t = unlisted();
        SearchIndex.Results online = INDEX.search(t.name);
        Set<String> listed = OfflineSearch.savedIds(Collections.emptyList(), CATALOG.all(), MOCK_LISTS);
        assertFalse(contains(OfflineSearch.filter(online, listed).titles, t));
        Set<String> viewed = OfflineSearch.savedIds(Collections.singletonList(t.id), CATALOG.all(), MOCK_LISTS);
        assertTrue(contains(OfflineSearch.filter(online, viewed).titles, t));
    }

    @Test
    public void nothingSavedMatchesNothing() {
        SearchIndex.Results r = OfflineSearch.filter(INDEX.search("moon"), Collections.emptySet());
        assertFalse(r.hasMatches());
        assertTrue(r.suggestions.isEmpty());
    }

    private static boolean contains(List<SearchIndex.Hit> hits, Title t) {
        for (SearchIndex.Hit h : hits) if (h.title.id.equals(t.id)) return true;
        return false;
    }
}
