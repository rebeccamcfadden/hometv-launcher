package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads TMDB's answers to {@link TmdbAccountApi}'s calls (BACKLOG.md, TMDB sign-in and watchlist sync). An answer
 * that is not a success becomes a {@link Failure} that says what to do about it, from TMDB's status codes
 * (developer.themoviedb.org/docs/errors). Pure Java, unit-tested (TmdbAccountParseTest).
 */
final class TmdbAccountParse {
    private TmdbAccountParse() {}

    /** What a failed call means for the code that made it. */
    enum Problem {
        /** The network or TMDB is down or busy (5xx, 429, no answer): try again later. */
        RETRY,
        /** The request token is not approved yet (codes 41 and 17): keep waiting. */
        NOT_APPROVED,
        /** The request token is too old or unknown (code 33): a new code is needed. */
        EXPIRED,
        /** The account's token or session is no longer accepted (codes 3, 14, 35, 36, 37): sign in again. */
        SIGNED_OUT,
        /** Anything else: the call itself was wrong or refused, and repeating it won't help. */
        REJECTED
    }

    /** A call that failed, with TMDB's code (0 when it gave none) and HTTP status (0 when nothing answered). */
    static final class Failure extends Exception {
        final Problem problem;
        final int status;
        final int code;

        Failure(Problem problem, int status, int code, String message) {
            super(message);
            this.problem = problem;
            this.status = status;
            this.code = code;
        }

        /** No answer at all: the network. */
        static Failure network(String message) {
            return new Failure(Problem.RETRY, 0, 0, message);
        }
    }

    /** The account the session belongs to. */
    static final class Account {
        final int id;
        final String username;

        Account(int id, String username) {
            this.id = id;
            this.username = username;
        }
    }

    /** One page of titles, as title ids in TMDB's order, and the list's name on a list's page ("" elsewhere). */
    static final class Page {
        final List<String> ids;
        final int page;
        final int totalPages;
        final String name;

        Page(List<String> ids, int page, int totalPages, String name) {
            this.ids = ids;
            this.page = page;
            this.totalPages = totalPages;
            this.name = name;
        }
    }

    /** One of the account's lists. */
    static final class ListInfo {
        final long id;
        final String name;
        final boolean isPublic;
        final int items;

        ListInfo(long id, String name, boolean isPublic, int items) {
            this.id = id;
            this.name = name;
            this.isPublic = isPublic;
            this.items = items;
        }
    }

    /** One page of the account's lists. */
    static final class ListPage {
        final List<ListInfo> lists;
        final int page;
        final int totalPages;

        ListPage(List<ListInfo> lists, int page, int totalPages) {
            this.lists = lists;
            this.page = page;
            this.totalPages = totalPages;
        }
    }

    /** The access token and the account's v4 id from a finished sign-in. */
    static final class Access {
        final String accessToken;
        final String accountObjectId;

        Access(String accessToken, String accountObjectId) {
            this.accessToken = accessToken;
            this.accountObjectId = accountObjectId;
        }
    }

    // ---- Sign-in -------------------------------------------------------------------------------------------------

    static String requestToken(AccountHttp.Reply r) throws Failure {
        return required(ok(r), "request_token", r);
    }

    static Access access(AccountHttp.Reply r) throws Failure {
        JSONObject o = ok(r);
        return new Access(required(o, "access_token", r), required(o, "account_id", r));
    }

    static String sessionId(AccountHttp.Reply r) throws Failure {
        return required(ok(r), "session_id", r);
    }

    static Account account(AccountHttp.Reply r) throws Failure {
        JSONObject o = ok(r);
        int id = o.optInt("id", 0);
        if (id <= 0) throw malformed(r, "id");
        return new Account(id, text(o, "username"));
    }

    // ---- Lists ---------------------------------------------------------------------------------------------------

    /**
     * A page of the watchlist, the favourites or a list. {@code kind} is the media type when the endpoint has one
     * kind ("movie" or "tv"); null reads each result's own media_type, as a list's do. Results that are neither a
     * movie nor a show (a person, say) are skipped.
     */
    static Page page(AccountHttp.Reply r, String kind) throws Failure {
        JSONObject o = ok(r);
        JSONArray results = o.optJSONArray("results");
        Set<String> ids = new LinkedHashSet<>();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) continue;
                String type = kind != null ? kind : text(item, "media_type");
                String id = TmdbAccountApi.id(type, item.optLong("id", 0));
                if (id != null) ids.add(id);
            }
        }
        return new Page(Collections.unmodifiableList(new ArrayList<>(ids)), o.optInt("page", 1),
                Math.max(1, o.optInt("total_pages", 1)), text(o, "name"));
    }

    static ListPage lists(AccountHttp.Reply r) throws Failure {
        JSONObject o = ok(r);
        JSONArray results = o.optJSONArray("results");
        List<ListInfo> out = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) continue;
                long id = item.optLong("id", 0);
                if (id <= 0) continue;
                // v4 gives public as 0 or 1; accept a boolean too.
                Object pub = item.opt("public");
                boolean isPublic = pub instanceof Boolean ? (Boolean) pub : item.optInt("public", 0) != 0;
                out.add(new ListInfo(id, text(item, "name"), isPublic, item.optInt("number_of_items", 0)));
            }
        }
        return new ListPage(Collections.unmodifiableList(out), o.optInt("page", 1),
                Math.max(1, o.optInt("total_pages", 1)));
    }

    /** The id of a list just made. */
    static long createdList(AccountHttp.Reply r) throws Failure {
        long id = ok(r).optLong("id", 0);
        if (id <= 0) throw malformed(r, "id");
        return id;
    }

    /** A write that answers only with success: the watchlist, the favourites, list items, signing out. */
    static void done(AccountHttp.Reply r) throws Failure {
        JSONObject o = ok(r);
        // List item calls report each title; one that failed is already there or already gone, which is fine.
        if (o.has("success") && !o.optBoolean("success", true)) throw failure(r, o);
    }

    // ---- Failures ------------------------------------------------------------------------------------------------

    /** The answer as JSON when it is a success; otherwise the failure it is. */
    private static JSONObject ok(AccountHttp.Reply r) throws Failure {
        JSONObject o = json(r.body);
        if (!r.ok()) throw failure(r, o);
        if (o == null) throw new Failure(Problem.RETRY, r.status, 0, "TMDB answered with something that is not JSON");
        return o;
    }

    static Failure failure(AccountHttp.Reply r) {
        return failure(r, json(r.body));
    }

    private static Failure failure(AccountHttp.Reply r, JSONObject o) {
        int code = o == null ? 0 : o.optInt("status_code", 0);
        String message = o == null ? "" : text(o, "status_message");
        if (message.isEmpty()) message = "HTTP " + r.status;
        return new Failure(problem(r.status, code), r.status, code, message);
    }

    static Problem problem(int status, int code) {
        switch (code) {
            case 41:
            case 17:
                return Problem.NOT_APPROVED;
            case 33:
                return Problem.EXPIRED;
            case 3:
            case 14:
            case 35:
            case 36:
            case 37:
                return Problem.SIGNED_OUT;
            case 9:
            case 11:
            case 24:
            case 25:
            case 43:
            case 46:
                return Problem.RETRY;
            default:
                break;
        }
        if (status == 429 || status >= 500 || status == 0) return Problem.RETRY;
        return Problem.REJECTED;
    }

    private static Failure malformed(AccountHttp.Reply r, String key) {
        return new Failure(Problem.REJECTED, r.status, 0, "TMDB's answer has no " + key);
    }

    private static String required(JSONObject o, String key, AccountHttp.Reply r) throws Failure {
        String v = text(o, key);
        if (v.isEmpty()) throw malformed(r, key);
        return v;
    }

    /** A string field, or "" when it is missing or null (Android's optString would give "null"). */
    private static String text(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        Object v = o.opt(key);
        return v instanceof String ? (String) v : String.valueOf(v);
    }

    private static JSONObject json(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            return null;
        }
    }
}
