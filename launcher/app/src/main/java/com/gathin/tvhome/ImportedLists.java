package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * The lists after My list (docs/rework.md, Lists by name, 2026-10-31): either the mock catalogue's Household and
 * Friends, which a TV keeps until it has had a TMDB account, or the TMDB lists shown in Choose lists or set in
 * config.xml, each with its TMDB name and the titles last read from TMDB (none until the first read), in chip order.
 * The TV reads these lists, and keeps the last copy of each for offline ({@link #save}, {@link #restore}); the title
 * menu puts a title on one or takes it off ({@link #withTitle}, Accounts#setOnShownList).
 * Pure Java, unit-tested (ImportedListsTest).
 */
final class ImportedLists {
    /** One shown TMDB list: the setting that asked for it, its number (0 until a name is found), name and titles. */
    static final class Entry {
        final ListRef ref;
        final long id;
        final String name;
        final List<String> ids;

        Entry(ListRef ref, long id, String name, Collection<String> ids) {
            this.ref = ref;
            this.id = Math.max(0, id > 0 ? id : ref.id);
            this.name = name == null ? "" : name;
            this.ids = ids == null ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(ids)));
        }

        ShownList shown() {
            return ShownList.tmdb(id, ref.name, name);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry)) return false;
            Entry e = (Entry) o;
            return id == e.id && name.equals(e.name) && ids.equals(e.ids) && ref.toString().equals(e.ref.toString());
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, name, ids, ref.toString());
        }
    }

    private boolean mock = true;
    private List<Entry> entries = Collections.emptyList();
    private List<ShownList> shown = ShownList.MOCK;
    private final Map<String, Set<String>> byKey = new HashMap<>();

    /** Whether the mock Household and Friends show (a TV that never had an account, with no list set). */
    boolean mock() {
        return mock;
    }

    /** Back to the mock lists; returns whether anything changed. */
    boolean setMock() {
        if (mock) return false;
        mock = true;
        entries = Collections.emptyList();
        shown = ShownList.MOCK;
        byKey.clear();
        return true;
    }

    /**
     * The TMDB lists, in chip order; none leaves only My list. Two settings that name the same list show it once.
     * Returns whether anything changed.
     */
    boolean set(List<Entry> lists) {
        List<Entry> in = lists == null ? Collections.<Entry>emptyList() : lists;
        if (!mock && entries.equals(in)) return false;
        mock = false;
        entries = Collections.unmodifiableList(new ArrayList<>(in));
        byKey.clear();
        List<ShownList> out = new ArrayList<>();
        for (Entry e : entries) {
            ShownList l = e.shown();
            if (byKey.containsKey(l.key)) continue;
            byKey.put(l.key, new LinkedHashSet<>(e.ids));
            out.add(l);
        }
        shown = Collections.unmodifiableList(out);
        return true;
    }

    /** The lists after My list, in chip order. */
    List<ShownList> shown() {
        return shown;
    }

    List<Entry> entries() {
        return entries;
    }

    /** Whether t is on the list: TMDB's copy for a TMDB list, the catalogue's for a mock one while they show. */
    boolean isOn(Title t, ShownList list) {
        if (list.mock != null) return mock && t.isOn(list.mock);
        Set<String> s = byKey.get(list.key);
        return s != null && s.contains(t.id);
    }

    /** Every id read for any shown list, for the catalogue to load and the prices to keep. */
    Set<String> allIds() {
        Set<String> out = new LinkedHashSet<>();
        for (Set<String> s : byKey.values()) out.addAll(s);
        return out;
    }

    /** A shown TMDB list's ids in the order TMDB returned them; none for a list not shown or a mock one. */
    List<String> ids(ShownList list) {
        Set<String> s = byKey.get(list.key);
        return s == null ? Collections.<String>emptyList() : new ArrayList<>(s);
    }

    /**
     * The copies with a title put on (at the end) or taken off the TMDB list numbered {@code listId}, as the title menu
     * changes it on the TV before TMDB answers (TitleMenu); every other list as it was.
     */
    static List<Entry> withTitle(List<Entry> lists, long listId, String titleId, boolean on) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : lists) {
            if (listId <= 0 || e.id != listId || e.ids.contains(titleId) == on) {
                out.add(e);
                continue;
            }
            List<String> ids = new ArrayList<>(e.ids);
            if (on) ids.add(titleId);
            else ids.remove(titleId);
            out.add(new Entry(e.ref, e.id, e.name, ids));
        }
        return out;
    }

    /**
     * The copies with a title put back on the TMDB list numbered {@code listId} at {@code at} (clamped to the list),
     * where it stood before a removal TMDB refused, so its "Recently added" place comes back too; every other list as
     * it was, and nothing changes when the title is already on it.
     */
    static List<Entry> withTitleAt(List<Entry> lists, long listId, String titleId, int at) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : lists) {
            if (listId <= 0 || e.id != listId || e.ids.contains(titleId)) {
                out.add(e);
                continue;
            }
            List<String> ids = new ArrayList<>(e.ids);
            ids.add(Math.max(0, Math.min(at, ids.size())), titleId);
            out.add(new Entry(e.ref, e.id, e.name, ids));
        }
        return out;
    }

    // ---- The copies kept for offline ---------------------------------------------------------------------------

    /**
     * The shown lists as last saved, one per setting in {@code refs}' order: the copy read last for that same setting,
     * or an empty one named by the setting (a name), else by {@code unnamed} ("List 8221116") until TMDB is read. Reads
     * the copies the household's Household and Friends were saved under before the lists had names, too.
     */
    static List<Entry> restore(List<ListRef> refs, String json, LongFunction<String> unnamed) {
        Map<String, JSONObject> saved = new HashMap<>();
        try {
            JSONObject o = new JSONObject(json == null || json.trim().isEmpty() ? "{}" : json);
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject e = o.optJSONObject(key);
                if (e != null) saved.put(e.optString("ref", key), e);
            }
        } catch (JSONException e) {
            saved.clear();
        }
        List<Entry> out = new ArrayList<>();
        for (ListRef ref : refs) {
            JSONObject e = saved.get(ref.toString());
            List<String> ids = new ArrayList<>();
            JSONArray a = e == null ? null : e.optJSONArray("ids");
            if (a != null) for (int i = 0; i < a.length(); i++) ids.add(a.optString(i));
            long id = e == null ? ref.id : e.optLong("id", ref.id);
            String name = e == null ? "" : e.optString("name", "");
            if (name.isEmpty()) name = !ref.name.isEmpty() ? ref.name : unnamed.apply(id);
            out.add(new Entry(ref, id, name, ids));
        }
        return out;
    }

    /** The copies to keep, keyed by their setting; what {@link #restore} reads. */
    static String save(List<Entry> lists) {
        JSONObject o = new JSONObject();
        try {
            for (Entry e : lists) {
                o.put(e.ref.toString(), new JSONObject().put("ref", e.ref.toString()).put("id", e.id)
                        .put("name", e.name).put("ids", new JSONArray(e.ids)));
            }
        } catch (JSONException e) {
            return "{}";
        }
        return o.toString();
    }
}
