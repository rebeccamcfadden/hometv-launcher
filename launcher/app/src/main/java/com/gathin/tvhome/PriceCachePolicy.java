package com.gathin.tvhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * When to ask the price sources and Wikidata again, and how much of each keyed source's quota may be spent, as pure
 * logic (PriceCachePolicyTest). The project lead's policy (2026-10-30), with no paid plans ever:
 *
 * <ul>
 *   <li>Prices are kept 7 days, a deep link a month; after a failure (offline, a 5xx) a title waits an hour.</li>
 *   <li>Titles on the household's lists are refreshed in the background, weekly, within budget; any other title only
 *       when its preview opens. Grids and search never ask. Movie of the Night's change feed, read at most once a day
 *       while Home shows, marks a kept title stale early ({@link ChangeFeed}).</li>
 *   <li>The order: Watchmode (most Swedish stores), Movie of the Night only when Watchmode had no price or its budget
 *       is spent, iTunes for the Apple TV Store when neither priced it. With a keyed budget spent, only Apple is
 *       priced, and the other stores show Rent or Buy without an amount.</li>
 *   <li>Each keyed source follows its server's own count ({@link Quota}, synced from every reply's headers) and stops
 *       short of the quota: Watchmode at 2,300 credits of 2,500, Movie of the Night at 900 requests of 1,000. What is
 *       left is spread over the days to the server's reset; background work may use half of a day's share, an opened
 *       preview all of it.</li>
 * </ul>
 */
final class PriceCachePolicy {
    private PriceCachePolicy() {}

    static final long HOUR = 3_600_000L;
    static final long DAY = 24 * HOUR;
    static final long PRICE_TTL = 7 * DAY;
    static final long DEEP_LINK_TTL = 30 * DAY;
    static final long FAILURE_RETRY = HOUR;
    /** How often the household's titles are checked for prices due while Home shows (cheap: memory only). */
    static final long KEEP_FRESH_EVERY = 6 * HOUR;

    /** Why a lookup is wanted: an opened preview, or the background (household lists, the change feed). */
    enum Trigger { PREVIEW, BACKGROUND }

    /** Where each keyed source stops, below its free quota: no paid plans, ever. */
    static final int WATCHMODE_STOP = 2300, WATCHMODE_MARGIN = 200;
    static final int MOTN_STOP = 900, MOTN_MARGIN = 100;

    /**
     * Where a source stops spending this period: the fixed stop, lowered when the server grants less (its quota minus
     * the margin); 0 for a keyless source, which has no budget.
     */
    static int stopAt(String sourceId, int granted) {
        switch (sourceId) {
            case WatchmodePrices.ID:
                return granted > 0 ? Math.max(0, Math.min(WATCHMODE_STOP, granted - WATCHMODE_MARGIN)) : WATCHMODE_STOP;
            case StreamingAvailabilityPrices.ID:
                return granted > 0 ? Math.max(0, Math.min(MOTN_STOP, granted - MOTN_MARGIN)) : MOTN_STOP;
            default:
                return 0;
        }
    }

    /** What one request costs against a source's budget: Watchmode charges 2 credits for a TMDB-id lookup. */
    static int requestCost(String sourceId) {
        return WatchmodePrices.ID.equals(sourceId) ? 2 : 1;
    }

    /** The least time between two requests to a keyless source: Apple asks for at most about 20 a minute. */
    static long minIntervalMs(String sourceId) {
        return ItunesPrices.ID.equals(sourceId) ? 3_000L : WikidataIds.ID.equals(sourceId) ? 1_000L : 0L;
    }

    /** Whether an answer fetched at {@code fetchedAt} (0 for never) is still good at {@code now}. */
    static boolean fresh(long fetchedAt, long now, long ttl) {
        return fetchedAt > 0 && now >= fetchedAt && now - fetchedAt < ttl;
    }

    /** Whether iTunes is still worth asking after the keyed sources answered. */
    static boolean needsItunes(List<StorePrice> found) {
        for (StorePrice p : found) if (ItunesPrices.STORE.equals(p.store)) return false;
        return true;
    }

    /**
     * One keyed source's quota as its server last told it, plus what was spent since: the server's monthly grant, the
     * count used, when the count goes back to 0, and today's spending for the daily allowance. Synced from the
     * headers of every reply ({@link #sync}), so a reinstall that forgot the count can't overspend once the server
     * has answered. Movie of the Night says when it resets (X-Quota-Reset, the 1st of the month); Watchmode doesn't, so
     * its period is taken to run a month from first use and is corrected whenever its count drops. Kept as a short
     * string ({@link #encode}) in the app's preferences. Mutable and not thread-safe: its owner serialises access.
     */
    static final class Quota {
        final String sourceId;
        int granted;
        int used;
        /** When the server's count resets (epoch ms), 0 when not known yet. */
        long resetAt;
        /** When a reply's headers last told the count, 0 for never. */
        long syncedAt;
        LocalDate day;
        int dayUsed;

        Quota(String sourceId) {
            this.sourceId = sourceId;
        }

        /** "granted|used|resetAt|syncedAt|day|dayUsed". */
        String encode() {
            return granted + "|" + used + "|" + resetAt + "|" + syncedAt + "|" + (day == null ? "" : day) + "|" + dayUsed;
        }

        /** A quota from {@link #encode}, or a fresh one when the text is missing or broken. */
        static Quota decode(String sourceId, String s) {
            Quota q = new Quota(sourceId);
            if (s == null) return q;
            try {
                String[] p = s.split("\\|", -1);
                q.granted = Integer.parseInt(p[0]);
                q.used = Integer.parseInt(p[1]);
                q.resetAt = Long.parseLong(p[2]);
                q.syncedAt = Long.parseLong(p[3]);
                q.day = p[4].isEmpty() ? null : LocalDate.parse(p[4]);
                q.dayUsed = Integer.parseInt(p[5]);
                return q;
            } catch (RuntimeException e) {
                return new Quota(sourceId);
            }
        }

        /** A new period once the reset has passed, and a new day once the date has moved on. */
        void roll(long now, ZoneId zone) {
            if (resetAt > 0 && now >= resetAt) {
                used = 0;
                resetAt = nextReset(resetAt, now);
            }
            LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            if (!today.equals(day)) {
                day = today;
                dayUsed = 0;
            }
        }

        /** Counts a request sent now; the server's count, when its reply says it, then replaces {@link #used}. */
        void record(int cost, long now, ZoneId zone) {
            roll(now, zone);
            if (resetAt == 0 && WatchmodePrices.ID.equals(sourceId)) resetAt = monthLater(now);
            used += cost;
            dayUsed += cost;
        }

        /**
         * Takes the server's count from a reply's headers (names in any case): Watchmode's X-Account-Quota and
         * X-Account-Quota-Used, Movie of the Night's X-Quota-Granted, X-Quota-Used and X-Quota-Reset. A Watchmode count
         * that dropped means its period started again: the next reset is a month from now.
         */
        void sync(Map<String, String> headers, long now) {
            if (headers == null) return;
            Integer g, u;
            if (WatchmodePrices.ID.equals(sourceId)) {
                g = number(headers, "x-account-quota");
                u = number(headers, "x-account-quota-used");
                if (u != null && (u < used - 2 || resetAt == 0)) resetAt = monthLater(now);
            } else {
                g = number(headers, "x-quota-granted");
                u = number(headers, "x-quota-used");
                long reset = instant(header(headers, "x-quota-reset"));
                if (reset > 0) resetAt = reset;
            }
            if (g != null) granted = g;
            if (u != null) {
                used = u;
                syncedAt = now;
            }
        }

        /** Where this source stops spending this period. */
        int stop() {
            return stopAt(sourceId, granted);
        }

        /** Today's share of what is left below the stop, over the days left to the reset (today included). */
        int dailyAllowance(long now, ZoneId zone) {
            roll(now, zone);
            long end = resetAt > 0 ? resetAt : endOfMonth(now, zone);
            long daysLeft = Math.max(1, (end - now + DAY - 1) / DAY);
            int leftBeforeToday = stop() - (used - dayUsed);
            return (int) Math.max(0, leftBeforeToday / daysLeft);
        }

        /**
         * Whether a request costing {@code cost} may go out now: a keyless source always; a keyed one only below its
         * stop, and within today's allowance (half of it for background work).
         */
        boolean allowed(int cost, Trigger trigger, long now, ZoneId zone) {
            if (!isKeyed()) return true;
            int stop = stop();
            int allowance = dailyAllowance(now, zone);
            if (used + cost > stop) return false;
            int cap = trigger == Trigger.BACKGROUND ? allowance / 2 : allowance;
            return dayUsed + cost <= cap;
        }

        boolean isKeyed() {
            return WatchmodePrices.ID.equals(sourceId) || StreamingAvailabilityPrices.ID.equals(sourceId);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s %d/%d (stop %d) today %d", sourceId, used, granted, stop(), dayUsed);
        }
    }

    private static long monthLater(long now) {
        return Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).plusMonths(1).toInstant().toEpochMilli();
    }

    /** The reset after one that has passed: a month on, as often as needed to be after now. */
    private static long nextReset(long reset, long now) {
        ZonedDateTime r = Instant.ofEpochMilli(reset).atZone(ZoneOffset.UTC);
        while (r.toInstant().toEpochMilli() <= now) r = r.plusMonths(1);
        return r.toInstant().toEpochMilli();
    }

    private static long endOfMonth(long now, ZoneId zone) {
        LocalDate d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return d.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    private static Integer number(Map<String, String> headers, String name) {
        String v = header(headers, name);
        if (v == null) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "2026-11-01T00:00:00Z", or seconds since 1970, as epoch ms; 0 for anything else. */
    private static long instant(String v) {
        if (v == null) return 0;
        try {
            return Instant.parse(v.trim()).toEpochMilli();
        } catch (DateTimeParseException e) {
            try {
                return Long.parseLong(v.trim()) * 1000L;
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }
}
