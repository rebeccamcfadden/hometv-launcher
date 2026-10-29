package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search over the catalogue, ported from design_preview/src/core/fuzzy.ts (search, titlesForPerson, titlesInGenre)
 * and the result list of src/screens/search.ts: titles, people and genres matched with {@link Fuzzy}, each sorted by
 * score, the four suggestions mixed from them, and the cards the results grid shows under a filter chip. Pure Java,
 * unit-tested on the JVM (SearchIndexTest).
 */
final class SearchIndex {

    enum HitKind { TITLE, PERSON, GENRE }

    /** The results grid's filter chips, in order: All, Movies, TV shows, People, Included only. */
    enum Filter { ALL, MOVIES, SHOWS, PEOPLE, INCLUDED }

    /** A title, person or genre that matched, with the matched letters of its label. */
    static final class Hit {
        final HitKind kind;
        final String label;
        final Fuzzy.Match match;
        /** For a title. */
        final Title title;
        /** For a person: the titles they are in, in catalogue order. */
        final List<Title> titles;

        Hit(HitKind kind, String label, Fuzzy.Match match, Title title, List<Title> titles) {
            this.kind = kind;
            this.label = label;
            this.match = match;
            this.title = title;
            this.titles = titles;
        }

        @Override
        public String toString() {
            return kind + ":" + label;
        }
    }

    /** One query's matches, each list best first. */
    static final class Results {
        final List<Hit> titles;
        final List<Hit> people;
        final List<Hit> genres;
        /** Up to four: the best four of the top four titles, top two people and top genre. */
        final List<Hit> suggestions;

        Results(List<Hit> titles, List<Hit> people, List<Hit> genres, List<Hit> suggestions) {
            this.titles = titles;
            this.people = people;
            this.genres = genres;
            this.suggestions = suggestions;
        }

        /** A genre alone is not a match: the page shows "No matches" unless a title or a person matched. */
        boolean hasMatches() {
            return !titles.isEmpty() || !people.isEmpty();
        }
    }

    /** A genre suggestion this good also lists the genre's titles in the results (search.ts, genreTitles). */
    static final double GENRE_TITLES_SCORE = 60;

    private static final Comparator<Hit> BY_SCORE = (a, b) -> Double.compare(b.match.score, a.match.score);
    private static final Fuzzy.Match NO_MATCH = new Fuzzy.Match(0, new int[0]);

    private final Catalog catalog;
    /** Everyone in a title's cast or crew, in the order the catalogue first names them. */
    private final Map<String, List<Title>> people = new LinkedHashMap<>();

    SearchIndex(Catalog catalog) {
        this.catalog = catalog;
        for (Title t : catalog.all()) {
            List<Person> all = new ArrayList<>(t.cast);
            all.addAll(t.crew);
            for (Person p : all) {
                List<Title> list = people.get(p.name);
                if (list == null) {
                    list = new ArrayList<>();
                    people.put(p.name, list);
                }
                if (!list.contains(t)) list.add(t);
            }
        }
    }

    /** The titles a person appears in or made, in catalogue order; empty for a name the catalogue lacks. */
    List<Title> titlesForPerson(String name) {
        List<Title> list = people.get(name);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /** Titles whose genres include genre, in catalogue order. */
    List<Title> titlesInGenre(String genre) {
        List<Title> out = new ArrayList<>();
        for (Title t : catalog.all()) if (t.genres.contains(genre)) out.add(t);
        return out;
    }

    Results search(String query) {
        List<Hit> titles = new ArrayList<>();
        for (Title t : catalog.all()) {
            Fuzzy.Match m = Fuzzy.match(query, t.name);
            if (m != null) titles.add(new Hit(HitKind.TITLE, t.name, m, t, null));
        }
        List<Hit> ppl = new ArrayList<>();
        for (Map.Entry<String, List<Title>> e : people.entrySet()) {
            Fuzzy.Match m = Fuzzy.match(query, e.getKey());
            if (m != null) ppl.add(new Hit(HitKind.PERSON, e.getKey(), m.shifted(-6),
                    null, Collections.unmodifiableList(e.getValue())));
        }
        List<Hit> genres = new ArrayList<>();
        for (String g : catalog.genres()) {
            Fuzzy.Match m = Fuzzy.match(query, g);
            if (m != null) genres.add(new Hit(HitKind.GENRE, g, m.shifted(-4), null, null));
        }
        // Stable sorts, as the prototype's Array.sort: equal scores keep catalogue order.
        titles.sort(BY_SCORE);
        ppl.sort(BY_SCORE);
        genres.sort(BY_SCORE);
        List<Hit> suggestions = new ArrayList<>(titles.subList(0, Math.min(4, titles.size())));
        suggestions.addAll(ppl.subList(0, Math.min(2, ppl.size())));
        suggestions.addAll(genres.subList(0, Math.min(1, genres.size())));
        suggestions.sort(BY_SCORE);
        if (suggestions.size() > 4) suggestions = new ArrayList<>(suggestions.subList(0, 4));
        return new Results(titles, ppl, genres, suggestions);
    }

    /**
     * The cards of the results grid (search.ts render): the matching titles, then, when the best genre matched well,
     * that genre's other titles (with nothing highlighted), then the people; kept by the filter. Included only keeps
     * titles with a free offer or one on a subscribed service.
     */
    List<Hit> items(Results r, Filter filter, Set<ServiceId> subscribed) {
        List<Hit> all = new ArrayList<>(r.titles);
        Hit genre = r.genres.isEmpty() ? null : r.genres.get(0);
        if (genre != null && genre.match.score > GENRE_TITLES_SCORE) {
            for (Title t : titlesInGenre(genre.label)) {
                boolean listed = false;
                for (Hit h : r.titles) {
                    if (h.title == t) {
                        listed = true;
                        break;
                    }
                }
                if (!listed) all.add(new Hit(HitKind.TITLE, t.name, NO_MATCH, t, null));
            }
        }
        all.addAll(r.people);
        List<Hit> out = new ArrayList<>();
        for (Hit h : all) if (keeps(filter, h, subscribed)) out.add(h);
        return out;
    }

    private static boolean keeps(Filter filter, Hit h, Set<ServiceId> subscribed) {
        switch (filter) {
            case ALL:
                return true;
            case PEOPLE:
                return h.kind == HitKind.PERSON;
            case INCLUDED:
                return h.kind == HitKind.TITLE && Offers.isIncluded(h.title, subscribed);
            case MOVIES:
                return h.kind == HitKind.TITLE && h.title.kind == Kind.MOVIE;
            default:
                return h.kind == HitKind.TITLE && h.title.kind == Kind.SHOW;
        }
    }
}
