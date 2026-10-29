package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Fuzzy, the port of design_preview/src/core/fuzzy.ts fuzzyMatch. The whole search is compared in SearchIndexTest. */
public class FuzzyTest {

    @Test
    public void subsequenceFindsArrival() {
        Fuzzy.Match m = Fuzzy.match("arvl", "Arrival");
        assertNotNull(m);
        // ARriVaL: a r . . v . l
        assertArrayEquals(new int[]{0, 1, 4, 6}, m.indices);
        // 100 − 7 × 0.4, a word start (+12), a run (+8), gaps of 2 and 1 (−2, −1): the prototype's 114.2.
        assertEquals(114.2, m.score, 1e-9);
    }

    @Test
    public void exactAndPrefixBeatAScatteredMatch() {
        double exact = Fuzzy.match("moon", "Moon").score;
        double prefix = Fuzzy.match("moon", "Moonlight").score;
        double scattered = Fuzzy.match("moon", "Mad Men of Ohio Nights").score;
        assertTrue(exact > prefix);
        assertTrue(prefix > scattered);
    }

    @Test
    public void wordStartsScore() {
        // "the" at the start of "The Arrival" scores more than t-h-e scattered through a word.
        assertTrue(Fuzzy.match("ta", "The Arrival").score > Fuzzy.match("ta", "Stark").score);
        assertArrayEquals(new int[]{0, 4}, Fuzzy.match("ta", "The Arrival").indices);
    }

    @Test
    public void oneTypoIsForgivenFromFourLetters() {
        Fuzzy.Match m = Fuzzy.match("arrivsl", "Arrival");
        assertNotNull(m);
        // Seven letters allow two; one was used: 40 − 10.
        assertEquals(30, m.score, 1e-9);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6}, m.indices);
        // A swapped pair counts as one edit (optimal string alignment).
        assertNotNull(Fuzzy.match("dnue", "Dune: Part Two"));
        assertArrayEquals(new int[]{0, 1, 2, 3}, Fuzzy.match("dnue", "Dune: Part Two").indices);
        // The typo is tried against each word: the second word of the title here.
        assertArrayEquals(new int[]{4, 5, 6, 7, 8, 9, 10}, Fuzzy.match("arrivsl", "The Arrival").indices);
    }

    @Test
    public void typoBudgetDependsOnLength() {
        // Three letters: no typo allowed.
        assertNull(Fuzzy.match("mxn", "Moon"));
        // Four to six letters: one.
        assertNotNull(Fuzzy.match("mopn", "Moon"));
        assertNull(Fuzzy.match("mqpn", "Moon"));
        // Seven and more: two.
        assertNotNull(Fuzzy.match("sevrence", "Severance"));
        assertEquals(20, Fuzzy.match("sevrence", "Severance").score, 1e-9);
        // A query with a space never falls back to typos.
        assertNull(Fuzzy.match("arrivsl x", "Arrival"));
    }

    @Test
    public void accentsAndCaseAreIgnored() {
        assertNotNull(Fuzzy.match("carnivale", "Carnivàle"));
        assertNotNull(Fuzzy.match("HÜLLER", "Sandra Hüller"));
        assertEquals("carnivale", Fuzzy.fold("Carnivàle"));
        // Folding keeps one character per character, so indices still point into the original text.
        assertEquals("Timothée Chalamet".length(), Fuzzy.fold("Timothée Chalamet").length());
    }

    @Test
    public void spacesAreNormalized() {
        assertEquals("ar vl", Fuzzy.normalize("  ar   VL "));
        assertArrayEquals(Fuzzy.match("arvl", "Arrival").indices, Fuzzy.match("  arvl ", "Arrival").indices);
    }

    @Test
    public void blankOrMissingIsNull() {
        assertNull(Fuzzy.match("", "Arrival"));
        assertNull(Fuzzy.match("   ", "Arrival"));
        assertNull(Fuzzy.match("qzxv", "Arrival"));
    }

    @Test
    public void separatorsStartWords() {
        assertTrue(Fuzzy.isSeparator(' '));
        assertTrue(Fuzzy.isSeparator('-'));
        assertTrue(Fuzzy.isSeparator(':'));
        assertTrue(Fuzzy.isSeparator('\u2019'));
        assertTrue(Fuzzy.isSeparator('&'));
        // The "s" after the hyphen of One-Shots starts a word; the one at the end does not.
        assertEquals(100 - 9 * 0.4 + 12, Fuzzy.match("s", "One-Shots").score, 1e-9);
        assertEquals(100 - 5 * 0.4, Fuzzy.match("s", "Moons").score, 1e-9);
    }

    @Test
    public void osaDistance() {
        assertEquals(0, Fuzzy.osa("dune", "dune"));
        assertEquals(1, Fuzzy.osa("dnue", "dune"));
        assertEquals(1, Fuzzy.osa("dun", "dune"));
        assertEquals(2, Fuzzy.osa("sevrence", "severance"));
        assertEquals(3, Fuzzy.osa("", "abc"));
    }
}
