package com.hometv.launcher;

import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The phone remote's small rules (F27, F39), apart from the views so they are tested on the JVM. */
final class PhoneRemoteKeys {
    private PhoneRemoteKeys() {}

    /** A key the page sends, as the remote's key code; KEYCODE_UNKNOWN for anything else (Home is the Bridge's). */
    static int keyCode(String key) {
        switch (key) {
            case "up": return KeyEvent.KEYCODE_DPAD_UP;
            case "down": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "left": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "right": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "ok": return KeyEvent.KEYCODE_DPAD_CENTER;
            case "back": return KeyEvent.KEYCODE_BACK;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    /** F39: the catalogue's titles whose name holds the query, ignoring case; names that start with it first. */
    static List<Title> matching(List<Title> all, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Title> starts = new ArrayList<>(), holds = new ArrayList<>();
        if (q.isEmpty()) return starts;
        for (Title t : all) {
            String name = t.name.toLowerCase(Locale.ROOT);
            if (name.startsWith(q)) starts.add(t);
            else if (name.contains(q)) holds.add(t);
        }
        starts.addAll(holds);
        return starts;
    }

    /** F39: {@code first}, then {@code then}'s titles not already there, by id. */
    static List<Title> merge(List<Title> first, List<Title> then) {
        Map<String, Title> out = new LinkedHashMap<>();
        for (Title t : first) out.put(t.id, t);
        if (then != null) for (Title t : then) if (!out.containsKey(t.id)) out.put(t.id, t);
        return new ArrayList<>(out.values());
    }

    /** TMDB is asked only from two characters (TmdbCatalog.searchPage, SearchPage); shorter, the TV's own titles. */
    static final int TMDB_MIN = 2;

    /** How long a phone's search waits for TMDB before it answers with the TV's own matches. */
    static final long SEARCH_FALLBACK_MS = 2500;

    static boolean asksTmdb(String query) {
        return query != null && query.trim().length() >= TMDB_MIN;
    }

    /** Passes on only the first answer: TMDB's or the fallback's, whichever comes first. Main thread. */
    static <T> java.util.function.Consumer<T> once(java.util.function.Consumer<T> done) {
        boolean[] given = {false};
        return t -> {
            if (given[0]) return;
            given[0] = true;
            done.accept(t);
        };
    }

    /** What a phone's Enter does on Search: expand now, wait for TMDB's answer to this query, or nothing. */
    enum Enter { EXPAND, WAIT, NOTHING }

    /**
     * Enter from a phone (F27): the full results when there are matches; while TMDB hasn't answered this query yet
     * ({@code asking}), it waits for the answer instead of being lost.
     */
    static Enter onEnter(String query, boolean expanded, boolean hasMatches, boolean asking) {
        if (expanded || query == null || query.isEmpty()) return Enter.NOTHING;
        if (hasMatches) return Enter.EXPAND;
        return asking ? Enter.WAIT : Enter.NOTHING;
    }

    /** A waiting Enter expands when the answer for its own query brings matches; a changed query drops it. */
    static boolean expandsOnAnswer(String enterFor, String query, boolean expanded, boolean hasMatches) {
        return enterFor != null && enterFor.equals(query) && !expanded && hasMatches;
    }
}