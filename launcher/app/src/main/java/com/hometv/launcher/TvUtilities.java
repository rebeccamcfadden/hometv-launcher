package com.hometv.launcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which apps go in All Apps' TV Utilities folder (F35 b, Rebecca 2026-11-02: on for every TV, no switch;
 * design_preview/src/core/tvUtilities.ts). The rule is the same on every brand, so no maker's app list is needed: an
 * app goes in when it came on the TV's system image, or comes from a TV maker's package, unless it is a watch app the
 * launcher knows. It is only about where an app came from, so an app on Home's row stays in (Rebecca, F35 Q4). Hold OK
 * moves an app in or out by hand, and that choice wins; a hand move that matches the rule is forgotten, so the rule
 * takes over again. Pure Java, tested on the JVM; HomeApps stores the moves.
 */
final class TvUtilities {
    private TvUtilities() {}

    /** Package prefixes of TV makers and their chip vendors: their apps are TV utilities wherever installed. */
    static final List<String> VENDOR_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "com.tcl.", "com.sony.", "com.philips.", "org.droidtv.", "com.tpvision.", "com.hisense.", "com.jamdeo.",
            "com.vidaa.", "com.cvte.", "com.xiaomi.", "com.mitv.", "com.mediatek.", "com.mstar.", "com.realtek.",
            "com.rtk.", "com.amlogic.", "com.droidlogic.", "com.skyworth.", "com.coocaa.", "com.vestel.",
            "com.toshiba.", "com.sharp.", "jp.co.sharp.", "com.panasonic.", "com.haier.", "com.changhong.",
            "com.konka."));

    /** Watch and shop apps that aren't TMDB providers: Google's own, and Netflix's phone build. */
    static final List<String> OTHER_WATCH_APPS = Collections.unmodifiableList(Arrays.asList(
            "com.netflix.mediaclient",
            "com.google.android.youtube.tvkids",
            "com.google.android.youtube.tvmusic",
            "com.android.vending",
            "com.google.android.play.games"));

    /** Apps that stay in All Apps even when the TV came with them: every provider's app, Home's seed, and the rest. */
    static Set<String> watchApps(Collection<String> providerPackages) {
        Set<String> out = new HashSet<>(providerPackages);
        out.addAll(AppRow.STREAMING);
        out.addAll(OTHER_WATCH_APPS);
        return Collections.unmodifiableSet(out);
    }

    /** The rule alone, before any hand move. {@code system}: on the system image, also when updated since. */
    static boolean byRule(String pkg, boolean system, Set<String> watch) {
        if (watch.contains(pkg)) return false;
        if (system) return true;
        for (String p : VENDOR_PREFIXES) if (pkg.startsWith(p)) return true;
        return false;
    }

    /** Whether the app is in the folder: a hand move ({@code moved}: true in, false out, null none) wins. */
    static boolean inFolder(String pkg, boolean system, Boolean moved, Set<String> watch) {
        return moved != null ? moved : byRule(pkg, system, watch);
    }

    /** The moves after moving {@code pkg} in or out: kept only when it goes against the rule. */
    static Map<String, Boolean> move(Map<String, Boolean> moves, String pkg, boolean in, boolean byRule) {
        Map<String, Boolean> out = new TreeMap<>(moves);
        if (in == byRule) out.remove(pkg);
        else out.put(pkg, in);
        return out;
    }

    /** One move a line, "+pkg" in or "-pkg" out; unreadable lines are skipped. */
    static String encode(Map<String, Boolean> moves) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Boolean> e : new TreeMap<>(moves).entrySet()) {
            if (b.length() > 0) b.append('\n');
            b.append(e.getValue() ? '+' : '-').append(e.getKey());
        }
        return b.toString();
    }

    static Map<String, Boolean> decode(String text) {
        Map<String, Boolean> out = new TreeMap<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.length() < 2 || (l.charAt(0) != '+' && l.charAt(0) != '-')) continue;
            out.put(l.substring(1), l.charAt(0) == '+');
        }
        return out;
    }
}
