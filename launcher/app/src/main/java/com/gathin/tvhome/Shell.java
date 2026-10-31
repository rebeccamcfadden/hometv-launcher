package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The new UI's screen stack, keys and focus, ported from design_preview/src/core/app.ts. The prototype is the
 * source of truth for behaviour: the stack and its layers, Back, focus memory per screen and per row or grid, and
 * the page transition between the nav's sections. The D-pad never goes through Android's own focus search: arrows
 * go to SpatialNav, so the TV moves exactly as the prototype does.
 */
final class Shell {
    interface Pages {
        Screen create(Screen.Tab tab);
    }

    static final String NAV_GROUP = "nav";

    final FrameLayout stage;
    private final Context ctx;
    private final Pages pages;
    private final List<Screen> stack = new ArrayList<>();
    private final Map<Screen.Tab, Screen> tabs = new EnumMap<>(Screen.Tab.class);
    private final TextView toast;
    private final Runnable hideToast;
    private View focused;

    /** The page sliding out during a tab switch, kept on the stage under the new one until it has gone. */
    private Screen leaving;
    private Screen entering;
    private final Runnable endTransition = this::endTransition;

    /** OK is acted on when it is released, on the item it went down on, so an OK that opened something can't
     *  also activate the first row of what it opened. Held past the key repeat delay, the screen is offered a
     *  hold-OK once; if it takes it, the release does nothing, otherwise the release is an ordinary OK. */
    private View okTarget;
    private boolean okLongTried;
    private boolean okHeld;
    /** The key whose character a screen took as typing, so its key-up is taken too (M6). */
    private int typedKey = KeyEvent.KEYCODE_UNKNOWN;

    Shell(FrameLayout stage, Pages pages) {
        this.stage = stage;
        this.ctx = stage.getContext();
        this.pages = pages;
        toast = Ui.text(ctx, 22, Ui.TEXT_1, Ui.REGULAR);
        toast.setPadding(Ui.px(32), Ui.px(18), Ui.px(32), Ui.px(18));
        toast.setBackground(Ui.round(Ui.TOAST_BG, Ui.RADIUS_PILL, Ui.LINE_STRONG, Ui.LINE_W));
        toast.setElevation(Ui.px(20));
        toast.setAlpha(0f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.px(110);
        stage.addView(toast, lp);
        hideToast = () -> toast.animate().alpha(0f).translationY(Ui.px(20)).setDuration(Ui.ms(Ui.FAST_MS)).start();
    }

    Screen top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /** Whether s is on the stack, showing or under another screen. */
    boolean has(Screen s) {
        return stack.contains(s);
    }

    View focused() {
        return focused;
    }

    /** The top-level page for a nav section, made once and kept, so it keeps its focus and state. */
    Screen tab(Screen.Tab id) {
        Screen s = tabs.get(id);
        if (s == null) {
            s = pages.create(id);
            s.tab = id;
            tabs.put(id, s);
        }
        return s;
    }

    static String navKey(Screen.Tab tab) {
        return "nav-" + tab.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Shows a nav section's page, with focus on its own nav item when focusNav, else where it was left. */
    void goTab(Screen.Tab id, boolean focusNav) {
        Screen s = tab(id);
        Screen prev = stack.size() == 1 ? stack.get(0) : null;
        Screen root = stack.isEmpty() ? null : stack.get(0);
        if (root != null && root != s) root.onLeave();
        endTransition();
        boolean animate = prev != null && prev.tab != null && prev != s && !Ui.reducedMotion();
        if (animate) leaving = prev;
        stack.clear();
        stack.add(s);
        render();
        if (animate) startTransition(prev, s);
        s.onShow();
        View nav = focusNav ? Focus.byKey(s.view, navKey(id)) : null;
        focus(nav != null ? nav : restoreTarget(s));
    }

    /**
     * OK on a nav section. Coming back to the page being left should put focus on that page's own section, not on
     * the one it switched to.
     */
    void switchTab(Screen.Tab id) {
        Screen from = top();
        goTab(id, true);
        if (from == null || from.tab == null) return;
        View own = Focus.byKey(from.view, navKey(from.tab));
        if (own == null) return;
        from.lastFocus = own;
        from.lastFocusKey = Focus.key(own);
        from.memory.put(NAV_GROUP, own);
    }

    /** The remote's Home on Home: everything over it closes and Home starts again from its first item. */
    void home() {
        Screen home = tab(Screen.Tab.HOME);
        home.lastFocus = null;
        home.lastFocusKey = null;
        home.memory.clear();
        goTab(Screen.Tab.HOME, false);
    }

    void push(Screen s) {
        stack.add(s);
        render();
        s.onShow();
        focus(restoreTarget(s));
    }

    void pop() {
        if (stack.size() <= 1) return;
        stack.remove(stack.size() - 1);
        Screen s = top();
        render();
        s.onShow();
        focus(restoreTarget(s));
    }

    /** Closes every dropdown and drawer, e.g. when another app takes the screen. */
    void closeDialogs() {
        boolean any = false;
        while (stack.size() > 1 && top().layer == Screen.Layer.DIALOG) {
            stack.remove(stack.size() - 1);
            any = true;
        }
        if (!any) return;
        render();
        Screen s = top();
        s.onShow();
        focus(restoreTarget(s));
    }

    boolean isOpen(String label) {
        Screen s = top();
        return s != null && s.layer != Screen.Layer.PAGE && label.equals(s.label);
    }

    void back() {
        Screen s = top();
        if (s == null || s.onKey(Screen.Key.BACK, focused)) return;
        switch (Layers.back(stack.size(), s.tab)) {
            case POP:
                pop();
                break;
            case HOME:
                goTab(Screen.Tab.HOME, true);
                break;
            default:
                break;
        }
    }

    void focus(View v) {
        Screen s = top();
        if (v == null || s == null) return;
        v.requestFocus();
        focused = v;
        // M3: scroll every ScrollBox above v so it is in view (the prototype's ensureVisible).
        ScrollBox.reveal(v);
        s.lastFocus = v;
        s.lastFocusKey = Focus.key(v);
        String group = Focus.group(v);
        if (group != null) s.memory.put(group, v);
        s.onFocus(v);
    }

    /** Focus survives a rebuild by key, and falls back to the screen's default. */
    View restoreTarget(Screen s) {
        View last = s.lastFocus;
        if (last != null && last.isAttachedToWindow() && last.isShown() && Focus.isInside(s.view, last)) return last;
        View byKey = Focus.byKey(s.view, s.lastFocusKey);
        if (byKey != null) return byKey;
        View d = s.defaultFocus();
        if (d != null) return d;
        List<View> all = Focus.all(s.view);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Call after a screen rebuilt part of itself, so focus never stays on a view that has gone. */
    void refocus() {
        Screen s = top();
        if (s == null) return;
        if (focused != null && focused.isAttachedToWindow() && focused.isShown() && Focus.isInside(s.view, focused)) return;
        focus(restoreTarget(s));
    }

    /** A move limited to root, for screens that handle their edges themselves. */
    boolean moveWithin(View root, SpatialNav.Dir dir) {
        Screen s = top();
        if (s == null || focused == null) return false;
        View next = intoNav(s.view, findNext(root, focused, dir, s.memory));
        if (next != null) focus(next);
        return next != null;
    }

    /**
     * A move from the page into the nav lands on the page's own section (its tab, or Movies / TV Shows for Pick for
     * me), not on the section or device icon used last or nearest (app.ts intoNav): Up from Home after a look at
     * Search is Home. Moves along the nav itself are left alone.
     */
    private View intoNav(View root, View next) {
        if (next == null || focused == null) return next;
        View bar = navBarOf(next, root);
        if (bar == null || navBarOf(focused, root) != null) return next;
        for (View v : Focus.candidates(bar)) {
            if (NAV_GROUP.equals(Focus.group(v)) && v.isActivated()) return v;
        }
        return next;
    }

    void toast(CharSequence text) {
        toast.setText(text);
        toast.removeCallbacks(hideToast);
        toast.bringToFront();
        toast.setTranslationY(Ui.px(20));
        toast.animate().alpha(1f).translationY(0f).setDuration(Ui.ms(Ui.SLOW_MS)).setInterpolator(Ui.EASE).start();
        toast.postDelayed(hideToast, 2400);
    }

    // ---- Keys --------------------------------------------------------------------------------------------------

    /** The D-pad, OK, Back, Backspace and typing (M6). Returns false for every other key. */
    boolean dispatch(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        SpatialNav.Dir dir = dirOf(code);
        if (dir != null) {
            if (down) move(dir);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A) {
            if (down) {
                if (e.getRepeatCount() == 0) {
                    okTarget = focused;
                    okLongTried = false;
                    okHeld = false;
                } else if (!okLongTried && okTarget != null && okTarget == focused) {
                    okLongTried = true;
                    Screen s = top();
                    okHeld = s != null && s.onLongOk(focused);
                }
            } else {
                boolean act = !e.isCanceled() && !okHeld && okTarget != null && okTarget == focused;
                okTarget = null;
                okLongTried = false;
                okHeld = false;
                if (act) ok();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) {
            if (!down && !e.isCanceled()) back();
            return true;
        }
        // A keyboard's Backspace is Back on every screen (app.ts onKeyDown), Search included: only its on-screen
        // delete key deletes. One press is one Back; its repeats are ignored until the key is let go.
        if (code == KeyEvent.KEYCODE_DEL) {
            if (down && e.getRepeatCount() == 0) back();
            return true;
        }
        // M6: typing from a hardware keyboard or a remote with keys (app.ts onKeyDown).
        // A printable character goes to the top screen; only a screen that types (Search) takes it, and then its
        // key-up is taken too. Characters with Ctrl, Alt or Meta are shortcuts, not typing.
        if (down) {
            int u = e.getUnicodeChar();
            Screen s = top();
            if (u > 0 && !Character.isISOControl(u) && Character.isBmpCodePoint(u) && !e.isCtrlPressed()
                    && !e.isAltPressed() && !e.isMetaPressed() && s != null && s.onText((char) u)) {
                typedKey = code;
                return true;
            }
        } else if (code == typedKey) {
            typedKey = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        return false;
    }

    private static SpatialNav.Dir dirOf(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return SpatialNav.Dir.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return SpatialNav.Dir.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return SpatialNav.Dir.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return SpatialNav.Dir.RIGHT;
            default:
                return null;
        }
    }

    /** The direction of a D-pad key, or null for OK and Back. */
    static SpatialNav.Dir dirOf(Screen.Key key) {
        switch (key) {
            case UP:
                return SpatialNav.Dir.UP;
            case DOWN:
                return SpatialNav.Dir.DOWN;
            case LEFT:
                return SpatialNav.Dir.LEFT;
            case RIGHT:
                return SpatialNav.Dir.RIGHT;
            default:
                return null;
        }
    }

    /** A D-pad move on the top screen, as if the key had been pressed: the screen sees it first. */
    void move(SpatialNav.Dir dir) {
        Screen s = top();
        if (s == null) return;
        if (focused == null || !focused.isAttachedToWindow() || !Focus.isInside(s.view, focused)) {
            focus(restoreTarget(s));
            return;
        }
        // Left and Right on the top bar stay on it, to the nearest item, whatever the page below holds and whichever
        // section was used last: ← from Wi-Fi is always Search (project lead, 2026-10-29). Past either end, nothing.
        View bar = navBarOf(focused, s.view);
        if (bar != null && (dir == SpatialNav.Dir.LEFT || dir == SpatialNav.Dir.RIGHT)) {
            View next = nearestIn(bar, focused, dir);
            if (next != null) {
                focus(next);
                stage.playSoundEffect(soundFor(dir));
            }
            return;
        }
        if (s.onKey(Screen.Key.valueOf(dir.name()), focused)) return;
        View next = intoNav(s.view, findNext(s.view, focused, dir, s.memory));
        if (next == null) return;
        focus(next);
        stage.playSoundEffect(soundFor(dir));
    }

    /** The top bar v sits in, below root; null when v isn't on it. */
    private static View navBarOf(View v, View root) {
        for (View c = v; c != null && c != root; c = c.getParent() instanceof View ? (View) c.getParent() : null) {
            if (c.getTag(R.id.ui_nav) != null) return c;
        }
        return null;
    }

    private static int soundFor(SpatialNav.Dir dir) {
        switch (dir) {
            case UP:
                return SoundEffectConstants.NAVIGATION_UP;
            case DOWN:
                return SoundEffectConstants.NAVIGATION_DOWN;
            case LEFT:
                return SoundEffectConstants.NAVIGATION_LEFT;
            default:
                return SoundEffectConstants.NAVIGATION_RIGHT;
        }
    }

    private void ok() {
        Screen s = top();
        if (s == null || s.onKey(Screen.Key.OK, focused)) return;
        if (focused != null && focused.isAttachedToWindow() && focused.hasOnClickListeners()) focused.performClick();
    }

    /** The nearest item in dir under root, with no row memory or default: the next item along the top bar. */
    private static View nearestIn(View root, View from, SpatialNav.Dir dir) {
        List<View> list = Focus.candidates(root);
        list.remove(from);
        List<float[]> rects = new ArrayList<>(list.size());
        for (View v : list) rects.add(Focus.rectIn(v, root));
        int i = SpatialNav.nearest(Focus.rectIn(from, root), rects, dir);
        return i < 0 ? null : list.get(i);
    }

    /** The nearest item in dir; entering another row or grid returns to the item it remembers, else its default. */
    private View findNext(View root, View from, SpatialNav.Dir dir, Map<String, View> memory) {
        List<View> list = Focus.candidates(root);
        list.remove(from);
        List<float[]> rects = new ArrayList<>(list.size());
        for (View v : list) rects.add(Focus.rectIn(v, root));
        int i = SpatialNav.nearest(Focus.rectIn(from, root), rects, dir);
        if (i < 0) return null;
        View best = list.get(i);
        String group = Focus.group(best);
        if (group != null && !group.equals(Focus.group(from))) {
            View remembered = memory.get(group);
            if (remembered != null && remembered.isAttachedToWindow() && list.contains(remembered)) return remembered;
            for (View v : list) if (group.equals(Focus.group(v)) && Focus.isDefault(v)) return v;
        }
        return best;
    }

    // ---- Drawing -----------------------------------------------------------------------------------------------

    /** Puts the visible screens on the stage in order (the leaving page at the bottom, the toast on top). */
    private void render() {
        Screen.Layer[] layers = new Screen.Layer[stack.size()];
        for (int i = 0; i < layers.length; i++) layers[i] = stack.get(i).layer;
        List<View> want = new ArrayList<>();
        if (leaving != null) want.add(leaving.view);
        int[] visible = Layers.visible(layers);
        for (int i : visible) want.add(stack.get(i).view);
        for (int i = stage.getChildCount() - 1; i >= 0; i--) {
            View child = stage.getChildAt(i);
            if (child != toast && !want.contains(child)) stage.removeViewAt(i);
        }
        for (int i = 0; i < want.size(); i++) {
            View v = want.get(i);
            if (stage.getChildAt(i) == v) continue;
            if (v.getParent() == stage) stage.removeView(v);
            else if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            stage.addView(v, i, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        Screen top = top();
        for (View v : want) {
            if (!(v instanceof ViewGroup)) continue;
            ((ViewGroup) v).setDescendantFocusability(top != null && v == top.view
                    ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }

    /**
     * The new page's content slides 90 px in from the side of the section it came from and fades in (360 ms); the
     * old content slides 60 px the other way and fades out (240 ms). The nav and the page's shade stay still.
     */
    private void startTransition(Screen from, Screen to) {
        int dir = Integer.signum(to.tab.ordinal() - from.tab.ordinal());
        if (dir == 0) dir = 1;
        entering = to;
        to.view.setAlpha(0f);
        to.view.animate().alpha(1f).setDuration(240).setInterpolator(null).start();
        for (View child : sliding(to.view)) {
            child.setAlpha(0f);
            child.setTranslationX(dir * Ui.px(90));
            child.animate().alpha(1f).translationX(0f).setDuration(360).setInterpolator(Ui.EASE).start();
        }
        for (View child : sliding(from.view)) {
            child.animate().alpha(0f).translationX(-dir * Ui.px(60)).setDuration(240)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
        }
        stage.postDelayed(endTransition, 380);
    }

    private void endTransition() {
        stage.removeCallbacks(endTransition);
        if (entering != null) {
            entering.view.animate().cancel();
            entering.view.setAlpha(1f);
            for (View child : sliding(entering.view)) settle(child);
            entering = null;
        }
        if (leaving == null) return;
        Screen gone = leaving;
        leaving = null;
        for (View child : sliding(gone.view)) settle(child);
        if (!stack.contains(gone) && gone.view.getParent() == stage) stage.removeView(gone.view);
    }

    private static void settle(View v) {
        v.animate().cancel();
        v.setAlpha(1f);
        v.setTranslationX(0f);
    }

    /** A page's children that slide during a transition: everything but the nav and the shade. */
    private static List<View> sliding(View page) {
        List<View> out = new ArrayList<>();
        if (!(page instanceof ViewGroup)) return out;
        ViewGroup g = (ViewGroup) page;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getTag(R.id.ui_nav) == null && c.getTag(R.id.ui_still) == null) out.add(c);
        }
        return out;
    }
}
