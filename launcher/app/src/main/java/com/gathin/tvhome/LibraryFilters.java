package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Filters drawer's definitions and state for Movies or TV Shows, ported from
 * design_preview/src/screens/filterDefs.ts (README, Movies and TV Shows): Availability, Review score, Genre, Length
 * (movies only) and Watch state. Each definition has its options, the words it adds to the page's summary line, and
 * the test a title must pass for each option; option 0 is always "no filter". The state starts at the Figma default,
 * "Included only · Review 7.0+". Pure Java, English only (like {@link Offers}), unit-tested against the prototype.
 */
final class LibraryFilters {

    static final String AVAILABILITY = "availability";
    static final String SCORE = "score";
    static final String GENRE = "genre";
    static final String RUNTIME = "runtime";
    static final String WATCH = "watch";

    /** Genres only movies have in the mock catalogue; TV Shows leave them out of the Genre options. */
    private static final List<String> MOVIE_ONLY_GENRES = Arrays.asList("Food", "Musical", "Western", "Horror");
    private static final double[] SCORES = {0, 7, 8};
    private static final int[] RUNTIMES = {0, 90, 120, 150};

    interface Test {
        boolean test(Title t, int option);
    }

    /** One row of the drawer. */
    static final class Def {
        final String id;
        final String label;
        final List<String> options;
        /** The summary line's words per option; "" for option 0. */
        final List<String> shorts;
        private final Test test;

        Def(String id, String label, List<String> options, List<String> shorts, Test test) {
            this.id = id;
            this.label = label;
            this.options = Collections.unmodifiableList(new ArrayList<>(options));
            this.shorts = Collections.unmodifiableList(new ArrayList<>(shorts));
            this.test = test;
        }

        boolean test(Title t, int option) {
            return test.test(t, option);
        }
    }

    final Kind kind;
    final List<Def> defs;
    private final int[] values;

    /**
     * @param genres     every genre in the catalogue, sorted (Catalog.genres)
     * @param subscribed the household's services, for "Included"
     * @param seen       whether the household has seen a title (Household.isSeen), read each time a filter runs
     */
    LibraryFilters(Kind kind, List<String> genres, Set<ServiceId> subscribed, Predicate<String> seen) {
        this.kind = kind;
        this.defs = Collections.unmodifiableList(defs(kind, genres, subscribed, seen));
        this.values = new int[defs.size()];
        reset();
    }

    static List<Def> defs(Kind kind, List<String> genres, Set<ServiceId> subscribed, Predicate<String> seen) {
        List<String> g = new ArrayList<>();
        for (String genre : genres) if (kind == Kind.MOVIE || !MOVIE_ONLY_GENRES.contains(genre)) g.add(genre);
        List<Def> out = new ArrayList<>();
        out.add(new Def(AVAILABILITY, "Availability",
                Arrays.asList("Everything", "Included only", "Included + rentals"),
                Arrays.asList("", "Included only", "Included + rentals"),
                (t, i) -> i == 0 || (i == 1 ? Offers.isIncluded(t, subscribed)
                        : Offers.isIncluded(t, subscribed) || hasRental(t))));
        out.add(new Def(SCORE, "Review score",
                Arrays.asList("Any score", "7.0 and above", "8.0 and above"),
                Arrays.asList("", "Review 7.0+", "Review 8.0+"),
                (t, i) -> t.score >= SCORES[i]));
        List<String> genreOptions = new ArrayList<>();
        genreOptions.add("All genres");
        genreOptions.addAll(g);
        List<String> genreShorts = new ArrayList<>();
        genreShorts.add("");
        genreShorts.addAll(g);
        out.add(new Def(GENRE, "Genre", genreOptions, genreShorts,
                (t, i) -> i == 0 || t.genres.contains(g.get(i - 1))));
        if (kind == Kind.MOVIE) {
            out.add(new Def(RUNTIME, "Length",
                    Arrays.asList("Any length", "Under 90 minutes", "Under 2 hours", "Under 2\u00bd hours"),
                    Arrays.asList("", "Under 90 min", "Under 2h", "Under 2\u00bdh"),
                    (t, i) -> i == 0 || (t.runtime > 0 && t.runtime < RUNTIMES[i])));
        }
        out.add(new Def(WATCH, "Watch state",
                Arrays.asList("Seen or not", "Unwatched only", "Seen only"),
                Arrays.asList("", "Unwatched", "Seen"),
                (t, i) -> i == 0 || (i == 1) != seen.test(t.id)));
        return out;
    }

    private static boolean hasRental(Title t) {
        for (Offer o : t.offers) if (o.type == OfferType.RENT) return true;
        return false;
    }

    /** The definition with this id, or null (Length on TV Shows). */
    Def def(String id) {
        for (Def d : defs) if (d.id.equals(id)) return d;
        return null;
    }

    int value(Def d) {
        return values[defs.indexOf(d)];
    }

    int value(String id) {
        Def d = def(id);
        return d == null ? 0 : value(d);
    }

    /** The option the drawer shows for d now, e.g. "Included only". */
    String valueLabel(Def d) {
        return d.options.get(value(d));
    }

    /** Sets d's option, wrapped into its range. */
    void set(Def d, int option) {
        int n = d.options.size();
        values[defs.indexOf(d)] = ((option % n) + n) % n;
    }

    void set(String id, int option) {
        Def d = def(id);
        if (d != null) set(d, option);
    }

    /** ← or → in the drawer: the previous or next option, wrapping round. Returns the new option. */
    int cycle(Def d, int step) {
        set(d, value(d) + step);
        return value(d);
    }

    /** Clear all, and the empty state's Clear filters: every row back to option 0. */
    void clear() {
        Arrays.fill(values, 0);
    }

    /** The V1 default from Figma: "Included only · Review 7.0+". */
    void reset() {
        clear();
        set(AVAILABILITY, 1);
        set(SCORE, 1);
    }

    boolean passes(Title t) {
        for (int i = 0; i < defs.size(); i++) if (!defs.get(i).test(t, values[i])) return false;
        return true;
    }

    /** The titles that pass every filter, in their order. */
    List<Title> apply(List<Title> titles) {
        List<Title> out = new ArrayList<>();
        for (Title t : titles) if (passes(t)) out.add(t);
        return out;
    }

    /** How many rows are not at option 0: the n of "Filters · n". */
    int activeCount() {
        int n = 0;
        for (int v : values) if (v != 0) n++;
        return n;
    }

    /** "Included only · Review 7.0+", or "" with no filter on. */
    String summary() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < defs.size(); i++) {
            String word = defs.get(i).shorts.get(values[i]);
            if (word.isEmpty()) continue;
            if (s.length() > 0) s.append(" · ");
            s.append(word);
        }
        return s.toString();
    }
}
