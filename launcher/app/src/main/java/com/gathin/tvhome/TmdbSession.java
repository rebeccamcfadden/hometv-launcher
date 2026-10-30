package com.gathin.tvhome;

/**
 * A signed-in TMDB account (BACKLOG.md, TMDB sign-in and watchlist sync): the v4 access token and account id, which
 * read the lists and write the account's own lists, and the v3 session id and account number, which the watchlist
 * and favourite writes need. Both tokens are as good as a password: {@link TmdbSessionStore} keeps them in the
 * app's private settings, and {@link #toString()} never shows them.
 */
final class TmdbSession {
    final String accessToken;
    final String accountObjectId;
    final String sessionId;
    final int accountId;
    final String username;

    TmdbSession(String accessToken, String accountObjectId, String sessionId, int accountId, String username) {
        this.accessToken = accessToken;
        this.accountObjectId = accountObjectId;
        this.sessionId = sessionId;
        this.accountId = accountId;
        this.username = username == null ? "" : username;
    }

    /** Whether every part is there: a half-saved session counts as signed out. */
    boolean complete() {
        return notEmpty(accessToken) && notEmpty(accountObjectId) && notEmpty(sessionId) && accountId > 0;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    @Override
    public String toString() {
        return "TmdbSession(" + username + ", tokens hidden)";
    }
}
