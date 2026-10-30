package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TMDB's genres in the launcher's words (GenreMap), and the ratings filters read (Certifications). */
public class GenreMapTest {

    @Test
    public void tmdbGenresGetTheLaunchersNames() {
        assertEquals(Arrays.asList("Drama", "Sci-fi", "Mystery"), GenreMap.names(Kind.MOVIE, new int[]{18, 878, 9648}));
        assertEquals(Arrays.asList("Sci-fi", "Fantasy", "Action", "Adventure", "Drama"),
                GenreMap.names(Kind.SHOW, new int[]{10765, 10759, 18}));
        // A movie id read as a show's (878 is not a TV genre) and unknown ids are skipped; repeats too.
        assertEquals(Arrays.asList("Drama"), GenreMap.names(Kind.SHOW, new int[]{878, 18, 18, 12345}));
        assertTrue(GenreMap.names(Kind.MOVIE, null).isEmpty());
    }

    @Test
    public void everyNameThePickAndSearchScreensUseExists() {
        Set<String> all = new HashSet<>(GenreMap.all());
        for (String g : new String[]{"Drama", "Sci-fi", "Comedy", "Animation", "Mystery", "Thriller", "Documentary"}) {
            assertTrue(g, all.contains(g));
        }
        List<String> sorted = GenreMap.all();
        for (int i = 1; i < sorted.size(); i++) assertTrue(sorted.get(i - 1).compareTo(sorted.get(i)) < 0);
        assertFalse(GenreMap.all(Kind.SHOW).contains("Horror"));
        assertTrue(GenreMap.all(Kind.SHOW).contains("Kids"));
        assertFalse(GenreMap.all(Kind.MOVIE).contains("Kids"));
    }

    @Test
    public void discoverIds() {
        assertEquals(878, GenreMap.idFor(Kind.MOVIE, "Sci-fi"));
        assertEquals(10765, GenreMap.idFor(Kind.SHOW, "sci-fi"));
        assertEquals(10759, GenreMap.idFor(Kind.SHOW, "Adventure"));
        assertEquals(18, GenreMap.idFor(Kind.SHOW, "Drama"));
        assertEquals(0, GenreMap.idFor(Kind.SHOW, "Horror"));
        assertEquals(0, GenreMap.idFor(Kind.MOVIE, "Food"));
    }

    @Test
    public void certificationsAreTidiedToTheUsScales() {
        assertEquals("PG-13", Certifications.tidy(" pg-13 "));
        assertEquals("NR", Certifications.tidy("Not Rated"));
        assertEquals("NR", Certifications.tidy("Unrated"));
        assertEquals("TV-MA", Certifications.tidy("TV-MA"));
        assertEquals("TV-Y7", Certifications.tidy("TV-Y7-FV"));
        // Another country's rating in the US field, or nothing: not rated here.
        assertEquals("", Certifications.tidy("15"));
        assertEquals("", Certifications.tidy(""));
        assertEquals("", Certifications.tidy(null));
    }

    @Test
    public void aMoviesRatingPrefersTheTheatricalRelease() {
        assertEquals("R", Certifications.movie(new int[]{1, 3, 4}, new String[]{"PG-13", "R", "PG-13"}));
        assertEquals("PG-13", Certifications.movie(new int[]{1, 4}, new String[]{"", "PG-13"}));
        assertEquals("", Certifications.movie(new int[]{3}, new String[]{""}));
        assertEquals("", Certifications.movie(null, null));
    }

    @Test
    public void ranksOrderTheScale() {
        assertEquals(0, Certifications.rank("G"));
        assertEquals(0, Certifications.rank("TV-Y7"));
        assertEquals(1, Certifications.rank("TV-PG"));
        assertEquals(2, Certifications.rank("PG-13"));
        assertEquals(2, Certifications.rank("TV-14"));
        assertEquals(3, Certifications.rank("R"));
        assertEquals(3, Certifications.rank("TV-MA"));
        assertEquals(4, Certifications.rank("NC-17"));
        assertEquals(Certifications.UNRATED, Certifications.rank("NR"));
        assertEquals(Certifications.UNRATED, Certifications.rank(""));
        assertEquals(Certifications.UNRATED, Certifications.rank(null));
    }
}
