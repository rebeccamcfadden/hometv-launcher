package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A movie or a show in the catalogue (design_preview/src/types.ts, Title). Immutable, with public final fields and
 * unmodifiable lists. {@link #runtime} is in minutes for movies and 0 for shows (or when not known yet);
 * {@link #seasons} is empty for movies; {@link #lists} is empty for a catalogue-only title (on no imported
 * watchlist); {@link #nextSeason} and {@link #nextEpisode} are the episode to watch next, 0 when there is none.
 * {@link #art} is the artwork: a TMDB backdrop path ("/abc.jpg") that {@link Art} loads, or the mock catalogue's
 * base name ("arrival"), which shows the placeholder.
 *
 * <p>From TMDB a title first arrives as a summary (a search hit, a recommendation): name, year, genres, score,
 * synopsis and art. {@link #detailed} says whether its details (runtime, rating, cast, seasons, trailer) are in;
 * the mock catalogue's titles always are. {@link #episodeRuntime} is a show's typical episode length from TMDB, for
 * when its episodes are not loaded; {@link #trailer} is the YouTube id of its trailer, or null.
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
    final boolean detailed;
    final int episodeRuntime;
    final String trailer;

    /** A complete title, as the mock catalogue and the tests make them. */
    Title(String id, Kind kind, String name, int year, List<String> genres, int runtime, String rating, double score,
          String synopsis, String art, List<Offer> offers, List<ListId> lists, List<Person> cast, List<Person> crew,
          List<Season> seasons, int nextSeason, int nextEpisode) {
        this(id, kind, name, year, genres, runtime, rating, score, synopsis, art, offers, lists, cast, crew, seasons,
                nextSeason, nextEpisode, true, 0, null);
    }

    Title(String id, Kind kind, String name, int year, List<String> genres, int runtime, String rating, double score,
          String synopsis, String art, List<Offer> offers, List<ListId> lists, List<Person> cast, List<Person> crew,
          List<Season> seasons, int nextSeason, int nextEpisode, boolean detailed, int episodeRuntime,
          String trailer) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.year = year;
        this.genres = copy(genres);
        this.runtime = runtime;
        this.rating = rating == null ? "" : rating;
        this.score = score;
        this.synopsis = synopsis == null ? "" : synopsis;
        this.art = art;
        this.offers = copy(offers);
        this.lists = copy(lists);
        this.cast = copy(cast);
        this.crew = copy(crew);
        this.seasons = copy(seasons);
        this.nextSeason = nextSeason;
        this.nextEpisode = nextEpisode;
        this.detailed = detailed;
        this.episodeRuntime = episodeRuntime;
        this.trailer = trailer;
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

    /** The season numbered n, or null. */
    Season season(int n) {
        for (Season s : seasons) if (s.n == n) return s;
        return null;
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null || list.isEmpty() ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(list));
    }

    @Override
    public String toString() {
        return id;
    }

    /** A changeable copy of a title, for the catalogue to fill in and build a new one from. */
    static final class Draft {
        String id;
        Kind kind;
        String name;
        int year;
        List<String> genres;
        int runtime;
        String rating;
        double score;
        String synopsis;
        String art;
        List<Offer> offers;
        List<ListId> lists;
        List<Person> cast;
        List<Person> crew;
        List<Season> seasons;
        int nextSeason;
        int nextEpisode;
        boolean detailed;
        int episodeRuntime;
        String trailer;

        Draft(String id, Kind kind, String name) {
            this.id = id;
            this.kind = kind;
            this.name = name;
        }

        Draft(Title t) {
            id = t.id;
            kind = t.kind;
            name = t.name;
            year = t.year;
            genres = t.genres;
            runtime = t.runtime;
            rating = t.rating;
            score = t.score;
            synopsis = t.synopsis;
            art = t.art;
            offers = t.offers;
            lists = t.lists;
            cast = t.cast;
            crew = t.crew;
            seasons = t.seasons;
            nextSeason = t.nextSeason;
            nextEpisode = t.nextEpisode;
            detailed = t.detailed;
            episodeRuntime = t.episodeRuntime;
            trailer = t.trailer;
        }

        Title build() {
            return new Title(id, kind, name, year, genres, runtime, rating, score, synopsis, art, offers, lists, cast,
                    crew, seasons, nextSeason, nextEpisode, detailed, episodeRuntime, trailer);
        }
    }
}
