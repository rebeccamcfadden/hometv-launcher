package com.gathin.tvhome;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;

/**
 * A top-bar dropdown on the Shell's stack (dialog layer): it hangs under its button, grows inward from the right,
 * and closes on Back, on OK on a row, on a click outside it, or when the D-pad moves past its edge. Focus then
 * returns to the button; a move past the left, right or bottom edge carries on one step from the button, so
 * Right in the Wi-Fi menu lands on Inputs (decided on the TV, 2026-10-29).
 */
final class Dropdown {
    private Dropdown() {}

    static Screen create(Shell shell, String label, View anchor, View header, List<MenuPanel.Row> rows) {
        FrameLayout root = new FrameLayout(shell.stage.getContext());
        View catcher = new View(root.getContext());
        catcher.setOnClickListener(v -> shell.pop());
        root.addView(catcher, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout panel = MenuPanel.build(root.getContext(), header, rows, row -> {
            shell.pop();
            if (row.action != null) row.action.run();
        });
        int top = Ui.px(130);
        int right = Ui.px(Ui.SAFE_X);
        if (anchor != null && anchor.isAttachedToWindow()) {
            float[] r = Focus.rectIn(anchor, shell.stage);
            top = Math.round(r[3]) + Ui.px(14);
            right = Math.max(0, Math.round(shell.stage.getWidth() - r[2]));
        }
        root.addView(panel, MenuPanel.hang(top, right));
        return new Screen(label, root, Screen.Layer.DIALOG) {
            private boolean shown;

            @Override
            View defaultFocus() {
                for (View v : Focus.all(panel)) return v;
                return null;
            }

            @Override
            void onShow() {
                if (shown) return;
                shown = true;
                MenuPanel.dropIn(panel);
            }

            @Override
            boolean onKey(Key key, View focused) {
                SpatialNav.Dir dir = Shell.dirOf(key);
                if (dir == null) return false;
                if (shell.moveWithin(panel, dir)) return true;
                shell.pop();
                if (anchor != null && anchor.isAttachedToWindow() && anchor.isShown()) shell.focus(anchor);
                if (dir != SpatialNav.Dir.UP) shell.move(dir);
                return true;
            }
        };
    }
}
