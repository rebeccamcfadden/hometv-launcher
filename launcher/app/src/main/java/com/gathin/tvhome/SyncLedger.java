package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What the TV knows about the account's lists between syncs (BACKLOG.md, TMDB sign-in and watchlist sync), kept as
 * JSON beside the household's state: whose account it is, each list as it was last read from TMDB (the base a merge
 * compares with) and when, the TMDB list numbers found for Seen, Household and Friends, and the queue of changes made
 * on the TV that TMDB has not taken yet. A change waits there while offline and is retried with a growing delay
 * ({@link #retryDelay}). Pure Java, unit-tested (SyncLedgerTest).
 */
final class SyncLedger {
    static final long RETRY_FIRST = 30_000L;
    static final long RETRY_MAX = 60 * 60_000L;
    /** More than a person makes between two syncs; past it the oldest change goes. */
    static final int MAX_PENDING = 500;

    /** A change made on the TV: the title is on the list now, or not, since {@link #at}. */
    static final class Op {
        final SyncList list;
        final String id;
        final boolean present;
        final long at;
        final int attempts;
        /** Not sent again before this time. */
        final long notBefore;

        Op(SyncList list, String id, boolean present, long at, int attempts, long notBefore) {
            this.list = list;
            this.id = id;
            this.present = present;
            this.at = at;
            this.attempts = attempts;
            this.notBefore = notBefore;
        }

        boolean due(long now) {
            return now >= notBefore;
        }

        @Override
        public String toString() {
            return (present ? "+" : "-") + list + " " + id;
        }
    }

    private String account = "";
    private final Map<SyncList, List<String>> bases = new EnumMap<>(SyncList.class);
    private final Map<SyncList, Long> pulledAt = new EnumMap<>(SyncList.class);
    private final Map<SyncList, Long> listIds = new EnumMap<>(SyncList.class);
    private final List<Op> pending = new ArrayList<>();

    /** The account object id the bases belong to; "" before the first sync. */
    String account() {
        return account;
    }

    /** A different account, or a fresh start: everything is forgotten. */
    void reset(String accountObjectId) {
        account = accountObjectId == null ? "" : accountObjectId;
        bases.clear();
        pulledAt.clear();
        listIds.clear();
        pending.clear();
    }

    // ---- Changes made on the TV ----------------------------------------------------------------------------------

    /**
     * A title put on or taken off a list on the TV. A newer change to the same title replaces an older one, so the
     * last one wins. Read-only lists and ids that are not TMDB titles are ignored.
     */
    void record(SyncList list, String id, boolean present, long now) {
        if (!list.writable || TmdbAccountApi.mediaType(id) == null) return;
        // A change saved before the process died can arrive after a newer one: the newer stays.
        for (Op op : pending) if (op.list == list && op.id.equals(id) && op.at > now) return;
        removeOp(list, id);
        pending.add(new Op(list, id, present, now, 0, 0));
        while (pending.size() > MAX_PENDING) pending.remove(0);
    }

    /** Takes the household's unsent changes ({@link HouseholdState#unsent}), oldest first. */
    void recordAll(List<Op> ops) {
        List<Op> sorted = new ArrayList<>(ops);
        sorted.sort((a, b) -> Long.compare(a.at, b.at));
        for (Op op : sorted) record(op.list, op.id, op.present, op.at);
    }

    List<Op> pending() {
        return Collections.unmodifiableList(new ArrayList<>(pending));
    }

    List<Op> pending(SyncList list) {
        List<Op> out = new ArrayList<>();
        for (Op op : pending) if (op.list == list) out.add(op);
        return out;
    }

    /** The changes that may be sent now, oldest first. */
    List<Op> due(long now) {
        List<Op> out = new ArrayList<>();
        for (Op op : pending) if (op.due(now)) out.add(op);
        return out;
    }

    /** TMDB took the change: it leaves the queue and joins the base. */
    void pushed(Op op) {
        if (!pending.remove(op)) return;
        List<String> base = bases.get(op.list);
        if (base == null) return;
        base.remove(op.id);
        if (op.present) base.add(op.id);
    }

    /** The change failed for now: tried again after {@link #retryDelay}. */
    void retry(Op op, long now) {
        int i = pending.indexOf(op);
        if (i < 0) return;
        int attempts = op.attempts + 1;
        pending.set(i, new Op(op.list, op.id, op.present, op.at, attempts, now + retryDelay(attempts)));
    }

    /** The change can never go through (TMDB refused it): it is dropped, and the next pull puts the TV right. */
    void drop(Op op) {
        pending.remove(op);
    }

    /** After a merge: the list's queue is what the merge kept. */
    void keepOnly(SyncList list, List<Op> kept) {
        for (Iterator<Op> it = pending.iterator(); it.hasNext(); ) {
            Op op = it.next();
            if (op.list == list && !kept.contains(op)) it.remove();
        }
    }

    /** 30 seconds, doubling each time, at most an hour. */
    static long retryDelay(int attempts) {
        long d = RETRY_FIRST;
        for (int i = 1; i < attempts && d < RETRY_MAX; i++) d *= 2;
        return Math.min(d, RETRY_MAX);
    }

    private void removeOp(SyncList list, String id) {
        for (Iterator<Op> it = pending.iterator(); it.hasNext(); ) {
            Op op = it.next();
            if (op.list == list && op.id.equals(id)) it.remove();
        }
    }

    // ---- What TMDB had -------------------------------------------------------------------------------------------

    /** The list as last read from TMDB, or null when it never was. */
    List<String> base(SyncList list) {
        List<String> b = bases.get(list);
        return b == null ? null : Collections.unmodifiableList(new ArrayList<>(b));
    }

    /** When the base was read; 0 when never. */
    long pulledAt(SyncList list) {
        Long t = pulledAt.get(list);
        return t == null ? 0 : t;
    }

    void setBase(SyncList list, List<String> ids, long at) {
        bases.put(list, new ArrayList<>(ids));
        pulledAt.put(list, at);
    }

    /** The TMDB list number found for Seen, Household or Friends; 0 when not found yet. */
    long listId(SyncList list) {
        Long id = listIds.get(list);
        return id == null ? 0 : id;
    }

    void setListId(SyncList list, long id) {
        if (id > 0) listIds.put(list, id);
        else listIds.remove(list);
    }

    // ---- JSON ----------------------------------------------------------------------------------------------------

    String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("account", account);
            JSONObject b = new JSONObject();
            for (Map.Entry<SyncList, List<String>> e : bases.entrySet()) {
                b.put(e.getKey().name(), new JSONObject().put("at", pulledAt(e.getKey()))
                        .put("ids", new JSONArray(e.getValue())));
            }
            o.put("bases", b);
            JSONObject l = new JSONObject();
            for (Map.Entry<SyncList, Long> e : listIds.entrySet()) l.put(e.getKey().name(), e.getValue());
            o.put("lists", l);
            JSONArray p = new JSONArray();
            for (Op op : pending) {
                p.put(new JSONObject().put("list", op.list.name()).put("id", op.id).put("present", op.present)
                        .put("at", op.at).put("attempts", op.attempts).put("notBefore", op.notBefore));
            }
            o.put("pending", p);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads what {@link #toJson} wrote; anything unreadable gives an empty ledger, so the next sync starts over. */
    static SyncLedger fromJson(String json) {
        SyncLedger l = new SyncLedger();
        if (json == null || json.trim().isEmpty()) return l;
        try {
            JSONObject o = new JSONObject(json);
            l.account = o.optString("account", "");
            JSONObject b = o.optJSONObject("bases");
            if (b != null) {
                for (SyncList list : SyncList.values()) {
                    JSONObject e = b.optJSONObject(list.name());
                    if (e == null) continue;
                    l.setBase(list, strings(e.optJSONArray("ids")), e.optLong("at", 0));
                }
            }
            JSONObject ids = o.optJSONObject("lists");
            if (ids != null) for (SyncList list : SyncList.values()) l.setListId(list, ids.optLong(list.name(), 0));
            JSONArray p = o.optJSONArray("pending");
            if (p != null) {
                for (int i = 0; i < p.length(); i++) {
                    JSONObject e = p.getJSONObject(i);
                    SyncList list = SyncList.valueOf(e.getString("list"));
                    String id = e.getString("id");
                    if (!list.writable || TmdbAccountApi.mediaType(id) == null) continue;
                    l.pending.add(new Op(list, id, e.getBoolean("present"), e.getLong("at"), e.optInt("attempts", 0),
                            e.optLong("notBefore", 0)));
                }
            }
            return l;
        } catch (JSONException | IllegalArgumentException e) {
            return new SyncLedger();
        }
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, null);
            if (s != null && TmdbAccountApi.mediaType(s) != null && !out.contains(s)) out.add(s);
        }
        return out;
    }
}
