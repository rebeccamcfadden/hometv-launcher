package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OverlayFileTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final List<String> warnings = new ArrayList<>();
    private final OverlayFile overlay = new OverlayFile(warnings::add);

    @Test
    public void aSecondReadOfTheSameFileNeitherReadsNorWrites() throws IOException {
        File file = write("config.json", "{\"dock_apps\":[\"a\"]}");
        File copy = new File(tmp.getRoot(), "copy.json");
        assertEquals("{\"dock_apps\":[\"a\"]}", overlay.read(file, copy, true));
        assertTrue(copy.isFile());
        long copied = copy.lastModified();
        assertEquals("{\"dock_apps\":[\"a\"]}", overlay.read(file, copy, true));
        assertEquals(1, overlay.reads);
        assertEquals(1, overlay.copies);
        assertEquals(copied, copy.lastModified());
    }

    @Test
    public void aChangedFileIsReadAgain() throws IOException {
        File file = write("config.json", "{\"a\":1}");
        File copy = new File(tmp.getRoot(), "copy.json");
        overlay.read(file, copy, true);
        write("config.json", "{\"a\":22}");
        assertEquals("{\"a\":22}", overlay.read(file, copy, true));
        assertEquals(2, overlay.reads);
        assertEquals("{\"a\":22}", text(copy));
    }

    @Test
    public void aFileThatWentDeletesTheCopyOnce() throws IOException {
        File file = write("config.json", "{\"a\":1}");
        File copy = new File(tmp.getRoot(), "copy.json");
        overlay.read(file, copy, true);
        assertTrue(file.delete());
        assertNull(overlay.read(file, copy, true));
        assertFalse(copy.exists());
        assertNull(overlay.read(file, copy, true));
        assertEquals(2, overlay.reads);
    }

    @Test
    public void unmountedStorageReadsTheCopy() throws IOException {
        File copy = write("copy.json", "{\"kept\":true}");
        assertEquals("{\"kept\":true}", overlay.read(new File(tmp.getRoot(), "gone.json"), copy, false));
        assertNull(overlay.read(null, new File(tmp.getRoot(), "none.json"), false));
        assertEquals("the copy is not counted as a read of the file", 0, overlay.reads);
    }

    @Test
    public void theCopyIsReadAgainOnlyWhenItChanged() throws IOException {
        File copy = write("copy.json", "{\"kept\":true}");
        File gone = new File(tmp.getRoot(), "gone.json");
        overlay.read(gone, copy, false);
        assertEquals("{\"kept\":true}", overlay.read(gone, copy, false));
        assertEquals(1, overlay.copyReads);
        write("copy.json", "{\"kept\":false}");
        assertEquals("{\"kept\":false}", overlay.read(gone, copy, false));
        assertEquals(2, overlay.copyReads);
    }

    @Test
    public void aCopyThatCouldNotBeDeletedIsTriedAgain() throws IOException {
        File copy = tmp.newFolder("copy-with-something-in-it");
        assertTrue(new File(copy, "x").createNewFile());
        File gone = new File(tmp.getRoot(), "gone.json");
        assertNull(overlay.read(gone, copy, true));
        assertEquals(1, warnings.size());
        overlay.read(gone, copy, true);
        assertEquals("a failed delete isn't remembered", 2, overlay.reads);
    }

    @Test
    public void noStorageMeansNoOverlay() {
        File copy = new File(tmp.getRoot(), "copy.json");
        assertNull(overlay.read(null, copy, true));
        assertFalse(copy.exists());
    }

    @Test
    public void badJsonAndTooLargeFilesAreIgnoredWithAWarning() throws IOException {
        File copy = new File(tmp.getRoot(), "copy.json");
        assertNull(overlay.read(write("bad.json", "{nope"), copy, true));
        assertEquals(1, warnings.size());
        StringBuilder big = new StringBuilder("{\"a\":\"");
        while (big.length() <= OverlayFile.MAX_BYTES) big.append("xxxxxxxxxx");
        big.append("\"}");
        assertNull(overlay.read(write("big.json", big.toString()), copy, true));
        assertEquals(2, warnings.size());
    }

    @Test
    public void aCopyThatCouldNotBeWrittenIsTriedAgain() throws IOException {
        File file = write("config.json", "{\"a\":1}");
        File copy = tmp.newFolder("copy-is-a-folder");
        assertEquals("{\"a\":1}", overlay.read(file, copy, true));
        assertEquals(1, warnings.size());
        overlay.read(file, copy, true);
        assertEquals("an I/O failure isn't remembered", 2, overlay.reads);
    }

    @Test
    public void badJsonIsRememberedUntilTheFileChanges() throws IOException {
        File file = write("config.json", "{nope");
        File copy = new File(tmp.getRoot(), "copy.json");
        assertNull(overlay.read(file, copy, true));
        assertNull(overlay.read(file, copy, true));
        assertEquals(1, overlay.reads);
    }

    @Test
    public void aLeadingBomIsDropped() throws IOException {
        File copy = new File(tmp.getRoot(), "copy.json");
        assertEquals("{\"a\":1}", overlay.read(write("bom.json", "\uFEFF{\"a\":1}"), copy, true));
    }

    @Test
    public void stampNamesLengthAndTime() throws IOException {
        assertEquals("absent", OverlayFile.stamp(null));
        assertEquals("absent", OverlayFile.stamp(new File(tmp.getRoot(), "none")));
        File f = write("x.json", "{}");
        assertEquals("2@" + f.lastModified(), OverlayFile.stamp(f));
    }

    private File write(String name, String text) throws IOException {
        File f = new File(tmp.getRoot(), name);
        long before = f.isFile() ? f.lastModified() : 0;
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        // Some file systems keep mtime to the second: make a rewrite visible to the stamp.
        if (before != 0 && f.lastModified() <= before) assertTrue(f.setLastModified(before + 2000));
        return f;
    }

    private static String text(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
