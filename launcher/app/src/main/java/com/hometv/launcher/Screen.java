package com.hometv.launcher;

import android.os.Bundle;
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
    enum Tab { HOME, MOVIES, TV, SEARCH }

    /** The remote's keys a screen sees. A keyboard's Backspace (KEYCODE_DEL) arrives as BACK, on every screen. */
    enum Key { UP, DOWN, LEFT, RIGHT, OK, BACK }

    final String label;
    final View view;
    final Layer layer;
    /** Set for the nav's top-level pages. */
    Tab tab;

    final Map<String, View> memory = new HashMap<>();
    View lastFocus;
    String lastFocusKey;
    /**
     * F26: the item focused before the activity was made again, not there yet (a grid still loading); the Shell
     * focuses it when a rebuild brings it, until the first key press.
     */
    String pendingFocusKey;

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

    /**
     * A character typed on a hardware keyboard or a remote with keys (app.ts onText). Return true when used; Search
     * types it into its bar. Other screens ignore typing.
     */
    boolean onText(char c) {
        return false;
    }

    /** A page's text box as a phone remote sees it (F27; app.ts TextField): what it holds, set it whole, and Enter. */
    interface TextField {
        /** Whether it takes typing now (Search behind its TMDB gate doesn't). */
        boolean ready();

        String value();

        void set(String value);

        void enter();
    }

    /** The text box a phone remote types into while this screen shows, or null: buttons only (F27). */
    TextField textField() {
        return null;
    }

    /** The title this screen is about (a title preview), for the phone remote's "On the TV" card (F39); else null. */
    Title title() {
        return null;
    }

    /** OK held down on an item (the hold-OK menus). Return true when used. */
    boolean onLongOk(View focused) {
        return false;
    }

    /** Each time the screen becomes the top of the stack. */
    void onShow() {
    }

    /**
     * Whether Phone notes' import pill moves to the top centre while this page shows, because its own content runs to
     * the bottom edge where the pill otherwise sits (Launcher settings' pane).
     */
    boolean pillAtTop() {
        return false;
    }

    /**
     * When a top-level page stops being the current tab: another tab, Back or Backspace to Home, or the remote's
     * Home. Not called for a popup or page pushed over it. Search forgets its query here (app.ts goTab).
     */
    void onLeave() {
    }

    void onFocus(View v) {
    }

    /**
     * F26: what a top-level page keeps when the activity is made again, as after Android ended the process while
     * another app played: its own choices (Search's query, a library page's filters). Nothing by default.
     */
    void save(Bundle out) {
    }

    /** Puts back what {@link #save} wrote, before the page first shows. */
    void restore(Bundle in) {
    }
}
