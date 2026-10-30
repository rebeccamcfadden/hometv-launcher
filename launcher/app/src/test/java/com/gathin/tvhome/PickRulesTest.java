package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Pick for me's rules against design_preview/src/screens/pick.ts and its reference shots (pick-01, pick-02). */
public class PickRulesTest {
    private static final Set<ServiceId> SUBS = OffersTest.PROTOTYPE;
    private static final Set<ServiceId> NONE = Collections.emptySet();

    private final MockCatalog catalog = new MockCatalog();

    /** The household's seed (store.ts defaults): My list from the MY titles, seen knives-out and budapest. */
    private final HouseholdState seed = HouseholdState.defaults(catalog);
    private final PickRules.Household household = household(seed);

    private static PickRules.Household household(HouseholdState s) {
        return new PickRules.Household() {
            @Override
            public boolean inMyList(String id) {
                return s.inMyList(id);
            }

            @Override
            public boolean isSeen(String id) {
                return s.isSeen(id);
            }
        };
    }

    /** A household with exactly these titles on My list and these seen. */
    private static PickRules.Household household(Set<String> myList, Set<String> seen) {
        return new PickRules.Household() {
            @Override
            public boolean inMyList(String id) {
                return myList.contains(id);
            }

            @Override
            public boolean isSeen(String id) {
                return seen.contains(id);
            }
        };
    }

    private static final PickRules.Household EMPTY = household(Collections.emptySet(), Collections.emptySet());

    private static Title movie(String id, int runtime, String rating, double score, List<String> genres, List<Offer> offers,
                               ListId... lists) {
        return new Title(id, Kind.MOVIE, id, 2020, genres, runtime, rating, score, "", id, offers, Arrays.asList(lists),
                null, null, null, 0, 0);
    }

    private static Title show(String id, int... episodeRuntimes) {
        List<Episode> eps = new ArrayList<>();
        for (int i = 0; i < episodeRuntimes.length; i++) eps.add(new Episode(i + 1, "E" + (i + 1), episodeRuntimes[i]));
        List<Season> seasons = eps.isEmpty() ? null : Collections.singletonList(new Season(1, eps));
        return new Title(id, Kind.SHOW, id, 2020, Collections.singletonList("Drama"), 0, "TV-14", 8, "", id,
                null, Collections.singletonList(ListId.MY), null, null, seasons, 0, 0);
    }

    private static Title movie(String id, int runtime) {
        return movie(id, runtime, "PG", 8, Collections.singletonList("Drama"), null, ListId.HOUSEHOLD);
    }

    private static Title rated(String rating) {
        return movie(rating, 100, rating, 8, Collections.singletonList("Drama"), null, ListId.HOUSEHOLD);
    }

    private static Title scored(double score) {
        return movie("s" + score, 100, "PG", score, Collections.singletonList("Drama"), null, ListId.HOUSEHOLD);
    }

    private static Title genres(String... g) {
        return movie(String.join("/", g), 100, "PG", 8, Arrays.asList(g), null, ListId.HOUSEHOLD);
    }

    private static boolean test(int field, int option, Title t) {
        return PickRules.test(field, option, t, EMPTY, SUBS);
    }

    private static List<String> ids(List<Title> titles) {
        List<String> out = new ArrayList<>();
        for (Title t : titles) out.add(t.id);
        return out;
    }

    private List<Title> matches(PickRules.State s) {
        return PickRules.matches(s, catalog.all(), household, SUBS);
    }

    /** Every field at its first option ("Any …", Unwatched only, Included + paid). */
    private static PickRules.State open(Kind kind) {
        PickRules.State s = PickRules.defaults(kind);
        Arrays.fill(s.values, 0);
        return s;
    }

    // ---- The defaults and the reference shots ------------------------------------------------------------------

    @Test
    public void defaultsAreTheShotsSetup() {
        PickRules.State s = PickRules.defaults(Kind.MOVIE);
        assertEquals(PickRules.Source.LISTS, s.source);
        assertEquals(EnumSet.of(ListId.MY, ListId.HOUSEHOLD), s.lists);
        assertEquals("Under 2 hours", PickRules.option(PickRules.LENGTH, s.values[PickRules.LENGTH]));
        assertEquals("Drama + Sci-fi", PickRules.option(PickRules.GENRE, s.values[PickRules.GENRE]));
        assertEquals("7.0 and above", PickRules.option(PickRules.SCORE, s.values[PickRules.SCORE]));
        assertEquals("Up to R", PickRules.option(PickRules.RATING, s.values[PickRules.RATING]));
        assertEquals("Unwatched only", PickRules.option(PickRules.WATCH, s.values[PickRules.WATCH]));
        assertEquals("Included + paid", PickRules.option(PickRules.AVAIL, s.values[PickRules.AVAIL]));
    }

    /** pick-01-setup.png: 4 picks, 6 titles match; pick-02-results.png draws its four from these six. */
    @Test
    public void defaultMoviesMatchTheShot() {
        PickRules.State s = PickRules.defaults(Kind.MOVIE);
        List<Title> m = matches(s);
        assertEquals(Arrays.asList("arrival", "past-lives", "coda", "moon", "zone", "american-fiction"), ids(m));
        int n = PickRules.pickCount(m.size());
        assertEquals(4, n);
        assertEquals("picks, just for tonight", PickRules.countText(n));
        assertEquals("Unwatched movies from My list + Household.", PickRules.summaryLead(s, 7));
        assertEquals("Movies · Under 2 hours · Drama + Sci-fi · 7.0 and above · Up to R · Unwatched only · Included + paid",
                PickRules.filterText(s));
        assertEquals("Find 4 picks", PickRules.findLabel(n));
        assertEquals("6 titles match. Reroll on the next screen, or adjust filters.", PickRules.note(m.size()));
        assertEquals("4 random matches · My list + Household · All 7 services", PickRules.resultsContext(4, s, 7));
        assertEquals("Reroll 4 picks", PickRules.rerollLabel(n));
    }

    @Test
    public void defaultShows() {
        PickRules.State s = PickRules.defaults(Kind.SHOW);
        // Shows on My list or Household, Drama or Sci-fi, 7.0+, any TV rating but NR, unseen.
        assertEquals(Arrays.asList("severance", "the-bear", "last-of-us", "shogun", "fallout", "the-crown"), ids(matches(s)));
        assertEquals("TV Shows · Under 2 hours · Drama + Sci-fi · 7.0 and above · Up to R · Unwatched only · Included + paid",
                PickRules.filterText(s));
        assertEquals("Unwatched shows from My list + Household.", PickRules.summaryLead(s, 7));
    }

    // ---- Each field ------------------------------------------------------------------------------------------------

    @Test
    public void lengthForMovies() {
        assertEquals("Movie length", PickRules.label(PickRules.LENGTH, Kind.MOVIE));
        assertTrue(test(PickRules.LENGTH, 0, movie("long", 240)));
        assertTrue(test(PickRules.LENGTH, 1, movie("a", 119)));
        assertFalse(test(PickRules.LENGTH, 1, movie("b", 120)));
        assertTrue(test(PickRules.LENGTH, 2, movie("c", 89)));
        assertFalse(test(PickRules.LENGTH, 2, movie("d", 90)));
    }

    /** "TV Shows use episode length": the episodes' average; a show with none listed passes, as in the prototype. */
    @Test
    public void lengthForShowsIsTheEpisodeLength() {
        assertEquals("Episode length", PickRules.label(PickRules.LENGTH, Kind.SHOW));
        assertEquals(55, PickRules.episodeLength(show("x", 50, 60, 55)));
        assertTrue(test(PickRules.LENGTH, 2, show("short", 30, 40)));
        assertFalse(test(PickRules.LENGTH, 2, show("feature", 95, 100)));
        assertTrue(test(PickRules.LENGTH, 1, show("feature", 95, 100)));
        assertFalse(test(PickRules.LENGTH, 1, show("epic", 130)));
        assertTrue(test(PickRules.LENGTH, 2, show("unknown")));
        assertEquals(0, PickRules.episodeLength(show("unknown")));
        // Every show in the mock catalogue has episodes under 90 minutes, so the field passes them all, as there.
        for (Title t : catalog.all()) {
            if (!t.isMovie()) assertTrue(t.id, test(PickRules.LENGTH, 2, t));
        }
    }

    @Test
    public void genreMatchesEitherOfThePair() {
        assertEquals("Genre · Match either", PickRules.label(PickRules.GENRE, Kind.MOVIE));
        assertTrue(test(PickRules.GENRE, 0, genres("Western")));
        assertTrue(test(PickRules.GENRE, 1, genres("Drama")));
        assertTrue(test(PickRules.GENRE, 1, genres("Comedy", "Sci-fi")));
        assertFalse(test(PickRules.GENRE, 1, genres("Comedy")));
        assertTrue(test(PickRules.GENRE, 2, genres("Animation")));
        assertTrue(test(PickRules.GENRE, 3, genres("Thriller")));
        assertFalse(test(PickRules.GENRE, 3, genres("Drama")));
        assertTrue(test(PickRules.GENRE, 4, genres("Documentary")));
        assertFalse(test(PickRules.GENRE, 4, genres("Drama", "History")));
    }

    @Test
    public void scoreIsInclusive() {
        assertTrue(test(PickRules.SCORE, 0, scored(1)));
        assertTrue(test(PickRules.SCORE, 1, scored(7.0)));
        assertFalse(test(PickRules.SCORE, 1, scored(6.9)));
        assertTrue(test(PickRules.SCORE, 2, scored(8.0)));
        assertFalse(test(PickRules.SCORE, 2, scored(7.9)));
    }

    @Test
    public void contentRating() {
        for (String r : new String[]{"G", "PG", "PG-13", "R", "NR", "TV-MA", "TV-14"}) {
            assertTrue(r, test(PickRules.RATING, 0, rated(r)));
        }
        for (String r : new String[]{"G", "PG", "PG-13", "TV-G", "TV-Y7", "TV-PG", "TV-14"}) {
            assertTrue(r, test(PickRules.RATING, 1, rated(r)));
        }
        for (String r : new String[]{"R", "TV-MA", "NR"}) assertFalse(r, test(PickRules.RATING, 1, rated(r)));
        assertTrue(test(PickRules.RATING, 2, rated("R")));
        assertTrue(test(PickRules.RATING, 2, rated("TV-MA")));
        assertFalse(test(PickRules.RATING, 2, rated("NR")));
        // From TMDB: NC-17 is above R, and a title without a US rating counts as not rated.
        assertFalse(test(PickRules.RATING, 2, rated("NC-17")));
        assertFalse(test(PickRules.RATING, 1, rated("")));
        assertFalse(test(PickRules.RATING, 2, rated("")));
        assertTrue(test(PickRules.RATING, 0, rated("")));
    }

    @Test
    public void aShowsLengthFallsBackToTmdbsEpisodeLength() {
        Title.Draft d = new Title.Draft(show("tmdb"));
        d.episodeRuntime = 95;
        d.seasons = Collections.singletonList(new Season(1, 8, Collections.emptyList()));
        Title t = d.build();
        assertEquals(95, PickRules.episodeLength(t));
        assertFalse(test(PickRules.LENGTH, 2, t));
        assertTrue(test(PickRules.LENGTH, 1, t));
    }

    @Test
    public void titlesStillLoadingAreNotPicked() {
        Title arrival = catalog.get("arrival");
        Title.Draft d = new Title.Draft(arrival);
        d.id = "loading";
        d.detailed = false;
        PickRules.Household both = household(new java.util.HashSet<>(Arrays.asList("arrival", "loading")),
                Collections.emptySet());
        List<Title> m = PickRules.matches(open(Kind.MOVIE), Arrays.asList(arrival, d.build()), both, SUBS);
        assertEquals(Collections.singletonList("arrival"), ids(m));
    }

    @Test
    public void watchState() {
        Title t = movie("seen-one", 100);
        PickRules.Household seen = household(Collections.emptySet(), Collections.singleton("seen-one"));
        assertFalse(PickRules.test(PickRules.WATCH, 0, t, seen, SUBS));
        assertTrue(PickRules.test(PickRules.WATCH, 1, t, seen, SUBS));
        assertTrue(PickRules.test(PickRules.WATCH, 0, t, EMPTY, SUBS));
        // The seed has seen Knives Out: it never comes up while "Unwatched only" is chosen.
        PickRules.State s = open(Kind.MOVIE);
        s.lists.add(ListId.FRIENDS);
        assertFalse(ids(matches(s)).contains("knives-out"));
        s.step(PickRules.WATCH);
        assertTrue(ids(matches(s)).contains("knives-out"));
        assertEquals("Movies · Seen or not · Included + paid", PickRules.filterText(s));
        assertEquals("Movies from My list + Household + Friends.", PickRules.summaryLead(s, 7));
    }

    @Test
    public void availability() {
        Title rentOnly = movie("rent", 100, "PG", 8, Collections.singletonList("Drama"),
                Collections.singletonList(new Offer(ServiceId.APPLETV, OfferType.RENT, 3.99, null)), ListId.HOUSEHOLD);
        Title sub = movie("sub", 100, "PG", 8, Collections.singletonList("Drama"),
                Collections.singletonList(new Offer(ServiceId.NETFLIX, OfferType.SUB, 0, null)), ListId.HOUSEHOLD);
        Title free = movie("free", 100, "PG", 8, Collections.singletonList("Drama"),
                Collections.singletonList(new Offer(ServiceId.YOUTUBE, OfferType.FREE, 0, null)), ListId.HOUSEHOLD);
        assertTrue(test(PickRules.AVAIL, 0, rentOnly));
        assertFalse(test(PickRules.AVAIL, 1, rentOnly));
        assertTrue(test(PickRules.AVAIL, 1, sub));
        assertFalse(PickRules.test(PickRules.AVAIL, 1, sub, EMPTY, NONE));
        assertTrue(PickRules.test(PickRules.AVAIL, 1, free, EMPTY, NONE));
        // "Included + paid" needs an included, rent or buy offer: a SUB on a service the TV doesn't have is neither.
        Title otherSub = movie("other", 100, "PG", 8, Collections.singletonList("Drama"),
                Collections.singletonList(new Offer(ServiceId.APPLETVPLUS, OfferType.SUB, 0, null)), ListId.HOUSEHOLD);
        Set<ServiceId> netflixOnly = EnumSet.of(ServiceId.NETFLIX);
        assertTrue(PickRules.test(PickRules.AVAIL, 0, otherSub, EMPTY, SUBS));
        assertFalse(PickRules.test(PickRules.AVAIL, 0, otherSub, EMPTY, netflixOnly));
        assertTrue(PickRules.test(PickRules.AVAIL, 0, rentOnly, EMPTY, NONE));
        assertFalse(PickRules.test(PickRules.AVAIL, 0, movie("none", 100), EMPTY, SUBS));
        // CODA is only on Apple TV+: on a Netflix-only TV it is no pick, so no pick ends up "Unavailable".
        PickRules.State any = PickRules.defaults(Kind.MOVIE);
        assertTrue(ids(PickRules.matches(any, catalog.all(), household, SUBS)).contains("coda"));
        List<Title> netflix = PickRules.matches(any, catalog.all(), household, netflixOnly);
        assertFalse(ids(netflix).contains("coda"));
        for (Title t : netflix) assertNotNull(t.id, Offers.bestOffer(t, netflixOnly));
        // Moon is rent or buy only: it leaves the default set with "Included only".
        PickRules.State s = PickRules.defaults(Kind.MOVIE);
        s.step(PickRules.AVAIL);
        assertEquals(Arrays.asList("arrival", "past-lives", "coda", "zone", "american-fiction"), ids(matches(s)));
    }

    @Test
    public void stepWrapsRound() {
        PickRules.State s = PickRules.defaults(Kind.MOVIE);
        for (int f = 0; f < PickRules.IDS.length; f++) {
            int start = s.values[f];
            for (int i = 0; i < PickRules.optionCount(f); i++) s.step(f);
            assertEquals(PickRules.IDS[f], start, s.values[f]);
        }
        s.values[PickRules.GENRE] = 4;
        s.step(PickRules.GENRE);
        assertEquals("Any genre", PickRules.option(PickRules.GENRE, s.values[PickRules.GENRE]));
    }

    // ---- Sources and kinds -----------------------------------------------------------------------------------------

    /** My list is the household's (kept on the TV); Household and Friends are the imported lists. */
    @Test
    public void listSources() {
        PickRules.State s = open(Kind.MOVIE);
        s.lists.clear();
        assertTrue(matches(s).isEmpty());
        assertEquals("No lists", PickRules.sourceText(s, 7));
        assertEquals("0", String.valueOf(PickRules.pickCount(0)));
        assertEquals("No matches", PickRules.findLabel(0));
        assertEquals("Nothing matches. Loosen a filter.", PickRules.note(0));

        s.toggle(ListId.FRIENDS);
        for (Title t : matches(s)) assertTrue(t.id, t.isOn(ListId.FRIENDS));
        assertEquals("Friends", PickRules.sourceText(s, 7));

        s.toggle(ListId.FRIENDS);
        s.toggle(ListId.MY);
        PickRules.Household mine = household(new HashSet<>(Arrays.asList("interstellar", "moon")), Collections.emptySet());
        assertEquals(Arrays.asList("moon", "interstellar"), ids(PickRules.matches(s, catalog.all(), mine, SUBS)));

        s.lists.addAll(EnumSet.allOf(ListId.class));
        assertEquals("My list + Household + Friends", PickRules.sourceText(s, 7));
        for (Title t : matches(s)) assertFalse(t.id, t.lists.isEmpty() && !seed.inMyList(t.id));
    }

    /** "My services": everything included on a subscribed service, catalogue-only titles too; the lists don't count. */
    @Test
    public void servicesSource() {
        PickRules.State s = open(Kind.MOVIE);
        s.source = PickRules.Source.SERVICES;
        s.lists.clear();
        List<Title> m = matches(s);
        boolean catalogueOnly = false;
        for (Title t : m) {
            assertTrue(t.id, Offers.isIncluded(t, SUBS));
            assertFalse(t.id, seed.isSeen(t.id));
            catalogueOnly |= t.lists.isEmpty();
        }
        assertTrue(catalogueOnly);
        assertFalse(ids(m).contains("moon"));
        assertEquals("Everything included on your 7 services", PickRules.sourceText(s, 7));
        assertEquals("Everything included on your 1 service", PickRules.sourceText(s, 1));
        assertEquals("2 random matches · Everything included on your 7 services · All 7 services",
                PickRules.resultsContext(2, s, 7));
        // With no services, only free titles are left.
        for (Title t : PickRules.matches(s, catalog.all(), household, NONE)) {
            assertTrue(t.id, Offers.isIncluded(t, NONE));
        }
        // The default fields on the services: the shot's six less Moon (rent or buy only).
        PickRules.State d = PickRules.defaults(Kind.MOVIE);
        d.source = PickRules.Source.SERVICES;
        assertEquals(Arrays.asList("arrival", "past-lives", "coda", "zone", "american-fiction"), ids(matches(d)));
    }

    @Test
    public void onlyTheChosenKind() {
        PickRules.State s = open(Kind.SHOW);
        s.lists.addAll(EnumSet.allOf(ListId.class));
        for (Title t : matches(s)) assertEquals(t.id, Kind.SHOW, t.kind);
        s.kind = Kind.MOVIE;
        for (Title t : matches(s)) assertEquals(t.id, Kind.MOVIE, t.kind);
    }

    @Test
    public void copyIsIndependent() {
        PickRules.State s = PickRules.defaults(Kind.MOVIE);
        PickRules.State c = s.copy();
        s.step(PickRules.SCORE);
        s.toggle(ListId.FRIENDS);
        s.kind = Kind.SHOW;
        assertEquals(1, c.values[PickRules.SCORE]);
        assertFalse(c.lists.contains(ListId.FRIENDS));
        assertEquals(Kind.MOVIE, c.kind);
    }

    // ---- Counts and texts ------------------------------------------------------------------------------------------

    @Test
    public void counts() {
        assertEquals(0, PickRules.pickCount(0));
        assertEquals(1, PickRules.pickCount(1));
        assertEquals(3, PickRules.pickCount(3));
        assertEquals(4, PickRules.pickCount(4));
        assertEquals(4, PickRules.pickCount(40));
        assertEquals("pick, just for tonight", PickRules.countText(1));
        assertEquals("picks, just for tonight", PickRules.countText(0));
        assertEquals("Find 1 pick", PickRules.findLabel(1));
        assertEquals("1 title matches. Reroll on the next screen, or adjust filters.", PickRules.note(1));
        assertEquals("Reroll 1 pick", PickRules.rerollLabel(1));
        assertEquals("1 random match · Friends · Your 1 service", PickRules.resultsContext(1, friends(), 1));
    }

    private static PickRules.State friends() {
        PickRules.State s = PickRules.defaults(Kind.MOVIE);
        s.lists.clear();
        s.lists.add(ListId.FRIENDS);
        return s;
    }

    /** pick-02-results.png's tiles and bar: Max Included, Apple TV $3.99 rent, "Open in Max". */
    @Test
    public void offerLines() {
        ServiceTable table = new ServiceTable();
        Title zone = catalog.get("zone");
        Offer max = Offers.bestOffer(zone, SUBS);
        assertEquals("Included", PickRules.tileAvailability(max, SUBS));
        assertEquals("Max · Included · Open the title in your service", PickRules.barNote(max, SUBS, table));
        assertEquals("Open in Max", PickRules.openLabel(max, SUBS, table));
        Offer moon = Offers.bestOffer(catalog.get("moon"), SUBS);
        assertEquals("$3.99 rent", PickRules.tileAvailability(moon, SUBS));
        assertEquals("Rent on Apple TV · $3.99", PickRules.openLabel(moon, SUBS, table));
        assertEquals("Apple TV · Rent $3.99 · Open the title in your service", PickRules.barNote(moon, SUBS, table));
        Offer buy = new Offer(ServiceId.GOOGLETV, OfferType.BUY, 9.99, null);
        assertEquals("$9.99 buy", PickRules.tileAvailability(buy, SUBS));
        assertEquals("Buy on Google TV · $9.99", PickRules.openLabel(buy, SUBS, table));
        Offer free = new Offer(ServiceId.YOUTUBE, OfferType.FREE, 0, null);
        assertEquals("Included", PickRules.tileAvailability(free, NONE));
        assertEquals("Open in YouTube", PickRules.openLabel(free, NONE, table));
        assertEquals("", PickRules.tileAvailability(null, SUBS));
        assertEquals("Unavailable", PickRules.openLabel(null, SUBS, table));
        assertEquals("Not on your services", PickRules.barNote(null, SUBS, table));
    }

    // ---- Randomness ------------------------------------------------------------------------------------------------

    @Test
    public void shuffleIsAPermutationAndRepeatable() {
        List<Integer> in = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
        List<Integer> a = PickRules.shuffle(in, new Random(42));
        assertEquals(new HashSet<>(in), new HashSet<>(a));
        assertEquals(in.size(), a.size());
        assertEquals(a, PickRules.shuffle(in, new Random(42)));
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8), in);
    }

    @Test
    public void rollIsDeterministicWithASeed() {
        List<Title> m = matches(PickRules.defaults(Kind.MOVIE));
        List<Title> a = PickRules.roll(m, null, new Random(7));
        List<Title> b = PickRules.roll(m, null, new Random(7));
        assertEquals(4, a.size());
        assertEquals(a, b);
        assertEquals(4, new HashSet<>(a).size());
        assertTrue(m.containsAll(a));
    }

    @Test
    public void fewerThanFourShowsWhatThereIs() {
        List<Title> m = matches(PickRules.defaults(Kind.MOVIE)).subList(0, 3);
        List<Title> a = PickRules.roll(m, null, new Random(1));
        assertEquals(new HashSet<>(m), new HashSet<>(a));
        assertEquals(new HashSet<>(m), new HashSet<>(PickRules.roll(m, a, new Random(2))));
        assertTrue(PickRules.roll(Collections.emptyList(), a, new Random(3)).isEmpty());
    }

    /** With more than four matches, Reroll never shows the same four again, whatever the random draw. */
    @Test
    public void rerollNeverRepeatsTheSameFour() {
        List<Title> m = matches(PickRules.defaults(Kind.MOVIE));
        assertEquals(6, m.size());
        for (long seed = 0; seed < 500; seed++) {
            Random rnd = new Random(seed);
            List<Title> first = PickRules.roll(m, null, rnd);
            List<Title> next = PickRules.roll(m, first, rnd);
            assertEquals(4, next.size());
            assertEquals(4, new HashSet<>(next).size());
            assertTrue(m.containsAll(next));
            assertNotEquals("seed " + seed, new HashSet<>(first), new HashSet<>(next));
        }
    }

    /** With exactly four, Reroll can only reorder them. */
    @Test
    public void rerollWithExactlyFour() {
        List<Title> m = matches(PickRules.defaults(Kind.MOVIE)).subList(0, 4);
        List<Title> first = PickRules.roll(m, null, new Random(5));
        assertEquals(new HashSet<>(m), new HashSet<>(PickRules.roll(m, first, new Random(6))));
    }
}
