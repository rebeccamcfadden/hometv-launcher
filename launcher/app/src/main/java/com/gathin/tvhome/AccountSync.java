package com.gathin.tvhome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One sync of the household's lists with the TMDB account (BACKLOG.md, TMDB sign-in and watchlist sync). Blocking:
 * run it off the main thread, never while Home waits on it. In order: find the TMDB list behind Seen (and make a
 * private Seen list if the account has none and that is allowed); read every list and merge the writable ones with the
 * TV's copy ({@link ListMerge}); read the lists shown as chips, each with its TMDB name; then send the queued changes
 * that are due. A list that can't be read keeps the TV's copy; a change that can't be sent stays queued with a longer
 * delay ({@link SyncLedger}); a session TMDB no longer accepts stops the sync and asks for a new sign-in. Pure Java
 * over {@link AccountHttp}, unit-tested against a fake TMDB (AccountSyncTest).
 */
final class AccountSync {
    /** Which TMDB lists are Seen and the lists shown as chips (config.xml and Choose lists; see {@link ListRef}). */
    static final class Settings {
        final ListRef seen;
        /** The lists shown as chips, in chip order; the TV only reads them. */
        final List<ListRef> shown;
        /** Whether to make a private list named as {@link #seen} says when the account has none. */
        final boolean createSeen;

        Settings(ListRef seen, List<ListRef> shown, boolean createSeen) {
            this.seen = seen;
            this.shown = shown == null ? Collections.<ListRef>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(shown));
            this.createSeen = createSeen;
        }

        ListRef ref(SyncList list) {
            return list == SyncList.SEEN ? seen : ListRef.NONE;
        }
    }

    static final class Result {
        /** The lists read from TMDB this time, merged with the TV's for the writable ones. Missing: not read. */
        final Map<SyncList, List<String>> lists;
        /** The shown lists read this time, by their setting ({@link ListRef#toString}); missing: not read. */
        final Map<String, ImportedLists.Entry> shown;
        /** The account's own lists in TMDB's order when they were read this time, else null. */
        final List<TmdbAccountParse.ListInfo> accountLists;
        /** TMDB no longer accepts the session: sign in again. Queued changes are kept for the same account. */
        final boolean signedOut;
        /** Changes TMDB took, and changes still queued. */
        final int sent;
        final int queued;
        /** Short notes on what failed, for a log line: never a token or session id. */
        final List<String> problems;

        Result(Map<SyncList, List<String>> lists, Map<String, ImportedLists.Entry> shown,
               List<TmdbAccountParse.ListInfo> accountLists, boolean signedOut, int sent, int queued,
               List<String> problems) {
            this.lists = lists;
            this.shown = shown;
            this.accountLists = accountLists;
            this.signedOut = signedOut;
            this.sent = sent;
            this.queued = queued;
            this.problems = problems;
        }
    }

    /** Thrown inside a run when the session is refused, to stop it. */
    private static final class SignedOut extends Exception {
        SignedOut() {
            super(null, null, false, false);
        }
    }

    private final AccountHttp http;
    private final TmdbSession session;
    private final SyncLedger ledger;
    private final Settings settings;
    private final List<String> problems = new ArrayList<>();

    AccountSync(AccountHttp http, TmdbSession session, SyncLedger ledger, Settings settings) {
        if (!session.accountObjectId.equals(ledger.account())) {
            throw new IllegalArgumentException("the ledger is another account's; SeedPolicy prepares it");
        }
        this.http = http;
        this.session = session;
        this.ledger = ledger;
        this.settings = settings;
    }

    /** Lists read so far of a full read, for the Account pane's import bar; called on the sync's thread. */
    interface Progress {
        void step(int done, int total);
    }

    private Progress progress;
    private int done, total;

    /** Reports each list read in a full read: My list, Favorites, Seen, then each shown list. */
    AccountSync withProgress(Progress p) {
        progress = p;
        return this;
    }

    private void step() {
        done++;
        if (progress != null) progress.step(Math.min(done, total), total);
    }

    /**
     * @param local the TV's writable lists now (My list, Favorites, Seen), each in its order
     * @param pull false to only send the queued changes, as after a change on the TV
     */
    Result run(Map<SyncList, List<String>> local, long now, boolean pull) {
        problems.clear();
        mine = null;
        mineRead = false;
        Map<SyncList, List<String>> lists = new EnumMap<>(SyncList.class);
        Map<String, ImportedLists.Entry> shown = new LinkedHashMap<>();
        int sent = 0;
        boolean signedOut = false;
        try {
            if (pull || hasDue(SyncList.SEEN, now)) findLists(pull);
            if (pull) {
                total = SyncList.values().length + settings.shown.size();
                done = 0;
                if (progress != null) progress.step(0, total);
                for (SyncList list : SyncList.values()) {
                    List<String> remote;
                    try {
                        remote = read(list);
                    } finally {
                        step();
                    }
                    if (remote == null) continue;
                    List<String> mineNow = local.get(list);
                    ListMerge.Result m = ListMerge.merge(mineNow == null ? Collections.<String>emptyList() : mineNow,
                            ledger.base(list), ledger.pulledAt(list), remote, ledger.pending(list));
                    ledger.keepOnly(list, m.keep);
                    ledger.setBase(list, remote, now);
                    lists.put(list, m.local);
                }
                readShown(shown);
            }
            sent = push(now);
        } catch (SignedOut e) {
            signedOut = true;
            problems.add("TMDB no longer accepts the session");
        }
        return new Result(Collections.unmodifiableMap(lists), Collections.unmodifiableMap(shown),
                mine == null ? null : Collections.unmodifiableList(mine), signedOut, sent, ledger.pending().size(),
                Collections.unmodifiableList(new ArrayList<>(problems)));
    }

    private boolean hasDue(SyncList list, long now) {
        for (SyncLedger.Op op : ledger.due(now)) if (op.list == list) return true;
        return false;
    }

    // ---- Finding the TMDB lists ----------------------------------------------------------------------------------

    /** The account's lists, read at most once a run; null until read, or when they couldn't be. */
    private List<TmdbAccountParse.ListInfo> mine;
    private boolean mineRead;

    private List<TmdbAccountParse.ListInfo> mine() throws SignedOut {
        if (!mineRead) {
            mineRead = true;
            mine = accountLists();
        }
        return mine;
    }

    private void findLists(boolean pull) throws SignedOut {
        for (SyncList list : SyncList.values()) {
            if (!list.isCustomList()) continue;
            ListRef ref = settings.ref(list);
            if (ref.isNone()) {
                ledger.setListId(list, 0);
                continue;
            }
            if (ref.id > 0) {
                ledger.setListId(list, ref.id);
                continue;
            }
            // A name: looked up again on every full sync, so a renamed or replaced list is followed.
            if (ledger.listId(list) > 0 && !pull) continue;
            if (mine() == null) return;
            long id = ref.resolve(mine);
            if (id == 0 && list == SyncList.SEEN && settings.createSeen) id = createList(ref.name);
            if (id == 0 && ledger.listId(list) > 0) problems.add("no list named " + ref + " any more");
            ledger.setListId(list, id);
        }
    }

    private List<TmdbAccountParse.ListInfo> accountLists() throws SignedOut {
        List<TmdbAccountParse.ListInfo> out = new ArrayList<>();
        for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
            TmdbAccountParse.ListPage p;
            try {
                p = TmdbAccountParse.lists(send(TmdbAccountApi.accountLists(session.accountObjectId, page)));
            } catch (TmdbAccountParse.Failure f) {
                note(f, "the account's lists");
                return null;
            }
            out.addAll(p.lists);
            if (page >= p.totalPages) break;
        }
        return out;
    }

    private long createList(String name) throws SignedOut {
        try {
            return TmdbAccountParse.createdList(send(TmdbAccountApi.createList(name)));
        } catch (TmdbAccountParse.Failure f) {
            note(f, "making the list \"" + name + "\"");
            return 0;
        }
    }

    // ---- Reading -------------------------------------------------------------------------------------------------

    /** The whole list from TMDB, or null when it has none or can't be read now. */
    private List<String> read(SyncList list) throws SignedOut {
        List<String> out = new ArrayList<>();
        try {
            if (list.isCustomList()) {
                long id = ledger.listId(list);
                if (id == 0) return null;
                for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
                    TmdbAccountParse.Page p = TmdbAccountParse.page(send(TmdbAccountApi.listPage(id, page)), null);
                    addAll(out, p.ids);
                    if (page >= p.totalPages) break;
                }
            } else {
                for (TmdbAccountApi.Kind kind : TmdbAccountApi.Kind.values()) {
                    for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
                        TmdbAccountParse.Page p = TmdbAccountParse.page(
                                send(TmdbAccountApi.accountPage(session.accountObjectId, list, kind, page)), kind.path);
                        addAll(out, p.ids);
                        if (page >= p.totalPages) break;
                    }
                }
            }
        } catch (TmdbAccountParse.Failure f) {
            note(f, list.name());
            // A list that is gone or private to someone else: look it up again next time.
            if (list.isCustomList() && f.problem == TmdbAccountParse.Problem.REJECTED) ledger.setListId(list, 0);
            return null;
        }
        return out;
    }

    private static void addAll(List<String> out, List<String> ids) {
        for (String id : ids) if (!out.contains(id)) out.add(id);
    }

    /**
     * Each list shown as a chip, whole, with its name: the account's own name for it when it is one of the account's
     * lists, else the name the list's page gives (a friend's public list). A list set by name is found among the
     * account's lists on every full sync, so a renamed or replaced one is followed. A list that can't be read now is
     * left out, and keeps the TV's copy.
     */
    private void readShown(Map<String, ImportedLists.Entry> out) throws SignedOut {
        if (settings.shown.isEmpty()) return;
        List<TmdbAccountParse.ListInfo> own = mine();
        for (ListRef ref : settings.shown) {
            try {
                readShown(out, own, ref);
            } finally {
                step();
            }
        }
    }

    private void readShown(Map<String, ImportedLists.Entry> out, List<TmdbAccountParse.ListInfo> own, ListRef ref)
            throws SignedOut {
        long id = ref.resolve(own);
        if (id == 0) {
            if (own != null) problems.add("no list named " + ref);
            return;
        }
        List<String> ids = new ArrayList<>();
        String name = "";
        try {
            for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
                TmdbAccountParse.Page p = TmdbAccountParse.page(send(TmdbAccountApi.listPage(id, page)), null);
                if (page == 1) name = p.name;
                addAll(ids, p.ids);
                if (page >= p.totalPages) break;
            }
        } catch (TmdbAccountParse.Failure f) {
            note(f, ref.toString());
            return;
        }
        String ownName = nameOf(own, id);
        out.put(ref.toString(), new ImportedLists.Entry(ref, id, ownName.isEmpty() ? name : ownName, ids));
    }

    private static String nameOf(List<TmdbAccountParse.ListInfo> lists, long id) {
        if (lists != null) for (TmdbAccountParse.ListInfo l : lists) if (l.id == id) return l.name;
        return "";
    }

    /**
     * A whole TMDB list without an account, with the app's token, and its name: a list shown by number while the TV is
     * not signed in. Only a public list answers. Blocking.
     */
    static ImportedLists.Entry readPublicList(AccountHttp http, ListRef ref) throws TmdbAccountParse.Failure {
        List<String> out = new ArrayList<>();
        String name = "";
        for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
            AccountHttp.Reply r;
            try {
                r = http.send(TmdbAccountApi.listPage(ref.id, page, AccountHttp.Auth.APP));
            } catch (IOException e) {
                throw TmdbAccountParse.Failure.network(e.getClass().getSimpleName());
            }
            TmdbAccountParse.Page p = TmdbAccountParse.page(r, null);
            if (page == 1) name = p.name;
            addAll(out, p.ids);
            if (page >= p.totalPages) break;
        }
        return new ImportedLists.Entry(ref, ref.id, name, out);
    }

    // ---- Sending -------------------------------------------------------------------------------------------------

    private int push(long now) throws SignedOut {
        int sent = 0;
        for (SyncLedger.Op op : ledger.due(now)) {
            AccountHttp.Call call;
            if (op.list == SyncList.SEEN) {
                long id = ledger.listId(SyncList.SEEN);
                if (id == 0) continue;
                call = TmdbAccountApi.listItems(id, Collections.singletonList(op.id), op.present);
            } else {
                call = TmdbAccountApi.setOnAccount(session.accountId, session.sessionId, op.list, op.id, op.present);
            }
            try {
                TmdbAccountParse.done(send(call));
                ledger.pushed(op);
                sent++;
            } catch (TmdbAccountParse.Failure f) {
                note(f, op.toString());
                if (f.problem == TmdbAccountParse.Problem.RETRY) {
                    // Offline or TMDB busy: the rest would fail too, so they wait as they are.
                    ledger.retry(op, now);
                    break;
                }
                ledger.drop(op);
            }
        }
        return sent;
    }

    private AccountHttp.Reply send(AccountHttp.Call call) throws TmdbAccountParse.Failure, SignedOut {
        AccountHttp.Reply r;
        try {
            r = http.send(call);
        } catch (IOException e) {
            throw TmdbAccountParse.Failure.network(e.getClass().getSimpleName());
        }
        if (!r.ok()) {
            TmdbAccountParse.Failure f = TmdbAccountParse.failure(r);
            if (f.problem == TmdbAccountParse.Problem.SIGNED_OUT || f.problem == TmdbAccountParse.Problem.NOT_APPROVED
                    || f.problem == TmdbAccountParse.Problem.EXPIRED) {
                throw new SignedOut();
            }
            throw f;
        }
        return r;
    }

    private void note(TmdbAccountParse.Failure f, String what) {
        problems.add(what + ": " + f.problem + (f.code != 0 ? " (" + f.code + ")" : ""));
    }
}
