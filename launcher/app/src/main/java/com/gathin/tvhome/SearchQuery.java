package com.gathin.tvhome;

import java.util.Locale;

/**
 * The search bar's text rules, from design_preview/src/screens/search.ts: what typing does to the query, which
 * characters a keyboard may type, and the ghost completion shown after the query. Pure Java, unit-tested.
 */
final class SearchQuery {
    private SearchQuery() {}

    /** The characters a hardware keyboard or a remote with keys may type into the bar (search.ts TYPEABLE). */
    static boolean isTypeable(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || " -:'\u2019&.!?".indexOf(c) >= 0;
    }

    /** The query after an edit: no leading space, and never two spaces in a row (search.ts setQuery). */
    static String clean(String q) {
        int i = 0;
        while (i < q.length() && Character.isWhitespace(q.charAt(i))) i++;
        return q.substring(i).replaceAll("\\s{2,}", " ");
    }

    /** The query with c typed at its end, lower-cased as the prototype's onText does. */
    static String type(String q, char c) {
        return clean(q + Character.toLowerCase(c));
    }

    /** The query with its last character removed (⌫). */
    static String delete(String q) {
        return q.isEmpty() ? q : clean(q.substring(0, q.length() - 1));
    }

    /**
     * The ghost completion: the top suggestion in lower case, unless it is what was typed, or the full results are
     * showing. Null when there is none. The bar shows it after an arrow, "arvl → arrival".
     */
    static String ghost(String query, SearchIndex.Results results, boolean expanded) {
        if (expanded || results == null || results.suggestions.isEmpty()) return null;
        String top = results.suggestions.get(0).label.toLowerCase(Locale.ROOT);
        return top.equals(query.toLowerCase(Locale.ROOT)) ? null : top;
    }
}
