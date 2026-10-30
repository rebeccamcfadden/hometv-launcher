package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Where the signed-in TMDB account is kept (BACKLOG.md, TMDB sign-in and watchlist sync): the SharedPreferences file
 * "tmdb_account", private to the app. The manifest has allowBackup="false", so it never leaves the TV in a backup;
 * nothing here logs, and the tokens never go into the disk cache, a URL that is logged or a screenshot. Clearing the
 * app's data or signing out removes it. Not encrypted: Android TV 9 has no hardware-backed store worth the code for a
 * token the person can revoke from TMDB's settings at any time.
 */
final class TmdbSessionStore {
    static final String PREFS = "tmdb_account";
    private static final String ACCESS = "access_token", OBJECT_ID = "account_object_id", SESSION = "session_id",
            ACCOUNT = "account_id", NAME = "username";

    private final SharedPreferences prefs;

    TmdbSessionStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The saved account, or null when signed out or only half saved. */
    TmdbSession load() {
        TmdbSession s = new TmdbSession(prefs.getString(ACCESS, null), prefs.getString(OBJECT_ID, null),
                prefs.getString(SESSION, null), prefs.getInt(ACCOUNT, 0), prefs.getString(NAME, ""));
        return s.complete() ? s : null;
    }

    void save(TmdbSession s) {
        prefs.edit()
                .putString(ACCESS, s.accessToken)
                .putString(OBJECT_ID, s.accountObjectId)
                .putString(SESSION, s.sessionId)
                .putInt(ACCOUNT, s.accountId)
                .putString(NAME, s.username)
                .apply();
    }

    void clear() {
        prefs.edit().clear().apply();
    }
}
