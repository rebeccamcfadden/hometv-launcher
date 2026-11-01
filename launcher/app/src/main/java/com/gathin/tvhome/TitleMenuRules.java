package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the hold-OK menu on a title card holds (design_preview/README.md, Title menu; F3, the project lead,
 * 2026-11-01): Mark seen, Favorite, the lists and More like this, each worded for the title's state; and the lists
 * menu behind the lists row: My list, then the shown TMDB lists the TV can change, each ticked when the title is on
 * it. English only, as the rest of V1. Pure Java, unit-tested (TitleMenuRulesTest); {@link TitleMenu} builds the
 * menus.
 */
final class TitleMenuRules {
    private TitleMenuRules() {}

    /** MY_LIST: the lists row when My list is the only list the TV can change (signed out, or not a TMDB title). */
    enum Action { SEEN, FAVORITE, MY_LIST, LISTS, MORE_LIKE }

    /** At most this many rows in the lists menu: 72 px rows in the title-safe height (1080 less 2 × 54, less padding). */
    static final int MAX_LIST_ROWS = 13;

    static final class Row {
        final Action action;
        final String label;
        /** The right-hand note, or null: which lists hold the title. */
        final String note;
        /** Whether the title has the row's state (seen, a favourite, on a list): the row's icon shows it. */
        final boolean on;

        Row(Action action, String label, String note, boolean on) {
            this.action = action;
            this.label = label;
            this.note = note;
            this.on = on;
        }
    }

    /** One list in the lists menu, ticked when the title is on it. */
    static final class ListRow {
        final ShownList list;
        final boolean on;

        ListRow(ShownList list, boolean on) {
            this.list = list;
            this.on = on;
        }
    }

    /**
     * The menu's rows, in order; first focus is the first. {@code lists}: the lists menu ({@link #lists}). When My list
     * is all it holds (signed out, or a title TMDB doesn't know), the lists row puts the title on My list or takes it off
     * itself; otherwise it opens the lists menu. Signed out, the TMDB lists are left out (they need the account); Seen,
     * Favorite and My list stay, as on the title preview, since they are kept on the TV.
     */
    static List<Row> rows(boolean seen, boolean favorite, List<ListRow> lists) {
        List<Row> out = new ArrayList<>();
        out.add(new Row(Action.SEEN, seen ? "Mark unseen" : "Mark seen", null, seen));
        out.add(new Row(Action.FAVORITE, favorite ? "Unfavorite" : "Favorite", null, favorite));
        if (lists.size() == 1 && lists.get(0).list.isMy()) {
            boolean my = lists.get(0).on;
            out.add(new Row(Action.MY_LIST, my ? "Remove from My list" : "Add to My list", null, my));
        } else {
            List<ShownList> on = on(lists);
            out.add(new Row(Action.LISTS, on.isEmpty() ? "Add to a list" : "Change lists", listsNote(on),
                    !on.isEmpty()));
        }
        out.add(new Row(Action.MORE_LIKE, "More like this", null, false));
        return Collections.unmodifiableList(out);
    }

    /** "On My list", "On Our picks", "On 2 lists"; null on none. */
    static String listsNote(List<ShownList> on) {
        if (on.isEmpty()) return null;
        if (on.size() == 1) return "On " + on.get(0).name;
        return "On " + on.size() + " lists";
    }

    /**
     * My list, then the shown TMDB lists in chip order (past the chip cap too) when the TV can change them
     * ({@code tmdb}: signed in, and the title is a TMDB title). The mock Household and Friends, and a list set by a name
     * not found yet, are only read, so they are left out; past {@link #MAX_LIST_ROWS} the rest are too.
     */
    static List<ListRow> lists(ListMembership m, Title t, boolean tmdb) {
        List<ListRow> out = new ArrayList<>();
        out.add(new ListRow(ShownList.MY, m.isOn(t, ShownList.MY)));
        if (!tmdb) return out;
        for (ShownList l : m.shownLists()) {
            if (out.size() >= MAX_LIST_ROWS) break;
            if (!changeable(l)) continue;
            out.add(new ListRow(l, m.isOn(t, l)));
        }
        return out;
    }

    /** Whether the title menu can put a title on the list: My list, or a shown TMDB list with a number. */
    static boolean changeable(ShownList l) {
        return l.isMy() || l.mock == null && l.tmdbId > 0;
    }

    /** The lists that hold the title, in menu order: the lists row's note. */
    static List<ShownList> on(List<ListRow> lists) {
        List<ShownList> out = new ArrayList<>();
        for (ListRow r : lists) if (r.on) out.add(r.list);
        return out;
    }

    /** The toast after a list row: "Added to Our picks", "Removed from My list". */
    static String listToast(String list, boolean on) {
        return (on ? "Added to " : "Removed from ") + list;
    }

    /** The toast when TMDB didn't take a change to one of its lists, which then goes back as it was. */
    static String listFailed(String list) {
        return "Couldn’t change " + list + " on TMDB. Try again in a moment.";
    }

    /** The toast for OK on a list whose last change TMDB hasn't answered yet: nothing is sent. */
    static String listBusy(String list) {
        return "Still saving " + list + " on TMDB…";
    }
}
