package com.gathin.tvhome;

import org.json.JSONException;
import org.junit.Test;

import java.net.URLDecoder;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Movie of the Night's change feed (an answer recorded 2026-10-30) and the stale marks it leaves. */
public class ChangeFeedTest {

    private static final long NOW = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli();
    private static final long D = PriceCachePolicy.DAY;

    @Test
    public void readAtMostOnceADay() {
        assertTrue(ChangeFeed.due(0, NOW));
        assertFalse(ChangeFeed.due(NOW - 23 * PriceCachePolicy.HOUR, NOW));
        assertTrue(ChangeFeed.due(NOW - D, NOW));
        assertEquals(3, ChangeFeed.MAX_PAGES);
    }

    @Test
    public void theRequest() throws Exception {
        String url = ChangeFeed.url(NOW - D, NOW, null);
        assertTrue(url.startsWith("https://api.movieofthenight.com/v4/changes?country=se&change_type=updated"
                + "&item_type=show&catalogs="));
        assertTrue(URLDecoder.decode(url, "UTF-8").contains("catalogs=apple.rent,apple.buy,prime.rent,prime.buy"));
        assertTrue(url.endsWith("&from=" + (NOW - D) / 1000));
        // Never read: a week back, as far as kept prices go.
        assertTrue(ChangeFeed.url(0, NOW, null).endsWith("&from=" + (NOW - 7 * D) / 1000));
        // The next page, its cursor escaped.
        assertTrue(ChangeFeed.url(NOW - D, NOW, "a?b&c").endsWith("&cursor=a%3Fb%26c"));
    }

    @Test
    public void theRecordedPageNamesTitlesByTmdbId() throws JSONException {
        ChangeFeed.Page p = ChangeFeed.parse(Fixtures.read("motn_changes.json"));
        assertEquals(new LinkedHashSet<>(Arrays.asList("movie:1155324", "movie:345887", "movie:926393")), p.ids);
        assertNull(p.nextCursor);
        assertTrue(ChangeFeed.parse(null).ids.isEmpty());
        ChangeFeed.Page more = ChangeFeed.parse("{\"changes\":[],\"shows\":{},\"hasMore\":true,\"nextCursor\":\"x\"}");
        assertEquals("x", more.nextCursor);
    }

    @Test
    public void ids() {
        assertEquals("movie:597", ChangeFeed.launcherId("movie/597"));
        assertEquals("tv:1396", ChangeFeed.launcherId("tv/1396"));
        assertNull(ChangeFeed.launcherId("person/1"));
        assertNull(ChangeFeed.launcherId(""));
    }

    @Test
    public void marksAreKeptAWeekAndClearedByFreshPrices() {
        ChangeFeed.Marks m = new ChangeFeed.Marks();
        m.mark(new LinkedHashSet<>(Arrays.asList("movie:1", "tv:2")), NOW);
        assertEquals(NOW, m.staleAt("movie:1"));
        assertEquals(0, m.staleAt("movie:3"));
        m.clear("movie:1");
        assertEquals(0, m.staleAt("movie:1"));
        ChangeFeed.Marks back = ChangeFeed.Marks.decode(m.encode());
        assertEquals(NOW, back.staleAt("tv:2"));
        back.expire(NOW + 7 * D);
        assertTrue(back.ids().isEmpty());
        assertTrue(ChangeFeed.Marks.decode("garbage,movie:x=1,=2").ids().isEmpty());
    }

    @Test
    public void marksAreCapped() {
        ChangeFeed.Marks m = new ChangeFeed.Marks();
        for (int i = 1; i <= ChangeFeed.Marks.MAX + 10; i++) m.mark(Collections.singleton("movie:" + i), NOW + i);
        assertEquals(ChangeFeed.Marks.MAX, m.ids().size());
        assertEquals(0, m.staleAt("movie:1"));
        assertEquals(NOW + ChangeFeed.Marks.MAX + 10, m.staleAt("movie:" + (ChangeFeed.Marks.MAX + 10)));
    }
}
