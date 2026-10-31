package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The offer rules, ported from design_preview/src/data/offers.ts. The prototype hard-codes which services are
 * subscribed; here the set is passed in ({@link Services#subscribed()}), so the rules stay pure and unit-tested.
 */
final class Offers {
    private Offers() {}

    /** "$3.99". The mock prices are in dollars, as in the prototype. */
    static String price(double n) {
        return String.format(Locale.US, "$%.2f", n);
    }

    /** Whether the offer has a price: every mock rent or buy offer, and a real one a price source priced. */
    static boolean isPriced(Offer o) {
        return o.money != null || (!o.real && o.price > 0);
    }

    /** The price as it reads: "$3.99" (mock), "39 kr", "from 49 kr"; null when it has none. */
    static String price(Offer o) {
        if (o.money != null) return o.from ? o.money.formatFrom() : o.money.format();
        return isPriced(o) ? price(o.price) : null;
    }

    /** The cheapest amount alone, without "from": "39 kr", "$3.99"; null when it has none. */
    static String amount(Offer o) {
        if (o.money != null) return o.money.format();
        return isPriced(o) ? price(o.price) : null;
    }

    /** "Rent 39 kr", "Buy from 99 kr", or just "Rent" for a store whose price isn't known. */
    static String paid(Offer o) {
        String verb = o.type == OfferType.RENT ? "Rent" : "Buy";
        String p = price(o);
        return p == null ? verb : verb + " " + p;
    }

    /** FREE, or SUB on a service the household has. */
    static boolean isIncludedOffer(Offer o, Set<ServiceId> subscribed) {
        return o != null && (o.type == OfferType.FREE || (o.type == OfferType.SUB && subscribed.contains(o.service)));
    }

    static boolean isIncluded(Title t, Set<ServiceId> subscribed) {
        for (Offer o : t.offers) if (isIncludedOffer(o, subscribed)) return true;
        return false;
    }

    /**
     * The offer the Watch button uses: the first included one, else the cheapest rental, else the cheapest
     * purchase (the first of equal prices); null when there is none of those.
     */
    static Offer bestOffer(Title t, Set<ServiceId> subscribed) {
        for (Offer o : t.offers) if (isIncludedOffer(o, subscribed)) return o;
        Offer rent = cheapest(t, OfferType.RENT);
        return rent != null ? rent : cheapest(t, OfferType.BUY);
    }

    /**
     * The cheapest offer of that type (the first of equal prices), or null. An offer with a price beats one without
     * (a store no source priced), which is only picked when no offer of the type has a price.
     */
    static Offer cheapest(Title t, OfferType type) {
        Offer best = null;
        for (Offer o : t.offers) {
            if (o.type != type) continue;
            if (best == null || (isPriced(o) && (!isPriced(best) || o.price < best.price))) best = o;
        }
        return best;
    }

    /**
     * "Prime Video · Included", "YouTube · Free", "Apple TV · Rent $3.99", "Not available" for none. A SUB offer on
     * a service the household doesn't have reads "Netflix · Subscription"; the prototype never shows one, because
     * there every service is subscribed and bestOffer skips such offers.
     */
    static String offerLabel(Offer o, Set<ServiceId> subscribed, ServiceTable table) {
        if (o == null) return "Not available";
        String name = table.name(o.service);
        switch (o.type) {
            case SUB:
                return name + " · " + (subscribed.contains(o.service) ? "Included" : "Subscription");
            case FREE:
                return name + " · Free";
            default:
                return table.storeName(o.service) + " · " + availability(o, subscribed);
        }
    }

    /**
     * The library card's second provider line: "Included", "Free", "Rent $3.99" or "Buy $9.99" ("Rent 39 kr", "Rent
     * from 49 kr", or "Rent" without a known price, for a real offer); "" for none and "Subscription" for SUB on a
     * service the household doesn't have.
     */
    static String availability(Offer o, Set<ServiceId> subscribed) {
        if (o == null) return "";
        switch (o.type) {
            case FREE:
                return "Free";
            case SUB:
                return subscribed.contains(o.service) ? "Included" : "Subscription";
            default:
                return paid(o);
        }
    }

    /**
     * One entry per service in the order the offers first name it, with its rent and buy merged (a later offer of the
     * same type replaces an earlier one, as in the prototype); included or free services first, otherwise in order.
     * The quality is the first offer's, "HD" when it has none; for real offers the first one any of the service's
     * offers has, "" when none has (TMDB gives no quality, only a price source does).
     */
    static List<ServiceOffers> byService(Title t, Set<ServiceId> subscribed) {
        Map<ServiceId, Builder> map = new LinkedHashMap<>();
        for (Offer o : t.offers) {
            Builder e = map.get(o.service);
            if (e == null) {
                e = new Builder(o.service, o.quality != null || o.real ? o.quality : "HD");
                map.put(o.service, e);
            }
            if (e.quality == null) e.quality = o.quality;
            if (o.type == OfferType.SUB && subscribed.contains(o.service)) e.included = true;
            if (o.type == OfferType.FREE) e.free = true;
            if (o.type == OfferType.RENT) e.rent = o;
            if (o.type == OfferType.BUY) e.buy = o;
        }
        List<ServiceOffers> out = new ArrayList<>(map.size());
        for (Builder e : map.values()) out.add(new ServiceOffers(e.service, e.included, e.free, e.rent, e.buy,
                e.quality != null ? e.quality : ""));
        // List.sort is stable, so services of equal rank keep the offers' order, as Array.prototype.sort does.
        out.sort((a, b) -> Boolean.compare(b.watchable(), a.watchable()));
        return Collections.unmodifiableList(out);
    }

    private static final class Builder {
        final ServiceId service;
        String quality;
        boolean included, free;
        Offer rent, buy;

        Builder(ServiceId service, String quality) {
            this.service = service;
            this.quality = quality;
        }
    }

    /** "1h 56m", or "52m" under an hour. */
    static String runtimeLabel(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }
}
