package com.gathin.tvhome;

import org.junit.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The list chips of Movies and TV Shows against design_preview/src/screens/library.ts (inList and the chip counts),
 * from the fixture LibraryFiltersTest reads, and My list following the household stored on the TV.
 */
public class LibraryListsTest {

    static LibraryLists.Choice choice(String s) {
        switch (s) {
            case "my":
                return LibraryLists.Choice.MY;
            case "household":
                return LibraryLists.Choice.HOUSEHOLD;
            case "friends":
                return LibraryLists.Choice.FRIENDS;
            default:
                return LibraryLists.Choice.ALL;
        }
    }

    private final MockCatalog catalog = new MockCatalog();
    private final HouseholdState household = HouseholdState.seed(catalog);
    private final Predicate<String> my = household::inMyList;

    private List<Title> under(Kind kind, LibraryLists.Choice c) {
        return LibraryLists.under(LibraryLists.pool(catalog.all(), kind, my), c, my);
    }

    @Test
    public void countsAndTitlesMatchThePrototype() throws Exception {
        int lines = 0;
        for (String[] f : LibraryFiltersTest.fixture("list")) {
            List<Title> titles = under(LibraryFiltersTest.kind(f[1]), choice(f[2]));
            assertEquals(f[1] + " " + f[2], Integer.parseInt(f[3]), titles.size());
            assertEquals(f[1] + " " + f[2], f[4], LibraryFiltersTest.ids(titles));
            lines++;
        }
        assertEquals(8, lines);
    }

    @Test
    public void theFigmaCounts() {
        assertEquals(16, under(Kind.MOVIE, LibraryLists.Choice.ALL).size());
        assertEquals(6, under(Kind.MOVIE, LibraryLists.Choice.MY).size());
        assertEquals(6, under(Kind.MOVIE, LibraryLists.Choice.HOUSEHOLD).size());
        assertEquals(4, under(Kind.MOVIE, LibraryLists.Choice.FRIENDS).size());
        assertEquals(12, under(Kind.SHOW, LibraryLists.Choice.ALL).size());
        assertEquals(5, under(Kind.SHOW, LibraryLists.Choice.MY).size());
        assertEquals(4, under(Kind.SHOW, LibraryLists.Choice.HOUSEHOLD).size());
        assertEquals(3, under(Kind.SHOW, LibraryLists.Choice.FRIENDS).size());
    }

    @Test
    public void eachPageListsOnlyItsKind() {
        for (Kind k : Kind.values()) {
            for (Title t : LibraryLists.pool(catalog.all(), k, my)) assertEquals(t.id, k, t.kind);
        }
    }

    @Test
    public void aCatalogueTitleAddedToMyListJoinsMyListAndAllLists() {
        Title extra = null;
        for (Title t : catalog.all()) {
            if (t.kind == Kind.MOVIE && t.lists.isEmpty()) {
                extra = t;
                break;
            }
        }
        assertNotNull("the mock catalogue has a catalogue-only movie", extra);
        assertFalse(LibraryLists.inList(extra, LibraryLists.Choice.ALL, my));
        household.toggleMyList(extra.id);
        assertTrue(LibraryLists.inList(extra, LibraryLists.Choice.MY, my));
        assertEquals(17, under(Kind.MOVIE, LibraryLists.Choice.ALL).size());
        assertEquals(7, under(Kind.MOVIE, LibraryLists.Choice.MY).size());
        assertEquals(6, under(Kind.MOVIE, LibraryLists.Choice.HOUSEHOLD).size());
    }

    @Test
    public void removingATitleFromMyListTakesItOffAllListsUnlessImported() {
        // V2 Phase 3: My list is the TV's own (the TMDB watchlist when signed in); the mock catalogue's "My list"
        // flag no longer counts, so a household that starts empty doesn't see the mock picks under All lists.
        assertTrue(household.inMyList("arrival"));
        household.toggleMyList("arrival");
        assertEquals(5, under(Kind.MOVIE, LibraryLists.Choice.MY).size());
        assertEquals(15, under(Kind.MOVIE, LibraryLists.Choice.ALL).size());
    }

    @Test
    public void householdAndFriendsComeFromTheCatalogue() {
        household.toggleMyList("coda");
        for (Title t : under(Kind.MOVIE, LibraryLists.Choice.HOUSEHOLD)) assertTrue(t.isOn(ListId.HOUSEHOLD));
        for (Title t : under(Kind.MOVIE, LibraryLists.Choice.FRIENDS)) assertTrue(t.isOn(ListId.FRIENDS));
        assertTrue(LibraryLists.inList(catalog.get("coda"), LibraryLists.Choice.MY, my));
    }
}
