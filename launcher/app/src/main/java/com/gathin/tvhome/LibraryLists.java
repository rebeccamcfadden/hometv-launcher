package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Which titles Movies and TV Shows list, and on which list chip (design_preview/src/screens/library.ts, inList; and
 * src/core/lists.ts). V1 is lists-only: a page shows its kind's titles that are on one of the household's lists. The
 * chips are All lists, My list (stored on the TV, {@link Household}, so a title added or removed in the title preview
 * moves in or out of it), then each TMDB list shown in Choose lists by its TMDB name, or the mock Household and
 * Friends before the TV has had an account ({@link ListMembership}, {@link ShownList}). Pure Java, unit-tested.
 */
final class LibraryLists {
    private LibraryLists() {}

    /** The list chips, in their order: All lists, My list, then the first six shown lists. */
    static List<ShownList> choices(ListMembership lists) {
        List<ShownList> out = new ArrayList<>();
        out.add(ShownList.ALL);
        out.addAll(lists.chipLists());
        return out;
    }

    /** The chip with that key, or All lists when its list is hidden or gone since it was chosen. */
    static ShownList choice(String key, List<ShownList> choices) {
        for (ShownList c : choices) if (c.key.equals(key)) return c;
        return ShownList.ALL;
    }

    /** Whether t belongs under the chip: All lists is any of the lists; any other chip, its own list. */
    static boolean inList(Title t, ShownList c, ListMembership lists) {
        return c.isAll() ? lists.onAnyList(t) : lists.isOn(t, c);
    }

    /** The page's titles: its kind, on some list (All lists), in the catalogue's order. */
    static List<Title> pool(List<Title> all, Kind kind, ListMembership lists) {
        List<Title> out = new ArrayList<>();
        for (Title t : all) if (t.kind == kind && inList(t, ShownList.ALL, lists)) out.add(t);
        return out;
    }

    /** The titles of the pool under the chip. */
    static List<Title> under(List<Title> pool, ShownList c, ListMembership lists) {
        List<Title> out = new ArrayList<>();
        for (Title t : pool) if (inList(t, c, lists)) out.add(t);
        return out;
    }

    /**
     * Sort's "Recently added" ranks for the titles under the chip ({@link LibrarySort#recentRanks}): the chip's own
     * list, or for All lists every list in chip order, past the chip cap included.
     */
    static Map<String, Integer> recentRanks(List<Title> pool, ShownList c, ListMembership lists) {
        List<List<String>> orders = new ArrayList<>();
        for (ShownList l : c.isAll() ? lists.lists() : java.util.Collections.singletonList(c)) {
            orders.add(lists.order(l, pool));
        }
        return LibrarySort.recentRanks(orders);
    }

    /** With the catalogue's mock lists after My list, as the prototype comparisons use. */
    static boolean inList(Title t, ShownList c, Predicate<String> inMyList) {
        return inList(t, c, (ListMembership) inMyList::test);
    }

    static List<Title> pool(List<Title> all, Kind kind, Predicate<String> inMyList) {
        return pool(all, kind, (ListMembership) inMyList::test);
    }

    static List<Title> under(List<Title> pool, ShownList c, Predicate<String> inMyList) {
        return under(pool, c, (ListMembership) inMyList::test);
    }

    /** The most names {@link #names} spells out; past it, the first two and how many more. */
    static final int NAMED = 4;

    /**
     * The lists' names for the page's context line (lists.ts, names): "My list, Family picks & Friends' tips"; past
     * four, "My list, Family picks & 4 more". The words come from the strings: {@code comma} joins two names ("%1$s,
     * %2$s"), {@code and} the last two ("%1$s & %2$s"), {@code more} the first two and the rest ("%1$s, %2$s & %3$d
     * more").
     */
    static String names(List<String> names, String comma, String and, String more) {
        int n = names.size();
        if (n == 0) return "";
        if (n == 1) return names.get(0);
        if (n > NAMED) return String.format(Locale.ROOT, more, names.get(0), names.get(1), n - 2);
        String head = names.get(0);
        for (int i = 1; i < n - 1; i++) head = String.format(Locale.ROOT, comma, head, names.get(i));
        return String.format(Locale.ROOT, and, head, names.get(n - 1));
    }
}
