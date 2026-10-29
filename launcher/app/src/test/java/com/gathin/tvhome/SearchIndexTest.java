package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * SearchIndex against the prototype. src/test/resources/prototype_search.tsv holds design_preview/src/core/fuzzy.ts
 * run as it is for about seventy queries: per query the titles, people, genres and suggestions it returns (kind,
 * label, score, matched indices, in order), and the results grid under each filter chip as search.ts builds it
 * (with the services the prototype marks subscribed); then titlesForPerson for everyone in the catalogue and
 * titlesInGenre for every genre. It was made by a throwaway script that imports fuzzy.ts, offers.ts and catalog.ts,
 * bundled with the esbuild that design_preview's Vite installs (node -e "require('esbuild').buildSync({...,
 * bundle: true, platform: 'node'})") and run with Node, writing one tab-separated line per query and list.
 * Regenerate it the same way if the prototype's matching or data changes.
 */
public class SearchIndexTest {

    private static final MockCatalog CATALOG = new MockCatalog();
    private static final SearchIndex INDEX = new SearchIndex(CATALOG);

    private static List<String[]> fixture() throws Exception {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = SearchIndexTest.class.getResourceAsStream("/prototype_search.tsv");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = r.readLine();
            assertEquals("query\tlist\thits", line);
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] cols = line.split("\t", -1);
                assertEquals(line, 3, cols.length);
                out.add(cols);
            }
        }
        return out;
    }

    private static String kindName(SearchIndex.HitKind k) {
        return k.name().toLowerCase(Locale.ROOT);
    }

    private static List<String> split(String s) {
        return s.isEmpty() ? Collections.emptyList() : Arrays.asList(s.split(";", -1));
    }

    private static String indices(int[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) b.append(i == 0 ? "" : ",").append(a[i]);
        return b.toString();
    }

    private static void assertHits(String where, String expected, List<SearchIndex.Hit> actual) {
        List<String> want = split(expected);
        assertEquals(where + " count: " + actual, want.size(), actual.size());
        for (int i = 0; i < want.size(); i++) {
            String[] w = want.get(i).split("\\|", -1);
            SearchIndex.Hit h = actual.get(i);
            String at = where + " #" + i;
            assertEquals(at, w[0], kindName(h.kind));
            assertEquals(at, w[1], h.label);
            assertEquals(at + " score", Double.parseDouble(w[2]), h.match.score, 1e-9);
            assertEquals(at + " indices", w[3], indices(h.match.indices));
        }
    }

    private static SearchIndex.Filter filterOf(String name) {
        switch (name) {
            case "all":
                return SearchIndex.Filter.ALL;
            case "movie":
                return SearchIndex.Filter.MOVIES;
            case "show":
                return SearchIndex.Filter.SHOWS;
            case "person":
                return SearchIndex.Filter.PEOPLE;
            case "included":
                return SearchIndex.Filter.INCLUDED;
            default:
                throw new IllegalArgumentException(name);
        }
    }

    @Test
    public void matchesThePrototypeForEveryQuery() throws Exception {
        int queries = 0, checks = 0;
        String last = null;
        SearchIndex.Results r = null;
        for (String[] row : fixture()) {
            String q = row[0], list = row[1], hits = row[2];
            if (q.startsWith("person:") || q.startsWith("genre:")) continue;
            if (!q.equals(last)) {
                r = INDEX.search(q);
                last = q;
                queries++;
            }
            String where = "\"" + q + "\" " + list;
            switch (list) {
                case "titles":
                    assertHits(where, hits, r.titles);
                    break;
                case "people":
                    assertHits(where, hits, r.people);
                    break;
                case "genres":
                    assertHits(where, hits, r.genres);
                    break;
                case "suggestions":
                    assertHits(where, hits, r.suggestions);
                    break;
                default:
                    List<String> got = new ArrayList<>();
                    for (SearchIndex.Hit h : INDEX.items(r, filterOf(list.substring("items-".length())), OffersTest.PROTOTYPE)) {
                        got.add(kindName(h.kind) + "|" + h.label);
                    }
                    assertEquals(where, split(hits), got);
                    break;
            }
            checks++;
        }
        assertTrue("queries " + queries, queries >= 60);
        assertEquals(queries * 9, checks);
    }

    @Test
    public void titlesForPersonAndGenreMatchThePrototype() throws Exception {
        int people = 0, genres = 0;
        for (String[] row : fixture()) {
            List<Title> got;
            if (row[0].startsWith("person:")) {
                got = INDEX.titlesForPerson(row[0].substring("person:".length()));
                people++;
            } else if (row[0].startsWith("genre:")) {
                got = INDEX.titlesInGenre(row[0].substring("genre:".length()));
                genres++;
            } else {
                continue;
            }
            List<String> ids = new ArrayList<>();
            for (Title t : got) ids.add(t.id);
            assertEquals(row[0], split(row[2]), ids);
        }
        assertTrue(people > 50);
        assertEquals(CATALOG.genres().size(), genres);
    }

    @Test
    public void arvlFindsArrivalFirst() {
        SearchIndex.Results r = INDEX.search("arvl");
        assertEquals("Arrival", r.titles.get(0).label);
        assertEquals("Arrival", r.suggestions.get(0).label);
        assertEquals(4, r.suggestions.size());
        assertTrue(r.hasMatches());
        assertEquals("arrival", SearchQuery.ghost("arvl", r, false));
    }

    @Test
    public void typosFindTheTitle() {
        assertEquals("Arrival", INDEX.search("arrivsl").titles.get(0).label);
        assertEquals("Severance", INDEX.search("sevrance").titles.get(0).label);
        assertEquals("Dune: Part Two", INDEX.search("dnue").titles.get(0).label);
    }

    @Test
    public void peopleAndGenresRankBelowEqualTitles() {
        SearchIndex.Results r = INDEX.search("amy adams");
        assertFalse(r.people.isEmpty());
        assertEquals("Amy Adams", r.people.get(0).label);
        assertFalse(INDEX.titlesForPerson("Amy Adams").isEmpty());
        assertEquals(INDEX.titlesForPerson("Amy Adams"), r.people.get(0).titles);
        Fuzzy.Match raw = Fuzzy.match("amy adams", "Amy Adams");
        assertNotNull(raw);
        assertEquals(raw.score - 6, r.people.get(0).match.score, 1e-9);
        SearchIndex.Results g = INDEX.search("drama");
        assertEquals("Drama", g.genres.get(0).label);
        assertEquals(Fuzzy.match("drama", "Drama").score - 4, g.genres.get(0).match.score, 1e-9);
    }

    @Test
    public void aGoodGenreMatchAddsItsTitles() {
        SearchIndex.Results r = INDEX.search("documentary");
        assertTrue(r.genres.get(0).match.score > SearchIndex.GENRE_TITLES_SCORE);
        List<SearchIndex.Hit> items = INDEX.items(r, SearchIndex.Filter.ALL, OffersTest.PROTOTYPE);
        List<Title> docs = INDEX.titlesInGenre("Documentary");
        assertFalse(docs.isEmpty());
        for (Title t : docs) {
            boolean found = false;
            for (SearchIndex.Hit h : items) found |= h.title == t;
            assertTrue(t.id, found);
        }
    }

    @Test
    public void noMatches() {
        SearchIndex.Results r = INDEX.search("qzxv");
        assertFalse(r.hasMatches());
        assertTrue(r.suggestions.isEmpty());
        assertEquals(null, SearchQuery.ghost("qzxv", r, false));
        // A genre alone is not a match.
        assertFalse(INDEX.search("dcoumentary").hasMatches());
        assertFalse(INDEX.search("dcoumentary").genres.isEmpty());
    }

    @Test
    public void filtersSplitTheResults() {
        SearchIndex.Results r = INDEX.search("ar");
        List<SearchIndex.Hit> all = INDEX.items(r, SearchIndex.Filter.ALL, OffersTest.PROTOTYPE);
        int movies = INDEX.items(r, SearchIndex.Filter.MOVIES, OffersTest.PROTOTYPE).size();
        int shows = INDEX.items(r, SearchIndex.Filter.SHOWS, OffersTest.PROTOTYPE).size();
        int people = INDEX.items(r, SearchIndex.Filter.PEOPLE, OffersTest.PROTOTYPE).size();
        assertEquals(all.size(), movies + shows + people);
        for (SearchIndex.Hit h : INDEX.items(r, SearchIndex.Filter.INCLUDED, OffersTest.PROTOTYPE)) {
            assertTrue(Offers.isIncluded(h.title, OffersTest.PROTOTYPE));
        }
        // With no services, Included only keeps the free titles alone.
        for (SearchIndex.Hit h : INDEX.items(r, SearchIndex.Filter.INCLUDED, Collections.emptySet())) {
            assertTrue(Offers.isIncluded(h.title, Collections.emptySet()));
        }
    }
}
