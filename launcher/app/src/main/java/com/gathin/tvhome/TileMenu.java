package com.gathin.tvhome;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;

/**
 * The hold-OK menu on an app tile (docs/rework.md: carried into V1, restyled): the dropdowns' glass panel, hung from
 * the tile instead of a nav button. It opens under the tile, else over it, else beside it (MenuPlace), never over
 * Home's greeting, date and clock, and closes on Back, on OK on a row, or when the D-pad moves past its edge; focus
 * then goes back to the tile.
 */
final class TileMenu {
    private TileMenu() {}

    static final String LABEL = "Tile menu";

    static Screen create(Shell shell, View tile, List<MenuPanel.Row> rows) {
        return create(shell, tile, rows, null);
    }

    /** As create, keeping the menu below the avoid view (Home's greeting, date and clock) while that view is showing. */
    static Screen create(Shell shell, View tile, List<MenuPanel.Row> rows, View avoid) {
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
        int ceiling = 0;
        if (avoid != null && avoid.isShown() && avoid.getAlpha() > 0f) {
            ceiling = Math.round(Focus.rectIn(avoid, shell.stage)[3]) + gap;
        }
        MenuPlace at = MenuPlace.of(r, pw, ph, stageW, stageH, Ui.px(Ui.SAFE_X), Ui.px(Ui.SAFE_Y), gap, ceiling);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.leftMargin = at.left;
        lp.topMargin = at.top;
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
                // It grows out of the tile: down from under it, up from over it, sideways from beside it.
                boolean sideways = at.side == MenuPlace.Side.RIGHT || at.side == MenuPlace.Side.LEFT;
                int shift = Ui.px(8);
                if (sideways) panel.setTranslationX(at.side == MenuPlace.Side.RIGHT ? -shift : shift);
                else panel.setTranslationY(at.side == MenuPlace.Side.BELOW ? -shift : shift);
                panel.post(() -> {
                    panel.setPivotX(at.side == MenuPlace.Side.LEFT ? panel.getWidth() : 0);
                    panel.setPivotY(at.side == MenuPlace.Side.ABOVE ? panel.getHeight()
                            : sideways ? panel.getHeight() / 2f : 0);
                    panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                            .setDuration(200).setInterpolator(Ui.EASE).start();
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
