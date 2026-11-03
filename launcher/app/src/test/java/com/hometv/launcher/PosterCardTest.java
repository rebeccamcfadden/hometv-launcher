package com.hometv.launcher;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** F40's poster cards: the poster path TMDB gives, the size asked for, and the card's service line. */
public class PosterCardTest {

    @Test
    public void summariesAndDetailsKeepThePoster() throws Exception {
        JSONObject hit = new JSONObject("{\"id\":329865,\"media_type\":\"movie\",\"title\":\"Arrival\","
                + "\"release_date\":\"2016-11-10\",\"backdrop_path\":\"/b.jpg\",\"poster_path\":\"/p.jpg\"}");
        Title t = TmdbParse.summary(hit, null);
        assertEquals("/p.jpg", t.poster);
        assertEquals("/b.jpg", t.art);
        Title d = TmdbParse.details(new JSONObject("{\"id\":329865,\"title\":\"Arrival\",\"poster_path\":\"/p2.jpg\"}"),
                Kind.MOVIE, Region.SE);
        assertEquals("/p2.jpg", d.poster);
        assertNull(TmdbParse.summary(new JSONObject("{\"id\":1,\"media_type\":\"tv\",\"name\":\"X\"}"), null).poster);
    }

    @Test
    public void aDraftCarriesThePoster() {
        Title.Draft d = new Title.Draft("movie:1", Kind.MOVIE, "X");
        d.poster = "/p.jpg";
        Title t = d.build();
        assertEquals("/p.jpg", new Title.Draft(t).build().poster);
    }

    @Test
    public void thePosterSizeFitsTheCard() {
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", TmdbApi.poster("/p.jpg", 268));
        assertTrue(TmdbApi.poster("/p.jpg", 600).contains("/w780/"));
        assertNull(TmdbApi.poster("arrival", 268));
        assertNull(TmdbApi.poster(null, 268));
    }

    @Test
    public void theServiceLine() {
        assertEquals("Netflix · Included", TitleText.serviceLine("Netflix", "Included"));
        assertEquals("Apple TV Store · Rent 39 kr", TitleText.serviceLine("Apple TV Store", "Rent 39 kr"));
        assertEquals("Not on your services", TitleText.serviceLine("", "Not on your services"));
        assertEquals("Netflix", TitleText.serviceLine("Netflix", ""));
        assertEquals("", TitleText.serviceLine(null, null));
    }
}
