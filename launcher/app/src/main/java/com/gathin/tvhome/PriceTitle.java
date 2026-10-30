package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The title a {@link PriceSource} is asked about: its TMDB id and kind, which Watchmode and Streaming Availability
 * look up directly, and its names and year, which iTunes needs to check a match ({@link TitleMatch}). The iTunes ids
 * come from Wikidata ({@link WikidataIds#itunesIds}); without them iTunes can only search by name.
 */
final class PriceTitle {
    final int tmdbId;
    final Kind kind;
    final String name;
    /** The original-language name when it differs ("Le Fabuleux Destin d'Amélie Poulain"), or null. */
    final String originalName;
    /** Release year for a movie, first-air year for a show; 0 when unknown. */
    final int year;
    final List<String> itunesIds;

    PriceTitle(int tmdbId, Kind kind, String name, String originalName, int year, List<String> itunesIds) {
        this.tmdbId = tmdbId;
        this.kind = kind;
        this.name = name;
        this.originalName = originalName;
        this.year = year;
        this.itunesIds = itunesIds == null || itunesIds.isEmpty() ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(itunesIds));
    }

    PriceTitle(int tmdbId, Kind kind, String name, int year) {
        this(tmdbId, kind, name, null, year, null);
    }

    boolean isMovie() {
        return kind == Kind.MOVIE;
    }

    /** The names a store's title may match: the name, then the original name when it differs. */
    List<String> names() {
        List<String> out = new ArrayList<>(2);
        if (name != null && !name.isEmpty()) out.add(name);
        if (originalName != null && !originalName.isEmpty() && !originalName.equals(name)) out.add(originalName);
        return out;
    }

    /** "movie/329865" or "tv/1396": TMDB's own path for the title. */
    String tmdbPath() {
        return (isMovie() ? "movie/" : "tv/") + tmdbId;
    }

    @Override
    public String toString() {
        return tmdbPath();
    }
}
