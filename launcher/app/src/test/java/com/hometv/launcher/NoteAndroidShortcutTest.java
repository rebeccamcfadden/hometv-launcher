package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * The HTTP Shortcuts template the setup page imports on Android (res/raw/notes_android_shortcut.json, F45): what the
 * app's importer requires (HTTP-Shortcuts' ImportExportBase.validate at develop 1058818d), that it holds no address
 * and no code (the page's Pair link hands those to the app on the phone), and that the page and the template agree on
 * the names that join them.
 */
public class NoteAndroidShortcutTest {
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_]{1,30}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]{1,30})\\}\\}");
    private static final Pattern GET_VARIABLE = Pattern.compile("(?:get|set)Variable\\('([A-Za-z0-9_]+)'");

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String template() throws IOException {
        return read("src/main/res/raw/notes_android_shortcut.json");
    }

    private static JSONArray shortcuts(JSONObject t) throws Exception {
        return t.getJSONArray("categories").getJSONObject(0).getJSONArray("shortcuts");
    }

    private static JSONObject shortcut(JSONObject t, String name) throws Exception {
        JSONArray s = shortcuts(t);
        for (int i = 0; i < s.length(); i++) if (name.equals(s.getJSONObject(i).getString("name"))) return s.getJSONObject(i);
        throw new AssertionError("no shortcut " + name);
    }

    @Test
    public void theImporterWouldTakeIt() throws Exception {
        JSONObject t = new JSONObject(template());
        assertTrue("a version above 0", t.getLong("version") > 0);
        // The app refuses a file whose compatibility version is newer than its own (90 since 2025's releases).
        assertTrue(t.getLong("compatibilityVersion") <= 90);
        Set<String> ids = new HashSet<>();
        JSONArray categories = t.getJSONArray("categories");
        for (int c = 0; c < categories.length(); c++) {
            JSONObject cat = categories.getJSONObject(c);
            assertTrue(UUID.matcher(cat.getString("id")).matches());
            assertFalse(cat.getString("name").trim().isEmpty());
            assertTrue(ids.add(cat.getString("id")));
            JSONArray s = cat.getJSONArray("shortcuts");
            for (int i = 0; i < s.length(); i++) {
                JSONObject sc = s.getJSONObject(i);
                assertTrue(UUID.matcher(sc.getString("id")).matches());
                assertTrue(ids.add(sc.getString("id")));
                assertFalse(sc.getString("name").trim().isEmpty());
                int timeout = sc.optInt("timeout", 0);
                assertTrue(timeout == 0 || timeout >= 250);
            }
        }
        Set<String> keys = new HashSet<>();
        JSONArray vars = t.getJSONArray("variables");
        for (int i = 0; i < vars.length(); i++) {
            JSONObject v = vars.getJSONObject(i);
            assertTrue(UUID.matcher(v.getString("id")).matches());
            assertTrue(ids.add(v.getString("id")));
            assertTrue(KEY.matcher(v.getString("key")).matches());
            assertTrue("duplicate key " + v.getString("key"), keys.add(v.getString("key")));
        }
    }

    @Test
    public void itHoldsNoAddressAndNoCode() throws Exception {
        String raw = template();
        assertFalse(raw.contains("Bearer"));
        assertFalse(raw.contains("http://"));
        assertFalse(raw.contains("https://"));
        assertFalse(raw.contains(String.valueOf(NoteInbox.DEFAULT_PORT)));
        JSONArray vars = new JSONObject(raw).getJSONArray("variables");
        for (int i = 0; i < vars.length(); i++) {
            JSONObject v = vars.getJSONObject(i);
            assertEquals(v.getString("key") + " starts empty", "", v.optString("value", ""));
            if (!v.optBoolean("isShareText")) {
                // An export from the phone leaves out what Pair stored there.
                assertTrue(v.getString("key"), v.optBoolean("isExcludeValueFromExport"));
            }
        }
    }

    @Test
    public void everyVariableTheShortcutsUseIsDefined() throws Exception {
        JSONObject t = new JSONObject(template());
        Set<String> keys = new HashSet<>();
        JSONArray vars = t.getJSONArray("variables");
        for (int i = 0; i < vars.length(); i++) keys.add(vars.getJSONObject(i).getString("key"));
        Matcher m = PLACEHOLDER.matcher(shortcuts(t).toString());
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue("{{" + m.group(1) + "}} is not a variable", keys.contains(m.group(1)));
        }
        assertTrue(n >= 4);
        Matcher g = GET_VARIABLE.matcher(shortcuts(t).toString());
        while (g.find()) assertTrue(g.group(1) + " is not a variable", keys.contains(g.group(1)));
    }

    @Test
    public void sendPostsTheSharedTextAndPairStaysOutOfTheShareSheet() throws Exception {
        JSONObject t = new JSONObject(template());
        Map<String, JSONObject> byKey = new HashMap<>();
        JSONArray vars = t.getJSONArray("variables");
        for (int i = 0; i < vars.length(); i++) byKey.put(vars.getJSONObject(i).getString("key"), vars.getJSONObject(i));

        JSONObject send = shortcut(t, "Send to TV");
        assertEquals("app", send.getString("executionType"));
        assertEquals("POST", send.getString("method"));
        assertEquals("{{hometv_url}}?list={{hometv_list}}", send.getString("url"));
        assertTrue("the list name goes into the address encoded", byKey.get("hometv_list").getBoolean("urlEncode"));
        assertEquals("Authorization", send.getJSONArray("headers").getJSONObject(0).getString("key"));
        assertEquals("{{hometv_auth}}", send.getJSONArray("headers").getJSONObject(0).getString("value"));
        assertEquals("custom_text", send.getString("requestBodyType"));
        assertEquals("text/plain", send.getString("contentType"));
        assertEquals("{{hometv_note}}", send.getString("bodyContent"));
        // Using a share variable makes it a share target (HTTP Shortcuts' ShareUtil); launcherShortcut publishes it as a
        // Direct Share target, so the share sheet shows Send to TV itself and not only the app (LauncherShortcutUpdater).
        assertTrue(send.getBoolean("launcherShortcut"));
        assertTrue(byKey.get("hometv_note").getBoolean("isShareText"));

        JSONObject pair = shortcut(t, "Pair with TV");
        assertEquals("scripting", pair.getString("executionType"));
        assertTrue(pair.getBoolean("hidden"));
        // The importer publishes every shortcut as an app shortcut unless told not to (ImportExportDefaultsProvider).
        assertFalse(pair.getBoolean("launcherShortcut"));
        // A scripting shortcut is a file-share target unless it says not.
        assertTrue(pair.getBoolean("excludeFromFileSharing"));
        assertFalse(pair.toString().contains("{{hometv_note}}"));
        for (String k : new String[] {"hometv_url", "hometv_auth", "hometv_list"}) {
            assertTrue(k, pair.getString("codeOnPrepare").contains("setVariable('" + k + "'"));
        }
        // HTTP Shortcuts hands a deep link's value to getVariable already URL-encoded when the variable has urlEncode,
        // and encodes the stored value again on every send: Pair stores the list decoded, or "Mary Ann" would go out
        // as Mary%2520Ann.
        String code = pair.getString("codeOnPrepare");
        assertTrue(code.indexOf("decodeURIComponent(l)") >= 0);
        assertTrue(code.indexOf("decodeURIComponent(l)") < code.indexOf("setVariable('hometv_list', l)"));
    }

    /** HTTP Shortcuts' cURL import doesn't understand the shell's '\'' escape, so the page's URL carries no apostrophe. */
    @Test
    public void theCurlCommandNeedsNoShellEscapes() throws Exception {
        String page = read("src/main/res/raw/notes_setup.html");
        assertTrue(page.contains("encodeURIComponent(name).replace(/'/g, '%27')"));
        assertFalse(page.contains("'\\''"));
    }

    /**
     * The import turns the response window off only for a silent command (CurlParser's -s; responseSuccessOutput and
     * responseFailureOutput NONE), which is step 6 of the by-hand steps; the page goes on at step 7 after it.
     */
    @Test
    public void theCurlCommandIsSilent() throws Exception {
        String page = read("src/main/res/raw/notes_setup.html");
        assertTrue(page.contains("'curl -s -X POST '"));
        assertTrue(page.contains("then go on at step 7"));
    }

    /** The page's links name the template's path, the Pair shortcut and the variables it fills. */
    @Test
    public void theSetupPageAndTheTemplateAgree() throws Exception {
        String page = read("src/main/res/raw/notes_setup.html");
        assertTrue(page.contains("'" + NoteInbox.ANDROID_SHORTCUT_PATH + "'"));
        assertTrue(page.contains("http-shortcuts://import?url="));
        // Pair runs the hidden shortcut by its id, which the template keeps fixed.
        String pairId = shortcut(new JSONObject(template()), "Pair with TV").getString("id");
        assertTrue(page.contains("PAIR_ID = '" + pairId + "'"));
        assertTrue(page.contains("'http-shortcuts://deep-link/' + PAIR_ID"));
        for (String k : new String[] {"hometv_url", "hometv_auth", "hometv_list"}) {
            assertTrue(k, page.contains(k + "="));
        }
        assertTrue(page.contains("https://play.google.com/store/apps/details?id=ch.rmy.android.http_shortcuts"));
        // The pairing link is built on the phone from the #fragment; the code is never written into the page.
        assertFalse(page.contains("{{code}}"));
    }
}
