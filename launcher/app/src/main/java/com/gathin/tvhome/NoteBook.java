package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phone notes' state kept on the TV (BACKLOG.md, Phone notes): the notes waiting to be imported, the latest per list
 * (a later send of the same list replaces the earlier one, as it holds every line anyway), and each list's last send
 * for Launcher settings (when, lines, added, the lines that matched nothing). A note waits until its import is done,
 * so one sent signed out, offline, or while TMDB refused a list call is imported later. Saved as JSON in the
 * "phone_notes" preferences; thread-safe (the inbox thread adds, the main thread takes). Pure Java, unit-tested
 * (NoteBookTest).
 */
final class NoteBook {
    /** Last sends kept for the pane, newest first. */
    static final int MAX_SENDS = 8;

    /** A note waiting to be imported. {@link #seq} tells a later send of the same list from this one. */
    static final class Pending {
        final String list, text;
        final long at, seq;

        Pending(String list, String text, long at, long seq) {
            this.list = list;
            this.text = text;
            this.at = at;
            this.seq = seq;
        }
    }

    /** A list's last import, for the pane's Last sends: the lines not matched exactly are asked about there. */
    static final class Send {
        final String list;
        final long at;
        final int lines, added;
        /** The lines not matched exactly, as written, and the same with TMDB's answers to pick from. */
        final List<String> unmatched;
        final List<NoteMatch.Ask> asks;

        Send(String list, long at, int lines, int added, List<NoteMatch.Ask> asks) {
            this.list = list;
            this.at = at;
            this.lines = lines;
            this.added = added;
            this.asks = asks == null ? Collections.<NoteMatch.Ask>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(asks));
            List<String> u = new ArrayList<>();
            for (NoteMatch.Ask a : this.asks) u.add(a.query);
            this.unmatched = Collections.unmodifiableList(u);
        }

        /** A send whose unmatched lines have no answers to offer, by their text. */
        static Send ofLines(String list, long at, int lines, int added, List<String> unmatched) {
            return new Send(list, at, lines, added, asks(unmatched));
        }

        private static List<NoteMatch.Ask> asks(List<String> lines) {
            List<NoteMatch.Ask> out = new ArrayList<>();
            if (lines != null) for (String q : lines) out.add(new NoteMatch.Ask(NoteLines.key(q), q, false, null));
            return out;
        }
    }

    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final List<Send> sends = new ArrayList<>();
    private long seq;

    // ---- Waiting notes -------------------------------------------------------------------------------------------

    /** A note arrived: it replaces one still waiting for the same list. Returns it. */
    synchronized Pending put(String list, String text, long at) {
        String key = NoteLines.key(list);
        pending.remove(key);
        Pending p = new Pending(list.trim(), text, at, ++seq);
        pending.put(key, p);
        return p;
    }

    /** The notes waiting, oldest first. */
    synchronized List<Pending> pending() {
        return Collections.unmodifiableList(new ArrayList<>(pending.values()));
    }

    /** The oldest note waiting whose list isn't in {@code skip} (list keys), or null. */
    synchronized Pending next(java.util.Set<String> skip) {
        for (Map.Entry<String, Pending> e : pending.entrySet()) if (!skip.contains(e.getKey())) return e.getValue();
        return null;
    }

    /** The note was imported: it stops waiting, unless a newer send of its list has replaced it meanwhile. */
    synchronized boolean done(Pending p) {
        String key = NoteLines.key(p.list);
        Pending now = pending.get(key);
        if (now == null || now.seq != p.seq) return false;
        pending.remove(key);
        return true;
    }

    // ---- Last sends ----------------------------------------------------------------------------------------------

    /**
     * A list's import finished: its send replaces the list's last one, at the top. A line still waiting for an answer
     * keeps the answers it was offered last time (a line already looked up isn't looked up again, so has none now).
     */
    synchronized void record(Send s) {
        String key = NoteLines.key(s.list);
        Send old = null;
        for (int i = sends.size() - 1; i >= 0; i--) {
            if (NoteLines.key(sends.get(i).list).equals(key)) old = sends.remove(i);
        }
        if (old != null && !old.asks.isEmpty()) {
            Map<String, NoteMatch.Ask> before = new LinkedHashMap<>();
            for (NoteMatch.Ask a : old.asks) before.put(a.key, a);
            List<NoteMatch.Ask> asks = new ArrayList<>();
            for (NoteMatch.Ask a : s.asks) {
                NoteMatch.Ask b = before.get(a.key);
                asks.add(a.top.isEmpty() && b != null && !b.top.isEmpty() ? a.with(b.top) : a);
            }
            s = new Send(s.list, s.at, s.lines, s.added, asks);
        }
        sends.add(0, s);
        while (sends.size() > MAX_SENDS) sends.remove(sends.size() - 1);
    }

    /** TMDB's answers were found for a line of the list's last send that had none: they are kept with it. */
    synchronized void offered(String list, String lineKey, List<NoteMatch.Candidate> top) {
        String key = NoteLines.key(list);
        for (int i = 0; i < sends.size(); i++) {
            Send s = sends.get(i);
            if (!NoteLines.key(s.list).equals(key)) continue;
            List<NoteMatch.Ask> asks = new ArrayList<>();
            for (NoteMatch.Ask a : s.asks) asks.add(a.key.equals(lineKey) ? a.with(top) : a);
            sends.set(i, new Send(s.list, s.at, s.lines, s.added, asks));
            return;
        }
    }

    /** A line of the list's last send was answered on the TV: it leaves Last sends. Returns whether it was there. */
    synchronized boolean answered(String list, String lineKey) {
        String key = NoteLines.key(list);
        for (int i = 0; i < sends.size(); i++) {
            Send s = sends.get(i);
            if (!NoteLines.key(s.list).equals(key)) continue;
            List<NoteMatch.Ask> asks = new ArrayList<>();
            for (NoteMatch.Ask a : s.asks) if (!a.key.equals(lineKey)) asks.add(a);
            if (asks.size() == s.asks.size()) return false;
            sends.set(i, new Send(s.list, s.at, s.lines, s.added, asks));
            return true;
        }
        return false;
    }

    synchronized List<Send> sends() {
        return Collections.unmodifiableList(new ArrayList<>(sends));
    }

    /** Lines that matched nothing in the lists' last sends: the section's amber "3 not matched". */
    synchronized int unmatched() {
        int n = 0;
        for (Send s : sends) n += s.unmatched.size();
        return n;
    }

    // ---- Saving --------------------------------------------------------------------------------------------------

    synchronized String pendingJson() {
        JSONArray a = new JSONArray();
        try {
            for (Pending p : pending.values()) {
                a.put(new JSONObject().put("list", p.list).put("text", p.text).put("at", p.at));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return a.toString();
    }

    synchronized String sendsJson() {
        JSONArray a = new JSONArray();
        try {
            for (Send s : sends) {
                JSONArray asks = new JSONArray();
                for (NoteMatch.Ask q : s.asks) asks.put(q.toJson());
                a.put(new JSONObject().put("list", s.list).put("at", s.at).put("lines", s.lines).put("added", s.added)
                        .put("unmatched", asks));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return a.toString();
    }

    /** The book as saved; anything unreadable is left out. */
    static NoteBook restore(String pendingJson, String sendsJson) {
        NoteBook b = new NoteBook();
        JSONArray p = array(pendingJson);
        for (int i = 0; p != null && i < p.length(); i++) {
            JSONObject o = p.optJSONObject(i);
            if (o == null) continue;
            String list = o.optString("list", "").trim();
            String text = o.optString("text", "");
            if (list.isEmpty() || text.isEmpty()) continue;
            b.put(list, text, o.optLong("at", 0));
        }
        JSONArray s = array(sendsJson);
        for (int i = 0; s != null && i < s.length() && b.sends.size() < MAX_SENDS; i++) {
            JSONObject o = s.optJSONObject(i);
            if (o == null || o.optString("list", "").trim().isEmpty()) continue;
            // Each unmatched line with its answers; an older TV saved just the line's text.
            List<NoteMatch.Ask> asks = new ArrayList<>();
            JSONArray u = o.optJSONArray("unmatched");
            for (int j = 0; u != null && j < u.length(); j++) {
                NoteMatch.Ask a = NoteMatch.Ask.fromJson(u.opt(j));
                if (a != null) asks.add(a);
            }
            b.sends.add(new Send(o.optString("list", "").trim(), o.optLong("at", 0), o.optInt("lines", 0),
                    o.optInt("added", 0), asks));
        }
        return b;
    }

    private static JSONArray array(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return null;
        }
    }
}
