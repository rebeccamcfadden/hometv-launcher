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
}
