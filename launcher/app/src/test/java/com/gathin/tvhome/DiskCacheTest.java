package com.gathin.tvhome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The disk cache's freshness, stale copies and size limit (DiskCache), with a clock the test moves. */
public class DiskCacheTest {

    /** Under the module's build folder (Gradle runs tests from launcher/app), cleaned after each test. */
    private final File dir = new File("build/test-disk-cache");
    private long now = 1_000_000_000_000L;
    private DiskCache cache;

    @Before
    public void setUp() {
        delete(dir);
        cache = new DiskCache(dir, 1000, () -> now);
    }

    @After
    public void tearDown() {
        delete(dir);
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void nothingCachedIsNull() {
        assertNull(cache.read("/movie/1", 1000));
    }

    @Test
    public void freshUntilTheTimeToLiveRunsOut() {
        cache.write("/movie/1", bytes("{\"id\":1}"));
        DiskCache.Entry e = cache.read("/movie/1", 60_000);
        assertTrue(e.fresh);
        assertEquals("{\"id\":1}", e.text());
        now += 59_999;
        assertTrue(cache.read("/movie/1", 60_000).fresh);
        now += 1;
        DiskCache.Entry stale = cache.read("/movie/1", 60_000);
        // Stale, but still there for when the network fails.
        assertFalse(stale.fresh);
        assertArrayEquals(bytes("{\"id\":1}"), stale.bytes);
    }

    @Test
    public void aWriteReplacesAndRefreshes() {
        cache.write("k", bytes("old"));
        now += 10_000;
        cache.write("k", bytes("new"));
        DiskCache.Entry e = cache.read("k", 5_000);
        assertEquals("new", e.text());
        assertTrue(e.fresh);
    }

    @Test
    public void keysAreSafeFileNames() {
        File f = cache.file("/search/multi?query=a/../../b&page=1");
        assertEquals(dir, f.getParentFile());
        assertEquals(40, f.getName().length());
        assertEquals(DiskCache.sha1("x"), DiskCache.sha1("x"));
    }

    @Test
    public void pruneDropsTheOldestOverTheLimit() {
        for (int i = 0; i < 5; i++) {
            cache.write("k" + i, new byte[200]);
            now += 1_000;
        }
        assertEquals(1000, cache.size());
        cache.write("k5", new byte[200]);
        cache.prune();
        // Down to three quarters of 1000: the oldest go first.
        assertTrue(cache.size() <= 750);
        assertNull(cache.read("k0", Long.MAX_VALUE));
        assertNull(cache.read("k1", Long.MAX_VALUE));
        assertTrue(cache.read("k5", Long.MAX_VALUE).fresh);
    }

    @Test
    public void pruneDropsWhatIsLongPastAnyTimeToLive() {
        cache.write("old", bytes("x"));
        now += DiskCache.MAX_AGE + 1;
        cache.write("new", bytes("y"));
        cache.prune();
        assertNull(cache.read("old", Long.MAX_VALUE));
        assertEquals("y", cache.read("new", 1).text());
    }
}
