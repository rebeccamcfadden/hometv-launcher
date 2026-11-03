package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PhoneRemoteKeysTest {
    private final MockCatalog catalog = new MockCatalog();

    @Test public void thePagesKeysAreTheRemotesKeys() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, PhoneRemoteKeys.keyCode("up"));
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, PhoneRemoteKeys.keyCode("down"));
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, PhoneRemoteKeys.keyCode("left"));
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, PhoneRemoteKeys.keyCode("right"));
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, PhoneRemoteKeys.keyCode("ok"));
        assertEquals(KeyEvent.KEYCODE_BACK, PhoneRemoteKeys.keyCode("back"));
        // Home is the bridge's own; anything else is nothing.
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, PhoneRemoteKeys.keyCode("home"));
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, PhoneRemoteKeys.keyCode("power"));
    }

    @Test public void searchMatchesNamesStartingWithTheQueryFirst() {
        List<Title> found = PhoneRemoteKeys.matching(catalog.all(), "AR");
        assertFalse(found.isEmpty());
        boolean pastStarts = false;
        for (Title t : found) {
            boolean starts = t.name.toLowerCase(java.util.Locale.ROOT).startsWith("ar");
            assertTrue(t.name, t.name.toLowerCase(java.util.Locale.ROOT).contains("ar"));
            if (!starts) pastStarts = true;
            else assertFalse("a name starting with it after one that doesn't: " + t.name, pastStarts);
        }
        assertTrue(PhoneRemoteKeys.matching(catalog.all(), "  ").isEmpty());
    }

    @Test public void mergeKeepsTheFirstListsOrderAndDropsRepeats() {
        List<Title> all = catalog.all();
        List<Title> first = new ArrayList<>(all.subList(0, 2));
        List<Title> then = new ArrayList<>(all.subList(1, 4));
        List<Title> merged = PhoneRemoteKeys.merge(first, then);
        assertEquals(4, merged.size());
        assertEquals(all.get(0).id, merged.get(0).id);
        assertEquals(all.get(1).id, merged.get(1).id);
        assertEquals(all.get(3).id, merged.get(3).id);
        assertEquals(first, PhoneRemoteKeys.merge(first, null));
        assertEquals(Collections.emptyList(), PhoneRemoteKeys.merge(Collections.emptyList(), Collections.emptyList()));
    }
}
