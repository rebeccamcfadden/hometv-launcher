package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Where titles come from (docs/rework.md, Catalogue): {@link TmdbCatalog}, the real one, when the build has a TMDB
 * token, else {@link MockCatalog}, the prototype's mock data, which has everything at hand and works offline.
 *
 * <p>The real catalogue knows a title in steps (a summary from a search, then its details, then a season's
 * episodes), so besides what it knows now ({@link #all}, {@link #get}) it answers questions later: each callback
 * runs on the main thread, possibly before the method returns when the answer is already in memory, and never when
 * there is no answer (offline with nothing cached), so a screen keeps what it shows. Listeners hear when titles were
 * added or filled in. The mock catalogue answers every question at once from its own titles.
 */
interface Catalog {
    /** Every title known now, in the catalogue's order. */
    List<Title> all();

    /** The title with this id, or null. */
    Title get(String id);

    /** Every genre a title can have, sorted and distinct. */
    List<String> genres();

    /** The genres a movie or a show can have: the Genre filter's options. */
    default List<String> genres(Kind kind) {
        return genres();
    }

    /** This catalogue's id for a mock catalogue id (the household's seed, ids stored by V1), or null for none. */
    default String fromMockId(String mockId) {
        return mockId;
    }

    /** Whether answers come over the network (search as you type, details as they arrive). */
    default boolean isRemote() {
        return false;
    }

    /**
     * Calls l on the main thread when titles were added or filled in, a few at a time. Held weakly: the caller keeps
     * l in a field for as long as it wants to hear, so a screen that is gone stops listening by itself.
     */
    default void addListener(Runnable l) {}

    /** Loads the details of these titles in the background, from the cache first, for lists and filters. */
    default void want(Collection<String> ids) {}

    /** The internet is back (or Home showed again): try once more what failed, such as a cold start before Wi-Fi. */
    default void retryFailed() {}

    /**
     * Whether t's details are on their way for the first time, or again after Retry or the network coming back, so a
     * screen shows its skeleton (V2 design 06-states A). Never for the mock catalogue, which has everything at hand.
     */
    default boolean pending(String id) {
        return false;
    }

    /** Whether t's details failed to load, with no saved copy to fall back on (offline, or TMDB didn't answer). */
    default boolean failed(String id) {
        return false;
    }

    /** When the copy of t's details was fetched from TMDB, in epoch milliseconds; 0 when not known. */
    default long savedAt(String id) {
        return 0;
    }

    /** The title with its details (cast, seasons, rating, trailer). */
    default void details(Title t, Consumer<Title> done) {
        Title known = get(t.id);
        done.accept(known != null ? known : t);
    }

    /** The show with season n's episodes filled in. */
    default void season(Title t, int n, Consumer<Title> done) {
        details(t, done);
    }

    /** Titles like t, for More like this. */
    default void similar(Title t, Consumer<List<Title>> done) {
        done.accept(PreviewRules.similar(t, all()));
    }

    /** What a person is in or made, and their photo. */
    default void person(Person p, Consumer<Credits> done) {
        done.accept(new Credits(p, PreviewRules.titlesFor(p.name, all())));
    }

    /** Titles and people the network finds for a query. The mock catalogue has no more than it holds, so never. */
    default void search(String query, Consumer<Found> done) {}

    /**
     * Asks for t's real prices and the links into its services' apps (V2 Phase 2) when its preview opens
     * ({@code preview}; anything else asks nothing: grids and search never do). {@code done}, which may be null, runs
     * on the main thread with the title as it then is; listeners hear too. The mock catalogue's offers are all it
     * has, so it does nothing.
     */
    default void prices(Title t, boolean preview, Consumer<Title> done) {}

    /** The titles on My list and on the TMDB lists shown as chips: they are kept priced in the background. */
    default void keepPriced(Collection<String> listedIds) {}

    /**
     * Whether the household's lists are priced in the background at all. Off until a TMDB account is signed in
     * (project lead, 2026-10-30), so a fresh TV doesn't spend price credits on the mock lists;
     * a preview still prices the title it shows.
     */
    default void setListPricing(boolean on) {}

    /**
     * The stores ticked in Launcher settings (Stores for rent and buy): rent and buy offers of the other stores the
     * pane lists are left out, the titles known now included ({@link ServicePicks#keep}). The mock catalogue keeps
     * the prototype's offers, so it does nothing.
     */
    default void setPriceStores(Set<ServiceId> stores) {}

    /** The link that opens t itself in a service's app, or null (the app then opens on its home screen). */
    default String link(Title t, ServiceId service) {
        return null;
    }

    /** Where t's availability and prices come from, for the Where to watch caption; null for the mock catalogue. */
    default String attribution(Title t) {
        return null;
    }

    /** When t's prices were fetched, for Where to watch's offline caption ("Saved today 19:52"); 0 when not known. */
    default long pricesSavedAt(Title t) {
        return 0;
    }

    /** Home is showing (true) or hidden: no price lookups start while it is hidden. */
    default void setVisible(boolean visible) {}

    /** The best-known titles of a genre, movies and shows. */
    default void genre(String genre, Consumer<List<Title>> done) {
        List<Title> out = new ArrayList<>();
        for (Title t : all()) if (t.genres.contains(genre)) out.add(t);
        done.accept(out);
    }

    /** A person and the titles they are known for. */
    final class Credits {
        final Person person;
        final List<Title> titles;

        Credits(Person person, List<Title> titles) {
            this.person = person;
            this.titles = Collections.unmodifiableList(new ArrayList<>(titles));
        }
    }

    /** What a search found: titles and people in the source's order, and each person's best-known titles. */
    final class Found {
        final List<Title> titles;
        final List<Person> people;
        private final Map<Integer, List<Title>> knownFor;

        Found(List<Title> titles, List<Person> people, Map<Integer, List<Title>> knownFor) {
            this.titles = Collections.unmodifiableList(new ArrayList<>(titles));
            this.people = Collections.unmodifiableList(new ArrayList<>(people));
            this.knownFor = new HashMap<>(knownFor);
        }

        /** The titles a found person is known for; empty for anyone else. */
        List<Title> knownFor(Person p) {
            List<Title> list = knownFor.get(p.id);
            return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
        }
    }
}
