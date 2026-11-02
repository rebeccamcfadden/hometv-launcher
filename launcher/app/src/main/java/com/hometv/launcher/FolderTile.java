package com.hometv.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * All Apps' TV Utilities folder tile (F35; design_preview/src/styles/screens.css, .aa-folder): an app tile's frame
 * holding the names of its first four apps two by two, with "TV Utilities · 13" under it. Focused, it scales up inside
 * the focus ring as an AppTile does.
 */
@SuppressLint("ViewConstructor") // built in code only, never inflated
final class FolderTile extends LinearLayout {
    FolderTile(Context ctx, List<CharSequence> names, int count, float width, float height, float labelGap) {
        super(ctx);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);
        String label = ctx.getString(R.string.tv_utilities_tile, count);
        setContentDescription(label);

        int ring = AppTile.RING;
        FrameLayout frame = new FrameLayout(ctx);
        frame.setPadding(Ui.px(ring), Ui.px(ring), Ui.px(ring), Ui.px(ring));
        frame.setBackground(Ui.states(TileFrame.resting(ring, Ui.RADIUS_L), null, TileFrame.focused(ring, Ui.RADIUS_L)));
        frame.setDuplicateParentStateEnabled(true);
        frame.setClipChildren(false);

        LinearLayout tile = new LinearLayout(ctx);
        tile.setOrientation(VERTICAL);
        int pad = Ui.px(12), gap = Ui.px(8);
        tile.setPadding(pad, pad, pad, pad);
        tile.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W), null,
                Ui.round(Ui.SURFACE_2, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W)));
        tile.setDuplicateParentStateEnabled(true);
        int w = Ui.px(width), h = Ui.px(height);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                TextView mini = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_2, Ui.REGULAR);
                mini.setGravity(Gravity.CENTER);
                mini.setPadding(Ui.px(6), 0, Ui.px(6), 0);
                mini.setBackground(Ui.round(Ui.SURFACE_2, Ui.RADIUS_S));
                if (i < names.size()) mini.setText(names.get(i));
                LayoutParams mlp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (c > 0) mlp.leftMargin = gap;
                row.addView(mini, mlp);
            }
            LayoutParams rlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            if (r > 0) rlp.topMargin = gap;
            tile.addView(row, rlp);
        }
        frame.addView(tile, new FrameLayout.LayoutParams(w, h));
        addView(frame, new LayoutParams(w + 2 * Ui.px(ring), h + 2 * Ui.px(ring)));

        TextView under = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
        under.setText(label);
        under.setGravity(Gravity.CENTER);
        under.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.TEXT_1));
        under.setDuplicateParentStateEnabled(true);
        LayoutParams ulp = new LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = Ui.px(labelGap - ring);
        addView(under, ulp);

        setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.05f : 1f;
            frame.animate().scaleX(s).scaleY(s).setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
        });
    }
}
