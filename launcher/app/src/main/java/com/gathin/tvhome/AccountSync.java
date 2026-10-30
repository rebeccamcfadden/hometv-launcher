package com.gathin.tvhome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One sync of the household's lists with the TMDB account (BACKLOG.md, TMDB sign-in and watchlist sync). Blocking:
 * run it off the main thread, never while Home waits on it. In order: find the TMDB lists behind Seen, Household and
 * Friends (and make a private Seen list if the account has none and that is allowed); read every list and merge the
 * writable ones with the TV's copy ({@link ListMerge}); then send the queued changes that are due. A list that can't be
 * read keeps the TV's copy; a change that can't be sent stays queued with a longer delay ({@link SyncLedger}); a
 * session TMDB no longer accepts stops the sync and asks for a new sign-in. Pure Java over {@link AccountHttp},
 * unit-tested against a fake TMDB (AccountSyncTest).
 */
final class AccountSync {
    /** Which TMDB lists are Seen, Household and Friends (config settings, see {@link ListRef}). */
    static final class Settings {
        final ListRef seen;
        final ListRef household;
        final ListRef friends;
        /** Whether to make a private list named as {@link #seen} says when the account has none. */
        final boolean createSeen;

        Settings(ListRef seen, ListRef household, ListRef friends, boolean createSeen) {
            this.seen = seen;
            this.household = household;
            this.friends = friends;
            this.createSeen = createSeen;
        }

        ListRef ref(SyncList list) {
            switch (list) {
                case SEEN:
                    return seen;
                case HOUSEHOLD:
                    return household;
                case FRIENDS:
                    return friends;
                default:
                    return ListRef.NONE;
            }
        }
    }

    static final class Result {
        /** The lists read from TMDB this time, merged with the TV's for the writable ones. Missing: not read. */
        final Map<SyncList, List<String>> lists;
        /** TMDB no longer accepts the session: sign in again. Queued changes are kept for the same account. */
        final boolean signedOut;
        /** Changes TMDB took, and changes still queued. */
        final int sent;
        final int queued;
        /** Short notes on what failed, for a log line: never a token or session id. */
        final List<String> problems;

        Result(Map<SyncList, List<String>> lists, boolean signedOut, int sent, int queued, List<String> problems) {
            this.lists = lists;
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

    /**
     * @param local the TV's writable lists now (My list, Favorites, Seen), each in its order
     * @param pull false to only send the queued changes, as after a change on the TV
     */
    Result run(Map<SyncList, List<String>> local, long now, boolean pull) {
        problems.clear();
        Map<SyncList, List<String>> lists = new EnumMap<>(SyncList.class);
        int sent = 0;
        boolean signedOut = false;
        try {
            if (pull || hasDue(SyncList.SEEN, now)) findLists(pull);
            if (pull) {
                for (SyncList list : SyncList.values()) {
                    List<String> remote = read(list);
                    if (remote == null) continue;
                    if (list.writable) {
                        List<String> mine = local.get(list);
                        ListMerge.Result m = ListMerge.merge(mine == null ? Collections.<String>emptyList() : mine,
                                ledger.base(list), ledger.pulledAt(list), remote, ledger.pending(list));
                        ledger.keepOnly(list, m.keep);
                        ledger.setBase(list, remote, now);
                        lists.put(list, m.local);
                    } else {
                        lists.put(list, remote);
                    }
                }
            }
            sent = push(now);
        } catch (SignedOut e) {
            signedOut = true;
            problems.add("TMDB no longer accepts the session");
        }
        return new Result(Collections.unmodifiableMap(lists), signedOut, sent, ledger.pending().size(),
                Collections.unmodifiableList(new ArrayList<>(problems)));
    }

    private boolean hasDue(SyncList list, long now) {
        for (SyncLedger.Op op : ledger.due(now)) if (op.list == list) return true;
        return false;
    }

    // ---- Finding the TMDB lists ----------------------------------------------------------------------------------

    private void findLists(boolean pull) throws SignedOut {
        List<TmdbAccountParse.ListInfo> mine = null;
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
            if (mine == null) mine = accountLists();
            if (mine == null) return;
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
     * A whole TMDB list without an account, with the app's token: Household or Friends set by number while the TV is
     * not signed in. Only a public list answers. Blocking.
     */
    static List<String> readPublicList(AccountHttp http, long listId) throws TmdbAccountParse.Failure {
        List<String> out = new ArrayList<>();
        for (int page = 1; page <= TmdbAccountApi.MAX_PAGES; page++) {
            AccountHttp.Reply r;
            try {
                r = http.send(TmdbAccountApi.listPage(listId, page, AccountHttp.Auth.APP));
            } catch (IOException e) {
                throw TmdbAccountParse.Failure.network(e.getClass().getSimpleName());
            }
            TmdbAccountParse.Page p = TmdbAccountParse.page(r, null);
            addAll(out, p.ids);
            if (page >= p.totalPages) break;
        }
        return out;
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
