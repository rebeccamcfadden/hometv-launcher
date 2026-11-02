package com.hometv.launcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** Launcher settings' focus rules (design_preview/src/screens/settings.ts) and its sections' order. */
public class SettingsNavTest {

    @Test
    public void theListEntersThePaneOnRightOrOk() {
        assertSame(SettingsNav.Action.ENTER, SettingsNav.onList(Screen.Key.RIGHT));
        assertSame(SettingsNav.Action.ENTER, SettingsNav.onList(Screen.Key.OK));
    }

    @Test
    public void upAndDownStayOnTheListAndLeftDoesNothing() {
        assertSame(SettingsNav.Action.MOVE_LIST, SettingsNav.onList(Screen.Key.UP));
        assertSame(SettingsNav.Action.MOVE_LIST, SettingsNav.onList(Screen.Key.DOWN));
        assertSame(SettingsNav.Action.STAY, SettingsNav.onList(Screen.Key.LEFT));
    }

    @Test
    public void backOnTheListClosesThePage() {
        assertSame(SettingsNav.Action.CLOSE, SettingsNav.onList(Screen.Key.BACK));
    }

    @Test
    public void inThePaneArrowsMoveBackLeavesAndOkIsTheControls() {
        for (Screen.Key k : new Screen.Key[]{Screen.Key.UP, Screen.Key.DOWN, Screen.Key.LEFT, Screen.Key.RIGHT}) {
            assertSame(k.name(), SettingsNav.Action.MOVE_PANE, SettingsNav.inPane(k));
        }
        assertSame(SettingsNav.Action.BACK, SettingsNav.inPane(Screen.Key.BACK));
        assertSame(SettingsNav.Action.PASS, SettingsNav.inPane(Screen.Key.OK));
    }

    @Test
    public void thePageOpensOnTheSectionAskedForWhenItIsListed() {
        List<String> shown = Arrays.asList("services", "weather", "theme");
        assertEquals("theme", SettingsNav.initial("theme", shown));
        assertEquals("services", SettingsNav.initial(null, shown));
        // Account is left out without a TMDB token; a quick-settings row for it opens the first section instead.
        assertEquals("services", SettingsNav.initial("account", shown));
        assertNull(SettingsNav.initial("theme", Collections.emptyList()));
    }

    @Test
    public void theSectionsAreInGroupingAsOrder() {
        assertEquals(Arrays.asList("account", "notes", "services", "stores", "features", "keys", "theme",
                "performance", "apps", "weather", "start", "home", "region", "tv", "about"), SettingsNav.ORDER);
        List<String> groups = new ArrayList<>();
        for (SettingsNav.Group g : SettingsNav.GROUPS) groups.add(g.key);
        assertEquals(Arrays.asList("accounts", "watch", "look", "tv"), groups);
    }

    private static String lines(List<String> shown) {
        StringBuilder b = new StringBuilder();
        for (SettingsNav.Line l : SettingsNav.lines(shown)) {
            if (b.length() > 0) b.append(' ');
            b.append(l.isHeading() ? "#" + l.group : l.id);
        }
        return b.toString();
    }

    @Test
    public void eachGroupWithASectionShowsItsHeadingThenItsSectionsInOrder() {
        // Today's sections, given in any order; Features, API keys and Region aren't built yet.
        List<String> today = Arrays.asList("about", "account", "notes", "services", "stores", "weather", "apps",
                "theme", "performance", "home", "tv");
        assertEquals("#accounts account notes #watch services stores #look theme performance apps weather"
                + " #tv home tv about", lines(today));
    }

    @Test
    public void aGroupWithNoneOfItsSectionsHasNoHeading() {
        // A build without a TMDB token has no Account or Phone notes.
        assertEquals("#watch services #tv tv", lines(Arrays.asList("tv", "services")));
        assertEquals("", lines(Collections.<String>emptyList()));
    }

    @Test
    public void sectionsStillToComeTakeTheirPlaces() {
        assertEquals("#watch services stores features keys #tv start region tv",
                lines(Arrays.asList("keys", "region", "features", "services", "stores", "start", "tv")));
    }

    @Test
    public void aSectionInNoGroupComesLastWithoutAHeading() {
        SettingsNav.Line last = SettingsNav.lines(Arrays.asList("x-new", "about")).get(2);
        assertEquals("x-new", last.id);
        assertNull(last.group);
    }
}