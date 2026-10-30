package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which titles Movies and TV Shows list, and on which list chip (design_preview/src/screens/library.ts, inList). V1
 * is lists-only: a page shows its kind's titles that are on an imported watchlist or on the household's My list.
 * My list is the one stored on the TV ({@link Household}), so a title added or removed in the title preview moves
 * in or out of it; Household and Friends are the TMDB lists config.xml names, or the mock catalogue's
 * ({@link ListMembership}). Pure Java, unit-tested.
 */
final class LibraryLists {
    private LibraryLists() {}

    /** The list chips, in their order. */
    enum Choice {
        ALL(null), MY(ListId.MY), HOUSEHOLD(ListId.HOUSEHOLD), FRIENDS(ListId.FRIENDS);

        /** The watchlist behind the chip; null for All lists. */
        final ListId list;

        Choice(ListId list) {
            this.list = list;
        }
    }

    /**
     * Whether t belongs under the chip: All lists is any of the lists; My list is the household's own; Household and
     * Friends are the imported lists ({@link ListMembership}).
     */
    static boolean inList(Title t, Choice c, ListMembership lists) {
        return c == Choice.ALL ? lists.onAnyList(t) : lists.isOn(t, c.list);
    }

    /** The page's titles: its kind, on some list (All lists), in the catalogue's order. */
    static List<Title> pool(List<Title> all, Kind kind, ListMembership lists) {
        List<Title> out = new ArrayList<>();
        for (Title t : all) if (t.kind == kind && inList(t, Choice.ALL, lists)) out.add(t);
        return out;
    }

    /** The titles of the pool under the chip. */
    static List<Title> under(List<Title> pool, Choice c, ListMembership lists) {
        List<Title> out = new ArrayList<>();
        for (Title t : pool) if (inList(t, c, lists)) out.add(t);
        return out;
    }

    /** With the catalogue's lists for Household and Friends, as the prototype comparisons use. */
    static boolean inList(Title t, Choice c, Predicate<String> inMyList) {
        return inList(t, c, (ListMembership) inMyList::test);
    }

    static List<Title> pool(List<Title> all, Kind kind, Predicate<String> inMyList) {
        return pool(all, kind, (ListMembership) inMyList::test);
    }

    static List<Title> under(List<Title> pool, Choice c, Predicate<String> inMyList) {
        return under(pool, c, (ListMembership) inMyList::test);
    }
}