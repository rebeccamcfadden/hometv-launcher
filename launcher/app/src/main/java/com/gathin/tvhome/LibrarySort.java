package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Sort chip's orders on Movies and TV Shows (the project lead, 2026-11-01, F4): Alphabetical (A–Z) by default,
 * Recently added to my lists, Newest and Oldest release first, and Highest rated. Every order but A–Z falls back to
 * A–Z between titles it ranks the same, and puts the titles it can't rank (no year, no score, on no list it knows the
 * order of) last, in A–Z. A–Z leaves out a leading "The", "A" or "An", accents and case. Pure Java, unit-tested.
 *
 * <p>No list says when a title was added to it: the TV keeps My list in the order titles were added, TMDB returns the
 * watchlist oldest first and a list in its own order. So "Recently added" reads each list from its end (BACKLOG.md,
 * From M4): the last title on a list counts as its newest, and across lists (All lists) each list's last titles come
 * first, in chip order, then each list's second-to-last, and so on.
 */
enum LibrarySort {
    AZ,
    RECENT,
    NEWEST,
    OLDEST,
    RATED;

    private static final Pattern ARTICLE = Pattern.compile("^(the|an|a)\\s+(?=\\S)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    /**
     * What A–Z compares: the name without a leading article, accents or case, from its first letter or digit
     * ("The Holdovers" files under h, "Amélie" with "amelie", "(500) Days of Summer" under 5). "A" or "The" alone is
     * kept.
     */
    static String sortName(String name) {
        if (name == null) return "";
        String s = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        int start = 0;
        while (start < s.length() && !Character.isLetterOrDigit(s.charAt(start))) start++;
        s = s.substring(start).trim();
        s = ARTICLE.matcher(s).replaceFirst("");
        return s.toLowerCase(Locale.ROOT);
    }

    private static final Comparator<Title> BY_NAME = (a, b) -> {
        int c = sortName(a.name).compareTo(sortName(b.name));
        if (c != 0) return c;
        c = String.valueOf(a.name).compareTo(String.valueOf(b.name));
        return c != 0 ? c : String.valueOf(a.id).compareTo(String.valueOf(b.id));
    };

    /** A known value first; two known by the order asked; two unknown the same. */
    private static int known(boolean hasA, boolean hasB) {
        return hasA == hasB ? 0 : hasA ? -1 : 1;
    }

    private static boolean hasYear(Title t) {
        return t.year > 0;
    }

    private static boolean hasScore(Title t) {
        return t.score > 0 && !Double.isNaN(t.score);
    }

    /**
     * The order, with ties in A–Z. {@code recent} ranks titles for {@link #RECENT} (smaller is newer,
     * {@link #recentRanks}); the other orders ignore it, and a title it leaves out goes last.
     */
    Comparator<Title> comparator(Map<String, Integer> recent) {
        switch (this) {
            case RECENT: {
                Map<String, Integer> r = recent == null ? Collections.<String, Integer>emptyMap() : recent;
                return (a, b) -> {
                    Integer ra = r.get(a.id), rb = r.get(b.id);
                    int c = known(ra != null, rb != null);
                    if (c == 0 && ra != null) c = Integer.compare(ra, rb);
                    return c != 0 ? c : BY_NAME.compare(a, b);
                };
            }
            case NEWEST:
            case OLDEST: {
                boolean newest = this == NEWEST;
                return (a, b) -> {
                    int c = known(hasYear(a), hasYear(b));
                    if (c == 0 && hasYear(a)) c = newest ? Integer.compare(b.year, a.year) : Integer.compare(a.year, b.year);
                    return c != 0 ? c : BY_NAME.compare(a, b);
                };
            }
            case RATED:
                return (a, b) -> {
                    int c = known(hasScore(a), hasScore(b));
                    if (c == 0 && hasScore(a)) c = Double.compare(b.score, a.score);
                    return c != 0 ? c : BY_NAME.compare(a, b);
                };
            default:
                return BY_NAME;
        }
    }

    /** The titles in this order, as a new list. */
    List<Title> sort(List<Title> titles, Map<String, Integer> recent) {
        List<Title> out = new ArrayList<>(titles);
        Collections.sort(out, comparator(recent));
        return out;
    }

    /**
     * Ranks for {@link #RECENT} from the lists' ids, each oldest first, in chip order: 0 for the newest. A list's last
     * id ranks before every list's second-to-last, and between two lists the earlier chip's comes first; an id on more
     * than one list takes its best place. A null list is skipped.
     */
    static Map<String, Integer> recentRanks(List<List<String>> lists) {
        // Per id: how far from its list's end, then which list.
        Map<String, long[]> best = new HashMap<>();
        for (int li = 0; li < lists.size(); li++) {
            List<String> ids = lists.get(li);
            if (ids == null) continue;
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                if (id == null) continue;
                long[] place = {ids.size() - 1 - i, li};
                long[] was = best.get(id);
                if (was == null || place[0] < was[0] || (place[0] == was[0] && place[1] < was[1])) best.put(id, place);
            }
        }
        List<Map.Entry<String, long[]>> all = new ArrayList<>(best.entrySet());
        Collections.sort(all, (a, b) -> {
            int c = Long.compare(a.getValue()[0], b.getValue()[0]);
            return c != 0 ? c : Long.compare(a.getValue()[1], b.getValue()[1]);
        });
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < all.size(); i++) out.put(all.get(i).getKey(), i);
        return out;
    }
}
