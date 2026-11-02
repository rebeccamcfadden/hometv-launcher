package com.hometv.launcher;

/**
 * Search past TMDB's first 20 results (BACKLOG.md, TMDB): when focus reaches the last row of the full results, the
 * next page is asked for and merged in (Catalog.Found.merge), up to {@link #MAX_PAGES} pages, so the titles kept for a
 * search stay bounded on a 1 GB TV. Pure Java, tested on the JVM; SearchPage asks.
 */
final class SearchPages {
    private SearchPages() {}

    /** At most 100 results from TMDB (before the art and photo filter): past that, typing more says it better. */
    static final int MAX_PAGES = 5;

    /** The page to ask for after {@code found}, or 0 when there is none or the cap is reached. */
    static int next(Catalog.Found found) {
        if (found == null || found.page >= found.totalPages || found.page >= MAX_PAGES) return 0;
        return found.page + 1;
    }

    /** Whether the card at index (of count, in rows of columns) is on the grid's last row. */
    static boolean onLastRow(int index, int count, int columns) {
        if (index < 0 || index >= count || columns <= 0) return false;
        return index / columns == (count - 1) / columns;
    }
}
