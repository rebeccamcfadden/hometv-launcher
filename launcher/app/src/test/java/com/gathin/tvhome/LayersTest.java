package com.gathin.tvhome;

import org.junit.Test;

import static com.gathin.tvhome.Screen.Layer.DIALOG;
import static com.gathin.tvhome.Screen.Layer.PAGE;
import static com.gathin.tvhome.Screen.Layer.POPUP;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** The stack rules in design_preview/README.md: which layers are drawn, and what Back does. */
public class LayersTest {

    private static Screen.Layer[] stack(Screen.Layer... layers) {
        return layers;
    }

    @Test
    public void aPageAloneIsDrawn() {
        assertArrayEquals(new int[]{0}, Layers.visible(stack(PAGE)));
        assertArrayEquals(new int[0], Layers.visible(stack()));
    }

    @Test
    public void aDropdownDrawsOverItsPage() {
        assertArrayEquals(new int[]{0, 1}, Layers.visible(stack(PAGE, DIALOG)));
    }

    @Test
    public void aPopupFromAPopupReplacesItOnScreen() {
        // Title preview, then More like this opens another preview on top: only the latest popup shows.
        assertArrayEquals(new int[]{0, 2}, Layers.visible(stack(PAGE, POPUP, POPUP)));
        assertArrayEquals(new int[]{0, 2, 3}, Layers.visible(stack(PAGE, POPUP, POPUP, DIALOG)));
    }

    @Test
    public void aPushedPageHidesThePagesUnderIt() {
        // Movies, then Pick for me: the pushed page is the base now.
        assertArrayEquals(new int[]{1, 2}, Layers.visible(stack(PAGE, PAGE, DIALOG)));
    }

    @Test
    public void backClosesThenGoesHome() {
        assertEquals(Layers.Back.POP, Layers.back(2, null));
        assertEquals(Layers.Back.HOME, Layers.back(1, Screen.Tab.MOVIES));
        assertEquals(Layers.Back.HOME, Layers.back(1, Screen.Tab.SEARCH));
        assertEquals(Layers.Back.NONE, Layers.back(1, Screen.Tab.HOME));
        assertEquals(Layers.Back.NONE, Layers.back(0, null));
    }
}
