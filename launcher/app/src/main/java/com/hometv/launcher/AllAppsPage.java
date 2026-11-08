package com.hometv.launcher;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * All Apps, pushed from Home's Shortcuts (docs/rework.md, decided 2026-10-29): a plain working grid of every app with a
 * TV entry point, plus phone apps that have none, sorted by name. OK opens an app; hold-OK adds it to Home's row or
 * takes it off, moves it into or out of TV Utilities, or opens App info. Styled from the tokens until it is designed
 * (BACKLOG.md, From M2).
 *
 * F35 (b, Rebecca 2026-11-02: on for every TV, no switch): the grid ends with a TV Utilities folder tile holding the
 * apps the TV came with that aren't watch apps (TvUtilities); OK opens it as this same page with only those apps, and
 * Back returns to the tile. It hides when it is empty (design_preview/src/screens/allApps.ts).
 *
 * Like Home's app row, the grid catches up when the page comes back into view (from App info, the Play Store or an
 * app, or from the folder): an app disabled, uninstalled or installed meanwhile leaves or joins it, and focus stays on
 * the same app.
 */
final class AllAppsPage {
    private static final int COLUMNS = 6;
    private static final float TILE_W = 264;
    private static final float TILE_H = TILE_W * 9 / 16;
    private static final int COLUMN_GAP = 20;
    private static final int ROW_GAP = 28;
    private static final int GRID_TOP = 262;
    /** The folder page has a line under its title, so its grid starts lower. */
    private static final int FOLDER_GRID_TOP = 300;
    /** Room above the first row for the focused tile's ring and scale-up. */
    private static final int ROOM = 16;
    private static final String GROUP = "all-apps";
    private static final String KEY_PREFIX = "aa-";
    private static final String FOLDER_KEY = "aa-folder";

    private final MainActivity activity;
    private final Shell shell;
    private final HomeApps apps;
    /** This page is the TV Utilities folder, not All Apps. */
    private final boolean folder;
    private final View back;
    private final TextView count;
    private final FrameLayout window;
    private final LinearLayout grid;
    private final Screen screen;
    /** The apps on this page, in order: the folder's, or All Apps' without them. */
    private List<HomeApps.App> shown = new ArrayList<>();
    /** What the grid was built from (TvUtilities' choice included), to tell when it needs building again. */
    private String built = "";
    /** AppArt's version the grid's tiles were built with: an app updated since has new art to show. */
    private int artBuilt;

    static Screen create(MainActivity activity, Shell shell, HomeApps apps) {
        return new AllAppsPage(activity, shell, apps, false).screen;
    }

    private AllAppsPage(MainActivity activity, Shell shell, HomeApps apps, boolean folder) {
        this.activity = activity;
        this.shell = shell;
        this.apps = apps;
        this.folder = folder;
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
        title.setText(folder ? R.string.tv_utilities : R.string.all_apps);
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
        if (folder) {
            TextView note = Ui.line(activity, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
            note.setText(R.string.tv_utilities_note);
            FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            nlp.topMargin = Ui.px(218);
            nlp.leftMargin = Ui.px(Ui.SAFE_X);
            page.addView(note, nlp);
        }

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
        int top = folder ? FOLDER_GRID_TOP : GRID_TOP;
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.px(Ui.STAGE_H - top + ROOM), Gravity.TOP);
        wlp.topMargin = Ui.px(top - ROOM);
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

        screen = new Screen(activity.getString(folder ? R.string.tv_utilities : R.string.all_apps), page,
                Screen.Layer.PAGE) {
            @Override
            View defaultFocus() {
                List<View> tiles = Focus.all(grid);
                return tiles.isEmpty() ? back : tiles.get(0);
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

    /** Lays the grid out for these apps (every app the launcher can open), and the count beside the title. */
    private void fill(List<HomeApps.App> every) {
        Set<String> utilities = apps.utilities(every);
        List<HomeApps.App> inside = new ArrayList<>();
        List<HomeApps.App> outside = new ArrayList<>();
        for (HomeApps.App a : every) (utilities.contains(a.pkg) ? inside : outside).add(a);
        shown = folder ? inside : outside;
        built = signature(every, utilities);
        artBuilt = AppArt.version();
        int n = folder ? inside.size() : every.size();
        count.setText(activity.getResources().getQuantityString(R.plurals.all_apps_count, n, n));
        grid.removeAllViews();
        int at = 0;
        for (HomeApps.App app : shown) {
            AppTile t = new AppTile(activity, app.pkg, app.label, TILE_W, TILE_H, 12);
            Focus.make(t, GROUP, KEY_PREFIX + app.pkg, at == 0, v -> open(app.pkg));
            place(t, at++);
        }
        // Last, after the apps (Rebecca, F35 Q2).
        if (!folder && !inside.isEmpty()) {
            FolderTile t = new FolderTile(activity, inside.size(), TILE_W, TILE_H, 12);
            Focus.make(t, GROUP, FOLDER_KEY, at == 0, v -> openFolder());
            place(t, at);
        }
    }

    /** Puts a tile at this place in the grid, starting a new row every {@link #COLUMNS}. */
    private void place(View tile, int i) {
        LinearLayout row;
        if (i % COLUMNS == 0) {
            row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = Ui.px(ROW_GAP);
            grid.addView(row, rlp);
        } else {
            row = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (i % COLUMNS > 0) tlp.leftMargin = Ui.px(COLUMN_GAP - 2 * AppTile.RING);
        row.addView(tile, tlp);
    }

    private static String signature(List<HomeApps.App> every, Set<String> utilities) {
        StringBuilder b = new StringBuilder();
        for (HomeApps.App a : every) {
            b.append(a.pkg).append('\t').append(a.label).append('\t').append(utilities.contains(a.pkg)).append('\n');
        }
        return b.toString();
    }

    private void openFolder() {
        shell.push(new AllAppsPage(activity, shell, apps, true).screen);
    }

    /**
     * Reads the apps again and rebuilds the grid when they changed. Focus stays on the app (or the folder) it was on,
     * or, when that has gone, moves to the app that took its place. An emptied folder page closes.
     */
    private void refresh() {
        List<HomeApps.App> next = apps.all();
        if (signature(next, apps.utilities(next)).equals(built) && artBuilt == AppArt.version()) return;
        View focused = shell.focused();
        String key = Focus.isInside(grid, focused) ? Focus.key(focused) : null;
        rebuild(next, key, null);
    }

    /**
     * Builds the grid from these apps and focuses the view with focusKey, else the one keyed {@code was} if it is
     * still here, else the app that took its place.
     */
    private void rebuild(List<HomeApps.App> every, String was, String focusKey) {
        int at = -1;
        for (int i = 0; was != null && i < shown.size(); i++) if (was.equals(KEY_PREFIX + shown.get(i).pkg)) at = i;
        fill(every);
        if (shell.top() != screen) return;
        if (folder && shown.isEmpty()) {
            shell.pop();
            return;
        }
        if (was == null && focusKey == null) {
            shell.refocus();
            return;
        }
        View target = Focus.byKey(grid, focusKey);
        if (target == null) target = Focus.byKey(grid, was);
        if (target == null && at >= 0 && !shown.isEmpty()) {
            target = Focus.byKey(grid, KEY_PREFIX + shown.get(Math.min(at, shown.size() - 1)).pkg);
        }
        shell.focus(target != null ? target : screen.defaultFocus());
    }

    /** Slides the grid so the focused tile's row is inside the window. */
    private void reveal(View v) {
        if (!Focus.isInside(grid, v) || !(v.getParent() instanceof View)) return;
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

    private HomeApps.App appOf(String pkg) {
        for (HomeApps.App a : shown) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private boolean menu(View focused) {
        if (!(focused instanceof AppTile)) return false;
        AppTile tile = (AppTile) focused;
        String pkg = tile.pkg;
        HomeApps.App app = appOf(pkg);
        if (app == null) return false;
        String key = KEY_PREFIX + pkg;
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
        rows.add(new MenuPanel.Row(activity.getString(folder ? R.string.tv_utilities_move_out
                : R.string.tv_utilities_move_in), 0, () -> {
            apps.moveUtility(app, !folder);
            shell.toast(activity.getString(folder ? R.string.tv_utilities_moved_out : R.string.tv_utilities_moved_in,
                    app.label));
            rebuild(apps.all(), key, folder ? null : FOLDER_KEY);
        }));
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
