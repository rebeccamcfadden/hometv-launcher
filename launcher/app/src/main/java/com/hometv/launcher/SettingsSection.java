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
     * The settings.ts section id: account, notes, services, stores, keys, weather, apps, theme, performance, screensaver, features,
     * start, home, region, tv, about; and First run's own tabs tmdbKey and priceKeys (F16).
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

        /** Shows another section and enters its pane. */
        void enter(String id);

        /**
         * A switched-off feature's On (the project lead, 2026-11-02; F34 made it the feature's own switch): the feature
         * on (FeaturePrefs.turnOn), then straight into its setup.
         */
        void turnOn(Features.Feature f);

        /** A feature was just switched off on its own pane (F34): the section shows again, now saying it is off. */
        void featureSwitchedOff(Features.Feature f);

        /** Features' overview: OK on a feature opens the section that sets it up (F34). */
        void openFeature(Features.Feature f);

        /** First run's cut-down split rather than Launcher settings: Features' lead says what turning one on does. */
        default boolean inSetup() {
            return false;
        }

        /**
         * The launcher must be rebuilt before this section can work (Account, once a TMDB key is on the TV but the
         * launcher was built without one, F16): rebuilt, it comes back to this page with that section entered.
         */
        default void rebuildInto(String id) {
            enter(id);
        }

        Shell shell();
    }
}
