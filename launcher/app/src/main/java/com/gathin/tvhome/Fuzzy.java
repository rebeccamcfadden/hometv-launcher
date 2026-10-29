package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Search's fuzzy matching, ported from design_preview/src/core/fuzzy.ts (README, Product decisions: "arvl" finds
 * Arrival and small typos are forgiven). A query matches when its letters appear in order in the text, scored for
 * letters at the start of a word and letters in a run; failing that, a query of four or more letters may match a
 * single word with one typo (two from seven letters). Accents and case are ignored. Pure Java, unit-tested on the
 * JVM against the prototype's results (FuzzyTest).
 */
final class Fuzzy {
    private Fuzzy() {}

    /** A match: its score (higher is better) and the matched characters' indices in the text, for highlighting. */
    static final class Match {
        final double score;
        final int[] indices;

        Match(double score, int[] indices) {
            this.score = score;
            this.indices = indices;
        }

        /** The same match with its score moved by delta (people and genres rank a little below titles). */
        Match shifted(double delta) {
            return new Match(score + delta, indices);
        }
    }

    /**
     * Lower case, one base letter per character ("Carnivàle" to "carnivale"), so an index into the folded string is
     * an index into the original.
     */
    static String fold(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            String one = new String(Character.toChars(cp));
            out.append(Normalizer.normalize(one, Normalizer.Form.NFD).charAt(0));
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    /** Damerau-Levenshtein (optimal string alignment) distance. */
    static int osa(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 1; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    /** The characters that end a word: white space and - : . ' ’ &amp; (fuzzy.ts isWordStart). */
    static boolean isSeparator(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || "-:.'\u2019&".indexOf(c) >= 0;
    }

    private static boolean isWordStart(String t, int i) {
        return i == 0 || isSeparator(t.charAt(i - 1));
    }

    /** The query as matched: folded, runs of white space made one space, trimmed. */
    static String normalize(String query) {
        return fold(query).replaceAll("\\s+", " ").trim();
    }

    /**
     * Subsequence match ("arvl" to ARriVaL), scored for word starts and runs; falls back to one or two typos against
     * a single word ("arrivsl" to Arrival). Null when the query is blank or nothing matches.
     */
    static Match match(String query, String text) {
        String q = normalize(query);
        if (q.isEmpty()) return null;
        String t = fold(text);

        int[] found = new int[q.length()];
        int qi = 0;
        for (int i = 0; i < t.length() && qi < q.length(); i++) {
            if (t.charAt(i) == q.charAt(qi)) found[qi++] = i;
        }
        if (qi == q.length()) {
            double score = 100 - t.length() * 0.4;
            for (int k = 0; k < found.length; k++) {
                if (isWordStart(t, found[k])) score += 12;
                if (k > 0) {
                    int gap = found[k] - found[k - 1] - 1;
                    score += gap == 0 ? 8 : -Math.min(gap, 8);
                }
            }
            if (t.startsWith(q)) score += 30;
            if (t.equals(q)) score += 40;
            return new Match(score, found);
        }

        if (q.length() >= 4 && q.indexOf(' ') < 0) {
            int budget = q.length() >= 7 ? 2 : 1;
            int i = 0;
            while (i < t.length()) {
                if (isSeparator(t.charAt(i))) {
                    i++;
                    continue;
                }
                int start = i;
                while (i < t.length() && !isSeparator(t.charAt(i))) i++;
                String word = t.substring(start, i);
                int d = Math.min(osa(q, word), osa(q, word.substring(0, Math.min(word.length(), q.length()))));
                if (d <= budget) {
                    int len = Math.min(word.length(), q.length());
                    int[] indices = new int[len];
                    for (int k = 0; k < len; k++) indices[k] = start + k;
                    return new Match(40 - d * 10, indices);
                }
            }
        }
        return null;
    }
}
