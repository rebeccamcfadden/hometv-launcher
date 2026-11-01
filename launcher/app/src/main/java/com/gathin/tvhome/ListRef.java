package com.gathin.tvhome;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which TMDB list a household list is (BACKLOG.md, Shown TMDB lists): a setting that is either a list's
 * number, as in themoviedb.org/list/8221116, or its name, matched without regard to case or spaces at the ends among
 * the signed-in account's lists. A number also reaches a public list of another account, such as a friend's. Blank
 * means none. Pure Java, unit-tested (ListRefTest).
 */
final class ListRef {
    static final ListRef NONE = new ListRef(0, "");

    /** The list's number, or 0 when it is chosen by {@link #name}. */
    final long id;
    final String name;

    private ListRef(long id, String name) {
        this.id = id;
        this.name = name;
    }

    private static final Pattern NUMBER = Pattern.compile("(\\d{1,12})");
    private static final Pattern LINK = Pattern.compile(".*/list/(\\d{1,12})(?:[-?#/].*)?");

    /**
     * "8221116" or a link such as "https://www.themoviedb.org/list/8221116-our-picks" is a number; anything else a
     * name. A name made only of digits therefore can't be chosen by name; use its number.
     */
    static ListRef parse(String setting) {
        String s = setting == null ? "" : setting.trim();
        if (s.isEmpty()) return NONE;
        Matcher m = NUMBER.matcher(s);
        if (!m.matches()) m = LINK.matcher(s);
        if (m.matches()) {
            long id = Long.parseLong(m.group(1));
            if (id > 0) return new ListRef(id, "");
        }
        return new ListRef(0, s);
    }

    /** A list chosen by its name only, even one made of digits (a phone note's list, NoteImport). */
    static ListRef named(String name) {
        String s = name == null ? "" : name.trim();
        return s.isEmpty() ? NONE : new ListRef(0, s);
    }

    boolean isNone() {
        return id == 0 && name.isEmpty();
    }

    /**
     * The list's number: its own, or the first of the account's lists with the name (the oldest, which has the lowest
     * number, when two share it). 0 when no list has it.
     */
    long resolve(List<TmdbAccountParse.ListInfo> accountLists) {
        if (id > 0) return id;
        if (name.isEmpty() || accountLists == null) return 0;
        String want = key(name);
        long best = 0;
        for (TmdbAccountParse.ListInfo l : accountLists) {
            if (key(l.name).equals(want) && (best == 0 || l.id < best)) best = l.id;
        }
        return best;
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return id > 0 ? "list " + id : name.isEmpty() ? "none" : "\"" + name + "\"";
    }
}
