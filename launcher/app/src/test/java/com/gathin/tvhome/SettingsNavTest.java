package com.gathin.tvhome;

import org.junit.Test;

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
    public void theSectionsAreInTheSpecsOrderWithThemeAfterHomeRowApps() {
        assertEquals(Arrays.asList("account", "services", "stores", "weather", "apps", "theme", "motion", "start",
                "home", "tv", "about"), SettingsNav.ORDER);
    }
}
