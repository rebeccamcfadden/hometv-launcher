package com.gathin.tvhome;

/**
 * The household's lists that follow the TMDB account both ways (BACKLOG.md, TMDB sign-in and watchlist sync): My list
 * is the account's watchlist, Favorites its favourites, and Seen a private list of its own (TMDB has no "seen").
 * {@link TmdbAccountApi} has the calls behind each. The lists shown as chips are only read, by their TMDB number
 * ({@link AccountSync.Settings#shown}); before they had names, Household and Friends were two more entries here,
 * which older saved data may still name ({@link SyncLedger#fromJson} skips them).
 */
enum SyncList {
    MY_LIST(true),
    FAVORITES(true),
    SEEN(true);

    /** Whether the TV changes it: every list here is both ways now. */
    final boolean writable;

    SyncList(boolean writable) {
        this.writable = writable;
    }

    /** Whether it is one of the account's lists (a TMDB list id), rather than the watchlist or favourites. */
    boolean isCustomList() {
        return this == SEEN;
    }
}
