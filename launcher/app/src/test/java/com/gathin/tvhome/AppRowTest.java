package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/** Home's app row: the seed, what shows, and Move, Remove and Add (docs/rework.md, Home app row). */
public class AppRowTest {

    private static List<String> list(String... pkgs) {
        return Arrays.asList(pkgs);
    }

    private static Predicate<String> installed(String... pkgs) {
        Set<String> set = new HashSet<>(Arrays.asList(pkgs));
        return set::contains;
    }

    @Test
    public void seedIsDockAppsWhenOneOfThemShows() {
        List<String> dock = list("a.one", "a.two", "a.three");
        assertEquals(dock, AppRow.seed(dock, installed("a.two")));
    }

    @Test
    public void seedFallsBackToTheStreamingAppsInThePrototypesOrder() {
        assertEquals(AppRow.STREAMING, AppRow.seed(list("a.one"), installed("com.netflix.ninja")));
        assertEquals(AppRow.STREAMING, AppRow.seed(Collections.emptyList(), installed()));
        List<String> shown = AppRow.visible(AppRow.STREAMING, installed("com.spotify.tv.android",
                "com.netflix.ninja", "com.hbo.hbonow", "com.google.android.youtube.tv"));
        assertEquals(list("com.netflix.ninja", "com.google.android.youtube.tv", "com.hbo.hbonow",
                "com.spotify.tv.android"), shown);
    }

    @Test
    public void theSavedListWinsOverTheSeed() {
        List<String> saved = list("b.one", "b.two");
        assertEquals(saved, AppRow.full(saved, list("a.one"), installed("a.one", "b.one")));
        assertEquals(list("a.one"), AppRow.full(null, list("a.one"), installed("a.one")));
        // An empty saved list is the household removing everything, not "no choice yet".
        assertEquals(Collections.emptyList(), AppRow.full(Collections.emptyList(), list("a.one"), installed("a.one")));
    }

    @Test
    public void visibleSkipsAppsThatCannotShowAndDuplicates() {
        assertEquals(list("a", "c"), AppRow.visible(list("a", "b", "c", "a"), installed("a", "c")));
    }

    @Test
    public void moveRightAndLeft() {
        List<String> full = list("a", "b", "c");
        assertEquals(list("b", "a", "c"), AppRow.move(full, full, "a", 1));
        assertEquals(list("a", "c", "b"), AppRow.move(full, full, "c", -1));
    }

    @Test
    public void moveStopsAtTheEnds() {
        List<String> full = list("a", "b", "c");
        assertEquals(full, AppRow.move(full, full, "a", -1));
        assertEquals(full, AppRow.move(full, full, "c", 1));
        assertEquals(full, AppRow.move(full, full, "missing", 1));
    }

    @Test
    public void moveJumpsOverAppsThatAreNotInstalled() {
        // x is saved but not installed: moving a right puts it past c, the next visible app, and x stays put.
        List<String> full = list("a", "x", "c", "d");
        List<String> visible = list("a", "c", "d");
        assertEquals(list("x", "c", "a", "d"), AppRow.move(full, visible, "a", 1));
        assertEquals(list("c", "a", "x", "d"), AppRow.move(full, visible, "c", -1));
    }

    @Test
    public void removeAndAdd() {
        List<String> full = list("a", "b", "c");
        assertEquals(list("a", "c"), AppRow.remove(full, "b"));
        assertEquals(full, AppRow.remove(full, "z"));
        assertEquals(list("a", "b", "c", "d"), AppRow.add(full, "d"));
        assertEquals(full, AppRow.add(full, "b"));
    }

    @Test
    public void encodeDecodeRoundTrip() {
        List<String> full = list("com.netflix.ninja", "com.spotify.tv.android");
        assertEquals(full, AppRow.decode(AppRow.encode(full)));
        assertEquals(Collections.emptyList(), AppRow.decode(AppRow.encode(Collections.emptyList())));
        assertNull(AppRow.decode(null));
    }

    @Test
    public void decodeDropsLinesThatAreNotPackages() {
        assertEquals(list("a.b", "c.d"), AppRow.decode("a.b\n\nnot a package\n../x\nc.d\na.b\n"));
    }

    @Test
    public void theRowShowsAtMostSeven() {
        List<String> nine = list("a", "b", "c", "d", "e", "f", "g", "h", "i");
        Predicate<String> all = installed("a", "b", "c", "d", "e", "f", "g", "h", "i");
        assertEquals(list("a", "b", "c", "d", "e", "f", "g"), AppRow.visible(nine, all));
        assertFalse(AppRow.hasRoom(nine, all));
        // An app that can't show makes room for the eighth, which was kept in the saved list.
        assertEquals(list("a", "c", "d", "e", "f", "g", "h"),
                AppRow.visible(nine, installed("a", "c", "d", "e", "f", "g", "h", "i")));
        assertTrue(AppRow.hasRoom(list("a", "b"), all));
    }
}