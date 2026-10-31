package com.gathin.tvhome;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home row apps in Launcher settings (design_preview/README.md, The other panes; src/screens/panes/apps.ts): Home's
 * row as small tiles in order ("On Home · 7"), then every other app. OK on an app on Home picks it up, as Home's
 * hold-OK Move does: it lifts with arrows on its sides, ← → move it, OK puts it down, ↓ takes it off Home (one must
 * stay) and Back puts it back where it was. OK on another app adds it at the end, already picked up to place. The
 * logic is {@link HomeRowEdit}; every change is saved at once ({@link HomeApps#save}) and Home's row follows when it
 * shows again. The row's value is the number of apps on it.
 */
final class HomeAppsSection implements SettingsSection {
    private static final String GROUP_HOME = "ha-home", GROUP_OTHER = "ha-other";
    /** Small tiles (screens.css .ha-tile, .ha-face) and how many share a line before the next one. */
    private static final float TILE_W = 140, TILE_H = 84, GAP = 14;
    private static final int PER_LINE = 6;

    private final HomeApps apps;
    private HomeRowEdit edit;
    private Context ctx;
    private Host host;
    private LinearLayout column;
    private View first;

    HomeAppsSection(HomeApps apps) {
        this.apps = apps;
    }

    @Override
    public String id() {
        return "apps";
    }

    @Override
    public int label() {
        return R.string.settings_home_apps;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_apps;
    }

    @Override
    public CharSequence value(Context ctx) {
        return String.valueOf(apps.visible().size());
    }

    @Override
    public View pane(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        // An app picked up stays picked up while another section is looked at, as the prototype's pane does.
        if (edit == null || edit.moving() == null) edit = new HomeRowEdit(apps.full(), apps::shown);
        column = PaneParts.column(ctx);
        fill();
        return PaneParts.scroller(ctx, column);
    }

    @Override
    public View firstFocus() {
        return first;
    }

    /** The page closed with an app picked up (the remote's Home): it stays where it was put, which is saved. */
    @Override
    public void onClose() {
        edit = null;
    }

    /** Back while an app is picked up puts the row back as it was. */
    @Override
    public boolean onBack() {
        if (edit == null || edit.moving() == null) return false;
        String pkg = edit.moving();
        edit.cancel();
        changed(pkg, true);
        return true;
    }

    @Override
    public boolean onKey(Screen.Key key, View focused) {
        if (edit == null || edit.moving() == null || key == Screen.Key.BACK) return false;
        String pkg = edit.moving();
        switch (key) {
            case LEFT:
            case RIGHT:
                if (edit.step(key == Screen.Key.LEFT ? -1 : 1)) changed(pkg, true);
                break;
            case OK:
                edit.putDown();
                changed(pkg, false);
                break;
            case DOWN:
                if (edit.takeOff()) changed(pkg, true);
                else host.shell().toast(ctx.getString(R.string.home_apps_keep_one));
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * Redraws the pane with focus on the app that changed and, when the row did, saves it (Home's row follows) and
     * tells the page its new count. Picking an app up alone saves nothing, so the dock_apps seed still applies.
     */
    private void changed(String pkg, boolean save) {
        if (save) apps.save(edit.full());
        fill();
        View target = Focus.byKey(column, key(pkg));
        if (target != null) host.shell().focus(target);
        if (save) host.valueChanged();
    }

    private void fill() {
        column.removeAllViews();
        List<String> on = edit.visible();
        column.addView(PaneParts.eyebrow(ctx, ctx.getString(R.string.home_apps_eyebrow)));
        column.addView(PaneParts.title(ctx, ctx.getString(R.string.settings_home_apps), 40), PaneParts.below(10));
        column.addView(PaneParts.sub(ctx, ctx.getString(R.string.home_apps_sub)), PaneParts.below(8));

        column.addView(PaneParts.groupHeader(ctx, ctx.getString(R.string.home_apps_on_home, on.size(), AppRow.MAX)),
                PaneParts.wide(26));
        List<View> home = new ArrayList<>();
        for (int i = 0; i < on.size(); i++) home.add(tile(on.get(i), true, i == 0));
        // → from the section returns to the app picked up, if one is.
        int lifted = edit.moving() == null ? -1 : on.indexOf(edit.moving());
        first = home.isEmpty() ? null : home.get(Math.max(0, lifted));
        column.addView(lines(home), PaneParts.wide(12));
        TextView hint = PaneParts.note(ctx, ctx.getString(edit.moving() != null ? R.string.home_apps_hint_moving
                : R.string.home_apps_hint), true);
        hint.setMaxWidth(Integer.MAX_VALUE);
        column.addView(hint, PaneParts.wide(14));

        column.addView(PaneParts.groupHeader(ctx, ctx.getString(R.string.home_apps_others)), PaneParts.wide(26));
        List<View> others = new ArrayList<>();
        for (HomeApps.App a : apps.all()) {
            if (!on.contains(a.pkg) && apps.shown(a.pkg)) others.add(tile(a.pkg, false, others.isEmpty()));
        }
        if (others.isEmpty()) {
            column.addView(PaneParts.note(ctx, ctx.getString(R.string.home_apps_all_on_home), false), PaneParts.wide(12));
        } else {
            column.addView(lines(others), PaneParts.wide(12));
        }
        if (first == null && !others.isEmpty()) first = others.get(0);
        column.addView(PaneParts.note(ctx, ctx.getString(R.string.home_apps_note), false), PaneParts.wide(22));
    }

    /** The tiles in lines of {@link #PER_LINE}, 14 px apart. */
    private LinearLayout lines(List<View> tiles) {
        LinearLayout box = PaneParts.column(ctx);
        LinearLayout line = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % PER_LINE == 0) {
                line = PaneParts.across(ctx);
                box.addView(line, PaneParts.below(i == 0 ? 0 : GAP));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = i % PER_LINE == 0 ? 0 : Ui.px(GAP - 2 * AppTile.RING);
            line.addView(tiles.get(i), lp);
        }
        return box;
    }

    private View tile(String pkg, boolean onHome, boolean isDefault) {
        AppTile t = new AppTile(ctx, pkg, apps.label(pkg), TILE_W, TILE_H, 8);
        Focus.make(t, onHome ? GROUP_HOME : GROUP_OTHER, key(pkg), isDefault, v -> {
            // OK reaches onKey first while an app is picked up; this is for a page that sends it here instead.
            if (edit.moving() != null) {
                String moving = edit.moving();
                edit.putDown();
                changed(moving, false);
            } else if (!onHome && edit.visible().size() >= AppRow.MAX) {
                host.shell().toast(ctx.getString(R.string.home_apps_full, AppRow.MAX));
            } else if (onHome ? edit.pickUp(pkg) : edit.add(pkg)) {
                changed(pkg, !onHome);
            }
        });
        if (pkg.equals(edit.moving())) t.setMoving(true);
        return t;
    }

    private static String key(String pkg) {
        return "ha-" + pkg;
    }
}
