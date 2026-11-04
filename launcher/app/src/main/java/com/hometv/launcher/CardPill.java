package com.hometv.launcher;

/**
 * F46 (Rebecca, 2026-11-04): the service pill on a title's poster (design_preview ui/widgets.ts servicePill). The
 * service or store's name in words (09:40) after a round mark: a tick for Included or Free, or the region's currency
 * for rent or buy ("kr" in Sweden, "$" in the US). No price: grids never fetch prices; the title preview has them.
 * Pure Java, unit-tested (CardPillTest).
 */
final class CardPill {
    /** True for rent or buy: the mark is {@link #currency}; false for Included or Free: a tick. */
    final boolean paid;
    /** "kr" or "$" for a paid pill; empty otherwise. */
    final String currency;
    /** The service or store's name: "Netflix", "SF Anytime". */
    final String name;
    /** A free service (YouTube, a free tier): the tick, said as "free". */
    final boolean free;

    private CardPill(boolean paid, boolean free, String currency, String name) {
        this.paid = paid;
        this.free = free;
        this.currency = currency;
        this.name = name;
    }

    /**
     * The pill for the household's offer on a card ({@link Data#cardOffer}), named {@code name}; null with no offer or
     * no name to show.
     */
    static CardPill of(Offer o, String name, Region region) {
        if (o == null || name == null || name.isEmpty()) return null;
        boolean paid = PriceCards.isPaid(o);
        return new CardPill(paid, o.type == OfferType.FREE, paid ? currencyMark(region) : "", name);
    }

    /** "kr" for Sweden's kronor, "$" for US dollars; the ISO code for any other. */
    static String currencyMark(Region r) {
        Region region = r == null ? Region.DEFAULT : r;
        switch (region.currency) {
            case "SEK":
                return "kr";
            case "USD":
                return "$";
            default:
                return region.currency;
        }
    }

    /** What a screen reader says: "Netflix, included", "YouTube, free" or "SF Anytime, rent or buy". */
    String spoken() {
        return name + (paid ? ", rent or buy" : free ? ", free" : ", included");
    }
}
