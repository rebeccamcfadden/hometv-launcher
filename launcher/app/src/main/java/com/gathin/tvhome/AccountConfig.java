package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;

import org.json.JSONObject;

/**
 * The TMDB account's settings (BACKLOG.md, TMDB sign-in and watchlist sync): which TMDB lists are Household, Friends
 * and Seen, and whether the launcher may make the Seen list. config.xml's tmdb_household_list, tmdb_friends_list,
 * tmdb_seen_list and create_seen_list, overlaid by a pushed config.json like every other key. Kept apart from
 * {@link Config} so the account code stays in its own files. {@link #of} is pure and unit-tested (AccountConfigTest).
 */
final class AccountConfig {
    final ListRef household;
    final ListRef friends;
    /** "Seen" by default: the account's private list of that name; NONE keeps Seen on the TV only. */
    final ListRef seen;
    final boolean createSeen;

    private AccountConfig(ListRef household, ListRef friends, ListRef seen, boolean createSeen) {
        this.household = household;
        this.friends = friends;
        this.seen = seen;
        this.createSeen = createSeen;
    }

    static AccountConfig of(String household, String friends, String seen, boolean createSeen) {
        ListRef s = ListRef.parse(seen);
        return new AccountConfig(ListRef.parse(household), ListRef.parse(friends), s, createSeen && !s.isNone());
    }

    static AccountConfig load(Context ctx) {
        Resources res = ctx.getResources();
        String household = res.getString(R.string.tmdb_household_list);
        String friends = res.getString(R.string.tmdb_friends_list);
        String seen = res.getString(R.string.tmdb_seen_list);
        boolean create = res.getBoolean(R.bool.create_seen_list);
        JSONObject json = Config.overlay(ctx);
        if (json != null) {
            household = json.optString("tmdb_household_list", household);
            friends = json.optString("tmdb_friends_list", friends);
            seen = json.optString("tmdb_seen_list", seen);
            create = json.optBoolean("create_seen_list", create);
        }
        return of(household, friends, seen, create);
    }

    /** Whether Seen follows a TMDB list; otherwise its changes are not queued for the account at all. */
    boolean syncsSeen() {
        return !seen.isNone();
    }

    /** The ref behind Household or Friends; NONE for My list. */
    ListRef imported(ListId list) {
        return list == ListId.HOUSEHOLD ? household : list == ListId.FRIENDS ? friends : ListRef.NONE;
    }

    AccountSync.Settings settings() {
        return new AccountSync.Settings(seen, household, friends, createSeen);
    }
}
