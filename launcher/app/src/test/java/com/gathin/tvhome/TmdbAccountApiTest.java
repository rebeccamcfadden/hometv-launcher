package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class TmdbAccountApiTest {
    @Test
    public void titleIds() {
        assertEquals("movie", TmdbAccountApi.mediaType("movie:550"));
        assertEquals("tv", TmdbAccountApi.mediaType("tv:1396"));
        assertNull(TmdbAccountApi.mediaType("arrival"));
        assertNull(TmdbAccountApi.mediaType("movie:"));
        assertNull(TmdbAccountApi.mediaType("movie:0"));
        assertNull(TmdbAccountApi.mediaType("movie:12a"));
        assertNull(TmdbAccountApi.mediaType("person:5"));
        assertNull(TmdbAccountApi.mediaType(null));
        assertEquals(550, TmdbAccountApi.number("movie:550"));
        assertEquals("movie:550", TmdbAccountApi.id("movie", 550));
        assertEquals("tv:1396", TmdbAccountApi.id("tv", 1396));
        assertNull(TmdbAccountApi.id("person", 5));
        assertNull(TmdbAccountApi.id("movie", 0));
    }

    @Test
    public void signInCalls() throws Exception {
        AccountHttp.Call c = TmdbAccountApi.requestToken();
        assertEquals(AccountHttp.Method.POST, c.method);
        assertEquals("https://api.themoviedb.org/4/auth/request_token", c.url);
        assertEquals(AccountHttp.Auth.APP, c.auth);
        assertEquals(0, new JSONObject(c.body).length());

        c = TmdbAccountApi.accessToken("rt");
        assertEquals("https://api.themoviedb.org/4/auth/access_token", c.url);
        assertEquals(AccountHttp.Method.POST, c.method);
        assertEquals(AccountHttp.Auth.APP, c.auth);
        assertEquals("rt", new JSONObject(c.body).getString("request_token"));

        c = TmdbAccountApi.session("at");
        assertEquals("https://api.themoviedb.org/3/authentication/session/convert/4", c.url);
        assertEquals("at", new JSONObject(c.body).getString("access_token"));

        c = TmdbAccountApi.account("s 1");
        assertEquals(AccountHttp.Method.GET, c.method);
        assertEquals("https://api.themoviedb.org/3/account?session_id=s+1", c.url);
        assertNull(c.body);

        c = TmdbAccountApi.logout("at");
        assertEquals(AccountHttp.Method.DELETE, c.method);
        assertEquals("https://api.themoviedb.org/4/auth/access_token", c.url);
        assertEquals("at", new JSONObject(c.body).getString("access_token"));

        c = TmdbAccountApi.deleteSession("sid");
        assertEquals(AccountHttp.Method.DELETE, c.method);
        assertEquals("https://api.themoviedb.org/3/authentication/session", c.url);
        assertEquals("sid", new JSONObject(c.body).getString("session_id"));
    }

    @Test
    public void approveUrlEncodesTheToken() {
        assertEquals("https://www.themoviedb.org/auth/access?request_token=a.b-c_d", TmdbAccountApi.approveUrl("a.b-c_d"));
        assertEquals("https://www.themoviedb.org/auth/access?request_token=a%2Bb%3D", TmdbAccountApi.approveUrl("a+b="));
    }

    @Test
    public void readCalls() {
        AccountHttp.Call c = TmdbAccountApi.accountPage("abc", SyncList.MY_LIST, TmdbAccountApi.Kind.MOVIE, 2);
        assertEquals(AccountHttp.Method.GET, c.method);
        assertEquals("https://api.themoviedb.org/4/account/abc/movie/watchlist?page=2&sort_by=created_at.asc", c.url);
        assertEquals(AccountHttp.Auth.USER, c.auth);
        c = TmdbAccountApi.accountPage("abc", SyncList.FAVORITES, TmdbAccountApi.Kind.TV, 1);
        assertEquals("https://api.themoviedb.org/4/account/abc/tv/favorites?page=1&sort_by=created_at.asc", c.url);
        assertEquals("https://api.themoviedb.org/4/account/abc/lists?page=3", TmdbAccountApi.accountLists("abc", 3).url);
        assertEquals("https://api.themoviedb.org/4/list/8221116?page=1", TmdbAccountApi.listPage(8221116, 1).url);
        try {
            TmdbAccountApi.accountPage("abc", SyncList.SEEN, TmdbAccountApi.Kind.MOVIE, 1);
            fail();
        } catch (IllegalArgumentException expected) {
            // Seen is a TMDB list.
        }
    }

    @Test
    public void watchlistAndFavoriteWrites() throws Exception {
        AccountHttp.Call c = TmdbAccountApi.setOnAccount(42, "sid", SyncList.MY_LIST, "tv:1396", true);
        assertEquals(AccountHttp.Method.POST, c.method);
        assertEquals("https://api.themoviedb.org/3/account/42/watchlist?session_id=sid", c.url);
        assertEquals(AccountHttp.Auth.APP, c.auth);
        JSONObject b = new JSONObject(c.body);
        assertEquals("tv", b.getString("media_type"));
        assertEquals(1396, b.getInt("media_id"));
        assertTrue(b.getBoolean("watchlist"));

        c = TmdbAccountApi.setOnAccount(42, "sid", SyncList.FAVORITES, "movie:550", false);
        assertEquals("https://api.themoviedb.org/3/account/42/favorite?session_id=sid", c.url);
        b = new JSONObject(c.body);
        assertFalse(b.getBoolean("favorite"));
        assertEquals("movie", b.getString("media_type"));

        try {
            TmdbAccountApi.setOnAccount(42, "sid", SyncList.MY_LIST, "arrival", true);
            fail();
        } catch (IllegalArgumentException expected) {
            // Only TMDB titles go to the account.
        }
    }

    @Test
    public void listWrites() throws Exception {
        AccountHttp.Call c = TmdbAccountApi.listItems(7, Arrays.asList("movie:550", "tv:1396"), true);
        assertEquals(AccountHttp.Method.POST, c.method);
        assertEquals("https://api.themoviedb.org/4/list/7/items", c.url);
        assertEquals(AccountHttp.Auth.USER, c.auth);
        JSONArray items = new JSONObject(c.body).getJSONArray("items");
        assertEquals(2, items.length());
        assertEquals("movie", items.getJSONObject(0).getString("media_type"));
        assertEquals(550, items.getJSONObject(0).getInt("media_id"));
        assertEquals("tv", items.getJSONObject(1).getString("media_type"));
        assertEquals(AccountHttp.Method.DELETE, TmdbAccountApi.listItems(7, Arrays.asList("movie:550"), false).method);

        c = TmdbAccountApi.createList("Seen");
        assertEquals("https://api.themoviedb.org/4/list", c.url);
        assertEquals(AccountHttp.Auth.USER, c.auth);
        JSONObject b = new JSONObject(c.body);
        assertEquals("Seen", b.getString("name"));
        assertFalse(b.getBoolean("public"));
        assertEquals("en", b.getString("iso_639_1"));
    }

    @Test
    public void callsNeverShowASessionOrBody() {
        String shown = TmdbAccountApi.setOnAccount(42, "secret-session", SyncList.MY_LIST, "movie:1", true).toString();
        assertFalse(shown.contains("secret-session"));
        assertTrue(shown.contains("session_id=…"));
        assertFalse(TmdbAccountApi.session("secret-token").toString().contains("secret-token"));
    }
}
