package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountWordsTest {
    @Test
    public void countdownRoundsUp() {
        assertEquals("15:00", AccountWords.countdown(SignInFlow.TOKEN_LIFETIME));
        assertEquals("14:32", AccountWords.countdown(872_000));
        assertEquals("0:01", AccountWords.countdown(1));
        assertEquals("0:00", AccountWords.countdown(0));
        assertEquals("0:00", AccountWords.countdown(-5));
        assertEquals("1:00", AccountWords.countdown(59_001));
    }

    @Test
    public void qrModulesAreWholePixelsWithinTheSize() {
        // TMDB's v4 sign-in link (a 284-character request token, 337 characters in all) is a version 14 code: 73
        // modules, 81 with the quiet zone. The page draws it in 540 px.
        assertEquals(14, Qr.encode("https://www.themoviedb.org/auth/access?request_token=" + "x".repeat(284)).version);
        int scale = AccountWords.moduleSize(73, 540);
        assertEquals(6, scale);
        assertTrue((73 + 2 * AccountWords.QUIET) * scale <= 540);
        assertTrue("at least 420 px", (73 + 2 * AccountWords.QUIET) * scale >= 420);
        assertEquals(1, AccountWords.moduleSize(177, 50));
    }

    @Test
    public void theSettingsPaneDrawsTheCodeIn486Px() {
        int scale = AccountWords.moduleSize(73, 486);
        assertEquals(6, scale);
        assertEquals(486, (73 + 2 * AccountWords.QUIET) * scale);
    }

    @Test
    public void barFollowsTheTimeLeft() {
        assertEquals(1f, AccountWords.fraction(SignInFlow.TOKEN_LIFETIME), 0f);
        assertEquals(0.5f, AccountWords.fraction(SignInFlow.TOKEN_LIFETIME / 2), 1e-6f);
        assertEquals(0f, AccountWords.fraction(0), 0f);
        assertEquals(0f, AccountWords.fraction(-1), 0f);
        assertEquals(1f, AccountWords.fraction(SignInFlow.TOKEN_LIFETIME * 2), 0f);
    }

    @Test
    public void sectionValueFollowsTheSignIn() {
        assertEquals(AccountWords.Row.NOT_SIGNED_IN, AccountWords.row(SignInFlow.Phase.SIGNED_OUT));
        assertEquals(AccountWords.Row.GETTING, AccountWords.row(SignInFlow.Phase.REQUESTING));
        assertEquals(AccountWords.Row.WAITING, AccountWords.row(SignInFlow.Phase.WAITING));
        assertEquals(AccountWords.Row.SIGNING_IN, AccountWords.row(SignInFlow.Phase.FINISHING));
        assertEquals(AccountWords.Row.EXPIRED, AccountWords.row(SignInFlow.Phase.EXPIRED));
        assertEquals(AccountWords.Row.SIGNED_IN, AccountWords.row(SignInFlow.Phase.SIGNED_IN));
        // A failed attempt reads Not signed in, as in the prototype; the pane says why.
        assertEquals(AccountWords.Row.NOT_SIGNED_IN, AccountWords.row(SignInFlow.Phase.FAILED));
        assertTrue(AccountWords.Row.WAITING.warn);
        assertTrue(AccountWords.Row.GETTING.warn);
        assertFalse(AccountWords.Row.SIGNED_IN.warn);
        assertFalse(AccountWords.Row.NOT_SIGNED_IN.warn);
    }

    @Test
    public void lastSyncedInWholeMinutes() {
        long now = 10_000_000L;
        assertEquals(-1, AccountWords.minutesSince(now, 0));
        assertEquals(-1, AccountWords.minutesSince(now, now + 1));
        assertEquals(0, AccountWords.minutesSince(now, now - 59_999));
        assertEquals(1, AccountWords.minutesSince(now, now - 60_000));
        assertEquals(125, AccountWords.minutesSince(now, now - 125 * 60_000L));
    }
}
