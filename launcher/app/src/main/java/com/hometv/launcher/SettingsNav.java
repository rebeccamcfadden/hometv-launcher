package com.hometv.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Launcher settings' focus rules (design_preview/src/screens/settings.ts onKey), kept apart from the views so they
 * are tested on the JVM: what a key does on the section list and inside a pane, and which section the page opens on.
 */
final class SettingsNav {
    private SettingsNav() {}

    /** One group of sections under its heading (F23): a key for its heading's string, and its sections in order. */
    static final class Group {
        final String key;
        final List<String> ids;

        Group(String key, String... ids) {
            this.key = key;
            this.ids = Collections.unmodifiableList(Arrays.asList(ids));
        }
    }

    /**
     * The section list's groups, in order (F23, grouping A, the project lead, 2026-11-01; design_preview settings.ts
     * GROUPS). API keys (F16) comes right after Stores (the project lead, 2026-11-02). A group with none of its sections
     * on this TV isn't shown.
     */
    static final List<Group> GROUPS = Collections.unmodifiableList(Arrays.asList(
            new Group("accounts", "account", "notes"),
            new Group("watch", "services", "stores", "keys", "features"),
            new Group("look", "theme", "performance", "screensaver", "apps", "weather"),
            new Group("tv", "start", "home", "region", "remote", "tv", "about")));

    /** The sections in the order the list shows them (SettingsSection.id): the groups' in turn. */
    static final List<String> ORDER = order();

    private static List<String> order() {
        List<String> out = new ArrayList<>();
        for (Group g : GROUPS) out.addAll(g.ids);
        return Collections.unmodifiableList(out);
    }

    /** A line of the section list: a group's heading (id null) or a section. */
    static final class Line {
        final String group;
        final String id;

        private Line(String group, String id) {
            this.group = group;
            this.id = id;
        }

        boolean isHeading() {
            return id == null;
        }
    }

    /**
     * The section list for the sections this TV shows ({@code shown}, any order): each group that has one, its heading
     * then its sections in {@link #ORDER}. A section in no group (none today) comes last, under no heading.
     */
    static List<Line> lines(List<String> shown) {
        List<Line> out = new ArrayList<>();
        for (Group g : GROUPS) {
            boolean head = false;
            for (String id : g.ids) {
                if (!shown.contains(id)) continue;
                if (!head) out.add(new Line(g.key, null));
                head = true;
                out.add(new Line(g.key, id));
            }
        }
        for (String id : shown) if (!ORDER.contains(id)) out.add(new Line(null, id));
        return out;
    }

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
