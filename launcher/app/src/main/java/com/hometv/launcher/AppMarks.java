package com.hometv.launcher;

import java.util.HashMap;
import java.util.Map;

/**
 * F43, App logos › Minimal (the V2 Figma Home frames, file 7gFFvfdGdXJVLZnj18Zah4, "Streaming services" 2:741): each
 * Home row app as an off-white wordmark on the glass tile instead of its full-colour banner. The marks are typeset, not
 * artwork: the service's name the way its logo spells it, at the Figma frame's weight and size (the prototype's
 * data/services.ts has the same marks). An app the table doesn't know shows its own name. Pure Java, unit-tested
 * (AppMarksTest); nothing is bundled, so the APK grows by this table only.
 */
final class AppMarks {
    private AppMarks() {}

    /** A wordmark: its text, CSS-style weight (400, 500, 600, 700, 800) and size in design px. */
    static final class Mark {
        final String text;
        final int weight;
        final float size;

        Mark(String text, int weight, float size) {
            this.text = text;
            this.weight = weight;
            this.size = size;
        }
    }

    /** An app the table doesn't know: its name, Medium, a little smaller than a wordmark (it may be long). */
    static final int NAME_WEIGHT = 500;
    static final float NAME_SIZE = 26;

    private static final Map<String, Mark> BY_PACKAGE = new HashMap<>();

    private static void put(Mark m, String... packages) {
        for (String p : packages) BY_PACKAGE.put(p, m);
    }

    static {
        put(new Mark("NETFLIX", 800, 31), "com.netflix.ninja");
        put(new Mark("YouTube", 700, 28), "com.google.android.youtube.tv");
        put(new Mark("prime video", 700, 29), "com.amazon.amazonvideo.livingroom");
        put(new Mark("Disney+", 500, 34), "com.disney.disneyplus");
        // HBO Max is its current name (docs/how-it-works.md, Choosing the household's services; the prototype and the app's label say so too).
        put(new Mark("HBO Max", 800, 31), "com.wbd.stream", "com.hbo.hbonow");
        put(new Mark("Apple TV", 500, 29), "com.apple.atve.androidtv.appletv");
        put(new Mark("Spotify", 700, 30), "com.spotify.tv.android");
        put(new Mark("hulu", 700, 34), "com.hulu.livingroomplus");
        put(new Mark("viaplay", 700, 31), "com.viaplay.android");
        put(new Mark("SkyShowtime", 600, 27), "com.skyshowtime.skyshowtime.google");
        put(new Mark("SVT Play", 700, 29), "se.svt.android.svtplay");
        put(new Mark("TV4 Play", 700, 29), "se.tv4.tv4playtab");
        put(new Mark("plex", 700, 33), "com.plexapp.android");
        put(new Mark("pluto tv", 700, 30), "tv.pluto.android");
        put(new Mark("MUBI", 800, 31), "com.mubi");
        put(new Mark("crunchyroll", 600, 27), "com.crunchyroll.crunchyroid");
        put(new Mark("peacock", 700, 31), "com.peacocktv.peacockandroid");
        put(new Mark("Paramount+", 600, 28), "com.cbs.ott");
        put(new Mark("tubi", 800, 34), "com.tubitv");
        put(new Mark("Twitch", 700, 30), "tv.twitch.android.app");
        put(new Mark("Kodi", 700, 31), "org.xbmc.kodi");
    }

    /** The app's wordmark, or its name (trimmed; the package when it has none) in Medium. */
    static Mark of(String pkg, CharSequence label) {
        Mark m = pkg == null ? null : BY_PACKAGE.get(pkg);
        if (m != null) return m;
        String name = label == null ? "" : label.toString().trim();
        return new Mark(name.isEmpty() ? (pkg == null ? "" : pkg) : name, NAME_WEIGHT, NAME_SIZE);
    }

    /** The Figma tile's height the sizes are for (Home's row), and the smallest a long name shrinks to there. */
    static final float TILE_H = 136, MIN_SIZE = 18;

    /** A size for Home's tile, in proportion on a tile {@code tileH} tall. */
    static float scaled(float size, float tileH) {
        return tileH <= 0 ? size : size * tileH / TILE_H;
    }

    /** Roboto (or the theme's face) has Regular, Medium and Bold: 600 and over draw Bold, 500 Medium. */
    static int face(int weight) {
        return weight >= 600 ? 700 : weight >= 500 ? 500 : 400;
    }

    static final String FULL = "full", MINIMAL = "minimal";

    /**
     * Theme › App logos: the household's choice ({@code saved}, "full" or "minimal") wins, then this TV's config
     * (config.xml or a pushed config.json {@code app_logos}), else full colour. Anything else reads as full colour.
     */
    static boolean minimal(Object saved, String configured) {
        if (FULL.equals(saved) || MINIMAL.equals(saved)) return MINIMAL.equals(saved);
        return configured != null && MINIMAL.equals(configured.trim().toLowerCase(java.util.Locale.ROOT));
    }
}
