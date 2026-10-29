package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which titles Movies and TV Shows list, and on which list chip (design_preview/src/screens/library.ts, inList). V1
 * is lists-only: a page shows its kind's titles that are on an imported watchlist or on the household's My list.
 * My list is the one stored on the TV ({@link Household}), so a title added or removed in the title preview moves
 * in or out of it; Household and Friends are the mock catalogue's lists. Pure Java, unit-tested.
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
     * Whether t belongs under the chip: All lists is any imported list or My list; My list is the TV's own; the
     * others are the imported lists.
     */
    static boolean inList(Title t, Choice c, Predicate<String> inMyList) {
        switch (c) {
            case ALL:
                return !t.lists.isEmpty() || inMyList.test(t.id);
            case MY:
                return inMyList.test(t.id);
            default:
                return t.isOn(c.list);
        }
    }

    /** The page's titles: its kind, on some list (All lists), in the catalogue's order. */
    static List<Title> pool(List<Title> all, Kind kind, Predicate<String> inMyList) {
        List<Title> out = new ArrayList<>();
        for (Title t : all) if (t.kind == kind && inList(t, Choice.ALL, inMyList)) out.add(t);
        return out;
    }

    /** The titles of the pool under the chip. */
    static List<Title> under(List<Title> pool, Choice c, Predicate<String> inMyList) {
        List<Title> out = new ArrayList<>();
        for (Title t : pool) if (inList(t, c, inMyList)) out.add(t);
        return out;
    }
}
