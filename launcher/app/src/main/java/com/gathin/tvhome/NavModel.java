package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * What every page's nav shows on its right: whether the Inputs icon is there (a streaming box has nothing to list)
 * and the network icon. Each page has its own nav, so they all listen here.
 */
final class NavModel {
    interface Listener {
        void onNavChanged(NavModel model);
    }

    boolean inputsShown;
    boolean networkShown = true;
    int networkIcon = R.drawable.ic_ui_wifi;
    /** Android reached the internet (a validated network). The icon stays the same when only this changes, but Home
     *  fetches the weather again when it comes back. */
    boolean internet;
    private final List<Listener> listeners = new ArrayList<>();

    /** Adds a listener and tells it the current state at once. */
    void listen(Listener l) {
        listeners.add(l);
        l.onNavChanged(this);
    }

    /** A nav that left the screen stops listening, so a page pushed and popped can be collected. */
    void unlisten(Listener l) {
        listeners.remove(l);
    }

    void set(boolean inputs, boolean network, int icon, boolean online) {
        if (inputs == inputsShown && network == networkShown && icon == networkIcon && online == internet) return;
        inputsShown = inputs;
        networkShown = network;
        networkIcon = icon;
        internet = online;
        notifyListeners();
    }

    /** An input was added, removed or renamed: listeners that list the inputs (Home's row) refresh, even when
     *  the nav itself shows the same. */
    void inputsChanged() {
        notifyListeners();
    }

    /** Over a copy: a listener may stop listening while it is told (its view leaving the screen). */
    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onNavChanged(this);
    }
}
