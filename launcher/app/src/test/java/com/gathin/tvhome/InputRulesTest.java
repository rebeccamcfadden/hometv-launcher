package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The Inputs menu's rules, kept from the old Home with the sets they came from. */
public class InputRulesTest {

    @Test
    public void hdmiPortsAlwaysSayHdmi() {
        assertEquals("HDMI 2", InputRules.portLabel("2", true));
        assertEquals("HDMI 2", InputRules.portLabel("HDMI 2", true));
        assertEquals("hdmi1", InputRules.portLabel("hdmi1", true));
        assertEquals("AV", InputRules.portLabel("AV", false));
    }

    @Test
    public void aGivenNameGoesBeforeThePort() {
        assertEquals("Apple TV \u00B7 HDMI 1", InputRules.named("Apple TV", "HDMI 1"));
        assertEquals("HDMI 1", InputRules.named("", "HDMI 1"));
        assertEquals("HDMI 1", InputRules.named(null, "HDMI 1"));
    }

    @Test
    public void everyPortConnectedIsNotBelieved() {
        assertTrue(InputRules.hdmiStateBogus(3, 3));
        assertFalse(InputRules.hdmiStateBogus(3, 2));
        assertFalse(InputRules.hdmiStateBogus(1, 1));
    }

    @Test
    public void hardwareDigitalTunersWin() {
        assertTrue(InputRules.betterTuner("com.tv/.TunerInputService/HW1", null));
        assertTrue(InputRules.betterTuner("com.tv/.TunerInputService/HW1", "com.airplay/.Airplay"));
        assertFalse(InputRules.betterTuner("com.airplay/.Airplay", "com.tv/.TunerInputService/HW1"));
        assertTrue(InputRules.betterTuner("com.mtk/.TunerInputService/HW2", "com.mtk/.AnalogInputService/HW1"));
        assertFalse(InputRules.betterTuner("com.mtk/.AnalogInputService/HW1", "com.mtk/.TunerInputService/HW2"));
        assertTrue(InputRules.betterTuner("com.tv/.A/HW1", "com.tv/.B/HW1"));
    }

    @Test
    public void inputSourcesMapPortsToVendorNumbers() {
        String[] map = {"HW5=23", " HW8 = - ", "HW9=x"};
        assertEquals(Integer.valueOf(23), InputRules.mappedSource("com.tv/.HdmiInputService/HW5", map));
        assertEquals(Integer.valueOf(-1), InputRules.mappedSource("com.tv/.HdmiInputService/HW8", map));
        assertNull(InputRules.mappedSource("com.tv/.HdmiInputService/HW9", map));
        assertNull(InputRules.mappedSource("com.tv/.HdmiInputService/HW6", map));
        assertNull(InputRules.mappedSource(null, map));
    }
}
