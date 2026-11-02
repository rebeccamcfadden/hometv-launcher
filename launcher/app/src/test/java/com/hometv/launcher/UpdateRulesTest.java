package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class UpdateRulesTest {

    private static final String SHA = rep("aa");
    private static final String CERT = rep("bb");
    private static final long DAY = UpdateRules.CHECK_EVERY_MS;

    private static String rep(String two) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) sb.append(two);
        return sb.toString();
    }

    private static String json(int code, String name, String apk, String sha, String cert) {
        return "{\"versionCode\": " + code + ", \"versionName\": \"" + name + "\", \"apk\": \"" + apk + "\", \"sha256\": \""
                + sha + "\", \"certSha256\": \"" + cert + "\", \"minSdk\": 26, \"commit\": \"abc\"}";
    }

    private static UpdateRules.Release release() {
        return UpdateRules.parse(json(9, "2.8", "hometv-launcher-v2.8.apk", SHA, CERT));
    }

    @Test
    public void readsWhatReleasePs1Writes() {
        UpdateRules.Release r = release();
        assertNotNull(r);
        assertEquals(9, r.versionCode);
        assertEquals("v2.8", r.tag());
        assertEquals(26, r.minSdk);
        // The APK comes from the release's own tag, never "latest", so it can't change between the two reads.
        assertEquals("https://github.com/rebeccamcfadden/hometv-launcher/releases/download/v2.8/hometv-launcher-v2.8.apk", r.apkUrl(""));
        assertEquals("http://10.0.2.2:8780/feed/hometv-launcher-v2.8.apk", r.apkUrl("http://10.0.2.2:8780/feed/"));
        assertEquals(UpdateRules.LATEST_VERSION_JSON, UpdateRules.versionJsonUrl(""));
        assertEquals("https://example.org/f/version.json", UpdateRules.versionJsonUrl("https://example.org/f"));
    }

    @Test
    public void refusesAVersionJsonThatIsNotRelease() {
        assertNull(UpdateRules.parse(null));
        assertNull(UpdateRules.parse("not json"));
        assertNull(UpdateRules.parse("{}"));
        assertNull(UpdateRules.parse(json(0, "2.8", "hometv-launcher-v2.8.apk", SHA, CERT)));
        // An APK name that isn't this version's, or that leaves the release's folder.
        assertNull(UpdateRules.parse(json(9, "2.8", "hometv-launcher-v2.9.apk", SHA, CERT)));
        assertNull(UpdateRules.parse(json(9, "2.8", "../hometv-launcher-v2.8.apk", SHA, CERT)));
        assertNull(UpdateRules.parse(json(9, "2.8/../x", "hometv-launcher-v2.8.apk", SHA, CERT)));
        assertNull(UpdateRules.parse(json(9, "2.8", "hometv-launcher-v2.8.apk", "abc", CERT)));
        assertNull(UpdateRules.parse(json(9, "2.8", "hometv-launcher-v2.8.apk", SHA, rep("zz"))));
    }

    @Test
    public void uppercaseDigestsAreRead() {
        UpdateRules.Release r = UpdateRules.parse(json(9, "2.8", "hometv-launcher-v2.8.apk", SHA.toUpperCase(), CERT.toUpperCase()));
        assertNotNull(r);
        assertEquals(SHA, r.sha256);
    }

    @Test
    public void onlyAHigherVersionCodeOnASupportedSdkIsAnUpdate() {
        UpdateRules.Release r = release();
        assertTrue(UpdateRules.isUpdate(r, 8, 28));
        assertFalse(UpdateRules.isUpdate(r, 9, 28));
        assertFalse(UpdateRules.isUpdate(r, 10, 28));
        assertFalse(UpdateRules.isUpdate(r, 8, 25));
        assertFalse(UpdateRules.isUpdate(null, 8, 28));
    }

    @Test
    public void checksOnceADayInAReleaseBuildWithTheCheckOn() {
        long now = 1_800_000_000_000L;
        assertTrue(UpdateRules.checkDue(true, true, 0, now));
        assertTrue(UpdateRules.checkDue(true, true, now - DAY, now));
        assertFalse(UpdateRules.checkDue(true, true, now - DAY + 1, now));
        assertFalse(UpdateRules.checkDue(false, true, 0, now));
        assertFalse(UpdateRules.checkDue(true, false, 0, now));
        // A clock set back doesn't hold the check off.
        assertTrue(UpdateRules.checkDue(true, true, now + DAY * 30, now));
    }

    @Test
    public void takesOnlyTheReleasesFileSignedByTheInstalledKey() {
        UpdateRules.Release r = release();
        List<String> one = Collections.singletonList(CERT);
        assertTrue(UpdateRules.acceptable(r, SHA, one, one));
        assertTrue(UpdateRules.acceptable(r, SHA.toUpperCase(), one, one));
        assertFalse(UpdateRules.acceptable(r, rep("cc"), one, one));
        List<String> other = Collections.singletonList(rep("dd"));
        assertFalse("another signer", UpdateRules.acceptable(r, SHA, other, one));
        assertFalse("installed with another key (a debug build)", UpdateRules.acceptable(r, SHA, one, other));
        assertFalse("two signers", UpdateRules.acceptable(r, SHA, Arrays.asList(CERT, CERT), one));
        assertFalse("no signer", UpdateRules.acceptable(r, SHA, Collections.emptyList(), one));
        assertFalse(UpdateRules.acceptable(null, SHA, one, one));
    }

    @Test
    public void sha256MatchesAKnownValue() throws Exception {
        String abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertEquals(abc, UpdateRules.sha256Hex("abc".getBytes("US-ASCII")));
        assertEquals(abc, UpdateRules.sha256Hex(new ByteArrayInputStream("abc".getBytes("US-ASCII"))));
    }

    @Test
    public void aboutSaysWhereTheUpdateStands() {
        // enabled, checkOn, downloading, available, lastFailed, canInstall, grantScreen
        assertEquals(UpdateRules.Status.OWN, UpdateRules.status(false, true, false, true, false, true, true));
        assertEquals(UpdateRules.Status.OFF, UpdateRules.status(true, false, false, true, false, true, true));
        assertEquals(UpdateRules.Status.CURRENT, UpdateRules.status(true, true, false, false, false, true, true));
        assertEquals(UpdateRules.Status.FAILED, UpdateRules.status(true, true, false, false, true, true, true));
        assertEquals(UpdateRules.Status.AVAILABLE, UpdateRules.status(true, true, false, true, false, true, true));
        assertEquals(UpdateRules.Status.DOWNLOADING, UpdateRules.status(true, true, true, true, false, true, true));
        assertEquals(UpdateRules.Status.NO_GRANT, UpdateRules.status(true, true, false, true, false, false, true));
        assertEquals(UpdateRules.Status.NO_SCREEN, UpdateRules.status(true, true, false, true, false, false, false));
    }

    @Test
    public void homeNoticesEachVersionOnce() {
        assertTrue(UpdateRules.notice(UpdateRules.Status.AVAILABLE, "2.8", ""));
        assertTrue(UpdateRules.notice(UpdateRules.Status.NO_GRANT, "2.8", "2.7"));
        assertFalse(UpdateRules.notice(UpdateRules.Status.AVAILABLE, "2.8", "2.8"));
        assertFalse(UpdateRules.notice(UpdateRules.Status.DOWNLOADING, "2.8", ""));
        assertFalse(UpdateRules.notice(UpdateRules.Status.CURRENT, "2.8", ""));
        assertFalse(UpdateRules.notice(UpdateRules.Status.AVAILABLE, null, ""));
    }

    @Test
    public void aTestFeedIsHttpsOrThisMachineOnly() {
        assertTrue(UpdateRules.feedAllowed("https://example.org/feed"));
        assertTrue(UpdateRules.feedAllowed("http://10.0.2.2:8780"));
        assertTrue(UpdateRules.feedAllowed("http://127.0.0.1:8780/feed/"));
        assertFalse(UpdateRules.feedAllowed("http://192.168.1.50:8780"));
        assertFalse(UpdateRules.feedAllowed("http://example.org"));
        assertFalse(UpdateRules.feedAllowed("http://10.0.2.2.example.org"));
        assertFalse(UpdateRules.feedAllowed("file:///sdcard/x"));
        assertFalse(UpdateRules.feedAllowed(""));
        assertFalse(UpdateRules.feedAllowed(null));
    }
}