package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The title menu's changes to the shown TMDB lists that TMDB hasn't answered yet (F3; Accounts#setOnShownList): one at
 * most per list and title, so a second OK on the same row waits for the first answer instead of racing it. Each keeps
 * what the list held before, so a refused change goes back to exactly that, and the saved copies a sync reads while it
 * is out keep showing it ({@link #apply}). Main thread only. Pure Java, unit-tested (ShownEditsTest).
 */
final class ShownEdits {
    private static final class Edit {
        final long listId;
        final String titleId;
        final boolean on;
        final boolean was;

        Edit(long listId, String titleId, boolean on, boolean was) {
            this.listId = listId;
            this.titleId = titleId;
            this.on = on;
            this.was = was;
        }
    }

    private final Map<String, Edit> pending = new LinkedHashMap<>();

    private static String key(long listId, String titleId) {
        return listId + ":" + titleId;
    }

    /** Whether a change to titleId on the list is out to TMDB. */
    boolean isPending(long listId, String titleId) {
        return pending.containsKey(key(listId, titleId));
    }

    /** Records a change about to be sent; false, and nothing recorded, while one for the same pair is out. */
    boolean start(long listId, String titleId, boolean on, boolean was) {
        String k = key(listId, titleId);
        if (pending.containsKey(k)) return false;
        pending.put(k, new Edit(listId, titleId, on, was));
        return true;
    }

    /**
     * TMDB answered: what the list should hold now, the change if it {@code took}, else what it held before. Null when
     * nothing was out for the pair.
     */
    Boolean finish(long listId, String titleId, boolean took) {
        Edit e = pending.remove(key(listId, titleId));
        if (e == null) return null;
        return took ? e.on : e.was;
    }

    /** The copies with every change still out applied over them: after a sync that read the lists before TMDB had it. */
    List<ImportedLists.Entry> apply(List<ImportedLists.Entry> lists) {
        List<ImportedLists.Entry> out = new ArrayList<>(lists);
        for (Edit e : pending.values()) out = ImportedLists.withTitle(out, e.listId, e.titleId, e.on);
        return out;
    }
}
