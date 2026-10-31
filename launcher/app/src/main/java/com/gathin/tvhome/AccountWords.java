package com.gathin.tvhome;

import java.util.Locale;

/**
 * Words and numbers for the TMDB sign-in (the Account pane of Launcher settings) that need no Android: the code's
 * countdown and bar, the QR code's module size, the section's value and the time since the last sync. Pure Java,
 * unit-tested (AccountWordsTest).
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

    /** How much of the code's life is left, 1 to 0, for the bar under the countdown. */
    static float fraction(long remainingMs) {
        return Math.max(0f, Math.min(1f, remainingMs / (float) SignInFlow.TOKEN_LIFETIME));
    }

    /**
     * The Account row's value in Launcher settings (design_preview/README.md, Launcher settings): Not signed in,
     * Getting a code…, Waiting…, Signing in…, Code expired or the username. A failed attempt reads Not signed in, as in
     * the prototype; the pane says what went wrong.
     */
    enum Row {
        NOT_SIGNED_IN(false), GETTING(true), WAITING(true), SIGNING_IN(true), EXPIRED(true), SIGNED_IN(false);

        /** Shown in amber: a sign-in is under way or needs a new code. */
        final boolean warn;

        Row(boolean warn) {
            this.warn = warn;
        }
    }

    static Row row(SignInFlow.Phase phase) {
        switch (phase) {
            case REQUESTING:
                return Row.GETTING;
            case WAITING:
                return Row.WAITING;
            case FINISHING:
                return Row.SIGNING_IN;
            case EXPIRED:
                return Row.EXPIRED;
            case SIGNED_IN:
                return Row.SIGNED_IN;
            default:
                return Row.NOT_SIGNED_IN;
        }
    }

    /** Whole minutes since the last sync, for "Last synced"; -1 for none yet (or a clock that went back). */
    static long minutesSince(long now, long at) {
        if (at <= 0 || now < at) return -1;
        return (now - at) / 60_000L;
    }
}
