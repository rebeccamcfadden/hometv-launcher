package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The TMDB account's settings (BACKLOG.md, TMDB sign-in and watchlist sync): which TMDB lists show as chips, which is
 * Seen, and whether the launcher may make the Seen list. config.xml's tmdb_shown_lists (or the older
 * tmdb_household_list and tmdb_friends_list), tmdb_seen_list and create_seen_list, overlaid by a pushed config.json
 * like every other key. Kept apart from {@link Config} so the account code stays in its own files. {@link #of} is pure
 * and unit-tested (AccountConfigTest).
 */
final class AccountConfig {
    /** The lists config.xml shows, in its order; empty leaves the choice to the TV (Choose lists). */
    final List<ListRef> shown;
    /** "Seen" by default: the account's private list of that name; NONE keeps Seen on the TV only. */
    final ListRef seen;
    final boolean createSeen;

    private AccountConfig(List<ListRef> shown, ListRef seen, boolean createSeen) {
        this.shown = shown;
        this.seen = seen;
        this.createSeen = createSeen;
    }

    /**
     * @param shown tmdb_shown_lists: list numbers, themoviedb.org/list links or names, one per line or comma-separated
     * @param household tmdb_household_list, read only when {@code shown} is empty
     * @param friends tmdb_friends_list, likewise, after {@code household}
     */
    static AccountConfig of(String shown, String household, String friends, String seen, boolean createSeen) {
        List<ListRef> refs = parseList(shown);
        if (refs.isEmpty()) {
            // The older keys hold one list each, so a name with a comma in it stays whole.
            List<ListRef> legacy = new ArrayList<>();
            for (String one : new String[]{household, friends}) {
                ListRef r = ListRef.parse(one);
                if (!r.isNone() && (legacy.isEmpty() || !legacy.get(0).toString().equals(r.toString()))) legacy.add(r);
            }
            refs = Collections.unmodifiableList(legacy);
        }
        ListRef s = ListRef.parse(seen);
        return new AccountConfig(refs, s, createSeen && !s.isNone());
    }

    /** "8221116, Our picks\nhttps://www.themoviedb.org/list/9001": each part a {@link ListRef}; blanks, repeats go. */
    static List<ListRef> parseList(String setting) {
        List<ListRef> out = new ArrayList<>();
        List<String> seenRefs = new ArrayList<>();
        if (setting == null) return out;
        for (String part : setting.split("[,\\r\\n]+")) {
            ListRef r = ListRef.parse(part);
            if (r.isNone() || seenRefs.contains(r.toString().toLowerCase(java.util.Locale.ROOT))) continue;
            seenRefs.add(r.toString().toLowerCase(java.util.Locale.ROOT));
            out.add(r);
        }
        return Collections.unmodifiableList(out);
    }

    static AccountConfig load(Context ctx) {
        Resources res = ctx.getResources();
        String shown = res.getString(R.string.tmdb_shown_lists);
        String household = res.getString(R.string.tmdb_household_list);
        String friends = res.getString(R.string.tmdb_friends_list);
        String seen = res.getString(R.string.tmdb_seen_list);
        boolean create = res.getBoolean(R.bool.create_seen_list);
        JSONObject json = Config.overlay(ctx);
        if (json != null) {
            // A pushed config.json may give the lists as one string or as an array of them.
            JSONArray a = json.optJSONArray("tmdb_shown_lists");
            if (a != null) {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < a.length(); i++) b.append(a.optString(i, "")).append('\n');
                shown = b.toString();
            } else {
                shown = json.optString("tmdb_shown_lists", shown);
            }
            household = json.optString("tmdb_household_list", household);
            friends = json.optString("tmdb_friends_list", friends);
            seen = json.optString("tmdb_seen_list", seen);
            create = json.optBoolean("create_seen_list", create);
        }
        return of(shown, household, friends, seen, create);
    }

    /** Whether Seen follows a TMDB list; otherwise its changes are not queued for the account at all. */
    boolean syncsSeen() {
        return !seen.isNone();
    }

    /** Whether config.xml sets the shown lists, so the TV's choice doesn't count. */
    boolean overridesShown() {
        return !shown.isEmpty();
    }
}
