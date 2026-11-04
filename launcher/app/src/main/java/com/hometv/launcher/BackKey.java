package com.hometv.launcher;

/**
 * F47: the remote's Back acts when it is released, and only when it was also pressed here. YouTube's TV app closes
 * on Back's press, so after a trailer the release alone reaches Home; acting on it would close Pick for me's picks
 * as well (found on the TCL, 2026-11-04). Pure Java, unit-tested.
 */
final class BackKey {
    private BackKey() {
    }

    /** Whether a Back released now goes back: pressed here, and not cancelled (a gesture, a dialog taking it). */
    static boolean acts(boolean pressedHere, boolean canceled) {
        return pressedHere && !canceled;
    }
}
