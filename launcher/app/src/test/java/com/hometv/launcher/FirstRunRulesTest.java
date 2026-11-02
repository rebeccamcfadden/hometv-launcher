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
    private static final List<String> STEPS = FirstRunRules.STEPS;
    private static final List<String> TWO = Arrays.asList("services", "stores");

    private static Set<String> opened(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    public void theStepsAreFeaturesServicesAndStoresAndLeaveOutWhatIsMissing() {
        // F14 (2026-11-01): Sign in to TMDB and Weather city are no longer steps; turning their feature on in the
        // Features step sets them up. F12: Region comes before the services and stores it decides.
        assertEquals(Arrays.asList("features", "region", "services", "stores"), STEPS);
        assertEquals(STEPS, FirstRunRules.steps(SettingsNav.ORDER));
        List<String> noFeatures = Arrays.asList("account", "services", "stores", "weather", "apps", "theme", "about");
        assertEquals(TWO, FirstRunRules.steps(noFeatures));
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
        assertEquals(0, FirstRunRules.doneCount(TWO, opened()));
        assertEquals(1, FirstRunRules.doneCount(TWO, opened("features", "stores")));
        assertFalse(FirstRunRules.allDone(TWO, opened("features", "stores")));
        assertTrue(FirstRunRules.allDone(TWO, opened("services", "stores")));
        assertFalse(FirstRunRules.allDone(STEPS, opened("services", "stores")));
    }

    @Test
    public void focusGoesBackToTheNextRowNotDoneThenStart() {
        assertEquals("features", FirstRunRules.nextFocus(STEPS, opened()));
        assertEquals("region", FirstRunRules.nextFocus(STEPS, opened("features", "services")));
        assertEquals("stores", FirstRunRules.nextFocus(STEPS, opened("features", "region", "services")));
        // Not "the row after the one just done": the first not done, wherever it is.
        assertEquals("features", FirstRunRules.nextFocus(STEPS, opened("services", "stores")));
        assertNull(FirstRunRules.nextFocus(STEPS, opened("features", "region", "services", "stores")));
        assertNull(FirstRunRules.nextFocus(TWO, opened("services", "stores")));
    }

    @Test
    public void theFooterCountsStepsAndOffersTheNextOrDone() {
        assertEquals(3, FirstRunRules.stepNumber(STEPS, "services"));
        assertEquals(1, FirstRunRules.stepNumber(TWO, "services"));
        assertEquals(0, FirstRunRules.stepNumber(TWO, "features"));
        assertEquals("stores", FirstRunRules.nextStep(STEPS, "services"));
        assertEquals("region", FirstRunRules.nextStep(STEPS, "features"));
        assertEquals("services", FirstRunRules.nextStep(STEPS, "region"));
        assertNull(FirstRunRules.nextStep(STEPS, "stores"));
        assertNull(FirstRunRules.nextStep(TWO, "features"));
        assertNull(FirstRunRules.nextStep(Collections.emptyList(), "stores"));
        // A feature's setup opened from the Features step is a step of its own: Step 1 of 1, then Done.
        assertEquals(1, FirstRunRules.stepNumber(Collections.singletonList("account"), "account"));
        assertNull(FirstRunRules.nextStep(Collections.singletonList("account"), "account"));
    }
    @Test
    public void aFeatureTurnedOnOpensItsOwnSetup() {
        assertEquals("account", FirstRunRules.setupOf(Features.Feature.TMDB));
        assertEquals("notes", FirstRunRules.setupOf(Features.Feature.NOTES));
        assertEquals("weather", FirstRunRules.setupOf(Features.Feature.WEATHER));
        // Prices' keys page is F16's; nothing until it exists.
        assertNull(FirstRunRules.setupOf(Features.Feature.PRICES));
        for (Features.Feature f : Features.Feature.values()) {
            String id = FirstRunRules.setupOf(f);
            if (id != null) assertTrue(id, SettingsNav.ORDER.contains(id));
        }
    }

    @Test
    public void theSetupTabsFollowTheFeaturesOn() {
        Features none = Features.ALL_OFF;
        assertTrue(FirstRunRules.setups(none).isEmpty());
        assertEquals(Arrays.asList("account", "notes", "weather"), FirstRunRules.setups(Features.ALL_ON));
        // TMDB account and Phone notes off: Weather and Price lookup on, and Price lookup has no setup here yet (F16).
        java.util.Map<String, Object> saved = new java.util.HashMap<>();
        saved.put("feature_tmdb", false);
        saved.put("feature_notes", false);
        assertEquals(Collections.singletonList("weather"), FirstRunRules.setups(Features.resolve(saved, null)));
        assertEquals(FirstRunRules.SETUPS, FirstRunRules.setups(Features.ALL_ON));
        assertSame(Features.Feature.NOTES, FirstRunRules.featureOf("notes"));
        assertNull(FirstRunRules.featureOf("services"));
        assertNull(FirstRunRules.featureOf(null));
    }

    @Test
    public void whatCountsAsSetUp() {
        assertTrue(FirstRunRules.setUp(Features.Feature.TMDB, true, true, false, false));
        assertFalse(FirstRunRules.setUp(Features.Feature.TMDB, true, false, true, true));
        // Phone notes: signed in and listening.
        assertTrue(FirstRunRules.setUp(Features.Feature.NOTES, true, true, true, false));
        assertFalse(FirstRunRules.setUp(Features.Feature.NOTES, true, true, false, false));
        assertFalse(FirstRunRules.setUp(Features.Feature.NOTES, true, false, true, false));
        assertTrue(FirstRunRules.setUp(Features.Feature.PRICES, true, false, false, true));
        assertFalse(FirstRunRules.setUp(Features.Feature.PRICES, true, true, true, false));
        // Weather always has a place (the TV's default).
        assertTrue(FirstRunRules.setUp(Features.Feature.WEATHER, true, false, false, false));
        // A build without a TMDB token has no sign-in to finish: TMDB account and Phone notes stay on.
        assertTrue(FirstRunRules.setUp(Features.Feature.TMDB, false, false, false, false));
        assertTrue(FirstRunRules.setUp(Features.Feature.NOTES, false, false, false, false));
        assertFalse(FirstRunRules.setUp(Features.Feature.PRICES, false, true, true, false));
    }

    @Test
    public void nextLeadsFromFeaturesThroughItsSetups() {
        List<String> panels = FirstRunRules.panels(STEPS, Arrays.asList("account", "weather"));
        assertEquals(Arrays.asList("features", "account", "weather", "region", "services", "stores"), panels);
        assertEquals("account", FirstRunRules.nextPanel(panels, "features"));
        assertEquals("region", FirstRunRules.nextPanel(panels, "weather"));
        assertNull(FirstRunRules.nextPanel(panels, "stores"));
        assertNull(FirstRunRules.nextPanel(panels, "notes"));
        assertEquals(STEPS, FirstRunRules.panels(STEPS, Collections.emptyList()));
    }

    @Test
    public void onlyTmdbAndPhoneNotesRebuildBeforeTheirSetup() {
        assertTrue(FirstRunRules.setupNeedsRebuild(Features.Feature.TMDB, false));
        assertTrue(FirstRunRules.setupNeedsRebuild(Features.Feature.NOTES, false));
        assertFalse(FirstRunRules.setupNeedsRebuild(Features.Feature.TMDB, true));
        assertFalse(FirstRunRules.setupNeedsRebuild(Features.Feature.WEATHER, false));
        assertFalse(FirstRunRules.setupNeedsRebuild(Features.Feature.PRICES, false));
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
        assertEquals("features,services", FirstRunRules.encode(opened("services", "features")));
        assertEquals("", FirstRunRules.encode(opened()));
        assertEquals(Arrays.asList("features", "services"), FirstRunRules.decode("features,services"));
        assertEquals(Arrays.asList("features", "services"),
                FirstRunRules.decode(FirstRunRules.encode(opened("services", "features", "theme"))));
        // Steps from before F14 (Sign in to TMDB, Weather city) are no longer steps: left out.
        assertEquals(Collections.singletonList("services"), FirstRunRules.decode("account,services,weather"));
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
