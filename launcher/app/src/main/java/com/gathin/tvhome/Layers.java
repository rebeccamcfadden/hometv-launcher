package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * The stack rules from design_preview/README.md (Screens and layers, Back), kept apart from the views so they are
 * tested on the JVM.
 */
final class Layers {
    private Layers() {}

    /**
     * Which stack entries are drawn, bottom to top: the topmost page, the last popup above it, and every dialog
     * above it. A popup opened from a popup (More like this) replaces the one under it on screen.
     */
    static int[] visible(Screen.Layer[] stack) {
        if (stack.length == 0) return new int[0];
        int base = stack.length - 1;
        while (base > 0 && stack[base] != Screen.Layer.PAGE) base--;
        int lastPopup = -1;
        for (int i = base + 1; i < stack.length; i++) if (stack[i] == Screen.Layer.POPUP) lastPopup = i;
        List<Integer> out = new ArrayList<>();
        out.add(base);
        for (int i = base + 1; i < stack.length; i++) {
            if (stack[i] == Screen.Layer.DIALOG || i == lastPopup) out.add(i);
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    enum Back { NONE, POP, HOME }

    /**
     * What Back does once the top screen has declined it: close the top dialog or popup, or return to the previous
     * page; on a top-level page other than Home, go Home; on Home, nothing.
     */
    static Back back(int stackSize, Screen.Tab topTab) {
        if (stackSize > 1) return Back.POP;
        if (stackSize == 1 && topTab != null && topTab != Screen.Tab.HOME) return Back.HOME;
        return Back.NONE;
    }
}
