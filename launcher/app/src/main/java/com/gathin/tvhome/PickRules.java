package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pick for me's matching rules, ported from design_preview/src/screens/pick.ts (FIELDS, matches, shuffle, sourceText,
 * filterText and the setup card's lines). Pure Java and English only, like {@link Offers}, so it is unit-tested on
 * the JVM; the household and the subscribed services are passed in. Kept apart from the filters drawer's rules
 * (filterDefs.ts): the two screens use different vocabularies.
 */
final class PickRules {
    private PickRules() {}

    /** Where the picks come from: the imported watchlists, or everything included on the household's services. */
    enum Source { LISTS, SERVICES }

    /** What the rules need to know about the household: My list and what has been seen. */
    interface Household {
        boolean inMyList(String id);

        boolean isSeen(String id);
    }

    /** The most picks a roll shows. */
    static final int PICKS = 4;

    // The six fields, in the order the setup page lays them out.
    static final int LENGTH = 0, GENRE = 1, SCORE = 2, RATING = 3, WATCH = 4, AVAIL = 5;
    static final String[] IDS = {"length", "genre", "score", "rating", "watch", "avail"};

    private static final String[][] OPTIONS = {
            {"Any length", "Under 2 hours", "Under 90 minutes"},
            {"Any genre", "Drama + Sci-fi", "Comedy + Animation", "Mystery + Thriller", "Documentary"},
            {"Any score", "7.0 and above", "8.0 and above"},
            {"Any rating", "Up to PG-13", "Up to R"},
            {"Unwatched only", "Seen or not"},
            {"Included + paid", "Included only"},
    };
    private static final int[] MINUTES = {0, 120, 90};
    private static final String[][] GENRES = {{}, {"Drama", "Sci-fi"}, {"Comedy", "Animation"}, {"Mystery", "Thriller"},
            {"Documentary"}};
    private static final double[] SCORES = {0, 7, 8};
    private static final List<String> ABOVE_PG13 = Arrays.asList("R", "TV-MA", "NR");

    /** The lists the setup page offers, in its order. */
    static final ListId[] LISTS = {ListId.MY, ListId.HOUSEHOLD, ListId.FRIENDS};

    /** The setup page's choices. Mutable: the page changes it as OK is pressed. */
    static final class State {
        Kind kind;
        Source source = Source.LISTS;
        final Set<ListId> lists = EnumSet.noneOf(ListId.class);
        final int[] values = new int[IDS.length];

        State(Kind kind) {
            this.kind = kind;
        }

        State copy() {
            State s = new State(kind);
            s.source = source;
            s.lists.addAll(lists);
            System.arraycopy(values, 0, s.values, 0, values.length);
            return s;
        }

        /** OK on a field: its next option, wrapping round to the first. */
        void step(int field) {
            values[field] = (values[field] + 1) % OPTIONS[field].length;
        }

        void toggle(ListId list) {
            if (!lists.remove(list)) lists.add(list);
        }
    }

    /** The prototype's starting point: My list + Household, under 2 hours, Drama + Sci-fi, 7.0 and above, up to R. */
    static State defaults(Kind kind) {
        State s = new State(kind);
        s.lists.add(ListId.MY);
        s.lists.add(ListId.HOUSEHOLD);
        int[] start = {1, 1, 1, 2, 0, 0};
        System.arraycopy(start, 0, s.values, 0, start.length);
        return s;
    }

    /** "Movie length" or "Episode length", "Genre · Match either" and so on. */
    static String label(int field, Kind kind) {
        switch (field) {
            case LENGTH:
                return kind == Kind.MOVIE ? "Movie length" : "Episode length";
            case GENRE:
                return "Genre · Match either";
            case SCORE:
                return "Review score · Out of 10";
            case RATING:
                return "Age / content rating";
            case WATCH:
                return "Watch state";
            default:
                return "Availability";
        }
    }

    static int optionCount(int field) {
        return OPTIONS[field].length;
    }

    static String option(int field, int i) {
        return OPTIONS[field][i];
    }

    /**
     * Whether t passes option i of a field. A show's length is its episodes' average length (the field reads
     * "Episode length" for shows); a show with no episodes listed passes, as every show does in the prototype.
     */
    static boolean test(int field, int i, Title t, Household h, Set<ServiceId> subscribed) {
        switch (field) {
            case LENGTH:
                if (i == 0) return true;
                if (t.isMovie()) return t.runtime < MINUTES[i];
                int episodes = episodeLength(t);
                return episodes == 0 || episodes < MINUTES[i];
            case GENRE:
                if (i == 0) return true;
                for (String g : GENRES[i]) if (t.genres.contains(g)) return true;
                return false;
            case SCORE:
                return t.score >= SCORES[i];
            case RATING:
                if (i == 0) return true;
                return i == 1 ? !ABOVE_PG13.contains(t.rating) : !"NR".equals(t.rating);
            case WATCH:
                return i == 1 || !h.isSeen(t.id);
            default:
                // "Included + paid" still needs a way to watch: in the prototype every SUB service is subscribed, so
                // no title lacks one; here a SUB offer on a service the TV doesn't have is neither.
                return i == 0 ? Offers.bestOffer(t, subscribed) != null : Offers.isIncluded(t, subscribed);
        }
    }

    /** A show's average episode length in minutes, rounded; 0 for a movie or a show without episodes. */
    static int episodeLength(Title t) {
        int sum = 0, n = 0;
        for (Season s : t.seasons) {
            for (Episode e : s.episodes) {
                sum += e.runtime;
                n++;
            }
        }
        return n == 0 ? 0 : Math.round(sum / (float) n);
    }

    /**
     * The titles the choices match, in catalogue order: of the chosen kind; on one of the chosen lists (My list
     * is the household's, kept on the TV), or with "My services" included on a subscribed service, catalogue-only
     * titles too; and passing every field.
     */
    static List<Title> matches(State s, List<Title> all, Household h, Set<ServiceId> subscribed) {
        List<Title> out = new ArrayList<>();
        for (Title t : all) {
            if (t.kind != s.kind) continue;
            if (s.source == Source.LISTS) {
                if (!onAnyList(t, s.lists, h)) continue;
            } else if (!Offers.isIncluded(t, subscribed)) {
                continue;
            }
            boolean pass = true;
            for (int f = 0; f < IDS.length && pass; f++) pass = test(f, s.values[f], t, h, subscribed);
            if (pass) out.add(t);
        }
        return out;
    }

    private static boolean onAnyList(Title t, Set<ListId> lists, Household h) {
        for (ListId l : lists) {
            if (l == ListId.MY ? h.inMyList(t.id) : t.isOn(l)) return true;
        }
        return false;
    }

    /** How many picks a roll shows: up to four. */
    static int pickCount(int matches) {
        return Math.min(PICKS, matches);
    }

    /** The prototype's Fisher-Yates shuffle, on a copy. */
    static <T> List<T> shuffle(List<T> in, Random rnd) {
        List<T> out = new ArrayList<>(in);
        for (int i = out.size() - 1; i > 0; i--) Collections.swap(out, i, rnd.nextInt(i + 1));
        return out;
    }

    /**
     * Up to four random matches. When there are more than four and the roll drew the same four as {@code previous}
     * (in any order), one of them is swapped for a match that wasn't shown, so Reroll always shows something new.
     */
    static List<Title> roll(List<Title> matches, List<Title> previous, Random rnd) {
        List<Title> shuffled = shuffle(matches, rnd);
        List<Title> picks = new ArrayList<>(shuffled.subList(0, pickCount(shuffled.size())));
        if (previous != null && shuffled.size() > PICKS && new HashSet<>(picks).equals(new HashSet<>(previous))) {
            picks.set(rnd.nextInt(PICKS), shuffled.get(PICKS));
        }
        return picks;
    }

    // ---- Lines on the setup card and the results page ------------------------------------------------------------

    /** "Everything included on your 7 services", or the chosen lists joined with " + ", or "No lists". */
    static String sourceText(State s, int subscribedCount) {
        if (s.source == Source.SERVICES) return "Everything included on your " + services(subscribedCount);
        List<String> names = new ArrayList<>();
        for (ListId l : LISTS) if (s.lists.contains(l)) names.add(ListNames.of(l));
        return names.isEmpty() ? "No lists" : String.join(" + ", names);
    }

    /** "1 service", "7 services". */
    static String services(int n) {
        return n + (n == 1 ? " service" : " services");
    }

    /** "Movies · Under 2 hours · Drama + Sci-fi · …": the kind, then every option but the "Any …" ones. */
    static String filterText(State s) {
        StringBuilder b = new StringBuilder(s.kind == Kind.MOVIE ? "Movies" : "TV Shows");
        for (int f = 0; f < IDS.length; f++) {
            String o = OPTIONS[f][s.values[f]];
            if (!o.startsWith("Any")) b.append(" · ").append(o);
        }
        return b.toString();
    }

    /**
     * "Unwatched movies from My list + Household." The prototype always says "Unwatched"; here only when the watch
     * state asks for it, so the lead line never contradicts the filters under it.
     */
    static String summaryLead(State s, int subscribedCount) {
        String kind = s.kind == Kind.MOVIE ? "movies" : "shows";
        String what = s.values[WATCH] == 0 ? "Unwatched " + kind : Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
        return what + " from " + sourceText(s, subscribedCount) + ".";
    }

    /** The words after the big count: "picks, just for tonight", "pick, just for tonight". */
    static String countText(int picks) {
        return picks == 1 ? "pick, just for tonight" : "picks, just for tonight";
    }

    /** The Find button: "Find 4 picks", "Find 1 pick", "No matches". */
    static String findLabel(int picks) {
        if (picks == 0) return "No matches";
        return "Find " + picks + (picks == 1 ? " pick" : " picks");
    }

    /** The line under the Find button. */
    static String note(int matches) {
        if (matches == 0) return "Nothing matches. Loosen a filter.";
        return matches + (matches == 1 ? " title matches" : " titles match") + ". Reroll on the next screen, or adjust filters.";
    }

    /** The results page's context: "4 random matches · My list + Household · All 7 services". */
    static String resultsContext(int picks, State s, int subscribedCount) {
        String matches = picks == 1 ? " random match" : " random matches";
        String all = subscribedCount == 1 ? "Your 1 service" : "All " + subscribedCount + " services";
        return picks + matches + " · " + sourceText(s, subscribedCount) + " · " + all;
    }

    /** "Reroll 4 picks", "Reroll 1 pick". */
    static String rerollLabel(int picks) {
        return "Reroll " + picks + (picks == 1 ? " pick" : " picks");
    }

    /** A pick tile's second provider line: "Included", "$3.99 rent", "$9.99 buy" or "" for none. */
    static String tileAvailability(Offer o, Set<ServiceId> subscribed) {
        if (o == null) return "";
        if (Offers.isIncludedOffer(o, subscribed)) return "Included";
        return Offers.price(o.price) + " " + (o.type == OfferType.RENT ? "rent" : "buy");
    }

    /** The results bar's note: "Max · Included · Open the title in your service", or "Not on your services". */
    static String barNote(Offer o, Set<ServiceId> subscribed, ServiceTable table) {
        if (o == null) return "Not on your services";
        return Offers.offerLabel(o, subscribed, table) + " · Open the title in your service";
    }

    /** The bar's main button: "Open in Max", "Rent on Apple TV · $3.99", "Buy on Apple TV · $9.99", "Unavailable". */
    static String openLabel(Offer o, Set<ServiceId> subscribed, ServiceTable table) {
        if (o == null) return "Unavailable";
        String name = table.name(o.service);
        if (Offers.isIncludedOffer(o, subscribed)) return "Open in " + name;
        return (o.type == OfferType.RENT ? "Rent" : "Buy") + " on " + name + " · " + Offers.price(o.price);
    }
}
