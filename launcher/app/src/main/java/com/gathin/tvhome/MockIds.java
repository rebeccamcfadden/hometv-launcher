package com.gathin.tvhome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mock catalogue's titles on TMDB (BACKLOG.md, Real catalogue): each prototype id with its TMDB id, and TMDB's
 * name and year for it, looked up once by name and year (2026-10-30) and kept here. The household's seed and any
 * ids stored before the real catalogue (My list, Seen, Favorites, Recently viewed) move to TMDB ids through it, and
 * the mock offers and watchlists stay on these titles until real availability arrives. "marvel-one-shots" has no
 * single TMDB title (TMDB lists each short apart), so it is not here and drops out. Pure Java, unit-tested
 * (MockIdsTest).
 */
final class MockIds {
    private MockIds() {}

    /** A mock title's TMDB id, with the name and year TMDB gives it. */
    static final class Entry {
        final String mockId;
        final String id;
        final String name;
        final int year;

        Entry(String mockId, String id, String name, int year) {
            this.mockId = mockId;
            this.id = id;
            this.name = name;
            this.year = year;
        }
    }

    private static final Map<String, Entry> BY_MOCK = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    static {
        movie("arrival", 329865, "Arrival", 2016);
        movie("dune", 693134, "Dune: Part Two", 2024);
        movie("past-lives", 666277, "Past Lives", 2023);
        movie("mitchells", 501929, "The Mitchells vs. the Machines", 2021);
        movie("coda", 776503, "CODA", 2021);
        movie("moon", 17431, "Moon", 2009);
        movie("knives-out", 546554, "Knives Out", 2019);
        movie("budapest", 120467, "The Grand Budapest Hotel", 2014);
        movie("eeaao", 545611, "Everything Everywhere All at Once", 2022);
        movie("oppenheimer", 872585, "Oppenheimer", 2023);
        movie("killers", 466420, "Killers of the Flower Moon", 2023);
        movie("poor-things", 792307, "Poor Things", 2023);
        movie("maestro", 523607, "Maestro", 2023);
        movie("zone", 467244, "The Zone of Interest", 2023);
        movie("anatomy", 915935, "Anatomy of a Fall", 2023);
        movie("american-fiction", 1056360, "American Fiction", 2023);
        tv("severance", 95396, "Severance", 2022);
        tv("the-bear", 136315, "The Bear", 2022);
        tv("dark", 70523, "Dark", 2017);
        tv("last-of-us", 100088, "The Last of Us", 2023);
        tv("shogun", 126308, "Shōgun", 2024);
        tv("fallout", 106379, "Fallout", 2024);
        tv("our-planet", 83880, "Our Planet", 2019);
        tv("microcosmos", 117442, "Journey to the Microcosmos", 2019);
        tv("peripheral", 95403, "The Peripheral", 2022);
        tv("the-crown", 65494, "The Crown", 2016);
        tv("wheel-of-time", 71914, "The Wheel of Time", 2021);
        tv("blue-planet", 74313, "Blue Planet II", 2017);
        tv("carnival-row", 90027, "Carnival Row", 2019);
        movie("the-arrival-1996", 10547, "The Arrival", 1996);
        movie("blade-runner-2049", 335984, "Blade Runner 2049", 2017);
        movie("interstellar", 157336, "Interstellar", 2014);
        tv("larva-island", 83248, "Larva Island", 2018);
        movie("carnival-of-souls", 16093, "Carnival of Souls", 1962);
        tv("mrs-maisel", 70796, "The Marvelous Mrs. Maisel", 2017);
        tv("marvel-assembled", 118924, "Marvel Studios Assembled", 2021);
        tv("daredevil", 61889, "Marvel's Daredevil", 2015);
        movie("harvey-girls", 28907, "The Harvey Girls", 1946);
        tv("carnival-eats", 65080, "Carnival Eats", 2014);
        tv("carnivale", 185, "Carnivàle", 2003);
        tv("911", 75219, "9-1-1", 2018);
        tv("911-lone-star", 89393, "9-1-1: Lone Star", 2020);
    }

    private static void movie(String mockId, int tmdb, String name, int year) {
        add(new Entry(mockId, TmdbApi.id(Kind.MOVIE, tmdb), name, year));
    }

    private static void tv(String mockId, int tmdb, String name, int year) {
        add(new Entry(mockId, TmdbApi.id(Kind.SHOW, tmdb), name, year));
    }

    private static void add(Entry e) {
        BY_MOCK.put(e.mockId, e);
        BY_ID.put(e.id, e);
    }

    /** Every mapped title, in the mock catalogue's order. */
    static Map<String, Entry> all() {
        return Collections.unmodifiableMap(BY_MOCK);
    }

    /** The TMDB id for a mock id, or null when it has none. */
    static String tmdbId(String mockId) {
        Entry e = mockId == null ? null : BY_MOCK.get(mockId);
        return e == null ? null : e.id;
    }

    /** The mock id a TMDB id came from, or null. */
    static String mockId(String tmdbId) {
        Entry e = tmdbId == null ? null : BY_ID.get(tmdbId);
        return e == null ? null : e.mockId;
    }

    /**
     * A stored id in the real catalogue's terms: a TMDB id as it is, a mapped mock id as its TMDB id, and null for
     * a mock id with no TMDB title.
     */
    static String migrate(String id) {
        if (TmdbApi.isTmdbId(id)) return id;
        return tmdbId(id);
    }
}
