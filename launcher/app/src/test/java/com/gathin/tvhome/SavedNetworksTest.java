package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Wi-Fi dropdown's saved networks: which are offered, in which order, and on which Android. */
public class SavedNetworksTest {

    private static SavedNetworks.Net net(int id, String ssid) {
        return new SavedNetworks.Net(id, ssid, false);
    }

    private static List<String> names(List<SavedNetworks.Net> nets) {
        List<String> out = new ArrayList<>();
        for (SavedNetworks.Net n : nets) out.add(n.ssid);
        return out;
    }

    @Test
    public void onlyUpToAndroid9() {
        assertTrue(SavedNetworks.supported(26));
        assertTrue(SavedNetworks.supported(28));
        assertFalse(SavedNetworks.supported(29));
        assertFalse(SavedNetworks.supported(34));
    }

    @Test
    public void theCurrentNetworkIsInTheHeaderNotTheList() {
        List<SavedNetworks.Net> saved = Arrays.asList(net(0, "Home"), net(1, "guest"), net(2, "Attic"));
        assertEquals(Arrays.asList("Attic", "guest"), names(SavedNetworks.others(saved, 0, "Home")));
        // Android 9 hides the name from an app without location access, but not the id.
        assertEquals(Arrays.asList("Attic", "guest"), names(SavedNetworks.others(saved, 0, null)));
        // An id the list doesn't know (a network being joined) still drops the name.
        assertEquals(Arrays.asList("Attic", "guest"), names(SavedNetworks.others(saved, 7, "Home")));
    }

    @Test
    public void offlineOffersEverySavedNetwork() {
        List<SavedNetworks.Net> saved = Arrays.asList(net(0, "Home"), net(1, "Guest"));
        assertEquals(Arrays.asList("Guest", "Home"), names(SavedNetworks.others(saved, -1, null)));
    }

    @Test
    public void eachNameOnceAndNoNameless() {
        List<SavedNetworks.Net> saved = Arrays.asList(net(0, "Home"), net(3, "Home"), net(1, "Cabin"),
                net(4, "Cabin"), net(5, " "), net(6, null), null);
        // Another entry under the current network's name is the same network to the person.
        assertEquals(Collections.singletonList("Cabin"), names(SavedNetworks.others(saved, 3, null)));
        List<SavedNetworks.Net> offline = SavedNetworks.others(saved, -1, null);
        assertEquals(Arrays.asList("Cabin", "Home"), names(offline));
        assertEquals(1, offline.get(0).id);
    }

    @Test
    public void sortedByNameIgnoringCase() {
        List<SavedNetworks.Net> saved = Arrays.asList(net(0, "b"), net(1, "C"), net(2, "a"), net(3, "B"));
        assertEquals(Arrays.asList("a", "B", "b", "C"), names(SavedNetworks.others(saved, -1, null)));
    }

    @Test
    public void nameOfTheConnectedId() {
        List<SavedNetworks.Net> saved = Arrays.asList(net(0, "Home"), net(1, "Guest"));
        assertEquals("Guest", SavedNetworks.nameOf(saved, 1));
        assertNull(SavedNetworks.nameOf(saved, -1));
        assertNull(SavedNetworks.nameOf(saved, 9));
        assertNull(SavedNetworks.nameOf(Collections.emptyList(), 0));
    }
}
