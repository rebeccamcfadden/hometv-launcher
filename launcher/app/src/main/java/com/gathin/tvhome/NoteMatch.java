package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns a phone note's lines into titles on a list (BACKLOG.md, Phone notes). Each line is looked up once: its answer,
 * a title id or "not found", is kept per list ({@link Memory}), so sending the same note again only looks up the new
 * lines, and a title taken off the list on the TV is not put back. Adding only: a line gone from the note takes
 * nothing off. A ticked line's title goes to Seen and comes off the list.
 *
 * <p>Matching: a TMDB link is the title. Otherwise TMDB's search (movies or shows when the line says which, shows or
 * movies first under a heading, with its year), and the result whose title, or original title, is the line's (ignoring
 * case, accents and punctuation) and, when the line has a year, from that year or the next; the most popular of those.
 * TMDB's first answer also counts when it and the line differ only by a leading "The". Anything else is asked on the
 * TV (the project lead, 2026-11-01): the line is kept with TMDB's best three answers ({@link Ask}) for Launcher
 * settings' picker, a slip of the keyboard first; "None of these" is remembered ({@link #DISMISSED}) and not asked
 * again. A changed line is looked up again; an unchanged one is not.
 * Blocking and pure Java over {@link Fetch}; unit-tested against canned TMDB answers (NoteMatchTest).
 */
final class NoteMatch {
    /** GET a TMDB v3 path ("/search/movie?…"); throws when TMDB can't be reached. */
    interface Fetch {
        JSONObject get(String path) throws IOException;
    }

    /** How a run is going: lines dealt with so far of the note's lines. Called on the run's thread. */
    interface Progress {
        void step(int done, int total);
    }

    /** A line's remembered answer when the household picked "None of these": it isn't listed or asked again. */
    static final String DISMISSED = "-";

    /** TMDB answers offered for a line that wasn't matched exactly. */
    static final int ASK_TOP = 3;

    /**
     * A line that wasn't matched exactly, as written, with TMDB's best answers to pick from on the TV (none when TMDB
     * had none). {@link #key} is the line's key in {@link Memory}; a ticked line's pick goes to Seen. {@link #looked}
     * tells answers TMDB was asked for (perhaps none) from a line never looked up (a resend of a line looked up
     * before, or an older TV's saved text), which the picker looks up when it opens.
     */
    static final class Ask {
        final String key, query;
        final boolean ticked, looked;
        final List<Candidate> top;

        /** {@code top} null: not looked up. */
        Ask(String key, String query, boolean ticked, List<Candidate> top) {
            this(key, query, ticked, top, top != null);
        }

        Ask(String key, String query, boolean ticked, List<Candidate> top, boolean looked) {
            this.key = key;
            this.query = query;
            this.ticked = ticked;
            this.looked = looked;
            this.top = top == null ? Collections.<Candidate>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(top));
        }

        /** The same line with the answers TMDB gave for it (perhaps none). */
        Ask with(List<Candidate> other) {
            return new Ask(key, query, ticked, other, true);
        }

        /** The same line without answers, as it shows while they are looked up. */
        Ask unanswered() {
            return new Ask(key, query, ticked, null, false);
        }

        JSONObject toJson() throws JSONException {
            JSONArray a = new JSONArray();
            for (Candidate c : top) {
                a.put(new JSONObject().put("id", c.id).put("title", c.title).put("year", c.year)
                        .put("poster", c.poster == null ? "" : c.poster));
            }
            return new JSONObject().put("key", key).put("query", query).put("ticked", ticked).put("looked", looked)
                    .put("top", a);
        }

        /** An ask as saved, or as an older TV saved an unmatched line (just its text); null if unreadable. */
        static Ask fromJson(Object o) {
            if (o instanceof String) {
                String q = ((String) o).trim();
                return q.isEmpty() ? null : new Ask(NoteLines.key(q), q, false, null);
            }
            if (!(o instanceof JSONObject)) return null;
            JSONObject j = (JSONObject) o;
            String q = j.optString("query", "").trim();
            if (q.isEmpty()) return null;
            List<Candidate> top = new ArrayList<>();
            JSONArray a = j.optJSONArray("top");
            for (int i = 0; a != null && i < a.length() && top.size() < ASK_TOP; i++) {
                JSONObject c = a.optJSONObject(i);
                String id = c == null ? "" : c.optString("id", "");
                if (!id.matches("(movie|tv):\\d+")) continue;
                String poster = c.optString("poster", "");
                top.add(new Candidate(id, c.optString("title", ""), "", c.optInt("year", 0),
                        TmdbApi.isImagePath(poster) ? poster : null, 0, i));
            }
            // Saved before "looked" was: answers mean it was looked up; none, that it may not have been.
            return new Ask(j.optString("key", NoteLines.key(q)), q, j.optBoolean("ticked", false), top,
                    j.optBoolean("looked", !top.isEmpty()));
        }
    }

    /** What a line came to: its title id when matched exactly (or null), and TMDB's best answers to ask about. */
    static final class Found {
        final String id;
        final List<Candidate> top;

        Found(String id, List<Candidate> top) {
            this.id = id;
            this.top = top == null ? Collections.<Candidate>emptyList() : top;
        }
    }

    /** What a note asks for, worked out. */
    static final class Plan {
        /** Titles to add to the list, in the note's order. */
        final List<String> add;
        /** Titles ticked in the note: onto Seen, off the list. */
        final List<String> seen;
        /** Lines that matched nothing exactly, as written (without markers). */
        final List<String> unmatched;
        /** The same lines, with what to ask about each. */
        final List<Ask> asks;
        /** Lines in the note, and those looked up this time (the rest were known). */
        final int lines, looked;
        /** Lines that couldn't be looked up (offline): they are tried again next time. */
        final int failed;

        Plan(List<String> add, List<String> seen, List<Ask> asks, int lines, int looked, int failed) {
            this.add = add;
            this.seen = seen;
            this.asks = asks;
            List<String> u = new ArrayList<>();
            for (Ask a : asks) u.add(a.query);
            this.unmatched = u;
            this.lines = lines;
            this.looked = looked;
            this.failed = failed;
        }
    }

    /**
     * Each list's lines and their answers, saved with the household's state: line key → title id, or "" for not
     * found. Not thread-safe; the run's thread owns it while a note is matched.
     */
    static final class Memory {
        private final Map<String, Map<String, String>> lists = new LinkedHashMap<>();

        String get(String list, String line) {
            Map<String, String> m = lists.get(NoteLines.key(list));
            return m == null ? null : m.get(line);
        }

        void put(String list, String line, String id) {
            String k = NoteLines.key(list);
            Map<String, String> m = lists.get(k);
            if (m == null) lists.put(k, m = new LinkedHashMap<>());
            m.put(line, id);
        }

        /** Drops everything remembered for the list. */
        void forget(String list) {
            lists.remove(NoteLines.key(list));
        }

        /** Lines remembered for the list, for the pane's "not matched" list. */
        Map<String, String> of(String list) {
            Map<String, String> m = lists.get(NoteLines.key(list));
            return m == null ? Collections.<String, String>emptyMap() : Collections.unmodifiableMap(m);
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                for (Map.Entry<String, Map<String, String>> e : lists.entrySet()) o.put(e.getKey(), new JSONObject(e.getValue()));
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
            return o;
        }

        static Memory fromJson(JSONObject o) {
            Memory m = new Memory();
            if (o == null) return m;
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String list = it.next();
                JSONObject lines = o.optJSONObject(list);
                if (lines == null) continue;
                for (java.util.Iterator<String> l = lines.keys(); l.hasNext(); ) {
                    String line = l.next();
                    m.put(list, line, lines.optString(line, ""));
                }
            }
            return m;
        }
    }

    private final Fetch fetch;

    NoteMatch(Fetch fetch) {
        this.fetch = fetch;
    }

    /**
     * Works out what the note asks of the list. {@code onList} is what the list holds now, so a title already there
     * isn't added again.
     */
    Plan plan(String list, List<NoteLines.Line> lines, Memory memory, Set<String> onList, Progress progress) {
        // Every line counts towards "6 of 12", the known ones at once, so the pill has its total from the start.
        int total = lines.size();
        Set<String> add = new LinkedHashSet<>(), seen = new LinkedHashSet<>();
        List<Ask> asks = new ArrayList<>();
        int done = 0, looked = 0, failed = 0;
        if (progress != null) progress.step(0, total);
        for (NoteLines.Line l : lines) {
            String known = l.id != null ? l.id : memory.get(list, l.key);
            boolean fresh = known == null;
            List<Candidate> top = null;
            if (fresh) {
                try {
                    Found f = find(l);
                    known = f.id;
                    top = f.top;
                } catch (IOException e) {
                    failed++;
                    looked++;
                    if (progress != null) progress.step(++done, total);
                    continue;
                }
                memory.put(list, l.key, known == null ? "" : known);
                looked++;
            } else if (l.id != null) {
                memory.put(list, l.key, l.id);
            }
            if (progress != null) progress.step(++done, total);
            if (DISMISSED.equals(known)) continue;
            if (known == null || known.isEmpty()) {
                asks.add(new Ask(l.key, l.query, l.ticked, top));
            } else if (l.ticked) {
                seen.add(known);
            } else if (fresh && !onList.contains(known)) {
                // Only a line seen for the first time adds: one the household took off on the TV stays off.
                add.add(known);
            }
        }
        add.removeAll(seen);
        return new Plan(new ArrayList<>(add), new ArrayList<>(seen), asks, lines.size(), looked, failed);
    }

    /** The line's title id, or null when nothing matches exactly. */
    String match(NoteLines.Line l) throws IOException {
        return find(l).id;
    }

    /** The line's title id, or TMDB's best answers to ask about when nothing matches exactly. */
    Found find(NoteLines.Line l) throws IOException {
        if (l.id != null) return new Found(l.id, null);
        if (l.kind != null) {
            List<Candidate> found = search(l.kind == Kind.MOVIE ? "movie" : "tv", l);
            return new Found(exact(l, found), top(l, found));
        }
        if (l.hint != null) {
            // Under "Movies to watch" a line is most likely a movie: ask for those first, then shows.
            String first = l.hint == Kind.MOVIE ? "movie" : "tv";
            List<Candidate> a = search(first, l);
            String hit = exact(l, a);
            if (hit != null) return new Found(hit, top(l, a));
            List<Candidate> b = search(first.equals("movie") ? "tv" : "movie", l);
            hit = exact(l, b);
            if (hit != null) return new Found(hit, top(l, b));
            List<Candidate> both = new ArrayList<>(top(l, a));
            both.addAll(top(l, b));
            return new Found(null, both.subList(0, Math.min(ASK_TOP, both.size())));
        }
        List<Candidate> found = new ArrayList<>();
        found.addAll(search("movie", l));
        found.addAll(search("tv", l));
        return new Found(exact(l, found), top(l, found));
    }

    private List<Candidate> search(String type, NoteLines.Line l) throws IOException {
        String path = "/search/" + type + "?query=" + URLEncoder.encode(l.query, "UTF-8") + "&include_adult=false&page=1"
                + (l.year > 0 ? (type.equals("movie") ? "&year=" : "&first_air_date_year=") + l.year : "");
        JSONObject o = fetch.get(path);
        List<Candidate> out = new ArrayList<>();
        JSONArray results = o == null ? null : o.optJSONArray("results");
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null || r.optBoolean("adult")) continue;
            String id = TmdbAccountApi.id(type, r.optLong("id"));
            if (id == null) continue;
            String date = r.optString(type.equals("movie") ? "release_date" : "first_air_date", "");
            int year = date.length() >= 4 && date.substring(0, 4).matches("\\d{4}") ? Integer.parseInt(date.substring(0, 4)) : 0;
            out.add(new Candidate(id, r.optString(type.equals("movie") ? "title" : "name", ""),
                    r.optString(type.equals("movie") ? "original_title" : "original_name", ""), year,
                    TmdbApi.isImagePath(r.optString("poster_path", null)) ? r.optString("poster_path") : null,
                    r.optDouble("popularity", 0), i));
        }
        return out;
    }

    /** One of TMDB's answers: its id ("movie:603"), title, year (0 unknown) and poster path (or null). */
    static final class Candidate {
        final String id, title, original, poster;
        final int year, rank;
        final double popularity;

        Candidate(String id, String title, String original, int year, String poster, double popularity, int rank) {
            this.id = id;
            this.title = title;
            this.original = original;
            this.year = year;
            this.poster = poster;
            this.popularity = popularity;
            this.rank = rank;
        }
    }

    /** The exact match among TMDB's answers, or null: see the class comment. */
    static String exact(NoteLines.Line l, List<Candidate> found) {
        String want = fold(l.query);
        if (want.isEmpty()) return null;
        Candidate exact = null;
        for (Candidate c : found) {
            if (!want.equals(fold(c.title)) && !want.equals(fold(c.original))) continue;
            if (l.year > 0 && c.year != 0 && c.year != l.year && c.year != l.year + 1) continue;
            if (exact == null || c.popularity > exact.popularity) exact = c;
        }
        if (exact != null) return exact.id;
        // TMDB's first answer, when it is the line but for a leading "The" ("Birdcage" for "The Birdcage").
        Candidate first = null;
        for (Candidate c : found) if (c.rank == 0 && (first == null || c.popularity > first.popularity)) first = c;
        if (first == null) return null;
        if (l.year > 0 && first.year != 0 && Math.abs(first.year - l.year) > 1) return null;
        String t = fold(first.title);
        return t.equals("the" + want) || want.equals("the" + t) ? first.id : null;
    }

    /**
     * TMDB's best {@link #ASK_TOP} answers for a line it didn't match exactly: a slip of the keyboard first
     * ("Inglourious Basterds" for "Inglorious basterds"), then in TMDB's order, the more popular first between a movie
     * and a show at the same place; one of the line's year (or the next) before others.
     */
    static List<Candidate> top(NoteLines.Line l, List<Candidate> found) {
        String want = fold(l.query);
        List<Candidate> sorted = new ArrayList<>(found);
        Collections.sort(sorted, (a, b) -> {
            int c = Boolean.compare(!close(want, fold(a.title)) && !close(want, fold(a.original)),
                    !close(want, fold(b.title)) && !close(want, fold(b.original)));
            if (c != 0) return c;
            if (l.year > 0) {
                c = Boolean.compare(!near(a.year, l.year), !near(b.year, l.year));
                if (c != 0) return c;
            }
            c = Integer.compare(a.rank, b.rank);
            return c != 0 ? c : Double.compare(b.popularity, a.popularity);
        });
        List<Candidate> out = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (Candidate c : sorted) {
            if (out.size() >= ASK_TOP) break;
            if (ids.add(c.id)) out.add(c);
        }
        return out;
    }

    private static boolean near(int year, int want) {
        return year == want || year == want + 1;
    }

    /** Within a slip of the keyboard, a leading "the" aside: one letter in six or more, two in twelve or more. */
    static boolean close(String want, String title) {
        String a = want.startsWith("the") ? want.substring(3) : want;
        String b = title.startsWith("the") ? title.substring(3) : title;
        int n = Math.min(a.length(), b.length());
        int allowed = n >= 12 ? 2 : n >= 6 ? 1 : 0;
        return allowed > 0 && Math.abs(a.length() - b.length()) <= allowed && distance(a, b, allowed) <= allowed;
    }

    /** Levenshtein distance, giving up (returning max + 1) once it can't be max or less. */
    private static int distance(String a, String b, int max) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > max) return max + 1;
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** Lower case, no accents, only letters and digits, "&" as "and": "Amélie", "amelie" and "AMÉLIE!" fold alike. */
    static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace("&", "and"), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }
}
