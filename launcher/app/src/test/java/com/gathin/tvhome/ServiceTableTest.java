package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The services table (design_preview/src/data/services.ts) and the rule for which ones are subscribed. */
public class ServiceTableTest {

    private final ServiceTable table = new ServiceTable();
    private static final List<String> AUTO = Collections.emptyList();

    private static Set<String> installed(String... packages) {
        return new HashSet<>(Arrays.asList(packages));
    }

    @Test
    public void packagesAreTheTvApps() {
        assertEquals(Collections.singletonList("com.netflix.ninja"), table.packages(ServiceId.NETFLIX));
        assertEquals(Collections.singletonList("com.amazon.amazonvideo.livingroom"), table.packages(ServiceId.PRIME));
        assertEquals(Collections.singletonList("com.disney.disneyplus"), table.packages(ServiceId.DISNEY));
        assertEquals(Arrays.asList("com.wbd.stream", "com.hbo.hbonow"), table.packages(ServiceId.MAX));
        assertEquals(Collections.singletonList("com.apple.atve.androidtv.appletv"), table.packages(ServiceId.APPLETVPLUS));
        assertEquals(Collections.singletonList("com.apple.atve.androidtv.appletv"), table.packages(ServiceId.APPLETV));
        assertEquals(Collections.singletonList("com.hulu.livingroomplus"), table.packages(ServiceId.HULU));
        assertEquals(Collections.singletonList("com.google.android.youtube.tv"), table.packages(ServiceId.YOUTUBE));
        assertEquals(Collections.singletonList("com.google.android.videos"), table.packages(ServiceId.GOOGLETV));
    }

    @Test
    public void namesAndStoresAreThePrototypes() {
        assertEquals("Prime Video", table.name(ServiceId.PRIME));
        assertEquals("Apple TV+", table.name(ServiceId.APPLETVPLUS));
        assertEquals("Apple TV", table.name(ServiceId.APPLETV));
        assertEquals("Google TV", table.name(ServiceId.GOOGLETV));
        assertEquals("Disney+", table.name(ServiceId.DISNEY));
        Set<ServiceId> stores = EnumSet.noneOf(ServiceId.class);
        for (ServiceId s : ServiceId.values()) if (table.isStore(s)) stores.add(s);
        assertEquals(EnumSet.of(ServiceId.YOUTUBE, ServiceId.APPLETV, ServiceId.GOOGLETV), stores);
    }

    @Test
    public void nothingInstalledMeansNothingSubscribed() {
        assertTrue(table.subscribed(installed(), AUTO).isEmpty());
        assertTrue(table.subscribed(installed(), null).isEmpty());
    }

    @Test
    public void installedSubscriptionAppsAndYouTubeCount() {
        assertEquals(EnumSet.of(ServiceId.NETFLIX, ServiceId.YOUTUBE),
                table.subscribed(installed("com.netflix.ninja", "com.google.android.youtube.tv", "com.spotify.tv.android"), AUTO));
        // The Apple TV app is both Apple TV+ (a subscription) and the Apple TV store (never "included").
        assertEquals(EnumSet.of(ServiceId.APPLETVPLUS), table.subscribed(installed("com.apple.atve.androidtv.appletv"), AUTO));
        // A store's app alone subscribes to nothing.
        assertTrue(table.subscribed(installed("com.google.android.videos"), AUTO).isEmpty());
        // Max under either of its packages.
        assertEquals(EnumSet.of(ServiceId.MAX), table.subscribed(installed("com.hbo.hbonow"), AUTO));
        assertTrue(table.isInstalled(ServiceId.MAX, installed("com.wbd.stream")));
        assertFalse(table.isInstalled(ServiceId.MAX, installed("com.netflix.ninja")));
    }

    @Test
    public void everyAppInstalledGivesThePrototypesSet() {
        Set<String> all = new HashSet<>();
        for (ServiceId s : ServiceId.values()) all.addAll(table.packages(s));
        assertEquals(OffersTest.PROTOTYPE, table.subscribed(all, AUTO));
    }

    @Test
    public void theOverrideWinsWhateverIsInstalled() {
        assertEquals(EnumSet.of(ServiceId.NETFLIX, ServiceId.PRIME),
                table.subscribed(installed("com.hulu.livingroomplus"), Arrays.asList("netflix", " PRIME ", "bogus")));
        // Listing a store makes its offers count too: the household's call.
        assertEquals(EnumSet.of(ServiceId.GOOGLETV), table.subscribed(installed(), Collections.singletonList("googletv")));
        // Only unknown ids: no services at all.
        assertTrue(table.subscribed(installed("com.netflix.ninja"), Collections.singletonList("none")).isEmpty());
    }

    @Test
    public void serviceIdsParse() {
        assertEquals(ServiceId.APPLETVPLUS, ServiceId.parse("appletvplus"));
        assertEquals(ServiceId.MAX, ServiceId.parse(" Max "));
        assertEquals("appletvplus", ServiceId.APPLETVPLUS.id);
        assertNull(ServiceId.parse("hbo"));
        assertNull(ServiceId.parse(null));
    }
}
