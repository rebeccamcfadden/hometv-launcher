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

    /**
     * The card that asked last, after focus lands somewhere: kept while focus is on the last row (a redraw puts it back
     * on that card, which must not ask again), forgotten anywhere else (another row, a filter chip), so coming back
     * to the same card asks again (a page that showed no new card leaves that card last).
     */
    static String guard(String askedFrom, boolean onLastRow) {
        return onLastRow ? askedFrom : null;
    }

    /** Whether focus on this last-row card asks: not when it is the card that asked last and nothing is on its way. */
    static boolean asks(String askedFrom, String key, boolean pageOnItsWay) {
        return pageOnItsWay || key == null || !key.equals(askedFrom);
    }

    /**
     * Whether to ask for the next page without a key press: nothing shows for the query and filter yet (no card to
     * reach the last row with), or the page just merged showed no new card.
     */
    static boolean askOnItsOwn(int visibleBefore, int visibleNow, Catalog.Found found) {
        if (next(found) == 0) return false;
        return visibleNow == 0 || visibleNow <= visibleBefore;
    }
}
