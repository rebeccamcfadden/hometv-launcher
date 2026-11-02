package com.hometv.launcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** All Apps' TV Utilities folder: the rule on every brand, hand moves, and how they are saved (F35 b). */
public class TvUtilitiesTest {

    private static Set<String> watch() {
        List<String> providers = new ArrayList<>();
        for (WatchProviders.Entry e : new WatchProviders().all()) providers.addAll(e.packages);
        return TvUtilities.watchApps(providers);
    }

    private static boolean in(String pkg, boolean system) {
        return TvUtilities.byRule(pkg, system, false, watch());
    }

    @Test
    public void appsTheTvCameWithGoInOnEveryBrand() {
        // TCL (her TV), Sony, Philips, Hisense, CVTE and Android's own: each on the system image.
        for (String pkg : new String[]{"com.tcl.settings", "com.tcl.sourcemananger", "com.tcl.ui_mediaCenter",
                "com.sony.dtv.tvx", "org.droidtv.channels", "com.jamdeo.tv.vod", "com.cvte.tv.setting",
                "com.android.tv.settings", "com.google.android.tv"}) {
            assertTrue(pkg, in(pkg, true));
        }
    }

    @Test
    public void aTvMakersAppGoesInWhereverItWasInstalled() {
        assertTrue(in("com.tcl.tvweishi", false));
        assertTrue(in("com.xiaomi.mitv.tvmanager", false));
        assertTrue(in("com.mediatek.wwtv.tvcenter", false));
        // A prefix is a whole package segment: com.tclx is someone else.
        assertFalse(in("com.tclx.player", false));
    }

    @Test
    public void appsTheHouseholdInstalledStayOut() {
        for (String pkg : new String[]{"org.xbmc.kodi", "com.valvesoftware.steamlink", "tv.twitch.android.app",
                "com.nordvpn.android"}) {
            assertFalse(pkg, in(pkg, false));
        }
    }

    @Test
    public void watchAppsStayOutEvenWhenTheTvCameWithThem() {
        for (String pkg : new String[]{"com.netflix.ninja", "com.google.android.youtube.tv",
                "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "tv.wuaki.apptv", "com.android.vending",
                "com.google.android.videos", "com.google.android.youtube.tvmusic", "com.spotify.tv.android",
                "com.plexapp.android", "se.svt.android.svtplay", "com.tubitv"}) {
            assertFalse(pkg, in(pkg, true));
        }
        // A streaming app the launcher doesn't know, that came with the TV, goes in: hold OK moves it out.
        assertTrue(in("com.eros.now", true));
    }

    @Test
    public void everyProviderAndHomesSeedAreWatchApps() {
        Set<String> w = watch();
        for (WatchProviders.Entry e : new WatchProviders().all()) assertTrue(e.key, w.containsAll(e.packages));
        assertTrue(w.containsAll(AppRow.STREAMING));
    }

    @Test
    public void anAppOnHomesRowStaysOut() {
        assertFalse(TvUtilities.byRule("com.tcl.tv", true, true, watch()));
    }

    @Test
    public void aHandMoveWins() {
        Set<String> w = watch();
        assertFalse(TvUtilities.inFolder("com.eros.now", true, false, false, w));
        assertTrue(TvUtilities.inFolder("org.xbmc.kodi", false, false, true, w));
        assertTrue(TvUtilities.inFolder("com.tcl.tv", true, true, true, w));
        assertTrue(TvUtilities.inFolder("com.tcl.tv", true, false, null, w));
    }

    @Test
    public void aMoveIsKeptOnlyAgainstTheRule() {
        Map<String, Boolean> none = Collections.emptyMap();
        Map<String, Boolean> out = TvUtilities.move(none, "com.eros.now", false, true);
        assertEquals(Collections.singletonMap("com.eros.now", false), out);
        assertEquals(none, TvUtilities.move(out, "com.eros.now", true, true));
        assertEquals(none, TvUtilities.move(none, "org.xbmc.kodi", false, false));
    }

    @Test
    public void movesRoundTripAndSkipWhatTheyCantRead() {
        Map<String, Boolean> moves = new TreeMap<>();
        moves.put("com.eros.now", false);
        moves.put("org.xbmc.kodi", true);
        assertEquals("-com.eros.now\n+org.xbmc.kodi", TvUtilities.encode(moves));
        assertEquals(moves, TvUtilities.decode(TvUtilities.encode(moves)));
        assertEquals(moves, TvUtilities.decode("\n-com.eros.now\n?junk\n+\n +org.xbmc.kodi \n"));
        assertTrue(TvUtilities.decode(null).isEmpty());
        assertEquals("", TvUtilities.encode(Collections.emptyMap()));
    }
}
