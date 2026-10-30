package com.gathin.tvhome;

import java.util.Locale;

/**
 * Words and numbers for the TMDB sign-in page that need no Android: the code's countdown and the QR code's module
 * size. Pure Java, unit-tested (AccountWordsTest).
 */
final class AccountWords {
    private AccountWords() {}

    /** Light modules around a QR code, as the standard asks, so a phone finds its edges. */
    static final int QUIET = 4;

    /** "14:32" for the time left, rounded up so it reaches 0:00 only when the code has run out. */
    static String countdown(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Whole pixels per module for a code of {@code modules} drawn in at most {@code px}, quiet zone included. */
    static int moduleSize(int modules, int px) {
        return Math.max(1, px / (modules + 2 * QUIET));
    }
}
