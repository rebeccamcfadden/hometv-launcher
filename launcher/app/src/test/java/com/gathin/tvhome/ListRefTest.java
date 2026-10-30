package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ListRefTest {
    private static final List<TmdbAccountParse.ListInfo> LISTS = Arrays.asList(
            new TmdbAccountParse.ListInfo(30, "Household", false, 4),
            new TmdbAccountParse.ListInfo(12, " household ", true, 2),
            new TmdbAccountParse.ListInfo(40, "Friends' picks", true, 9),
            new TmdbAccountParse.ListInfo(50, "Seen", false, 0));

    @Test
    public void blankIsNone() {
        assertTrue(ListRef.parse(null).isNone());
        assertTrue(ListRef.parse("  ").isNone());
        assertEquals(0, ListRef.parse("").resolve(LISTS));
    }

    @Test
    public void numbersAndLinks() {
        assertEquals(8221116, ListRef.parse("8221116").id);
        assertEquals(8221116, ListRef.parse(" 8221116 ").id);
        assertEquals(8221116, ListRef.parse("https://www.themoviedb.org/list/8221116").id);
        assertEquals(8221116, ListRef.parse("https://www.themoviedb.org/list/8221116-our-picks").id);
        assertEquals(8221116, ListRef.parse("https://www.themoviedb.org/list/8221116?language=en").id);
        assertEquals(8221116, ListRef.parse("8221116").resolve(LISTS));
        assertEquals("not a list number", 0, ListRef.parse("0").id);
        assertEquals("0", ListRef.parse("0").name);
    }

    @Test
    public void namesMatchWithoutCaseOrSpaces() {
        ListRef r = ListRef.parse("HOUSEHOLD");
        assertEquals(0, r.id);
        assertEquals("HOUSEHOLD", r.name);
        assertFalse(r.isNone());
        assertEquals("the oldest of two with the name", 12, r.resolve(LISTS));
        assertEquals(40, ListRef.parse("friends' picks").resolve(LISTS));
        assertEquals(50, ListRef.parse("Seen").resolve(LISTS));
        assertEquals(0, ListRef.parse("Friends").resolve(LISTS));
        assertEquals(0, ListRef.parse("Seen").resolve(null));
        assertEquals("a name with a number in it", 0, ListRef.parse("Top 10").id);
    }

    @Test
    public void shown() {
        assertEquals("list 7", ListRef.parse("7").toString());
        assertEquals("\"Seen\"", ListRef.parse("Seen").toString());
        assertEquals("none", ListRef.NONE.toString());
    }
}
