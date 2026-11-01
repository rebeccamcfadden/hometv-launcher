package com.hometv.launcher;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Design 07-prices A (design_preview/README.md, Prices and Search's card line): the Where to watch order and its
 * Cheapest tag, the line per quality, the credit, the rent/buy dialog and the small cards' third line, with the
 * README's examples in kronor.
 */
public class PriceCardsTest {

    private final ServiceTable table = ServiceTable.branded();
    private static final Set<ServiceId> MAX_ONLY = EnumSet.of(ServiceId.MAX);
    private static final Quality SD = Quality.SD, HD = Quality.HD, UHD = Quality.UHD;

    /** A real rent or buy offer: pairs of a quality and whole kronor. */
    private static Offer priced(ServiceId s, OfferType type, boolean seasonal, Object... pairs) {
        Map<Quality, Money> prices = new EnumMap<>(Quality.class);
        for (int i = 0; i < pairs.length; i += 2) prices.put((Quality) pairs[i], kr((Integer) pairs[i + 1]));
        Money min = null;
        Quality q = null;
        for (Map.Entry<Quality, Money> e : prices.entrySet()) {
            if (min == null || e.getValue().compareTo(min) <= 0) {
                min = e.getValue();
                q = e.getKey();
            }
        }
        boolean from = seasonal || prices.values().stream().distinct().count() > 1;
        return new Offer(s, type, min == null ? 0 : min.major(), q == null ? null : q.label, min, from, true,
                prices, seasonal);
    }

    private static Offer rent(ServiceId s, Object... pairs) {
        return priced(s, OfferType.RENT, false, pairs);
    }

    private static Offer buy(ServiceId s, Object... pairs) {
        return priced(s, OfferType.BUY, false, pairs);
    }

    private static Offer sub(ServiceId s) {
        return new Offer(s, OfferType.SUB, 0, null, null, false, true);
    }

    private static Money kr(int n) {
        return new Money(n * 100L, "SEK");
    }

    private static Title title(Kind kind, Offer... offers) {
        Title.Draft d = new Title.Draft(kind == Kind.MOVIE ? "movie:17431" : "tv:136315", kind, "Moon");
        d.year = 2009;
        d.offers = Arrays.asList(offers);
        d.detailed = true;
        return d.build();
    }

    /**
     * The README's Moon, on a household with HBO Max: SF Anytime (HD, cheapest), the Apple TV Store (dearer 4K),
     * Amazon Video (4K to buy only), the Viaplay Store (HD) and Rakuten TV (no known price), in TMDB's order.
     */
    private static Title moon() {
        return title(Kind.MOVIE,
                sub(ServiceId.MAX),
                rent(ServiceId.RAKUTEN), buy(ServiceId.RAKUTEN),
                rent(ServiceId.APPLETV, HD, 49, UHD, 59), buy(ServiceId.APPLETV, HD, 149, UHD, 179),
                rent(ServiceId.AMAZONVIDEO, HD, 49), buy(ServiceId.AMAZONVIDEO, HD, 139, UHD, 169),
                rent(ServiceId.SFANYTIME, HD, 39), buy(ServiceId.SFANYTIME, HD, 129),
                rent(ServiceId.VIAPLAY, HD, 45), buy(ServiceId.VIAPLAY, HD, 139));
    }

    private List<String> describe(List<PriceCards.Card> cards) {
        List<String> out = new ArrayList<>();
        for (PriceCards.Card c : cards) out.add(c.offers.service.id + ": " + c.said());
        return out;
    }

    private static PriceCards.Card card(List<PriceCards.Card> cards, ServiceId id) {
        for (PriceCards.Card c : cards) if (c.offers.service == id) return c;
        throw new AssertionError(id);
    }

    private static ServiceOffers offers(Title t, ServiceId id) {
        for (ServiceOffers s : Offers.byService(t, MAX_ONLY)) if (s.service == id) return s;
        throw new AssertionError(id);
    }

    // ---- Where to watch -------------------------------------------------------------------------------------------

    @Test
    public void includedFirstThenRentalsCheapestFirstThenPurchasesThenUnpriced() {
        List<PriceCards.Card> cards = PriceCards.cards(moon(), MAX_ONLY, null, null);
        assertEquals(Arrays.asList(
                "max: Included; With your subscription",
                "sfanytime: Cheapest; Rent 39 kr · Buy 129 kr   HD",
                "viaplay: Rent 45 kr · Buy 139 kr   HD",
                // Amazon and Apple rent at 49 kr: the offers' order decides.
                "appletv: Rent 49 kr · Buy 149 kr   HD; Rent 59 kr · Buy 179 kr   4K",
                "amazonvideo: Rent 49 kr · Buy 139 kr   HD; Buy 169 kr   4K",
                "rakuten: Rent · Buy; Price shown in the app"), describe(cards));
    }

    @Test
    public void rentalsComeBeforeCheaperPurchases() {
        Title t = title(Kind.MOVIE, buy(ServiceId.APPLETV, HD, 29), rent(ServiceId.SFANYTIME, HD, 99),
                buy(ServiceId.SFANYTIME, HD, 149));
        List<PriceCards.Card> cards = PriceCards.cards(t, MAX_ONLY, null, null);
        assertEquals(ServiceId.SFANYTIME, cards.get(0).offers.service);
        assertTrue(cards.get(0).cheapest);
        assertTrue(cards.get(0).isGreen(0, OfferType.RENT));
        assertFalse(cards.get(0).isGreen(0, OfferType.BUY));
        assertFalse(cards.get(1).cheapest);
    }

    @Test
    public void purchasesAreOrderedByPriceAndTheCheapestPurchaseIsGreen() {
        Title t = title(Kind.MOVIE, buy(ServiceId.APPLETV, HD, 149), buy(ServiceId.AMAZONVIDEO, UHD, 169),
                buy(ServiceId.SFANYTIME, HD, 99));
        List<PriceCards.Card> cards = PriceCards.cards(t, MAX_ONLY, null, null);
        assertEquals(Arrays.asList(
                "sfanytime: Cheapest; Buy 99 kr   HD",
                "appletv: Buy 149 kr   HD",
                // A store that only sells 4K.
                "amazonvideo: Buy 169 kr   4K"), describe(cards));
        assertTrue(cards.get(0).isGreen(0, OfferType.BUY));
    }

    @Test
    public void theGreenAmountIsTheDecidingPriceOnItsOwnLine() {
        // SD costs more than HD here: the green rental is HD's, on the second line.
        Title t = title(Kind.MOVIE, rent(ServiceId.APPLETV, SD, 59, HD, 39), rent(ServiceId.SFANYTIME, HD, 49));
        PriceCards.Card apple = PriceCards.cards(t, MAX_ONLY, null, null).get(0);
        assertEquals(ServiceId.APPLETV, apple.offers.service);
        assertFalse(apple.isGreen(0, OfferType.RENT));
        assertTrue(apple.isGreen(1, OfferType.RENT));
    }

    @Test
    public void cheapestNeedsTwoPricedStores() {
        Title one = title(Kind.MOVIE, sub(ServiceId.MAX), rent(ServiceId.APPLETV, HD, 49), rent(ServiceId.RAKUTEN));
        for (PriceCards.Card c : PriceCards.cards(one, MAX_ONLY, null, null)) {
            assertFalse(c.cheapest);
            assertEquals(-1, c.greenLine);
        }
        assertNull(PriceCards.cheapestStore(PriceCards.order(Offers.byService(one, MAX_ONLY), null)));
    }

    @Test
    public void aQualityThatCostsTheSameJoinsTheLine() {
        Title t = title(Kind.MOVIE, rent(ServiceId.APPLETV, HD, 49, UHD, 49), buy(ServiceId.APPLETV, HD, 149, UHD, 149));
        assertEquals("[Rent 49 kr · Buy 149 kr   HD · 4K]",
                PriceCards.lines(offers(t, ServiceId.APPLETV)).toString());
        // The same rental with a dearer 4K purchase: two lines.
        Title t2 = title(Kind.MOVIE, rent(ServiceId.APPLETV, HD, 49, UHD, 49), buy(ServiceId.APPLETV, HD, 149, UHD, 179));
        assertEquals("[Rent 49 kr · Buy 149 kr   HD, Rent 49 kr · Buy 179 kr   4K]",
                PriceCards.lines(offers(t2, ServiceId.APPLETV)).toString());
        // SD and HD the same, 4K dearer: SD goes without saying.
        Title t3 = title(Kind.MOVIE, buy(ServiceId.SFANYTIME, SD, 99, HD, 99, UHD, 129));
        assertEquals("[Buy 99 kr   HD, Buy 129 kr   4K]", PriceCards.lines(offers(t3, ServiceId.SFANYTIME)).toString());
        Title t4 = title(Kind.MOVIE, rent(ServiceId.APPLETV, SD, 39, HD, 39, UHD, 39), buy(ServiceId.APPLETV, SD, 99, HD, 99, UHD, 99));
        assertEquals("[Rent 39 kr · Buy 99 kr   HD · 4K]", PriceCards.lines(offers(t4, ServiceId.APPLETV)).toString());
        // SD cheaper keeps its own line; SD alone keeps its label.
        Title t5 = title(Kind.MOVIE, rent(ServiceId.APPLETV, SD, 29, HD, 39));
        assertEquals("[Rent 29 kr   SD, Rent 39 kr   HD]", PriceCards.lines(offers(t5, ServiceId.APPLETV)).toString());
        Title t6 = title(Kind.MOVIE, buy(ServiceId.RAKUTEN, SD, 99), rent(ServiceId.RAKUTEN, HD, 49, UHD, 49));
        assertEquals("[Buy 99 kr   SD, Rent 49 kr   HD · 4K]", PriceCards.lines(offers(t6, ServiceId.RAKUTEN)).toString());
        // SD to buy at HD's price, but no SD rental (Oppenheimer at Rakuten TV): SD still goes without saying.
        Title t7 = title(Kind.MOVIE, rent(ServiceId.RAKUTEN, HD, 39, UHD, 39), buy(ServiceId.RAKUTEN, SD, 79, HD, 79, UHD, 79));
        assertEquals("[Rent 39 kr · Buy 79 kr   HD · 4K]", PriceCards.lines(offers(t7, ServiceId.RAKUTEN)).toString());
        assertEquals(Arrays.asList(HD, UHD), PriceCards.qualities(offers(t7, ServiceId.RAKUTEN)));
    }

    @Test
    public void aShowsSeasonPricesSayPerSeason() {
        Title show = title(Kind.SHOW, priced(ServiceId.APPLETV, OfferType.BUY, true, HD, 199),
                priced(ServiceId.SFANYTIME, OfferType.BUY, true, HD, 219));
        List<PriceCards.Card> cards = PriceCards.cards(show, MAX_ONLY, null, null);
        assertEquals(Arrays.asList("appletv: Cheapest; Buy 199 kr   HD; Per season", "sfanytime: Buy 219 kr   HD; Per season"),
                describe(cards));
    }

    @Test
    public void freeServicesAndUnsubscribedOnes() {
        Offer youtube = new Offer(ServiceId.YOUTUBE, OfferType.FREE, 0, "HD", null, false, true);
        Title t = title(Kind.MOVIE, sub(ServiceId.NETFLIX), rent(ServiceId.APPLETV, HD, 49), youtube);
        assertEquals(Arrays.asList("youtube: Free; Free with ads; HD", "appletv: Rent 49 kr   HD",
                        "netflix: Subscription"), describe(PriceCards.cards(t, MAX_ONLY, null, null)));
    }

    @Test
    public void untickedStoresAreLeftOutEverywhere() {
        Set<ServiceId> noSf = EnumSet.of(ServiceId.APPLETV, ServiceId.AMAZONVIDEO, ServiceId.VIAPLAY, ServiceId.RAKUTEN);
        List<PriceCards.Card> cards = PriceCards.cards(moon(), MAX_ONLY, noSf, null);
        assertEquals(5, cards.size());
        assertEquals(ServiceId.VIAPLAY, cards.get(1).offers.service);
        assertTrue(cards.get(1).cheapest);
        assertEquals("Rent from 45 kr · 4 stores", PriceCards.cardLine(moon(), EnumSet.noneOf(ServiceId.class), null,
                noSf, table));
        // A subscription service with a store keeps its subscription when its store isn't ticked.
        Title t = title(Kind.MOVIE, sub(ServiceId.VIAPLAY), rent(ServiceId.VIAPLAY, HD, 45));
        Title kept = PriceCards.ticked(t, EnumSet.noneOf(ServiceId.class));
        assertEquals(1, kept.offers.size());
        assertEquals(OfferType.SUB, kept.offers.get(0).type);
        // Nothing left out: the same title.
        assertTrue(t == PriceCards.ticked(t, EnumSet.of(ServiceId.VIAPLAY)));
        assertTrue(t == PriceCards.ticked(t, null));
    }

    // ---- A tie for Cheapest (2026-10-31) ---------------------------------------------------------------------------

    /** SF Anytime and the Apple TV Store tie on a 39 kr rental, in TMDB's order; the Viaplay Store rents for 45. */
    private static Title tie() {
        return title(Kind.MOVIE, sub(ServiceId.MAX), rent(ServiceId.SFANYTIME, HD, 39), buy(ServiceId.SFANYTIME, HD, 129),
                rent(ServiceId.APPLETV, HD, 39, UHD, 49), rent(ServiceId.VIAPLAY, HD, 45));
    }

    @Test
    public void aTieForCheapestGoesToAnInstalledStore() {
        List<PriceCards.Card> cards = PriceCards.cards(tie(), MAX_ONLY, null, EnumSet.of(ServiceId.APPLETV));
        assertEquals(Arrays.asList(
                "max: Included; With your subscription",
                "appletv: Cheapest; Rent 39 kr   HD; Rent 49 kr   4K",
                "sfanytime: Rent 39 kr · Buy 129 kr   HD",
                "viaplay: Rent 45 kr   HD"), describe(cards));
        assertTrue(cards.get(1).isGreen(0, OfferType.RENT));
        assertEquals(-1, cards.get(2).greenLine);
        // Purchases alone: the same.
        Title buys = title(Kind.MOVIE, buy(ServiceId.SFANYTIME, HD, 99), buy(ServiceId.RAKUTEN, HD, 99),
                buy(ServiceId.APPLETV, HD, 99), buy(ServiceId.VIAPLAY, HD, 89));
        assertEquals(Arrays.asList(ServiceId.VIAPLAY, ServiceId.SFANYTIME, ServiceId.RAKUTEN, ServiceId.APPLETV),
                services(PriceCards.cards(buys, MAX_ONLY, null, EnumSet.of(ServiceId.APPLETV))));
        Title buyTie = title(Kind.MOVIE, buy(ServiceId.SFANYTIME, HD, 99), buy(ServiceId.RAKUTEN, HD, 99),
                buy(ServiceId.APPLETV, HD, 99));
        List<PriceCards.Card> bought = PriceCards.cards(buyTie, MAX_ONLY, null, EnumSet.of(ServiceId.APPLETV));
        assertEquals(Arrays.asList(ServiceId.APPLETV, ServiceId.SFANYTIME, ServiceId.RAKUTEN), services(bought));
        assertTrue(bought.get(0).cheapest);
        assertTrue(bought.get(0).isGreen(0, OfferType.BUY));
    }

    @Test
    public void aTieWithNeitherOrBothInstalledKeepsTmdbsOrder() {
        List<ServiceId> tmdb = Arrays.asList(ServiceId.MAX, ServiceId.SFANYTIME, ServiceId.APPLETV, ServiceId.VIAPLAY);
        for (Set<ServiceId> installed : Arrays.<Set<ServiceId>>asList(null, EnumSet.noneOf(ServiceId.class),
                EnumSet.of(ServiceId.VIAPLAY, ServiceId.MAX), EnumSet.of(ServiceId.SFANYTIME, ServiceId.APPLETV),
                EnumSet.of(ServiceId.SFANYTIME))) {
            List<PriceCards.Card> cards = PriceCards.cards(tie(), MAX_ONLY, null, installed);
            assertEquals(String.valueOf(installed), tmdb, services(cards));
            assertTrue(cards.get(1).cheapest);
            assertFalse(cards.get(2).cheapest);
        }
    }

    @Test
    public void installedAppsDontMoveStoresWithoutATie() {
        // Moon: SF Anytime alone is cheapest; Apple and Amazon tie at 49 kr, which decides nothing, so they keep
        // TMDB's order whichever is installed.
        List<String> plain = describe(PriceCards.cards(moon(), MAX_ONLY, null, null));
        assertEquals(plain, describe(PriceCards.cards(moon(), MAX_ONLY, null,
                EnumSet.of(ServiceId.AMAZONVIDEO, ServiceId.VIAPLAY, ServiceId.RAKUTEN))));
        assertEquals(plain, describe(PriceCards.cards(moon(), MAX_ONLY, null, EnumSet.allOf(ServiceId.class))));
        // A cheaper purchase in an installed store doesn't beat a rental.
        Title t = title(Kind.MOVIE, rent(ServiceId.SFANYTIME, HD, 99), buy(ServiceId.APPLETV, HD, 99));
        assertEquals(Arrays.asList(ServiceId.SFANYTIME, ServiceId.APPLETV),
                services(PriceCards.cards(t, MAX_ONLY, null, EnumSet.of(ServiceId.APPLETV))));
    }

    private static List<ServiceId> services(List<PriceCards.Card> cards) {
        List<ServiceId> out = new ArrayList<>();
        for (PriceCards.Card c : cards) out.add(c.offers.service);
        return out;
    }

    // ---- The credit -----------------------------------------------------------------------------------------------

    @Test
    public void theCreditSaysWhenPricesWereSavedOffline() {
        String a = "Availability: JustWatch · Prices: Apple / Watchmode";
        assertEquals(a, PriceCards.credit(a, false, "today 19:52"));
        assertEquals(a + " · Saved today 19:52, prices may be out of date", PriceCards.credit(a, true, "today 19:52"));
        assertEquals(a + " · Prices may be out of date", PriceCards.credit(a, true, null));
        assertNull(PriceCards.credit(null, true, "today 19:52"));
    }

    @Test
    public void savedWhenOnTheTvsClock() {
        ZoneId zone = ZoneId.of("America/New_York");
        long now = ZonedDateTime.of(2026, 10, 30, 21, 5, 0, 0, zone).toInstant().toEpochMilli();
        long today = ZonedDateTime.of(2026, 10, 30, 19, 52, 0, 0, zone).toInstant().toEpochMilli();
        long yesterday = ZonedDateTime.of(2026, 10, 29, 7, 5, 0, 0, zone).toInstant().toEpochMilli();
        long older = ZonedDateTime.of(2026, 10, 28, 19, 52, 0, 0, zone).toInstant().toEpochMilli();
        assertEquals("today 19:52", PriceCards.savedWhen(today, now, zone, true));
        assertEquals("today 7:52 PM", PriceCards.savedWhen(today, now, zone, false));
        assertEquals("yesterday 07:05", PriceCards.savedWhen(yesterday, now, zone, true));
        assertEquals("28 Oct 19:52", PriceCards.savedWhen(older, now, zone, true));
    }

    // ---- The rent/buy dialog --------------------------------------------------------------------------------------

    @Test
    public void theDialogsQualitySwitchAndNote() {
        ServiceOffers apple = offers(moon(), ServiceId.APPLETV);
        assertEquals(Arrays.asList(HD, UHD), PriceCards.qualities(apple));
        assertEquals(HD, PriceCards.startQuality(apple));
        assertEquals("4K costs 10–30 kr more here", PriceCards.fourKNote(apple));
        // One difference: one amount.
        Title same = title(Kind.MOVIE, rent(ServiceId.APPLETV, HD, 49, UHD, 59), buy(ServiceId.APPLETV, HD, 149, UHD, 159));
        assertEquals("4K costs 10 kr more here", PriceCards.fourKNote(offers(same, ServiceId.APPLETV)));
        // One quality: no switch, no note.
        ServiceOffers sf = offers(moon(), ServiceId.SFANYTIME);
        assertEquals(Collections.singletonList(HD), PriceCards.qualities(sf));
        assertNull(PriceCards.fourKNote(sf));
        // A store that only sells 4K opens on it.
        Title fourK = title(Kind.MOVIE, buy(ServiceId.AMAZONVIDEO, UHD, 169));
        assertEquals(UHD, PriceCards.startQuality(offers(fourK, ServiceId.AMAZONVIDEO)));
        // Nothing priced: no quality.
        assertNull(PriceCards.startQuality(offers(moon(), ServiceId.RAKUTEN)));
        // SD at HD's prices is no choice; equal prices open on the first quality shown, as rent.ts does.
        Title equal = title(Kind.MOVIE, rent(ServiceId.APPLETV, SD, 39, HD, 39, UHD, 39), buy(ServiceId.APPLETV, SD, 99, HD, 99, UHD, 99));
        assertEquals(Arrays.asList(HD, UHD), PriceCards.qualities(offers(equal, ServiceId.APPLETV)));
        assertEquals(HD, PriceCards.startQuality(offers(equal, ServiceId.APPLETV)));
        assertNull(PriceCards.fourKNote(offers(equal, ServiceId.APPLETV)));
        // A cheaper SD stays a choice, and the dialog opens on it.
        Title sd = title(Kind.MOVIE, rent(ServiceId.APPLETV, SD, 29, HD, 39));
        assertEquals(Arrays.asList(SD, HD), PriceCards.qualities(offers(sd, ServiceId.APPLETV)));
        assertEquals(SD, PriceCards.startQuality(offers(sd, ServiceId.APPLETV)));
    }

    @Test
    public void theDialogsPriceButtons() {
        ServiceOffers apple = offers(moon(), ServiceId.APPLETV);
        List<PriceCards.PriceButton> hd = PriceCards.buttons(apple, HD, "Apple TV");
        assertEquals("Rent · HD|49 kr|48 hours to finish once started", said(hd.get(0)));
        assertEquals("Buy · HD|149 kr|Yours to keep in Apple TV", said(hd.get(1)));
        assertEquals("Rent · 4K|59 kr|48 hours to finish once started", said(PriceCards.buttons(apple, UHD, "Apple TV").get(0)));
        // Amazon rents in HD only: in 4K only Buy shows.
        List<PriceCards.PriceButton> amazon4k = PriceCards.buttons(offers(moon(), ServiceId.AMAZONVIDEO), UHD, "Prime Video");
        assertEquals(1, amazon4k.size());
        assertEquals("Buy · 4K|169 kr|Yours to keep in Prime Video", said(amazon4k.get(0)));
        // No known price: the same buttons without amounts.
        List<PriceCards.PriceButton> rakuten = PriceCards.buttons(offers(moon(), ServiceId.RAKUTEN), null, "Rakuten TV");
        assertEquals("Rent|Price in the app|48 hours to finish once started", said(rakuten.get(0)));
        assertEquals("Buy|Price in the app|Yours to keep in Rakuten TV", said(rakuten.get(1)));
        // A season.
        Title show = title(Kind.SHOW, priced(ServiceId.APPLETV, OfferType.BUY, true, HD, 199));
        assertEquals("Buy a season · HD|199 kr|Per season, yours to keep in Apple TV",
                said(PriceCards.buttons(offers(show, ServiceId.APPLETV), HD, "Apple TV").get(0)));
    }

    private static String said(PriceCards.PriceButton b) {
        return b.label + "|" + b.amount + "|" + b.detail;
    }

    @Test
    public void theDialogNamesTheInstalledStoreOfATie() {
        Title t = tie();
        List<ServiceOffers> all = Offers.byService(t, MAX_ONLY);
        Set<ServiceId> apple = EnumSet.of(ServiceId.APPLETV);
        assertEquals("SF Anytime has it for 39 kr (HD).",
                PriceCards.cheaperElsewhere(all, offers(t, ServiceId.VIAPLAY), null, table));
        assertEquals("Apple TV Store has it for 39 kr (HD).",
                PriceCards.cheaperElsewhere(all, offers(t, ServiceId.VIAPLAY), apple, table));
        // The tied store hears of nothing cheaper either way.
        assertEquals("", PriceCards.cheaperElsewhere(all, offers(t, ServiceId.SFANYTIME), apple, table));
        assertEquals("", PriceCards.cheaperElsewhere(all, offers(t, ServiceId.APPLETV), null, table));
    }

    @Test
    public void theDialogNamesACheaperStore() {
        Title t = moon();
        List<ServiceOffers> all = Offers.byService(t, MAX_ONLY);
        assertEquals("SF Anytime has it for 39 kr (HD).", PriceCards.cheaperElsewhere(all, offers(t, ServiceId.APPLETV), null, table));
        assertEquals("Payment finishes in the Apple TV app. SF Anytime has it for 39 kr (HD).",
                PriceCards.paymentNote("Apple TV", PriceCards.cheaperElsewhere(all, offers(t, ServiceId.APPLETV), null, table)));
        // The cheapest store itself, and a store no source priced, which still hears of the cheaper one.
        assertEquals("", PriceCards.cheaperElsewhere(all, offers(t, ServiceId.SFANYTIME), null, table));
        assertEquals("SF Anytime has it for 39 kr (HD).", PriceCards.cheaperElsewhere(all, offers(t, ServiceId.RAKUTEN), null, table));
        assertEquals("Payment finishes in the Rakuten TV app.", PriceCards.paymentNote("Rakuten TV", ""));
        // Equal rentals: nothing cheaper.
        Title equal = title(Kind.MOVIE, rent(ServiceId.SFANYTIME, HD, 49), rent(ServiceId.APPLETV, HD, 49));
        assertEquals("", PriceCards.cheaperElsewhere(Offers.byService(equal, MAX_ONLY), offers(equal, ServiceId.APPLETV), null, table));
        // The Viaplay Store is named as a store.
        Title via = title(Kind.MOVIE, rent(ServiceId.VIAPLAY, HD, 29), rent(ServiceId.APPLETV, HD, 49));
        assertEquals("Viaplay Store has it for 29 kr (HD).",
                PriceCards.cheaperElsewhere(Offers.byService(via, MAX_ONLY), offers(via, ServiceId.APPLETV), null, table));
        // SD at HD's price isn't named: the quality is HD.
        Title sd = title(Kind.MOVIE, rent(ServiceId.SFANYTIME, SD, 39, HD, 39, UHD, 39), rent(ServiceId.APPLETV, HD, 49));
        assertEquals("SF Anytime has it for 39 kr (HD).",
                PriceCards.cheaperElsewhere(Offers.byService(sd, MAX_ONLY), offers(sd, ServiceId.APPLETV), null, table));
    }

    @Test
    public void theDialogSetsRentAgainstRentAndBuyAgainstBuy() {
        // No priced rental anywhere: Cheapest goes on the cheapest purchase, and the dialog names it as one.
        Title t = title(Kind.MOVIE, rent(ServiceId.SFANYTIME), buy(ServiceId.SFANYTIME, HD, 129, UHD, 159),
                buy(ServiceId.APPLETV, HD, 149));
        List<ServiceOffers> all = Offers.byService(t, MAX_ONLY);
        assertEquals("SF Anytime has it for 129 kr to buy (HD).",
                PriceCards.cheaperElsewhere(all, offers(t, ServiceId.APPLETV), null, table));
        assertEquals("", PriceCards.cheaperElsewhere(all, offers(t, ServiceId.SFANYTIME), null, table));
        // Equal purchases: nothing cheaper.
        Title equal = title(Kind.MOVIE, buy(ServiceId.SFANYTIME, HD, 149), buy(ServiceId.APPLETV, HD, 149));
        assertEquals("", PriceCards.cheaperElsewhere(Offers.byService(equal, MAX_ONLY), offers(equal, ServiceId.APPLETV), null, table));
        // A rental decides Cheapest: a store that only sells hears of it, its own cheaper purchase not set against it.
        Title mixed = title(Kind.MOVIE, rent(ServiceId.SFANYTIME, HD, 39), buy(ServiceId.APPLETV, HD, 29));
        assertEquals("SF Anytime has it for 39 kr (HD).", PriceCards.cheaperElsewhere(Offers.byService(mixed, MAX_ONLY),
                offers(mixed, ServiceId.APPLETV), null, table));
    }

    // ---- The card line --------------------------------------------------------------------------------------------

    @Test
    public void cardLines() {
        Set<ServiceId> none = EnumSet.noneOf(ServiceId.class);
        assertEquals("HBO Max · Included", PriceCards.cardLine(moon(), MAX_ONLY, null, null, table));
        assertEquals("YouTube · Free", PriceCards.cardLine(title(Kind.MOVIE,
                new Offer(ServiceId.YOUTUBE, OfferType.FREE, 0, null, null, false, true), rent(ServiceId.APPLETV, HD, 29)),
                none, EnumSet.of(ServiceId.YOUTUBE), null, table));
        // Rakuten has no known price but still counts as a store.
        assertEquals("Rent from 39 kr · 5 stores", PriceCards.cardLine(moon(), none, null, null, table));
        assertEquals("Buy from 99 kr · 2 stores", PriceCards.cardLine(title(Kind.MOVIE,
                buy(ServiceId.APPLETV, HD, 149), buy(ServiceId.SFANYTIME, HD, 99)), none, null, null, table));
        assertEquals("Apple TV Store · Rent 29 kr", PriceCards.cardLine(title(Kind.MOVIE,
                rent(ServiceId.APPLETV, HD, 29), buy(ServiceId.APPLETV, HD, 99)), none, null, null, table));
        assertEquals("Apple TV Store · Rent from 29 kr", PriceCards.cardLine(title(Kind.MOVIE,
                rent(ServiceId.APPLETV, HD, 29, UHD, 39)), none, null, null, table));
        assertEquals("Rakuten TV · Rent", PriceCards.cardLine(title(Kind.MOVIE, rent(ServiceId.RAKUTEN)), none, null,
                null, table));
        assertEquals("Viaplay Store · Rent 45 kr", PriceCards.cardLine(title(Kind.MOVIE,
                sub(ServiceId.VIAPLAY), rent(ServiceId.VIAPLAY, HD, 45)), none, null, null, table));
        assertEquals("Buy a season from 199 kr · 2 stores", PriceCards.cardLine(title(Kind.SHOW,
                priced(ServiceId.APPLETV, OfferType.BUY, true, HD, 199),
                priced(ServiceId.SFANYTIME, OfferType.BUY, true, HD, 219)), none, null, null, table));
        assertEquals("Buy a season from 199 kr", PriceCards.cardLine(title(Kind.SHOW,
                priced(ServiceId.APPLETV, OfferType.BUY, true, HD, 199)), none, null, null, table));
        // Neither a subscription the household doesn't have nor nothing at all names anything (F1).
        assertEquals("", PriceCards.cardLine(title(Kind.MOVIE, sub(ServiceId.NETFLIX)), none, null, null, table));
        assertEquals("", PriceCards.cardLine(title(Kind.MOVIE), none, null, null, table));
        // Only unticked stores sell it.
        assertEquals("", PriceCards.cardLine(title(Kind.MOVIE, rent(ServiceId.RAKUTEN)), none, null, none, table));
    }

    // ---- Cards count only the household's services and stores (F1, 2026-11-01) ------------------------------------

    private static Offer free(ServiceId s) {
        return new Offer(s, OfferType.FREE, 0, null, null, false, true);
    }

    @Test
    public void theRuleOfferByOffer() {
        Set<ServiceId> none = EnumSet.noneOf(ServiceId.class);
        Set<ServiceId> netflix = EnumSet.of(ServiceId.NETFLIX);
        Set<ServiceId> sfOnly = EnumSet.of(ServiceId.SFANYTIME);
        assertTrue(PriceCards.isYours(sub(ServiceId.NETFLIX), netflix, none, none));
        assertFalse(PriceCards.isYours(sub(ServiceId.NETFLIX), none, netflix, null));
        assertTrue(PriceCards.isYours(free(ServiceId.SVTPLAY), none, EnumSet.of(ServiceId.SVTPLAY), none));
        assertTrue(PriceCards.isYours(free(ServiceId.SVTPLAY), EnumSet.of(ServiceId.SVTPLAY), none, none));
        assertTrue(PriceCards.isYours(free(ServiceId.SVTPLAY), EnumSet.of(ServiceId.SVTPLAY), null, none));
        assertFalse(PriceCards.isYours(free(ServiceId.SVTPLAY), netflix, netflix, null));
        assertTrue(PriceCards.isYours(rent(ServiceId.SFANYTIME, HD, 39), none, none, sfOnly));
        assertFalse(PriceCards.isYours(rent(ServiceId.APPLETV, HD, 29), netflix, EnumSet.of(ServiceId.APPLETV),
                sfOnly));
        assertFalse(PriceCards.isYours(buy(ServiceId.APPLETV, HD, 99), none, none, none));
        // Null stores: no Stores choice made, every store counts.
        assertTrue(PriceCards.isYours(buy(ServiceId.APPLETV, HD, 99), none, none, null));
    }

    @Test
    public void aTickedSubscriptionIsIncluded() {
        Title t = title(Kind.MOVIE, sub(ServiceId.NETFLIX), rent(ServiceId.APPLETV, HD, 29));
        assertEquals("Netflix · Included", PriceCards.cardLine(t, EnumSet.of(ServiceId.NETFLIX), null, null, table));
        assertEquals(ServiceId.NETFLIX, PriceCards.cardOffer(t, EnumSet.of(ServiceId.NETFLIX), null, null).service);
    }

    @Test
    public void anUntickedSubscriptionIsHidden() {
        Set<ServiceId> none = EnumSet.noneOf(ServiceId.class);
        Title t = title(Kind.MOVIE, sub(ServiceId.NETFLIX));
        assertEquals("", PriceCards.cardLine(t, MAX_ONLY, null, null, table));
        assertNull(PriceCards.cardOffer(t, MAX_ONLY, null, null));
        // Its app being installed doesn't make it the household's: only the tick does.
        assertEquals("", PriceCards.cardLine(t, none, EnumSet.of(ServiceId.NETFLIX), null, table));
        // A store that has it still shows, the subscription left out.
        Title rented = title(Kind.MOVIE, sub(ServiceId.NETFLIX), rent(ServiceId.APPLETV, HD, 29));
        assertEquals("Apple TV Store · Rent 29 kr", PriceCards.cardLine(rented, MAX_ONLY, null, null, table));
    }

    @Test
    public void aFreeServiceShowsWhenInstalledOrTicked() {
        Set<ServiceId> none = EnumSet.noneOf(ServiceId.class);
        Title t = title(Kind.MOVIE, free(ServiceId.SVTPLAY));
        assertEquals("SVT Play · Free", PriceCards.cardLine(t, none, EnumSet.of(ServiceId.SVTPLAY), null, table));
        assertEquals("SVT Play · Free", PriceCards.cardLine(t, EnumSet.of(ServiceId.SVTPLAY), none, null, table));
        assertEquals(ServiceId.SVTPLAY, PriceCards.cardOffer(t, none, EnumSet.of(ServiceId.SVTPLAY), null).service);
    }

    @Test
    public void aFreeServiceNeitherInstalledNorTickedIsHidden() {
        Set<ServiceId> none = EnumSet.noneOf(ServiceId.class);
        Title t = title(Kind.MOVIE, free(ServiceId.PLUTOTV));
        assertEquals("", PriceCards.cardLine(t, none, EnumSet.of(ServiceId.SVTPLAY), null, table));
        assertEquals("", PriceCards.cardLine(t, MAX_ONLY, null, null, table));
        assertNull(PriceCards.cardOffer(t, none, none, null));
        // A ticked store's rental shows instead of the free service the household doesn't have.
        Title rented = title(Kind.MOVIE, free(ServiceId.PLUTOTV), rent(ServiceId.SFANYTIME, HD, 39));
        assertEquals("SF Anytime · Rent 39 kr", PriceCards.cardLine(rented, none, none,
                EnumSet.of(ServiceId.SFANYTIME), table));
    }

    @Test
    public void aTickedStoresPriceShowsAndAnUntickedOnesDoesNot() {
        Set<ServiceId> none = EnumSet.noneOf(ServiceId.class);
        Set<ServiceId> sfOnly = ServicePicks.counted(EnumSet.of(ServiceId.SFANYTIME));
        Title t = title(Kind.MOVIE, rent(ServiceId.APPLETV, HD, 29), rent(ServiceId.SFANYTIME, HD, 39));
        assertEquals("SF Anytime · Rent 39 kr", PriceCards.cardLine(t, none, null, sfOnly, table));
        Offer o = PriceCards.cardOffer(t, none, null, sfOnly);
        assertEquals(ServiceId.SFANYTIME, o.service);
        Title apple = title(Kind.MOVIE, rent(ServiceId.APPLETV, HD, 29));
        assertEquals("", PriceCards.cardLine(apple, none, null, sfOnly, table));
        assertNull(PriceCards.cardOffer(apple, none, null, sfOnly));
    }

    @Test
    public void nothingOfTheHouseholdsLeavesTheLineBlank() {
        Set<ServiceId> none = EnumSet.noneOf(ServiceId.class);
        Title t = title(Kind.MOVIE, sub(ServiceId.NETFLIX), free(ServiceId.PLUTOTV), rent(ServiceId.APPLETV, HD, 29));
        assertEquals("", PriceCards.cardLine(t, none, none, none, table));
        assertNull(PriceCards.cardOffer(t, none, none, none));
        assertEquals("", Offers.availability(PriceCards.cardOffer(t, none, none, none), none));
    }

    @Test
    public void includedOnlyFollowsTheCardsRule() {
        Set<ServiceId> none = EnumSet.noneOf(ServiceId.class);
        Set<ServiceId> svt = EnumSet.of(ServiceId.SVTPLAY);
        Title pluto = title(Kind.MOVIE, free(ServiceId.PLUTOTV));
        Title svtFree = title(Kind.MOVIE, free(ServiceId.SVTPLAY));
        Title netflix = title(Kind.MOVIE, sub(ServiceId.NETFLIX));
        // A free service counts only when its app is installed or it is ticked; null installed counts every one.
        assertFalse(Offers.isOwnIncluded(pluto, none, svt));
        assertTrue(Offers.isOwnIncluded(svtFree, none, svt));
        assertTrue(Offers.isOwnIncluded(pluto, EnumSet.of(ServiceId.PLUTOTV), none));
        assertTrue(Offers.isOwnIncluded(pluto, none, null));
        assertTrue(Offers.isOwnIncluded(netflix, EnumSet.of(ServiceId.NETFLIX), none));
        assertFalse(Offers.isOwnIncluded(netflix, none, EnumSet.of(ServiceId.NETFLIX)));
        // Movies and TV Shows' Availability › Included only.
        LibraryFilters f = new LibraryFilters(Kind.MOVIE, Collections.emptyList(), none, svt, id -> false);
        f.clear();
        f.set(LibraryFilters.AVAILABILITY, 1);
        assertFalse(f.passes(pluto));
        assertTrue(f.passes(svtFree));
        // Pick for me's Availability › Included only.
        assertFalse(PickRules.test(PickRules.AVAIL, 1, pluto, null, none, svt));
        assertTrue(PickRules.test(PickRules.AVAIL, 1, svtFree, null, none, svt));
    }

    @Test
    public void mockOffersKeepTheirDollarPriceUnderTheirQuality() {
        Offer apple = new Offer(ServiceId.APPLETV, OfferType.RENT, 3.99, "4K · Dolby Vision");
        assertEquals(Collections.singletonMap(UHD, new Money(399, "USD")), PriceCards.prices(apple));
        assertEquals(Collections.singletonMap(HD, new Money(999, "USD")),
                PriceCards.prices(new Offer(ServiceId.YOUTUBE, OfferType.BUY, 9.99, "HD")));
        assertTrue(PriceCards.prices(new Offer(ServiceId.PRIME, OfferType.SUB, 0, "4K · HDR")).isEmpty());
        Title t = title(Kind.MOVIE, apple, new Offer(ServiceId.GOOGLETV, OfferType.RENT, 3.99, "4K"));
        assertEquals("Rent from $3.99 · 2 stores", PriceCards.cardLine(t, EnumSet.noneOf(ServiceId.class), null, null,
                new ServiceTable()));
    }
}
