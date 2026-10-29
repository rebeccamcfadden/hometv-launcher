package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A movie or a show in the catalogue (design_preview/src/types.ts, Title). Immutable, with public final fields and
 * unmodifiable lists. {@link #runtime} is in minutes for movies and 0 for shows; {@link #seasons} is empty for
 * movies; {@link #lists} is empty for a catalogue-only title (on no imported watchlist); {@link #nextSeason} and
 * {@link #nextEpisode} are the episode to watch next, 0 when there is none. {@link #art} is the artwork's base name
 * ("arrival"), which {@link Art} turns into an image.
 */
final class Title {
    final String id;
    final Kind kind;
    final String name;
    final int year;
    final List<String> genres;
    final int runtime;
    final String rating;
    final double score;
    final String synopsis;
    final String art;
    final List<Offer> offers;
    final List<ListId> lists;
    final List<Person> cast;
    final List<Person> crew;
    final List<Season> seasons;
    final int nextSeason;
    final int nextEpisode;

    Title(String id, Kind kind, String name, int year, List<String> genres, int runtime, String rating, double score,
          String synopsis, String art, List<Offer> offers, List<ListId> lists, List<Person> cast, List<Person> crew,
          List<Season> seasons, int nextSeason, int nextEpisode) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.year = year;
        this.genres = copy(genres);
        this.runtime = runtime;
        this.rating = rating;
        this.score = score;
        this.synopsis = synopsis;
        this.art = art;
        this.offers = copy(offers);
        this.lists = copy(lists);
        this.cast = copy(cast);
        this.crew = copy(crew);
        this.seasons = copy(seasons);
        this.nextSeason = nextSeason;
        this.nextEpisode = nextEpisode;
    }

    boolean isMovie() {
        return kind == Kind.MOVIE;
    }

    /** Whether the title is on that imported watchlist. */
    boolean isOn(ListId list) {
        return lists.contains(list);
    }

    /** Whether the show has a next episode to watch. */
    boolean hasNext() {
        return nextSeason > 0 && nextEpisode > 0;
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null || list.isEmpty() ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(list));
    }

    @Override
    public String toString() {
        return id;
    }
}
