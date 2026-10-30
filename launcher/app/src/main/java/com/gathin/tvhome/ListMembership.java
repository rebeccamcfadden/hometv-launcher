package com.gathin.tvhome;

/**
 * Which of the household's lists a title is on, as the list chips and Pick for me read it: My list from the TV (and
 * the TMDB watchlist when signed in), Household and Friends from the TMDB lists set in config.xml, or the mock
 * catalogue's lists while none is set (BACKLOG.md, Household and Friends lists). {@link Household} is the real one;
 * a test can make one from a My list predicate alone.
 */
interface ListMembership {
    boolean inMyList(String id);

    /** Whether t is on the list; this default reads the catalogue's (mock) lists for Household and Friends. */
    default boolean isOn(Title t, ListId list) {
        return list == ListId.MY ? inMyList(t.id) : t.isOn(list);
    }

    /** Whether t is on any of them: what All lists shows. */
    default boolean onAnyList(Title t) {
        for (ListId l : ListId.values()) if (isOn(t, l)) return true;
        return false;
    }
}
