package com.gathin.tvhome;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The title preview's rules against design_preview/src/screens/preview.ts and core/fuzzy.ts. The expected values in
 * src/test/resources/prototype_preview.tsv and prototype_people.tsv are the prototype's own outputs for every title
 * and person in the mock catalogue, with every service the prototype marks subscribed: the Watch label, the
 * Overview's availability line, the similar titles, the Where to watch cards and each episode's state. They were
 * made the way PrototypeTitles describes, with the prototype's preview.ts logic copied into the throwaway script
 * (it builds DOM, so it can't be imported); regenerate them the same way if the prototype changes.
 */
public class PreviewRulesTest {

    private static final Set<ServiceId> NONE = Collections.emptySet();

    private final MockCatalog catalog = new MockCatalog();
    private final ServiceTable table = new ServiceTable();

    private Title t(String id) {
        Title t = catalog.get(id);
        assertNotNull(id, t);
        return t;
    }

    /** A subscription the household doesn't have, with nothing to rent or buy: the card says so, and OK opens the
     *  app (Router.watch with the subscription offer) instead of an empty rent/buy dialog. */
    @Test
    public void unsubscribedServiceWithNoStoreOffersSaysSubscription() {
        Title mitchells = t("mitchells");
        ServiceOffers netflix = PreviewRules.forService(mitchells, ServiceId.NETFLIX, NONE);
        assertNotNull(netflix);
        org.junit.Assert.assertFalse(netflix.watchable());
        org.junit.Assert.assertEquals("Subscription", PreviewRules.serviceOffer(netflix));
        Offer sub = PreviewRules.subscriptionOffer(mitchells, ServiceId.NETFLIX);
        assertNotNull(sub);
        org.junit.Assert.assertEquals(OfferType.SUB, sub.type);
        org.junit.Assert.assertNull(PreviewRules.subscriptionOffer(mitchells, ServiceId.PRIME));
    }

    private static List<String[]> rows(String resource) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = PreviewRulesTest.class.getResourceAsStream(resource);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                out.add(line.split("\t", -1));
            }
        }
        return out;
    }

    private static List<String> ids(List<Title> titles) {
        List<String> out = new ArrayList<>();
        for (Title t : titles) out.add(t.id);
        return out;
    }

    // ---- Against the prototype, every title -----------------------------------------------------------------------

    @Test
    public void everyTitleMatchesThePrototype() throws Exception {
        List<String[]> rows = rows("/prototype_preview.tsv");
        assertEquals(catalog.all().size(), rows.size());
        for (String[] f : rows) {
            Title t = t(f[0]);
            Offer o = Offers.bestOffer(t, OffersTest.PROTOTYPE);
            assertEquals(t.id + " watch", f[1], PreviewRules.watchLabel(t, o, OffersTest.PROTOTYPE, table));
            assertEquals(t.id + " overview", f[2], PreviewRules.availability(t, OffersTest.PROTOTYPE, table).toString());
            List<String> similar = f[3].isEmpty() ? Collections.emptyList() : Arrays.asList(f[3].split(","));
            assertEquals(t.id + " similar", similar, ids(PreviewRules.similar(t, catalog.all())));
            assertEquals(t.id + " services", f[4], services(t));
            assertEquals(t.id + " episodes", f[5], episodes(t));
        }
    }

    /** "Prime Video|Included|With your subscription|4K · HDR / Apple TV|-|Rent $3.99 · Buy $9.99|4K · Dolby Vision". */
    private String services(Title t) {
        List<String> cards = new ArrayList<>();
        for (ServiceOffers s : Offers.byService(t, OffersTest.PROTOTYPE)) {
            String badge = PreviewRules.serviceBadge(s);
            cards.add(table.name(s.service) + "|" + (badge == null ? "-" : badge) + "|" + PreviewRules.serviceOffer(s)
                    + "|" + s.quality);
        }
        return String.join(" / ", cards);
    }

    /** "1.1:w:48m · Watched 1.3:n*:53m 1.4:-:51m": watched, up next or neither, * for the row's default. */
    private static String episodes(Title t) {
        List<String> out = new ArrayList<>();
        for (Season s : t.seasons) {
            for (Episode e : s.episodes) {
                boolean watched = PreviewRules.watched(t, s.n, e.n);
                boolean next = PreviewRules.upNext(t, s.n, e.n);
                out.add(s.n + "." + e.n + ":" + (next ? "n" : watched ? "w" : "-")
                        + (PreviewRules.isDefaultEpisode(t, s.n, e.n) ? "*" : "") + ":" + PreviewRules.episodeMeta(e, watched));
            }
        }
        return String.join(" ", out);
    }

    @Test
    public void everyPersonMatchesThePrototype() throws Exception {
        List<String[]> rows = rows("/prototype_people.tsv");
        assertTrue(rows.size() > 100);
        for (String[] f : rows) {
            assertEquals(f[0], Arrays.asList(f[1].split(",")), ids(PreviewRules.titlesFor(f[0], catalog.all())));
        }
        assertTrue(PreviewRules.titlesFor("Nobody At All", catalog.all()).isEmpty());
    }

    // ---- The Watch button and its offer ---------------------------------------------------------------------------

    @Test
    public void watchLabels() {
        assertEquals("Watch on Prime Video", label("arrival", OffersTest.PROTOTYPE));
        assertEquals("Watch S1 E3 on Apple TV+", label("severance", OffersTest.PROTOTYPE));
        assertEquals("Rent on Apple TV · $3.99", label("moon", OffersTest.PROTOTYPE));
        // Without Prime, Arrival's Watch is the cheapest rental (Apple TV, the first of the equal $3.99 ones).
        assertEquals("Rent on Apple TV · $3.99", label("arrival", NONE));
        // Severance is only on Apple TV+: with no subscriptions there is nothing to watch it with.
        assertEquals("Not on your services", label("severance", NONE));
    }

    private String label(String id, Set<ServiceId> subscribed) {
        Title t = t(id);
        return PreviewRules.watchLabel(t, Offers.bestOffer(t, subscribed), subscribed, table);
    }

    @Test
    public void watchUsesIncludedThenCheapestRentThenCheapestBuy() {
        Title arrival = t("arrival");
        Offer included = Offers.bestOffer(arrival, OffersTest.PROTOTYPE);
        assertSame(ServiceId.PRIME, included.service);
        assertSame(OfferType.SUB, included.type);
        Offer rent = Offers.bestOffer(arrival, NONE);
        assertSame(OfferType.RENT, rent.type);
        assertSame(ServiceId.APPLETV, rent.service);

        Title buyOnly = title(Arrays.asList(new Offer(ServiceId.GOOGLETV, OfferType.BUY, 14.99, "4K"),
                new Offer(ServiceId.APPLETV, OfferType.BUY, 12.99, "HD")), 0, 0);
        Offer buy = Offers.bestOffer(buyOnly, NONE);
        assertSame(ServiceId.APPLETV, buy.service);
        assertEquals("Buy on Apple TV · $12.99", PreviewRules.watchLabel(buyOnly, buy, NONE, table));
    }

    @Test
    public void freeOfferIsWatchedNotRented() {
        Title free = title(Arrays.asList(new Offer(ServiceId.APPLETV, OfferType.RENT, 2.99, "HD"),
                new Offer(ServiceId.YOUTUBE, OfferType.FREE, 0, "HD")), 0, 0);
        Offer o = Offers.bestOffer(free, NONE);
        assertSame(OfferType.FREE, o.type);
        assertEquals("Watch on YouTube", PreviewRules.watchLabel(free, o, NONE, table));
        PreviewRules.Availability a = PreviewRules.availability(free, NONE, table);
        assertEquals("Free", a.badge);
        assertEquals("Free on YouTube · Rent from $2.99 elsewhere", a.toString());
    }

    @Test
    public void showWithoutNextEpisodeSaysWatchOn() {
        Title show = title(Collections.singletonList(new Offer(ServiceId.NETFLIX, OfferType.SUB, 0, null)), 0, 0);
        assertEquals("Watch on Netflix", PreviewRules.watchLabel(show, show.offers.get(0), OffersTest.PROTOTYPE, table));
        assertEquals("", PreviewRules.next(show));
    }

    // ---- Overview -------------------------------------------------------------------------------------------------

    @Test
    public void overviewAvailability() {
        PreviewRules.Availability a = PreviewRules.availability(t("arrival"), OffersTest.PROTOTYPE, table);
        assertEquals("Included", a.badge);
        assertEquals("on Prime Video", a.text);
        assertEquals("· Rent from $3.99 elsewhere", a.elsewhere);
        assertEquals("Included on Prime Video · Rent from $3.99 elsewhere", a.toString());

        PreviewRules.Availability moon = PreviewRules.availability(t("moon"), OffersTest.PROTOTYPE, table);
        assertNull(moon.badge);
        assertNull(moon.elsewhere);
        assertEquals("Rent on Apple TV", moon.toString());

        assertEquals("Included on Apple TV+", PreviewRules.availability(t("severance"), OffersTest.PROTOTYPE, table).toString());
        assertEquals("Not on your services", PreviewRules.availability(t("severance"), NONE, table).toString());
    }

    @Test
    public void starringAndLead() {
        Title arrival = t("arrival");
        assertEquals("Amy Adams, Jeremy Renner, Forest Whitaker", PreviewRules.starring(arrival));
        assertEquals("Director", PreviewRules.leadRole(arrival));
        assertEquals("Denis Villeneuve", PreviewRules.leadNames(arrival));
        Title bare = title(Collections.emptyList(), 0, 0);
        assertNull(PreviewRules.starring(bare));
        assertNull(PreviewRules.leadRole(bare));
        assertNull(PreviewRules.leadNames(bare));
    }

    // ---- Where to watch -------------------------------------------------------------------------------------------

    @Test
    public void whereToWatchOffers() {
        Title arrival = t("arrival");
        ServiceOffers apple = PreviewRules.forService(arrival, ServiceId.APPLETV, OffersTest.PROTOTYPE);
        assertNotNull(apple);
        assertFalse(apple.watchable());
        assertNull(PreviewRules.serviceBadge(apple));
        assertEquals("Rent $3.99 · Buy $9.99", PreviewRules.serviceOffer(apple));
        assertNull(PreviewRules.forService(arrival, ServiceId.NETFLIX, OffersTest.PROTOTYPE));

        Offer prime = PreviewRules.includedOffer(arrival, ServiceId.PRIME, OffersTest.PROTOTYPE);
        assertNotNull(prime);
        assertSame(OfferType.SUB, prime.type);
        assertNull(PreviewRules.includedOffer(arrival, ServiceId.PRIME, NONE));
        assertNull(PreviewRules.includedOffer(arrival, ServiceId.APPLETV, OffersTest.PROTOTYPE));
    }

    // ---- More like this -------------------------------------------------------------------------------------------

    @Test
    public void similarTitles() {
        List<Title> similar = PreviewRules.similar(t("arrival"), catalog.all());
        assertEquals(PreviewRules.SIMILAR_MAX, similar.size());
        assertEquals("blade-runner-2049", similar.get(0).id);
        for (Title o : similar) assertFalse(o.id.equals("arrival"));
    }

    @Test
    public void similarNeedsAScoreOfTwo() {
        // A lone genre nobody shares and a kind of its own: a tenth of a 10/10 score is not enough.
        Title odd = new Title("odd", Kind.SHOW, "Odd", 2020, Collections.singletonList("Nothing"), 0, "TV-G", 9,
                "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), 0, 0);
        Title other = new Title("other", Kind.MOVIE, "Other", 2020, Collections.singletonList("Else"), 90, "G", 10,
                "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), 0, 0);
        assertTrue(PreviewRules.similar(odd, Arrays.asList(odd, other)).isEmpty());
    }

    // ---- Episodes -------------------------------------------------------------------------------------------------

    @Test
    public void episodesWatchedAndUpNext() {
        Title severance = t("severance");
        assertEquals(1, PreviewRules.startSeason(severance));
        assertTrue(PreviewRules.watched(severance, 1, 1));
        assertTrue(PreviewRules.watched(severance, 1, 2));
        assertFalse(PreviewRules.watched(severance, 1, 3));
        assertTrue(PreviewRules.upNext(severance, 1, 3));
        assertTrue(PreviewRules.isDefaultEpisode(severance, 1, 3));
        assertFalse(PreviewRules.isDefaultEpisode(severance, 2, 1));
        assertFalse(PreviewRules.upNext(severance, 2, 3));
        assertFalse(PreviewRules.watched(severance, 2, 1));

        // Later in the show: every episode of an earlier season is watched.
        Title later = title(Collections.emptyList(), 2, 4);
        assertEquals(2, PreviewRules.startSeason(later));
        assertTrue(PreviewRules.watched(later, 1, 9));
        assertTrue(PreviewRules.watched(later, 2, 3));
        assertTrue(PreviewRules.upNext(later, 2, 4));
        assertFalse(PreviewRules.watched(later, 3, 1));

        // No next episode: nothing watched, nothing up next, each season's row starts at episode 1.
        Title fresh = title(Collections.emptyList(), 0, 0);
        assertEquals(1, PreviewRules.startSeason(fresh));
        assertFalse(PreviewRules.watched(fresh, 1, 1));
        assertFalse(PreviewRules.upNext(fresh, 1, 1));
        assertTrue(PreviewRules.isDefaultEpisode(fresh, 2, 1));
        assertFalse(PreviewRules.isDefaultEpisode(fresh, 2, 2));
    }

    @Test
    public void episodeMeta() {
        Episode e = new Episode(1, "Pilot", 48);
        assertEquals("48m · Watched", PreviewRules.episodeMeta(e, true));
        assertEquals("48m", PreviewRules.episodeMeta(e, false));
        assertEquals("1h 2m", PreviewRules.episodeMeta(new Episode(2, "Long", 62), false));
    }

    /** A show with these offers and next episode, two seasons of nine. */
    private static Title title(List<Offer> offers, int nextSeason, int nextEpisode) {
        List<Season> seasons = new ArrayList<>();
        for (int s = 1; s <= 2; s++) {
            List<Episode> eps = new ArrayList<>();
            for (int e = 1; e <= 9; e++) eps.add(new Episode(e, "Episode " + e, 50));
            seasons.add(new Season(s, eps));
        }
        return new Title("test", Kind.SHOW, "Test", 2024, Collections.singletonList("Drama"), 0, "TV-14", 8.0, "", "",
                offers, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), seasons,
                nextSeason, nextEpisode);
    }
}
