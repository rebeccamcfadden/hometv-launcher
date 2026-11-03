package com.hometv.launcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Which grid rows hold their pictures (F15 P1-2), and when the grid can be rebound in place (P2-2). */
public class ArtWindowTest {
    private static final int H = 900;

    private static Title movie(String id) {
        return new Title(id, Kind.MOVIE, id, 2020, null, 0, null, 0, null, null, null, null, null, null, null, 0, 0);
    }

    @Test
    public void rowsOnScreenAndAScreenEitherSideBind() {
        assertEquals(ArtWindow.Act.BIND, ArtWindow.decide(100, 340, H));
        assertEquals("partly above the top", ArtWindow.Act.BIND, ArtWindow.decide(-100, 140, H));
        assertEquals("a screen below", ArtWindow.Act.BIND, ArtWindow.decide(1700, 1940, H));
        assertEquals("a screen above", ArtWindow.Act.BIND, ArtWindow.decide(-1100, -900, H));
    }

    @Test
    public void rowsMoreThanTwoScreensAwayRelease() {
        assertEquals(ArtWindow.Act.RELEASE, ArtWindow.decide(2701, 2941, H));
        assertEquals(ArtWindow.Act.RELEASE, ArtWindow.decide(-2100, -1801, H));
    }

    @Test
    public void rowsBetweenKeepWhatTheyHave() {
        assertEquals(ArtWindow.Act.KEEP, ArtWindow.decide(1801, 2041, H));
        assertEquals(ArtWindow.Act.KEEP, ArtWindow.decide(2700, 2940, H));
        assertEquals(ArtWindow.Act.KEEP, ArtWindow.decide(-1500, -1260, H));
    }

    @Test
    public void nothingChangesBeforeTheBoxHasAHeight() {
        assertEquals(ArtWindow.Act.KEEP, ArtWindow.decide(100, 340, 0));
    }

    @Test
    public void sameIdsInTheSameOrderAllowARebindInPlace() {
        Title a = movie("a");
        Title a2 = movie("a");
        Title b = movie("b");
        assertTrue(ArtWindow.sameIds(Arrays.asList(a, b), Arrays.asList(a2, b)));
        assertFalse("order", ArtWindow.sameIds(Arrays.asList(a, b), Arrays.asList(b, a)));
        assertFalse("size", ArtWindow.sameIds(Arrays.asList(a, b), Collections.singletonList(a)));
        List<Title> none = null;
        assertFalse(ArtWindow.sameIds(Arrays.asList(a), none));
    }
}
