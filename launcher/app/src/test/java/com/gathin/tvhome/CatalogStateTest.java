package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** CatalogState: which loading, offline or error state a page and a preview tab show (06-states A). */
public class CatalogStateTest {

    private static CatalogState.Lists lists(boolean online, int titles, int detailed, int pending, boolean settled) {
        return CatalogState.lists(true, online, titles, detailed, pending, settled);
    }

    @Test
    public void theMockCatalogueIsAlwaysOnline() {
        assertEquals(CatalogState.Lists.ONLINE, CatalogState.lists(false, false, 16, 0, 16, false));
        assertEquals(CatalogState.Lists.ONLINE, CatalogState.lists(false, false, 16, 0, 0, true));
    }

    @Test
    public void theFirstLoadShowsSkeletonsUntilEveryTitleSettles() {
        assertEquals(CatalogState.Lists.LOADING, lists(true, 16, 0, 16, false));
        assertEquals(CatalogState.Lists.LOADING, lists(true, 16, 15, 1, false));
        assertEquals(CatalogState.Lists.ONLINE, lists(true, 16, 16, 0, false));
        // A title that failed is settled too: the page shows what it has.
        assertEquals(CatalogState.Lists.ONLINE, lists(true, 16, 15, 0, false));
    }

    @Test
    public void aTitleJoiningLaterFillsInWithoutTheSkeletons() {
        assertEquals(CatalogState.Lists.ONLINE, lists(true, 17, 16, 1, true));
    }

    @Test
    public void backOnlineWithNothingSavedLoadsWithSkeletonsAgain() {
        assertEquals(CatalogState.Lists.LOADING, lists(true, 16, 0, 16, true));
    }

    @Test
    public void readingTheCacheOfflineIsALoadToo() {
        assertEquals(CatalogState.Lists.LOADING, lists(false, 16, 0, 16, false));
    }

    @Test
    public void offlineWithSomethingSavedShowsTheSavedCopy() {
        assertEquals(CatalogState.Lists.OFFLINE_SAVED, lists(false, 16, 3, 0, true));
        assertEquals(CatalogState.Lists.OFFLINE_SAVED, lists(false, 16, 16, 0, false));
    }

    @Test
    public void offlineWithNothingSavedSaysSo() {
        assertEquals(CatalogState.Lists.OFFLINE_EMPTY, lists(false, 16, 0, 0, true));
        assertEquals(CatalogState.Lists.OFFLINE_EMPTY, lists(false, 16, 0, 0, false));
    }

    @Test
    public void emptyListsOfflineAreNotNothingSaved() {
        // The lists themselves are on the TV: they are empty, not unsaved.
        assertEquals(CatalogState.Lists.OFFLINE_SAVED, lists(false, 0, 0, 0, true));
    }

    @Test
    public void previewTabs() {
        // Detailed, or the mock catalogue: the content.
        assertEquals(CatalogState.Tab.READY, CatalogState.tab(true, true, false, false, true));
        assertEquals(CatalogState.Tab.READY, CatalogState.tab(false, false, false, true, true));
        // Loading: every tab's skeleton.
        assertEquals(CatalogState.Tab.LOADING, CatalogState.tab(true, false, true, false, true));
        assertEquals(CatalogState.Tab.LOADING, CatalogState.tab(true, false, true, false, false));
        // Failed: the tabs drawn from the details (Overview too) say so; More like this shows its own row.
        assertEquals(CatalogState.Tab.ERROR, CatalogState.tab(true, false, false, true, true));
        assertEquals(CatalogState.Tab.READY, CatalogState.tab(true, false, false, true, false));
        // Neither loading nor failed (a summary nobody asked about yet): as it is.
        assertEquals(CatalogState.Tab.READY, CatalogState.tab(true, false, false, false, true));
    }

    @Test
    public void previewHeader() {
        assertTrue(CatalogState.headerLoading(true, false, true));
        assertFalse(CatalogState.headerLoading(true, true, true));
        assertFalse(CatalogState.headerLoading(true, false, false));
        assertFalse(CatalogState.headerLoading(false, false, true));
    }

    @Test
    public void watchIsBlankUntilTheAvailabilityIsKnown() {
        // A summary loading or failed, with no offers from the card: no label, not "Not on your services".
        assertTrue(CatalogState.watchBlank(true, false, false));
        // Loaded with no offers: "Not on your services" is true then.
        assertFalse(CatalogState.watchBlank(true, true, false));
        // The card knew offers, or the mock catalogue has everything: the real label.
        assertFalse(CatalogState.watchBlank(true, false, true));
        assertFalse(CatalogState.watchBlank(false, false, false));
    }
}
