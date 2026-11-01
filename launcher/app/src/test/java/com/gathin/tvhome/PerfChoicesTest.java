package com.gathin.tvhome;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Launcher settings › Performance: where a TV starts, and the household's choices over it. */
public class PerfChoicesTest {

    @Test
    public void lightDefaultsFollowTheLowRamFlagUnlessTheConfigSays() {
        assertTrue(PerfChoices.light("auto", true));
        assertFalse(PerfChoices.light("auto", false));
        assertTrue(PerfChoices.light("", true));
        assertTrue(PerfChoices.light(null, true));
        assertTrue(PerfChoices.light("on", false));
        assertTrue(PerfChoices.light(" TRUE ", false));
        assertFalse(PerfChoices.light("off", true));
        assertFalse(PerfChoices.light("false", true));
        assertTrue(PerfChoices.light("maybe", true));
    }

    @Test
    public void nothingSavedStartsAtTheTvsEnd() {
        Map<String, Object> none = new HashMap<>();
        PerfChoices light = PerfChoices.resolve(none, true);
        assertEquals(PerfChoices.Shadows.OFF, light.shadows);
        assertTrue(light.glow);
        assertFalse(light.plainBackdrop);
        assertFalse(light.details);
        assertTrue(light.same(PerfChoices.LIGHT));
        assertTrue(PerfChoices.resolve(none, false).same(PerfChoices.FULL));
        assertFalse(PerfChoices.anySaved(none));
    }

    @Test
    public void savedChoicesWinOverTheStart() {
        Map<String, Object> saved = new HashMap<>();
        saved.put(PerfChoices.KEY_SHADOWS, "focus");
        saved.put(PerfChoices.KEY_GLOW, false);
        saved.put(PerfChoices.KEY_BACKDROP, "plain");
        saved.put(PerfChoices.KEY_DETAILS, true);
        for (boolean light : new boolean[]{true, false}) {
            PerfChoices p = PerfChoices.resolve(saved, light);
            assertEquals(PerfChoices.Shadows.FOCUS, p.shadows);
            assertFalse(p.glow);
            assertTrue(p.plainBackdrop);
            assertTrue(p.details);
        }
        assertTrue(PerfChoices.anySaved(saved));
        // Shadows back On on a light-start TV; the rest keep its start.
        Map<String, Object> on = new HashMap<>();
        on.put(PerfChoices.KEY_SHADOWS, "on");
        on.put(PerfChoices.KEY_BACKDROP, "photo");
        PerfChoices p = PerfChoices.resolve(on, true);
        assertEquals(PerfChoices.Shadows.ON, p.shadows);
        assertFalse(p.plainBackdrop);
        assertFalse(p.details);
    }

    @Test
    public void unknownOrWronglyTypedValuesAreIgnored() {
        Map<String, Object> odd = new HashMap<>();
        odd.put(PerfChoices.KEY_SHADOWS, "sometimes");
        odd.put(PerfChoices.KEY_GLOW, "yes");
        odd.put(PerfChoices.KEY_BACKDROP, 3);
        odd.put(PerfChoices.KEY_DETAILS, "on");
        assertTrue(PerfChoices.resolve(odd, false).same(PerfChoices.FULL));
        assertTrue(PerfChoices.resolve(odd, true).same(PerfChoices.LIGHT));
    }

    @Test
    public void lightModeIsEveryChoiceAtItsLightest() {
        PerfChoices m = PerfChoices.MINIMUM;
        assertEquals(PerfChoices.Shadows.OFF, m.shadows);
        assertFalse(m.glow);
        assertTrue(m.plainBackdrop);
        assertFalse(m.details);
        assertFalse(m.same(PerfChoices.LIGHT));
    }

    @Test
    public void theViewsDrawWithWhatWasApplied() {
        try {
            PerfChoices.apply(PerfChoices.LIGHT);
            assertSame(PerfChoices.LIGHT, PerfChoices.current());
            PerfChoices.apply(null);
            assertSame(PerfChoices.FULL, PerfChoices.current());
        } finally {
            PerfChoices.apply(PerfChoices.FULL);
        }
    }
}
