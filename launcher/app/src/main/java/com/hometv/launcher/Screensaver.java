package com.hometv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

/**
 * F36 (Rebecca chose the prototype's shape, 2026-11-04): the clock screensaver's choices and rules, kept apart from
 * the views so they can be tested (design_preview/src/ui/screensaver.ts). Launcher settings › Screensaver saves how
 * long Home sits untouched before the clock shows ({@link After}, Off on every TV until the household picks a time)
 * and what is behind the clock ({@link Back}). MainActivity shows the clock over Home ({@link ClockScene}), and
 * {@link ClockDream} is the same clock as the TV's own screensaver.
 */
final class Screensaver {
    private Screensaver() {}

    static final String KEY_AFTER = "screensaver_after";
    static final String KEY_BACK = "screensaver_back";

    /** Every minute the clock fades out, moves to a new spot and fades in, so nothing burns in. */
    static final long MOVE_MS = 60_000;
    static final long FADE_OUT_MS = 900;
    static final long FADE_IN_MS = 1200;
    /** The glow's colour change: 16 steps over 8 s (a full-screen gradient isn't redrawn every frame). */
    static final int GLOW_STEPS = 16;
    static final long GLOW_STEP_MS = 500;
    /** Deep, low-brightness glow colours, drawn at partial alpha on black (upstream's ClockScene palette). */
    static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};
    /** The glow's alpha at its centre, halfway out and at its edge (25 %, 8 %, 0 %). */
    static final int[] GLOW_ALPHA = {0x40, 0x14, 0x00};
    /**
     * The clock's text colours: light on its dark screen whatever the theme (Daylight's dark text would vanish on black).
     * Midnight's text-1 and text-2.
     */
    static final int TEXT_1 = 0xFFF4F5F7;
    static final int TEXT_2 = 0xFFADB5C0;
    /** Theme picture: the backdrop darkened by this much black, so the clock reads over it. */
    static final int PICTURE_SHADE = 0x9E;

    /** How long Home sits with no key before the clock shows; Off never starts it from Home. */
    enum After {
        OFF("off", 0), FIVE("5", 5), FIFTEEN("15", 15), THIRTY("30", 30);

        final String code;
        final int minutes;

        After(String code, int minutes) {
            this.code = code;
            this.minutes = minutes;
        }

        long ms() {
            return minutes * 60_000L;
        }

        /** A saved code; anything unknown, missing or old is Off. */
        static After parse(String code) {
            for (After a : values()) if (a.code.equals(code)) return a;
            return OFF;
        }
    }

    /** What the clock floats over: a slow colour glow, the theme's picture darkened, or black (for an OLED). */
    enum Back {
        GLOW("glow"), PICTURE("picture"), BLACK("black");

        final String code;

        Back(String code) {
            this.code = code;
        }

        /** A saved code; anything unknown or missing is Glow. */
        static Back parse(String code) {
            for (Back b : values()) if (b.code.equals(code)) return b;
            return GLOW;
        }
    }

    /**
     * Whether the clock may start over Home now: a time is chosen, Home itself is the top screen (no dropdown, popup or
     * page over it), and the launcher is in front with the window's focus (no app, Live TV or a translucent panel such
     * as quick settings over it).
     */
    static boolean mayStart(After after, boolean homeOnTop, boolean resumed, boolean windowFocus) {
        return after != After.OFF && homeOnTop && resumed && windowFocus;
    }

    /**
     * A spot that keeps the whole clock on screen: {@code r} (0 to 1, random) across 10–90 % of the room left over
     * once the clock is placed, so it never touches an edge.
     */
    static int place(float r, int room) {
        return Math.round(Math.max(0, room) * (0.1f + 0.8f * Math.max(0f, Math.min(1f, r))));
    }

    /** Two colours mixed, {@code f} of the way from a to b, channel by channel (alpha included). */
    static int mix(int a, int b, float f) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * f) & 0xFF) << shift;
        }
        return out;
    }

    /** The glow's three gradient stops for a palette colour: the colour at 25 %, 8 % and 0 %. */
    static int[] glowStops(int color) {
        int c = color & 0x00FFFFFF;
        return new int[]{(GLOW_ALPHA[0] << 24) | c, (GLOW_ALPHA[1] << 24) | c, (GLOW_ALPHA[2] << 24) | c};
    }

    /** Milliseconds until the next whole minute, so the clock's minute changes on time. At least 1. */
    static long toNextMinute(long nowMs) {
        return 60_000 - Math.floorMod(nowMs, 60_000L);
    }

    // ---- The key helpers (HomeKeyService, InputKeyService) see a key before Home does ----------------------------

    /** Wakes the clock over Home; set by MainActivity while it shows, null otherwise. Main thread only. */
    private static Runnable wake;
    /** The key a helper woke the clock with: its repeats and its release are swallowed too. */
    private static int helperWakeKey = KeyEvent.KEYCODE_UNKNOWN;

    /** MainActivity: the clock shows over Home (with how to wake it), or not (null). */
    static void setShown(Runnable wakeIt) {
        wake = wakeIt;
    }

    /**
     * Called first by a key helper. While the clock shows, a key the helper would act on (an app button, Input, the
     * gear) only wakes it; the press, its repeats and its release are swallowed here. Volume keys are left alone, as
     * Home leaves them. True when the helper must stop.
     */
    static boolean helperKey(KeyEvent e) {
        return helperKey(e.getKeyCode(), e.getAction(), e.getRepeatCount());
    }

    static boolean helperKey(int code, int action, int repeat) {
        if (isVolume(code)) return false;
        if (wake != null) {
            if (action == KeyEvent.ACTION_DOWN) {
                helperWakeKey = code;
                Runnable w = wake;
                wake = null;
                w.run();
            }
            return true;
        }
        if (helperWakeKey == KeyEvent.KEYCODE_UNKNOWN || code != helperWakeKey) return false;
        boolean fresh = action == KeyEvent.ACTION_DOWN && repeat == 0;
        // A new press of the same key is a key of its own (its release may never have come).
        if (action == KeyEvent.ACTION_UP || fresh) helperWakeKey = KeyEvent.KEYCODE_UNKNOWN;
        return !fresh;
    }

    /** Volume and mute change the sound over the clock and leave it showing. */
    static boolean isVolume(int code) {
        return code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    static After after(Context ctx) {
        return After.parse(prefs(ctx).getString(KEY_AFTER, null));
    }

    static Back back(Context ctx) {
        return Back.parse(prefs(ctx).getString(KEY_BACK, null));
    }

    static void saveAfter(Context ctx, After a) {
        prefs(ctx).edit().putString(KEY_AFTER, a.code).apply();
    }

    static void saveBack(Context ctx, Back b) {
        prefs(ctx).edit().putString(KEY_BACK, b.code).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
