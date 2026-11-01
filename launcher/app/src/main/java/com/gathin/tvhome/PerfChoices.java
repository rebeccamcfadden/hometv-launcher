package com.gathin.tvhome;

import java.util.Map;

/**
 * Launcher settings › Performance (the project lead's decision, 2026-11-01): what the launcher draws that costs GPU
 * fill on a TV whose GPU redraws the whole screen every frame (the TCL's Mali-470). Card shadows (on, under the focused
 * card only, or off), the focus glow, the backdrop picture (the photo or drawing, or the theme's plain sky colour) and
 * the animated details. Motion sits on the same page but stays MotionChoice. Each choice is saved on the TV; without
 * one, a TV that starts at the light end ({@link #light}: Android's low-RAM flag, or config.xml / config.json
 * {@code light_defaults}) starts with {@link #LIGHT}, any other TV with {@link #FULL}. Pure Java, unit-tested
 * (PerfChoicesTest); PerfPrefs reads and saves the choices, and {@link #current} is what the views draw with.
 */
final class PerfChoices {
    enum Shadows {
        ON("on"), FOCUS("focus"), OFF("off");

        final String code;

        Shadows(String code) {
            this.code = code;
        }

        static Shadows parse(String code) {
            if (code == null) return null;
            for (Shadows s : values()) if (s.code.equals(code.trim())) return s;
            return null;
        }
    }

    static final String KEY_SHADOWS = "card_shadows";
    static final String KEY_GLOW = "focus_glow";
    static final String KEY_BACKDROP = "backdrop_picture";
    /** The Theme pane's old Moving details switch, kept under its key. */
    static final String KEY_DETAILS = "moving_details";
    static final String BACKDROP_PHOTO = "photo", BACKDROP_PLAIN = "plain";

    /** Every choice this page saves besides Motion: Follow theme and a theme push drop them. */
    static final String[] KEYS = {KEY_SHADOWS, KEY_GLOW, KEY_BACKDROP, KEY_DETAILS};

    final Shadows shadows;
    final boolean glow;
    final boolean plainBackdrop;
    final boolean details;

    PerfChoices(Shadows shadows, boolean glow, boolean plainBackdrop, boolean details) {
        this.shadows = shadows;
        this.glow = glow;
        this.plainBackdrop = plainBackdrop;
        this.details = details;
    }

    /** Everything the theme draws. */
    static final PerfChoices FULL = new PerfChoices(Shadows.ON, true, false, true);
    /**
     * A low-RAM TV's start: no card shadows (about 19 points of jank on the TCL), nothing animated; the focus glow
     * (one tile) and the backdrop picture stay, as the theme's look. Motion starts at Reduced (MotionChoice).
     */
    static final PerfChoices LIGHT = new PerfChoices(Shadows.OFF, true, false, false);
    /** The pane's Light mode button: every choice at its lightest (with Motion Off). */
    static final PerfChoices MINIMUM = new PerfChoices(Shadows.OFF, false, true, false);

    /**
     * Whether a TV starts at the light end: config.xml or config.json {@code light_defaults} {@code on} or
     * {@code off} (or true and false) decide; {@code auto}, empty or anything else follows Android's low-RAM flag.
     */
    static boolean light(String setting, boolean lowRam) {
        String s = setting == null ? "" : setting.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.equals("on") || s.equals("true")) return true;
        if (s.equals("off") || s.equals("false")) return false;
        return lowRam;
    }

    /** The choices in effect: each saved one, else the TV's start ({@link #LIGHT} or {@link #FULL}). */
    static PerfChoices resolve(Map<String, ?> saved, boolean light) {
        PerfChoices start = light ? LIGHT : FULL;
        Shadows shadows = Shadows.parse(string(saved, KEY_SHADOWS));
        Object glow = saved.get(KEY_GLOW);
        String backdrop = string(saved, KEY_BACKDROP);
        Object details = saved.get(KEY_DETAILS);
        return new PerfChoices(shadows != null ? shadows : start.shadows,
                glow instanceof Boolean ? (Boolean) glow : start.glow,
                BACKDROP_PLAIN.equals(backdrop) || (!BACKDROP_PHOTO.equals(backdrop) && start.plainBackdrop),
                details instanceof Boolean ? (Boolean) details : start.details);
    }

    /** Whether any of these choices is saved on the TV. */
    static boolean anySaved(Map<String, ?> saved) {
        for (String k : KEYS) if (saved.containsKey(k)) return true;
        return false;
    }

    boolean same(PerfChoices o) {
        return shadows == o.shadows && glow == o.glow && plainBackdrop == o.plainBackdrop && details == o.details;
    }

    private static String string(Map<String, ?> saved, String key) {
        Object v = saved.get(key);
        return v instanceof String ? (String) v : null;
    }

    private static PerfChoices current = FULL;

    /** What tiles and backdrops draw with now; set when the launcher's views are built (MainActivity, quick settings). */
    static PerfChoices current() {
        return current;
    }

    static void apply(PerfChoices c) {
        current = c == null ? FULL : c;
    }
}
