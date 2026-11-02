package com.hometv.launcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How many titles the real catalogue holds beyond the household's (F15 P2-1). Search hits, More like this, a person's
 * titles and genre rows join what it knows; past {@link #SPARE} of those, the least recently used are forgotten, down
 * to {@link #SPARE} again, so a TV that runs for weeks doesn't keep every title it was ever shown. The household's
 * titles and those loading are never forgotten. Pure, for the tests.
 */
final class CatalogBound {
    private CatalogBound() {}

    /** Titles beyond the household's that stay known. */
    static final int SPARE = 300;
    /** How far past SPARE the catalogue grows before it trims, so it trims now and then rather than on every hit. */
    static final int SLACK = 50;

    /**
     * The ids to forget: none while at most spare + slack aren't pinned, else the least recently used of those down to
     * spare (an id never used counts as oldest; ties keep the catalogue's order).
     */
    static List<String> victims(Collection<String> ids, Predicate<String> pinned, Map<String, Long> used, int spare,
                                int slack) {
        List<String> free = new ArrayList<>();
        for (String id : ids) if (!pinned.test(id)) free.add(id);
        if (free.size() <= spare + slack) return new ArrayList<>();
        free.sort((a, b) -> Long.compare(used.getOrDefault(a, 0L), used.getOrDefault(b, 0L)));
        return new ArrayList<>(free.subList(0, free.size() - spare));
    }
}
