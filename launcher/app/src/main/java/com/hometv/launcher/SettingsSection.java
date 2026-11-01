package com.hometv.launcher;

import android.content.Context;
import android.view.View;

/**
 * One section of the Launcher settings page (design_preview/src/screens/settings.ts, V2 design 05-settings A): its
 * row in the list on the left, and the pane it shows on the right. Landing on the row previews the pane; → or OK
 * enters it on {@link #firstFocus()}; ← from the pane's left edge returns to the row.
 */
interface SettingsSection {
    /**
     * The settings.ts section id: account, notes, services, stores, weather, apps, theme, motion, start, home, tv,
     * about.
     */
    String id();

    /** The row's label (a string resource). */
    int label();

    /** The row's icon (a drawable resource). */
    int icon();

    /** The grey value on the row ("Not signed in", "New York", "Midnight"); empty for none. Read again on change. */
    CharSequence value(Context ctx);

    /** Builds the pane. Called each time the section is shown, so it reflects the current choices. */
    View pane(Context ctx, Host host);

    /** The view → or OK enters on; null when the pane has nothing to focus. Valid after {@link #pane}. */
    View firstFocus();

    /** Back inside the pane: return true when the pane closed a sub-state of its own (Change city, Choose lists). */
    default boolean onBack() {
        return false;
    }

    /**
     * ←, →, ↓ or OK inside the pane, before the page's own handling (Home row apps moves a picked-up app). Return true
     * when used. Back goes to {@link #onBack()}.
     */
    default boolean onKey(Screen.Key key, View focused) {
        return false;
    }

    /** Launcher settings closed (Back, Home, or the launcher rebuilt): stop anything the pane left running. */
    default void onClose() {
    }

    /** A character typed on a keyboard while the pane is showing (Change city's search). Return true when used. */
    default boolean onText(char c) {
        return false;
    }

    /** What the settings page offers its sections. */
    interface Host {
        /** A choice changed: the page reads every row's value again. */
        void valueChanged();

        /** The theme or its fonts changed: re-apply and rebuild the launcher, returning to this section. */
        void themeChanged();

        Shell shell();
    }
}
