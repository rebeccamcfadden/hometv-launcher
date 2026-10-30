package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** docs/rework.md, Accounts, 2026-10-30: a sign-in takes the account's lists and adds nothing from the TV. */
public class SeedPolicyTest {
    @Test
    public void signInDecisions() {
        assertEquals(SeedPolicy.FirstSync.ADOPT, SeedPolicy.onSignIn("", "a"));
        assertEquals(SeedPolicy.FirstSync.ADOPT, SeedPolicy.onSignIn(null, "a"));
        assertEquals(SeedPolicy.FirstSync.CONTINUE, SeedPolicy.onSignIn("a", "a"));
        assertEquals("never pour one account into another", SeedPolicy.FirstSync.ADOPT, SeedPolicy.onSignIn("a", "b"));
    }

    @Test
    public void firstSignInQueuesNothingFromTheTv() {
        SyncLedger ledger = new SyncLedger();
        assertEquals(SeedPolicy.FirstSync.ADOPT, SeedPolicy.prepare(ledger, "obj"));
        assertEquals("obj", ledger.account());
        assertTrue(ledger.pending().isEmpty());
    }

    @Test
    public void sameAccountKeepsItsQueue() {
        SyncLedger ledger = new SyncLedger();
        ledger.reset("obj");
        ledger.record(SyncList.MY_LIST, "movie:1", false, 5);
        ledger.setBase(SyncList.MY_LIST, Arrays.asList("movie:1"), 4);
        assertEquals(SeedPolicy.FirstSync.CONTINUE, SeedPolicy.prepare(ledger, "obj"));
        assertEquals(1, ledger.pending().size());
        assertEquals(Arrays.asList("movie:1"), ledger.base(SyncList.MY_LIST));
    }

    @Test
    public void otherAccountStartsOver() {
        SyncLedger ledger = new SyncLedger();
        ledger.reset("old");
        ledger.record(SyncList.MY_LIST, "movie:1", true, 5);
        assertEquals(SeedPolicy.FirstSync.ADOPT, SeedPolicy.prepare(ledger, "new"));
        assertEquals("new", ledger.account());
        assertTrue(ledger.pending().isEmpty());
    }
}
