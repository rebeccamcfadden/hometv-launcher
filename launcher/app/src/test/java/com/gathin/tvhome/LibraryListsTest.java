package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    static final ShownList HOUSEHOLD = ShownList.MOCK.get(0);
    static final ShownList FRIENDS = ShownList.MOCK.get(1);

    static ShownList choice(String s) {
        switch (s) {
            case "my":
                return ShownList.MY;
            case "household":
                return HOUSEHOLD;
            case "friends":
                return FRIENDS;
            default:
                return ShownList.ALL;
        }
    }

    private final MockCatalog catalog = new MockCatalog();
    private final HouseholdState household = HouseholdState.seed(catalog);
    private final Predicate<String> my = household::inMyList;

    private List<Title> under(Kind kind, ShownList c) {
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
        assertEquals(16, under(Kind.MOVIE, ShownList.ALL).size());
        assertEquals(6, under(Kind.MOVIE, ShownList.MY).size());
        assertEquals(6, under(Kind.MOVIE, HOUSEHOLD).size());
        assertEquals(4, under(Kind.MOVIE, FRIENDS).size());
        assertEquals(12, under(Kind.SHOW, ShownList.ALL).size());
        assertEquals(5, under(Kind.SHOW, ShownList.MY).size());
        assertEquals(4, under(Kind.SHOW, HOUSEHOLD).size());
        assertEquals(3, under(Kind.SHOW, FRIENDS).size());
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
        assertFalse(LibraryLists.inList(extra, ShownList.ALL, my));
        household.toggleMyList(extra.id);
        assertTrue(LibraryLists.inList(extra, ShownList.MY, my));
        assertEquals(17, under(Kind.MOVIE, ShownList.ALL).size());
        assertEquals(7, under(Kind.MOVIE, ShownList.MY).size());
        assertEquals(6, under(Kind.MOVIE, HOUSEHOLD).size());
    }

    @Test
    public void removingATitleFromMyListTakesItOffAllListsUnlessImported() {
        // V2 Phase 3: My list is the TV's own (the TMDB watchlist when signed in); the mock catalogue's "My list"
        // flag no longer counts, so a household that starts empty doesn't see the mock picks under All lists.
        assertTrue(household.inMyList("arrival"));
        household.toggleMyList("arrival");
        assertEquals(5, under(Kind.MOVIE, ShownList.MY).size());
        assertEquals(15, under(Kind.MOVIE, ShownList.ALL).size());
    }

    @Test
    public void householdAndFriendsComeFromTheCatalogue() {
        household.toggleMyList("coda");
        for (Title t : under(Kind.MOVIE, HOUSEHOLD)) assertTrue(t.isOn(ListId.HOUSEHOLD));
        for (Title t : under(Kind.MOVIE, FRIENDS)) assertTrue(t.isOn(ListId.FRIENDS));
        assertTrue(LibraryLists.inList(catalog.get("coda"), ShownList.MY, my));
    }

    // ---- The chips as the account's lists make them (lists.ts) -----------------------------------------------------

    private static final String COMMA = "%1$s, %2$s", AND = "%1$s & %2$s", MORE = "%1$s, %2$s & %3$d more";

    /** A signed-in household: these TMDB lists shown, in this order, the first holding arrival. */
    private static ListMembership signedIn(ImportedLists.Entry... shown) {
        ImportedLists l = new ImportedLists();
        l.set(Arrays.asList(shown));
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

    private static ImportedLists.Entry tmdb(long id, String name, String... ids) {
        return new ImportedLists.Entry(ListRef.parse(Long.toString(id)), id, name, Arrays.asList(ids));
    }

    private static List<String> keys(List<ShownList> lists) {
        List<String> out = new ArrayList<>();
        for (ShownList l : lists) out.add(l.key);
        return out;
    }

    @Test
    public void chipsAreAllListsMyListThenTheShownListsByName() {
        assertEquals("before any sign-in: the mock lists", Arrays.asList("all", "my", "household", "friends"),
                keys(LibraryLists.choices(my::test)));
        ListMembership m = signedIn(tmdb(7, "Family picks", "arrival"), tmdb(9, "Friends’ tips"), tmdb(3, "Weekend"));
        List<ShownList> chips = LibraryLists.choices(m);
        assertEquals(Arrays.asList("all", "my", "tmdb-7", "tmdb-9", "tmdb-3"), keys(chips));
        assertEquals("Family picks", chips.get(2).name);
        assertEquals(Collections.singletonList(catalog.get("arrival")),
                LibraryLists.under(LibraryLists.pool(catalog.all(), Kind.MOVIE, m), chips.get(2), m));
        ListMembership ten = signedIn(tmdb(1, "a"), tmdb(2, "b"), tmdb(3, "c"), tmdb(4, "d"), tmdb(5, "e"),
                tmdb(6, "f"), tmdb(7, "g", "arrival"), tmdb(8, "h"), tmdb(9, "i"), tmdb(10, "j"));
        assertEquals("capped: All lists, My list and the first six, in the account's order",
                Arrays.asList("all", "my", "tmdb-1", "tmdb-2", "tmdb-3", "tmdb-4", "tmdb-5", "tmdb-6"),
                keys(LibraryLists.choices(ten)));
        assertEquals(4, ten.pastCap());
        assertEquals("a list past the cap is still in All lists", Collections.singletonList(catalog.get("arrival")),
                LibraryLists.pool(catalog.all(), Kind.MOVIE, ten));
        assertEquals("and still counts as a watchlist", 11, ten.lists().size());
        assertEquals("a chosen chip past the cap falls back to All lists",
                "all", LibraryLists.choice("tmdb-7", LibraryLists.choices(ten)).key);
        assertEquals(0, m.pastCap());
        assertEquals("none shown: All lists and My list", Arrays.asList("all", "my"), keys(LibraryLists.choices(signedIn())));
    }

    /** A chosen chip whose list is hidden or gone since falls back to All lists. */
    @Test
    public void aHiddenChosenChipFallsBackToAllLists() {
        ListMembership both = signedIn(tmdb(7, "Family picks"), tmdb(9, "Friends’ tips"));
        assertEquals("tmdb-9", LibraryLists.choice("tmdb-9", LibraryLists.choices(both)).key);
        ListMembership hidden = signedIn(tmdb(7, "Family picks"));
        assertTrue(LibraryLists.choice("tmdb-9", LibraryLists.choices(hidden)).isAll());
        assertTrue("signed in, the mock lists are gone", LibraryLists.choice("household",
                LibraryLists.choices(hidden)).isAll());
        assertEquals("my", LibraryLists.choice("my", LibraryLists.choices(signedIn())).key);
    }

    @Test
    public void theContextLineNamesTheLists() {
        assertEquals("My list", LibraryLists.names(Collections.singletonList("My list"), COMMA, AND, MORE));
        assertEquals("My list & Family picks", LibraryLists.names(Arrays.asList("My list", "Family picks"), COMMA,
                AND, MORE));
        assertEquals("My list, Household & Friends", LibraryLists.names(Arrays.asList("My list", "Household",
                "Friends"), COMMA, AND, MORE));
        assertEquals("My list, Family picks, Weekend & Friends’ tips", LibraryLists.names(Arrays.asList("My list",
                "Family picks", "Weekend", "Friends’ tips"), COMMA, AND, MORE));
        assertEquals("My list, Family picks & 4 more", LibraryLists.names(Arrays.asList("My list", "Family picks",
                "a", "b", "c", "d"), COMMA, AND, MORE));
        assertEquals("", LibraryLists.names(Collections.<String>emptyList(), COMMA, AND, MORE));
    }
}