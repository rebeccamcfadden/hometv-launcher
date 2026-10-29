package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The search bar's text rules (design_preview/src/screens/search.ts setQuery, onText, the ghost). */
public class SearchQueryTest {

    private static final SearchIndex INDEX = new SearchIndex(new MockCatalog());

    @Test
    public void typeableCharacters() {
        for (char c : "abcxyzABCXYZ0189 -:'\u2019&.!?".toCharArray()) assertTrue(String.valueOf(c), SearchQuery.isTypeable(c));
        for (char c : "\t\n@#/\\é_+=,;".toCharArray()) assertFalse(String.valueOf(c), SearchQuery.isTypeable(c));
    }

    @Test
    public void typingLowerCasesAndCollapsesSpaces() {
        assertEquals("a", SearchQuery.type("", 'A'));
        assertEquals("", SearchQuery.type("", ' '));
        assertEquals("amy ", SearchQuery.type("amy", ' '));
        assertEquals("amy ", SearchQuery.type("amy ", ' '));
        assertEquals("amy a", SearchQuery.type("amy ", 'a'));
        assertEquals("x y", SearchQuery.clean("   x   y"));
    }

    @Test
    public void deleteRemovesTheLastCharacter() {
        assertEquals("arv", SearchQuery.delete("arvl"));
        assertEquals("", SearchQuery.delete("a"));
        assertEquals("", SearchQuery.delete(""));
        assertEquals("amy", SearchQuery.delete("amy "));
    }

    @Test
    public void ghostIsTheTopSuggestion() {
        assertEquals("arrival", SearchQuery.ghost("arvl", INDEX.search("arvl"), false));
        // Not once the query is the suggestion, whatever its case.
        assertNull(SearchQuery.ghost("arrival", INDEX.search("arrival"), false));
        assertNull(SearchQuery.ghost("Arrival", INDEX.search("Arrival"), false));
        // Not on the full results, nor without suggestions.
        assertNull(SearchQuery.ghost("arvl", INDEX.search("arvl"), true));
        assertNull(SearchQuery.ghost("qzxv", INDEX.search("qzxv"), false));
        assertNull(SearchQuery.ghost("", null, false));
        // A person or a genre can be the ghost too.
        assertEquals("amy adams", SearchQuery.ghost("amy ad", INDEX.search("amy ad"), false));
    }
}
