package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each TMDB title's prices and the services' own ids for it (BACKLOG.md, Streaming availability and prices; Title
 * deep links), looked up when a title preview opens, or in the background for the household's titles
 * ({@link TmdbCatalog} asks), and only while Home is showing ({@link #setActive}). One low-priority thread runs a
 * {@link PriceLookup} per title, previews first ({@link PriceQueue}: a preview moves its title's waiting background
 * lookup ahead), and once a day Movie of the Night's change feed ({@link ChangeFeed}), whose stale marks make a title
 * due again early and its lookup skip the kept answers. Answers are kept on disk ({@link CachedHttp}): prices 7 days,
 * the ids a month, and stale ones offline. When a title is due again is {@link PriceLookup#needsLookup}. What it found
 * lives here, on the main thread; {@link TmdbCatalog} merges it into the title's offers.
 */
final class PriceBook {
    /** Hears, on the main thread, that a title's prices or ids came in. */
    interface Listener {
        void priced(String id);
    }

    /** Answers on disk: a few thousand small JSON files at most. */
    private static final long DISK_MAX = 4L << 20;
    /** The change feed's job in the queue. */
    private static final String FEED = "changes";
    private static final String PREF_MARKS = "stale_marks", PREF_FEED_AT = "feed_read_at";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final PriceQueue queue = new PriceQueue();
    private final Listener listener;
    private final Runnable onStale;
    private final SharedPreferences prefs;
    private final PriceLookup lookup;
    private final CachedHttp[] https;
    private final CachedHttp motnHttp;
    private final Map<String, String> motnHeaders;
    private final boolean feedOn;
    private final Map<String, PriceLookup.Entry> entries = new HashMap<>();
    private final ChangeFeed.Marks marks;
    /** Titles queued or being looked up (main thread). */
    private final Set<String> inFlight = new HashSet<>();
    private final Map<String, List<Runnable>> waiting = new HashMap<>();
    private volatile boolean active = true;
    private Thread worker;

    /**
     * {@code onStale} runs on the main thread when the change feed marked titles stale, so the household's are asked
     * again.
     */
    PriceBook(Context ctx, String watchmodeKey, String motnKey, Listener listener, Runnable onStale) {
        this.listener = listener;
        this.onStale = onStale;
        Context app = ctx.getApplicationContext();
        DiskCache disk = new DiskCache(new File(app.getCacheDir(), "prices"), DISK_MAX, System::currentTimeMillis);
        prefs = app.getSharedPreferences("prices", Context.MODE_PRIVATE);
        CachedHttp.LedgerStore ledgers = new CachedHttp.LedgerStore() {
            @Override
            public String load(String sourceId) {
                return prefs.getString("quota_" + sourceId, null);
            }

            @Override
            public void save(String sourceId, String encoded) {
                prefs.edit().putString("quota_" + sourceId, encoded).apply();
            }
        };
        marks = ChangeFeed.Marks.decode(prefs.getString(PREF_MARKS, null));
        marks.expire(System.currentTimeMillis());
        ZoneId zone = ZoneId.systemDefault();
        CachedHttp wikidataHttp = http(WikidataIds.ID, disk, PriceCachePolicy.DEEP_LINK_TTL, ledgers, zone);
        CachedHttp itunesHttp = http(ItunesPrices.ID, disk, PriceCachePolicy.PRICE_TTL, ledgers, zone);
        CachedHttp watchmodeHttp = http(WatchmodePrices.ID, disk, PriceCachePolicy.PRICE_TTL, ledgers, zone);
        motnHttp = http(StreamingAvailabilityPrices.ID, disk, PriceCachePolicy.PRICE_TTL, ledgers, zone);
        https = new CachedHttp[]{wikidataHttp, itunesHttp, watchmodeHttp, motnHttp};
        StreamingAvailabilityPrices motn = new StreamingAvailabilityPrices(motnKey);
        motnHeaders = motn.headers();
        feedOn = motn.configured();
        lookup = new PriceLookup(wikidataHttp, new WatchmodePrices(watchmodeKey), watchmodeHttp, motn, motnHttp,
                new ItunesPrices(), itunesHttp);
    }

    private static CachedHttp http(String id, DiskCache disk, long ttl, CachedHttp.LedgerStore ledgers, ZoneId zone) {
        return new CachedHttp(id, disk, ttl, PriceCachePolicy.minIntervalMs(id), CachedHttp.NET, ledgers,
                System::currentTimeMillis, zone);
    }

    /** What is known for a title, or null. */
    PriceLookup.Entry get(String id) {
        return entries.get(id);
    }

    /**
     * Home is showing or not: nothing new starts while it is hidden. Showing again reads the change feed when a day
     * has passed since it was last read.
     */
    void setActive(boolean active) {
        this.active = active;
        if (active && feedOn && ChangeFeed.due(prefs.getLong(PREF_FEED_AT, 0), System.currentTimeMillis())
                && !inFlight.contains(FEED)) {
            inFlight.add(FEED);
            queue.add(FEED, null, null, 0, false, PriceCachePolicy.Trigger.BACKGROUND);
            startWorker();
        }
    }

    /**
     * Asks for t's ids and, with {@code needPrices}, its prices, unless {@link PriceLookup#needsLookup} says what is
     * known will do. {@code done} runs on the main thread once they are in (at once when no lookup is due); never
     * when the lookup doesn't start. A preview asking for a title whose background lookup is still waiting moves it
     * ahead.
     */
    void want(Title t, boolean preview, boolean needPrices, Runnable done) {
        Kind kind = TmdbApi.kindOf(t.id);
        if (kind == null) return;
        long now = System.currentTimeMillis();
        if (!PriceLookup.needsLookup(entries.get(t.id), needPrices, preview, now, marks.staleAt(t.id))) {
            if (done != null) done.run();
            return;
        }
        if (!active) return;
        if (done != null) {
            List<Runnable> list = waiting.get(t.id);
            if (list == null) waiting.put(t.id, list = new ArrayList<>());
            list.add(done);
        }
        if (inFlight.contains(t.id)) {
            if (preview) queue.boost(t.id, needPrices);
            return;
        }
        inFlight.add(t.id);
        queue.add(t.id, kind, t.name, t.year, needPrices,
                preview ? PriceCachePolicy.Trigger.PREVIEW : PriceCachePolicy.Trigger.BACKGROUND);
        startWorker();
    }

    private void startWorker() {
        if (worker != null) return;
        worker = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            while (true) {
                PriceQueue.Job job;
                try {
                    job = queue.take();
                } catch (InterruptedException e) {
                    return;
                }
                if (FEED.equals(job.id)) readFeed();
                else run(job);
            }
        }, "prices");
        worker.setDaemon(true);
        worker.start();
    }

    /** On the price thread. */
    private void run(PriceQueue.Job job) {
        String id = job.id;
        if (!active) {
            // Home went away while it waited: drop it; the screen asks again when it shows.
            main.post(() -> {
                inFlight.remove(id);
                waiting.remove(id);
            });
            return;
        }
        boolean stale = marks.staleAt(id) > 0;
        for (CachedHttp h : https) {
            h.trigger = job.trigger;
            // A title the feed marked stale skips its kept prices (not its Wikidata ids).
            h.refresh = stale && h != https[0];
        }
        PriceLookup.Result r = lookup.run(job.kind, TmdbApi.number(id), job.name, job.year, job.needPrices);
        for (CachedHttp h : https) h.refresh = false;
        long now = System.currentTimeMillis();
        main.post(() -> {
            inFlight.remove(id);
            PriceLookup.Entry e = PriceLookup.Entry.after(entries.get(id), r, job.needPrices, job.trigger, now);
            entries.put(id, e);
            if (e.pricedAt == now && marks.staleAt(id) > 0) {
                marks.clear(id);
                prefs.edit().putString(PREF_MARKS, marks.encode()).apply();
            }
            listener.priced(id);
            List<Runnable> list = waiting.remove(id);
            if (list != null) for (Runnable done : list) done.run();
        });
    }

    /** On the price thread: the change feed since it was last read, a few pages at most. */
    private void readFeed() {
        long now = System.currentTimeMillis();
        long last = prefs.getLong(PREF_FEED_AT, 0);
        Set<String> changed = new LinkedHashSet<>();
        boolean read = false;
        if (active) {
            motnHttp.trigger = PriceCachePolicy.Trigger.BACKGROUND;
            String cursor = null;
            try {
                for (int page = 0; page < ChangeFeed.MAX_PAGES; page++) {
                    ChangeFeed.Page p = ChangeFeed.parse(motnHttp.get(ChangeFeed.url(last, now, cursor), motnHeaders));
                    changed.addAll(p.ids);
                    read = true;
                    cursor = p.nextCursor;
                    if (cursor == null) break;
                }
            } catch (IOException | JSONException | RuntimeException e) {
                // Offline or out of budget: tried again when Home next shows.
            }
        }
        boolean wasRead = read;
        main.post(() -> {
            inFlight.remove(FEED);
            if (!wasRead) return;
            marks.mark(changed, now);
            marks.expire(now);
            prefs.edit().putString(PREF_MARKS, marks.encode()).putLong(PREF_FEED_AT, now).apply();
            if (!changed.isEmpty()) onStale.run();
        });
    }
}
