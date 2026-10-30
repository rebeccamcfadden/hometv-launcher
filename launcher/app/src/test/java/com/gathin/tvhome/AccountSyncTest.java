package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AccountSyncTest {
    private static final TmdbSession SESSION = new TmdbSession("at", "obj", "sid", 548, "lead");
    private static final AccountSync.Settings SETTINGS = new AccountSync.Settings(ListRef.parse("Seen"),
            ListRef.parse("Household"), ListRef.parse("9001"), true);

    private FakeTmdb tmdb;
    private SyncLedger ledger;
    private Map<SyncList, List<String>> local;

    @Before
    public void setUp() {
        tmdb = new FakeTmdb();
        tmdb.watchlist.addAll(Arrays.asList("movie:1", "tv:2"));
        tmdb.favorites.add("movie:3");
        tmdb.lists.put(50L, new FakeList("Household", new ArrayList<>(Arrays.asList("movie:10", "tv:11")), false));
        tmdb.lists.put(9001L, new FakeList("Friends of ours", new ArrayList<>(Collections.singletonList("tv:12")), true));
        ledger = new SyncLedger();
        ledger.reset("obj");
        local = new EnumMap<>(SyncList.class);
    }

    private AccountSync.Result run(long now, boolean pull) {
        return new AccountSync(tmdb, SESSION, ledger, SETTINGS).run(local, now, pull);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ledgerMustBeTheAccounts() {
        ledger.reset("someone else");
        new AccountSync(tmdb, SESSION, ledger, SETTINGS);
    }

    @Test
    public void firstFullSyncReadsEveryList() {
        AccountSync.Result r = run(1000, true);
        assertFalse(r.signedOut);
        assertEquals(Arrays.asList("movie:1", "tv:2"), r.lists.get(SyncList.MY_LIST));
        assertEquals(Collections.singletonList("movie:3"), r.lists.get(SyncList.FAVORITES));
        assertEquals(Arrays.asList("movie:10", "tv:11"), r.lists.get(SyncList.HOUSEHOLD));
        assertEquals(Collections.singletonList("tv:12"), r.lists.get(SyncList.FRIENDS));
        assertEquals("Seen was made, private", Collections.<String>emptyList(), r.lists.get(SyncList.SEEN));
        long seen = ledger.listId(SyncList.SEEN);
        assertTrue(seen > 0);
        assertFalse(tmdb.lists.get(seen).isPublic);
        assertEquals("Seen", tmdb.lists.get(seen).name);
        assertEquals(50, ledger.listId(SyncList.HOUSEHOLD));
        assertEquals(9001, ledger.listId(SyncList.FRIENDS));
        assertEquals(Arrays.asList("movie:1", "tv:2"), ledger.base(SyncList.MY_LIST));
        assertEquals(1000, ledger.pulledAt(SyncList.MY_LIST));
        assertTrue(r.problems.isEmpty());
        // Reads use the account's token; nothing uses a session id in a v4 call.
        for (String call : tmdb.log) if (call.startsWith("GET")) assertTrue(call, call.endsWith("USER"));
    }

    @Test
    public void readsEveryPage() {
        tmdb.pageSize = 2;
        for (int i = 100; i < 107; i++) tmdb.watchlist.add("movie:" + i);
        AccountSync.Result r = run(1000, true);
        assertEquals(9, r.lists.get(SyncList.MY_LIST).size());
    }

    @Test
    public void tvChangesGoUp() {
        run(1000, true);
        ledger.record(SyncList.MY_LIST, "movie:7", true, 2000);
        ledger.record(SyncList.MY_LIST, "movie:1", false, 2001);
        ledger.record(SyncList.FAVORITES, "tv:2", true, 2002);
        ledger.record(SyncList.SEEN, "movie:1", true, 2003);
        local.put(SyncList.MY_LIST, Arrays.asList("tv:2", "movie:7"));
        AccountSync.Result r = run(3000, false);
        assertEquals(4, r.sent);
        assertEquals(0, r.queued);
        assertTrue("push only reads nothing", r.lists.isEmpty());
        assertEquals(Arrays.asList("tv:2", "movie:7"), tmdb.watchlist);
        assertEquals(Arrays.asList("movie:3", "tv:2"), tmdb.favorites);
        assertEquals(Collections.singletonList("movie:1"), tmdb.lists.get(ledger.listId(SyncList.SEEN)).items);
        assertEquals(Arrays.asList("tv:2", "movie:7"), ledger.base(SyncList.MY_LIST));
    }

    @Test
    public void phoneChangesComeDown() {
        local.put(SyncList.MY_LIST, Arrays.asList("movie:1", "tv:2"));
        run(1000, true);
        tmdb.watchlist.remove("movie:1");
        tmdb.watchlist.add("movie:8");
        AccountSync.Result r = run(5000, true);
        assertEquals(Arrays.asList("tv:2", "movie:8"), r.lists.get(SyncList.MY_LIST));
    }

    @Test
    public void offlineChangesWaitAndRetry() {
        run(1000, true);
        tmdb.offline = true;
        ledger.record(SyncList.MY_LIST, "movie:7", true, 2000);
        ledger.record(SyncList.FAVORITES, "movie:8", true, 2001);
        local.put(SyncList.MY_LIST, Arrays.asList("movie:1", "tv:2", "movie:7"));
        AccountSync.Result r = run(3000, true);
        assertEquals(0, r.sent);
        assertEquals(2, r.queued);
        assertTrue("nothing read offline", r.lists.isEmpty());
        assertFalse(r.problems.isEmpty());
        int calls = tmdb.log.size();
        assertEquals("one try, then the rest wait", 1, ledger.pending().get(0).attempts);
        assertEquals(0, ledger.pending().get(1).attempts);

        tmdb.offline = false;
        r = run(3000 + SyncLedger.RETRY_FIRST - 1, false);
        assertEquals("the first is not due yet; the second goes", 1, r.sent);
        assertEquals(calls + 1, tmdb.log.size());
        r = run(3000 + SyncLedger.RETRY_FIRST, true);
        assertEquals(1, r.sent);
        assertEquals(Arrays.asList("movie:1", "tv:2", "movie:7"), r.lists.get(SyncList.MY_LIST));
        assertTrue(tmdb.watchlist.contains("movie:7"));
    }

    @Test
    public void offlineChangeSurvivesAPhoneChangeMadeBeforeIt() {
        local.put(SyncList.MY_LIST, Arrays.asList("movie:1", "tv:2"));
        run(1000, true);
        // On the phone, after the TV's read: movie:1 goes. On the TV, offline, later: movie:1 is added back.
        tmdb.watchlist.remove("movie:1");
        ledger.record(SyncList.MY_LIST, "movie:1", true, 2000);
        AccountSync.Result r = run(3000, true);
        assertEquals(Arrays.asList("movie:1", "tv:2"), r.lists.get(SyncList.MY_LIST));
        assertTrue(tmdb.watchlist.contains("movie:1"));
    }

    @Test
    public void refusedChangeIsDroppedAndTheNextReadPutsItRight() {
        run(1000, true);
        tmdb.refuseWrites = true;
        ledger.record(SyncList.MY_LIST, "movie:404", true, 2000);
        AccountSync.Result r = run(3000, false);
        assertEquals(0, r.sent);
        assertEquals(0, r.queued);
        local.put(SyncList.MY_LIST, Arrays.asList("movie:1", "tv:2", "movie:404"));
        r = run(4000, true);
        assertEquals(Arrays.asList("movie:1", "tv:2"), r.lists.get(SyncList.MY_LIST));
    }

    @Test
    public void revokedSessionStopsAndKeepsTheQueue() {
        run(1000, true);
        tmdb.revoked = true;
        ledger.record(SyncList.MY_LIST, "movie:7", true, 2000);
        AccountSync.Result r = run(3000, true);
        assertTrue(r.signedOut);
        assertEquals(1, r.queued);
        assertEquals(0, ledger.pending().get(0).attempts);
    }

    @Test
    public void listsFoundByNameFollowARename() {
        run(1000, true);
        tmdb.lists.get(50L).name = "Old household";
        tmdb.lists.put(60L, new FakeList("household", new ArrayList<>(Collections.singletonList("movie:61")), false));
        AccountSync.Result r = run(2000, true);
        assertEquals(60, ledger.listId(SyncList.HOUSEHOLD));
        assertEquals(Collections.singletonList("movie:61"), r.lists.get(SyncList.HOUSEHOLD));
    }

    @Test
    public void missingListIsForgottenAndNoted() {
        run(1000, true);
        tmdb.lists.remove(9001L);
        AccountSync.Result r = run(2000, true);
        assertNull(r.lists.get(SyncList.FRIENDS));
        assertFalse(r.problems.isEmpty());
        assertEquals(0, ledger.listId(SyncList.FRIENDS));
    }

    @Test
    public void seenIsNotMadeWhenNotAllowed() {
        AccountSync.Settings s = new AccountSync.Settings(ListRef.parse("Seen"), ListRef.NONE, ListRef.NONE, false);
        AccountSync.Result r = new AccountSync(tmdb, SESSION, ledger, s).run(local, 1000, true);
        assertNull(r.lists.get(SyncList.SEEN));
        assertNull(r.lists.get(SyncList.HOUSEHOLD));
        assertEquals(0, ledger.listId(SyncList.SEEN));
        ledger.record(SyncList.SEEN, "movie:1", true, 1500);
        r = new AccountSync(tmdb, SESSION, ledger, s).run(local, 2000, false);
        assertEquals("waits for a Seen list", 1, r.queued);
    }

    @Test
    public void firstSignInTakesTheAccountsListsAndSendsNothing() {
        SyncLedger fresh = new SyncLedger();
        Map<SyncList, List<String>> tv = new EnumMap<>(SyncList.class);
        tv.put(SyncList.MY_LIST, Arrays.asList("movie:5", "movie:1"));
        tv.put(SyncList.FAVORITES, Collections.singletonList("tv:7"));
        tv.put(SyncList.SEEN, Collections.singletonList("tv:6"));
        assertEquals(SeedPolicy.FirstSync.ADOPT, SeedPolicy.prepare(fresh, "obj"));
        AccountSync.Result r = new AccountSync(tmdb, SESSION, fresh, SETTINGS).run(tv, 1000, true);
        assertEquals(Arrays.asList("movie:1", "tv:2"), r.lists.get(SyncList.MY_LIST));
        assertEquals(Collections.singletonList("movie:3"), r.lists.get(SyncList.FAVORITES));
        assertEquals("the new private Seen list is empty", Collections.<String>emptyList(), r.lists.get(SyncList.SEEN));
        assertEquals(Arrays.asList("movie:1", "tv:2"), tmdb.watchlist);
        assertEquals(Collections.singletonList("movie:3"), tmdb.favorites);
        assertEquals(0, r.sent);
        for (String call : tmdb.log) assertFalse(call, call.startsWith("POST https://api.themoviedb.org/3/account"));
    }

    @Test
    public void publicListWithoutAnAccount() throws Exception {
        assertEquals(Collections.singletonList("tv:12"), AccountSync.readPublicList(tmdb, 9001));
        assertTrue(tmdb.log.get(tmdb.log.size() - 1).endsWith("APP"));
        try {
            AccountSync.readPublicList(tmdb, 50);
            org.junit.Assert.fail("a private list needs the account");
        } catch (TmdbAccountParse.Failure f) {
            assertEquals(TmdbAccountParse.Problem.REJECTED, f.problem);
        }
        tmdb.offline = true;
        try {
            AccountSync.readPublicList(tmdb, 9001);
            org.junit.Assert.fail();
        } catch (TmdbAccountParse.Failure f) {
            assertEquals(TmdbAccountParse.Problem.RETRY, f.problem);
        }
    }

    @Test
    public void seenStaysLocalWhenNotSet() {
        AccountSync.Settings s = new AccountSync.Settings(ListRef.NONE, ListRef.NONE, ListRef.NONE, false);
        AccountSync.Result r = new AccountSync(tmdb, SESSION, ledger, s).run(local, 1000, true);
        assertFalse(r.lists.containsKey(SyncList.SEEN));
        assertFalse(r.lists.containsKey(SyncList.HOUSEHOLD));
        assertEquals(Arrays.asList("movie:1", "tv:2"), r.lists.get(SyncList.MY_LIST));
        for (String call : tmdb.log) assertFalse(call, call.contains("/4/list") || call.contains("/lists"));
    }

    // ---- A small TMDB --------------------------------------------------------------------------------------------

    private static final class FakeList {
        String name;
        final List<String> items;
        final boolean isPublic;

        FakeList(String name, List<String> items, boolean isPublic) {
            this.name = name;
            this.items = items;
            this.isPublic = isPublic;
        }
    }

    private static final class FakeTmdb implements AccountHttp {
        final List<String> watchlist = new ArrayList<>();
        final List<String> favorites = new ArrayList<>();
        final Map<Long, FakeList> lists = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();
        int pageSize = 20;
        long nextList = 70;
        boolean offline;
        boolean revoked;
        boolean refuseWrites;

        @Override
        public Reply send(Call call) throws IOException {
            log.add(call.method + " " + call.url + " " + call.auth);
            if (offline) throw new IOException("offline");
            if (revoked) return new Reply(401, "{\"success\":false,\"status_code\":3}");
            try {
                URI u = URI.create(call.url);
                String[] p = u.getPath().split("/");
                Map<String, String> q = query(u.getRawQuery());
                if (call.method == Method.GET && p[1].equals("4") && p[2].equals("account")) {
                    assertEquals("obj", p[3]);
                    if (p[4].equals("lists")) return listsPage(Integer.parseInt(q.get("page")));
                    List<String> src = p[5].equals("watchlist") ? watchlist : favorites;
                    List<String> kind = new ArrayList<>();
                    for (String id : src) if (id.startsWith(p[4] + ":")) kind.add(id);
                    return page(kind, Integer.parseInt(q.get("page")), false);
                }
                if (p[1].equals("4") && p[2].equals("list")) {
                    if (p.length == 3) {
                        assertEquals(Method.POST, call.method);
                        JSONObject b = new JSONObject(call.body);
                        long id = nextList++;
                        lists.put(id, new FakeList(b.getString("name"), new ArrayList<String>(), b.getBoolean("public")));
                        return new Reply(201, "{\"success\":true,\"status_code\":1,\"id\":" + id + "}");
                    }
                    FakeList l = lists.get(Long.parseLong(p[3]));
                    if (l == null) return new Reply(404, "{\"success\":false,\"status_code\":34}");
                    if (call.auth == Auth.APP && !l.isPublic) {
                        return new Reply(401, "{\"success\":false,\"status_code\":39}");
                    }
                    if (call.method == Method.GET) return page(l.items, Integer.parseInt(q.get("page")), true);
                    if (refuseWrites) return new Reply(400, "{\"success\":false,\"status_code\":5}");
                    JSONArray items = new JSONObject(call.body).getJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);
                        String id = it.getString("media_type") + ":" + it.getInt("media_id");
                        l.items.remove(id);
                        if (call.method == Method.POST) l.items.add(id);
                    }
                    return new Reply(200, "{\"success\":true,\"status_code\":1,\"results\":[]}");
                }
                if (call.method == Method.POST && p[1].equals("3") && p[2].equals("account")) {
                    assertEquals("548", p[3]);
                    assertEquals("sid", q.get("session_id"));
                    if (refuseWrites) return new Reply(404, "{\"success\":false,\"status_code\":34}");
                    JSONObject b = new JSONObject(call.body);
                    String id = b.getString("media_type") + ":" + b.getInt("media_id");
                    boolean on = b.getBoolean(p[4]);
                    List<String> target = p[4].equals("watchlist") ? watchlist : favorites;
                    target.remove(id);
                    if (on) target.add(id);
                    return new Reply(201, "{\"status_code\":1,\"status_message\":\"Success.\"}");
                }
                return new Reply(404, "{\"success\":false,\"status_code\":34}");
            } catch (org.json.JSONException e) {
                throw new AssertionError(e);
            }
        }

        private Reply page(List<String> ids, int page, boolean withType) throws org.json.JSONException {
            JSONArray results = new JSONArray();
            int from = (page - 1) * pageSize;
            for (int i = from; i < Math.min(ids.size(), from + pageSize); i++) {
                String id = ids.get(i);
                JSONObject o = new JSONObject().put("id", Integer.parseInt(id.substring(id.indexOf(':') + 1)));
                if (withType) o.put("media_type", id.substring(0, id.indexOf(':')));
                results.put(o);
            }
            int total = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
            return new Reply(200, new JSONObject().put("page", page).put("results", results)
                    .put("total_pages", total).put("total_results", ids.size()).toString());
        }

        private Reply listsPage(int page) throws org.json.JSONException {
            JSONArray results = new JSONArray();
            for (Map.Entry<Long, FakeList> e : lists.entrySet()) {
                results.put(new JSONObject().put("id", e.getKey()).put("name", e.getValue().name)
                        .put("public", e.getValue().isPublic ? 1 : 0).put("number_of_items", e.getValue().items.size()));
            }
            return new Reply(200, new JSONObject().put("page", page).put("results", results).put("total_pages", 1)
                    .toString());
        }

        private static Map<String, String> query(String q) {
            Map<String, String> out = new LinkedHashMap<>();
            if (q == null) return out;
            for (String kv : q.split("&")) {
                int eq = kv.indexOf('=');
                out.put(kv.substring(0, eq), kv.substring(eq + 1));
            }
            return out;
        }
    }
}
