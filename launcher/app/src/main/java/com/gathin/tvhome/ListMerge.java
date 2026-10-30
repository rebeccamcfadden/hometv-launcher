package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges one list as TMDB has it now with the TV's copy and the changes still queued on the TV (BACKLOG.md, TMDB
 * sign-in and watchlist sync). Last writer wins for each title:
 * <ul>
 *   <li>A title with no queued change takes TMDB's state (added on the phone: it appears; removed: it goes).</li>
 *   <li>A queued change that TMDB already matches is done and leaves the queue.</li>
 *   <li>A queued change where TMDB still has the base (what the TV last read) is kept, to be sent.</li>
 *   <li>A queued change where TMDB changed too, the other way: TMDB doesn't say when, so its change is taken to have
 *       happened just after the TV last read the list. The TV's change wins when it was made after that read, and
 *       loses (leaves the queue) when it is older, e.g. a change that kept failing while reads succeeded.</li>
 * </ul>
 * Titles keep the TV's order; titles new from TMDB go at the end in TMDB's order. Pure Java, unit-tested
 * (ListMergeTest).
 */
final class ListMerge {
    private ListMerge() {}

    static final class Result {
        /** The list for the TV to show. */
        final List<String> local;
        /** The queued changes still to send. */
        final List<SyncLedger.Op> keep;

        Result(List<String> local, List<SyncLedger.Op> keep) {
            this.local = local;
            this.keep = keep;
        }
    }

    /**
     * @param local the TV's list, in its order
     * @param base the list as last read from TMDB, or null when it never was (then nothing on the TV counts as
     *             TMDB's, and only queued changes keep a title that TMDB lacks)
     * @param basePulledAt when the base was read
     * @param remote the list as TMDB has it now, in TMDB's order
     * @param pending the list's queued changes, at most one per title
     */
    static Result merge(List<String> local, List<String> base, long basePulledAt, List<String> remote,
                        List<SyncLedger.Op> pending) {
        Set<String> remoteSet = new HashSet<>(remote);
        Set<String> baseSet = base == null ? Collections.<String>emptySet() : new HashSet<>(base);
        Map<String, SyncLedger.Op> ops = new HashMap<>();
        for (SyncLedger.Op op : pending) {
            SyncLedger.Op had = ops.get(op.id);
            if (had == null || op.at >= had.at) ops.put(op.id, op);
        }
        List<SyncLedger.Op> keep = new ArrayList<>();
        Set<String> present = new HashSet<>();
        Set<String> all = new LinkedHashSet<>(local);
        all.addAll(remote);
        all.addAll(ops.keySet());
        for (String id : all) {
            boolean r = remoteSet.contains(id);
            SyncLedger.Op op = ops.get(id);
            boolean on;
            if (op == null || op.present == r) {
                on = r;
            } else {
                boolean remoteChanged = r != baseSet.contains(id);
                if (!remoteChanged || op.at > basePulledAt) {
                    on = op.present;
                    keep.add(op);
                } else {
                    on = r;
                }
            }
            if (on) present.add(id);
        }
        List<String> out = new ArrayList<>();
        for (String id : local) if (present.contains(id) && !out.contains(id)) out.add(id);
        for (String id : remote) if (present.contains(id) && !out.contains(id)) out.add(id);
        for (SyncLedger.Op op : keep) if (op.present && !out.contains(op.id)) out.add(op.id);
        // Queued changes keep the order they were made in.
        keep.sort((a, b) -> Long.compare(a.at, b.at));
        return new Result(Collections.unmodifiableList(out), Collections.unmodifiableList(keep));
    }
}
