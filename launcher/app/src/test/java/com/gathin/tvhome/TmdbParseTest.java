package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TMDB's answers into titles, seasons and people (TmdbParse), from hand-written answers in src/test/resources/tmdb. */
public class TmdbParseTest {

    static JSONObject fixture(String name) {
        try (InputStream in = TmdbParseTest.class.getResourceAsStream("/tmdb/" + name)) {
            if (in == null) throw new IllegalStateException("no fixture " + name);
            return new JSONObject(new String(DiskCache.readAll(in), StandardCharsets.UTF_8));
        } catch (IOException | org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void movieDetails() {
        Title t = TmdbParse.details(fixture("movie.json"), Kind.MOVIE);
        assertEquals("movie:329865", t.id);
        assertEquals(Kind.MOVIE, t.kind);
        assertEquals("Arrival", t.name);
        assertEquals(2016, t.year);
        assertEquals(116, t.runtime);
        assertEquals(7.6, t.score, 1e-9);
        assertEquals(Arrays.asList("Drama", "Sci-fi", "Mystery"), t.genres);
        assertEquals("/arrival-backdrop.jpg", t.art);
        assertTrue(t.synopsis.startsWith("Taking place"));
        assertTrue(t.detailed);
        assertTrue(t.offers.isEmpty());
        assertTrue(t.seasons.isEmpty());
    }

    @Test
    public void movieRatingIsTheUsTheatricalOne() {
        assertEquals("PG-13", TmdbParse.details(fixture("movie.json"), Kind.MOVIE).rating);
    }

    @Test
    public void castKeepsCharactersAndPhotos() {
        Title t = TmdbParse.details(fixture("movie.json"), Kind.MOVIE);
        assertEquals(3, t.cast.size());
        Person amy = t.cast.get(0);
        assertEquals("Amy Adams", amy.name);
        assertEquals("Louise Banks", amy.role);
        assertEquals(9273, amy.id);
        assertEquals("/amy-adams.jpg", amy.photo);
        assertNull(t.cast.get(1).photo);
        // No character: they still have a role line.
        assertEquals("Actor", t.cast.get(2).role);
    }

    @Test
    public void crewIsDirectorFirstThenTheJobsTheCardsShow() {
        Title t = TmdbParse.details(fixture("movie.json"), Kind.MOVIE);
        assertEquals(4, t.crew.size());
        assertEquals("Denis Villeneuve", t.crew.get(0).name);
        assertEquals("Director", t.crew.get(0).role);
        assertEquals("Writer", t.crew.get(1).role);
        assertEquals("Cinematography", t.crew.get(2).role);
        assertEquals("Music", t.crew.get(3).role);
        assertEquals("Jóhann Jóhannsson", t.crew.get(3).name);
        assertEquals("Director", PreviewRules.leadRole(t));
        assertEquals("Denis Villeneuve", PreviewRules.leadNames(t));
    }

    @Test
    public void trailerIsTheOfficialYouTubeTrailer() {
        assertEquals("officialKey1", TmdbParse.details(fixture("movie.json"), Kind.MOVIE).trailer);
        assertNull(TmdbParse.details(fixture("tv.json"), Kind.SHOW).trailer);
        assertNull(TmdbParse.trailer(null));
    }

    @Test
    public void showDetails() {
        Title t = TmdbParse.details(fixture("tv.json"), Kind.SHOW);
        assertEquals("tv:95396", t.id);
        assertEquals("Severance", t.name);
        assertEquals(2022, t.year);
        assertEquals(0, t.runtime);
        assertEquals("TV-MA", t.rating);
        assertEquals(Arrays.asList("Drama", "Sci-fi", "Fantasy", "Mystery"), t.genres);
        // Specials and empty seasons left out; the episodes come a season at a time.
        assertEquals(2, t.seasons.size());
        assertEquals(1, t.seasons.get(0).n);
        assertEquals(9, t.seasons.get(0).count);
        assertTrue(t.seasons.get(0).needsEpisodes());
        assertEquals(51, t.episodeRuntime);
        assertEquals(1, t.crew.size());
        assertEquals("Creator", t.crew.get(0).role);
        assertEquals("Dan Erickson", PreviewRules.leadNames(t));
        assertEquals("Adam Scott", t.cast.get(0).name);
    }

    @Test
    public void seasonEpisodes() {
        Season s = TmdbParse.season(fixture("season.json"), 50);
        assertEquals(1, s.n);
        assertEquals(2, s.episodes.size());
        assertEquals("Good News About Hell", s.episodes.get(0).name);
        assertEquals(57, s.episodes.get(0).runtime);
        // No name or runtime yet (an episode not aired): "Episode 2" and the show's usual length.
        assertEquals("Episode 2", s.episodes.get(1).name);
        assertEquals(50, s.episodes.get(1).runtime);
        assertFalse(s.needsEpisodes());
    }

    @Test
    public void similarIsRecommendationsElseSimilar() {
        List<Title> rec = TmdbParse.similar(fixture("movie.json"), Kind.MOVIE);
        assertEquals(2, rec.size());
        assertEquals("movie:157336", rec.get(0).id);
        assertEquals(8.4, rec.get(0).score, 1e-9);
        assertFalse(rec.get(0).detailed);
        assertEquals("tv:1399", rec.get(1).id);
        assertEquals(Arrays.asList("Sci-fi", "Fantasy", "Drama", "Action", "Adventure"), rec.get(1).genres);
        assertNull(rec.get(1).art);
        List<Title> sim = TmdbParse.similar(fixture("tv.json"), Kind.SHOW);
        assertEquals(1, sim.size());
        // Similar titles have no media_type: the kind comes from the title asked about.
        assertEquals("tv:100088", sim.get(0).id);
    }

    @Test
    public void searchSplitsTitlesAndPeople() {
        Catalog.Found f = TmdbParse.search(fixture("search.json"));
        // Without art or a photo (a namesake, a stub), left out.
        assertEquals(2, f.titles.size());
        assertEquals("movie:329865", f.titles.get(0).id);
        assertEquals("tv:1", f.titles.get(1).id);
        assertEquals(0, f.titles.get(1).year);
        assertEquals(1, f.people.size());
        Person amy = f.people.get(0);
        assertEquals("Amy Adams", amy.name);
        assertEquals(9273, amy.id);
        assertEquals("/amy-adams.jpg", amy.photo);
        assertEquals(2, f.knownFor(amy).size());
        assertEquals("movie:4523", f.knownFor(amy).get(1).id);
    }

    @Test
    public void personIsKnownForTheirMostVotedTitles() {
        Catalog.Credits c = TmdbParse.credits(fixture("person.json"), new Person("Amy Adams", "Louise Banks", 9273, null));
        assertEquals("/amy-adams.jpg", c.person.photo);
        assertEquals(9273, c.person.id);
        StringBuilder ids = new StringBuilder();
        for (Title t : c.titles) ids.append(t.id).append(' ');
        // Talk shows and appearances as themselves left out, Enchanted once.
        assertEquals("movie:329865 movie:5000 movie:4523 tv:2316 ", ids.toString());
    }

    @Test
    public void missingFieldsGiveEmptyValues() throws Exception {
        Title t = TmdbParse.details(new JSONObject("{\"id\": 5}"), Kind.MOVIE);
        assertEquals("movie:5", t.id);
        assertEquals("", t.name);
        assertEquals(0, t.year);
        assertEquals("", t.rating);
        assertTrue(t.cast.isEmpty());
        assertNull(t.art);
        assertNull(TmdbParse.summary(new JSONObject("{\"id\": 5, \"media_type\": \"person\", \"name\": \"x\"}"), null));
        assertNull(TmdbParse.summary(new JSONObject("{\"id\": 5}"), null));
        assertEquals(0, TmdbParse.year("unknown"));
    }
}
