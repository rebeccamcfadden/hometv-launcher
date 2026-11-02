package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Phone notes' waiting notes and last sends (BACKLOG.md, Phone notes). */
public class NoteBookTest {
    @Test
    public void theLatestNoteOfAListWins() {
        NoteBook b = new NoteBook();
        b.put("Alex", "Dune", 1);
        b.put("Partner", "Fargo", 2);
        b.put(" alex ", "Dune\nArrival", 3);
        List<NoteBook.Pending> waiting = b.pending();
        assertEquals(2, waiting.size());
        assertEquals("Partner", waiting.get(0).list);
        assertEquals("alex", waiting.get(1).list);
        assertEquals("Dune\nArrival", waiting.get(1).text);
    }

    @Test
    public void notesWaitOldestFirstAndCanBeSkipped() {
        NoteBook b = new NoteBook();
        b.put("Alex", "Dune", 1);
        b.put("Partner", "Fargo", 2);
        assertEquals("Alex", b.next(Collections.<String>emptySet()).list);
        assertEquals("Partner", b.next(new HashSet<>(Collections.singletonList("alex"))).list);
        assertNull(b.next(new HashSet<>(Arrays.asList("alex", "partner"))));
    }

    @Test
    public void aNoteDoneStopsWaitingUnlessANewerOneCameMeanwhile() {
        NoteBook b = new NoteBook();
        NoteBook.Pending first = b.put("Alex", "Dune", 1);
        NoteBook.Pending newer = b.put("Alex", "Dune\nArrival", 2);
        assertFalse("the newer send still waits", b.done(first));
        assertEquals(1, b.pending().size());
        assertTrue(b.done(newer));
        assertTrue(b.pending().isEmpty());
    }

    @Test
    public void waitingNotesSurviveARestart() {
        NoteBook b = new NoteBook();
        b.put("Alex", "☐ Dune\n☑ Arrival", 1000);
        b.put("Partner", "Fargo (tv)", 2000);
        NoteBook back = NoteBook.restore(b.pendingJson(), null);
        assertEquals(2, back.pending().size());
        assertEquals("Alex", back.pending().get(0).list);
        assertEquals("☐ Dune\n☑ Arrival", back.pending().get(0).text);
        assertEquals(2000, back.pending().get(1).at);
        // A note restored is replaced by the next send of its list, as before the restart.
        NoteBook.Pending again = back.put("ALEX", "Dune", 3000);
        assertEquals(2, back.pending().size());
        assertTrue(back.done(again));
    }

    @Test
    public void eachListKeepsItsLastSendNewestFirst() {
        NoteBook b = new NoteBook();
        b.record(NoteBook.Send.ofLines("Partner", 1, 14, 14, null));
        b.record(NoteBook.Send.ofLines("Alex", 2, 12, 9, Arrays.asList("Zzyzx", "Qq", "Xy")));
        b.record(NoteBook.Send.ofLines("partner", 3, 15, 1, Collections.singletonList("Nope")));
        List<NoteBook.Send> sends = b.sends();
        assertEquals(2, sends.size());
        assertEquals("partner", sends.get(0).list);
        assertEquals(1, sends.get(0).added);
        assertEquals("Alex", sends.get(1).list);
        assertEquals("the section's \"4 not matched\"", 4, b.unmatched());
    }

    @Test
    public void lastSendsSurviveARestart() {
        NoteBook b = new NoteBook();
        b.record(NoteBook.Send.ofLines("Alex", 2, 12, 9, Arrays.asList("Zzyzx Qq", "Dune (1066)")));
        NoteBook back = NoteBook.restore(null, b.sendsJson());
        NoteBook.Send s = back.sends().get(0);
        assertEquals("Alex", s.list);
        assertEquals(2, s.at);
        assertEquals(12, s.lines);
        assertEquals(9, s.added);
        assertEquals(Arrays.asList("Zzyzx Qq", "Dune (1066)"), s.unmatched);
    }

    @Test
    public void onlyTheLatestSendsAreKept() {
        NoteBook b = new NoteBook();
        for (int i = 0; i < NoteBook.MAX_SENDS + 3; i++) b.record(NoteBook.Send.ofLines("List " + i, i, 1, 1, null));
        assertEquals(NoteBook.MAX_SENDS, b.sends().size());
        assertEquals("List " + (NoteBook.MAX_SENDS + 2), b.sends().get(0).list);
    }

    @Test
    public void somethingUnreadableIsLeftOut() {
        NoteBook b = NoteBook.restore("not json", "[{\"list\":\"\"},{\"list\":\"Alex\",\"lines\":3}]");
        assertTrue(b.pending().isEmpty());
        assertEquals(1, b.sends().size());
        assertEquals(0, b.unmatched());
        assertTrue(NoteBook.restore("[{\"list\":\"Alex\",\"text\":\"\"}]", null).pending().isEmpty());
    }

    @Test
    public void aResendKeepsTheAnswersOfLinesStillWaitingAndAnAnsweredLineLeaves() {
        NoteBook b = new NoteBook();
        NoteMatch.Candidate c = new NoteMatch.Candidate("movie:207", "Dead Poets Society", "", 1989, null, 4, 0);
        b.record(new NoteBook.Send("Alex", 1, 3, 1, java.util.Arrays.asList(
                new NoteMatch.Ask("dead poet society", "Dead poet society", false, java.util.Collections.singletonList(c)),
                new NoteMatch.Ask("zzyzx qq", "Zzyzx Qq", false, null))));
        // Sent again: those lines were looked up before, so come with no answers; the old ones are kept.
        b.record(NoteBook.Send.ofLines("alex", 2, 3, 0, java.util.Arrays.asList("Dead poet society", "Zzyzx Qq")));
        NoteBook.Send s = b.sends().get(0);
        assertEquals(2, s.asks.size());
        assertEquals("movie:207", s.asks.get(0).top.get(0).id);
        assertTrue(s.asks.get(1).top.isEmpty());
        b.offered("Alex", "zzyzx qq", java.util.Collections.singletonList(c));
        assertEquals("answers found later are kept", "movie:207", b.sends().get(0).asks.get(1).top.get(0).id);
        assertTrue(b.answered("Alex", "dead poet society"));
        assertEquals(java.util.Collections.singletonList("Zzyzx Qq"), b.sends().get(0).unmatched);
        assertEquals(1, b.unmatched());
        assertFalse(b.answered("Alex", "dead poet society"));
        // Saved and read back with its answers; an older TV's plain lines still read.
        NoteBook back = NoteBook.restore(null, b.sendsJson());
        assertEquals("Zzyzx Qq", back.sends().get(0).asks.get(0).query);
        NoteBook old = NoteBook.restore(null, "[{\"list\":\"A\",\"at\":1,\"lines\":2,\"added\":1,\"unmatched\":[\"Qq\"]}]");
        assertEquals(java.util.Collections.singletonList("Qq"), old.sends().get(0).unmatched);
    }

    @Test
    public void aSearchThatFoundNothingIsKeptAndNotLookedUpAgain() {
        NoteBook b = new NoteBook();
        NoteMatch.Candidate c = new NoteMatch.Candidate("movie:207", "Dead Poets Society", "", 1989, null, 4, 0);
        b.record(new NoteBook.Send("Alex", 1, 1, 0, java.util.Collections.singletonList(
                new NoteMatch.Ask("x", "X", false, java.util.Collections.singletonList(c)))));
        // Search again found nothing: that replaces the earlier answers, and the line counts as looked up.
        b.offered("Alex", "x", java.util.Collections.<NoteMatch.Candidate>emptyList());
        NoteMatch.Ask a = b.sends().get(0).asks.get(0);
        assertTrue(a.top.isEmpty());
        assertTrue(a.looked);
        NoteMatch.Ask back = NoteBook.restore(null, b.sendsJson()).sends().get(0).asks.get(0);
        assertTrue("saved as looked up", back.looked);
        // An older TV's plain line, and a line resent without a look-up, aren't.
        assertFalse(NoteMatch.Ask.fromJson("X").looked);
        assertFalse(new NoteMatch.Ask("x", "X", false, null).looked);
        assertFalse(a.unanswered().looked);
        assertTrue(a.unanswered().top.isEmpty());
    }
}
