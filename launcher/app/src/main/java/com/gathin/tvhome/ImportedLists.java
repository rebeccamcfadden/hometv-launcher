package com.gathin.tvhome;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Household and Friends as read from the TMDB lists config.xml names (tmdb_household_list, tmdb_friends_list): a list
 * that is set holds the ids last read from TMDB (none until the first read), and one that isn't set leaves the
 * catalogue's mock list in place, so nothing disappears before the project lead chooses the lists (BACKLOG.md,
 * Household and Friends lists). Pure Java, unit-tested (ImportedListsTest).
 */
final class ImportedLists {
    private final Map<ListId, Set<String>> lists = new EnumMap<>(ListId.class);

    /** Marks the list as coming from TMDB, with nothing read yet, or with the ids read last. */
    void set(ListId list, Collection<String> ids) {
        if (list == ListId.MY) throw new IllegalArgumentException("My list is the household's own");
        lists.put(list, ids == null ? new LinkedHashSet<String>() : new LinkedHashSet<>(ids));
    }

    /** Back to the catalogue's mock list. */
    void clear(ListId list) {
        lists.remove(list);
    }

    boolean fromTmdb(ListId list) {
        return lists.containsKey(list);
    }

    /** The ids read for the list; empty when it is not from TMDB. */
    List<String> ids(ListId list) {
        Set<String> s = lists.get(list);
        return s == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new java.util.ArrayList<>(s));
    }

    /** Every id read for any list, for the catalogue to load. */
    Set<String> allIds() {
        Set<String> out = new LinkedHashSet<>();
        for (Set<String> s : lists.values()) out.addAll(s);
        return out;
    }

    /** Household or Friends for t: TMDB's list when it is set, else the catalogue's. My list is never asked here. */
    boolean isOn(Title t, ListId list) {
        Set<String> s = lists.get(list);
        return s != null ? s.contains(t.id) : t.isOn(list);
    }
}
