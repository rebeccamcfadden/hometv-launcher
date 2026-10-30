package com.gathin.tvhome;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Filters drawer's logic against design_preview/src/screens/filterDefs.ts. The expected outputs are in
 * src/test/resources/prototype_filters.tsv, written by running filterDefs.ts and the list rule of library.ts as they
 * are (Node 22+, --experimental-strip-types with a resolve hook for the imports that leave out ".ts", as
 * {@link PrototypeTitles} describes) with the store's default household and the services the prototype marks
 * subscribed. Regenerate it the same way if the prototype's filters or data change.
 */
public class LibraryFiltersTest {

    static List<String[]> fixture(String type) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = LibraryFiltersTest.class.getResourceAsStream("/prototype_filters.tsv");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                if (f[0].equals(type)) out.add(f);
            }
        }
        assertFalse("fixture has " + type + " lines", out.isEmpty());
        return out;
    }

    static Kind kind(String s) {
        return s.equals("movie") ? Kind.MOVIE : Kind.SHOW;
    }

    static String ids(List<Title> titles) {
        StringBuilder s = new StringBuilder();
        for (Title t : titles) {
            if (s.length() > 0) s.append(',');
            s.append(t.id);
        }
        return s.toString();
    }

    private final MockCatalog catalog = new MockCatalog();
    private final HouseholdState household = HouseholdState.defaults(catalog);
    private final Set<ServiceId> subscribed = OffersTest.PROTOTYPE;

    private LibraryFilters filters(Kind kind) {
        return new LibraryFilters(kind, catalog.genres(), subscribed, household::isSeen);
    }

    private List<Title> ofKind(Kind kind) {
        List<Title> out = new ArrayList<>();
        for (Title t : catalog.all()) if (t.kind == kind) out.add(t);
        return out;
    }

    @Test
    public void definitionsMatchThePrototype() throws Exception {
        for (String[] f : fixture("defs")) {
            StringBuilder s = new StringBuilder();
            for (LibraryFilters.Def d : filters(kind(f[1])).defs) {
                if (s.length() > 0) s.append(',');
                s.append(d.id).append(':').append(d.label);
            }
            assertEquals(f[1], f[2], s.toString());
        }
    }

    @Test
    public void everyOptionOfEveryDefinitionMatchesThePrototype() throws Exception {
        int checked = 0;
        for (String[] f : fixture("pred")) {
            Kind kind = kind(f[1]);
            LibraryFilters filters = filters(kind);
            LibraryFilters.Def d = filters.def(f[2]);
            int option = Integer.parseInt(f[3]);
            String where = f[1] + " " + f[2] + " " + option;
            assertEquals(where + " label", f[4], d.options.get(option));
            assertEquals(where + " short", f[5], d.shorts.get(option));
            List<Title> passing = new ArrayList<>();
            for (Title t : ofKind(kind)) if (d.test(t, option)) passing.add(t);
            assertEquals(where, f[6], ids(passing));
            // The same filter on its own through the state: apply, the drawer's label, the summary and the count.
            filters.clear();
            filters.set(d, option);
            assertEquals(where + " apply", f[6], ids(filters.apply(ofKind(kind))));
            assertEquals(f[4], filters.valueLabel(d));
            assertEquals(f[5], filters.summary());
            assertEquals(option == 0 ? 0 : 1, filters.activeCount());
            checked++;
        }
        assertEquals("every option of both kinds", fixturesOptionCount(), checked);
    }

    private int fixturesOptionCount() {
        int n = 0;
        for (Kind k : Kind.values()) for (LibraryFilters.Def d : filters(k).defs) n += d.options.size();
        return n;
    }

    @Test
    public void theDefaultIsIncludedOnlyAndReview7() throws Exception {
        for (Kind k : Kind.values()) {
            LibraryFilters f = filters(k);
            assertEquals(1, f.value(LibraryFilters.AVAILABILITY));
            assertEquals(1, f.value(LibraryFilters.SCORE));
            assertEquals(0, f.value(LibraryFilters.GENRE));
            assertEquals(0, f.value(LibraryFilters.RUNTIME));
            assertEquals(0, f.value(LibraryFilters.WATCH));
            assertEquals("Included only · Review 7.0+", f.summary());
            assertEquals(2, f.activeCount());
        }
        java.util.function.Predicate<String> my = household::inMyList;
        for (String[] f : fixture("default")) {
            Kind kind = kind(f[1]);
            LibraryFilters filters = filters(kind);
            List<Title> pool = LibraryLists.pool(catalog.all(), kind, my);
            List<Title> under = LibraryLists.under(pool, LibraryListsTest.choice(f[2]), my);
            assertEquals(f[1] + " " + f[2], f[3], ids(filters.apply(under)));
            assertEquals(f[4], filters.summary());
            assertEquals(Integer.parseInt(f[5]), filters.activeCount());
        }
    }

    @Test
    public void movies14Of16AsInFigma() {
        java.util.function.Predicate<String> my = household::inMyList;
        List<Title> pool = LibraryLists.pool(catalog.all(), Kind.MOVIE, my);
        assertEquals(16, pool.size());
        assertEquals(14, filters(Kind.MOVIE).apply(pool).size());
    }

    @Test
    public void summaryKeepsTheDrawersOrderAndCountsEveryActiveRow() {
        LibraryFilters f = filters(Kind.MOVIE);
        f.clear();
        assertEquals("", f.summary());
        assertEquals(0, f.activeCount());
        f.set(LibraryFilters.WATCH, 1);
        f.set(LibraryFilters.GENRE, f.def(LibraryFilters.GENRE).options.indexOf("Drama"));
        f.set(LibraryFilters.RUNTIME, 2);
        f.set(LibraryFilters.SCORE, 2);
        f.set(LibraryFilters.AVAILABILITY, 2);
        assertEquals("Included + rentals · Review 8.0+ · Drama · Under 2h · Unwatched", f.summary());
        assertEquals(5, f.activeCount());
        f.reset();
        assertEquals("Included only · Review 7.0+", f.summary());
    }

    @Test
    public void leftAndRightWrapRound() {
        LibraryFilters f = filters(Kind.MOVIE);
        LibraryFilters.Def a = f.def(LibraryFilters.AVAILABILITY);
        assertEquals(2, f.cycle(a, 1));
        assertEquals(0, f.cycle(a, 1));
        assertEquals(2, f.cycle(a, -1));
        assertEquals("Included + rentals", f.valueLabel(a));
        LibraryFilters.Def g = f.def(LibraryFilters.GENRE);
        assertEquals(g.options.size() - 1, f.cycle(g, -1));
        assertEquals("Western", f.valueLabel(g));
        assertEquals(0, f.cycle(g, 1));
    }

    @Test
    public void tvShowsHaveNoLengthNorMovieOnlyGenres() {
        LibraryFilters f = filters(Kind.SHOW);
        assertNull(f.def(LibraryFilters.RUNTIME));
        assertEquals(4, f.defs.size());
        f.set(LibraryFilters.RUNTIME, 1);
        assertEquals(0, f.value(LibraryFilters.RUNTIME));
        List<String> genres = f.def(LibraryFilters.GENRE).options;
        for (String g : new String[]{"Food", "Musical", "Western", "Horror"}) assertFalse(g, genres.contains(g));
        assertTrue(filters(Kind.MOVIE).def(LibraryFilters.GENRE).options.contains("Western"));
    }

    @Test
    public void watchStateReadsTheHouseholdEachTime() {
        LibraryFilters f = filters(Kind.MOVIE);
        f.clear();
        f.set(LibraryFilters.WATCH, 2);
        assertEquals("knives-out,budapest", ids(f.apply(ofKind(Kind.MOVIE))));
        household.toggleSeen("arrival");
        assertEquals("arrival,knives-out,budapest", ids(f.apply(ofKind(Kind.MOVIE))));
        f.set(LibraryFilters.WATCH, 1);
        for (Title t : f.apply(ofKind(Kind.MOVIE))) assertFalse(t.id, household.isSeen(t.id));
    }

    @Test
    public void includedFollowsTheHouseholdsServices() {
        LibraryFilters none = new LibraryFilters(Kind.MOVIE, catalog.genres(), Collections.emptySet(),
                household::isSeen);
        none.clear();
        none.set(LibraryFilters.AVAILABILITY, 1);
        for (Title t : none.apply(ofKind(Kind.MOVIE))) {
            boolean free = false;
            for (Offer o : t.offers) free |= o.type == OfferType.FREE;
            assertTrue(t.id + " passes Included only with no services only when free", free);
        }
        none.set(LibraryFilters.AVAILABILITY, 2);
        for (Title t : none.apply(ofKind(Kind.MOVIE))) {
            boolean freeOrRent = false;
            for (Offer o : t.offers) freeOrRent |= o.type == OfferType.FREE || o.type == OfferType.RENT;
            assertTrue(t.id, freeOrRent);
        }
    }

    @Test
    public void aMovieWhoseLengthIsNotKnownYetFailsALengthFilter() {
        LibraryFilters f = filters(Kind.MOVIE);
        f.clear();
        Title.Draft d = new Title.Draft(catalog.get("arrival"));
        d.runtime = 0;
        d.detailed = false;
        f.set(LibraryFilters.RUNTIME, 1);
        assertFalse(f.passes(d.build()));
        f.set(LibraryFilters.RUNTIME, 0);
        assertTrue(f.passes(d.build()));
    }
}
