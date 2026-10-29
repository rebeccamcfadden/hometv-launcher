package com.gathin.tvhome;

import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * One entry on the Shell's stack (design_preview/src/core/app.ts, Screen). A page fills the stage under the nav; a
 * popup draws over the nearest page behind a scrim; a dialog (a dropdown, a drawer) draws over whatever is showing.
 * Each screen keeps its own focus: the item last focused, and the last item per row or grid (memory).
 */
class Screen {
    enum Layer { PAGE, POPUP, DIALOG }

    /** The nav's sections, in the order the page transition reads as left to right. */
    enum Tab { HOME, MOVIES, TV, MUSIC, SEARCH }

    enum Key { UP, DOWN, LEFT, RIGHT, OK, BACK }

    final String label;
    final View view;
    final Layer layer;
    /** Set for the nav's top-level pages. */
    Tab tab;

    final Map<String, View> memory = new HashMap<>();
    View lastFocus;
    String lastFocusKey;

    Screen(String label, View view, Layer layer) {
        this.label = label;
        this.view = view;
        this.layer = layer;
    }

    /** The item focused the first time the screen shows; null for its first focusable item. */
    View defaultFocus() {
        return null;
    }

    /** Return true to stop the Shell's own handling of the key. */
    boolean onKey(Key key, View focused) {
        return false;
    }

    /** OK held down on an item (the hold-OK menus). Return true when used. */
    boolean onLongOk(View focused) {
        return false;
    }

    /** Each time the screen becomes the top of the stack. */
    void onShow() {
    }

    void onFocus(View v) {
    }
}
