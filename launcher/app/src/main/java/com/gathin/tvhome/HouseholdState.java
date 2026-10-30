package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The household's state that outlives a screen (design_preview/src/core/store.ts): My list, Seen and Favorites (sets
 * of title ids), recent searches (up to 4, newest first, lower case, no repeats) and recently viewed titles (up to
 * 10, newest first). Pure Java with its own small JSON reader and writer, so it is unit-tested on the JVM;
 * {@link Household} keeps it in SharedPreferences. The defaults are the prototype's mock seed (BACKLOG.md).
 */
final class HouseholdState {
    static final int MAX_RECENT = 4;
    static final int MAX_VIEWED = 10;

    private final Set<String> myList;
    private final Set<String> seen;
    private final Set<String> favorites;
    private final List<String> recent;
    private final List<String> viewed;

    private HouseholdState(Set<String> myList, Set<String> seen, Set<String> favorites, List<String> recent,
                           List<String> viewed) {
        this.myList = myList;
        this.seen = seen;
        this.favorites = favorites;
        this.recent = recent;
        this.viewed = viewed;
    }

    /**
     * store.ts's defaults: My list holds the catalogue's titles on the MY list; seen knives-out and budapest;
     * favourite arrival; four recent searches; six recently viewed. The ids are the catalogue's own
     * ({@link Catalog#fromMockId}): the real catalogue's TMDB ids, without the mock titles TMDB doesn't have.
     */
    static HouseholdState defaults(Catalog catalog) {
        Set<String> my = new LinkedHashSet<>();
        for (Title t : catalog.all()) if (t.isOn(ListId.MY)) my.add(t.id);
        return new HouseholdState(my,
                new LinkedHashSet<>(ids(catalog, "knives-out", "budapest")),
                new LinkedHashSet<>(ids(catalog, "arrival")),
                new ArrayList<>(Arrays.asList("dune", "amy adams", "sci-fi", "carnival row")),
                new ArrayList<>(ids(catalog, "arrival", "dune", "past-lives", "coda", "moon", "severance")));
    }

    private static List<String> ids(Catalog catalog, String... mockIds) {
        return migrate(catalog, Arrays.asList(mockIds));
    }

    /** Ids in the catalogue's terms, in order, without repeats; an id it has no title for drops out. */
    private static List<String> migrate(Catalog catalog, List<String> ids) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : ids) {
            String now = catalog.fromMockId(id);
            if (now != null) out.add(now);
        }
        return new ArrayList<>(out);
    }

    /**
     * Reads what {@link #toJson()} wrote. A key that is missing or not a list of strings keeps its default, as the
     * prototype's {@code {...defaults(), ...saved}} does; anything unreadable gives the defaults. Title ids stored
     * from the mock catalogue move to the catalogue's own (BACKLOG.md, Real catalogue).
     */
    static HouseholdState fromJson(String json, Catalog catalog) {
        HouseholdState s = defaults(catalog);
        if (json == null || json.trim().isEmpty()) return s;
        Object root;
        try {
            root = new Reader(json).document();
        } catch (IllegalArgumentException e) {
            return s;
        }
        if (!(root instanceof Map)) return s;
        Map<?, ?> m = (Map<?, ?>) root;
        List<String> v;
        if ((v = strings(m.get("myList"))) != null) replace(s.myList, migrate(catalog, v));
        if ((v = strings(m.get("seen"))) != null) replace(s.seen, migrate(catalog, v));
        if ((v = strings(m.get("favorites"))) != null) replace(s.favorites, migrate(catalog, v));
        if ((v = strings(m.get("recent"))) != null) replace(s.recent, cap(v, MAX_RECENT));
        if ((v = strings(m.get("viewed"))) != null) replace(s.viewed, cap(migrate(catalog, v), MAX_VIEWED));
        return s;
    }

    private static List<String> strings(Object o) {
        if (!(o instanceof List)) return null;
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) o) {
            if (!(item instanceof String)) return null;
            out.add((String) item);
        }
        return out;
    }

    private static List<String> cap(List<String> list, int max) {
        return list.size() > max ? list.subList(0, max) : list;
    }

    private static void replace(Collection<String> target, List<String> values) {
        target.clear();
        target.addAll(values);
    }

    String toJson() {
        StringBuilder b = new StringBuilder("{");
        field(b, "myList", myList).append(',');
        field(b, "seen", seen).append(',');
        field(b, "favorites", favorites).append(',');
        field(b, "recent", recent).append(',');
        field(b, "viewed", viewed);
        return b.append('}').toString();
    }

    // ---- Reading -----------------------------------------------------------------------------------------------

    boolean inMyList(String id) {
        return myList.contains(id);
    }

    boolean isSeen(String id) {
        return seen.contains(id);
    }

    boolean isFavorite(String id) {
        return favorites.contains(id);
    }

    /** My list's ids, in the order they were added. */
    List<String> myListIds() {
        return Collections.unmodifiableList(new ArrayList<>(myList));
    }

    /** Recent searches, newest first. */
    List<String> recent() {
        return Collections.unmodifiableList(new ArrayList<>(recent));
    }

    /** Recently viewed title ids, newest first. */
    List<String> viewed() {
        return Collections.unmodifiableList(new ArrayList<>(viewed));
    }

    /** Every title id the household keeps: My list, Seen, Favorites and Recently viewed, without repeats. */
    List<String> titleIds() {
        Set<String> all = new LinkedHashSet<>(myList);
        all.addAll(viewed);
        all.addAll(seen);
        all.addAll(favorites);
        return new ArrayList<>(all);
    }

    // ---- Changing ----------------------------------------------------------------------------------------------

    /** Returns whether the title is on My list now. */
    boolean toggleMyList(String id) {
        return toggle(myList, id);
    }

    boolean toggleSeen(String id) {
        return toggle(seen, id);
    }

    boolean toggleFavorite(String id) {
        return toggle(favorites, id);
    }

    private static boolean toggle(Set<String> set, String id) {
        if (!set.remove(id)) set.add(id);
        return set.contains(id);
    }

    /** Saves a search: trimmed and lower-cased, moved to the front, at most five kept. Returns false for blank. */
    boolean pushRecent(String query) {
        String v = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return false;
        pushFront(recent, v, MAX_RECENT);
        return true;
    }

    /** A title opened: moved to the front, at most ten kept. Returns false for a blank id. */
    boolean pushViewed(String id) {
        if (id == null || id.isEmpty()) return false;
        pushFront(viewed, id, MAX_VIEWED);
        return true;
    }

    private static void pushFront(List<String> list, String v, int max) {
        list.remove(v);
        list.add(0, v);
        while (list.size() > max) list.remove(list.size() - 1);
    }

    // ---- JSON ---------------------------------------------------------------------------------------------------

    private static StringBuilder field(StringBuilder b, String name, Collection<String> values) {
        quote(b, name).append(":[");
        boolean first = true;
        for (String v : values) {
            if (!first) b.append(',');
            quote(b, v);
            first = false;
        }
        return b.append(']');
    }

    private static StringBuilder quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"');
    }

    /** A small JSON reader: objects, arrays, strings, numbers, true, false and null. Throws on anything else. */
    private static final class Reader {
        private final String s;
        private int i;

        Reader(String s) {
            this.s = s;
        }

        Object document() {
            Object v = value();
            space();
            if (i != s.length()) throw new IllegalArgumentException("trailing text at " + i);
            return v;
        }

        private Object value() {
            space();
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) return word("true", Boolean.TRUE);
            if (s.startsWith("false", i)) return word("false", Boolean.FALSE);
            if (s.startsWith("null", i)) return word("null", null);
            return number();
        }

        private Object word(String w, Object v) {
            i += w.length();
            return v;
        }

        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            i++;
            space();
            if (peek() == '}') {
                i++;
                return out;
            }
            while (true) {
                space();
                if (peek() != '"') throw new IllegalArgumentException("expected a key at " + i);
                String key = string();
                space();
                expect(':');
                out.put(key, value());
                space();
                char c = next();
                if (c == '}') return out;
                if (c != ',') throw new IllegalArgumentException("expected , or } at " + (i - 1));
            }
        }

        private List<Object> array() {
            List<Object> out = new ArrayList<>();
            i++;
            space();
            if (peek() == ']') {
                i++;
                return out;
            }
            while (true) {
                out.add(value());
                space();
                char c = next();
                if (c == ']') return out;
                if (c != ',') throw new IllegalArgumentException("expected , or ] at " + (i - 1));
            }
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case 'n':
                        b.append('\n');
                        break;
                    case 'r':
                        b.append('\r');
                        break;
                    case 't':
                        b.append('\t');
                        break;
                    case 'b':
                        b.append('\b');
                        break;
                    case 'f':
                        b.append('\f');
                        break;
                    case 'u':
                        if (i + 4 > s.length()) throw new IllegalArgumentException("short \\u escape");
                        try {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("bad \\u escape at " + i);
                        }
                        i += 4;
                        break;
                    default:
                        b.append(e);
                }
            }
        }

        private Double number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("unexpected '" + s.charAt(i) + "' at " + i);
            try {
                return Double.valueOf(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad number at " + start);
            }
        }

        private void space() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw new IllegalArgumentException("expected " + c + " at " + (i - 1));
        }
    }
}
