package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The Wi-Fi dropdown's wording. */
public class NetworkWordsTest {

    @Test
    public void signalLevelsMapToWords() {
        assertEquals(NetworkWords.Signal.STRONG, NetworkWords.signal(4));
        assertEquals(NetworkWords.Signal.STRONG, NetworkWords.signal(3));
        assertEquals(NetworkWords.Signal.FAIR, NetworkWords.signal(2));
        assertEquals(NetworkWords.Signal.WEAK, NetworkWords.signal(1));
        assertEquals(NetworkWords.Signal.WEAK, NetworkWords.signal(0));
        assertEquals(NetworkWords.Signal.NONE, NetworkWords.signal(-1));
    }

    @Test
    public void aCaptivePortalIsConnectedWithoutInternet() {
        assertEquals(NetworkWords.State.OFFLINE, NetworkWords.state(false, false));
        assertEquals(NetworkWords.State.NO_INTERNET, NetworkWords.state(true, false));
        assertEquals(NetworkWords.State.ONLINE, NetworkWords.state(true, true));
    }

    @Test
    public void placeholderNamesAreDropped() {
        assertEquals("Home Wi-Fi", NetworkWords.ssid("\"Home Wi-Fi\""));
        assertNull(NetworkWords.ssid("<unknown ssid>"));
        assertNull(NetworkWords.ssid("0x"));
        assertNull(NetworkWords.ssid("\"\""));
        assertNull(NetworkWords.ssid(null));
    }

    @Test
    public void realNamesThatLookLikePlaceholdersAreKept() {
        assertEquals("unknown-network", NetworkWords.ssid("\"unknown-network\""));
        assertEquals("The Unknown", NetworkWords.ssid("\"The Unknown\""));
        assertEquals("\"quoted\" name", NetworkWords.ssid("\"\"quoted\" name\""));
        assertEquals("0x", NetworkWords.ssid("\"0x\""));
    }
}
