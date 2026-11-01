package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The title menu's shown-list changes out to TMDB (F3): one per list and title, and what a reply leaves. */
public class ShownEditsTest {
    private static final long PICKS = 8221116;
    private static final String ID = "movie:603";

    @Test
    public void aSecondChangeToTheSamePairWaitsForTheFirstAnswer() {
        ShownEdits e = new ShownEdits();
        assertTrue(e.start(PICKS, ID, true, false));
        assertTrue(e.isPending(PICKS, ID));
        // Add then remove before TMDB answers: the remove isn't sent, so two refusals can't leave it wrong.
        assertFalse(e.start(PICKS, ID, false, true));
        // Another list, or another title, isn't held up.
        assertTrue(e.start(9001, ID, true, false));
        assertTrue(e.start(PICKS, "movie:1", false, true));
    }

    @Test
    public void taken_theChangeStays_refused_theListGoesBackToWhatItHeld() {
        ShownEdits e = new ShownEdits();
        e.start(PICKS, ID, true, false);
        assertEquals(Boolean.TRUE, e.finish(PICKS, ID, true));
        assertFalse(e.isPending(PICKS, ID));

        e.start(PICKS, ID, false, true);
        assertEquals(Boolean.TRUE, e.finish(PICKS, ID, false));
        // Ready for the next change once answered.
        assertTrue(e.start(PICKS, ID, false, true));
    }

    @Test
    public void anAnswerForNothingOutChangesNothing() {
        assertNull(new ShownEdits().finish(PICKS, ID, true));
    }

    @Test
    public void aSyncReadBeforeTmdbHadTheChangeStillShowsIt() {
        ShownEdits e = new ShownEdits();
        e.start(PICKS, ID, true, false);
        e.start(9001, "movie:2", false, true);
        ImportedLists.Entry picks = new ImportedLists.Entry(ListRef.parse("8221116"), PICKS, "Our picks",
                Collections.singletonList("movie:1"));
        ImportedLists.Entry tips = new ImportedLists.Entry(ListRef.parse("9001"), 9001, "Friends' tips",
                Arrays.asList("movie:2", "movie:3"));
        List<ImportedLists.Entry> out = e.apply(Arrays.asList(picks, tips));
        assertEquals(Arrays.asList("movie:1", ID), out.get(0).ids);
        assertEquals(Collections.singletonList("movie:3"), out.get(1).ids);

        // Once answered, a read is shown as it is.
        e.finish(PICKS, ID, true);
        e.finish(9001, "movie:2", true);
        assertEquals(Arrays.asList(picks, tips), e.apply(Arrays.asList(picks, tips)));
    }
}
