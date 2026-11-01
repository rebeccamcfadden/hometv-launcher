package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which of the account's TMDB lists show as chips, as chosen on the TV (docs/rework.md, Lists by name, 2026-10-31):
 * Choose lists has a Show / Hidden switch per list, any number can show, and they show in the account's order (the
 * order TMDB lists them in). config.xml's tmdb_shown_lists (or the older tmdb_household_list and tmdb_friends_list),
 * when set, overrides the choice ({@link #refs}). A TV that chose Household and Friends before starts with those two
 * shown, in that order ({@link #load}). Pure Java, unit-tested (ListChoicesTest); {@link Accounts} keeps the numbers.
 */
final class ListChoices {
    private final List<Long> shown = new ArrayList<>();

    ListChoices(List<Long> shown) {
        for (Long id : shown) if (id != null && id > 0 && !this.shown.contains(id)) this.shown.add(id);
    }

    /**
     * The choice as saved ("8221116,9001", see {@link #save}); before anything was saved (null), the lists a TV chose
     * for Household and Friends, so nobody loses a chip on the update.
     */
    static ListChoices load(String saved, long household, long friends) {
        List<Long> ids = new ArrayList<>();
        if (saved == null) {
            ids.add(household);
            ids.add(friends);
        } else {
            for (String s : saved.split(",")) {
                try {
                    ids.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException e) {
                    // Not a list number: skipped.
                }
            }
        }
        return new ListChoices(ids);
    }

    String save() {
        StringBuilder b = new StringBuilder();
        for (Long id : shown) b.append(b.length() == 0 ? "" : ",").append(id);
        return b.toString();
    }

    boolean isShown(long listId) {
        return listId > 0 && shown.contains(listId);
    }

    /** The shown lists' numbers, in chip order. */
    List<Long> ids() {
        return Collections.unmodifiableList(new ArrayList<>(shown));
    }

    /**
     * Shows or hides a list; a list shown joins the others in the account's order ({@code accountOrder}, the account's
     * list numbers as TMDB gives them). Returns whether anything changed.
     */
    boolean setShown(long listId, boolean show, List<Long> accountOrder) {
        if (listId <= 0 || isShown(listId) == show) return false;
        if (show) {
            shown.add(listId);
            sortBy(accountOrder);
        } else {
            shown.remove(listId);
        }
        return true;
    }

    /**
     * The account's lists as just read: the shown ones follow its order, and a list no longer in the account is
     * dropped. Returns whether anything changed.
     */
    boolean follow(List<Long> accountOrder) {
        List<Long> before = new ArrayList<>(shown);
        shown.retainAll(accountOrder);
        sortBy(accountOrder);
        return !before.equals(shown);
    }

    /** In the account's order; lists it doesn't have keep their place after those it does. Linear, by an index map. */
    private void sortBy(List<Long> order) {
        if (order == null) return;
        java.util.Map<Long, Integer> at = new java.util.HashMap<>();
        for (int i = 0; i < order.size(); i++) at.putIfAbsent(order.get(i), i);
        List<Long> known = new ArrayList<>(), rest = new ArrayList<>();
        for (Long id : shown) (at.containsKey(id) ? known : rest).add(id);
        known.sort((a, b) -> Integer.compare(at.get(a), at.get(b)));
        shown.clear();
        shown.addAll(known);
        shown.addAll(rest);
    }

    /**
     * The lists that show: config.xml's when it sets any, else the TV's choice while signed in. Signed out, the TV's
     * choice (the account's own lists, maybe private) waits for the next sign-in.
     */
    List<ListRef> refs(List<ListRef> override, boolean signedIn) {
        if (override != null && !override.isEmpty()) return override;
        if (!signedIn) return Collections.emptyList();
        List<ListRef> out = new ArrayList<>();
        for (Long id : shown) out.add(ListRef.parse(Long.toString(id)));
        return out;
    }

    /**
     * Whether the mock Household and Friends show: in a build that can't sign in, or on a TV that never had an
     * account and has no list to show; once the TV has an account, only the lists that show (maybe none).
     */
    static boolean mock(boolean available, boolean hasAccount, List<ListRef> shown) {
        return !available || (!hasAccount && shown.isEmpty());
    }
}
