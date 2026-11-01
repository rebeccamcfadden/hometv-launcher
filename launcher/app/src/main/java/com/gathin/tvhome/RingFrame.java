package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * A rounded (or round) frame for artwork and portraits (design_preview/src/styles/base.css, .card-art and
 * .person-portrait). What goes in {@link #content()} is clipped to the shape, a hairline runs inside the edge, and
 * while the {@link #setRing ring} is on, the accent focus ring (Ui.FOCUS_W, 4 px in Midnight) is drawn just outside
 * it, over the theme's focus glow; resting, the theme's tile shadow, if any. The ring and glow lie outside the
 * frame's bounds, so the views above the frame must not clip their children (Kit's cards and rows see to that).
 * Built in code only, never inflated from XML, so it has no XML constructors.
 */
@SuppressLint("ViewConstructor")
final class RingFrame extends FrameLayout {
    private final float radius;
    private final boolean oval;
    private final FrameLayout content;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF box = new RectF();
    private boolean ringOn;

    /** radiusDesign in design px; oval makes it a circle whatever the radius. fill shows until the content covers it. */
    RingFrame(Context ctx, float radiusDesign, boolean oval, int fill) {
        super(ctx);
        this.radius = Ui.px(radiusDesign);
        this.oval = oval;
        setWillNotDraw(false);
        content = new FrameLayout(ctx);
        content.setBackground(oval ? Ui.oval(fill) : Ui.round(fill, radiusDesign));
        // Clipping by the outline is anti-aliased and cheap on the GPU, unlike clipPath.
        content.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                if (RingFrame.this.oval) outline.setOval(0, 0, view.getWidth(), view.getHeight());
                else outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        content.setClipToOutline(true);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.linePx());
        line.setColor(Ui.LINE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.px(Ui.FOCUS_W));
        ring.setColor(Ui.ACCENT);
    }

    /** The clipped area: put the artwork or the initials here. */
    FrameLayout content() {
        return content;
    }

    void setRing(boolean on) {
        if (ringOn == on) return;
        ringOn = on;
        invalidate();
    }

    boolean ringOn() {
        return ringOn;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Behind the content: the focus glow, or a resting tile's shadow (nothing in Midnight).
        box.set(0, 0, getWidth(), getHeight());
        float r = oval ? Math.min(getWidth(), getHeight()) / 2f : radius;
        if (ringOn) {
            Glow.focusShadow(canvas, box, r, 255);
            Glow.focus(canvas, box, r, 255);
        } else {
            Glow.rest(canvas, box, r, 255);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        float r = oval ? Math.min(getWidth(), getHeight()) / 2f : radius;
        // The inset hairline (box-shadow: inset 0 0 0 1px var(--line)).
        float half = line.getStrokeWidth() / 2f;
        if (half > 0) {
            rect.set(half, half, getWidth() - half, getHeight() - half);
            canvas.drawRoundRect(rect, Math.max(0, r - half), Math.max(0, r - half), line);
        }
        if (!ringOn || ring.getStrokeWidth() <= 0) return;
        // The focus ring outside the edge (box-shadow: 0 0 0 4px var(--accent)).
        float w = ring.getStrokeWidth() / 2f;
        rect.set(-w, -w, getWidth() + w, getHeight() + w);
        canvas.drawRoundRect(rect, r + w, r + w, ring);
    }
}
