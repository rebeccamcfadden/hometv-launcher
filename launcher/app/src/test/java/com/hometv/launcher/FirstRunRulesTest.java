package com.hometv.launcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * First run's rules (design_preview/src/screens/setup.ts): steps, done rows and how they are kept, focus, the footer,
 * the keys, the rows' names, and when it opens by itself.
 */
public class FirstRunRulesTest {
    private static final List<String> FOUR = FirstRunRules.STEPS;
    private static final List<String> THREE = Arrays.asList("services", "stores", "weather");

    private static Set<String> opened(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    public void theStepsFollowTheSettingsOrderAndLeaveOutWhatIsMissing() {
        assertEquals(FOUR, FirstRunRules.steps(SettingsNav.ORDER));
        // Without a TMDB token Launcher settings has no Account section: three steps.
        List<String> noAccount = Arrays.asList("services", "stores", "weather", "apps", "theme", "about");
        assertEquals(THREE, FirstRunRules.steps(noAccount));
    }

    @Test
    public void theStepsAreTheSettingsSectionsOfTheSameName() {
        for (String id : FirstRunRules.STEPS) assertTrue(id, SettingsNav.ORDER.contains(id));
    }

    @Test
    public void aRowIsDoneOnceItsPaneWasOpened() {
        assertFalse(FirstRunRules.done("services", opened()));
        assertTrue(FirstRunRules.done("services", opened("services")));
        assertFalse(FirstRunRules.done("stores", opened("services")));
    }

    @Test
    public void doneCountsOnlyTheStepsShown() {
        assertEquals(0, FirstRunRules.doneCount(THREE, opened()));
        assertEquals(1, FirstRunRules.doneCount(THREE, opened("account", "weather")));
        assertFalse(FirstRunRules.allDone(THREE, opened("account", "weather")));
        assertTrue(FirstRunRules.allDone(THREE, opened("services", "stores", "weather")));
        assertFalse(FirstRunRules.allDone(FOUR, opened("services", "stores", "weather")));
    }

    @Test
    public void focusGoesBackToTheNextRowNotDoneThenStart() {
        assertEquals("account", FirstRunRules.nextFocus(FOUR, opened()));
        assertEquals("stores", FirstRunRules.nextFocus(FOUR, opened("account", "services")));
        // Not "the row after the one just done": the first not done, wherever it is.
        assertEquals("account", FirstRunRules.nextFocus(FOUR, opened("services", "stores")));
        assertNull(FirstRunRules.nextFocus(FOUR, opened("account", "services", "stores", "weather")));
        assertNull(FirstRunRules.nextFocus(THREE, opened("services", "stores", "weather")));
    }

    @Test
    public void theFooterCountsStepsAndOffersTheNextOrDone() {
        assertEquals(2, FirstRunRules.stepNumber(FOUR, "services"));
        assertEquals(1, FirstRunRules.stepNumber(THREE, "services"));
        assertEquals(0, FirstRunRules.stepNumber(THREE, "account"));
        assertEquals("stores", FirstRunRules.nextStep(FOUR, "services"));
        assertEquals("services", FirstRunRules.nextStep(FOUR, "account"));
        assertNull(FirstRunRules.nextStep(FOUR, "weather"));
        assertNull(FirstRunRules.nextStep(THREE, "weather"));
        assertNull(FirstRunRules.nextStep(THREE, "account"));
        assertNull(FirstRunRules.nextStep(Collections.emptyList(), "weather"));
    }

    @Test
    public void onlyTheAccountsNotSignedInIsAPlaceholder() {
        assertTrue(FirstRunRules.placeholder("account", "Not signed in", "Not signed in"));
        assertFalse(FirstRunRules.placeholder("account", "Waiting…", "Not signed in"));
        assertFalse(FirstRunRules.placeholder("account", "evening_cinema", "Not signed in"));
        assertFalse(FirstRunRules.placeholder("weather", "Not signed in", "Not signed in"));
        assertFalse(FirstRunRules.placeholder("account", null, "Not signed in"));
    }

    @Test
    public void aValueThatOnlyRepeatsThePillIsLeftOut() {
        assertTrue(FirstRunRules.repeatsPill("Default", "Default"));
        assertTrue(FirstRunRules.repeatsPill(" Default ", "Default"));
        assertFalse(FirstRunRules.repeatsPill("default", "Default"));
        assertFalse(FirstRunRules.repeatsPill("New York", "Default"));
        assertFalse(FirstRunRules.repeatsPill("5 ticked", "Suggested"));
        assertFalse(FirstRunRules.repeatsPill(null, "Default"));
    }

    @Test
    public void itOpensByItselfOnceAfterAFreshInstallUnlessTurnedOff() {
        // Fresh install, not shown yet: it shows.
        assertTrue(FirstRunRules.showOnStart(true, true, false));
        // After an update (an install from before First run too): never.
        assertFalse(FirstRunRules.showOnStart(true, false, false));
        // Shown once already: never again.
        assertFalse(FirstRunRules.showOnStart(true, true, true));
        // first_run_checklist false: never.
        assertFalse(FirstRunRules.showOnStart(false, true, false));
        assertFalse(FirstRunRules.showOnStart(false, false, true));
    }

    @Test
    public void aFreshInstallHasNotBeenUpdated() {
        assertTrue(FirstRunRules.freshInstall(1_700_000_000_000L, 1_700_000_000_000L));
        assertFalse(FirstRunRules.freshInstall(1_700_000_000_000L, 1_700_000_500_000L));
    }

    @Test
    public void theNamesAreTheFirstTwoThenHowManyMore() {
        assertEquals("Netflix, Prime Video +6", FirstRunRules.names(Arrays.asList("Netflix", "Prime Video",
                "Disney+", "Max", "Apple TV+", "Viaplay", "SVT Play", "TV4 Play")));
        assertEquals("Apple TV Store, Amazon Video +1", FirstRunRules.names(Arrays.asList("Apple TV Store",
                "Amazon Video", "Viaplay Store")));
        assertEquals("Netflix, Max", FirstRunRules.names(Arrays.asList("Netflix", "Max")));
        assertEquals("Netflix", FirstRunRules.names(Collections.singletonList("Netflix")));
        assertEquals("", FirstRunRules.names(Collections.emptyList()));
    }

    @Test
    public void theTickedFollowThePanesOrder() {
        Set<ServiceId> ticked = new HashSet<>(Arrays.asList(ServiceId.MAX, ServiceId.NETFLIX, ServiceId.HAYU));
        assertEquals(Arrays.asList(ServiceId.NETFLIX, ServiceId.MAX, ServiceId.HAYU),
                FirstRunRules.ticked(ServicePicks.listed(Region.SE, ticked), ticked));
        assertEquals(Collections.singletonList(ServiceId.VIAPLAY), FirstRunRules.ticked(ServicePicks.SE_STORES,
                new HashSet<>(Arrays.asList(ServiceId.VIAPLAY, ServiceId.NETFLIX))));
        assertTrue(FirstRunRules.ticked(ServicePicks.SE_STORES, Collections.emptySet()).isEmpty());
    }

    @Test
    public void theDoneRowsAreKeptAndReadBack() {
        assertEquals("services,weather", FirstRunRules.encode(opened("weather", "services")));
        assertEquals("", FirstRunRules.encode(opened()));
        assertEquals(Arrays.asList("services", "weather"), FirstRunRules.decode("services,weather"));
        assertEquals(Arrays.asList("services", "weather"),
                FirstRunRules.decode(FirstRunRules.encode(opened("services", "weather", "theme"))));
        // Unknown ids, blanks and repeats are left out; nothing kept reads as none.
        assertEquals(Collections.singletonList("stores"), FirstRunRules.decode("stores,,theme,stores"));
        assertTrue(FirstRunRules.decode("").isEmpty());
        assertTrue(FirstRunRules.decode(null).isEmpty());
    }

    @Test
    public void rightOnARowStaysAndDownFromTheLastRowReachesStart() {
        assertSame(FirstRunRules.Move.STAY, FirstRunRules.onKey(Screen.Key.RIGHT, false, false));
        assertSame(FirstRunRules.Move.STAY, FirstRunRules.onKey(Screen.Key.RIGHT, false, true));
        assertSame(FirstRunRules.Move.START, FirstRunRules.onKey(Screen.Key.DOWN, false, true));
        assertSame(FirstRunRules.Move.PASS, FirstRunRules.onKey(Screen.Key.DOWN, false, false));
        assertSame(FirstRunRules.Move.PASS, FirstRunRules.onKey(Screen.Key.UP, false, false));
        assertSame(FirstRunRules.Move.PASS, FirstRunRules.onKey(Screen.Key.OK, false, false));
    }

    @Test
    public void leftAndRightOnStartStayAndUpLeavesIt() {
        assertSame(FirstRunRules.Move.STAY, FirstRunRules.onKey(Screen.Key.LEFT, true, false));
        assertSame(FirstRunRules.Move.STAY, FirstRunRules.onKey(Screen.Key.RIGHT, true, false));
        assertSame(FirstRunRules.Move.PASS, FirstRunRules.onKey(Screen.Key.UP, true, false));
        assertSame(FirstRunRules.Move.PASS, FirstRunRules.onKey(Screen.Key.OK, true, false));
    }

    @Test
    public void backGoesHomeFromAnywhere() {
        assertSame(FirstRunRules.Move.HOME, FirstRunRules.onKey(Screen.Key.BACK, false, false));
        assertSame(FirstRunRules.Move.HOME, FirstRunRules.onKey(Screen.Key.BACK, true, false));
    }
}
