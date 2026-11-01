package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Launcher settings' focus rules (design_preview/src/screens/settings.ts onKey), kept apart from the views so they
 * are tested on the JVM: what a key does on the section list and inside a pane, and which section the page opens on.
 */
final class SettingsNav {
    private SettingsNav() {}

    /** The sections in the order the list shows them (SettingsSection.id). */
    static final List<String> ORDER = Collections.unmodifiableList(Arrays.asList(
            "account", "notes", "services", "stores", "weather", "apps", "theme", "performance", "start", "home", "tv",
            "about"));

    enum Action {
        /** Focus the section's pane, on its first control. */
        ENTER,
        /** Move to the next section up or down; the list never lets focus out sideways. */
        MOVE_LIST,
        /** Nothing: ← on the list. */
        STAY,
        /** Close Launcher settings. */
        CLOSE,
        /** Move inside the pane; ← past its left edge goes back to the section. */
        MOVE_PANE,
        /** Leave a sub-state of the pane if it has one, else go back to the section. */
        BACK,
        /** The focused control's own OK. */
        PASS
    }

    /** A key on the section list. */
    static Action onList(Screen.Key key) {
        switch (key) {
            case RIGHT:
            case OK:
                return Action.ENTER;
            case UP:
            case DOWN:
                return Action.MOVE_LIST;
            case BACK:
                return Action.CLOSE;
            default:
                return Action.STAY;
        }
    }

    /** A key inside the pane, once the pane itself has passed on it (SettingsSection.onKey). */
    static Action inPane(Screen.Key key) {
        switch (key) {
            case BACK:
                return Action.BACK;
            case OK:
                return Action.PASS;
            default:
                return Action.MOVE_PANE;
        }
    }

    /** The section to open on: the one asked for when the page has it, else the first. */
    static String initial(String asked, List<String> shown) {
        if (shown.isEmpty()) return null;
        return asked != null && shown.contains(asked) ? asked : shown.get(0);
    }
}
