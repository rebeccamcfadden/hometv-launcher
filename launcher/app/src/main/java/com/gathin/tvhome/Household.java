package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The household's state stored on the TV (docs/rework.md, Watchlists): {@link HouseholdState} kept as one JSON
 * string in the SharedPreferences file "household". Every change is saved at once (apply, off the main thread) and
 * tells the listeners. A fresh install starts with empty lists (BACKLOG.md, Household seed).
 *
 * <p>V2 Phase 3: a change to My list, Favorites or Seen is also told to the {@link Recorder} ({@link Accounts}, which
 * queues it for the TMDB account), and {@link #applySynced} takes what a sync merged. The lists after My list are the
 * TMDB lists shown as chips ({@link ImportedLists}), or the catalogue's mock Household and Friends before the TV has
 * had an account.
 */
final class Household implements ListMembership {
    static final String PREFS = "household";
    private static final String KEY = "state";

    interface Listener {
        void onHouseholdChanged();
    }

    /** The TMDB account's side of a change made on the TV to a list it can keep. */
    interface Recorder {
        /** Whether a change to the list goes into the account's queue at all. */
        boolean queues(SyncList list);

        /** A change was saved, with its unsent copy ({@link #unsent}) when {@link #queues} said so. */
        void onListChanged();
    }

    private final SharedPreferences prefs;
    private final Catalog catalog;
    private final List<Listener> listeners = new ArrayList<>();
    private final ImportedLists imported = new ImportedLists();
    private HouseholdState state;
    private Recorder recorder;
    /** Moves with every change made on the TV, so a sync that read the lists before it knows they moved on. */
    private int version;

    Household(Context ctx, Catalog catalog) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.catalog = catalog;
        this.state = HouseholdState.fromJson(prefs.getString(KEY, null), catalog);
    }

    void setRecorder(Recorder r) {
        recorder = r;
    }

    @Override
    public boolean inMyList(String id) {
        return state.inMyList(id);
    }

    @Override
    public List<ShownList> shownLists() {
        return imported.shown();
    }

    @Override
    public boolean isOn(Title t, ShownList list) {
        return list.isMy() ? inMyList(t.id) : imported.isOn(t, list);
    }

    /**
     * My list in the order its titles were added (a sync keeps the TMDB watchlist's, oldest first), a TMDB list in the
     * order TMDB returned it; the mock lists have none, so the pool's order.
     */
    @Override
    public List<String> order(ShownList list, List<Title> pool) {
        if (list.isMy()) return state.myListIds();
        if (list.mock == null) return imported.ids(list);
        return ListMembership.super.order(list, pool);
    }

    boolean isSeen(String id) {
        return state.isSeen(id);
    }

    boolean isFavorite(String id) {
        return state.isFavorite(id);
    }

    /** Returns whether the title is on My list now. */
    boolean toggleMyList(String id) {
        return record(SyncList.MY_LIST, id, state.toggleMyList(id));
    }

    boolean toggleSeen(String id) {
        return record(SyncList.SEEN, id, state.toggleSeen(id));
    }

    boolean toggleFavorite(String id) {
        return record(SyncList.FAVORITES, id, state.toggleFavorite(id));
    }

    private boolean record(SyncList list, String id, boolean on) {
        version++;
        // The change and its unsent copy for the account go into one save, so neither outlives the other.
        if (recorder != null && recorder.queues(list)) state.queue(list, id, on, System.currentTimeMillis());
        changed();
        if (recorder != null) recorder.onListChanged();
        return on;
    }

    /** Changes the TMDB account has not queued yet. */
    List<SyncLedger.Op> unsent() {
        return state.unsent();
    }

    /** The account queued (and saved) these changes. */
    void queued(List<SyncLedger.Op> ops) {
        if (state.dropUnsent(ops)) prefs.edit().putString(KEY, state.toJson()).apply();
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

    /** Every title on My list or on a shown TMDB list: what is kept priced in the background. */
    List<String> listedIds() {
        Set<String> all = new LinkedHashSet<>(state.myListIds());
        all.addAll(imported.allIds());
        return new ArrayList<>(all);
    }

    /** Every title id the household keeps, the shown lists' too, for the catalogue to load. */
    List<String> titleIds() {
        Set<String> all = new LinkedHashSet<>(state.titleIds());
        all.addAll(imported.allIds());
        return new ArrayList<>(all);
    }

    void pushViewed(String id) {
        if (state.pushViewed(id)) changed();
    }

    // ---- The TMDB account (V2 Phase 3) ----------------------------------------------------------------------------

    int version() {
        return version;
    }

    /** My list, Favorites and Seen as they are now, for a sync. */
    Map<SyncList, List<String>> syncedLists() {
        Map<SyncList, List<String>> out = new EnumMap<>(SyncList.class);
        for (SyncList l : SyncList.values()) if (l.writable) out.put(l, state.ids(l));
        return out;
    }

    /**
     * Takes the lists a sync merged, unless something changed on the TV since it read them ({@code readAt} is the
     * {@link #version} then): that change is queued, and the next sync merges it. Returns whether they were taken.
     */
    boolean applySynced(Map<SyncList, List<String>> lists, int readAt) {
        if (readAt != version) return false;
        boolean any = false;
        for (Map.Entry<SyncList, List<String>> e : lists.entrySet()) any |= state.setList(e.getKey(), e.getValue());
        if (any) changed();
        return true;
    }

    /** The lists after My list: the TMDB lists shown, in chip order, each as last read; or null for the mock lists. */
    void setShownLists(List<ImportedLists.Entry> lists) {
        if (lists == null ? imported.setMock() : imported.set(lists)) notifyListeners();
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
