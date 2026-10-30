package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
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
}
