package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One phone note onto its TMDB list (BACKLOG.md, Phone notes; docs/phone-notes.md). Blocking: run on the account's
 * worker thread, so it never races a sync. In order: find the account's list with the note's name (as {@link ListRef}
 * matches names), or make a private one; read what it holds; look the note's new lines up ({@link NoteMatch}); add the
 * new titles in batches; take the ticked ones off the list and hand them to the household as Seen. Adding only. A
 * note whose every line is ticked only marks Seen: a list it names that doesn't exist isn't made (the project lead,
 * 2026-11-01). {@link #pick} adds the title the household picked on the TV for a line that wasn't matched exactly.
 *
 * <p>The lines' answers ({@link NoteMatch.Memory}) are worked out on a copy that is handed back only when the list was
 * written, so a note whose list can't be written is looked up and added in full next time. Nothing is logged: a
 * failure is a {@link Status}, never a URL or token. Pure Java over {@link AccountHttp}, unit-tested against a fake
 * TMDB (NoteImportTest).
 */
final class NoteImport {
    /** Titles per list write: TMDB's v4 list item calls take a batch; a page's worth stays well inside it. */
    static final int BATCH = 20;

    /** The household's side: titles ticked in the note go to Seen (only those not seen yet). */
    interface Seen {
        void mark(List<String> ids);
    }

    enum Status {
        /** Every line answered and the list written: the note is done. */
        DONE,
        /** TMDB couldn't be reached (or some lines couldn't be looked up): the note waits and is tried again. */
        OFFLINE,
        /** TMDB refused a list call: the note waits, and the household hears it couldn't reach TMDB. */
        FAILED,
        /** TMDB no longer accepts the session: the note waits for a sign-in. */
        SIGNED_OUT
    }

    static final class Outcome {
        final Status status;
        /** The list written, or 0 when it wasn't reached. */
        final long listId;
        /** Whether this run made the list. */
        final boolean created;
        /** Lines in the note, titles added, titles ticked (to Seen), lines that matched nothing as written. */
        final int lines, added, seen;
        final List<String> unmatched;
        /** The same lines, with TMDB's answers to ask about each. */
        final List<NoteMatch.Ask> asks;
        /** The account's lists as read (with one made this run), for the chip order; null when not read. */
        final List<TmdbAccountParse.ListInfo> accountLists;
        /** The lines' answers to save, or null to keep what was saved (the list wasn't written). */
        final NoteMatch.Memory memory;

        Outcome(Status status, long listId, boolean created, int lines, int added, int seen, List<NoteMatch.Ask> asks,
                List<TmdbAccountParse.ListInfo> accountLists, NoteMatch.Memory memory) {
            this.status = status;
            this.listId = listId;
            this.created = created;
            this.lines = lines;
            this.added = added;
            this.seen = seen;
            this.asks = asks == null ? Collections.<NoteMatch.Ask>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(asks));
            List<String> u = new ArrayList<>();
            for (NoteMatch.Ask a : this.asks) u.add(a.query);
            this.unmatched = Collections.unmodifiableList(u);
            this.accountLists = accountLists;
            this.memory = memory;
        }

        static Outcome stopped(Status status, long listId, List<TmdbAccountParse.ListInfo> lists) {
            return new Outcome(status, listId, false, 0, 0, 0, null, lists, null);
        }
    }

    /** Thrown inside a run when a call fails, to stop it with that status. */
    private static final class Stop extends Exception {
        final Status status;

        Stop(Status status) {
            super(null, null, false, false);
            this.status = status;
        }
    }

    private final AccountHttp http;
    private final TmdbSession session;
    private final Seen seen;

    NoteImport(AccountHttp http, TmdbSession session, Seen seen) {
        this.http = http;
        this.session = session;
        this.seen = seen;
    }

    /**
     * Imports the note's text onto the list named {@code list}. {@code memory} is read, never changed; the outcome
     * carries the new one to save.
     */
    Outcome run(String list, String text, NoteMatch.Memory memory, NoteMatch.Progress progress) {
        List<TmdbAccountParse.ListInfo> lists = null;
        long id = 0;
        boolean created = false;
        try {
            List<NoteLines.Line> lines = NoteLines.parse(text);
            boolean allTicked = !lines.isEmpty();
            for (NoteLines.Line l : lines) allTicked &= l.ticked;
            lists = accountLists();
            id = ListRef.named(list).resolve(lists);
            if (id == 0 && allTicked) {
                // Everything in it is watched: Seen only, and no list made just to hold nothing.
                NoteMatch.Memory copy = NoteMatch.Memory.fromJson(memory == null ? null : memory.toJson());
                NoteMatch.Plan plan = new NoteMatch(this::fetch).plan(list, lines, copy,
                        Collections.<String>emptySet(), progress);
                if (!plan.seen.isEmpty()) seen.mark(Collections.unmodifiableList(new ArrayList<>(plan.seen)));
                Status status = plan.failed > 0 ? Status.OFFLINE : Status.DONE;
                return new Outcome(status, 0, false, plan.lines, 0, plan.seen.size(), plan.asks, lists, copy);
            }
            if (id == 0) {
                id = TmdbAccountParse.createdList(send(TmdbAccountApi.createList(list.trim())));
                created = true;
                lists = new ArrayList<>(lists);
                lists.add(new TmdbAccountParse.ListInfo(id, list.trim(), false, 0));
            }
            Set<String> onList = items(id);
            NoteMatch.Memory copy = NoteMatch.Memory.fromJson(memory == null ? null : memory.toJson());
            // A list made afresh (the old one deleted on TMDB) starts empty: every line counts as new again.
            if (created) copy.forget(list);
            NoteMatch.Plan plan = new NoteMatch(this::fetch).plan(list, lines, copy, onList, progress);
            for (int i = 0; i < plan.add.size(); i += BATCH) {
                TmdbAccountParse.done(send(TmdbAccountApi.listItems(id,
                        plan.add.subList(i, Math.min(plan.add.size(), i + BATCH)), true)));
            }
            List<String> off = new ArrayList<>();
            for (String t : plan.seen) if (onList.contains(t)) off.add(t);
            for (int i = 0; i < off.size(); i += BATCH) {
                TmdbAccountParse.done(send(TmdbAccountApi.listItems(id,
                        off.subList(i, Math.min(off.size(), i + BATCH)), false)));
            }
            if (!plan.seen.isEmpty()) seen.mark(Collections.unmodifiableList(new ArrayList<>(plan.seen)));
            // Lines that couldn't be looked up aren't remembered, so the next try looks only those up.
            Status status = plan.failed > 0 ? Status.OFFLINE : Status.DONE;
            return new Outcome(status, id, created, plan.lines, plan.add.size(), plan.seen.size(), plan.asks,
                    lists, copy);
        } catch (Stop s) {
            return Outcome.stopped(s.status, id, lists);
        } catch (TmdbAccountParse.Failure f) {
            return Outcome.stopped(status(f), id, lists);
        }
    }

    /**
     * The household answered an asked line on the TV: {@code id} goes onto the list (made if missing), or to Seen
     * for a ticked line; null is "None of these". Either way the line is remembered, so it isn't asked again.
     */
    Outcome pick(String list, NoteMatch.Ask ask, String id, NoteMatch.Memory memory) {
        NoteMatch.Memory copy = NoteMatch.Memory.fromJson(memory == null ? null : memory.toJson());
        if (id == null) {
            copy.put(list, ask.key, NoteMatch.DISMISSED);
            return new Outcome(Status.DONE, 0, false, 1, 0, 0, null, null, copy);
        }
        if (ask.ticked) {
            seen.mark(Collections.singletonList(id));
            copy.put(list, ask.key, id);
            return new Outcome(Status.DONE, 0, false, 1, 0, 1, null, null, copy);
        }
        List<TmdbAccountParse.ListInfo> lists = null;
        long listId = 0;
        boolean created = false;
        try {
            lists = accountLists();
            listId = ListRef.named(list).resolve(lists);
            if (listId == 0) {
                listId = TmdbAccountParse.createdList(send(TmdbAccountApi.createList(list.trim())));
                created = true;
                lists = new ArrayList<>(lists);
                lists.add(new TmdbAccountParse.ListInfo(listId, list.trim(), false, 0));
            }
            boolean there = items(listId).contains(id);
            if (!there) {
                TmdbAccountParse.done(send(TmdbAccountApi.listItems(listId, Collections.singletonList(id), true)));
            }
            copy.put(list, ask.key, id);
            return new Outcome(Status.DONE, listId, created, 1, there ? 0 : 1, 0, null, lists, copy);
        } catch (Stop s) {
            return Outcome.stopped(s.status, listId, lists);
        } catch (TmdbAccountParse.Failure f) {
            return Outcome.stopped(status(f), listId, lists);
        }
    }

    /**
     * TMDB's best answers for a line asked about with none saved: an older TV kept only the line's text, and a line
     * already looked up isn't looked up again by a send. Null when TMDB can't be reached.
     */
    List<NoteMatch.Candidate> lookUp(String query) {
        List<NoteLines.Line> lines = NoteLines.parse(query);
        if (lines.isEmpty()) return Collections.emptyList();
        try {
            return new NoteMatch(this::fetch).find(lines.get(0)).top;
        } catch (IOException e) {
            return null;
        }
    }

    /** The account's own lists, every page. */
    private List<TmdbAccountParse.ListInfo> accountLists() throws Stop, TmdbAccountParse.Failure {
        List<TmdbAccountParse.ListInfo> out = new ArrayList<>();
        for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
            TmdbAccountParse.ListPage p = TmdbAccountParse.lists(
                    send(TmdbAccountApi.accountLists(session.accountObjectId, page)));
            out.addAll(p.lists);
            if (page >= p.totalPages) break;
        }
        return out;
    }

    /** What the list holds now, every page. */
    private Set<String> items(long listId) throws Stop, TmdbAccountParse.Failure {
        Set<String> out = new LinkedHashSet<>();
        for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
            TmdbAccountParse.Page p = TmdbAccountParse.page(send(TmdbAccountApi.listPage(listId, page)), null);
            out.addAll(p.ids);
            if (page >= p.totalPages) break;
        }
        return out;
    }

    /** NoteMatch's searches: TMDB v3 with the app's token, never cached on disk. */
    private JSONObject fetch(String path) throws IOException {
        AccountHttp.Reply r = http.send(new AccountHttp.Call(AccountHttp.Method.GET, TmdbAccountApi.BASE + "/3" + path,
                AccountHttp.Auth.APP, null));
        if (!r.ok()) throw new IOException("HTTP " + r.status);
        try {
            return new JSONObject(r.body);
        } catch (JSONException e) {
            throw new IOException("not JSON");
        }
    }

    private AccountHttp.Reply send(AccountHttp.Call call) throws Stop, TmdbAccountParse.Failure {
        AccountHttp.Reply r;
        try {
            r = http.send(call);
        } catch (IOException e) {
            throw new Stop(Status.OFFLINE);
        }
        if (!r.ok()) {
            TmdbAccountParse.Failure f = TmdbAccountParse.failure(r);
            throw new Stop(status(f));
        }
        return r;
    }

    private static Status status(TmdbAccountParse.Failure f) {
        switch (f.problem) {
            case RETRY:
                return Status.OFFLINE;
            case SIGNED_OUT:
            case NOT_APPROVED:
            case EXPIRED:
                return Status.SIGNED_OUT;
            default:
                return Status.FAILED;
        }
    }
}
