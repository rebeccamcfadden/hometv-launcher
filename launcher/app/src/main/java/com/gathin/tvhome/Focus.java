package com.gathin.tvhome;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks views the D-pad can reach, as the prototype's focusable() does (design_preview/src/core/focus.ts). A group
 * names a row or grid that remembers where focus was, a key restores focus after a view is rebuilt, and the default
 * is the item a group picks the first time it is entered. OK on a focused view runs its click listener.
 */
final class Focus {
    private Focus() {}

    static <T extends View> T make(T v, String group, String key, boolean isDefault, View.OnClickListener onOk) {
        v.setFocusable(true);
        v.setTag(R.id.ui_focusable, Boolean.TRUE);
        if (group != null) v.setTag(R.id.ui_focus_group, group);
        if (key != null) v.setTag(R.id.ui_focus_key, key);
        if (isDefault) v.setTag(R.id.ui_focus_default, Boolean.TRUE);
        if (onOk != null) v.setOnClickListener(onOk);
        return v;
    }

    static boolean isFocusable(View v) {
        return Boolean.TRUE.equals(v.getTag(R.id.ui_focusable));
    }

    static String group(View v) {
        return v == null ? null : (String) v.getTag(R.id.ui_focus_group);
    }

    static String key(View v) {
        return v == null ? null : (String) v.getTag(R.id.ui_focus_key);
    }

    static boolean isDefault(View v) {
        return Boolean.TRUE.equals(v.getTag(R.id.ui_focus_default));
    }

    /** Every focusable view under root that is on screen now (laid out with a size). */
    static List<View> candidates(View root) {
        List<View> out = new ArrayList<>();
        collect(root, out, true);
        return out;
    }

    /** Every focusable view under root that is visible, laid out or not: for restoring focus to a fresh screen. */
    static List<View> all(View root) {
        List<View> out = new ArrayList<>();
        collect(root, out, false);
        return out;
    }

    private static void collect(View v, List<View> out, boolean sized) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (isFocusable(v) && v.isEnabled() && (!sized || (v.getWidth() > 0 && v.getHeight() > 0))) out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out, sized);
        }
    }

    /** The visible view with this key under root, or null. */
    static View byKey(View root, String key) {
        if (key == null) return null;
        for (View v : all(root)) if (key.equals(key(v))) return v;
        return null;
    }

    /** Whether v sits somewhere under root. */
    static boolean isInside(View root, View v) {
        for (View cur = v; cur != null; cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) {
            if (cur == root) return true;
        }
        return false;
    }

    /**
     * v's bounds in root's coordinates as {left, top, right, bottom}. Translations and scrolling count, scale does
     * not: a focused item's scale-up must not change where the D-pad thinks it is.
     */
    static float[] rectIn(View v, View root) {
        float x = 0, y = 0;
        View cur = v;
        while (cur != null && cur != root) {
            x += cur.getLeft() + cur.getTranslationX();
            y += cur.getTop() + cur.getTranslationY();
            if (!(cur.getParent() instanceof View)) break;
            View parent = (View) cur.getParent();
            x -= parent.getScrollX();
            y -= parent.getScrollY();
            cur = parent;
        }
        return new float[]{x, y, x + v.getWidth(), y + v.getHeight()};
    }
}
