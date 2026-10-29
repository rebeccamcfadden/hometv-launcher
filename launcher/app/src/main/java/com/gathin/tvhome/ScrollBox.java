package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/**
 * Scrolling areas that keep the focused item in view, as the prototype's ensureVisible does
 * (design_preview/src/core/focus.ts): a vertical page or a row that runs off the screen's edge. Shell.focus calls
 * {@link #reveal} for every focus change, and each ScrollBox above the focused view scrolls just enough to bring it
 * inside the box with {@code pad} design pixels to spare, smoothly ({@link Ui#SLOW_MS}) or at once with reduced
 * motion. The boxes never take focus themselves, show no scrollbars or overscroll glow, and never scroll on their
 * own: Android's scroll-to-focused-child is switched off, since the D-pad goes through Shell, not focus search.
 *
 * <p>A box does not clip its content to its padding or to the content's bounds, so a focused card's ring and
 * scale-up can use the box's padding. Whatever holds the box decides whether it is clipped to its own bounds:
 * Kit.row clips it, as the prototype's rows do. Give a box padding for that room (and a matching negative margin)
 * where its items sit at its edge, and reveal with the same padding, as Kit.row does.
 */
final class ScrollBox {
    private ScrollBox() {}

    /** What both boxes do: scroll so a descendant is in view. */
    interface Box {
        void reveal(View descendant);
    }

    static ViewGroup vertical(Context ctx, float padDesign) {
        return new Vertical(ctx, padDesign);
    }

    static ViewGroup horizontal(Context ctx, float padDesign) {
        return new Horizontal(ctx, padDesign);
    }

    /** Scrolls every ScrollBox above v so v is inside it with the box's padding. Before layout, once it is laid out. */
    static void reveal(View v) {
        if (v == null) return;
        if (!v.isLaidOut()) {
            v.post(() -> {
                if (v.isAttachedToWindow()) revealNow(v);
            });
            return;
        }
        revealNow(v);
    }

    private static void revealNow(View v) {
        for (ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
            if (p instanceof Box) ((Box) p).reveal(v);
        }
    }

    private static void setUp(ViewGroup box) {
        box.setFocusable(false);
        box.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        box.setVerticalScrollBarEnabled(false);
        box.setHorizontalScrollBarEnabled(false);
        box.setOverScrollMode(View.OVER_SCROLL_NEVER);
        box.setClipToPadding(false);
        box.setClipChildren(false);
    }

    /**
     * How far to scroll so [start, end] (in the box's visible coordinates) sits within [pad, size - pad]: the
     * prototype's rule, nearer edge first. 0 when it already does.
     */
    static int delta(float start, float end, int size, int pad) {
        if (start < pad) return Math.round(start - pad);
        if (end > size - pad) return Math.round(end - (size - pad));
        return 0;
    }

    /** One running scroll animation per box; a new reveal takes over from wherever it got to. */
    private static ValueAnimator scroll(ValueAnimator running, int from, int to, ValueAnimator.AnimatorUpdateListener l) {
        if (running != null) running.cancel();
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(Ui.ms(Ui.SLOW_MS));
        a.setInterpolator(Ui.EASE);
        a.addUpdateListener(l);
        a.start();
        return a;
    }

    private static final class Vertical extends ScrollView implements Box {
        private final float pad;
        private ValueAnimator anim;

        Vertical(Context ctx, float padDesign) {
            super(ctx);
            pad = padDesign;
            setFillViewport(false);
            setUp(this);
        }

        @Override
        public void reveal(View d) {
            // A newer reveal always wins, even when its target is already in view: a scroll still running for the
            // previous focus could carry this one off screen.
            stop();
            float[] r = Focus.rectIn(d, this);
            int dy = delta(r[1], r[3], getHeight(), Ui.px(pad));
            if (dy == 0) return;
            View child = getChildCount() > 0 ? getChildAt(0) : null;
            int max = child == null ? 0 : Math.max(0, child.getHeight() + getPaddingTop() + getPaddingBottom() - getHeight());
            int to = Math.max(0, Math.min(max, getScrollY() + dy));
            if (to == getScrollY()) return;
            if (Ui.ms(Ui.SLOW_MS) == 0) {
                scrollTo(getScrollX(), to);
                return;
            }
            anim = scroll(null, getScrollY(), to, a -> scrollTo(getScrollX(), (Integer) a.getAnimatedValue()));
        }

        private void stop() {
            if (anim != null) anim.cancel();
            anim = null;
        }

        /** Android's own scroll-to-focus is off: Shell reveals the focused view. */
        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            return 0;
        }
    }

    private static final class Horizontal extends HorizontalScrollView implements Box {
        private final float pad;
        private ValueAnimator anim;

        Horizontal(Context ctx, float padDesign) {
            super(ctx);
            pad = padDesign;
            setFillViewport(false);
            setUp(this);
        }

        @Override
        public void reveal(View d) {
            stop();
            float[] r = Focus.rectIn(d, this);
            int dx = delta(r[0], r[2], getWidth(), Ui.px(pad));
            if (dx == 0) return;
            View child = getChildCount() > 0 ? getChildAt(0) : null;
            int max = child == null ? 0 : Math.max(0, child.getWidth() + getPaddingLeft() + getPaddingRight() - getWidth());
            int to = Math.max(0, Math.min(max, getScrollX() + dx));
            if (to == getScrollX()) return;
            if (Ui.ms(Ui.SLOW_MS) == 0) {
                scrollTo(to, getScrollY());
                return;
            }
            anim = scroll(null, getScrollX(), to, a -> scrollTo((Integer) a.getAnimatedValue(), getScrollY()));
        }

        private void stop() {
            if (anim != null) anim.cancel();
            anim = null;
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            return 0;
        }
    }
}
