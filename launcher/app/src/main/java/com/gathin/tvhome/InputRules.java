package com.gathin.tvhome;

import java.util.Locale;

/**
 * The Inputs menu's rules that need no TV to check (TvInputs applies them to TvInputManager's list). Each one came
 * from a set that got it wrong; the reasons are kept with the code.
 */
final class InputRules {
    private InputRules() {}

    /** An HDMI port's name always says HDMI ("HDMI 2", not "2" or "PlayStation"). */
    static String portLabel(String label, boolean hdmi) {
        if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) return "HDMI " + label;
        return label;
    }

    /** "Apple TV · HDMI 1": a name given in Settings > Inputs, or a CEC device's own, goes before the port. */
    static String named(CharSequence name, String port) {
        return name == null || name.length() == 0 ? port : name + " \u00B7 " + port;
    }

    /**
     * Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in; the state is
     * dropped then. A TV with every port really in use loses it too.
     */
    static boolean hdmiStateBogus(int ports, int connected) {
        return ports > 1 && connected == ports;
    }

    /**
     * Whether a tuner input beats the one picked so far for Live TV. A hardware tuner (".../HW<n>") wins: system
     * apps such as AirPlay register software inputs of type TUNER and may sort first (TCL G05). Among tuners of the
     * same kind an analog one loses (MediaTek's AnalogInputService would open a dead ATV source), then the lower id.
     */
    static boolean betterTuner(String id, String currentId) {
        if (currentId == null) return true;
        boolean hw = isHardware(id), curHw = isHardware(currentId);
        boolean analog = isAnalog(id), curAnalog = isAnalog(currentId);
        if (hw != curHw) return hw;
        if (analog != curAnalog) return !analog;
        return id.compareTo(currentId) < 0;
    }

    static boolean isHardware(String id) {
        return id.matches(".*/HW\\d+$");
    }

    static boolean isAnalog(String id) {
        return id.toLowerCase(Locale.ROOT).contains("analog");
    }

    /**
     * input_sources in config.xml: "HW5=23" maps the framework input whose id ends in /HW5 to a vendor source
     * number; "HW8=-" leaves that input out (a port the board reports but the set does not have). Returns the
     * number, -1 for a left-out input, or null when the input is not listed.
     */
    static Integer mappedSource(String inputId, String[] mappings) {
        if (inputId == null) return null;
        String hw = inputId.substring(inputId.lastIndexOf('/') + 1);
        for (String m : mappings) {
            int eq = m.indexOf('=');
            if (eq <= 0 || !m.substring(0, eq).trim().equals(hw)) continue;
            String v = m.substring(eq + 1).trim();
            if (v.equals("-")) return -1;
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
