package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * What the title preview says and picks, ported from design_preview/src/screens/preview.ts and person.ts (with
 * core/fuzzy.ts, titlesForPerson): the Watch button's label, the Overview's availability line, the Where to watch
 * cards' text, the similar titles, the episodes' watched and "Up next" state, and the titles a person appears in.
 * Pure Java and English only, like Offers and TitleText, so it is unit-tested against the prototype's outputs.
 */
final class PreviewRules {
    private PreviewRules() {}

    /** How many titles More like this shows. */
    static final int SIMILAR_MAX = 8;

    /**
     * The Watch button: "Watch on Prime Video" for an included movie, "Watch S1 E3 on Apple TV+" for an included
     * show with a next episode, "Rent on Apple TV · $3.99" or "Buy on … · $9.99" for a paid offer, and "Not on your
     * services" with none. {@code o} is the title's best offer ({@link Offers#bestOffer}).
     */
    static String watchLabel(Title t, Offer o, Set<ServiceId> subscribed, ServiceTable table) {
        if (o == null) return "Not on your services";
        String name = table.name(o.service);
        if (Offers.isIncludedOffer(o, subscribed)) {
            // The prototype would print "Watch  on" for a show with no next episode; every mock show has one.
            if (!t.isMovie() && t.hasNext()) return "Watch " + next(t) + " on " + name;
            return "Watch on " + name;
        }
        return paidVerb(o) + " on " + name + " · " + Offers.price(o.price);
    }

    /** "S1 E3", the show's next episode; "" when there is none. */
    static String next(Title t) {
        return t.hasNext() ? "S" + t.nextSeason + " E" + t.nextEpisode : "";
    }

    private static String paidVerb(Offer o) {
        return o.type == OfferType.RENT ? "Rent" : "Buy";
    }

    /**
     * The Overview's first fact: the green badge ("Included", "Free" or none), then "on Prime Video", "Rent on Apple
     * TV" or "Not on your services", then for an included title with a rental anywhere "· Rent from $3.99
     * elsewhere" (null otherwise), as preview.ts builds it.
     */
    static final class Availability {
        final String badge;
        final String text;
        final String elsewhere;

        Availability(String badge, String text, String elsewhere) {
            this.badge = badge;
            this.text = text;
            this.elsewhere = elsewhere;
        }

        /** "Included on Prime Video · Rent from $3.99 elsewhere": the line as it reads. */
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            if (badge != null) b.append(badge).append(' ');
            b.append(text);
            if (elsewhere != null) b.append(' ').append(elsewhere);
            return b.toString();
        }
    }

    static Availability availability(Title t, Set<ServiceId> subscribed, ServiceTable table) {
        Offer o = Offers.bestOffer(t, subscribed);
        if (o == null) return new Availability(null, "Not on your services", null);
        String name = table.name(o.service);
        if (!Offers.isIncludedOffer(o, subscribed)) return new Availability(null, paidVerb(o) + " on " + name, null);
        Offer rent = Offers.cheapest(t, OfferType.RENT);
        return new Availability(o.type == OfferType.FREE ? "Free" : "Included", "on " + name,
                rent == null ? null : "· Rent from " + Offers.price(rent.price) + " elsewhere");
    }

    /** The first three of the cast, "Amy Adams, Jeremy Renner, Forest Whitaker"; null with no cast. */
    static String starring(Title t) {
        if (t.cast.isEmpty()) return null;
        List<String> names = new ArrayList<>(3);
        for (Person p : t.cast) {
            if (names.size() == 3) break;
            names.add(p.name);
        }
        return String.join(", ", names);
    }

    /** The first crew member's role, "Director" or "Creator"; null with no crew. */
    static String leadRole(Title t) {
        return t.crew.isEmpty() ? null : t.crew.get(0).role;
    }

    /** Everyone in the crew with the lead's role: "Dan Erickson" or "Joel Coen, Ethan Coen". */
    static String leadNames(Title t) {
        String role = leadRole(t);
        if (role == null) return null;
        List<String> names = new ArrayList<>();
        for (Person p : t.crew) if (role.equals(p.role)) names.add(p.name);
        return String.join(", ", names);
    }

    // ---- Where to watch -------------------------------------------------------------------------------------------

    /** The card's badge: "Free", "Included" or null for a store. */
    static String serviceBadge(ServiceOffers s) {
        if (s.free) return "Free";
        return s.included ? "Included" : null;
    }

    /**
     * The card's offer line: "With your subscription", "Free with ads", "Rent $3.99 · Buy $9.99", or "Subscription"
     * for a service the household doesn't subscribe to that has nothing to rent or buy (the prototype assumes every
     * service is subscribed, so it never shows this).
     */
    static String serviceOffer(ServiceOffers s) {
        if (s.included) return "With your subscription";
        if (s.free) return "Free with ads";
        List<String> parts = new ArrayList<>(2);
        if (s.rent != null) parts.add("Rent " + Offers.price(s.rent.price));
        if (s.buy != null) parts.add("Buy " + Offers.price(s.buy.price));
        return parts.isEmpty() ? "Subscription" : String.join(" · ", parts);
    }

    /** The title's subscription offer on that service, or null. */
    static Offer subscriptionOffer(Title t, ServiceId service) {
        for (Offer o : t.offers) if (o.service == service && o.type == OfferType.SUB) return o;
        return null;
    }

    /** That service's merged offers for the title, or null. */
    static ServiceOffers forService(Title t, ServiceId service, Set<ServiceId> subscribed) {
        for (ServiceOffers s : Offers.byService(t, subscribed)) if (s.service == service) return s;
        return null;
    }

    /** The offer OK on an included or free service opens: its first included one on that service, or null. */
    static Offer includedOffer(Title t, ServiceId service, Set<ServiceId> subscribed) {
        for (Offer o : t.offers) if (o.service == service && Offers.isIncludedOffer(o, subscribed)) return o;
        return null;
    }

    // ---- More like this and the person page -----------------------------------------------------------------------

    /**
     * Up to eight titles like t, as preview.ts picks them: every other title scored by 2 per shared genre, 1 for
     * the same kind and a tenth of its review score; those scoring 2 or more, highest first, ties in catalogue order.
     */
    static List<Title> similar(Title t, List<Title> all) {
        List<Title> titles = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (Title o : all) {
            if (o.id.equals(t.id)) continue;
            int shared = 0;
            for (String g : o.genres) if (t.genres.contains(g)) shared++;
            double s = shared * 2 + (o.kind == t.kind ? 1 : 0) + o.score / 10;
            if (s < 2) continue;
            titles.add(o);
            scores.add(s);
        }
        List<Integer> order = new ArrayList<>(titles.size());
        for (int i = 0; i < titles.size(); i++) order.add(i);
        // List.sort is stable, as Array.prototype.sort is, so equal scores keep the catalogue's order.
        order.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        List<Title> out = new ArrayList<>(SIMILAR_MAX);
        for (int i : order) {
            if (out.size() == SIMILAR_MAX) break;
            out.add(titles.get(i));
        }
        return Collections.unmodifiableList(out);
    }

    /** The titles someone is in the cast or crew of, in catalogue order (core/fuzzy.ts, titlesForPerson). */
    static List<Title> titlesFor(String name, List<Title> all) {
        List<Title> out = new ArrayList<>();
        for (Title t : all) {
            if (has(t.cast, name) || has(t.crew, name)) out.add(t);
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean has(List<Person> people, String name) {
        for (Person p : people) if (p.name.equals(name)) return true;
        return false;
    }

    // ---- Episodes -------------------------------------------------------------------------------------------------

    /** The season the Episodes tab opens on: the next episode's, else 1 (preview.ts). */
    static int startSeason(Title t) {
        return t.hasNext() ? t.nextSeason : 1;
    }

    /** An episode before the next one: watched, with a tick. */
    static boolean watched(Title t, int season, int episode) {
        return t.hasNext() && (season < t.nextSeason || (season == t.nextSeason && episode < t.nextEpisode));
    }

    /** The next episode, with the "Up next" badge and the accent border. */
    static boolean upNext(Title t, int season, int episode) {
        return t.hasNext() && season == t.nextSeason && episode == t.nextEpisode;
    }

    /** The episode a season's row picks first: the next one, or episode 1 when the show has none. */
    static boolean isDefaultEpisode(Title t, int season, int episode) {
        return upNext(t, season, episode) || (!t.hasNext() && episode == 1);
    }

    /** "48m · Watched", or "53m". */
    static String episodeMeta(Episode e, boolean watched) {
        return Offers.runtimeLabel(e.runtime) + (watched ? " · Watched" : "");
    }
}
