package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns TMDB's answers into the launcher's titles, seasons and people (BACKLOG.md, Real catalogue): a list item
 * becomes a summary title, a details page a detailed one (runtime or seasons, US rating, cast and crew, trailer), a
 * season its episodes, a person their best-known titles. Missing fields give empty values, never an exception.
 * A details page's offers come from its watch providers in Sweden ({@link #availability}), without prices; the
 * imported watchlists are not TMDB's: {@link MockOverlay} adds them. Pure Java with org.json, unit-tested with
 * hand-written answers (TmdbParseTest).
 */
final class TmdbParse {
    private TmdbParse() {}

    /** How many of the cast and the crew a title keeps for Cast &amp; crew. */
    static final int CAST_MAX = 15, CREW_MAX = 6;
    /** How many titles a person page lists. */
    static final int KNOWN_FOR_MAX = 30;

    /** Talk shows, news and reality TV: a person's appearances there are not what they are known for. */
    private static final int[] NOT_KNOWN_FOR = {10767, 10763, 10764};

    // ---- Titles ---------------------------------------------------------------------------------------------------

    /**
     * A title from a list (search, discover, recommendations, credits): its kind from media_type, else the hint;
     * null for a person or anything else.
     */
    static Title summary(JSONObject o, Kind hint) {
        if (o == null) return null;
        Kind kind = kind(o.optString("media_type", ""), hint);
        int id = o.optInt("id");
        if (kind == null || id <= 0 || o.optBoolean("adult")) return null;
        Title.Draft d = new Title.Draft(TmdbApi.id(kind, id), kind, name(o, kind));
        if (d.name.isEmpty()) return null;
        d.year = year(o.optString(kind == Kind.MOVIE ? "release_date" : "first_air_date", ""));
        d.genres = GenreMap.names(kind, ints(o.optJSONArray("genre_ids")));
        d.score = score(o);
        d.synopsis = o.optString("overview", "");
        d.art = path(o, "backdrop_path");
        d.detailed = false;
        return d.build();
    }

    /** The summaries of a list answer's results, in TMDB's order, without repeats. */
    static List<Title> summaries(JSONArray results, Kind hint) {
        Map<String, Title> out = new LinkedHashMap<>();
        for (int i = 0; results != null && i < results.length(); i++) {
            Title t = summary(results.optJSONObject(i), hint);
            if (t != null && !out.containsKey(t.id)) out.put(t.id, t);
        }
        return new ArrayList<>(out.values());
    }

    /** A details answer (/movie/{id} or /tv/{id} with credits, ratings and videos appended). */
    static Title details(JSONObject o, Kind kind) {
        Title.Draft d = new Title.Draft(TmdbApi.id(kind, o.optInt("id")), kind, name(o, kind));
        d.year = year(o.optString(kind == Kind.MOVIE ? "release_date" : "first_air_date", ""));
        List<Integer> genreIds = new ArrayList<>();
        JSONArray g = o.optJSONArray("genres");
        for (int i = 0; g != null && i < g.length(); i++) {
            JSONObject genre = g.optJSONObject(i);
            if (genre != null) genreIds.add(genre.optInt("id"));
        }
        d.genres = GenreMap.names(kind, toArray(genreIds));
        d.score = score(o);
        d.synopsis = o.optString("overview", "");
        d.art = path(o, "backdrop_path");
        d.trailer = trailer(o.optJSONObject("videos"));
        JSONObject credits = o.optJSONObject("credits");
        d.cast = cast(credits);
        List<Person> crew = new ArrayList<>();
        if (kind == Kind.MOVIE) {
            d.runtime = Math.max(0, o.optInt("runtime"));
            d.rating = movieRating(o.optJSONObject("release_dates"));
        } else {
            d.rating = tvRating(o.optJSONObject("content_ratings"));
            d.episodeRuntime = episodeRuntime(o);
            d.seasons = seasons(o.optJSONArray("seasons"));
            JSONArray creators = o.optJSONArray("created_by");
            for (int i = 0; creators != null && i < creators.length(); i++) {
                Person p = person(creators.optJSONObject(i), "Creator");
                if (p != null) crew.add(p);
            }
        }
        crew.addAll(crew(credits, kind));
        d.crew = dedupe(crew, CREW_MAX);
        d.offers = OfferMerge.merge(availability(o), Collections.emptyList(), WatchProviders.SE).toOffers();
        d.detailed = true;
        return d.build();
    }

    /** Where a details page (with watch/providers appended) says the title can be watched in Sweden. */
    static ProviderAvailability availability(JSONObject details) {
        JSONObject providers = details.optJSONObject("watch/providers");
        return providers == null ? ProviderAvailability.empty(WatchProviders.REGION)
                : ProviderAvailability.parse(providers, WatchProviders.REGION);
    }

    /** More like this: the recommendations, or the similar titles when there are none. */
    static List<Title> similar(JSONObject details, Kind kind) {
        JSONObject rec = details.optJSONObject("recommendations");
        List<Title> out = summaries(rec == null ? null : rec.optJSONArray("results"), kind);
        if (!out.isEmpty()) return out;
        JSONObject sim = details.optJSONObject("similar");
        return summaries(sim == null ? null : sim.optJSONArray("results"), kind);
    }

    /** /tv/{id}/season/{n}: the season with its episodes; an episode without a runtime gets the show's. */
    static Season season(JSONObject o, int fallbackRuntime) {
        List<Episode> episodes = new ArrayList<>();
        JSONArray a = o.optJSONArray("episodes");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e == null) continue;
            int n = e.optInt("episode_number", i + 1);
            int runtime = e.isNull("runtime") ? 0 : e.optInt("runtime");
            String name = e.optString("name", "");
            if (name.isEmpty()) name = "Episode " + n;
            episodes.add(new Episode(n, name, runtime > 0 ? runtime : fallbackRuntime));
        }
        return new Season(o.optInt("season_number"), episodes.size(), episodes);
    }

    // ---- People and search ----------------------------------------------------------------------------------------

    /**
     * /search/multi: the titles and people, in TMDB's order, and what each person is known for. A title without
     * artwork and a person without a photo are left out: TMDB lists many namesakes and stubs ("amy adams" finds six
     * people of that name), and those are the ones a card could only show blank.
     */
    static Catalog.Found search(JSONObject o) {
        List<Title> titles = new ArrayList<>();
        List<Person> people = new ArrayList<>();
        Map<Integer, List<Title>> knownFor = new HashMap<>();
        JSONArray results = o.optJSONArray("results");
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            if ("person".equals(r.optString("media_type"))) {
                Person p = person(r, r.optString("known_for_department", ""));
                if (p == null || p.photo == null || r.optBoolean("adult")) continue;
                people.add(p);
                knownFor.put(p.id, summaries(r.optJSONArray("known_for"), null));
                continue;
            }
            Title t = summary(r, null);
            if (t != null && t.art != null) titles.add(t);
        }
        return new Catalog.Found(titles, people, knownFor);
    }

    /**
     * /person/{id} with combined_credits: the person with their photo, and up to 30 titles they are known for, most
     * voted first, leaving out talk shows, news, reality TV and appearances as themselves.
     */
    static Catalog.Credits credits(JSONObject o, Person asked) {
        Person p = new Person(o.optString("name", asked.name), asked.role, o.optInt("id", asked.id),
                orNull(path(o, "profile_path"), asked.photo));
        List<JSONObject> items = new ArrayList<>();
        JSONObject combined = o.optJSONObject("combined_credits");
        for (String part : new String[]{"cast", "crew"}) {
            JSONArray a = combined == null ? null : combined.optJSONArray(part);
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c != null && knownFor(c)) items.add(c);
            }
        }
        // Stable, so equal votes keep TMDB's order (cast before crew).
        items.sort((a, b) -> Integer.compare(b.optInt("vote_count"), a.optInt("vote_count")));
        Map<String, Title> out = new LinkedHashMap<>();
        for (JSONObject c : items) {
            Title t = summary(c, null);
            if (t != null && !out.containsKey(t.id)) out.put(t.id, t);
            if (out.size() == KNOWN_FOR_MAX) break;
        }
        return new Catalog.Credits(p, new ArrayList<>(out.values()));
    }

    private static boolean knownFor(JSONObject c) {
        int[] genres = ints(c.optJSONArray("genre_ids"));
        for (int g : genres) for (int not : NOT_KNOWN_FOR) if (g == not) return false;
        String character = c.optString("character", "").trim().toLowerCase(java.util.Locale.ROOT);
        return !(character.startsWith("self") || character.startsWith("himself") || character.startsWith("herself"));
    }

    // ---- Pieces ---------------------------------------------------------------------------------------------------

    private static Kind kind(String mediaType, Kind hint) {
        if ("movie".equals(mediaType)) return Kind.MOVIE;
        if ("tv".equals(mediaType)) return Kind.SHOW;
        return mediaType.isEmpty() ? hint : null;
    }

    private static String name(JSONObject o, Kind kind) {
        String n = o.optString(kind == Kind.MOVIE ? "title" : "name", "");
        return n.isEmpty() ? o.optString(kind == Kind.MOVIE ? "original_title" : "original_name", "") : n;
    }

    /** "2016-11-10" gives 2016; anything else 0. */
    static int year(String date) {
        if (date == null || date.length() < 4) return 0;
        for (int i = 0; i < 4; i++) if (!Character.isDigit(date.charAt(i))) return 0;
        return Integer.parseInt(date.substring(0, 4));
    }

    /** TMDB's average vote, to one decimal as the screens show it. */
    private static double score(JSONObject o) {
        double v = o.optDouble("vote_average", 0);
        if (Double.isNaN(v) || v < 0) return 0;
        return Math.round(v * 10) / 10.0;
    }

    /** An image path such as "/abc.jpg", or null. */
    private static String path(JSONObject o, String key) {
        if (o.isNull(key)) return null;
        String p = o.optString(key, "");
        return TmdbApi.isImagePath(p) ? p : null;
    }

    private static String orNull(String a, String b) {
        return a != null ? a : b;
    }

    private static int[] ints(JSONArray a) {
        if (a == null) return new int[0];
        int[] out = new int[a.length()];
        for (int i = 0; i < a.length(); i++) out[i] = a.optInt(i);
        return out;
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private static Person person(JSONObject o, String role) {
        if (o == null) return null;
        String name = o.optString("name", "");
        if (name.isEmpty()) return null;
        return new Person(name, role, o.optInt("id"), path(o, "profile_path"));
    }

    private static List<Person> cast(JSONObject credits) {
        List<Person> out = new ArrayList<>();
        JSONArray a = credits == null ? null : credits.optJSONArray("cast");
        for (int i = 0; a != null && i < a.length() && out.size() < CAST_MAX; i++) {
            JSONObject c = a.optJSONObject(i);
            if (c == null) continue;
            String role = c.optString("character", "");
            Person p = person(c, role.isEmpty() ? "Actor" : role);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** The jobs a title's crew shows, in this order: who directed it, wrote it, filmed it and scored it. */
    private static final String[][] JOBS = {
            {"Director", "Director"},
            {"Screenplay", "Writer"},
            {"Writer", "Writer"},
            {"Novel", "Novel"},
            {"Director of Photography", "Cinematography"},
            {"Original Music Composer", "Music"},
    };

    private static List<Person> crew(JSONObject credits, Kind kind) {
        List<Person> out = new ArrayList<>();
        JSONArray a = credits == null ? null : credits.optJSONArray("crew");
        if (a == null) return out;
        // A show's credits list its producers and writers by series; only a movie's say who did each job.
        if (kind != Kind.MOVIE) return out;
        for (String[] job : JOBS) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null || !job[0].equals(c.optString("job"))) continue;
                Person p = person(c, job[1]);
                if (p != null) out.add(p);
            }
        }
        return out;
    }

    private static List<Person> dedupe(List<Person> people, int max) {
        List<Person> out = new ArrayList<>();
        for (Person p : people) {
            boolean seen = false;
            for (Person q : out) seen |= q.name.equals(p.name) && q.role.equals(p.role);
            if (!seen) out.add(p);
            if (out.size() == max) break;
        }
        return out;
    }

    private static List<Season> seasons(JSONArray a) {
        List<Season> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null) continue;
            int n = s.optInt("season_number");
            int count = s.optInt("episode_count");
            // Season 0 is TMDB's "Specials"; the prototype's shows start at 1.
            if (n > 0 && count > 0) out.add(new Season(n, count, Collections.emptyList()));
        }
        return out;
    }

    /** A show's typical episode length: TMDB's episode_run_time averaged, else its last or next episode's. */
    static int episodeRuntime(JSONObject o) {
        int[] runs = ints(o.optJSONArray("episode_run_time"));
        int sum = 0, n = 0;
        for (int r : runs) {
            if (r > 0) {
                sum += r;
                n++;
            }
        }
        if (n > 0) return Math.round(sum / (float) n);
        for (String key : new String[]{"last_episode_to_air", "next_episode_to_air"}) {
            JSONObject e = o.optJSONObject(key);
            if (e != null && e.optInt("runtime") > 0) return e.optInt("runtime");
        }
        return 0;
    }

    /** The US certification from /movie's release_dates. */
    static String movieRating(JSONObject releaseDates) {
        JSONObject us = country(releaseDates);
        JSONArray dates = us == null ? null : us.optJSONArray("release_dates");
        if (dates == null) return "";
        int[] types = new int[dates.length()];
        String[] certs = new String[dates.length()];
        for (int i = 0; i < dates.length(); i++) {
            JSONObject d = dates.optJSONObject(i);
            types[i] = d == null ? 0 : d.optInt("type");
            certs[i] = d == null ? "" : d.optString("certification", "");
        }
        return Certifications.movie(types, certs);
    }

    /** The US rating from /tv's content_ratings. */
    static String tvRating(JSONObject contentRatings) {
        JSONObject us = country(contentRatings);
        return us == null ? "" : Certifications.tidy(us.optString("rating", ""));
    }

    private static JSONObject country(JSONObject o) {
        JSONArray results = o == null ? null : o.optJSONArray("results");
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r != null && Certifications.COUNTRY.equals(r.optString("iso_3166_1"))) return r;
        }
        return null;
    }

    /** The YouTube id of the title's trailer: an official one first, else any; null when there is none. */
    static String trailer(JSONObject videos) {
        JSONArray results = videos == null ? null : videos.optJSONArray("results");
        String any = null;
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject v = results.optJSONObject(i);
            if (v == null || !"YouTube".equals(v.optString("site")) || !"Trailer".equals(v.optString("type"))) continue;
            String key = v.optString("key", "");
            if (!key.matches("[A-Za-z0-9_-]{6,20}")) continue;
            if (v.optBoolean("official")) return key;
            if (any == null) any = key;
        }
        return any;
    }
}
