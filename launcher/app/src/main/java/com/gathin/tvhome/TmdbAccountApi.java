package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * The TMDB account calls (BACKLOG.md, TMDB sign-in and watchlist sync), built but not sent: {@link AccountHttp}
 * sends them. Sign-in is TMDB's v4 flow, whose access token reads the watchlist and favourites and reads and writes
 * the account's lists (movies and shows, private ones too); that token is then turned into a v3 session id, which
 * the watchlist and favourite writes still need (TMDB has no v4 call for them). Docs:
 * developer.themoviedb.org/v4/docs/authentication-user and /reference/authentication-create-session-from-v4-token.
 * Titles are "movie:123" and "tv:456", the catalogue's ids. Pure Java, unit-tested (TmdbAccountApiTest).
 */
final class TmdbAccountApi {
    private TmdbAccountApi() {}

    static final String BASE = "https://api.themoviedb.org";
    /** Where the person approves the request token, on their phone through the QR code. */
    static final String APPROVE = "https://www.themoviedb.org/auth/access?request_token=";
    /** Pages read at most from one account list: 20 titles a page. */
    static final int MAX_PAGES = 50;

    /** Which half of the watchlist or favourites: TMDB keeps movies and shows apart. */
    enum Kind {
        MOVIE("movie"), TV("tv");

        final String path;

        Kind(String path) {
            this.path = path;
        }
    }

    // ---- Title ids ---------------------------------------------------------------------------------------------

    /** "movie" or "tv" for a title id, or null when it is not a TMDB id (a mock catalogue id). */
    static String mediaType(String id) {
        if (id == null) return null;
        if (id.startsWith("movie:") && number(id) > 0) return "movie";
        if (id.startsWith("tv:") && number(id) > 0) return "tv";
        return null;
    }

    /** The TMDB number in a title id, or 0. */
    static int number(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        if (colon < 0) return 0;
        String digits = id.substring(colon + 1);
        if (digits.isEmpty() || digits.length() > 9) return 0;
        for (int i = 0; i < digits.length(); i++) if (!Character.isDigit(digits.charAt(i))) return 0;
        return Integer.parseInt(digits);
    }

    /** The title id for a TMDB media type and number, or null for anything but a movie or a show. */
    static String id(String mediaType, long number) {
        if (number <= 0 || number > 999_999_999L) return null;
        if ("movie".equals(mediaType)) return "movie:" + number;
        if ("tv".equals(mediaType)) return "tv:" + number;
        return null;
    }

    // ---- Sign-in -------------------------------------------------------------------------------------------------

    /** Step 1: a request token for the person to approve. */
    static AccountHttp.Call requestToken() {
        return post("/4/auth/request_token", AccountHttp.Auth.APP, new JSONObject());
    }

    /** Step 2: the page the QR code opens. */
    static String approveUrl(String requestToken) {
        return APPROVE + encode(requestToken);
    }

    /** Step 3, polled while the code shows: the account's access token once the request token is approved. */
    static AccountHttp.Call accessToken(String requestToken) {
        return post("/4/auth/access_token", AccountHttp.Auth.APP, object("request_token", requestToken));
    }

    /** Step 4: a v3 session id from the access token, for the watchlist and favourite writes. */
    static AccountHttp.Call session(String accessToken) {
        return post("/3/authentication/session/convert/4", AccountHttp.Auth.APP, object("access_token", accessToken));
    }

    /** Step 5: the v3 account, for its number and name. */
    static AccountHttp.Call account(String sessionId) {
        return new AccountHttp.Call(AccountHttp.Method.GET, BASE + "/3/account?session_id=" + encode(sessionId),
                AccountHttp.Auth.APP, null);
    }

    /** Signing out: the access token is revoked. */
    static AccountHttp.Call logout(String accessToken) {
        return call(AccountHttp.Method.DELETE, "/4/auth/access_token", AccountHttp.Auth.APP,
                object("access_token", accessToken));
    }

    /** Signing out: the v3 session is deleted. */
    static AccountHttp.Call deleteSession(String sessionId) {
        return call(AccountHttp.Method.DELETE, "/3/authentication/session", AccountHttp.Auth.APP,
                object("session_id", sessionId));
    }

    // ---- Reading -------------------------------------------------------------------------------------------------

    /** A page of the watchlist (My list) or the favourites, oldest first so new titles land at the end. */
    static AccountHttp.Call accountPage(String accountObjectId, SyncList list, Kind kind, int page) {
        String which;
        if (list == SyncList.MY_LIST) which = "watchlist";
        else if (list == SyncList.FAVORITES) which = "favorites";
        else throw new IllegalArgumentException(list + " is a TMDB list; use listPage");
        return get("/4/account/" + encode(accountObjectId) + "/" + kind.path + "/" + which + "?page=" + page
                + "&sort_by=created_at.asc");
    }

    /** A page of the account's own lists, private ones included. */
    static AccountHttp.Call accountLists(String accountObjectId, int page) {
        return get("/4/account/" + encode(accountObjectId) + "/lists?page=" + page);
    }

    /** A page of a TMDB list's titles, with the account's token (its private lists too). */
    static AccountHttp.Call listPage(long listId, int page) {
        return listPage(listId, page, AccountHttp.Auth.USER);
    }

    /** A page of a TMDB list's titles; with the app's token only a public list answers. */
    static AccountHttp.Call listPage(long listId, int page, AccountHttp.Auth auth) {
        return new AccountHttp.Call(AccountHttp.Method.GET, BASE + "/4/list/" + listId + "?page=" + page, auth, null);
    }

    // ---- Writing -------------------------------------------------------------------------------------------------

    /** A private list, for Seen. */
    static AccountHttp.Call createList(String name) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name).put("description", "").put("iso_639_1", "en").put("public", false);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return post("/4/list", AccountHttp.Auth.USER, o);
    }

    /** Adds a title to the watchlist or the favourites, or takes it off. */
    static AccountHttp.Call setOnAccount(int accountId, String sessionId, SyncList list, String id, boolean on) {
        String which;
        if (list == SyncList.MY_LIST) which = "watchlist";
        else if (list == SyncList.FAVORITES) which = "favorite";
        else throw new IllegalArgumentException(list + " is a TMDB list; use listItems");
        String type = requireType(id);
        JSONObject o = new JSONObject();
        try {
            o.put("media_type", type).put("media_id", number(id)).put(which, on);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return new AccountHttp.Call(AccountHttp.Method.POST, BASE + "/3/account/" + accountId + "/" + which
                + "?session_id=" + encode(sessionId), AccountHttp.Auth.APP, o.toString());
    }

    /** Adds titles to a TMDB list, or removes them. */
    static AccountHttp.Call listItems(long listId, List<String> ids, boolean add) {
        JSONArray items = new JSONArray();
        try {
            for (String id : ids) items.put(new JSONObject().put("media_type", requireType(id)).put("media_id", number(id)));
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return call(add ? AccountHttp.Method.POST : AccountHttp.Method.DELETE, "/4/list/" + listId + "/items",
                AccountHttp.Auth.USER, object("items", items));
    }

    // ---- Helpers -------------------------------------------------------------------------------------------------

    private static String requireType(String id) {
        String type = mediaType(id);
        if (type == null) throw new IllegalArgumentException("not a TMDB title id: " + id);
        return type;
    }

    private static AccountHttp.Call get(String path) {
        return new AccountHttp.Call(AccountHttp.Method.GET, BASE + path, AccountHttp.Auth.USER, null);
    }

    private static AccountHttp.Call post(String path, AccountHttp.Auth auth, JSONObject body) {
        return call(AccountHttp.Method.POST, path, auth, body);
    }

    private static AccountHttp.Call call(AccountHttp.Method method, String path, AccountHttp.Auth auth,
                                         JSONObject body) {
        return new AccountHttp.Call(method, BASE + path, auth, body.toString());
    }

    private static JSONObject object(String key, Object value) {
        try {
            return new JSONObject().put(key, value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String encode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
