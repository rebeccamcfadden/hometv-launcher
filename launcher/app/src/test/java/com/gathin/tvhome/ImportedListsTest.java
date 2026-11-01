package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The lists after My list (docs/rework.md, Lists by name, 2026-10-31): the mock Household and Friends until the TV has
 * an account, then the shown TMDB lists by name, the chips reading them, and the copies kept for offline.
 */
public class ImportedListsTest {
    private final MockCatalog catalog = new MockCatalog();

    private static final ShownList HOUSEHOLD = ShownList.MOCK.get(0);
    private static final ShownList FRIENDS = ShownList.MOCK.get(1);

    private Title mockOn(ListId list) {
        for (Title t : catalog.all()) if (t.isOn(list)) return t;
        throw new AssertionError("the mock catalogue has a title on " + list);
    }

    private static ImportedLists.Entry entry(long id, String name, String... ids) {
        return new ImportedLists.Entry(ListRef.parse(Long.toString(id)), id, name, Arrays.asList(ids));
    }

    /** A household with nothing on My list over these lists. */
    private static ListMembership over(ImportedLists l) {
        return new ListMembership() {
            @Override
            public boolean inMyList(String id) {
                return false;
            }

            @Override
            public List<ShownList> shownLists() {
                return l.shown();
            }

            @Override
            public boolean isOn(Title t, ShownList list) {
                return list.isMy() ? inMyList(t.id) : l.isOn(t, list);
            }
        };
    }

    @Test
    public void startsWithTheMockLists() {
        ImportedLists l = new ImportedLists();
        assertTrue(l.mock());
        assertEquals(ShownList.MOCK, l.shown());
        assertEquals(Arrays.asList("household", "friends"), Arrays.asList(l.shown().get(0).key, l.shown().get(1).key));
        assertEquals("Household", l.shown().get(0).name);
        assertTrue(l.isOn(mockOn(ListId.HOUSEHOLD), HOUSEHOLD));
        assertTrue(l.isOn(mockOn(ListId.FRIENDS), FRIENDS));
        assertTrue(l.allIds().isEmpty());
    }

    @Test
    public void shownListsReplaceTheMockOnesByName() {
        ImportedLists l = new ImportedLists();
        Title mock = mockOn(ListId.HOUSEHOLD);
        assertTrue(l.set(Arrays.asList(entry(7, "Family picks", mock.id, "movie:1", mock.id), entry(9, "Friends’ tips"))));
        assertFalse(l.mock());
        assertFalse("the mock lists are gone", l.isOn(mock, HOUSEHOLD));
        assertEquals(2, l.shown().size());
        ShownList family = l.shown().get(0);
        assertEquals("tmdb-7", family.key);
        assertEquals("Family picks", family.name);
        assertEquals(7, family.tmdbId);
        assertEquals("Friends’ tips", l.shown().get(1).name);
        assertTrue(l.isOn(mock, family));
        assertFalse(l.isOn(mock, l.shown().get(1)));
        assertEquals(Arrays.asList(mock.id, "movie:1"), l.entries().get(0).ids);
        assertFalse("the same again changes nothing", l.set(Arrays.asList(entry(7, "Family picks", mock.id, "movie:1"),
                entry(9, "Friends’ tips"))));
        assertTrue(l.setMock());
        assertTrue(l.isOn(mock, HOUSEHOLD));
        assertFalse(l.setMock());
    }

    @Test
    public void noneShownLeavesOnlyMyList() {
        ImportedLists l = new ImportedLists();
        assertTrue(l.set(Collections.<ImportedLists.Entry>emptyList()));
        assertTrue(l.shown().isEmpty());
        assertFalse(l.isOn(mockOn(ListId.HOUSEHOLD), HOUSEHOLD));
        assertEquals(Collections.singletonList(ShownList.MY), over(l).lists());
    }

    @Test
    public void aListSetTwiceShowsOnce() {
        ImportedLists l = new ImportedLists();
        l.set(Arrays.asList(entry(7, "Family picks", "movie:1"),
                new ImportedLists.Entry(ListRef.parse("Family picks"), 7, "Family picks", Arrays.asList("movie:1"))));
        assertEquals(1, l.shown().size());
    }

    @Test
    public void aNameNotFoundYetHasAStableKey() {
        ShownList byName = new ImportedLists.Entry(ListRef.parse(" Our Picks "), 0, "Our Picks", null).shown();
        assertEquals("name-our picks", byName.key);
        assertEquals("Our Picks", byName.name);
        assertEquals("tmdb-8221116", new ImportedLists.Entry(ListRef.parse("Our picks"), 8221116, "Our picks", null)
                .shown().key);
    }

    @Test
    public void chipsReadTheMembership() {
        ImportedLists l = new ImportedLists();
        Title a = catalog.get("arrival");
        Title moon = catalog.get("moon");
        l.set(Arrays.asList(entry(7, "Family picks", a.id), entry(9, "Weekend movies")));
        ListMembership m = over(l);
        List<Title> movies = LibraryLists.pool(catalog.all(), Kind.MOVIE, m);
        assertEquals(Collections.singletonList(a), movies);
        assertEquals(Collections.singletonList(a), LibraryLists.under(movies, l.shown().get(0), m));
        assertTrue(LibraryLists.under(movies, l.shown().get(1), m).isEmpty());
        assertFalse("moon is on the mock Household only", LibraryLists.inList(moon, ShownList.ALL, m));
    }

    @Test
    public void allListsFollowsMyListNotTheMockMyFlag() {
        // Start empty: a title on the mock "My list" is not on All lists unless the household put it on My list.
        Title mine = mockOn(ListId.MY);
        ListMembership empty = id -> false;
        boolean imported = mine.isOn(ListId.HOUSEHOLD) || mine.isOn(ListId.FRIENDS);
        assertEquals(imported, LibraryLists.inList(mine, ShownList.ALL, empty));
        ListMembership withIt = id -> id.equals(mine.id);
        assertTrue(LibraryLists.inList(mine, ShownList.ALL, withIt));
        assertTrue(LibraryLists.inList(mine, ShownList.MY, withIt));
    }

    // ---- The copies kept for offline ---------------------------------------------------------------------------

    @Test
    public void copiesRoundTripInTheSettingsOrder() {
        List<ImportedLists.Entry> lists = Arrays.asList(entry(7, "Family picks", "movie:1", "tv:2"),
                new ImportedLists.Entry(ListRef.parse("Weekend"), 12, "Weekend", Collections.singletonList("movie:3")));
        String json = ImportedLists.save(lists);
        List<ListRef> refs = Arrays.asList(ListRef.parse("Weekend"), ListRef.parse("7"));
        List<ImportedLists.Entry> back = ImportedLists.restore(refs, json, id -> "List " + id);
        assertEquals(2, back.size());
        assertEquals("Weekend", back.get(0).name);
        assertEquals("a name found once keeps its number", 12, back.get(0).id);
        assertEquals(Collections.singletonList("movie:3"), back.get(0).ids);
        assertEquals(lists.get(0), back.get(1));
    }

    @Test
    public void aListNotReadYetIsEmptyAndNamedBySetting() {
        List<ImportedLists.Entry> back = ImportedLists.restore(Arrays.asList(ListRef.parse("8221116"),
                ListRef.parse("Our picks")), null, id -> "List " + id);
        assertEquals("List 8221116", back.get(0).name);
        assertTrue(back.get(0).ids.isEmpty());
        assertEquals("Our picks", back.get(1).name);
        assertEquals(0, back.get(1).id);
        assertTrue(ImportedLists.restore(Collections.singletonList(ListRef.parse("7")), "not json", id -> "")
                .get(0).ids.isEmpty());
    }

    @Test
    public void readsTheCopiesSavedForHouseholdAndFriends() {
        // Before the lists had names, the copies were kept under HOUSEHOLD and FRIENDS with the setting as "ref".
        String old = "{\"HOUSEHOLD\":{\"ref\":\"list 50\",\"ids\":[\"movie:10\",\"tv:11\"]},"
                + "\"FRIENDS\":{\"ref\":\"\\\"Friends\\\"\",\"ids\":[\"tv:12\"]}}";
        List<ImportedLists.Entry> back = ImportedLists.restore(Arrays.asList(ListRef.parse("50"),
                ListRef.parse("Friends")), old, id -> "List " + id);
        assertEquals(Arrays.asList("movie:10", "tv:11"), back.get(0).ids);
        assertEquals("List 50", back.get(0).name);
        assertEquals(Collections.singletonList("tv:12"), back.get(1).ids);
        assertEquals("Friends", back.get(1).name);
    }
}
