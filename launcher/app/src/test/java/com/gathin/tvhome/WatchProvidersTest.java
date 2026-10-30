package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Swedish provider table: TMDB ids to the launcher's services and their TV apps. */
public class WatchProvidersTest {

    private final WatchProviders table = new WatchProviders();

    @Test
    public void theBigSwedishServicesMap() {
        assertEquals("netflix", table.byTmdbId(8).key);
        assertEquals("netflix", table.byTmdbId(175).key);
        assertEquals("prime", table.byTmdbId(119).key);
        assertEquals("amazonvideo", table.byTmdbId(10).key);
        assertEquals("disney", table.byTmdbId(337).key);
        assertEquals("max", table.byTmdbId(1899).key);
        assertEquals("appletvplus", table.byTmdbId(350).key);
        assertEquals("appletv", table.byTmdbId(2).key);
        assertEquals("viaplay", table.byTmdbId(76).key);
        assertEquals("skyshowtime", table.byTmdbId(1773).key);
        assertEquals("svtplay", table.byTmdbId(493).key);
        assertEquals("tv4play", table.byTmdbId(1944).key);
        assertEquals("sfanytime", table.byTmdbId(426).key);
        assertEquals("rakuten", table.byTmdbId(35).key);
        assertEquals("tele2play", table.byTmdbId(497).key);
    }

    @Test
    public void closedServicesAndChannelsAreLeftOut() {
        assertNull(table.byTmdbId(423));   // Blockbuster, closed
        assertNull(table.byTmdbId(1825));  // HBO Max Amazon Channel
        assertNull(table.byTmdbId(2243));  // Apple TV Amazon Channel
        assertNull(table.byTmdbId(188));   // YouTube Premium: not "included" with plain YouTube
    }

    @Test
    public void subscriptionsStoresAndFree() {
        WatchProviders.Entry viaplay = table.byKey("viaplay");
        assertTrue(viaplay.subscription && viaplay.store && !viaplay.free);
        WatchProviders.Entry svt = table.byKey("svtplay");
        assertTrue(svt.free && !svt.subscription && !svt.store);
        WatchProviders.Entry sf = table.byKey("sfanytime");
        assertTrue(sf.store && !sf.subscription);
        assertTrue(table.byKey("amazonvideo").store);
        assertFalse(table.byKey("prime").store);
    }

    @Test
    public void packagesAreTheAndroidTvApps() {
        assertEquals(Arrays.asList("com.viaplay.android"), table.byKey("viaplay").packages);
        assertEquals(Arrays.asList("se.svt.android.svtplay"), table.byKey("svtplay").packages);
        assertEquals(Arrays.asList("se.tv4.tv4playtab"), table.byKey("tv4play").packages);
        assertEquals(Arrays.asList("com.skyshowtime.skyshowtime.google"), table.byKey("skyshowtime").packages);
        assertEquals(Arrays.asList("com.sfanytime.tv"), table.byKey("sfanytime").packages);
        assertEquals(Arrays.asList("tv.wuaki.apptv"), table.byKey("rakuten").packages);
        assertEquals(Arrays.asList("com.tivo.android.comhem"), table.byKey("tele2play").packages);
        assertEquals(Arrays.asList("com.teliasonera.telia.playplus"), table.byKey("teliaplay").packages);
        // Amazon's store opens in the Prime Video app.
        assertEquals(table.byKey("prime").packages, table.byKey("amazonvideo").packages);
    }

    @Test
    public void existingServicesAgreeWithServiceTable() {
        ServiceTable services = new ServiceTable();
        for (ServiceId s : ServiceId.values()) {
            WatchProviders.Entry e = table.of(s);
            // Every service has its entry, so ServiceTable can be made from this table.
            assertNotNull(s.id, e);
            assertEquals(s.id, e.key);
            assertEquals(s, e.service);
            assertEquals(s.id, services.packages(s), e.packages);
            assertEquals(s.id, services.isStore(s), e.store && !e.subscription);
        }
    }

    @Test
    public void keysAndIdsAreUnique() {
        Set<String> keys = new HashSet<>();
        Set<Integer> ids = new HashSet<>();
        for (WatchProviders.Entry e : table.all()) {
            assertEquals(e.key.toLowerCase(Locale.ROOT), e.key);
            assertTrue(e.key, keys.add(e.key));
            for (int id : e.tmdbIds) assertTrue(e.key + " " + id, ids.add(id));
            assertFalse(e.key, e.packages.isEmpty());
            assertTrue(e.key, e.subscription || e.free || e.store);
            assertEquals(e.key, ServiceId.parse(e.key), e.service);
        }
    }

    @Test
    public void storesByThePriceSourcesNames() {
        assertEquals("appletv", table.storeForName("iTunes").key);
        assertEquals("appletv", table.storeForName("apple").key);
        assertEquals("amazonvideo", table.storeForName("Amazon").key);
        assertEquals("amazonvideo", table.storeForName("prime").key);
        assertEquals("googletv", table.storeForName("Google Play Movies & TV").key);
        assertEquals("sfanytime", table.storeForName("SF  Anytime").key);
        assertEquals("viaplay", table.storeForName("Viaplay Store").key);
        assertEquals("rakuten", table.storeForName("Rakuten TV").key);
        assertEquals("teliaplay", table.storeForName("Telia Play").key);
        assertNull(table.storeForName("Netflix"));
        assertNull(table.storeForName("Blockbuster"));
        assertNull(table.storeForName(null));
    }

    @Test
    public void regionCurrencies() {
        assertEquals("SEK", WatchProviders.currency("SE"));
        assertEquals("SEK", WatchProviders.currency("se"));
        assertEquals("USD", WatchProviders.currency("US"));
        assertNull(WatchProviders.currency("XX"));
    }
}
