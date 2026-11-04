package com.hometv.launcher;

/**
 * Launcher settings' Motion (design_preview/src/core/settings.ts; the quick-settings dropdown's Motion row): Full,
 * Reduced or Off, saved on the TV under {@link #KEY}. Older settings had only the Animations switch ("animations")
 * and config.xml's reduced_motion; without a saved Motion they decide it, so a TV keeps what it had. A TV whose
 * config.xml or config.json sets reduced_motion never gets Full (the project lead's decision, 2026-10-31). Pure Java,
 * unit-tested (MotionChoiceTest); MotionPrefs reads and writes it.
 *
 * <p>Reduced keeps the fades and drops the slides, rises and focus scale-ups (design_preview's base.css); Off moves
 * nothing and fades nothing. Ui reads {@link #moves()} and {@link #fades()}.
 */
enum MotionChoice {
    FULL("full"),
    REDUCED("reduced"),
    OFF("off");

    /** The preference key, and its values {@code full}, {@code reduced} and {@code off}. */
    static final String KEY = "motion";

    final String code;

    MotionChoice(String code) {
        this.code = code;
    }

    /** The choice saved as {@code code}, or null for none or an unknown value. */
    static MotionChoice parse(String code) {
        if (code == null) return null;
        for (MotionChoice m : values()) if (m.code.equals(code.trim())) return m;
        return null;
    }

    /**
     * The Motion in effect. A saved choice wins over the Animations switch; without one, the switch off is Off,
     * reduced_motion is Reduced, and anything else is Full. On a TV whose config sets reduced_motion
     * ({@code configReduced}) the result is at least Reduced: a saved Full is Reduced there, a saved Off stays Off.
     */
    static MotionChoice resolve(String saved, boolean animations, boolean configReduced) {
        return resolve(saved, animations, configReduced, false);
    }

    /**
     * As {@link #resolve(String, boolean, boolean)}, on a TV that starts at the light end ({@code light}, PerfChoices):
     * without a saved choice, Motion there is Off until the household picks Reduced or Full, since Reduced's fades
     * cost frames on a low-memory TV (TCL BeyondTV, 2026-11-03; Rebecca, 2026-11-04).
     */
    static MotionChoice resolve(String saved, boolean animations, boolean configReduced, boolean light) {
        MotionChoice m = parse(saved);
        if (m == null) m = !animations || light ? OFF : FULL;
        return configReduced && m == FULL ? REDUCED : m;
    }

    /** Whether the launcher's reduced motion applies (Config.reducedMotion()). */
    boolean reduced() {
        return this != FULL;
    }

    /** Things slide, rise and scale up (pages, rows, popups, focus): Full only. */
    boolean moves() {
        return this == FULL;
    }

    /** Things fade in and out (pages, popups, menus, the backdrop's crossfade): Full and Reduced. */
    boolean fades() {
        return this != OFF;
    }

    /**
     * The scale a focused item takes: {@code scaleTo}, except with Reduced, which drops focus scale-ups
     * (design_preview's base.css, .is-focused). Off keeps the scale and only drops the transition to it.
     */
    float focusScale(boolean focused, float scaleTo) {
        return focused && this != REDUCED ? scaleTo : 1f;
    }

    /** The Motion in effect on a TV whose system animations are off (animator scale 0): Off, whatever was chosen. */
    MotionChoice withSystem(boolean systemAnimationsOff) {
        return systemAnimationsOff ? OFF : this;
    }

    /** What the old Animations switch shows: on unless Off. */
    boolean animations() {
        return this != OFF;
    }

    /** OK on the Motion row: Full, Reduced, Off, then Full again. */
    MotionChoice next() {
        return values()[QuickSteps.next(ordinal(), values().length)];
    }

    /** OK on the Motion row of a TV whose config sets reduced_motion when {@code configReduced}: Full is skipped. */
    MotionChoice next(boolean configReduced) {
        MotionChoice m = next();
        return configReduced && m == FULL ? m.next() : m;
    }
}
