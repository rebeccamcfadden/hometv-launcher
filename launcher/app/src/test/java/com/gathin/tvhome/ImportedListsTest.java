package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Household and Friends from TMDB lists, the mock lists while none is set, and the list chips reading them. */
public class ImportedListsTest {
    private final MockCatalog catalog = new MockCatalog();

    private Title mockOn(ListId list) {
        for (Title t : catalog.all()) if (t.isOn(list)) return t;
        throw new AssertionError("the mock catalogue has a title on " + list);
    }

    @Test
    public void unsetListsStayMock() {
        ImportedLists l = new ImportedLists();
        Title t = mockOn(ListId.HOUSEHOLD);
        assertFalse(l.fromTmdb(ListId.HOUSEHOLD));
        assertTrue(l.isOn(t, ListId.HOUSEHOLD));
        assertTrue(l.ids(ListId.HOUSEHOLD).isEmpty());
    }

    @Test
    public void aSetListReplacesTheMockOne() {
        ImportedLists l = new ImportedLists();
        Title mock = mockOn(ListId.HOUSEHOLD);
        l.set(ListId.HOUSEHOLD, null);
        assertTrue(l.fromTmdb(ListId.HOUSEHOLD));
        assertFalse("nothing read yet: empty, not mock", l.isOn(mock, ListId.HOUSEHOLD));
        l.set(ListId.HOUSEHOLD, Arrays.asList(mock.id, "movie:1", mock.id));
        assertTrue(l.isOn(mock, ListId.HOUSEHOLD));
        assertEquals(Arrays.asList(mock.id, "movie:1"), l.ids(ListId.HOUSEHOLD));
        Title friends = mockOn(ListId.FRIENDS);
        assertTrue("Friends is still mock", l.isOn(friends, ListId.FRIENDS));
        l.set(ListId.FRIENDS, Collections.singletonList("tv:9"));
        assertEquals(Arrays.asList(mock.id, "movie:1", "tv:9"), l.allIds().stream().collect(java.util.stream.Collectors.toList()));
        l.clear(ListId.HOUSEHOLD);
        assertFalse(l.fromTmdb(ListId.HOUSEHOLD));
    }

    @Test(expected = IllegalArgumentException.class)
    public void myListIsNotImported() {
        new ImportedLists().set(ListId.MY, Collections.<String>emptyList());
    }

    @Test
    public void chipsReadTheMembership() {
        ImportedLists l = new ImportedLists();
        Title a = catalog.get("arrival");
        Title moon = catalog.get("moon");
        l.set(ListId.HOUSEHOLD, Collections.singletonList(a.id));
        l.set(ListId.FRIENDS, Collections.<String>emptyList());
        ListMembership m = new ListMembership() {
            @Override
            public boolean inMyList(String id) {
                return false;
            }

            @Override
            public boolean isOn(Title t, ListId list) {
                return list == ListId.MY ? inMyList(t.id) : l.isOn(t, list);
            }
        };
        List<Title> movies = LibraryLists.pool(catalog.all(), Kind.MOVIE, m);
        assertEquals(Collections.singletonList(a), movies);
        assertEquals(Collections.singletonList(a), LibraryLists.under(movies, LibraryLists.Choice.HOUSEHOLD, m));
        assertTrue(LibraryLists.under(movies, LibraryLists.Choice.FRIENDS, m).isEmpty());
        assertFalse("moon is on the mock Household only", LibraryLists.inList(moon, LibraryLists.Choice.ALL, m));
    }

    @Test
    public void allListsFollowsMyListNotTheMockMyFlag() {
        // Start empty: a title on the mock "My list" is not on All lists unless the household put it on My list.
        Title mine = mockOn(ListId.MY);
        ListMembership empty = id -> false;
        boolean imported = mine.isOn(ListId.HOUSEHOLD) || mine.isOn(ListId.FRIENDS);
        assertEquals(imported, LibraryLists.inList(mine, LibraryLists.Choice.ALL, empty));
        ListMembership withIt = id -> id.equals(mine.id);
        assertTrue(LibraryLists.inList(mine, LibraryLists.Choice.ALL, withIt));
        assertTrue(LibraryLists.inList(mine, LibraryLists.Choice.MY, withIt));
    }
}
