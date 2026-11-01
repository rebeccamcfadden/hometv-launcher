package com.gathin.tvhome;

import java.util.HashMap;
import java.util.Map;

/**
 * What stays mock on the real catalogue's titles (BACKLOG.md, Shown TMDB lists): the mock catalogue's
 * imported watchlists and next episode, carried over by TMDB id onto the titles {@link MockIds} maps. Any other title
 * is on no imported list. Offers are real since V2 Phase 2: TMDB's availability, with prices ({@link TmdbParse}).
 * Pure Java, unit-tested (MockOverlayTest).
 */
final class MockOverlay {
    private final Map<String, Title> byTmdbId = new HashMap<>();

    MockOverlay(Catalog mock) {
        for (Title t : mock.all()) {
            String id = MockIds.tmdbId(t.id);
            if (id != null) byTmdbId.put(id, t);
        }
    }

    /** The mock title a TMDB id came from, or null. */
    Title mock(String tmdbId) {
        return byTmdbId.get(tmdbId);
    }

    /** t with the mock title's lists and next episode, or t as it is when it has no mock title. */
    Title apply(Title t) {
        Title m = byTmdbId.get(t.id);
        if (m == null) return t;
        Title.Draft d = new Title.Draft(t);
        d.lists = m.lists;
        d.nextSeason = 0;
        d.nextEpisode = 0;
        if (m.hasNext()) {
            Season s = t.season(m.nextSeason);
            // Kept while the seasons are not known yet; dropped when the real show has no such episode.
            if (t.seasons.isEmpty() || (s != null && m.nextEpisode <= s.count)) {
                d.nextSeason = m.nextSeason;
                d.nextEpisode = m.nextEpisode;
            }
        }
        return d.build();
    }
}
