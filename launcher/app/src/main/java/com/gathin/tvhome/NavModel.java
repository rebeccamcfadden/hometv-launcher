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
    private final List<Listener> listeners = new ArrayList<>();

    void listen(Listener l) {
        listeners.add(l);
        l.onNavChanged(this);
    }

    void set(boolean inputs, boolean network, int icon) {
        if (inputs == inputsShown && network == networkShown && icon == networkIcon) return;
        inputsShown = inputs;
        networkShown = network;
        networkIcon = icon;
        for (Listener l : listeners) l.onNavChanged(this);
    }
}
