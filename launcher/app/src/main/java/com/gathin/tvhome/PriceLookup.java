package com.gathin.tvhome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One title's price and link lookup, and when it is due again, as pure Java (PriceLookupTest), for {@link PriceBook}
 * to run on its thread. {@link #run} asks Wikidata for the services' ids and the iTunes ids, then the price sources in
 * {@link PriceCachePolicy}'s order: Watchmode, Movie of the Night when Watchmode had no price or can't be asked,
 * iTunes when neither gave an Apple TV Store price. With the keyed budgets spent the run keeps only what answered
 * (Apple's prices), so the other stores show Rent or Buy without an amount.
 *
 * <p>The ids and the prices are stamped apart ({@link Entry}): a lookup whose ids came (often from their month-long
 * disk cache) but whose prices failed is retried after {@link PriceCachePolicy#FAILURE_RETRY}, and prices the
 * background could not ask for because its half of a keyed budget was used up are asked again when the title's
 * preview opens. Prices are good for {@link PriceCachePolicy#PRICE_TTL} (7 days), or until the change feed marks the
 * title stale.
 */
final class PriceLookup {

    /** What one run found, and how each step went. */
    static final class Result {
        final WikidataIds ids;
        final boolean idsOk;
        final List<StorePrice> prices;
        /** Every price step that ran answered (a source may still have had nothing). */
        final boolean pricesOk;
        /** A keyed source was out of budget, and no other keyed source answered instead. */
        final boolean budgetBlocked;

        Result(WikidataIds ids, boolean idsOk, List<StorePrice> prices, boolean pricesOk, boolean budgetBlocked) {
            this.ids = ids;
            this.idsOk = idsOk;
            this.prices = Collections.unmodifiableList(new ArrayList<>(prices));
            this.pricesOk = pricesOk;
            this.budgetBlocked = budgetBlocked;
        }
    }

    /** What is known about one title: its ids and prices, when each last came in, and the last failure. */
    static final class Entry {
        final WikidataIds ids;
        final List<StorePrice> prices;
        /** When the ids came in, 0 for never. */
        final long idsAt;
        /** When the prices came in complete, 0 for never. */
        final long pricedAt;
        /** When the last lookup left something missing, 0 when it didn't. */
        final long failedAt;
        /** What was missing was only the background's share of a keyed budget: a preview may ask again at once. */
        final boolean listBlocked;

        Entry(WikidataIds ids, List<StorePrice> prices, long idsAt, long pricedAt, long failedAt, boolean listBlocked) {
            this.ids = ids;
            this.prices = Collections.unmodifiableList(new ArrayList<>(prices));
            this.idsAt = idsAt;
            this.pricedAt = pricedAt;
            this.failedAt = failedAt;
            this.listBlocked = listBlocked;
        }

        /**
         * The entry after a run at {@code now}: each part from the run when its step answered, else from {@code old}
         * (which may be null). Prices a failed run still found (Apple's, with Watchmode out of budget) are kept.
         */
        static Entry after(Entry old, Result r, boolean needPrices, PriceCachePolicy.Trigger trigger, long now) {
            WikidataIds ids = r.idsOk ? r.ids : old != null ? old.ids : WikidataIds.none();
            long idsAt = r.idsOk ? now : old != null ? old.idsAt : 0;
            boolean pricesOk = needPrices && r.pricesOk;
            long pricedAt = pricesOk ? now : old != null ? old.pricedAt : 0;
            // A spent budget leaves only what answered: older prices from that source are not shown as current.
            List<StorePrice> prices = needPrices && (pricesOk || r.budgetBlocked || !r.prices.isEmpty()) ? r.prices
                    : old != null ? old.prices : Collections.emptyList();
            boolean failed = !r.idsOk || (needPrices && !r.pricesOk);
            boolean listBlocked = needPrices && r.budgetBlocked && trigger == PriceCachePolicy.Trigger.BACKGROUND;
            return new Entry(ids, prices, idsAt, pricedAt, failed ? now : 0, listBlocked);
        }
    }

    /**
     * Whether a title needs a lookup at {@code now}: when its ids, or its prices if they are wanted, are missing,
     * older than {@link PriceCachePolicy#PRICE_TTL} or (prices) older than the change feed's stale mark
     * ({@code staleAt}, 0 for none), and no failure in the last {@link PriceCachePolicy#FAILURE_RETRY} holds it back. A
     * preview is held back only by a failure that wasn't the background's spent budget.
     */
    static boolean needsLookup(Entry e, boolean needPrices, boolean preview, long now, long staleAt) {
        if (e == null) return true;
        boolean idsFresh = PriceCachePolicy.fresh(e.idsAt, now, PriceCachePolicy.PRICE_TTL);
        boolean pricesFresh = !needPrices
                || (PriceCachePolicy.fresh(e.pricedAt, now, PriceCachePolicy.PRICE_TTL) && e.pricedAt > staleAt);
        if (idsFresh && pricesFresh) return false;
        if (PriceCachePolicy.fresh(e.failedAt, now, PriceCachePolicy.FAILURE_RETRY)) return preview && e.listBlocked;
        return true;
    }

    private final PriceSource watchmode, streaming, itunes;
    private final HttpGet wikidataHttp, watchmodeHttp, streamingHttp, itunesHttp;

    PriceLookup(HttpGet wikidataHttp, PriceSource watchmode, HttpGet watchmodeHttp, PriceSource streaming,
                HttpGet streamingHttp, PriceSource itunes, HttpGet itunesHttp) {
        this.wikidataHttp = wikidataHttp;
        this.watchmode = watchmode;
        this.watchmodeHttp = watchmodeHttp;
        this.streaming = streaming;
        this.streamingHttp = streamingHttp;
        this.itunes = itunes;
        this.itunesHttp = itunesHttp;
    }

    /** Blocking: the ids, then (with {@code needPrices}) the prices. Never throws. */
    Result run(Kind kind, int number, String name, int year, boolean needPrices) {
        WikidataIds ids = WikidataIds.none();
        boolean idsOk = false;
        try {
            ids = WikidataIds.fetch(kind, number, wikidataHttp);
            idsOk = true;
        } catch (IOException | RuntimeException e) {
            // No ids: no links, and iTunes falls back to its search.
        }
        List<StorePrice> prices = new ArrayList<>();
        boolean pricesOk = true, blocked = false;
        if (needPrices) {
            PriceTitle pt = new PriceTitle(number, kind, name, null, year, ids.itunesIds(kind));
            boolean keyedOk = false, keyedFailed = false;
            for (PriceSource s : new PriceSource[]{watchmode, streaming}) {
                // Movie of the Night only when Watchmode gave no price (or couldn't be asked).
                if (!prices.isEmpty() || !s.configured()) continue;
                try {
                    prices.addAll(s.prices(pt, s == watchmode ? watchmodeHttp : streamingHttp));
                    keyedOk = true;
                } catch (CachedHttp.OverBudget e) {
                    blocked = true;
                } catch (IOException | RuntimeException e) {
                    keyedFailed = true;
                }
            }
            if (keyedOk) blocked = false;
            pricesOk = keyedOk || (!blocked && !keyedFailed);
            if (PriceCachePolicy.needsItunes(prices)) {
                try {
                    prices.addAll(itunes.prices(pt, itunesHttp));
                } catch (IOException | RuntimeException e) {
                    pricesOk = false;
                }
            }
        }
        return new Result(ids, idsOk, prices, pricesOk, blocked);
    }
}
