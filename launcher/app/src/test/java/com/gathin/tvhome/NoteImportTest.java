package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A phone note onto its TMDB list (BACKLOG.md, Phone notes), against a fake TMDB like AccountSyncTest's. */
public class NoteImportTest {
    private static final TmdbSession SESSION = new TmdbSession("at", "obj", "sid", 548, "lead");

    private FakeTmdb tmdb;
    private final List<String> seen = new ArrayList<>();
    private final List<String> steps = new ArrayList<>();

    @Before
    public void setUp() throws JSONException {
        tmdb = new FakeTmdb();
        tmdb.lists.put(50L, new FakeList("Rebecca", new ArrayList<>(Collections.singletonList("movie:10")), false));
        tmdb.lists.put(51L, new FakeList("Partner", new ArrayList<String>(), true));
        tmdb.movie("Dune", 438631, "Dune", "2021-09-15");
        tmdb.movie("Arrival", 10, "Arrival", "2016-11-10");
        tmdb.show("Fargo", 60622, "Fargo", "2014-04-15");
    }

    private NoteImport.Outcome run(String list, String text, NoteMatch.Memory memory) {
        return new NoteImport(tmdb, SESSION, seen::addAll).run(list, text, memory,
                (done, total) -> steps.add(done + "/" + total));
    }

    private int calls(String prefix) {
        int n = 0;
        for (String c : tmdb.log) if (c.startsWith(prefix)) n++;
        return n;
    }

    @Test
    public void findsTheListByNameIgnoringCaseAndSpaces() {
        NoteImport.Outcome o = run("  rebecca ", "Dune\nFargo (tv)", null);
        assertEquals(NoteImport.Status.DONE, o.status);
        assertEquals(50, o.listId);
        assertFalse(o.created);
        assertEquals(Arrays.asList("movie:10", "movie:438631", "tv:60622"), tmdb.lists.get(50L).items);
        assertEquals(2, o.added);
        assertEquals(2, o.lines);
        assertEquals("no list made", 0, calls("POST https://api.themoviedb.org/4/list "));
    }

    @Test
    public void makesAPrivateListWithTheNameWhenTheAccountHasNone() {
        NoteImport.Outcome o = run("Grandma", "Dune", null);
        assertEquals(NoteImport.Status.DONE, o.status);
        assertTrue(o.created);
        FakeList made = tmdb.lists.get(o.listId);
        assertNotNull(made);
        assertEquals("Grandma", made.name);
        assertFalse("private, like Seen", made.isPublic);
        assertEquals(Collections.singletonList("movie:438631"), made.items);
        assertEquals("the account's lists, with the new one last", o.listId,
                o.accountLists.get(o.accountLists.size() - 1).id);
    }

    @Test
    public void aListNamedInDigitsIsStillAName() {
        NoteImport.Outcome o = run("2024", "Dune", null);
        assertTrue(o.created);
        assertEquals("2024", tmdb.lists.get(o.listId).name);
    }

    @Test
    public void addsOnlyTitlesNotOnTheListAndListsWhatMatchedNothing() {
        tmdb.lists.get(50L).items.add("movie:438631");
        NoteImport.Outcome o = run("Rebecca", "Movies to watch\n- Dune\n- Fargo (tv)\n- Zzyzx Qq", null);
        assertEquals(NoteImport.Status.DONE, o.status);
        assertEquals(1, o.added);
        assertEquals(Arrays.asList("movie:10", "movie:438631", "tv:60622"), tmdb.lists.get(50L).items);
        assertEquals(Collections.singletonList("Zzyzx Qq"), o.unmatched);
        assertEquals(3, o.lines);
    }

    @Test
    public void aTickedTitleGoesToSeenAndComesOffTheList() {
        NoteImport.Outcome o = run("Rebecca", "☐ Dune\n☑ Arrival", null);
        assertEquals(NoteImport.Status.DONE, o.status);
        assertEquals(Collections.singletonList("movie:10"), seen);
        assertEquals(1, o.seen);
        assertEquals(Collections.singletonList("movie:438631"), tmdb.lists.get(50L).items);
        assertEquals(1, calls("DELETE https://api.themoviedb.org/4/list/50/items"));
    }

    @Test
    public void aTickedTitleNotOnTheListOnlyGoesToSeen() {
        NoteImport.Outcome o = run("Partner", "☑ Arrival", null);
        assertEquals(Collections.singletonList("movie:10"), seen);
        assertEquals(0, o.added);
        assertEquals(0, calls("DELETE"));
    }

    @Test
    public void aNoteWhoseEveryLineIsTickedOnlyMarksSeenAndMakesNoList() {
        NoteImport.Outcome o = run("Grandma", "Watched\n☑ Arrival\n[x] Dune", null);
        assertEquals(NoteImport.Status.DONE, o.status);
        assertEquals(Arrays.asList("movie:10", "movie:438631"), seen);
        assertEquals(0, o.listId);
        assertFalse(o.created);
        assertEquals(0, calls("POST https://api.themoviedb.org/4/list "));
        assertNotNull("the lines are remembered", o.memory);
    }

    @Test
    public void anAllTickedNoteForAnExistingListStillTakesThemOff() {
        NoteImport.Outcome o = run("Rebecca", "☑ Arrival", null);
        assertEquals(50, o.listId);
        assertEquals(Collections.singletonList("movie:10"), seen);
        assertTrue(tmdb.lists.get(50L).items.isEmpty());
    }

    @Test
    public void aPickGoesOntoTheListAndIsRemembered() {
        NoteMatch.Ask ask = new NoteMatch.Ask("zzyzx qq", "Zzyzx Qq", false, null);
        NoteImport.Outcome o = new NoteImport(tmdb, SESSION, seen::addAll).pick("Rebecca", ask, "tv:60622", null);
        assertEquals(NoteImport.Status.DONE, o.status);
        assertEquals(50, o.listId);
        assertEquals(1, o.added);
        assertEquals(Arrays.asList("movie:10", "tv:60622"), tmdb.lists.get(50L).items);
        assertEquals("tv:60622", o.memory.get("Rebecca", "zzyzx qq"));
        // Picked again (or already there): nothing more is written.
        tmdb.log.clear();
        assertEquals(0, new NoteImport(tmdb, SESSION, seen::addAll).pick("Rebecca", ask, "tv:60622", o.memory).added);
        assertEquals(0, calls("POST"));
    }

    @Test
    public void aTickedPickGoesToSeenAndNoneOfTheseIsRemembered() {
        NoteImport n = new NoteImport(tmdb, SESSION, seen::addAll);
        NoteImport.Outcome o = n.pick("Rebecca", new NoteMatch.Ask("a", "A", true, null), "movie:10", null);
        assertEquals(Collections.singletonList("movie:10"), seen);
        assertEquals(0, o.listId);
        assertEquals(0, calls("POST"));
        NoteImport.Outcome none = n.pick("Rebecca", new NoteMatch.Ask("b", "B", false, null), null, null);
        assertEquals(NoteImport.Status.DONE, none.status);
        assertEquals(NoteMatch.DISMISSED, none.memory.get("Rebecca", "b"));
        assertEquals(0, calls("POST"));
    }

    @Test
    public void aLineWithNoAnswersSavedIsLookedUpWithTheExactOneFirst() {
        List<NoteMatch.Candidate> top = new NoteImport(tmdb, SESSION, seen::addAll).lookUp("Dune");
        assertEquals("movie:438631", top.get(0).id);
        tmdb.offline = true;
        assertNull("offline", new NoteImport(tmdb, SESSION, seen::addAll).lookUp("Dune"));
    }

    @Test
    public void searchingAgainLooksUpWhatWasTypedNotTheLine() {
        // Search again (F9): the household fixes "Dun" to "dune" on the TV, and that text is what TMDB is asked.
        List<NoteMatch.Candidate> top = new NoteImport(tmdb, SESSION, seen::addAll).lookUp("dune");
        assertEquals("movie:438631", top.get(0).id);
        assertTrue(new NoteImport(tmdb, SESSION, seen::addAll).lookUp("   ").isEmpty());
    }

    @Test
    public void aPickForAListThatIsGoneMakesIt() {
        NoteImport.Outcome o = new NoteImport(tmdb, SESSION, seen::addAll).pick("Grandma",
                new NoteMatch.Ask("x", "X", false, null), "movie:438631", null);
        assertTrue(o.created);
        assertEquals(Collections.singletonList("movie:438631"), tmdb.lists.get(o.listId).items);
    }

    @Test
    public void theSameNoteAgainAddsNothingAndLooksNothingUp() {
        NoteImport.Outcome first = run("Rebecca", "Dune\nFargo (tv)\nZzyzx Qq", null);
        int searches = calls("GET https://api.themoviedb.org/3/search");
        tmdb.log.clear();
        NoteImport.Outcome again = run("Rebecca", "Dune\nFargo (tv)\nZzyzx Qq", first.memory);
        assertEquals(NoteImport.Status.DONE, again.status);
        assertEquals(0, again.added);
        assertEquals(0, calls("GET https://api.themoviedb.org/3/search"));
        assertEquals(0, calls("POST"));
        assertEquals("still not matched, still listed", Collections.singletonList("Zzyzx Qq"), again.unmatched);
        assertEquals("Dune and Zzyzx Qq as movies and shows, Fargo as a show", 5, searches);
    }

    @Test
    public void aListDeletedOnTmdbIsMadeAgainWithEveryLine() {
        NoteImport.Outcome first = run("Rebecca", "Dune\nFargo (tv)", null);
        assertEquals(2, first.added);
        tmdb.lists.remove(50L);
        NoteImport.Outcome again = run("Rebecca", "Dune\nFargo (tv)", first.memory);
        assertTrue(again.created);
        assertEquals(2, again.added);
        assertEquals(Arrays.asList("movie:438631", "tv:60622"), tmdb.lists.get(again.listId).items);
    }

    @Test
    public void aTitleTakenOffOnTheTvIsNotPutBack() {
        NoteImport.Outcome first = run("Rebecca", "Dune", null);
        tmdb.lists.get(50L).items.remove("movie:438631");
        NoteImport.Outcome again = run("Rebecca", "Dune\nFargo (tv)", first.memory);
        assertEquals(1, again.added);
        assertEquals(Arrays.asList("movie:10", "tv:60622"), tmdb.lists.get(50L).items);
    }

    @Test
    public void theMemoryHandedInIsNotChanged() {
        NoteMatch.Memory memory = new NoteMatch.Memory();
        run("Rebecca", "Dune", memory);
        assertNull(memory.get("Rebecca", "dune"));
    }

    @Test
    public void aRefusedListWriteKeepsTheNoteWholeForNextTime() {
        tmdb.refuseWrites = true;
        NoteImport.Outcome failed = run("Rebecca", "Dune\nFargo (tv)", null);
        assertEquals(NoteImport.Status.FAILED, failed.status);
        assertNull("nothing remembered", failed.memory);
        assertEquals(Collections.singletonList("movie:10"), tmdb.lists.get(50L).items);
        tmdb.refuseWrites = false;
        NoteImport.Outcome later = run("Rebecca", "Dune\nFargo (tv)", failed.memory);
        assertEquals(NoteImport.Status.DONE, later.status);
        assertEquals(2, later.added);
    }

    @Test
    public void makingTheListRefusedIsAFailureToo() {
        tmdb.refuseWrites = true;
        NoteImport.Outcome o = run("Grandma", "Dune", null);
        assertEquals(NoteImport.Status.FAILED, o.status);
        assertEquals(0, o.listId);
        assertNull(o.memory);
    }

    @Test
    public void offlineWaits() {
        tmdb.offline = true;
        NoteImport.Outcome o = run("Rebecca", "Dune", null);
        assertEquals(NoteImport.Status.OFFLINE, o.status);
        assertNull(o.memory);
    }

    @Test
    public void lookupsThatFailAreTriedAgainAndTheRestStays() {
        tmdb.searchDown = "Fargo";
        NoteImport.Outcome o = run("Rebecca", "Dune\nFargo (tv)", null);
        assertEquals("the note waits for the line that couldn't be looked up", NoteImport.Status.OFFLINE, o.status);
        assertEquals(1, o.added);
        assertNotNull(o.memory);
        tmdb.searchDown = null;
        tmdb.log.clear();
        NoteImport.Outcome later = run("Rebecca", "Dune\nFargo (tv)", o.memory);
        assertEquals(NoteImport.Status.DONE, later.status);
        assertEquals(1, later.added);
        assertEquals("only Fargo is looked up again", 1, calls("GET https://api.themoviedb.org/3/search"));
    }

    @Test
    public void aRefusedSessionWaitsForASignIn() {
        tmdb.revoked = true;
        assertEquals(NoteImport.Status.SIGNED_OUT, run("Rebecca", "Dune", null).status);
    }

    @Test
    public void progressCountsEveryLineFromTheStart() {
        NoteMatch.Memory memory = run("Rebecca", "Dune", null).memory;
        steps.clear();
        // Dune is known, so it counts at once; the pill reads "0 of 3" before anything is looked up.
        run("Rebecca", "Dune\nFargo (tv)\nZzyzx Qq", memory);
        assertEquals(Arrays.asList("0/3", "1/3", "2/3", "3/3"), steps);
    }

    @Test
    public void manyTitlesGoInBatches() throws JSONException {
        StringBuilder note = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            tmdb.movie("Film " + i, 1000 + i, "Film " + i, "2020-01-01");
            note.append("Film ").append(i).append('\n');
        }
        NoteImport.Outcome o = run("Partner", note.toString(), null);
        assertEquals(25, o.added);
        assertEquals(25, tmdb.lists.get(51L).items.size());
        assertEquals(2, calls("POST https://api.themoviedb.org/4/list/51/items"));
    }

    @Test
    public void searchesUseTheAppsTokenAndListCallsTheAccounts() {
        run("Rebecca", "Dune", null);
        for (String c : tmdb.log) {
            if (c.contains("/3/search/")) assertTrue(c, c.endsWith("APP"));
            else assertTrue(c, c.endsWith("USER"));
        }
    }

    @Test
    public void readsEveryPageOfTheAccountsListsAndTheList() {
        tmdb.pageSize = 1;
        for (long id = 60; id < 64; id++) tmdb.lists.put(id, new FakeList("Other " + id, new ArrayList<String>(), false));
        tmdb.lists.put(70L, new FakeList("Last", new ArrayList<>(Arrays.asList("movie:438631", "tv:60622")), false));
        NoteImport.Outcome o = run("Last", "Dune\nFargo (tv)", null);
        assertEquals(70, o.listId);
        assertEquals("both already there", 0, o.added);
    }

    // ---- A fake TMDB -------------------------------------------------------------------------------------------------

    private static final class FakeList {
        final String name;
        final List<String> items;
        final boolean isPublic;

        FakeList(String name, List<String> items, boolean isPublic) {
            this.name = name;
            this.items = items;
            this.isPublic = isPublic;
        }
    }

    private static final class FakeTmdb implements AccountHttp {
        final Map<Long, FakeList> lists = new LinkedHashMap<>();
        final Map<String, JSONArray> search = new HashMap<>();
        final List<String> log = new ArrayList<>();
        int pageSize = 20;
        long nextList = 90;
        boolean offline, revoked, refuseWrites;
        /** A query whose search fails, as if the line's lookup timed out. */
        String searchDown;

        void movie(String query, long id, String title, String date) throws JSONException {
            add("movie:" + query, new JSONObject().put("id", id).put("title", title).put("original_title", title)
                    .put("release_date", date).put("popularity", 10));
        }

        void show(String query, long id, String name, String date) throws JSONException {
            add("tv:" + query, new JSONObject().put("id", id).put("name", name).put("original_name", name)
                    .put("first_air_date", date).put("popularity", 10));
        }

        private void add(String key, JSONObject result) {
            // TMDB's search ignores case.
            key = key.toLowerCase(java.util.Locale.ROOT);
            JSONArray a = search.get(key);
            if (a == null) search.put(key, a = new JSONArray());
            a.put(result);
        }

        @Override
        public Reply send(Call call) throws IOException {
            log.add(call.method + " " + call.url + " " + call.auth);
            if (offline) throw new IOException("offline");
            try {
                URI u = URI.create(call.url);
                String[] p = u.getPath().split("/");
                Map<String, String> q = query(u.getRawQuery());
                if (p[1].equals("3") && p[2].equals("search")) {
                    assertEquals(Auth.APP, call.auth);
                    String text = URLDecoder.decode(q.get("query"), "UTF-8");
                    if (text.equals(searchDown)) throw new IOException("timeout");
                    JSONArray a = search.get((p[3] + ":" + text).toLowerCase(java.util.Locale.ROOT));
                    return new Reply(200, new JSONObject().put("results", a == null ? new JSONArray() : a).toString());
                }
                if (revoked) return new Reply(401, "{\"success\":false,\"status_code\":3}");
                if (call.method == Method.GET && p[1].equals("4") && p[2].equals("account")) {
                    assertEquals("obj", p[3]);
                    return listsPage(Integer.parseInt(q.get("page")));
                }
                if (p[1].equals("4") && p[2].equals("list")) {
                    if (p.length == 3) {
                        assertEquals(Method.POST, call.method);
                        if (refuseWrites) return new Reply(400, "{\"success\":false,\"status_code\":5}");
                        JSONObject b = new JSONObject(call.body);
                        long id = nextList++;
                        lists.put(id, new FakeList(b.getString("name"), new ArrayList<String>(), b.getBoolean("public")));
                        return new Reply(201, "{\"success\":true,\"status_code\":1,\"id\":" + id + "}");
                    }
                    FakeList l = lists.get(Long.parseLong(p[3]));
                    if (l == null) return new Reply(404, "{\"success\":false,\"status_code\":34}");
                    if (call.method == Method.GET) return page(l.items, Integer.parseInt(q.get("page")), l.name);
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
                return new Reply(404, "{\"success\":false,\"status_code\":34}");
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }

        private Reply page(List<String> ids, int page, String name) throws JSONException {
            JSONArray results = new JSONArray();
            int from = (page - 1) * pageSize;
            for (int i = from; i < Math.min(ids.size(), from + pageSize); i++) {
                String id = ids.get(i);
                results.put(new JSONObject().put("id", Integer.parseInt(id.substring(id.indexOf(':') + 1)))
                        .put("media_type", id.substring(0, id.indexOf(':'))));
            }
            int total = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
            return new Reply(200, new JSONObject().put("page", page).put("results", results).put("total_pages", total)
                    .put("name", name).toString());
        }

        private Reply listsPage(int page) throws JSONException {
            List<Map.Entry<Long, FakeList>> all = new ArrayList<>(lists.entrySet());
            JSONArray results = new JSONArray();
            int from = (page - 1) * pageSize;
            for (int i = from; i < Math.min(all.size(), from + pageSize); i++) {
                Map.Entry<Long, FakeList> e = all.get(i);
                results.put(new JSONObject().put("id", e.getKey()).put("name", e.getValue().name)
                        .put("public", e.getValue().isPublic ? 1 : 0).put("number_of_items", e.getValue().items.size()));
            }
            int total = Math.max(1, (all.size() + pageSize - 1) / pageSize);
            return new Reply(200, new JSONObject().put("page", page).put("results", results).put("total_pages", total)
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
