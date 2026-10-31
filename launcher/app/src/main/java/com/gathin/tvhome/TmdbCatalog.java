package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The real catalogue, from TMDB (BACKLOG.md, Real catalogue; {@link Catalog} says how it answers). It starts from
 * the mock catalogue's titles on TMDB ({@link MockIds}), as stubs with a name and a year, and fills them in from the
 * disk cache or the network a couple of seconds after Home is up, never on the main thread and never holding Home
 * up. Titles met on the way (search hits, recommendations, a person's titles) join what it knows. A detailed title's
 * offers are where TMDB says it can be watched in Sweden, with the prices {@link PriceBook} finds on demand merged in
 * ({@link OfferMerge}); the imported watchlists stay mock ({@link MockOverlay}).
 *
 * <p>Everything it knows lives on the main thread; three low-priority threads read the cache and the network, at
 * most three requests at a time, what a screen waits for first. A request that fails is not tried again for a
 * minute, so an offline TV doesn't spin. Small answers (similar titles, people, searches, genres) are kept in memory
 * too, a few dozen at most.
 */
final class TmdbCatalog implements Catalog {
    private static final int THREADS = 3;
    private static final long RETRY_MS = 60_000;
    private static final long NOTIFY_MS = 150;
    private static final long START_DELAY_MS = 2_000;
    private static final int GENRE_TITLES = 40;

    private final TmdbClient client;
    private final MockOverlay overlay;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor pool;
    private final AtomicLong order = new AtomicLong();

    private final LinkedHashMap<String, Title> known = new LinkedHashMap<>();
    private List<Title> snapshot;
    private final Set<String> loading = new HashSet<>();
    private final Set<String> urgent = new HashSet<>();
    private final Map<String, Long> failedAt = new HashMap<>();
    /** When each detailed title's copy was fetched from TMDB (06-states A: "as saved today 19:52"). */
    private final Map<String, Long> savedAt = new HashMap<>();
    private final Map<String, List<Consumer<Title>>> waiting = new HashMap<>();
    private final List<WeakReference<Runnable>> listeners = new ArrayList<>();
    private boolean notifyPosted;
    /** Until a couple of seconds after the start, background loads wait here (see the constructor). */
    private boolean started;
    private final Set<String> deferred = new java.util.LinkedHashSet<>();

    private final Map<String, List<Title>> similar = lru(24);
    private final Map<Integer, Credits> people = lru(12);
    private final Map<String, Found> searches = lru(24);
    private final Map<String, List<Title>> genres = lru(8);

    /** V2 Phase 2: each detailed title's availability in Sweden, its prices and links, and their sources. */
    private final Map<String, ProviderAvailability> availability = new HashMap<>();
    private final Map<String, Set<String>> priceSources = new HashMap<>();
    private final PriceBook book;
    /** My list, whose titles (with the Household and Friends lists') are kept priced in the background. */
    private final Set<String> myList = new HashSet<>();
    private boolean visible = true;
    /** Off until a TMDB account is signed in: see {@link Catalog#setListPricing}. */
    private boolean listPricing;
    /** While Home shows, the household's titles are checked for prices due every few hours (memory only). */
    private final Runnable keepFresh = new Runnable() {
        @Override
        public void run() {
            refreshKept();
            main.postDelayed(this, PriceCachePolicy.KEEP_FRESH_EVERY);
        }
    };

    TmdbCatalog(Context ctx, String token) {
        client = new TmdbClient(token, ctx.getApplicationContext().getCacheDir());
        overlay = new MockOverlay(new MockCatalog());
        book = new PriceBook(ctx, PriceKeys.watchmode(), PriceKeys.movieOfTheNight(), this::repriced, this::refreshKept);
        pool = new ThreadPoolExecutor(THREADS, THREADS, 10, TimeUnit.SECONDS, new PriorityBlockingQueue<>(), r -> {
            Thread t = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, "tmdb");
            t.setDaemon(true);
            return t;
        });
        pool.allowCoreThreadTimeOut(true);
        for (MockIds.Entry e : MockIds.all().values()) {
            Title.Draft d = new Title.Draft(e.id, TmdbApi.kindOf(e.id), e.name);
            d.year = e.year;
            known.put(e.id, overlay.apply(d.build()));
        }
        Art.init(client);
        // The lists' details load once Home has drawn, so a cold start doesn't share the CPU with them.
        main.postDelayed(() -> {
            started = true;
            run(Job.LOW, client::prune);
            List<String> ids = new ArrayList<>(known.keySet());
            ids.addAll(deferred);
            deferred.clear();
            want(ids);
        }, START_DELAY_MS);
    }

    // ---- What it knows --------------------------------------------------------------------------------------------

    @Override
    public List<Title> all() {
        if (snapshot == null) snapshot = Collections.unmodifiableList(new ArrayList<>(known.values()));
        return snapshot;
    }

    @Override
    public Title get(String id) {
        return id == null ? null : known.get(id);
    }

    @Override
    public List<String> genres() {
        return GenreMap.all();
    }

    @Override
    public List<String> genres(Kind kind) {
        return GenreMap.all(kind);
    }

    @Override
    public String fromMockId(String mockId) {
        return MockIds.migrate(mockId);
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public void addListener(Runnable l) {
        listeners.add(new WeakReference<>(l));
    }

    // ---- Details ----------------------------------------------------------------------------------------------------

    @Override
    public void want(Collection<String> ids) {
        if (!started) {
            deferred.addAll(ids);
            return;
        }
        for (String id : ids) load(id, false);
    }

    @Override
    public void retryFailed() {
        if (!started || failedAt.isEmpty()) return;
        List<String> ids = new ArrayList<>(failedAt.keySet());
        failedAt.clear();
        for (String id : ids) load(id, false);
        // 06-states A: the screens that said "offline" or showed an error show their skeletons while these load.
        changed();
    }

    @Override
    public boolean pending(String id) {
        if (id == null || TmdbApi.kindOf(id) == null || failedAt.containsKey(id)) return false;
        Title k = known.get(id);
        if (k != null && k.detailed) return false;
        // Before the start everything the lists hold waits its turn (see the constructor).
        return !started || loading.contains(id);
    }

    @Override
    public boolean failed(String id) {
        if (id == null || !failedAt.containsKey(id)) return false;
        Title k = known.get(id);
        return k == null || !k.detailed;
    }

    @Override
    public long savedAt(String id) {
        Long at = id == null ? null : savedAt.get(id);
        return at == null ? 0 : at;
    }

    @Override
    public void details(Title t, Consumer<Title> done) {
        Title k = known.get(t.id);
        if (k == null) k = upsert(t);
        if (k.detailed) {
            done.accept(k);
            return;
        }
        List<Consumer<Title>> list = waiting.get(t.id);
        if (list == null) waiting.put(t.id, list = new ArrayList<>());
        list.add(done);
        // Asked for now (a preview opening, or its Retry): it is loading again rather than failed (06-states A).
        failedAt.remove(t.id);
        load(t.id, true);
    }

    private void load(String id, boolean now) {
        Kind kind = TmdbApi.kindOf(id);
        if (kind == null) return;
        Title k = known.get(id);
        if (k != null && k.detailed) return;
        Long failed = failedAt.get(id);
        if (!now && failed != null && SystemClock.elapsedRealtime() - failed < RETRY_MS) return;
        if (now ? !urgent.add(id) : loading.contains(id)) return;
        loading.add(id);
        run(now ? Job.HIGH : Job.LOW, () -> {
            Title t = null;
            ProviderAvailability a = null;
            long at = 0;
            try {
                String path = TmdbApi.details(id);
                JSONObject o = client.get(path);
                t = TmdbParse.details(o, kind);
                a = TmdbParse.availability(o);
                at = client.savedAt(path);
            } catch (IOException | RuntimeException e) {
                // Offline with nothing cached, or an odd answer: the stub stays, and the screen keeps what it shows.
            }
            Title result = t;
            ProviderAvailability where = a;
            long saved = at;
            main.post(() -> loaded(id, result, where, saved));
        });
    }

    private void loaded(String id, Title t, ProviderAvailability where, long saved) {
        loading.remove(id);
        urgent.remove(id);
        List<Consumer<Title>> list = waiting.remove(id);
        if (t == null) {
            failedAt.put(id, SystemClock.elapsedRealtime());
            // The screens hear, so a skeleton waiting on this title turns into the offline or error state.
            changed();
            return;
        }
        failedAt.remove(id);
        if (saved > 0) savedAt.put(id, saved);
        if (where != null) availability.put(id, where);
        Title k = upsert(t);
        if (isKept(k)) wantPrices(k, false, null);
        if (list != null) for (Consumer<Title> done : list) done.accept(k);
    }

    @Override
    public void season(Title t, int n, Consumer<Title> done) {
        Season have = t.season(n);
        if (have != null && !have.needsEpisodes()) {
            done.accept(t);
            return;
        }
        String id = t.id;
        int fallback = t.episodeRuntime;
        run(Job.HIGH, () -> {
            Season s = null;
            try {
                s = TmdbParse.season(client.get(TmdbApi.season(id, n)), fallback);
            } catch (IOException | RuntimeException e) {
                // Nothing to show yet; the season stays empty.
            }
            Season result = s;
            main.post(() -> {
                Title cur = known.get(id);
                if (result == null || cur == null || result.episodes.isEmpty()) return;
                Title.Draft d = new Title.Draft(cur);
                List<Season> seasons = new ArrayList<>(cur.seasons);
                boolean replaced = false;
                for (int i = 0; i < seasons.size(); i++) {
                    if (seasons.get(i).n == n) {
                        seasons.set(i, result);
                        replaced = true;
                    }
                }
                if (!replaced) return;
                d.seasons = seasons;
                known.put(id, d.build());
                changed();
                done.accept(known.get(id));
            });
        });
    }

    // ---- Prices and links (V2 Phase 2) ------------------------------------------------------------------------------

    /** Only a preview asks for one title: grids and search never do (the household's titles are kept fresh). */
    @Override
    public void prices(Title t, boolean preview, Consumer<Title> done) {
        Title k = known.get(t.id);
        if (preview && k != null) wantPrices(k, true, done);
    }

    @Override
    public void keepPriced(Collection<String> myListIds) {
        myList.clear();
        myList.addAll(myListIds);
        refreshKept();
    }

    /** On My list, or on the Household or Friends list, while list pricing is on. Seen and the rest are not kept. */
    private boolean isKept(Title t) {
        if (!listPricing) return false;
        return myList.contains(t.id) || t.isOn(ListId.HOUSEHOLD) || t.isOn(ListId.FRIENDS);
    }

    @Override
    public void setListPricing(boolean on) {
        if (listPricing == on) return;
        listPricing = on;
        refreshKept();
    }

    /** The household's titles whose prices are due, in the background (PriceBook skips the fresh ones). */
    private void refreshKept() {
        if (!visible || !started || !listPricing) return;
        for (Title t : all()) if (isKept(t)) wantPrices(t, false, null);
    }

    /** Asks the price book: a preview for the links and any prices, the background only for titles that sell. */
    private void wantPrices(Title k, boolean preview, Consumer<Title> done) {
        if (!k.detailed || !availability.containsKey(k.id)) return;
        boolean stores = false;
        for (Offer o : k.offers) stores |= o.type == OfferType.RENT || o.type == OfferType.BUY;
        if (!preview && !stores) return;
        String id = k.id;
        book.want(k, preview, stores, done == null ? null : () -> done.accept(known.get(id)));
    }

    @Override
    public String link(Title t, ServiceId service) {
        PriceLookup.Entry e = book.get(t.id);
        return e == null ? null : DeepLinks.link(service.id, t.kind, e.ids, t.name);
    }

    @Override
    public String attribution(Title t) {
        if (!availability.containsKey(t.id)) return null;
        Set<String> sources = priceSources.get(t.id);
        return OfferMerge.attribution(true, sources == null ? Collections.emptySet() : sources);
    }

    @Override
    public long pricesSavedAt(Title t) {
        return book.savedAt(t.id);
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        book.setActive(visible);
        main.removeCallbacks(keepFresh);
        if (visible) keepFresh.run();
    }

    /** t's offers: its availability with what the price book found merged in. */
    private Title priced(Title t) {
        ProviderAvailability a = availability.get(t.id);
        if (a == null) return t;
        PriceLookup.Entry e = book.get(t.id);
        OfferMerge.Result r = OfferMerge.merge(a, e == null ? Collections.emptyList() : e.prices, WatchProviders.SE);
        priceSources.put(t.id, r.priceSources);
        Title.Draft d = new Title.Draft(t);
        d.offers = r.toOffers();
        return d.build();
    }

    /** The price book found something for a title: its offers again, and the screens hear. */
    private void repriced(String id) {
        Title k = known.get(id);
        if (k == null || !k.detailed) return;
        known.put(id, priced(k));
        changed();
    }

    // ---- Lists of titles --------------------------------------------------------------------------------------------

    @Override
    public void similar(Title t, Consumer<List<Title>> done) {
        List<Title> hit = similar.get(t.id);
        if (hit != null) {
            done.accept(resolve(hit));
            return;
        }
        Kind kind = TmdbApi.kindOf(t.id);
        if (kind == null) return;
        String id = t.id;
        fetch(TmdbApi.details(id), o -> TmdbParse.similar(o, kind), list -> {
            List<Title> stored = upsertAll(list);
            similar.put(id, stored);
            done.accept(stored);
        });
    }

    @Override
    public void person(Person p, Consumer<Credits> done) {
        if (p.id <= 0) {
            Catalog.super.person(p, done);
            return;
        }
        Credits hit = people.get(p.id);
        if (hit != null) {
            done.accept(new Credits(hit.person, resolve(hit.titles)));
            return;
        }
        fetch(TmdbApi.person(p.id), o -> TmdbParse.credits(o, p), c -> {
            Credits stored = new Credits(c.person, upsertAll(c.titles));
            people.put(p.id, stored);
            done.accept(stored);
        });
    }

    @Override
    public void search(String query, Consumer<Found> done) {
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.length() < 2) return;
        Found hit = searches.get(q);
        if (hit != null) {
            done.accept(hit);
            return;
        }
        fetch(TmdbApi.search(q), TmdbParse::search, f -> {
            Map<Integer, List<Title>> knownFor = new HashMap<>();
            for (Person p : f.people) knownFor.put(p.id, upsertAll(f.knownFor(p)));
            Found stored = new Found(upsertAll(f.titles), f.people, knownFor);
            searches.put(q, stored);
            done.accept(stored);
        });
    }

    @Override
    public void genre(String genre, Consumer<List<Title>> done) {
        List<Title> hit = genres.get(genre);
        if (hit != null) {
            done.accept(resolve(hit));
            return;
        }
        int movie = GenreMap.idFor(Kind.MOVIE, genre), tv = GenreMap.idFor(Kind.SHOW, genre);
        if (movie == 0 && tv == 0) return;
        run(Job.HIGH, () -> {
            List<Title> movies = Collections.emptyList(), shows = Collections.emptyList();
            boolean any = false;
            try {
                if (movie != 0) {
                    movies = TmdbParse.summaries(client.get(TmdbApi.discover(Kind.MOVIE, movie)).optJSONArray("results"),
                            Kind.MOVIE);
                    any = true;
                }
            } catch (IOException | RuntimeException ignored) {
                // The shows may still come.
            }
            try {
                if (tv != 0) {
                    shows = TmdbParse.summaries(client.get(TmdbApi.discover(Kind.SHOW, tv)).optJSONArray("results"),
                            Kind.SHOW);
                    any = true;
                }
            } catch (IOException | RuntimeException ignored) {
                // The movies may have come.
            }
            if (!any) return;
            // Both lists are most popular first: taking them in turns keeps that order across the two.
            List<Title> mixed = new ArrayList<>();
            for (int i = 0; mixed.size() < GENRE_TITLES && (i < movies.size() || i < shows.size()); i++) {
                if (i < movies.size()) mixed.add(movies.get(i));
                if (i < shows.size() && mixed.size() < GENRE_TITLES) mixed.add(shows.get(i));
            }
            main.post(() -> {
                List<Title> stored = upsertAll(mixed);
                genres.put(genre, stored);
                done.accept(stored);
            });
        });
    }

    // ---- Plumbing ---------------------------------------------------------------------------------------------------

    private interface Parse<T> {
        T parse(JSONObject o);
    }

    /** Gets path in the background, parses it there, and hands the result over on the main thread. */
    private <T> void fetch(String path, Parse<T> parse, Consumer<T> done) {
        run(Job.HIGH, () -> {
            T result;
            try {
                result = parse.parse(client.get(path));
            } catch (IOException | RuntimeException e) {
                return;
            }
            main.post(() -> done.accept(result));
        });
    }

    /**
     * Puts what TMDB said about a title among what is known, with its mock lists and its offers priced, and returns
     * the copy kept. A summary never replaces details, and new details keep the episodes of the seasons already fetched.
     */
    private Title upsert(Title t) {
        Title in = overlay.apply(t);
        if (in.detailed) in = priced(in);
        Title old = known.get(in.id);
        Title keep = in;
        if (old != null && old.detailed && !in.detailed) {
            if (old.art != null || in.art == null) return old;
            Title.Draft d = new Title.Draft(old);
            d.art = in.art;
            keep = d.build();
        } else if (old != null && old.detailed) {
            List<Season> seasons = new ArrayList<>();
            for (Season s : in.seasons) {
                Season before = old.season(s.n);
                seasons.add(s.needsEpisodes() && before != null && !before.episodes.isEmpty() ? before : s);
            }
            Title.Draft d = new Title.Draft(in);
            d.seasons = seasons;
            keep = d.build();
        }
        known.put(keep.id, keep);
        changed();
        return keep;
    }

    private List<Title> upsertAll(List<Title> titles) {
        List<Title> out = new ArrayList<>(titles.size());
        for (Title t : titles) out.add(upsert(t));
        return out;
    }

    /** The newest copy of each title, which may have been filled in since the list was made. */
    private List<Title> resolve(List<Title> titles) {
        List<Title> out = new ArrayList<>(titles.size());
        for (Title t : titles) {
            Title k = known.get(t.id);
            out.add(k != null ? k : t);
        }
        return out;
    }

    private void changed() {
        snapshot = null;
        if (notifyPosted) return;
        notifyPosted = true;
        main.postDelayed(this::notifyListeners, NOTIFY_MS);
    }

    private void notifyListeners() {
        notifyPosted = false;
        List<Runnable> live = new ArrayList<>();
        for (Iterator<WeakReference<Runnable>> it = listeners.iterator(); it.hasNext(); ) {
            Runnable l = it.next().get();
            if (l == null) it.remove();
            else live.add(l);
        }
        for (Runnable l : live) l.run();
    }

    private void run(int priority, Runnable r) {
        pool.execute(new Job(priority, order.incrementAndGet(), r));
    }

    /** A background task, run in priority order (what a screen waits for first), then in the order asked. */
    private static final class Job implements Runnable, Comparable<Job> {
        static final int HIGH = 0, LOW = 1;
        final int priority;
        final long seq;
        final Runnable r;

        Job(int priority, long seq, Runnable r) {
            this.priority = priority;
            this.seq = seq;
            this.r = r;
        }

        @Override
        public void run() {
            r.run();
        }

        @Override
        public int compareTo(Job o) {
            return priority != o.priority ? Integer.compare(priority, o.priority) : Long.compare(seq, o.seq);
        }
    }

    private static <K, V> Map<K, V> lru(int max) {
        return new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        };
    }
}
