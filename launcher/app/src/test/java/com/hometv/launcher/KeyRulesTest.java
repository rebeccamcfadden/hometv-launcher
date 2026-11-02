package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** API keys' rules (F16): what a key may be, the state it shows, the key the TV uses, the row's value and the check. */
public class KeyRulesTest {
    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0.c2lnbmF0dXJl";

    @Test
    public void aKeyHasTheCharactersTheBuildTakes() {
        assertTrue(KeyRules.valid(KeyRules.Id.TMDB, JWT));
        assertTrue(KeyRules.valid(KeyRules.Id.TMDB, "  " + JWT + "\n"));
        assertFalse(KeyRules.valid(KeyRules.Id.TMDB, "eyJ abc"));
        assertFalse(KeyRules.valid(KeyRules.Id.TMDB, "abc+def"));
        assertTrue(KeyRules.valid(KeyRules.Id.WATCHMODE, "aB3dE5fG7hJ9kL1mN3pQ5rS7tU9"));
        assertTrue(KeyRules.valid(KeyRules.Id.MOTN, "a+b/c=d_e-f"));
        assertFalse(KeyRules.valid(KeyRules.Id.MOTN, "with space"));
        assertFalse(KeyRules.valid(KeyRules.Id.MOTN, "quote\"d"));
        assertFalse(KeyRules.valid(KeyRules.Id.MOTN, "back\\slash"));
        assertFalse(KeyRules.valid(KeyRules.Id.WATCHMODE, "caf\u00e9"));
        assertFalse(KeyRules.valid(KeyRules.Id.WATCHMODE, ""));
        assertFalse(KeyRules.valid(KeyRules.Id.WATCHMODE, null));
        char[] longKey = new char[KeyRules.MAX_KEY + 1];
        Arrays.fill(longKey, 'a');
        assertFalse(KeyRules.valid(KeyRules.Id.WATCHMODE, new String(longKey)));
    }

    @Test
    public void onlyTheLastFourCharactersShowAgain() {
        assertEquals("dXJl", KeyRules.last4(JWT));
        assertEquals("abc", KeyRules.last4(" abc "));
        assertEquals("", KeyRules.last4(null));
    }

    @Test
    public void aKeyEnteredWinsOverTheBuildsAndRemovingLeavesNone() {
        // Entered and checked: its state; the build's key isn't used.
        assertSame(KeyRules.State.OK, KeyRules.state("mine", KeyRules.State.OK, false, "built"));
        assertEquals("mine", KeyRules.effective("mine", KeyRules.State.OK, false, "built"));
        // Not reached: kept and used.
        assertSame(KeyRules.State.UNREACHABLE, KeyRules.state("mine", KeyRules.State.UNREACHABLE, false, ""));
        assertEquals("mine", KeyRules.effective("mine", KeyRules.State.UNREACHABLE, false, ""));
        // Refused: kept to show, never used, and the build's key doesn't come back either.
        assertSame(KeyRules.State.BAD, KeyRules.state("mine", KeyRules.State.BAD, false, "built"));
        assertEquals("", KeyRules.effective("mine", KeyRules.State.BAD, false, "built"));
        // A check that never finished counts as not reached.
        assertSame(KeyRules.State.UNREACHABLE, KeyRules.state("mine", null, false, ""));
        assertSame(KeyRules.State.UNREACHABLE, KeyRules.state("mine", KeyRules.State.CHECKING, false, ""));
        // None entered: the build's, until removed (the project lead, 2026-11-02).
        assertSame(KeyRules.State.BUILT, KeyRules.state("", null, false, " built "));
        assertEquals("built", KeyRules.effective("", null, false, " built "));
        assertSame(KeyRules.State.UNSET, KeyRules.state("", null, true, "built"));
        assertEquals("", KeyRules.effective("", null, true, "built"));
        assertSame(KeyRules.State.UNSET, KeyRules.state(null, null, false, ""));
        assertEquals("", KeyRules.effective(null, null, false, null));
    }

    @Test
    public void workingKeysAreTheOnesChecked() {
        assertTrue(KeyRules.State.OK.works());
        assertTrue(KeyRules.State.BUILT.works());
        // Kept and used, but never checked: not counted as working until a check passes (Copilot on #83).
        assertFalse(KeyRules.State.UNREACHABLE.works());
        assertTrue(KeyRules.State.UNREACHABLE.set());
        assertFalse(KeyRules.State.BAD.works());
        assertFalse(KeyRules.State.BAD.set());
        assertFalse(KeyRules.State.CHECKING.works());
        assertFalse(KeyRules.State.UNSET.works());
        for (KeyRules.State s : KeyRules.State.values()) assertSame(s, KeyRules.State.of(s.wire));
        for (KeyRules.Id id : KeyRules.Id.values()) assertSame(id, KeyRules.Id.of(id.id));
        assertNull(KeyRules.Id.of("netflix"));
        assertTrue(KeyRules.Id.MOTN.optional());
        assertFalse(KeyRules.Id.WATCHMODE.optional());
    }

    private static Map<KeyRules.Id, KeyRules.State> states(KeyRules.State tmdb, KeyRules.State watchmode,
                                                          KeyRules.State motn) {
        Map<KeyRules.Id, KeyRules.State> m = new EnumMap<>(KeyRules.Id.class);
        m.put(KeyRules.Id.TMDB, tmdb);
        m.put(KeyRules.Id.WATCHMODE, watchmode);
        m.put(KeyRules.Id.MOTN, motn);
        return m;
    }

    private static String value(List<KeyRules.Id> ids, Map<KeyRules.Id, KeyRules.State> states) {
        KeyRules.Summary s = KeyRules.summary(ids, states);
        return s.kind + " " + s.count + "/" + s.total;
    }

    @Test
    public void theRowSaysRefusedFirstThenCheckingThenWhatIsSet() {
        List<KeyRules.Id> all = Arrays.asList(KeyRules.Id.values());
        KeyRules.State ok = KeyRules.State.OK, built = KeyRules.State.BUILT, unset = KeyRules.State.UNSET;
        assertEquals("WORKING 3/3", value(all, states(built, built, built)));
        assertEquals("REFUSED 1/3", value(all, states(ok, KeyRules.State.BAD, KeyRules.State.CHECKING)));
        assertEquals("CHECKING 0/3", value(all, states(ok, KeyRules.State.CHECKING, unset)));
        assertEquals("NOT_SET 0/3", value(all, states(unset, unset, unset)));
        // Removing the build's TMDB key: "2 of 3 set".
        assertEquals("SOME 2/3", value(all, states(unset, built, built)));
        // Movie of the Night is optional: two working of three is all there is to set.
        assertEquals("WORKING 2/3", value(all, states(ok, ok, unset)));
        // Price lookup off: the TMDB key alone.
        assertEquals("WORKING 1/1", value(Collections.singletonList(KeyRules.Id.TMDB), states(ok, unset, unset)));
        // A key kept but never checked: set, not working.
        KeyRules.State unreached = KeyRules.State.UNREACHABLE;
        assertEquals("UNCHECKED 1/3", value(all, states(ok, unreached, unset)));
        assertEquals("SOME 2/3", value(all, states(unset, unreached, ok)));
    }

    @Test
    public void aKeyThatOnceWorkedStaysWorkingWhenItCantBeReached() {
        assertSame(KeyRules.State.OK, KeyRules.afterCheck(KeyRules.State.OK, KeyRules.State.UNREACHABLE));
        assertSame(KeyRules.State.BAD, KeyRules.afterCheck(KeyRules.State.OK, KeyRules.State.BAD));
        assertSame(KeyRules.State.UNREACHABLE,
                KeyRules.afterCheck(KeyRules.State.UNREACHABLE, KeyRules.State.UNREACHABLE));
        assertSame(KeyRules.State.OK, KeyRules.afterCheck(KeyRules.State.UNREACHABLE, KeyRules.State.OK));
        assertSame(KeyRules.State.UNREACHABLE, KeyRules.afterCheck(KeyRules.State.BAD, KeyRules.State.UNREACHABLE));
    }

    @Test
    public void eachCheckIsOneFreeRequestWithTheKeyInAHeader() {
        KeyRules.Check t = KeyRules.check(KeyRules.Id.TMDB, " " + JWT + " ");
        assertEquals("https://api.themoviedb.org/3/authentication", t.url);
        assertEquals("Bearer " + JWT, t.headers.get("Authorization"));
        KeyRules.Check w = KeyRules.check(KeyRules.Id.WATCHMODE, "wkey");
        assertEquals("https://api.watchmode.com/v1/regions/", w.url);
        assertEquals("wkey", w.headers.get("X-API-Key"));
        KeyRules.Check m = KeyRules.check(KeyRules.Id.MOTN, "mkey");
        assertEquals("https://api.movieofthenight.com/v4/countries/us", m.url);
        assertEquals("mkey", m.headers.get("X-API-Key"));
        for (KeyRules.Check c : Arrays.asList(t, w, m)) assertFalse(c.url, c.url.contains("key"));
    }

    @Test
    public void theAnswerSaysWhetherTheKeyWorks() {
        assertSame(KeyRules.State.OK, KeyRules.fromStatus(200));
        assertSame(KeyRules.State.OK, KeyRules.fromStatus(204));
        assertSame(KeyRules.State.BAD, KeyRules.fromStatus(401));
        assertSame(KeyRules.State.BAD, KeyRules.fromStatus(403));
        // A limit or an outage says nothing about the key: kept, checked again later.
        assertSame(KeyRules.State.UNREACHABLE, KeyRules.fromStatus(429));
        assertSame(KeyRules.State.UNREACHABLE, KeyRules.fromStatus(500));
        assertSame(KeyRules.State.UNREACHABLE, KeyRules.fromStatus(302));
        assertSame(KeyRules.State.UNREACHABLE, KeyRules.fromStatus(-1));
    }

    @Test
    public void theQrKeepsTheCodeInTheFragment() {
        assertEquals("http://192.168.1.50:8766/keys", KeyRules.address("192.168.1.50", 8766));
        assertEquals("http://192.168.1.50:8766/keys#K7QM4PXT", KeyRules.pageUrl("192.168.1.50", 8766, "k7qm-4pxt"));
        assertNull(KeyRules.address(null, 8766));
        assertNull(KeyRules.pageUrl("", 8766, "K7QM-4PXT"));
        assertNull(KeyRules.pageUrl("192.168.1.50", 8766, ""));
    }
}
