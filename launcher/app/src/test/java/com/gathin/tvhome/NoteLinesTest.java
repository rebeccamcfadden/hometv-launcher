package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class NoteLinesTest {
    private static List<String> queries(String text) {
        List<String> out = new ArrayList<>();
        for (NoteLines.Line l : NoteLines.parse(text)) out.add(l.query);
        return out;
    }

    private static NoteLines.Line only(String text) {
        List<NoteLines.Line> l = NoteLines.parse(text);
        assertEquals(l.toString(), 1, l.size());
        return l.get(0);
    }

    @Test
    public void emptyTextHasNoLines() {
        assertTrue(NoteLines.parse(null).isEmpty());
        assertTrue(NoteLines.parse("  \n\n ").isEmpty());
    }

    @Test
    public void keepChecklistDropsTheTitleAndReadsTicks() {
        List<NoteLines.Line> l = NoteLines.parse("Movies to watch\n☐ Dune\n☑ Arrival\n☐ Past Lives\n");
        assertEquals(3, l.size());
        assertEquals("Dune", l.get(0).query);
        assertFalse(l.get(0).ticked);
        assertEquals("Arrival", l.get(1).query);
        assertTrue(l.get(1).ticked);
    }

    @Test
    public void markdownCheckboxesAndBullets() {
        List<NoteLines.Line> l = NoteLines.parse("Watch\n- [ ] Dune\n- [x] Arrival\n* [X] Moon\n• Coda\n1. Fargo\n2) Dark");
        assertEquals(List.of("Dune", "Arrival", "Moon", "Coda", "Fargo", "Dark"), queries(
                "Watch\n- [ ] Dune\n- [x] Arrival\n* [X] Moon\n• Coda\n1. Fargo\n2) Dark"));
        assertFalse(l.get(0).ticked);
        assertTrue(l.get(1).ticked);
        assertTrue(l.get(2).ticked);
        assertFalse(l.get(3).ticked);
    }

    @Test
    public void otherTickMarksAndStrikethrough() {
        assertTrue(only("1. [x] Dune").ticked);
        assertEquals("Dune", only("1. [x] Dune").query);
        assertFalse(only("2) [ ] Dune").ticked);
        assertEquals("Dune", only("2) [ ] Dune").query);
        assertTrue(only("✓ Dune").ticked);
        assertTrue(only("✅ Dune").ticked);
        assertTrue(only("✔️ Dune").ticked);
        assertTrue(only("~~Dune~~").ticked);
        assertTrue(only("- ~~Dune~~").ticked);
        assertEquals("Dune", only("- ~~Dune~~").query);
    }

    @Test
    public void plainNoteKeepsItsFirstLineUnlessItNamesTheList() {
        assertEquals(List.of("Dune", "Arrival"), queries("Dune\nArrival"));
        assertEquals(List.of("Dune", "Arrival"), queries("Movies to watch\nDune\nArrival"));
        assertEquals(List.of("Dune", "Arrival"), queries("Our watchlist\n\nDune\nArrival"));
    }

    @Test
    public void yearInBracketsNarrowsTheSearch() {
        NoteLines.Line l = only("☐ Dune (2021)");
        assertEquals("Dune", l.query);
        assertEquals(2021, l.year);
        assertEquals(1999, only("The Matrix [1999]").year);
    }

    @Test
    public void numbersInTitlesStay() {
        assertEquals("Blade Runner 2049", only("Blade Runner 2049").query);
        assertEquals(0, only("Blade Runner 2049").year);
        assertEquals("1917", only("1917").query);
        assertEquals("2001: A Space Odyssey", only("2001: A Space Odyssey").query);
        assertEquals("Mission: Impossible", only("Mission: Impossible").query);
        assertEquals("Se7en", only("Se7en").query);
    }

    @Test
    public void kindFromTheLineOrASeason() {
        assertEquals(Kind.SHOW, only("Fargo (tv)").kind);
        assertEquals(Kind.SHOW, only("Fargo (series)").kind);
        assertEquals(Kind.MOVIE, only("Fargo (movie)").kind);
        NoteLines.Line s = only("Andor S2");
        assertEquals("Andor", s.query);
        assertEquals(Kind.SHOW, s.kind);
        assertEquals("The Bear", only("The Bear - season 3").query);
        NoteLines.Line both = only("Fargo S2 (2014)");
        assertEquals("Fargo", both.query);
        assertEquals(2014, both.year);
        assertNull(only("Dune").kind);
    }

    @Test
    public void headingsHintTheKindAndAreNotTitles() {
        List<NoteLines.Line> l = NoteLines.parse("Watch list\nMovies:\n- Dune\n- Arrival\n\nShows\n- Dark\n# Later\n- Fargo");
        assertEquals(List.of("Dune", "Arrival", "Dark", "Fargo"),
                queries("Watch list\nMovies:\n- Dune\n- Arrival\n\nShows\n- Dark\n# Later\n- Fargo"));
        assertEquals(Kind.MOVIE, l.get(0).hint);
        assertEquals(Kind.SHOW, l.get(2).hint);
        assertEquals(Kind.SHOW, l.get(3).hint);
        assertNull(l.get(0).kind);
    }

    @Test
    public void themoviedbLinkIsTheTitle() {
        NoteLines.Line l = only("https://www.themoviedb.org/movie/603-the-matrix");
        assertEquals("movie:603", l.id);
        assertEquals("movie:603", l.key);
        assertEquals("tv:1399", only("☑ themoviedb.org/tv/1399").id);
        assertTrue(only("☑ themoviedb.org/tv/1399").ticked);
    }

    @Test
    public void aRepeatedTitleCountsOnceTheLaterLineWinning() {
        List<NoteLines.Line> l = NoteLines.parse("- Dune\n- Arrival\n- [x] dune");
        assertEquals(2, l.size());
        assertEquals("Arrival", l.get(0).query);
        assertEquals("dune", l.get(1).query);
        assertTrue(l.get(1).ticked);
    }

    @Test
    public void keyIgnoresCaseSpacesAndCurlyQuotes() {
        assertEquals(only("Ocean’s  Eleven").key, only("ocean's eleven").key);
        assertFalse(only("Dune (2021)").key.equals(only("Dune (1984)").key));
    }

    @Test
    public void linesOfOnlySymbolsAndObjectReplacementsAreSkipped() {
        assertEquals(List.of("Dune"), queries("- Dune\n- ---\n\uFFFC\n☐"));
    }

    @Test
    public void longNotesAreCut() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < NoteLines.MAX_LINES + 50; i++) b.append("Title ").append(i).append('\n');
        assertEquals(NoteLines.MAX_LINES, NoteLines.parse(b.toString()).size());
        assertTrue(only("x".repeat(400)).query.length() <= NoteLines.MAX_LENGTH);
    }

    /** Google Keep's checklist as an iPhone shares it: the title, a blank line, open boxes, then the done ones last. */
    static final String KEEP = "Watchlist\r\n"
            + "\r\n"
            + "[ ] The other guys \r\n"
            + "[ ] \r\n"
            + "[ ]  Coda\r\n"
            + "[ ] Inglorious basterds\r\n"
            + "[ ] Angels and demons (2009) \r\n"
            + "[ ] Birdcage\r\n"
            + "[x] Arrival\r\n"
            + "[x] Billie Jean king movie\r\n";

    /** Apple Notes' checklist as an iPhone shares it: two sections, ◦ (U+25E6) and ✓ bullets, curly apostrophes. */
    static final String APPLE = "Movies to watch\r\n"
            + "\r\n"
            + "    \u25E6    Synecdoche, New York (2008)\r\n"
            + "    \u25E6    All the president\u2019s men\r\n"
            + "    \u25E6    \r\n"
            + "    \u25E6    Spotlight is pm\r\n"
            + "    \u25E6    Synecdoche New York\r\n"
            + "    \u2713    Little Miss Sunshine\r\n"
            + "\r\n"
            + "\r\n"
            + "Shows to Watch\r\n"
            + "    \u25E6    Slow Horses\r\n"
            + "    \u2713    True Detective\r\n";

    @Test
    public void aGoogleKeepChecklistSkipsItsTitleAndEmptyBoxes() {
        List<NoteLines.Line> l = NoteLines.parse(KEEP);
        assertEquals(List.of("The other guys", "Coda", "Inglorious basterds", "Angels and demons", "Birdcage", "Arrival",
                "Billie Jean king movie"), queries(KEEP));
        assertEquals(2009, l.get(3).year);
        for (int i = 0; i < 5; i++) assertFalse(l.get(i).ticked);
        assertTrue(l.get(5).ticked);
        assertTrue(l.get(6).ticked);
        for (NoteLines.Line x : l) {
            assertNull(x.kind);
            assertNull(x.hint);
        }
    }

    @Test
    public void anAppleNotesChecklistHintsMoviesThenShows() {
        List<NoteLines.Line> l = NoteLines.parse(APPLE);
        assertEquals(List.of("Synecdoche, New York", "All the president's men", "Spotlight is pm", "Synecdoche New York",
                "Little Miss Sunshine", "Slow Horses", "True Detective"), queries(APPLE));
        assertEquals(2008, l.get(0).year);
        assertEquals(0, l.get(3).year);
        for (int i = 0; i < 5; i++) assertEquals(Kind.MOVIE, l.get(i).hint);
        assertEquals(Kind.SHOW, l.get(5).hint);
        assertEquals(Kind.SHOW, l.get(6).hint);
        assertFalse(l.get(5).ticked);
        assertTrue(l.get(4).ticked);
        assertTrue(l.get(6).ticked);
        for (NoteLines.Line x : l) assertNull(x.kind);
    }
}
