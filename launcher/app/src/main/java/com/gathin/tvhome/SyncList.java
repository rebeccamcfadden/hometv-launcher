package com.gathin.tvhome;

/**
 * The household's lists that follow the TMDB account (BACKLOG.md, TMDB sign-in and watchlist sync): My list is the
 * account's watchlist, Favorites its favourites, Seen a private list of its own (TMDB has no "seen"), and Household
 * and Friends two of its lists that the TV only reads. {@link TmdbAccountApi} has the calls behind each.
 */
enum SyncList {
    MY_LIST(true),
    FAVORITES(true),
    SEEN(true),
    HOUSEHOLD(false),
    FRIENDS(false);

    /** Whether the TV changes it: the read-only lists are only pulled. */
    final boolean writable;

    SyncList(boolean writable) {
        this.writable = writable;
    }

    /** Whether it is one of the account's lists (a TMDB list id), rather than the watchlist or favourites. */
    boolean isCustomList() {
        return this == SEEN || this == HOUSEHOLD || this == FRIENDS;
    }
}
