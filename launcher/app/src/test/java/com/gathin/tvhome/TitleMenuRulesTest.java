package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The hold-OK title menu's rows and the lists menu behind them (design_preview/README.md, Title menu; F3). */
public class TitleMenuRulesTest {
    private static final String ID = "movie:329865";
    private static final Title T = new Title(ID, Kind.MOVIE, "Arrival", 2016, Collections.singletonList("Drama"), 116,
            "PG-13", 7.9, "", ID, null, Collections.<ListId>emptyList(), null, null, null, 0, 0);

    private static final ShownList PICKS = ShownList.tmdb(8221116, null, "Our picks");
    private static final ShownList TIPS = ShownList.tmdb(9001, null, "Friends' tips");
    /** Set by a name TMDB hasn't answered for yet: no number, so nothing can be put on it. */
    private static final ShownList UNFOUND = ShownList.tmdb(0, "Later", "Later");

    /** A household with My list and the shown lists given, each holding the ids in {@code on} by list key. */
    private static ListMembership household(boolean myList, List<ShownList> shown, String... onKeys) {
        Set<String> on = new HashSet<>(Arrays.asList(onKeys));
        return new ListMembership() {
            @Override
            public boolean inMyList(String id) {
                return myList && ID.equals(id);
            }

            @Override
            public List<ShownList> shownLists() {
                return shown;
            }

            @Override
            public boolean isOn(Title t, ShownList list) {
                return list.isMy() ? inMyList(t.id) : on.contains(list.key);
            }
        };
    }

    private static List<String> labels(List<TitleMenuRules.Row> rows) {
        List<String> out = new ArrayList<>();
        for (TitleMenuRules.Row r : rows) out.add(r.label);
        return out;
    }

    private static List<String> keys(List<TitleMenuRules.ListRow> rows) {
        List<String> out = new ArrayList<>();
        for (TitleMenuRules.ListRow r : rows) out.add(r.list.key);
        return out;
    }

    private static List<TitleMenuRules.Action> actions(List<TitleMenuRules.Row> rows) {
        List<TitleMenuRules.Action> out = new ArrayList<>();
        for (TitleMenuRules.Row r : rows) out.add(r.action);
        return out;
    }

    @Test
    public void signedOutTheMenuIsSeenFavoriteMyListAndMoreLikeThis() {
        // Signed out the lists menu is My list alone, so the lists row puts the title on My list itself.
        List<TitleMenuRules.ListRow> lists = TitleMenuRules.lists(household(false, Arrays.asList(PICKS, TIPS)), T,
                false);
        List<TitleMenuRules.Row> rows = TitleMenuRules.rows(false, false, lists);
        assertEquals(Arrays.asList(TitleMenuRules.Action.SEEN, TitleMenuRules.Action.FAVORITE,
                TitleMenuRules.Action.MY_LIST, TitleMenuRules.Action.MORE_LIKE), actions(rows));
        assertEquals(Arrays.asList("Mark seen", "Favorite", "Add to My list", "More like this"), labels(rows));
        for (TitleMenuRules.Row r : rows) {
            assertFalse(r.on);
            assertNull(r.note);
        }
    }

    @Test
    public void signedOutLabelsFollowTheState() {
        List<TitleMenuRules.ListRow> lists = TitleMenuRules.lists(household(true, Collections.<ShownList>emptyList()),
                T, false);
        List<TitleMenuRules.Row> rows = TitleMenuRules.rows(true, true, lists);
        assertEquals(Arrays.asList("Mark unseen", "Unfavorite", "Remove from My list", "More like this"),
                labels(rows));
        assertTrue(rows.get(0).on);
        assertTrue(rows.get(1).on);
        assertTrue(rows.get(2).on);
        assertFalse(rows.get(3).on);
    }

    @Test
    public void signedInWithListsTheListsRowOpensTheListsMenu() {
        ListMembership m = household(false, Arrays.asList(PICKS, TIPS));
        List<TitleMenuRules.Row> rows = TitleMenuRules.rows(false, false, TitleMenuRules.lists(m, T, true));
        assertEquals(Arrays.asList(TitleMenuRules.Action.SEEN, TitleMenuRules.Action.FAVORITE,
                TitleMenuRules.Action.LISTS, TitleMenuRules.Action.MORE_LIKE), actions(rows));
        assertEquals("Add to a list", rows.get(2).label);
        assertFalse(rows.get(2).on);
        assertNull(rows.get(2).note);

        List<TitleMenuRules.Row> on = TitleMenuRules.rows(true, true,
                TitleMenuRules.lists(household(true, Arrays.asList(PICKS, TIPS), TIPS.key), T, true));
        assertEquals(Arrays.asList("Mark unseen", "Unfavorite", "Change lists", "More like this"), labels(on));
        assertTrue(on.get(2).on);
        assertEquals("On 2 lists", on.get(2).note);
    }

    @Test
    public void signedInWithNoListTheTvCanChangeTheRowIsMyList() {
        // Signed in, but the mock lists and a list not found yet can't take the title: My list is all that's left.
        List<ShownList> shown = new ArrayList<>(ShownList.MOCK);
        shown.add(UNFOUND);
        List<TitleMenuRules.Row> rows = TitleMenuRules.rows(false, false,
                TitleMenuRules.lists(household(false, shown), T, true));
        assertEquals(TitleMenuRules.Action.MY_LIST, rows.get(2).action);
        assertEquals("Add to My list", rows.get(2).label);
    }

    @Test
    public void moreLikeThisIsAlwaysLast() {
        for (boolean tmdb : new boolean[] {false, true}) {
            List<TitleMenuRules.Row> rows = TitleMenuRules.rows(false, false,
                    TitleMenuRules.lists(household(false, Collections.singletonList(PICKS)), T, tmdb));
            assertEquals(TitleMenuRules.Action.MORE_LIKE, rows.get(rows.size() - 1).action);
            assertEquals("More like this", rows.get(rows.size() - 1).label);
        }
    }

    @Test
    public void theListsRowSaysWhichListsHoldTheTitle() {
        assertNull(TitleMenuRules.listsNote(Collections.<ShownList>emptyList()));
        assertEquals("On My list", TitleMenuRules.listsNote(Collections.singletonList(ShownList.MY)));
        assertEquals("On Our picks", TitleMenuRules.listsNote(Collections.singletonList(PICKS)));
        assertEquals("On 2 lists", TitleMenuRules.listsNote(Arrays.asList(ShownList.MY, PICKS)));
    }

    @Test
    public void signedOutTheListsMenuIsMyListAlone() {
        ListMembership m = household(true, Arrays.asList(PICKS, TIPS), PICKS.key);
        List<TitleMenuRules.ListRow> rows = TitleMenuRules.lists(m, T, false);
        assertEquals(Collections.singletonList("my"), keys(rows));
        assertTrue(rows.get(0).on);
    }

    @Test
    public void signedInMyListThenTheShownListsTickedWhenTheTitleIsOnThem() {
        ListMembership m = household(false, Arrays.asList(PICKS, TIPS), TIPS.key);
        List<TitleMenuRules.ListRow> rows = TitleMenuRules.lists(m, T, true);
        assertEquals(Arrays.asList("my", "tmdb-8221116", "tmdb-9001"), keys(rows));
        assertFalse(rows.get(0).on);
        assertFalse(rows.get(1).on);
        assertTrue(rows.get(2).on);
        assertEquals(Collections.singletonList(TIPS), TitleMenuRules.on(rows));
    }

    @Test
    public void theMockListsAndAListNotFoundYetCantBeChanged() {
        List<ShownList> shown = new ArrayList<>(ShownList.MOCK);
        shown.add(UNFOUND);
        shown.add(PICKS);
        List<TitleMenuRules.ListRow> rows = TitleMenuRules.lists(household(false, shown), T, true);
        assertEquals(Arrays.asList("my", "tmdb-8221116"), keys(rows));
        assertTrue(TitleMenuRules.changeable(ShownList.MY));
        assertTrue(TitleMenuRules.changeable(PICKS));
        assertFalse(TitleMenuRules.changeable(ShownList.MOCK.get(0)));
        assertFalse(TitleMenuRules.changeable(UNFOUND));
    }

    @Test
    public void listsPastTheChipCapAreInTheMenuUpToWhatFitsTheSafeArea() {
        List<ShownList> shown = new ArrayList<>();
        for (int i = 1; i <= 20; i++) shown.add(ShownList.tmdb(1000 + i, null, "List " + i));
        List<TitleMenuRules.ListRow> rows = TitleMenuRules.lists(household(false, shown), T, true);
        assertEquals(TitleMenuRules.MAX_LIST_ROWS, rows.size());
        assertEquals("my", rows.get(0).list.key);
        assertEquals("tmdb-1012", rows.get(rows.size() - 1).list.key);
        // 72 px rows and the panel's 10 px padding fit inside 1080 less the 54 px title-safe margins.
        assertTrue(TitleMenuRules.MAX_LIST_ROWS * 72 + 2 * 10 <= 1080 - 2 * 54);
    }

    @Test
    public void toasts() {
        assertEquals("Added to Our picks", TitleMenuRules.listToast("Our picks", true));
        assertEquals("Removed from My list", TitleMenuRules.listToast("My list", false));
        assertTrue(TitleMenuRules.listFailed("Our picks").startsWith("Couldn’t change Our picks on TMDB"));
        assertEquals("Still saving Our picks on TMDB…", TitleMenuRules.listBusy("Our picks"));
    }

    @Test
    public void withTitlePutsATitleOnOneTmdbListOrTakesItOff() {
        ImportedLists.Entry picks = new ImportedLists.Entry(ListRef.parse("8221116"), 8221116, "Our picks",
                Arrays.asList("movie:1", "movie:2"));
        ImportedLists.Entry tips = new ImportedLists.Entry(ListRef.parse("9001"), 9001, "Friends' tips",
                Collections.singletonList("movie:2"));
        List<ImportedLists.Entry> lists = Arrays.asList(picks, tips);

        List<ImportedLists.Entry> added = ImportedLists.withTitle(lists, 8221116, ID, true);
        assertEquals(Arrays.asList("movie:1", "movie:2", ID), added.get(0).ids);
        assertEquals(tips, added.get(1));

        List<ImportedLists.Entry> removed = ImportedLists.withTitle(lists, 9001, "movie:2", false);
        assertEquals(picks, removed.get(0));
        assertEquals(Collections.<String>emptyList(), removed.get(1).ids);

        // Already as asked, or no such list: unchanged.
        assertEquals(lists, ImportedLists.withTitle(lists, 8221116, "movie:1", true));
        assertEquals(lists, ImportedLists.withTitle(lists, 42, ID, true));
        assertEquals(lists, ImportedLists.withTitle(lists, 0, ID, true));
    }

    @Test
    public void aRefusedRemovalPutsTheTitleBackWhereItWas() {
        ImportedLists.Entry picks = new ImportedLists.Entry(ListRef.parse("8221116"), 8221116, "Our picks",
                Arrays.asList("movie:1", "movie:2", "movie:3"));
        List<ImportedLists.Entry> lists = Collections.singletonList(picks);
        List<ImportedLists.Entry> removed = ImportedLists.withTitle(lists, 8221116, "movie:1", false);
        assertEquals(Arrays.asList("movie:2", "movie:3"), removed.get(0).ids);
        // Back at index 0, not at the end: "Recently added" treats the last title as newest.
        assertEquals(lists, ImportedLists.withTitleAt(removed, 8221116, "movie:1", 0));
        // An index past the end (the list shrank meanwhile) clamps; a title already there is left alone.
        assertEquals(Arrays.asList("movie:2", "movie:3", "movie:1"),
                ImportedLists.withTitleAt(removed, 8221116, "movie:1", 9).get(0).ids);
        assertEquals(lists, ImportedLists.withTitleAt(lists, 8221116, "movie:2", 0));
    }
}
