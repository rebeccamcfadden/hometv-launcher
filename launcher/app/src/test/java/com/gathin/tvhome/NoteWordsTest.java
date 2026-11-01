package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The import pill's words and the section's value (design_preview/README.md, Phone notes and Import pill), formatted
 * from the English strings the TV shows (strings_notes.xml).
 */
public class NoteWordsTest {
    private static NoteWords.Texts texts;

    /** strings_notes.xml from the module, the launcher or the repo root, wherever the test runs from. */
    private static File stringsFile() {
        String path = "src/main/res/values/strings_notes.xml";
        for (String base : new String[] {"", "app/", "launcher/app/"}) {
            File f = new File(base + path);
            if (f.isFile()) return f;
        }
        throw new IllegalStateException("strings_notes.xml not found from " + new File("").getAbsolutePath());
    }

    @BeforeClass
    public static void readStrings() throws Exception {
        File f = stringsFile();
        NodeList all = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).getElementsByTagName("string");
        Map<String, String> s = new HashMap<>();
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            s.put(e.getAttribute("name"), e.getTextContent().replace("\\'", "'").replace("\\\"", "\""));
        }
        texts = new NoteWords.Texts(s.get("notes_pill_importing"), s.get("notes_pill_importing_count"),
                s.get("notes_pill_added"), s.get("notes_pill_added_unmatched"), s.get("notes_pill_offline"),
                s.get("notes_pill_failed"), s.get("notes_pill_signed_out"), s.get("notes_value_on"),
                s.get("notes_value_off"), s.get("notes_value_unmatched"));
    }

    @Test
    public void importingCountsTheNewLines() {
        NoteWords.Pill p = NoteWords.Pill.importing("Rebecca", 6, 12);
        assertEquals("Importing Rebecca · 6 of 12", NoteWords.pill(p, texts));
        assertEquals(0.5f, p.fraction(), 0.001f);
        assertFalse(p.kind.warn);
    }

    @Test
    public void beforeTheLinesAreCountedItJustSaysImporting() {
        NoteWords.Pill p = NoteWords.Pill.importing("Rebecca", 0, 0);
        assertEquals("Importing Rebecca", NoteWords.pill(p, texts));
        assertEquals(0f, p.fraction(), 0f);
    }

    @Test
    public void allMatchedIsNeutralWithATick() {
        NoteWords.Pill p = NoteWords.Pill.ended("Partner", NoteImport.Status.DONE, 8, 0);
        assertEquals(NoteWords.Kind.ADDED, p.kind);
        assertFalse(p.kind.warn);
        assertEquals("Partner · 8 added", NoteWords.pill(p, texts));
    }

    @Test
    public void linesThatMatchedNothingAreAmber() {
        NoteWords.Pill p = NoteWords.Pill.ended("Rebecca", NoteImport.Status.DONE, 9, 3);
        assertTrue(p.kind.warn);
        assertEquals("Rebecca · 9 added · 3 not matched", NoteWords.pill(p, texts));
    }

    @Test
    public void problemsAreAmberAndSayWhatTheNoteWaitsFor() {
        assertEquals("Rebecca · waiting for the internet",
                NoteWords.pill(NoteWords.Pill.ended("Rebecca", NoteImport.Status.OFFLINE, 0, 0), texts));
        assertEquals("Rebecca · couldn’t reach TMDB",
                NoteWords.pill(NoteWords.Pill.ended("Rebecca", NoteImport.Status.FAILED, 0, 0), texts));
        assertEquals("Rebecca · waiting for TMDB sign-in",
                NoteWords.pill(NoteWords.Pill.ended("Rebecca", NoteImport.Status.SIGNED_OUT, 0, 0), texts));
        for (NoteImport.Status s : new NoteImport.Status[]{NoteImport.Status.OFFLINE, NoteImport.Status.FAILED,
                NoteImport.Status.SIGNED_OUT}) {
            assertTrue(s.name(), NoteWords.Pill.ended("Rebecca", s, 0, 0).kind.warn);
        }
    }

    @Test
    public void theSectionReadsOffOnOrHowManyMatchedNothing() {
        assertEquals("Off", NoteWords.value(false, 0, texts));
        assertEquals("Off", NoteWords.value(false, 3, texts));
        assertFalse(NoteWords.valueWarns(false, 3));
        assertEquals("On", NoteWords.value(true, 0, texts));
        assertFalse(NoteWords.valueWarns(true, 0));
        assertEquals("3 not matched", NoteWords.value(true, 3, texts));
        assertTrue(NoteWords.valueWarns(true, 3));
    }

    @Test
    public void theAddressAPhoneTypes() {
        assertEquals("http://192.168.1.50:8765/notes", NoteWords.address("192.168.1.50", NoteInbox.DEFAULT_PORT));
        assertNull(NoteWords.address(null, NoteInbox.DEFAULT_PORT));
        assertNull(NoteWords.address("", NoteInbox.DEFAULT_PORT));
        assertEquals("the code after the #, as typed without the dash", "http://192.168.1.50:8765/setup#K7QM4PXT",
                NoteWords.setupUrl("192.168.1.50", NoteInbox.DEFAULT_PORT, "k7qm-4pxt"));
        assertNull(NoteWords.setupUrl(null, NoteInbox.DEFAULT_PORT, "K7QM-4PXT"));
        assertNull(NoteWords.setupUrl("192.168.1.50", NoteInbox.DEFAULT_PORT, ""));
    }

    @Test
    public void onlyAnICloudShortcutLinkReachesTheSetupPage() {
        String link = "https://www.icloud.com/shortcuts/0a1b2c3d4e5f60718293a4b5c6d7e8f9";
        assertEquals(link, NoteWords.shortcutLink("  " + link + "\n"));
        assertEquals("", NoteWords.shortcutLink(null));
        assertEquals("", NoteWords.shortcutLink(""));
        assertEquals("", NoteWords.shortcutLink("https://www.icloud.com/shortcuts/"));
        assertEquals("", NoteWords.shortcutLink("http://www.icloud.com/shortcuts/0a1b"));
        assertEquals("", NoteWords.shortcutLink("https://evil.example/shortcuts/0a1b"));
        assertEquals("", NoteWords.shortcutLink("https://www.icloud.com.evil.example/shortcuts/0a1b"));
        assertEquals("nothing that leaves the attribute", "",
                NoteWords.shortcutLink("https://www.icloud.com/shortcuts/0a1b\"><script>x()</script>"));
        assertEquals("", NoteWords.shortcutLink("https://www.icloud.com/shortcuts/0a1b?x=1&y=2"));
        assertEquals("", NoteWords.shortcutLink("javascript:alert(1)"));
    }

    @Test
    public void theSetupPageGetsTheLinkOrNothing() {
        String page = "<body data-shortcut=\"{{shortcut}}\">";
        String link = "https://www.icloud.com/shortcuts/0a1b2c";
        assertEquals("<body data-shortcut=\"" + link + "\">", NoteWords.setupPage(page, link));
        assertEquals("<body data-shortcut=\"\">", NoteWords.setupPage(page, ""));
        assertEquals("<body data-shortcut=\"\">", NoteWords.setupPage(page, "https://evil.example/\""));
        assertNull(NoteWords.setupPage(null, link));
    }

    /** The page (res/raw/notes_setup.html) and the shortcut in docs/phone-notes.md agree on how a pairing looks. */
    @Test
    public void theSetupPagePairsTheShortcutByName() throws java.io.IOException {
        String page = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/main/res/raw/notes_setup.html")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(page.contains("data-shortcut=\"{{shortcut}}\""));
        assertTrue(page.contains("'shortcuts://run-shortcut?name=' + encodeURIComponent('Send to TV')"));
        assertTrue(page.contains("pair: 'cleanyourtv-pair'"));
        assertTrue("the shortcut keeps the whole header value", page.contains("auth: 'Bearer ' + code"));
    }
}
