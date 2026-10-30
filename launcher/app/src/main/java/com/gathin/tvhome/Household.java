package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The household's state stored on the TV (docs/rework.md, Watchlists): {@link HouseholdState} kept as one JSON
 * string in the SharedPreferences file "household". Every change is saved at once (apply, off the main thread) and
 * tells the listeners. The first run starts from the prototype's mock seed (BACKLOG.md).
 */
final class Household {
    static final String PREFS = "household";
    private static final String KEY = "state";

    interface Listener {
        void onHouseholdChanged();
    }

    private final SharedPreferences prefs;
    private final Catalog catalog;
    private final List<Listener> listeners = new ArrayList<>();
    private HouseholdState state;

    Household(Context ctx, Catalog catalog) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.catalog = catalog;
        this.state = HouseholdState.fromJson(prefs.getString(KEY, null), catalog);
    }

    boolean inMyList(String id) {
        return state.inMyList(id);
    }

    boolean isSeen(String id) {
        return state.isSeen(id);
    }

    boolean isFavorite(String id) {
        return state.isFavorite(id);
    }

    /** Returns whether the title is on My list now. */
    boolean toggleMyList(String id) {
        boolean on = state.toggleMyList(id);
        changed();
        return on;
    }

    boolean toggleSeen(String id) {
        boolean on = state.toggleSeen(id);
        changed();
        return on;
    }

    boolean toggleFavorite(String id) {
        boolean on = state.toggleFavorite(id);
        changed();
        return on;
    }

    List<String> myListIds() {
        return state.myListIds();
    }

    List<String> recent() {
        return state.recent();
    }

    void pushRecent(String query) {
        if (state.pushRecent(query)) changed();
    }

    List<String> viewed() {
        return state.viewed();
    }

    /** Every title id the household keeps, for the catalogue to load. */
    List<String> titleIds() {
        return state.titleIds();
    }

    void pushViewed(String id) {
        if (state.pushViewed(id)) changed();
    }

    /** Back to the seed: the prototype's Reset state. */
    void reset() {
        prefs.edit().remove(KEY).apply();
        state = HouseholdState.defaults(catalog);
        notifyListeners();
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        prefs.edit().putString(KEY, state.toJson()).apply();
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onHouseholdChanged();
    }
}
