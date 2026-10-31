package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The Home row apps pane's edits (design_preview/README.md, Launcher settings, The other panes; src/screens/panes/
 * apps.ts): OK on an app on Home picks it up, as Home's hold-OK Move does; ← → move it, OK puts it down, ↓ takes it
 * off Home (one must stay) and Back puts the row back as it was when it was picked up. OK on another app adds it at
 * the end, already picked up; Back then takes it off again. Every change is the row Home shows at once, so the pane
 * saves after each one. Pure Java over {@link AppRow}, unit-tested (HomeRowEditTest); HomeAppsSection draws it.
 */
final class HomeRowEdit {
    private final Predicate<String> shown;
    private List<String> full;
    private String moving;
    private List<String> before;

    /** {@code full} is the saved row (HomeApps.full), {@code shown} whether an app can show (HomeApps.shown). */
    HomeRowEdit(List<String> full, Predicate<String> shown) {
        this.full = new ArrayList<>(full);
        this.shown = shown;
    }

    /** The whole row to save, with the apps that can't show now kept in their places. */
    List<String> full() {
        return Collections.unmodifiableList(full);
    }

    /** The apps on Home's row, in order. */
    List<String> visible() {
        return AppRow.visible(full, shown);
    }

    /** The app picked up, or null. */
    String moving() {
        return moving;
    }

    /** Picks up an app on the row. False when it isn't on it. */
    boolean pickUp(String pkg) {
        if (!visible().contains(pkg)) return false;
        before = new ArrayList<>(full);
        moving = pkg;
        return true;
    }

    /**
     * Adds an app at the end of the row, picked up so it can be placed. False when it is on the row already or the row
     * is full ({@link AppRow#MAX}).
     */
    boolean add(String pkg) {
        if (visible().contains(pkg) || visible().size() >= AppRow.MAX) return false;
        before = new ArrayList<>(full);
        full = AppRow.add(AppRow.remove(full, pkg), pkg);
        moving = pkg;
        return true;
    }

    /** ← (-1) or → (+1) with the app picked up. False when nothing moved (nothing picked up, or at that end). */
    boolean step(int delta) {
        if (moving == null) return false;
        List<String> next = AppRow.move(full, visible(), moving, delta);
        if (next.equals(full)) return false;
        full = next;
        return true;
    }

    /** OK: the app stays where it is. */
    void putDown() {
        moving = null;
        before = null;
    }

    /** ↓: the app picked up leaves the row. False, and nothing changes, when it is the last app on it. */
    boolean takeOff() {
        if (moving == null) return false;
        if (visible().size() <= 1) return false;
        full = AppRow.remove(full, moving);
        moving = null;
        before = null;
        return true;
    }

    /** Back: the row as it was before the pick-up (an added app leaves again). False when nothing was picked up. */
    boolean cancel() {
        if (moving == null) return false;
        full = before;
        moving = null;
        before = null;
        return true;
    }
}
