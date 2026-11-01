package com.gathin.tvhome;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * All Apps, pushed from Home's Shortcuts (docs/rework.md, decided 2026-10-29): a plain working grid of every app with a
 * TV entry point, plus phone apps that have none, sorted by name. OK opens an app; hold-OK adds it to Home's row or
 * takes it off, or opens App info. Styled from the tokens until it is designed (BACKLOG.md, All Apps).
 *
 * Like Home's app row, the grid catches up when the page comes back into view (from App info, the Play Store or an
 * app): an app disabled, uninstalled or installed meanwhile leaves or joins it, and focus stays on the same app.
 */
final class AllAppsPage {
    private static final int COLUMNS = 6;
    private static final float TILE_W = 264;
    private static final float TILE_H = TILE_W * 9 / 16;
    private static final int COLUMN_GAP = 20;
    private static final int ROW_GAP = 28;
    private static final int GRID_TOP = 262;
    /** Room above the first row for the focused tile's ring and scale-up. */
    private static final int ROOM = 16;
    private static final String GROUP = "all-apps";
    private static final String KEY_PREFIX = "aa-";

    private final MainActivity activity;
    private final Shell shell;
    private final HomeApps apps;
    private final View back;
    private final TextView count;
    private final FrameLayout window;
    private final LinearLayout grid;
    private final Screen screen;
    private List<HomeApps.App> all = new ArrayList<>();

    static Screen create(MainActivity activity, Shell shell, HomeApps apps) {
        return new AllAppsPage(activity, shell, apps).screen;
    }

    private AllAppsPage(MainActivity activity, Shell shell, HomeApps apps) {
        this.activity = activity;
        this.shell = shell;
        this.apps = apps;
        FrameLayout page = StubPage.frame(activity);
        back = StubPage.backButton(activity, shell);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        blp.topMargin = Ui.px(60 - StubPage.ROOM);
        blp.leftMargin = Ui.px(Ui.SAFE_X - StubPage.ROOM);
        page.addView(StubPage.roomy(back, Ui.px(66)), blp);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setBaselineAligned(true);
        TextView title = Ui.line(activity, Ui.T_H1, Ui.TEXT_1, Ui.display(Ui.REGULAR));
        title.setText(R.string.all_apps);
        head.addView(title);
        count = Ui.line(activity, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.px(20);
        head.addView(count, clp);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        hlp.topMargin = Ui.px(150);
        hlp.leftMargin = Ui.px(Ui.SAFE_X);
        page.addView(head, hlp);

        // The grid scrolls by its translation inside this window, which the page clips at the screen's bottom edge. The
        // grid is measured at its full height, so rows below the window are laid out and reachable with the D-pad.
        window = new FrameLayout(activity) {
            @Override
            protected void measureChildWithMargins(View child, int widthSpec, int widthUsed, int heightSpec,
                                                   int heightUsed) {
                super.measureChildWithMargins(child, widthSpec, widthUsed,
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightUsed);
            }
        };
        window.setClipChildren(false);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.px(Ui.STAGE_H - GRID_TOP + ROOM), Gravity.TOP);
        wlp.topMargin = Ui.px(GRID_TOP - ROOM);
        page.addView(window, wlp);
        grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, Ui.px(ROOM), 0, Ui.px(Ui.SAFE_Y));
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        glp.leftMargin = Ui.px(Ui.SAFE_X - AppTile.RING);
        window.addView(grid, glp);
        fill(apps.all());

        // Told when the TV comes back from another app's window; the first call is the page being pushed.
        page.addView(new View(activity) {
            private boolean seen;

            @Override
            protected void onWindowVisibilityChanged(int visibility) {
                super.onWindowVisibilityChanged(visibility);
                if (visibility != VISIBLE || !isAttachedToWindow()) return;
                if (seen) refresh();
                seen = true;
            }
        }, new FrameLayout.LayoutParams(0, 0));

        screen = new Screen(activity.getString(R.string.all_apps), page, Screen.Layer.PAGE) {
            @Override
            View defaultFocus() {
                View first = all.isEmpty() ? null : Focus.byKey(grid, KEY_PREFIX + all.get(0).pkg);
                return first != null ? first : back;
            }

            @Override
            void onFocus(View v) {
                reveal(v);
            }

            @Override
            boolean onLongOk(View focused) {
                return menu(focused);
            }
        };
    }

    /** Lays the grid out for these apps, and the count beside the title. */
    private void fill(List<HomeApps.App> list) {
        all = list;
        count.setText(activity.getResources().getQuantityString(R.plurals.all_apps_count, list.size(), list.size()));
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            HomeApps.App app = list.get(i);
            if (i % COLUMNS == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = Ui.px(ROW_GAP);
                grid.addView(row, rlp);
            }
            AppTile t = new AppTile(activity, app.pkg, app.label, TILE_W, TILE_H, 12);
            Focus.make(t, GROUP, KEY_PREFIX + app.pkg, i == 0, v -> open(app.pkg));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % COLUMNS > 0) tlp.leftMargin = Ui.px(COLUMN_GAP - 2 * AppTile.RING);
            row.addView(t, tlp);
        }
    }

    /**
     * Reads the apps again and rebuilds the grid when they changed. Focus stays on the app it was on, or, when that
     * app has gone, moves to the one that took its place.
     */
    private void refresh() {
        List<HomeApps.App> next = apps.all();
        if (same(next, all)) return;
        View focused = shell.focused();
        String pkg = focused instanceof AppTile && Focus.isInside(grid, focused) ? ((AppTile) focused).pkg : null;
        int at = -1;
        for (int i = 0; pkg != null && i < all.size(); i++) if (all.get(i).pkg.equals(pkg)) at = i;
        fill(next);
        if (shell.top() != screen) return;
        if (pkg == null) {
            shell.refocus();
            return;
        }
        View target = Focus.byKey(grid, KEY_PREFIX + pkg);
        if (target == null && !next.isEmpty() && at >= 0) {
            target = Focus.byKey(grid, KEY_PREFIX + next.get(Math.min(at, next.size() - 1)).pkg);
        }
        shell.focus(target != null ? target : back);
    }

    private static boolean same(List<HomeApps.App> a, List<HomeApps.App> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).pkg.equals(b.get(i).pkg) || !a.get(i).label.equals(b.get(i).label)) return false;
        }
        return true;
    }

    /** Slides the grid so the focused tile's row is inside the window. */
    private void reveal(View v) {
        if (!(v instanceof AppTile) || !(v.getParent() instanceof View)) return;
        View r = (View) v.getParent();
        Runnable reveal = () -> {
            if (r.getParent() != grid) return;
            float target = Reveal.offset(grid.getTranslationY(), grid.getTop() + r.getTop() + v.getTop(),
                    grid.getTop() + r.getTop() + v.getBottom(), Ui.px(ROOM), window.getHeight() - Ui.px(Ui.SAFE_Y));
            if (target == grid.getTranslationY()) return;
            grid.animate().translationY(target).setDuration(Ui.ms(240)).setInterpolator(Ui.EASE).start();
        };
        if (grid.isLaidOut() && !grid.isLayoutRequested()) reveal.run();
        else grid.post(reveal);
    }

    private boolean menu(View focused) {
        if (!(focused instanceof AppTile)) return false;
        AppTile tile = (AppTile) focused;
        String pkg = tile.pkg;
        List<MenuPanel.Row> rows = new ArrayList<>();
        if (apps.onRow(pkg)) {
            rows.add(new MenuPanel.Row(activity.getString(R.string.library_remove_shortcut), 0, () -> {
                apps.save(AppRow.remove(apps.full(), pkg));
                shell.toast(activity.getString(R.string.home_removed));
            }));
        } else {
            rows.add(new MenuPanel.Row(activity.getString(R.string.tile_add_to_home), 0, () -> {
                if (!AppRow.hasRoom(apps.full(), apps::shown)) {
                    shell.toast(activity.getString(R.string.home_apps_full, AppRow.MAX));
                    return;
                }
                apps.save(AppRow.add(apps.full(), pkg));
                shell.toast(activity.getString(R.string.home_added));
            }));
        }
        Intent info = SystemSettings.appInfo(activity, pkg);
        if (info != null) {
            rows.add(new MenuPanel.Row(activity.getString(R.string.library_app_info), 0, () -> activity.start(info)));
        }
        shell.push(TileMenu.create(shell, tile, rows));
        return true;
    }

    private void open(String pkg) {
        Intent i = apps.launchIntent(pkg);
        if (i == null) {
            shell.toast(activity.getString(R.string.not_available));
            return;
        }
        activity.start(i);
    }
}
