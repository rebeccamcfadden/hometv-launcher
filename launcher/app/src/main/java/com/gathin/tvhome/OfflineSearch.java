package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Search offline (V2 design 06-states A; design_preview/src/screens/search.ts, render): the keyboard and the rows of
 * saved titles still work, and the results come from the titles saved on the TV only, those on the lists and those
 * the household keeps (My list, viewed, seen, favorites), whose details the catalogue loads and caches. People and
 * genres need the internet, so they are left out. Pure Java, unit-tested (OfflineSearchTest).
 */
final class OfflineSearch {
    private OfflineSearch() {}

    /**
     * The ids of the titles saved on the TV: the household's own, then those on any of its lists (My list and the
     * shown lists; the mock Household and Friends before the TV has had an account).
     */
    static Set<String> savedIds(Collection<String> household, List<Title> all, ListMembership lists) {
        Set<String> out = new LinkedHashSet<>(household);
        for (Title t : all) if (lists.onAnyList(t)) out.add(t.id);
        return out;
    }

    /**
     * r with only the saved titles: no people, no genres (and so no genre's titles), and only the suggestions that
     * are saved titles. The order and the scores stay as SearchIndex made them.
     */
    static SearchIndex.Results filter(SearchIndex.Results r, Set<String> saved) {
        List<SearchIndex.Hit> titles = new ArrayList<>();
        for (SearchIndex.Hit h : r.titles) if (saved.contains(h.title.id)) titles.add(h);
        List<SearchIndex.Hit> suggestions = new ArrayList<>();
        for (SearchIndex.Hit h : r.suggestions) {
            if (h.kind == SearchIndex.HitKind.TITLE && saved.contains(h.title.id)) suggestions.add(h);
        }
        return new SearchIndex.Results(titles, Collections.emptyList(), Collections.emptyList(), suggestions,
                Collections.emptyList());
    }
}
