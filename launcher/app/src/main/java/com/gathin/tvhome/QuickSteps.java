package com.gathin.tvhome;

/**
 * The stepping rows of the quick-settings dropdown (design_preview/src/screens/quickSettings.ts): OK moves Motion to
 * its next value in place, wrapping round, and small dots show which of them is on. Pure Java, unit-tested
 * (QuickStepsTest).
 */
final class QuickSteps {
    private QuickSteps() {}

    /** The step after {@code index} of {@code count}, back to the first after the last; the first for a bad index. */
    static int next(int index, int count) {
        if (count <= 0) return 0;
        if (index < 0 || index >= count - 1) return 0;
        return index + 1;
    }
}
