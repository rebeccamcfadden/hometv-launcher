package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * Which of the household's lists a title is on, as the list chips, Pick for me and the cards' provenance read it
 * (design_preview/src/core/lists.ts): My list from the TV (and the TMDB watchlist when signed in), then the shown TMDB
 * lists ({@link ShownList}), or the mock catalogue's Household and Friends before the TV has had an account.
 * {@link Household} is the real one; a test can make one from a My list predicate alone, which reads the mock lists.
 */
interface ListMembership {
    boolean inMyList(String id);

    /** The lists after My list, in chip order; this default is the mock Household and Friends. */
    default List<ShownList> shownLists() {
        return ShownList.MOCK;
    }

    /** Whether t is on the list; this default reads the catalogue's (mock) lists for anything after My list. */
    default boolean isOn(Title t, ShownList list) {
        if (list.isMy()) return inMyList(t.id);
        return list.mock != null && t.isOn(list.mock);
    }

    /** My list, then {@link #shownLists}, past the chip cap included: what All lists, prices and provenance read. */
    default List<ShownList> lists() {
        List<ShownList> out = new ArrayList<>();
        out.add(ShownList.MY);
        out.addAll(shownLists());
        return out;
    }

    /** At most this many shown lists get a chip (the project lead, 2026-10-31); the rest are still in All lists. */
    int CHIP_CAP = 6;

    /** My list, then the first {@link #CHIP_CAP} shown lists: the chips after All lists, and Pick for me's lists. */
    default List<ShownList> chipLists() {
        List<ShownList> all = lists();
        return new ArrayList<>(all.subList(0, Math.min(all.size(), 1 + CHIP_CAP)));
    }

    /** Shown lists past the cap: no chip, but in All lists. */
    default int pastCap() {
        return Math.max(0, shownLists().size() - CHIP_CAP);
    }

    /** Whether t is on any of them: what All lists shows. Per title, so it builds no list. */
    default boolean onAnyList(Title t) {
        if (isOn(t, ShownList.MY)) return true;
        for (ShownList l : shownLists()) if (isOn(t, l)) return true;
        return false;
    }

    /** The first list in chip order that holds t, or null: the card's provenance line. Per title: no list built. */
    default ShownList firstListOf(Title t) {
        if (isOn(t, ShownList.MY)) return ShownList.MY;
        for (ShownList l : shownLists()) if (isOn(t, l)) return l;
        return null;
    }

    /**
     * The list's title ids, the oldest added first, for Sort's "Recently added" ({@link LibrarySort}). This default
     * knows no order: the pool's titles on the list, in the pool's order (the mock lists, and My list in a test).
     */
    default List<String> order(ShownList list, List<Title> pool) {
        List<String> out = new ArrayList<>();
        for (Title t : pool) if (isOn(t, list)) out.add(t.id);
        return out;
    }
}
