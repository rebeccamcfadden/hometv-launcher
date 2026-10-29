package com.gathin.tvhome;

/** One episode of a show's season (design_preview/src/types.ts, Episode): its number, name and minutes. Immutable. */
final class Episode {
    final int n;
    final String name;
    final int runtime;

    Episode(int n, String name, int runtime) {
        this.n = n;
        this.name = name;
        this.runtime = runtime;
    }
}
