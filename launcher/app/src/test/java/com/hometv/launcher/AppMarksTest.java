package com.hometv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** F43: Home row apps as wordmarks (App logos › Minimal). */
public class AppMarksTest {

    @Test
    public void aKnownServiceIsItsWordmark() {
        AppMarks.Mark m = AppMarks.of("com.netflix.ninja", "Netflix");
        assertEquals("NETFLIX", m.text);
        assertEquals(800, m.weight);
        // HBO Max's old package too.
        assertEquals("HBO Max", AppMarks.of("com.hbo.hbonow", "HBO Max").text);
        assertEquals("HBO Max", AppMarks.of("com.wbd.stream", "Max").text);
    }

    @Test
    public void anyOtherAppIsItsNameInMedium() {
        AppMarks.Mark m = AppMarks.of("com.example.radio", "  Radio Sweden ");
        assertEquals("Radio Sweden", m.text);
        assertEquals(AppMarks.NAME_WEIGHT, m.weight);
        assertEquals(AppMarks.NAME_SIZE, m.size, 0f);
        assertEquals("com.example.radio", AppMarks.of("com.example.radio", " ").text);
        assertEquals("", AppMarks.of(null, null).text);
    }

    @Test
    public void fullColourUnlessTheHouseholdOrTheConfigSaysMinimal() {
        assertEquals(false, AppMarks.minimal(null, null));
        assertEquals(false, AppMarks.minimal(null, "full"));
        assertEquals(true, AppMarks.minimal(null, " Minimal "));
        // The household's choice wins over the config.
        assertEquals(false, AppMarks.minimal("full", "minimal"));
        assertEquals(true, AppMarks.minimal("minimal", "full"));
        // Anything else saved reads as not chosen.
        assertEquals(false, AppMarks.minimal(Boolean.TRUE, null));
        assertEquals(true, AppMarks.minimal("mono", "minimal"));
    }

    @Test
    public void marksScaleWithTheTile() {
        assertEquals(31f, AppMarks.scaled(31, 136), 0.001f);
        assertEquals(15.5f, AppMarks.scaled(31, 68), 0.001f);
        assertEquals(31f, AppMarks.scaled(31, 0), 0.001f);
    }

    @Test
    public void weightsFallOnTheFacesThereAre() {
        assertEquals(700, AppMarks.face(800));
        assertEquals(700, AppMarks.face(600));
        assertEquals(500, AppMarks.face(500));
        assertEquals(400, AppMarks.face(400));
    }
}
