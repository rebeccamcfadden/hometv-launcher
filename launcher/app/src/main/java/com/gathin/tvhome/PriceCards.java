package com.gathin.tvhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * How design 07-prices A shows a title's prices (design_preview/README.md, Prices, and Search's card line; ported from
 * src/data/offers.ts and src/screens/rent.ts): the order of the Where to watch cards and their Cheapest tag, a line
 * per picture quality, the credit under the row, the rent/buy dialog's quality switch, buttons and cheaper-store
 * hint, and the third line of the small cards. Pure Java and English only, like {@link Offers} and
 * {@link PreviewRules}, so it is unit-tested on the JVM (PriceCardsTest).
 *
 * <p>Ticks: a store's rent and buy offers count only when the household ticked the store. Launcher settings has no
 * store ticks yet, so callers pass null for "every store" (BACKLOG.md, Streaming availability and prices).
 */
final class PriceCards {
    private PriceCards() {}

    private static final Quality[] QUALITIES = Quality.values();

    // ---- Ticks and prices -----------------------------------------------------------------------------------------

    static boolean isPaid(Offer o) {
        return o.type == OfferType.RENT || o.type == OfferType.BUY;
    }

    /**
     * The title as the household can use it: its rent and buy offers only from the ticked {@code stores} (null means
     * every store). The same title when nothing is left out.
     */
    static Title ticked(Title t, Set<ServiceId> stores) {
        if (stores == null) return t;
        List<Offer> kept = new ArrayList<>(t.offers.size());
        for (Offer o : t.offers) if (!isPaid(o) || stores.contains(o.service)) kept.add(o);
        if (kept.size() == t.offers.size()) return t;
        Title.Draft d = new Title.Draft(t);
        d.offers = kept;
        return d.build();
    }

    /**
     * A rent or buy offer's price per quality: a real one's own; a mock one's single dollar price under the quality
     * it names ("4K · Dolby Vision" is 4K, none is HD). Empty for an unpriced offer and for SUB and FREE.
     */
    static Map<Quality, Money> prices(Offer o) {
        if (o == null || !isPaid(o)) return Collections.emptyMap();
        if (!o.prices.isEmpty()) return o.prices;
        if (o.money != null) {
            Quality q = qualityOf(o.quality);
            Map<Quality, Money> one = new EnumMap<>(Quality.class);
            one.put(q, o.money);
            return one;
        }
        if (o.real || o.price <= 0) return Collections.emptyMap();
        Money m = Money.of(o.price, "USD");
        if (m == null) return Collections.emptyMap();
        Map<Quality, Money> one = new EnumMap<>(Quality.class);
        one.put(qualityOf(o.quality), m);
        return one;
    }

    private static Quality qualityOf(String label) {
        if (label == null) return Quality.HD;
        int dot = label.indexOf(" · ");
        Quality q = Quality.parse(dot < 0 ? label : label.substring(0, dot));
        return q == null ? Quality.HD : q;
    }

    /** The cheapest price in any quality, or null without one. */
    static Money cheapest(Offer o) {
        Money min = null;
        for (Money m : prices(o).values()) if (min == null || m.compareTo(min) < 0) min = m;
        return min;
    }

    // ---- Where to watch -------------------------------------------------------------------------------------------

    /**
     * The cards' order (README, Cheapest rule, 2026-10-30): included and free services first, in the offers' order;
     * then the stores: rentals with a price, cheapest first; purchases with a price, cheapest first; stores with no
     * price; last, subscriptions the household doesn't have. Equal ones keep the offers' order, except that when
     * stores tie on the price that decides Cheapest (2026-10-31), the first of them whose app is in
     * {@code installed} (null for none known) leads, and so is tagged.
     */
    static List<ServiceOffers> order(List<ServiceOffers> services, Set<ServiceId> installed) {
        List<ServiceOffers> out = new ArrayList<>(services);
        // List.sort is stable, so equal ranks keep the order Offers.byService gave.
        out.sort((a, b) -> {
            int ra = rank(a), rb = rank(b);
            if (ra != rb) return Integer.compare(ra, rb);
            if (ra == 1 || ra == 2) return deciding(a).compareTo(deciding(b));
            return 0;
        });
        installedLeads(out, installed);
        return Collections.unmodifiableList(out);
    }

    /** A priced store's deciding price: its cheapest rental, else its cheapest purchase. */
    private static Money deciding(ServiceOffers s) {
        Money rent = cheapest(s.rent);
        return rent != null ? rent : cheapest(s.buy);
    }

    /**
     * Moves the first installed store of those tied with the leading priced store on its deciding price to the
     * lead, when the leading one isn't installed itself; the others keep their order.
     */
    private static void installedLeads(List<ServiceOffers> sorted, Set<ServiceId> installed) {
        if (installed == null || installed.isEmpty()) return;
        int lead = 0;
        while (lead < sorted.size() && rank(sorted.get(lead)) == 0) lead++;
        if (lead == sorted.size()) return;
        ServiceOffers first = sorted.get(lead);
        int r = rank(first);
        if ((r != 1 && r != 2) || installed.contains(first.service)) return;
        Money price = deciding(first);
        for (int i = lead + 1; i < sorted.size(); i++) {
            ServiceOffers s = sorted.get(i);
            if (rank(s) != r || deciding(s).compareTo(price) != 0) return;
            if (installed.contains(s.service)) {
                sorted.add(lead, sorted.remove(i));
                return;
            }
        }
    }

    /** 0 included or free, 1 a priced rental, 2 a priced purchase, 3 a store with no price, 4 anything else. */
    private static int rank(ServiceOffers s) {
        if (s.watchable()) return 0;
        if (cheapest(s.rent) != null) return 1;
        if (cheapest(s.buy) != null) return 2;
        return s.rent != null || s.buy != null ? 3 : 4;
    }

    /**
     * The store tagged Cheapest: the first priced store of {@link #order}'s list (the cheapest rental, else the
     * cheapest purchase; an installed one of equals), once two or more stores have a price; null otherwise.
     */
    static ServiceId cheapestStore(List<ServiceOffers> ordered) {
        ServiceOffers first = null;
        int priced = 0;
        for (ServiceOffers s : ordered) {
            int r = rank(s);
            if (r != 1 && r != 2) continue;
            if (first == null) first = s;
            priced++;
        }
        return priced >= 2 ? first.service : null;
    }

    /** A store card's line for one quality, or for several that cost the same ("HD · 4K"); null for no offer. */
    static final class Line {
        final String quality;
        final Money rent;
        final Money buy;

        Line(String quality, Money rent, Money buy) {
            this.quality = quality;
            this.rent = rent;
            this.buy = buy;
        }

        /** "Rent 49 kr · Buy 149 kr", without the quality. */
        String prices() {
            List<String> parts = new ArrayList<>(2);
            if (rent != null) parts.add("Rent " + rent.format());
            if (buy != null) parts.add("Buy " + buy.format());
            return String.join(" · ", parts);
        }

        @Override
        public String toString() {
            return prices() + "   " + quality;
        }
    }

    /**
     * A store's prices, one line per quality from SD up; a quality that costs the same as the line above joins it
     * ("HD · 4K"). SD is left out when it costs nothing less than HD (each price it has is HD's): nobody picks it at
     * HD's price, and "SD · HD · 4K" doesn't fit the card beside two prices.
     */
    static List<Line> lines(ServiceOffers s) {
        Map<Quality, Money> rent = prices(s.rent), buy = prices(s.buy);
        boolean skipSd = sdIsNoCheaper(rent, buy);
        List<Line> out = new ArrayList<>(3);
        for (Quality q : QUALITIES) {
            if (q == Quality.SD && skipSd) continue;
            Money r = rent.get(q), b = buy.get(q);
            if (r == null && b == null) continue;
            Line last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && Objects.equals(last.rent, r) && Objects.equals(last.buy, b)) {
                out.set(out.size() - 1, new Line(last.quality + " · " + q.label, r, b));
            } else {
                out.add(new Line(q.label, r, b));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** The store prices HD, and SD's rent and buy, where it has them, cost the same as HD's. */
    private static boolean sdIsNoCheaper(Map<Quality, Money> rent, Map<Quality, Money> buy) {
        if (!rent.containsKey(Quality.HD) && !buy.containsKey(Quality.HD)) return false;
        Money r = rent.get(Quality.SD), b = buy.get(Quality.SD);
        if (r == null && b == null) return false;
        return (r == null || r.equals(rent.get(Quality.HD))) && (b == null || b.equals(buy.get(Quality.HD)));
    }

    /** One Where to watch card, as it reads. */
    static final class Card {
        final ServiceOffers offers;
        /** "Included", "Free", or null for a store. */
        final String badge;
        /** The store is tagged Cheapest (green), and its deciding price is green. */
        final boolean cheapest;
        /** The price lines; empty for an included or free service and for a store with no price. */
        final List<Line> lines;
        /** The line, and the type on it, whose amount is green; -1 for none. */
        final int greenLine;
        final OfferType greenType;
        /** Without price lines: "With your subscription", "Free with ads", "Rent · Buy", "Subscription". */
        final String text;
        /** The quality ("4K · HDR"), "Per season", "Price shown in the app", or null. */
        final String detail;

        Card(ServiceOffers offers, String badge, boolean cheapest, List<Line> lines, int greenLine,
             OfferType greenType, String text, String detail) {
            this.offers = offers;
            this.badge = badge;
            this.cheapest = cheapest;
            this.lines = lines;
            this.greenLine = greenLine;
            this.greenType = greenType;
            this.text = text;
            this.detail = detail;
        }

        /** Whether the amount of {@code type} on line {@code i} is the green one. */
        boolean isGreen(int i, OfferType type) {
            return i == greenLine && type == greenType;
        }

        /** "Cheapest; Rent 49 kr · Buy 149 kr   HD; Per season": everything but the name, for tests and TalkBack. */
        String said() {
            List<String> parts = new ArrayList<>();
            if (badge != null) parts.add(badge);
            if (cheapest) parts.add("Cheapest");
            if (text != null) parts.add(text);
            for (Line l : lines) parts.add(l.toString());
            if (detail != null && !detail.isEmpty()) parts.add(detail);
            return String.join("; ", parts);
        }
    }

    /**
     * The title's Where to watch cards, in {@link #order}, with the ticks applied; {@code installed}, the services
     * whose app is on the TV, settles a tie for Cheapest.
     */
    static List<Card> cards(Title t, Set<ServiceId> subscribed, Set<ServiceId> stores, Set<ServiceId> installed) {
        List<ServiceOffers> ordered = order(Offers.byService(ticked(t, stores), subscribed), installed);
        ServiceId best = cheapestStore(ordered);
        List<Card> out = new ArrayList<>(ordered.size());
        for (ServiceOffers s : ordered) out.add(card(s, s.service == best));
        return Collections.unmodifiableList(out);
    }

    static Card card(ServiceOffers s, boolean isCheapest) {
        String quality = s.quality == null || s.quality.isEmpty() ? null : s.quality;
        if (s.watchable()) {
            return new Card(s, s.free ? "Free" : "Included", false, Collections.emptyList(), -1, null,
                    s.included ? "With your subscription" : "Free with ads", quality);
        }
        if (s.rent == null && s.buy == null) {
            return new Card(s, null, false, Collections.emptyList(), -1, null, "Subscription", quality);
        }
        List<Line> lines = lines(s);
        if (lines.isEmpty()) {
            List<String> verbs = new ArrayList<>(2);
            if (s.rent != null) verbs.add("Rent");
            if (s.buy != null) verbs.add("Buy");
            return new Card(s, null, false, lines, -1, null, String.join(" · ", verbs), "Price shown in the app");
        }
        int greenLine = -1;
        OfferType greenType = null;
        if (isCheapest) {
            Money rent = cheapest(s.rent);
            greenType = rent != null ? OfferType.RENT : OfferType.BUY;
            Money green = rent != null ? rent : cheapest(s.buy);
            for (int i = 0; i < lines.size() && greenLine < 0; i++) {
                Line l = lines.get(i);
                if (green.equals(greenType == OfferType.RENT ? l.rent : l.buy)) greenLine = i;
            }
        }
        boolean seasonal = (s.rent != null && s.rent.seasonal) || (s.buy != null && s.buy.seasonal);
        return new Card(s, null, isCheapest, lines, greenLine, greenType, null, seasonal ? "Per season" : null);
    }

    /**
     * The caption under the row: the catalogue's attribution ("Availability: JustWatch · Prices: Apple / Watchmode"),
     * and offline "· Saved today 19:52, prices may be out of date" ({@code saved} from {@link #savedWhen}, or null
     * when not known). Null without an attribution (the mock catalogue).
     */
    static String credit(String attribution, boolean offline, String saved) {
        if (attribution == null) return null;
        if (!offline) return attribution;
        return attribution + (saved == null ? " · Prices may be out of date"
                : " · Saved " + saved + ", prices may be out of date");
    }

    /** "today 19:52", "yesterday 7:52 PM", "28 Oct 19:52": when something was saved, on the TV's clock. */
    static String savedWhen(long at, long now, ZoneId zone, boolean h24) {
        ZonedDateTime when = Instant.ofEpochMilli(at).atZone(zone);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String time = when.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", Locale.ENGLISH));
        LocalDate day = when.toLocalDate();
        if (day.equals(today)) return "today " + time;
        if (day.equals(today.minusDays(1))) return "yesterday " + time;
        return day.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " " + time;
    }

    // ---- The rent/buy dialog --------------------------------------------------------------------------------------

    /** The qualities the dialog can switch between, SD first; SD only when it costs something different from HD. */
    static List<Quality> qualities(ServiceOffers s) {
        Map<Quality, Money> rent = prices(s.rent), buy = prices(s.buy);
        boolean skipSd = sdIsNoCheaper(rent, buy);
        List<Quality> all = new ArrayList<>(QUALITIES.length);
        for (Quality q : QUALITIES) {
            if (q == Quality.SD && skipSd) continue;
            if (rent.containsKey(q) || buy.containsKey(q)) all.add(q);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * The quality the dialog opens on: the first of its {@link #qualities} in which the store's cheapest rental (else
     * purchase) costs its cheapest price, as rent.ts picks it; the first quality when that offer has no price; null
     * when the store has none.
     */
    static Quality startQuality(ServiceOffers s) {
        Offer lead = s.rent != null ? s.rent : s.buy;
        List<Quality> all = qualities(s);
        Quality q = lead == null ? null : shownQuality(all, lead);
        if (q != null) return q;
        return all.isEmpty() ? null : all.get(0);
    }

    /** The first of {@code shown} in which o costs its cheapest price, or null. */
    private static Quality shownQuality(List<Quality> shown, Offer o) {
        Money min = cheapest(o);
        if (min == null) return null;
        Map<Quality, Money> p = prices(o);
        for (Quality q : shown) if (min.equals(p.get(q))) return q;
        return null;
    }

    /** "4K costs 10–30 kr more here", from the store's HD and 4K prices; null without 4K or a difference. */
    static String fourKNote(ServiceOffers s) {
        Money lo = null, hi = null;
        for (Offer o : new Offer[]{s.rent, s.buy}) {
            Map<Quality, Money> p = prices(o);
            Money hd = p.get(Quality.HD), uhd = p.get(Quality.UHD);
            if (hd == null || uhd == null || !hd.currency.equals(uhd.currency) || uhd.compareTo(hd) <= 0) continue;
            Money diff = new Money(uhd.minor - hd.minor, uhd.currency);
            if (lo == null || diff.compareTo(lo) < 0) lo = diff;
            if (hi == null || diff.compareTo(hi) > 0) hi = diff;
        }
        if (lo == null) return null;
        return "4K costs " + range(lo, hi) + " more here";
    }

    /** "10 kr", or "10–30 kr" with the unit once. */
    private static String range(Money lo, Money hi) {
        String a = lo.format(), b = hi.format();
        if (lo.equals(hi)) return b;
        if (a.endsWith(" kr") && b.endsWith(" kr")) return a.substring(0, a.length() - 3) + "–" + b;
        return a + "–" + b;
    }

    /** One of the dialog's big price buttons. */
    static final class PriceButton {
        final OfferType type;
        final Offer offer;
        /** "Rent · HD", "Buy a season · 4K", or "Rent" when the store has no price. */
        final String label;
        /** "49 kr", or "Price in the app". */
        final String amount;
        /** "48 hours to finish once started", "Yours to keep in Apple TV". */
        final String detail;

        PriceButton(OfferType type, Offer offer, String label, String amount, String detail) {
            this.type = type;
            this.offer = offer;
            this.label = label;
            this.amount = amount;
            this.detail = detail;
        }
    }

    /**
     * The dialog's price buttons in {@code quality} (null when the store prices nothing): rent, then buy, each only
     * when the store has it in that quality. An offer no source priced keeps its button, without an amount.
     */
    static List<PriceButton> buttons(ServiceOffers s, Quality quality, String app) {
        List<PriceButton> out = new ArrayList<>(2);
        for (Offer o : new Offer[]{s.rent, s.buy}) {
            if (o == null) continue;
            Map<Quality, Money> p = prices(o);
            if (quality != null && !p.isEmpty() && !p.containsKey(quality)) continue;
            Money m = quality == null ? null : p.get(quality);
            boolean rent = o.type == OfferType.RENT;
            String label = (rent ? "Rent" : "Buy") + (o.seasonal ? " a season" : "")
                    + (m != null ? " · " + quality.label : "");
            String detail = rent ? "48 hours to finish once started"
                    : (o.seasonal ? "Per season, yours to keep in " : "Yours to keep in ") + app;
            out.add(new PriceButton(o.type, o, label, m != null ? m.format() : "Price in the app", detail));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The cheaper store the dialog names, "SF Anytime has it for 39 kr (HD)." or, for a purchase, "SF Anytime has it
     * for 129 kr to buy (HD).": the Cheapest store's deciding offer (its rental, else its purchase) when that store
     * isn't this one and costs less than this store's offer of the same type (or this store has no priced one); ""
     * otherwise. Rent is only set against rent, buy against buy. {@code installed} picks the Cheapest store as Where
     * to watch does.
     */
    static String cheaperElsewhere(List<ServiceOffers> services, ServiceOffers here, Set<ServiceId> installed,
                                   ServiceTable table) {
        List<ServiceOffers> ordered = order(services, installed);
        ServiceId best = cheapestStore(ordered);
        if (best == null || best == here.service) return "";
        ServiceOffers b = null;
        for (ServiceOffers s : ordered) if (s.service == best) b = s;
        if (b == null) return "";
        boolean rent = cheapest(b.rent) != null;
        Offer offer = rent ? b.rent : b.buy;
        Money there = cheapest(offer);
        if (there == null) return "";
        Money mine = cheapest(rent ? here.rent : here.buy);
        if (mine != null && there.compareTo(mine) >= 0) return "";
        Quality q = shownQuality(qualities(b), offer);
        return table.storeName(best) + " has it for " + there.format() + (rent ? "" : " to buy")
                + (q == null ? "" : " (" + q.label + ")") + ".";
    }

    /** "Payment finishes in the Apple TV app. SF Anytime has it for 39 kr (HD)." */
    static String paymentNote(String app, String cheaper) {
        return "Payment finishes in the " + app + " app." + (cheaper == null || cheaper.isEmpty() ? "" : " " + cheaper);
    }

    // ---- The card line --------------------------------------------------------------------------------------------

    /**
     * The small cards' third line (README, Search, Card line): "Prime Video · Included", "YouTube · Free"; else the
     * cheapest way to pay across the ticked stores, "Rent from 39 kr · 4 stores", "Buy from 99 kr · 2 stores"; one
     * store "Apple TV Store · Rent 29 kr" or "Rakuten TV · Rent" when its price isn't known; a show's seasons "Buy a
     * season from 199 kr · 2 stores". "Not on your services" when only subscriptions the household doesn't have
     * offer it, "Not available" when nothing does. Quality is left to Where to watch. Only the prices already known
     * are used: a card never asks for one.
     */
    static String cardLine(Title t, Set<ServiceId> subscribed, Set<ServiceId> stores, ServiceTable table) {
        Title k = ticked(t, stores);
        Offer o = Offers.bestOffer(k, subscribed);
        if (o == null) return k.offers.isEmpty() ? "Not available" : "Not on your services";
        if (Offers.isIncludedOffer(o, subscribed)) return Offers.offerLabel(o, subscribed, table);
        Set<ServiceId> sellers = EnumSet.noneOf(ServiceId.class);
        for (Offer x : k.offers) if (x.type == o.type) sellers.add(x.service);
        String verb = o.type == OfferType.RENT ? "Rent" : "Buy";
        Money min = cheapest(o);
        String from = min == null ? "" : " from " + min.format();
        String count = sellers.size() > 1 ? " · " + sellers.size() + " stores" : "";
        if (o.seasonal) return verb + " a season" + from + count;
        if (sellers.size() == 1) return table.storeName(o.service) + " · " + Offers.paid(o);
        return verb + from + count;
    }
}
