package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Search over the catalogue, ported from design_preview/src/core/fuzzy.ts (search, titlesForPerson, titlesInGenre)
 * and the result list of src/screens/search.ts: titles, people and genres matched with {@link Fuzzy}, each sorted by
 * score, the four suggestions mixed from them, and the cards the results grid shows under a filter chip.
 *
 * <p>With the real catalogue, what TMDB found for the query ({@link Remote}) is merged in: its titles and people
 * first, in TMDB's order, each scored by the same fuzzy rule (or, when the name doesn't match the letters typed, a
 * little below a one-typo match), then everything the TV already knows that matches, so "arival" still finds
 * Arrival offline. A genre typed in full ("drama") lists that genre's titles: those known here, then TMDB's most
 * popular ones. Pure Java, unit-tested on the JVM (SearchIndexTest).
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
        /** For a person: the titles they are in, in catalogue order (TMDB's best known first). */
        final List<Title> titles;
        /** For a person: who they are, with their TMDB id and photo when known. */
        final Person person;

        Hit(HitKind kind, String label, Fuzzy.Match match, Title title, List<Title> titles) {
            this(kind, label, match, title, titles, null);
        }

        Hit(HitKind kind, String label, Fuzzy.Match match, Title title, List<Title> titles, Person person) {
            this.kind = kind;
            this.label = label;
            this.match = match;
            this.title = title;
            this.titles = titles;
            this.person = person != null || kind != HitKind.PERSON ? person : new Person(label, "");
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
        /** When the best genre matched well: its titles that are not among {@link #titles} already. */
        final List<Title> genreTitles;
        /** The query is a genre's name ("drama"): its titles lead the results, before titles named like it. */
        final boolean genreFirst;

        Results(List<Hit> titles, List<Hit> people, List<Hit> genres, List<Hit> suggestions, List<Title> genreTitles) {
            this(titles, people, genres, suggestions, genreTitles, false);
        }

        Results(List<Hit> titles, List<Hit> people, List<Hit> genres, List<Hit> suggestions, List<Title> genreTitles,
                boolean genreFirst) {
            this.titles = titles;
            this.people = people;
            this.genres = genres;
            this.suggestions = suggestions;
            this.genreTitles = genreTitles;
            this.genreFirst = genreFirst && !genreTitles.isEmpty();
        }

        /**
         * Whether the page has results: a title, a person, or a genre typed well enough to list its titles. (The
         * prototype counted a genre alone as no match; BACKLOG.md, Exact genre search.)
         */
        boolean hasMatches() {
            return !titles.isEmpty() || !people.isEmpty() || !genreTitles.isEmpty();
        }
    }

    /** What the network found for a query, to merge in: TMDB's search, and the best genre's popular titles. */
    static final class Remote {
        final Catalog.Found found;
        /** The genre {@link #genreTitles} are for (as {@link Catalog#genres} names it), or null. */
        final String genre;
        final List<Title> genreTitles;

        Remote(Catalog.Found found, String genre, List<Title> genreTitles) {
            this.found = found;
            this.genre = genre;
            this.genreTitles = genreTitles == null ? Collections.emptyList() : genreTitles;
        }
    }

    /** A genre suggestion this good also lists the genre's titles in the results (search.ts, genreTitles). */
    static final double GENRE_TITLES_SCORE = 60;
    /** A title or person TMDB found whose name doesn't match the letters typed: just under a one-typo match. */
    static final double REMOTE_SCORE = 25;

    private static final Comparator<Hit> BY_SCORE = (a, b) -> Double.compare(b.match.score, a.match.score);
    private static final Fuzzy.Match NO_MATCH = new Fuzzy.Match(0, new int[0]);

    private final Catalog catalog;
    /** The catalogue's titles the people were gathered from; gathered again when the catalogue has changed. */
    private List<Title> indexed;
    /** Everyone in a title's cast or crew, in the order the catalogue first names them, with their titles. */
    private final Map<String, List<Title>> people = new LinkedHashMap<>();
    private final Map<String, Person> persons = new LinkedHashMap<>();

    SearchIndex(Catalog catalog) {
        this.catalog = catalog;
        index();
    }

    private void index() {
        List<Title> all = catalog.all();
        if (all == indexed) return;
        indexed = all;
        people.clear();
        persons.clear();
        for (Title t : all) {
            List<Person> cast = new ArrayList<>(t.cast);
            cast.addAll(t.crew);
            for (Person p : cast) {
                List<Title> list = people.get(p.name);
                if (list == null) {
                    list = new ArrayList<>();
                    people.put(p.name, list);
                    persons.put(p.name, p);
                }
                if (!list.contains(t)) list.add(t);
            }
        }
    }

    /** The titles a person appears in or made, in catalogue order; empty for a name the catalogue lacks. */
    List<Title> titlesForPerson(String name) {
        index();
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
        return search(query, null);
    }

    /** The query's matches over what the catalogue knows, with what the network found for it merged in. */
    Results search(String query, Remote remote) {
        index();
        Map<String, Hit> titleHits = new LinkedHashMap<>();
        if (remote != null && remote.found != null) {
            for (Title t : remote.found.titles) titleHits.put(t.id, titleHit(query, latest(t), REMOTE_SCORE));
        }
        for (Title t : catalog.all()) {
            if (titleHits.containsKey(t.id)) continue;
            Fuzzy.Match m = Fuzzy.match(query, t.name);
            if (m != null) titleHits.put(t.id, new Hit(HitKind.TITLE, t.name, m, t, null));
        }
        List<Hit> titles = new ArrayList<>(titleHits.values());

        Map<String, Hit> personHits = new LinkedHashMap<>();
        if (remote != null && remote.found != null) {
            for (Person p : remote.found.people) {
                String key = p.name.toLowerCase(Locale.ROOT);
                if (personHits.containsKey(key)) continue;
                Fuzzy.Match m = Fuzzy.match(query, p.name);
                List<Title> theirs = new ArrayList<>();
                for (Title t : remote.found.knownFor(p)) theirs.add(latest(t));
                for (Title t : titlesForPerson(p.name)) if (!contains(theirs, t)) theirs.add(t);
                personHits.put(key, new Hit(HitKind.PERSON, p.name, (m != null ? m : remoteMatch()).shifted(-6), null,
                        Collections.unmodifiableList(theirs), p));
            }
        }
        for (Map.Entry<String, List<Title>> e : people.entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            if (personHits.containsKey(key)) continue;
            Fuzzy.Match m = Fuzzy.match(query, e.getKey());
            if (m != null) personHits.put(key, new Hit(HitKind.PERSON, e.getKey(), m.shifted(-6),
                    null, Collections.unmodifiableList(e.getValue()), persons.get(e.getKey())));
        }
        List<Hit> ppl = new ArrayList<>(personHits.values());

        List<Hit> genres = new ArrayList<>();
        for (String g : catalog.genres()) {
            Fuzzy.Match m = Fuzzy.match(query, g);
            if (m != null) genres.add(new Hit(HitKind.GENRE, g, m.shifted(-4), null, null));
        }
        // Stable sorts, as the prototype's Array.sort: equal scores keep TMDB's order, then the catalogue's.
        titles.sort(BY_SCORE);
        ppl.sort(BY_SCORE);
        genres.sort(BY_SCORE);
        List<Hit> suggestions = new ArrayList<>(titles.subList(0, Math.min(4, titles.size())));
        suggestions.addAll(ppl.subList(0, Math.min(2, ppl.size())));
        suggestions.addAll(genres.subList(0, Math.min(1, genres.size())));
        suggestions.sort(BY_SCORE);
        if (suggestions.size() > 4) suggestions = new ArrayList<>(suggestions.subList(0, 4));
        boolean exactGenre = !genres.isEmpty() && Fuzzy.normalize(query).equals(Fuzzy.fold(genres.get(0).label));
        return new Results(titles, ppl, genres, suggestions, genreTitles(titles, genres, remote), exactGenre);
    }

    /** The genre whose titles the results list: the best genre, when it matched well; else null. */
    static String strongGenre(Results r) {
        Hit genre = r.genres.isEmpty() ? null : r.genres.get(0);
        return genre != null && genre.match.score > GENRE_TITLES_SCORE ? genre.label : null;
    }

    private List<Title> genreTitles(List<Hit> titles, List<Hit> genres, Remote remote) {
        Hit genre = genres.isEmpty() ? null : genres.get(0);
        if (genre == null || genre.match.score <= GENRE_TITLES_SCORE) return Collections.emptyList();
        List<Title> out = new ArrayList<>();
        List<Title> candidates = new ArrayList<>(titlesInGenre(genre.label));
        if (remote != null && genre.label.equals(remote.genre)) {
            for (Title t : remote.genreTitles) if (!contains(candidates, t)) candidates.add(latest(t));
        }
        for (Title t : candidates) {
            boolean listed = false;
            for (Hit h : titles) listed |= h.title.id.equals(t.id);
            if (!listed && !contains(out, t)) out.add(t);
        }
        return out;
    }

    /**
     * The cards of the results grid (search.ts render): the matching titles, then, when the best genre matched well,
     * that genre's other titles (with nothing highlighted), then the people; kept by the filter. A query that is a
     * genre's name puts the genre's titles first. Included only keeps titles with a free offer or one on a
     * subscribed service.
     */
    List<Hit> items(Results r, Filter filter, Set<ServiceId> subscribed) {
        List<Hit> genre = new ArrayList<>();
        for (Title t : r.genreTitles) genre.add(new Hit(HitKind.TITLE, t.name, NO_MATCH, t, null));
        List<Hit> all = new ArrayList<>();
        if (r.genreFirst) all.addAll(genre);
        all.addAll(r.titles);
        if (!r.genreFirst) all.addAll(genre);
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

    private static Hit titleHit(String query, Title t, double fallback) {
        Fuzzy.Match m = Fuzzy.match(query, t.name);
        return new Hit(HitKind.TITLE, t.name, m != null ? m : new Fuzzy.Match(fallback, new int[0]), t, null);
    }

    private static Fuzzy.Match remoteMatch() {
        return new Fuzzy.Match(REMOTE_SCORE, new int[0]);
    }

    /** The catalogue's newest copy of t: a search hit may have been filled in since. */
    private Title latest(Title t) {
        Title k = catalog.get(t.id);
        return k != null ? k : t;
    }

    private static boolean contains(List<Title> list, Title t) {
        for (Title o : list) if (o.id.equals(t.id)) return true;
        return false;
    }
}
