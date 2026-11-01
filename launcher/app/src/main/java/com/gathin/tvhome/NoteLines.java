package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A phone note's lines as titles to look up (BACKLOG.md, Phone notes): the text Apple Notes or Google Keep hands a
 * "Send to TV" Shortcut, one title a line. Checkboxes (Keep's ☐ and ☑, Markdown's [ ] and [x], ✓ and ✅),
 * bullets and numbering are taken off; a ticked or struck-through line means watched. A note's own title is skipped:
 * its first line, when the lines under it are a checklist or bullets and it isn't, or when it reads like a list's
 * name ("Movies to watch"). Headings ("Movies:", "# Shows") and blank
 * lines are not titles; a heading that names movies or shows says what the lines under it are. A year in brackets at
 * the end, "(2021)", narrows the search; "(tv)", "(series)", "(movie)" or a season ("S2", "season 2") say which kind.
 * A themoviedb.org link is the title itself. A title that appears twice counts once, the later line winning (so a
 * line ticked further down is watched). Pure Java, unit-tested (NoteLinesTest).
 */
final class NoteLines {
    private NoteLines() {}

    /** At most this many lines are read, and each is cut to this length: a note, not a library. */
    static final int MAX_LINES = 500, MAX_LENGTH = 200;

    static final class Line {
        /** What to search TMDB for: the line without its markers, year or kind. */
        final String query;
        /** The year in brackets, or 0. */
        final int year;
        /** Movie or show when the line itself says so ("(tv)", "S2", a link), else null: only that kind is searched. */
        final Kind kind;
        /** Movie or show when the note's title or a heading says so, else null: that kind is searched first. */
        final Kind hint;
        /** Ticked, checked or struck through: watched. */
        final boolean ticked;
        /** A title id from a themoviedb.org link ("movie:603"), else null. */
        final String id;
        /** The line's identity across sends: its query, year and kind, lower case with spaces collapsed. */
        final String key;

        Line(String query, int year, Kind kind, boolean ticked, String id) {
            this(query, year, kind, ticked, id, null);
        }

        Line(String query, int year, Kind kind, boolean ticked, String id, Kind hint) {
            this.query = query;
            this.year = year;
            this.kind = kind;
            this.hint = hint;
            this.ticked = ticked;
            this.id = id;
            this.key = id != null ? id : key(query) + (year > 0 ? " (" + year + ")" : "")
                    + (kind == null ? "" : kind == Kind.MOVIE ? " [movie]" : " [tv]");
        }

        @Override
        public String toString() {
            return (ticked ? "[x] " : "[ ] ") + key;
        }
    }

    private static final Pattern TICKED = Pattern.compile(
            "^(?:(?:[-*•+]|\\d{1,3}[.)])\\s*)?(?:☑|☒|✅|✔️?|✓|\\[[xX✓✔]\\])\\s*");
    private static final Pattern UNTICKED = Pattern.compile("^(?:(?:[-*•+]|\\d{1,3}[.)])\\s*)?(?:☐|□|⬜|\\[ ?\\])\\s*");
    private static final Pattern BULLET = Pattern.compile("^(?:[-*•+–—◦‣·▪●○]|\\d{1,3}[.)])\\s+");
    private static final Pattern STRUCK = Pattern.compile("^~~(.+)~~$");
    private static final Pattern YEAR = Pattern.compile("\\s*[(\\[]((?:19|20)\\d\\d)[)\\]]\\s*$");
    private static final Pattern KIND = Pattern.compile(
            "\\s*[(\\[](tv|tv show|show|series|serie|movie|film)[)\\]]\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEASON = Pattern.compile(
            "\\s*[-–—:,]?\\s*(?:\\bs\\d{1,2}(?:e\\d{1,3})?|\\bseason\\s+\\d{1,2})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile(
            "(?:https?://)?(?:www\\.)?themoviedb\\.org/(movie|tv)/(\\d{1,9})\\S*", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("^(?:#{1,6}\\s+.+|.{1,40}:)$");
    private static final Pattern MOVIES = Pattern.compile("^(?:#+\\s*)?(?:movies?|films?)\\s*:?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOWS = Pattern.compile(
            "^(?:#+\\s*)?(?:tv|tv shows?|shows?|series|tv series)\\s*:?$", Pattern.CASE_INSENSITIVE);

    static List<Line> parse(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        String[] raw = text.replace("\u2028", "\n").replace("\u2029", "\n").split("\r\n|\r|\n", -1);
        List<String> lines = new ArrayList<>();
        for (String r : raw) {
            if (lines.size() >= MAX_LINES) break;
            String s = r.replace('\u00A0', ' ').replace("\uFFFC", "").trim();
            if (s.length() > MAX_LENGTH) s = s.substring(0, MAX_LENGTH).trim();
            lines.add(s);
        }
        int first = firstTitleLine(lines);
        boolean listNote = listNote(lines);
        Map<String, Line> out = new LinkedHashMap<>();
        // The note's own title gives the first hint ("Movies to watch"); each heading after it, its own.
        Kind section = first > firstNonEmpty(lines) ? kindOf(lines.get(firstNonEmpty(lines))) : null;
        for (int i = first; i < lines.size(); i++) {
            String s = lines.get(i);
            if (s.isEmpty()) continue;
            if (MOVIES.matcher(s).matches()) {
                section = Kind.MOVIE;
                continue;
            }
            if (SHOWS.matcher(s).matches()) {
                section = Kind.SHOW;
                continue;
            }
            // A heading: "Movies:", "# Later", or in a checklist or bullet note any line without a marker
            // ("Shows to Watch" halfway down an Apple note). It is never a title; it may say movies or shows.
            if (!marked(s) && (HEADING.matcher(s).matches() || listNote)) {
                Kind k = kindOf(s);
                if (k != null) section = k;
                continue;
            }
            Line l = line(s, section);
            if (l == null) continue;
            out.remove(l.key);
            out.put(l.key, l);
        }
        return Collections.unmodifiableList(new ArrayList<>(out.values()));
    }

    /** A checklist or bullet note: most of its non-empty lines carry a marker. */
    private static boolean listNote(List<String> lines) {
        int marked = 0, others = 0;
        for (String s : lines) {
            if (s.isEmpty()) continue;
            if (marked(s)) marked++;
            else others++;
        }
        return marked > 0 && marked >= others;
    }

    private static int firstNonEmpty(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) if (!lines.get(i).isEmpty()) return i;
        return 0;
    }

    private static final Pattern MOVIE_WORD = Pattern.compile("\\b(?:movies?|films?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW_WORD = Pattern.compile("\\b(?:shows?|series|tv)\\b", Pattern.CASE_INSENSITIVE);

    /** What a heading or a note's title says the lines under it are: movies, shows, or (both or neither) null. */
    static Kind kindOf(String heading) {
        boolean movie = MOVIE_WORD.matcher(heading).find(), show = SHOW_WORD.matcher(heading).find();
        return movie == show ? null : movie ? Kind.MOVIE : Kind.SHOW;
    }

    /**
     * Where the titles start: after the note's own title when the note is a checklist or bullets and its first line
     * isn't one of them (Apple Notes and Keep both put the title first). A plain note starts at its first line.
     */
    private static int firstTitleLine(List<String> lines) {
        int first = -1, marked = 0, others = 0;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            if (s.isEmpty()) continue;
            if (first < 0) {
                first = i;
                continue;
            }
            if (marked(s)) marked++;
            else others++;
        }
        if (first < 0) return lines.size();
        String top = lines.get(first);
        if (marked(top)) return first;
        if (marked > 0 && marked >= others) return first + 1;
        return marked + others > 0 && LIST_TITLE.matcher(top).find() ? first + 1 : first;
    }

    /** A plain note's first line that names the list rather than a title: "Movies to watch", "Our watchlist". */
    private static final Pattern LIST_TITLE = Pattern.compile(
            "\\b(?:watch ?list|to watch|to see|movies|films|shows|series)\\b", Pattern.CASE_INSENSITIVE);

    private static boolean marked(String s) {
        return TICKED.matcher(s).find() || UNTICKED.matcher(s).find() || BULLET.matcher(s).find()
                || STRUCK.matcher(s).matches();
    }

    private static Line line(String s, Kind section) {
        boolean ticked = false;
        Matcher m = TICKED.matcher(s);
        if (m.find()) {
            ticked = true;
            s = s.substring(m.end());
        } else if ((m = UNTICKED.matcher(s)).find()) {
            s = s.substring(m.end());
        } else if ((m = BULLET.matcher(s)).find()) {
            s = s.substring(m.end());
        }
        m = STRUCK.matcher(s.trim());
        if (m.matches()) {
            ticked = true;
            s = m.group(1);
        }
        s = s.trim();
        m = LINK.matcher(s);
        if (m.find()) {
            String id = TmdbAccountApi.id(m.group(1).toLowerCase(Locale.ROOT), Long.parseLong(m.group(2)));
            if (id != null) return new Line(s, 0, "tv".equalsIgnoreCase(m.group(1)) ? Kind.SHOW : Kind.MOVIE, ticked, id, null);
        }
        Kind kind = null;
        int year = 0;
        // The year, kind and season come off the end in any order: "Dune (2021) (movie)", "Fargo S2 (2014)".
        for (boolean more = true; more; ) {
            more = false;
            if ((m = YEAR.matcher(s)).find() && year == 0) {
                year = Integer.parseInt(m.group(1));
                s = s.substring(0, m.start());
                more = true;
            }
            if ((m = KIND.matcher(s)).find()) {
                String k = m.group(1).toLowerCase(Locale.ROOT);
                kind = k.equals("movie") || k.equals("film") ? Kind.MOVIE : Kind.SHOW;
                s = s.substring(0, m.start());
                more = true;
            }
            if ((m = SEASON.matcher(s)).find() && m.start() > 0) {
                kind = Kind.SHOW;
                s = s.substring(0, m.start());
                more = true;
            }
        }
        s = s.replaceAll("[\\s\\-–—:,]+$", "").trim();
        if (s.isEmpty() || !hasLetterOrDigit(s)) return null;
        return new Line(s.replace('’', '\'').replace('‘', '\''), year, kind, ticked, null, kind == null ? section : null);
    }

    private static boolean hasLetterOrDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetterOrDigit(s.charAt(i))) return true;
        return false;
    }

    /** Lower case, inner spaces collapsed, curly quotes straightened: "Friends’ tips" and "friends' tips" match. */
    static String key(String s) {
        return s.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
                .replaceAll("\\s+", " ").trim();
    }
}
