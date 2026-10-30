package com.gathin.tvhome;

import org.json.JSONException;
import org.junit.Test;

import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Wikidata lookup by TMDB id and the links it gives into each service's app. Answers recorded 2026-10-30. */
public class WikidataLinksTest {

    private final WatchProviders table = new WatchProviders();

    private static WikidataIds ids(Map<String, String> values) {
        Map<String, java.util.List<String>> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : values.entrySet()) m.put(e.getKey(), Arrays.asList(e.getValue().split("\\|")));
        return new WikidataIds("Q1", m);
    }

    @Test
    public void theQueryAsksForTheRightProperties() throws Exception {
        String q = WikidataIds.query(Kind.MOVIE, 329865);
        assertTrue(q.startsWith("SELECT ?item (GROUP_CONCAT(DISTINCT ?vP1874;separator=\"|\") AS ?P1874)"));
        assertTrue(q.contains(" WHERE { ?item wdt:P4947 \"329865\" ."));
        assertTrue(q.contains("OPTIONAL { ?item wdt:P6398 ?vP6398 . }"));
        assertTrue(q.endsWith(" } GROUP BY ?item"));
        String show = WikidataIds.query(Kind.SHOW, 136315);
        assertTrue(show.contains("wdt:P4983 \"136315\""));
        assertTrue(show.contains("?P7596") && show.contains("?P9751") && show.contains("?P6381"));
        assertTrue(!show.contains("?P7595") && !show.contains("?P6398"));

        String url = WikidataIds.url(Kind.MOVIE, 329865);
        assertTrue(url.startsWith("https://query.wikidata.org/sparql?format=json&query="));
        assertEquals(q, URLDecoder.decode(url.substring(url.indexOf("query=") + 6), "UTF-8"));
        assertTrue(WikidataIds.headers().get("User-Agent").startsWith("CleanYourTV-launcher/"));
    }

    @Test
    public void arrivalsIds() throws JSONException {
        WikidataIds ids = WikidataIds.parse(Fixtures.read("wikidata_movie_329865.json"), Kind.MOVIE);
        assertEquals("Q20382729", ids.item);
        assertEquals("80117799", ids.first(WikidataIds.NETFLIX));
        assertEquals("umc.cmc.20sgkdxqbvucopzt913joa0gr", ids.first(WikidataIds.APPLE_MOVIE));
        assertEquals(Arrays.asList("1166395905", "1174618339", "1274926574", "1476438441"), ids.itunesIds(Kind.MOVIE));
        assertNull(ids.first(WikidataIds.DISNEY_MOVIE));
        assertTrue(ids.all(WikidataIds.MAX).isEmpty());
    }

    @Test
    public void noItemMeansNoIds() throws JSONException {
        WikidataIds none = WikidataIds.parse(Fixtures.read("wikidata_none.json"), Kind.MOVIE);
        assertNull(none.item);
        assertTrue(none.itunesIds(Kind.MOVIE).isEmpty());
        assertNull(WikidataIds.parse(null, Kind.MOVIE).item);
        assertTrue(DeepLinks.all(table, Kind.MOVIE, none, "X").isEmpty());
    }

    @Test
    public void fetchSendsTheUserAgent() throws Exception {
        Fixtures.FakeHttp http = new Fixtures.FakeHttp().answer(WikidataIds.ENDPOINT, "wikidata_tv_136315.json");
        WikidataIds ids = WikidataIds.fetch(Kind.SHOW, 136315, http);
        assertEquals("Q112761982", ids.item);
        assertEquals(WikidataIds.USER_AGENT, http.headers.get(0).get("User-Agent"));
    }

    @Test
    public void linksForAMovie() throws JSONException {
        WikidataIds ids = WikidataIds.parse(Fixtures.read("wikidata_movie_157336.json"), Kind.MOVIE);
        Map<String, String> links = DeepLinks.all(table, Kind.MOVIE, ids, "Interstellar");
        Map<String, String> want = new LinkedHashMap<>();
        want.put("netflix", "https://www.netflix.com/title/70305903");
        want.put("prime", "https://app.primevideo.com/detail?gti=B00TU9UFTS");
        want.put("appletvplus", "https://tv.apple.com/movie/interstellar/umc.cmc.1vrwat5k1ucm5k42q97ioqyq3");
        want.put("appletv", "https://tv.apple.com/movie/interstellar/umc.cmc.1vrwat5k1ucm5k42q97ioqyq3");
        want.put("amazonvideo", "https://app.primevideo.com/detail?gti=B00TU9UFTS");
        assertEquals(want, links);
    }

    @Test
    public void linksForAShowPreferDisneysBrowseId() throws JSONException {
        WikidataIds ids = WikidataIds.parse(Fixtures.read("wikidata_tv_136315.json"), Kind.SHOW);
        assertEquals("https://www.disneyplus.com/browse/entity-05eb6a8e-90ed-4947-8c0b-e6536cbddd5f",
                DeepLinks.link("disney", Kind.SHOW, ids, "The Bear"));
        assertEquals("https://tv.apple.com/show/the-bear/umc.cmc.javg04xbn3eonbgfvnaqmodk",
                DeepLinks.link("appletvplus", Kind.SHOW, ids, "The Bear"));
        assertNull(DeepLinks.link("netflix", Kind.SHOW, ids, "The Bear"));
    }

    @Test
    public void everyServicesFormat() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put(WikidataIds.DISNEY_SERIES, "52m6nx7HoP5F");
        v.put(WikidataIds.MAX, "series/urn:hbo:series:GWukCJAq-nIuHwwEAAAB4");
        v.put(WikidataIds.PRIME_ID, "amzn1.dv.gti.59c224c3-6b38-4e4a-a399-4271e52985d5");
        v.put(WikidataIds.SVT_PLAY, "bron");
        WikidataIds ids = ids(v);
        assertEquals("https://www.disneyplus.com/series/bron-broen/52m6nx7HoP5F",
                DeepLinks.link("disney", Kind.SHOW, ids, "Bron/Broen"));
        assertEquals("https://play.hbomax.com/series/urn:hbo:series:GWukCJAq-nIuHwwEAAAB4",
                DeepLinks.link("max", Kind.SHOW, ids, "Succession"));
        assertEquals("https://app.primevideo.com/detail?gti=amzn1.dv.gti.59c224c3-6b38-4e4a-a399-4271e52985d5",
                DeepLinks.link("prime", Kind.SHOW, ids, "X"));
        assertEquals("https://www.svtplay.se/bron", DeepLinks.link("svtplay", Kind.SHOW, ids, "Bron"));
        // No Wikidata property for these: the app opens on its home screen.
        assertNull(DeepLinks.link("tv4play", Kind.SHOW, ids, "X"));
        assertNull(DeepLinks.link("viaplay", Kind.SHOW, ids, "X"));
        assertNull(DeepLinks.link("sfanytime", Kind.SHOW, ids, "X"));
    }

    @Test
    public void malformedIdsNeverReachALink() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put(WikidataIds.NETFLIX, "123?x=1");
        v.put(WikidataIds.APPLE_MOVIE, "umc.cmc.short");
        v.put(WikidataIds.MAX, "../../evil");
        v.put(WikidataIds.SVT_PLAY, "a b");
        v.put(WikidataIds.PRIME_ID, "not/an/id");
        WikidataIds ids = ids(v);
        for (String s : Arrays.asList("netflix", "appletv", "max", "svtplay", "prime")) {
            assertNull(s, DeepLinks.link(s, Kind.MOVIE, ids, "X"));
        }
        assertEquals(Collections.emptyMap(), DeepLinks.all(table, Kind.MOVIE, ids, "X"));
    }

    @Test
    public void slugs() {
        assertEquals("le-fabuleux-destin-d-amelie-poulain", DeepLinks.slug("Le Fabuleux Destin d'Amélie Poulain"));
        assertEquals("x", DeepLinks.slug("!!!"));
        assertEquals("x", DeepLinks.slug(null));
    }
}
