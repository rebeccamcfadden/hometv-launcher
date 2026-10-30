package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One season of a show (design_preview/src/types.ts, Season): its number and episodes in order. Immutable. From TMDB
 * a show's details give each season's number and episode count; the episodes come later, a season at a time, so
 * {@link #episodes} may be empty while {@link #count} says how many there are.
 */
final class Season {
    final int n;
    final int count;
    final List<Episode> episodes;

    Season(int n, List<Episode> episodes) {
        this(n, episodes.size(), episodes);
    }

    Season(int n, int count, List<Episode> episodes) {
        this.n = n;
        this.episodes = Collections.unmodifiableList(new ArrayList<>(episodes));
        this.count = Math.max(count, this.episodes.size());
    }

    /** Whether the season's episodes still have to be fetched. */
    boolean needsEpisodes() {
        return episodes.isEmpty() && count > 0;
    }
}
