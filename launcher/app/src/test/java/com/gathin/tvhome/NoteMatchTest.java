package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NoteMatchTest {
    /** Canned TMDB search answers by query and type; offline when {@link #offline} is set. */
    private final Map<String, JSONArray> answers = new HashMap<>();
    private final List<String> asked = new ArrayList<>();
    private boolean offline;

    private final NoteMatch match = new NoteMatch(path -> {
        asked.add(path);
        if (offline) throw new IOException("offline");
        String type = path.startsWith("/search/movie") ? "movie" : "tv";
        String q = java.net.URLDecoder.decode(path.replaceAll(".*query=([^&]*).*", "$1"), "UTF-8");
        JSONArray a = answers.get(type + ":" + q);
        try {
            return new JSONObject().put("results", a == null ? new JSONArray() : a);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    });

    private static JSONObject movie(long id, String title, String date, double pop) throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("original_title", title).put("release_date", date)
                .put("popularity", pop);
    }

    private static JSONObject show(long id, String name, String date, double pop) throws JSONException {
        return new JSONObject().put("id", id).put("name", name).put("original_name", name).put("first_air_date", date)
                .put("popularity", pop);
    }

    private void answer(String key, JSONObject... results) {
        answers.put(key, new JSONArray(Arrays.asList(results)));
    }

    private static NoteLines.Line line(String text) {
        return NoteLines.parse(text).get(0);
    }

    @Test
    public void exactTitleWinsOverASequelAndTheMostPopularExactOneWins() throws Exception {
        answer("movie:Dune", movie(438631, "Dune", "2021-09-15", 90), movie(693134, "Dune: Part Two", "2024-02-27", 300),
                movie(841, "Dune", "1984-12-14", 20));
        assertEquals("movie:438631", match.match(line("Dune")));
    }

    @Test
    public void theYearPicksTheOlderOne() throws Exception {
        answer("movie:Dune", movie(438631, "Dune", "2021-09-15", 90), movie(841, "Dune", "1984-12-14", 20));
        assertEquals("movie:841", match.match(line("Dune (1984)")));
        assertTrue(asked.get(0), asked.get(0).contains("&year=1984"));
    }

    @Test
    public void accentsCaseAndPunctuationDontMatter() throws Exception {
        answer("movie:amelie", movie(194, "Amélie", "2001-04-25", 30));
        assertEquals("movie:194", match.match(line("amelie")));
        answer("movie:Knives Out!", movie(546554, "Knives Out", "2019-11-27", 40));
        assertEquals("movie:546554", match.match(line("Knives Out!")));
    }

    @Test
    public void aShowLineOnlyAsksForShows() throws Exception {
        answer("tv:Fargo", show(60622, "Fargo", "2014-04-15", 50));
        answer("movie:Fargo", movie(275, "Fargo", "1996-03-08", 30));
        assertEquals("tv:60622", match.match(line("Fargo S2")));
        assertEquals(1, asked.size());
        assertTrue(asked.get(0).startsWith("/search/tv"));
    }

    @Test
    public void withoutAKindTheMorePopularExactTitleWins() throws Exception {
        answer("tv:Fargo", show(60622, "Fargo", "2014-04-15", 50));
        answer("movie:Fargo", movie(275, "Fargo", "1996-03-08", 30));
        assertEquals("tv:60622", match.match(line("Fargo")));
    }

    @Test
    public void onlyALeadingTheIsForgiven() throws Exception {
        answer("tv:Bear", show(136315, "The Bear", "2022-06-23", 80));
        assertEquals("tv:136315", match.match(line("Bear (tv)")));
        answer("movie:Lord of the Rings", movie(120, "The Lord of the Rings: The Fellowship of the Ring", "2001", 99));
        assertNull(match.match(line("Lord of the Rings")));
        answer("movie:Dun", movie(438631, "Dune", "2021-09-15", 90));
        assertNull(match.match(line("Dun")));
    }

    @Test
    public void aSlipOfTheKeyboardIsAskedWithTheRightOneFirst() throws Exception {
        // Not exact, so asked on the TV (the project lead, 2026-11-01), with the near spelling offered first.
        answer("movie:Inglorious basterds", movie(1, "Basterds Reunion", "2020", 90),
                movie(16869, "Inglourious Basterds", "2009-08-02", 60));
        NoteMatch.Found f = match.find(line("Inglorious basterds"));
        assertNull(f.id);
        assertEquals("movie:16869", f.top.get(0).id);
        answer("movie:Dead poet society", movie(207, "Dead Poets Society", "1989-06-02", 40));
        assertNull(match.match(line("Dead poet society")));
        assertEquals("movie:207", match.find(line("Dead poet society")).top.get(0).id);
        // A leading "The" is still exact: straight in.
        answer("movie:Birdcage", movie(11000, "The Birdcage", "1996-03-08", 20));
        assertEquals("movie:11000", match.match(line("Birdcage")));
        answer("movie:Angels and demons", movie(13448, "Angels & Demons", "2009-05-13", 30));
        assertEquals("movie:13448", match.match(line("Angels and demons")));
    }

    @Test
    public void anAskOffersAtMostThreeWithTheirYearAndPoster() throws Exception {
        answer("movie:Arival", movie(1, "Arrivals", "2020-01-01", 9).put("poster_path", "/a.jpg"),
                movie(329865, "Arrival", "2016-11-10", 50).put("poster_path", "/b.jpg"),
                movie(3, "Arrival Day", "2019", 5), movie(4, "Late Arrival", "2001", 1));
        answer("tv:Arival", show(5, "Arrival TV", "2010", 2));
        List<NoteMatch.Candidate> top = match.find(line("Arival")).top;
        assertEquals(NoteMatch.ASK_TOP, top.size());
        // "Arrival" is one letter from "Arival" (six letters), so it comes first; then TMDB's order.
        assertEquals("movie:329865", top.get(0).id);
        assertEquals("/b.jpg", top.get(0).poster);
        assertEquals(2016, top.get(0).year);
        assertEquals("movie:1", top.get(1).id);
    }

    @Test
    public void aLinesYearPutsThatYearsAnswersFirst() throws Exception {
        answer("movie:Little women", movie(1, "Little Women", "1994-12-21", 30), movie(331482, "Little Women",
                "2019-12-25", 60));
        // Exact with the year: straight in.
        assertEquals("movie:1", match.match(line("Little women (1994)")));
        answer("movie:Litle women", movie(331482, "Little Women", "2019-12-25", 60),
                movie(1, "Little Women", "1994-12-21", 30));
        assertEquals("movie:1", match.find(line("Litle women (1994)")).top.get(0).id);
    }

    @Test
    public void aDismissedLineIsNeitherListedNorLookedUp() throws Exception {
        NoteMatch.Memory memory = new NoteMatch.Memory();
        memory.put("Rebecca", NoteLines.parse("Zzyzx Qq").get(0).key, NoteMatch.DISMISSED);
        NoteMatch.Plan p = match.plan("Rebecca", NoteLines.parse("Zzyzx Qq"), memory, new HashSet<String>(), null);
        assertTrue(p.unmatched.isEmpty());
        assertTrue(p.asks.isEmpty());
        assertTrue(asked.isEmpty());
    }

    @Test
    public void anAskSurvivesSavingAndAnOldTvsPlainLine() throws Exception {
        NoteMatch.Candidate c = new NoteMatch.Candidate("movie:207", "Dead Poets Society", "", 1989, "/p.jpg", 4, 0);
        NoteMatch.Ask a = new NoteMatch.Ask("dead poet society", "Dead poet society", true, Collections.singletonList(c));
        NoteMatch.Ask back = NoteMatch.Ask.fromJson(a.toJson());
        assertEquals("dead poet society", back.key);
        assertTrue(back.ticked);
        assertEquals("movie:207", back.top.get(0).id);
        assertEquals("/p.jpg", back.top.get(0).poster);
        assertEquals(1989, back.top.get(0).year);
        NoteMatch.Ask old = NoteMatch.Ask.fromJson("Zzyzx Qq");
        assertEquals("Zzyzx Qq", old.query);
        assertTrue(old.top.isEmpty());
        assertTrue("only movie:N and tv:N ids are read back", NoteMatch.Ask.fromJson(new JSONObject().put("query", "x")
                .put("top", new JSONArray().put(new JSONObject().put("id", "javascript:1")))).top.isEmpty());
    }

    @Test
    public void notesAndDescriptionsDontMatchSomethingElse() throws Exception {
        answer("movie:Spotlight is pm", movie(314365, "Spotlight", "2015-11-06", 30));
        assertNull(match.match(line("Spotlight is pm")));
        answer("movie:Billie Jean king movie", movie(339692, "Battle of the Sexes", "2017-09-22", 20));
        assertNull(match.match(line("Billie Jean king movie")));
        // Not the second answer, and not a short title two letters out.
        answer("movie:Arival", movie(1, "Arrivals", "2020", 9), movie(329865, "Arrival", "2016-11-10", 50));
        assertNull(match.match(line("Arival")));
        answer("movie:Coda", movie(776503, "Cobra", "2021", 50));
        assertNull(match.match(line("Coda")));
    }

    @Test
    public void aHeadingAsksForItsKindFirst() throws Exception {
        answer("movie:Slow Horses", movie(9, "Slow Horses", "2010", 1));
        answer("tv:Slow Horses", show(95480, "Slow Horses", "2022-04-01", 70));
        assertEquals("tv:95480", match.match(NoteLines.parse("Shows to watch\n- Slow Horses\n- Dark").get(0)));
        assertEquals(1, asked.size());
        assertTrue(asked.get(0).startsWith("/search/tv"));
    }

    @Test
    public void aHeadingFallsBackToTheOtherKind() throws Exception {
        answer("tv:Fargo", show(60622, "Fargo", "2014-04-15", 50));
        assertEquals("tv:60622", match.match(NoteLines.parse("Movies to watch\n- Fargo\n- Dune").get(0)));
        assertEquals(2, asked.size());
        assertTrue(asked.get(0).startsWith("/search/movie"));
        assertTrue(asked.get(1).startsWith("/search/tv"));
    }

    @Test
    public void twoSpellingsOfOneFilmAddItOnce() throws Exception {
        answer("movie:Synecdoche, New York", movie(4960, "Synecdoche, New York", "2008-10-24", 10));
        answer("movie:Synecdoche New York", movie(4960, "Synecdoche, New York", "2008-10-24", 10));
        NoteMatch.Plan p = match.plan("t", NoteLines.parse(NoteLinesTest.APPLE), new NoteMatch.Memory(),
                Collections.<String>emptySet(), null);
        assertEquals(1, Collections.frequency(p.add, "movie:4960"));
    }

    @Test
    public void aLinkNeedsNoSearch() throws Exception {
        assertEquals("movie:603", match.match(line("https://www.themoviedb.org/movie/603-the-matrix")));
        assertTrue(asked.isEmpty());
    }

    @Test
    public void planAddsNewLinesOnceAndListsWhatDidntMatch() throws Exception {
        answer("movie:Dune", movie(438631, "Dune", "2021-09-15", 90));
        answer("movie:Arrival", movie(329865, "Arrival", "2016-11-10", 60));
        NoteMatch.Memory memory = new NoteMatch.Memory();
        List<int[]> steps = new ArrayList<>();
        NoteMatch.Plan p = match.plan("Rebecca", NoteLines.parse("☐ Dune\n☐ Arrival\n☐ Zzyzx Qq"), memory,
                Collections.<String>emptySet(), (done, total) -> steps.add(new int[] {done, total}));
        assertEquals(Arrays.asList("movie:438631", "movie:329865"), p.add);
        assertEquals(Collections.singletonList("Zzyzx Qq"), p.unmatched);
        assertEquals(3, p.looked);
        assertEquals(4, steps.size());
        assertEquals(3, steps.get(3)[0]);
        assertEquals(3, steps.get(3)[1]);

        // The same note again: nothing looked up, nothing added (the household may have taken Dune off on the TV).
        asked.clear();
        NoteMatch.Plan again = match.plan("rebecca", NoteLines.parse("☐ Dune\n☐ Arrival\n☐ Zzyzx Qq"), memory,
                Collections.<String>emptySet(), null);
        assertTrue(asked.isEmpty());
        assertTrue(again.add.isEmpty());
        assertEquals(0, again.looked);
        assertEquals(Collections.singletonList("Zzyzx Qq"), again.unmatched);
    }

    @Test
    public void aTickedLineGoesToSeenEvenWhenItWasKnown() throws Exception {
        answer("movie:Dune", movie(438631, "Dune", "2021-09-15", 90));
        NoteMatch.Memory memory = new NoteMatch.Memory();
        match.plan("A", NoteLines.parse("- Dune"), memory, Collections.<String>emptySet(), null);
        NoteMatch.Plan p = match.plan("A", NoteLines.parse("- [x] Dune"), memory, Collections.<String>emptySet(), null);
        assertEquals(Collections.singletonList("movie:438631"), p.seen);
        assertTrue(p.add.isEmpty());
    }

    @Test
    public void aTitleAlreadyOnTheListIsNotAddedAgain() throws Exception {
        answer("movie:Dune", movie(438631, "Dune", "2021-09-15", 90));
        Set<String> on = new HashSet<>(Collections.singletonList("movie:438631"));
        assertTrue(match.plan("A", NoteLines.parse("Dune"), new NoteMatch.Memory(), on, null).add.isEmpty());
    }

    @Test
    public void offlineLinesAreTriedAgainNextTime() throws Exception {
        NoteMatch.Memory memory = new NoteMatch.Memory();
        offline = true;
        NoteMatch.Plan p = match.plan("A", NoteLines.parse("Dune"), memory, Collections.<String>emptySet(), null);
        assertEquals(1, p.failed);
        assertTrue(p.add.isEmpty());
        assertTrue(p.unmatched.isEmpty());
        offline = false;
        answer("movie:Dune", movie(438631, "Dune", "2021-09-15", 90));
        assertEquals(Collections.singletonList("movie:438631"),
                match.plan("A", NoteLines.parse("Dune"), memory, Collections.<String>emptySet(), null).add);
    }

    @Test
    public void memoryRoundTripsThroughJson() throws Exception {
        NoteMatch.Memory m = new NoteMatch.Memory();
        m.put("Rebecca", "dune", "movie:438631");
        m.put("Rebecca", "zzyzx", "");
        m.put("Partner", "fargo [tv]", "tv:60622");
        NoteMatch.Memory back = NoteMatch.Memory.fromJson(new JSONObject(m.toJson().toString()));
        assertEquals("movie:438631", back.get("rebecca", "dune"));
        assertEquals("", back.get("REBECCA", "zzyzx"));
        assertEquals("tv:60622", back.get("Partner", "fargo [tv]"));
        assertNull(back.get("Partner", "dune"));
    }
}
