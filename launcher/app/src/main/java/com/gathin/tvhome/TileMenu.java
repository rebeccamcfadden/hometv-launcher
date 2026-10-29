package com.gathin.tvhome;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;

/**
 * The hold-OK menu on an app tile (docs/rework.md: carried into V1, restyled): the dropdowns' glass panel, hung from
 * the tile instead of a nav button. It opens under the tile, or over it where the screen has no room below, and
 * closes on Back, on OK on a row, or when the D-pad moves past its edge; focus then goes back to the tile.
 */
final class TileMenu {
    private TileMenu() {}

    static final String LABEL = "Tile menu";

    static Screen create(Shell shell, View tile, List<MenuPanel.Row> rows) {
        FrameLayout root = new FrameLayout(shell.stage.getContext());
        View catcher = new View(root.getContext());
        catcher.setOnClickListener(v -> close(shell, tile));
        root.addView(catcher, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout panel = MenuPanel.build(root.getContext(), null, rows, row -> {
            close(shell, tile);
            if (row.action != null) row.action.run();
        });

        int stageW = shell.stage.getWidth(), stageH = shell.stage.getHeight();
        panel.measure(View.MeasureSpec.makeMeasureSpec(stageW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(stageH, View.MeasureSpec.AT_MOST));
        int pw = panel.getMeasuredWidth(), ph = panel.getMeasuredHeight();
        float[] r = Focus.rectIn(tile, shell.stage);
        int gap = Ui.px(14);
        int safeX = Ui.px(Ui.SAFE_X), safeY = Ui.px(Ui.SAFE_Y);
        int left = Math.max(safeX, Math.min(Math.round(r[0]), stageW - safeX - pw));
        boolean below = r[3] + gap + ph <= stageH - safeY;
        int top = below ? Math.round(r[3]) + gap : Math.max(safeY, Math.round(r[1]) - gap - ph);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.leftMargin = left;
        lp.topMargin = top;
        root.addView(panel, lp);

        return new Screen(LABEL, root, Screen.Layer.DIALOG) {
            private boolean shown;

            @Override
            View defaultFocus() {
                for (View v : Focus.all(panel)) return v;
                return null;
            }

            @Override
            void onShow() {
                if (shown || Ui.reducedMotion()) return;
                shown = true;
                panel.setAlpha(0f);
                panel.setScaleX(0.96f);
                panel.setScaleY(0.96f);
                panel.setTranslationY(below ? -Ui.px(8) : Ui.px(8));
                panel.post(() -> {
                    panel.setPivotX(0);
                    panel.setPivotY(below ? 0 : panel.getHeight());
                    panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(200)
                            .setInterpolator(Ui.EASE).start();
                });
            }

            @Override
            boolean onKey(Key key, View focused) {
                if (key == Key.BACK) {
                    close(shell, tile);
                    return true;
                }
                SpatialNav.Dir dir = Shell.dirOf(key);
                if (dir == null) return false;
                if (!shell.moveWithin(panel, dir)) close(shell, tile);
                return true;
            }
        };
    }

    private static void close(Shell shell, View tile) {
        shell.pop();
        if (tile.isAttachedToWindow() && tile.isShown()) shell.focus(tile);
    }
}
