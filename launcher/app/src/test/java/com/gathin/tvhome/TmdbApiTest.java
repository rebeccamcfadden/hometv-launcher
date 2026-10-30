package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TMDB's paths, time to live and image sizes (TmdbApi). */
public class TmdbApiTest {

    @Test
    public void titleIds() {
        assertEquals("movie:329865", TmdbApi.id(Kind.MOVIE, 329865));
        assertEquals("tv:95396", TmdbApi.id(Kind.SHOW, 95396));
        assertEquals(Kind.MOVIE, TmdbApi.kindOf("movie:329865"));
        assertEquals(Kind.SHOW, TmdbApi.kindOf("tv:95396"));
        assertNull(TmdbApi.kindOf("arrival"));
        assertNull(TmdbApi.kindOf("movie:"));
        assertNull(TmdbApi.kindOf("movie:12a"));
        assertNull(TmdbApi.kindOf("tv:0"));
        assertNull(TmdbApi.kindOf(null));
        assertEquals(95396, TmdbApi.number("tv:95396"));
        assertEquals(0, TmdbApi.number("911"));
        assertFalse(TmdbApi.isTmdbId("911-lone-star"));
    }

    @Test
    public void paths() {
        assertEquals("/movie/329865?append_to_response=credits,release_dates,videos,recommendations,similar"
                + "&language=en-US", TmdbApi.details("movie:329865"));
        assertEquals("/tv/95396?append_to_response=credits,content_ratings,videos,recommendations,similar"
                + "&language=en-US", TmdbApi.details("tv:95396"));
        assertEquals("/tv/95396/season/2?language=en-US", TmdbApi.season("tv:95396", 2));
        assertEquals("/person/9273?append_to_response=combined_credits&language=en-US", TmdbApi.person(9273));
        assertEquals("/search/multi?query=amy%20adams&include_adult=false&language=en-US&page=1",
                TmdbApi.search(" Amy Adams "));
        assertEquals("/search/multi?query=9-1-1%3A%20lone%20star&include_adult=false&language=en-US&page=1",
                TmdbApi.search("9-1-1: lone star"));
        assertTrue(TmdbApi.discover(Kind.MOVIE, 18).startsWith("/discover/movie?with_genres=18&"));
        assertTrue(TmdbApi.discover(Kind.MOVIE, 18).contains("&region=SE"));
        assertTrue(TmdbApi.discover(Kind.SHOW, 18).startsWith("/discover/tv?with_genres=18&"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void detailsNeedATmdbId() {
        TmdbApi.details("arrival");
    }

    @Test
    public void timeToLiveFollowsWhatThePathIs() {
        long hour = 3_600_000L;
        assertEquals(hour, TmdbApi.ttl(TmdbApi.search("dune")));
        assertEquals(3 * hour, TmdbApi.ttl(TmdbApi.discover(Kind.MOVIE, 18)));
        assertEquals(12 * hour, TmdbApi.ttl(TmdbApi.details("movie:1")));
        assertEquals(12 * hour, TmdbApi.ttl(TmdbApi.details("tv:1")));
        assertEquals(7 * 24 * hour, TmdbApi.ttl(TmdbApi.season("tv:1", 1)));
        assertEquals(7 * 24 * hour, TmdbApi.ttl(TmdbApi.person(1)));
    }

    @Test
    public void imagesAtTheSizeShown() {
        // Search's small cards (271), the library cards (417), the preview's hero (1728).
        assertEquals("https://image.tmdb.org/t/p/w300/a.jpg", TmdbApi.backdrop("/a.jpg", 271));
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", TmdbApi.backdrop("/a.jpg", 417));
        assertEquals("https://image.tmdb.org/t/p/w1280/a.jpg", TmdbApi.backdrop("/a.jpg", 1728));
        assertEquals("https://image.tmdb.org/t/p/w185/p.jpg", TmdbApi.profile("/p.jpg", 104));
        assertEquals("https://image.tmdb.org/t/p/w185/p.jpg", TmdbApi.profile("/p.jpg", 160));
        assertEquals("https://image.tmdb.org/t/p/w45/p.jpg", TmdbApi.profile("/p.jpg", 40));
    }

    @Test
    public void onlyTmdbPathsAreImages() {
        assertNull(TmdbApi.backdrop("arrival", 417));
        assertNull(TmdbApi.backdrop(null, 417));
        assertNull(TmdbApi.backdrop("/", 417));
        assertNull(TmdbApi.backdrop("/../etc", 417));
        assertTrue(TmdbApi.isImagePath("/abc.jpg"));
    }
}
