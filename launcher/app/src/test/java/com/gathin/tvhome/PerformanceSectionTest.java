package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Launcher settings › Performance: ↓ goes row by row (the controls' widths made the spatial D-pad skip Motion). */
public class PerformanceSectionTest {

    @Test
    public void downGoesToTheNextRowInOrder() {
        assertArrayEquals(new String[]{"perf-light", "perf-motion", "perf-shadows", "perf-glow", "perf-backdrop",
                "perf-details"}, PerformanceSection.ROWS);
        assertEquals("perf-motion", PerformanceSection.below("perf-light"));
        assertEquals("perf-shadows", PerformanceSection.below("perf-motion"));
        assertEquals("perf-glow", PerformanceSection.below("perf-shadows"));
        assertEquals("perf-backdrop", PerformanceSection.below("perf-glow"));
        assertEquals("perf-details", PerformanceSection.below("perf-backdrop"));
    }

    @Test
    public void theLastRowAndOtherGroupsAreLeftToTheDpad() {
        assertNull(PerformanceSection.below("perf-details"));
        assertNull(PerformanceSection.below("sections"));
        assertNull(PerformanceSection.below(null));
    }
}
