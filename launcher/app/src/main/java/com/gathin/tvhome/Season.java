package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One season of a show (design_preview/src/types.ts, Season): its number and episodes in order. Immutable. */
final class Season {
    final int n;
    final List<Episode> episodes;

    Season(int n, List<Episode> episodes) {
        this.n = n;
        this.episodes = Collections.unmodifiableList(new ArrayList<>(episodes));
    }
}
