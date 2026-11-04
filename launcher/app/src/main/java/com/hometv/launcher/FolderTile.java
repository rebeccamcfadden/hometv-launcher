package com.hometv.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * All Apps' TV Utilities folder tile (F35, F41; design_preview/src/ui/folderArt.ts): a folder, with a tab on its top
 * edge and a gear in the middle, drawn in code (FolderArt) in the theme's colours, with "TV Utilities · 13" under it.
 * Focused, it scales up 5% and the accent focus ring follows the folder's outline, tab included, as an AppTile's ring
 * follows its tile.
 */
@SuppressLint("ViewConstructor") // built in code only, never inflated
final class FolderTile extends LinearLayout {
    FolderTile(Context ctx, int count, float width, float height, float labelGap) {
        super(ctx);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);
        String label = ctx.getString(R.string.tv_utilities_tile, count);
        setContentDescription(label);

        int ring = AppTile.RING;
        int w = Ui.px(width), h = Ui.px(height);
        Face face = new Face(ctx, width, height, ring);
        face.setDuplicateParentStateEnabled(true);
        addView(face, new LayoutParams(w + 2 * Ui.px(ring), h + 2 * Ui.px(ring)));

        TextView under = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
        under.setText(label);
        under.setGravity(Gravity.CENTER);
        under.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.TEXT_1));
        under.setDuplicateParentStateEnabled(true);
        LayoutParams ulp = new LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = Ui.px(labelGap - ring);
        addView(under, ulp);

        setOnFocusChangeListener((v, focused) -> {
            float s = Ui.focusScale(focused, 1.05f);
            face.animate().scaleX(s).scaleY(s).setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
        });
    }

    /** The folder itself, {@code ring} design px inside the view's bounds, the room the focus ring needs. */
    private static final class Face extends View {
        private final float width, height, ring;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint focusRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF body = new RectF();
        private Path folder, gear;

        Face(Context ctx, float width, float height, float ring) {
            super(ctx);
            this.width = width;
            this.height = height;
            this.ring = ring;
            line.setStyle(Paint.Style.STROKE);
            focusRing.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            float s = Ui.px(1000) / 1000f, in = Ui.px(ring), fw = Ui.px(width), fh = Ui.px(height);
            folder = FolderArt.folder(in, in, fw, fh, s);
            float t = Ui.px(FolderArt.TAB_H);
            body.set(in, in + t, in + fw, in + fh);
            gear = FolderArt.gear(in + fw / 2f, in + t + (fh - t) / 2f, Ui.px(FolderArt.gearRadius(width, height)));
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (folder == null) return;
            boolean focused = false;
            for (int st : getDrawableState()) if (st == android.R.attr.state_focused) focused = true;
            float r = Ui.px(FolderArt.RADIUS);
            // The glow is drawn around the body's rect; clipped to outside the folder, so none of it shows through
            // the see-through tab or the tab's square corner.
            c.save();
            c.clipOutPath(folder);
            if (focused) {
                Glow.focusShadow(c, body, r, 255);
                Glow.focus(c, body, r, 255);
            } else {
                Glow.rest(c, body, r, 255);
            }
            c.restore();
            if (focused && Ui.FOCUS_W > 0) {
                // A stroke twice the ring's width along the outline, clipped to outside the folder: the ring follows
                // the tab, just outside the shape, as TileFrame's ring sits just outside a tile.
                float w = Math.max(1, Ui.px(Ui.FOCUS_W));
                c.save();
                c.clipOutPath(folder);
                focusRing.setStrokeWidth(2 * w);
                focusRing.setColor(Ui.ACCENT);
                c.drawPath(folder, focusRing);
                c.restore();
            }
            fill.setColor(focused ? Ui.SURFACE_2 : Ui.SURFACE);
            c.drawPath(folder, fill);
            int lw = Ui.linePx();
            if (lw > 0) {
                line.setStrokeWidth(lw);
                line.setColor(Ui.LINE);
                c.drawPath(folder, line);
            }
            fill.setColor(focused ? Ui.TEXT_1 : Ui.TEXT_2);
            c.drawPath(gear, fill);
        }
    }
}
