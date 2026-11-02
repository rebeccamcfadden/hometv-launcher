package com.hometv.launcher;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Launcher settings' About section (design_preview/src/screens/panes/misc.ts aboutPane): the launcher's version, and
 * the notices the data's terms ask for. TMDB's, wherever its data shows: only in builds with a TMDB token, as are
 * Where to watch's sources (JustWatch through TMDB, Apple's prices, and any keyed price source). Open-Meteo's credit
 * for the weather always. Then Follow theme (Config.followTheme), which hands Motion and Open on start back to the
 * config.
 *
 * <p>Updates (Family install step 5; design_preview aboutPane): where an in-app update stands, Check for updates (on to
 * start) and Install update, which downloads only then and hands the APK to Android's own prompt ({@link Updates}). A
 * build from a computer says it updates from there, and has no check.
 */
final class AboutSection implements SettingsSection {
    private View follow;
    private View firstAction;

    @Override
    public String id() {
        return "about";
    }

    @Override
    public int label() {
        return R.string.settings_about;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_info;
    }

    @Override
    public CharSequence value(Context ctx) {
        UpdateRules.Release r = Updates.get(ctx).available();
        if (r != null && Updates.get(ctx).checkOn() && Updates.enabled()) return ctx.getString(R.string.updates_value, r.versionName);
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public View pane(Context ctx, Host host) {
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_about)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.about_title), false), 10);
        SettingsKit.addWide(pane, kv(ctx, ctx.getString(R.string.about_version),
                ctx.getString(R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)), 18);
        firstAction = null;
        addUpdates(ctx, host, pane);
        if (!ApiKeys.get(ctx).key(KeyRules.Id.TMDB).isEmpty()) {
            TextView tmdb = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
            tmdb.setText(R.string.tmdb_notice);
            tmdb.setLineSpacing(0f, 1.5f);
            tmdb.setPadding(Ui.px(30), Ui.px(28), Ui.px(30), Ui.px(28));
            tmdb.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W));
            SettingsKit.addWide(pane, tmdb, 32);
            StringBuilder prices = new StringBuilder(ctx.getString(R.string.prices_notice_justwatch))
                    .append('\n').append(ctx.getString(R.string.prices_notice_itunes));
            if (!PriceKeys.watchmode(ctx).isEmpty()) prices.append('\n').append(ctx.getString(R.string.prices_notice_watchmode));
            if (!PriceKeys.movieOfTheNight(ctx).isEmpty()) prices.append('\n').append(ctx.getString(R.string.prices_notice_motn));
            SettingsKit.add(pane, SettingsKit.note(ctx, prices), 22);
        }
        SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.about_open_meteo)), 22);
        follow = SettingsKit.button(ctx, ctx.getString(R.string.follow_theme), 0, false, "about", "follow-theme", true,
                v -> {
                    boolean dropped = Config.followTheme(ctx);
                    host.shell().toast(ctx.getString(dropped ? R.string.follow_theme_done : R.string.follow_theme_nothing));
                    if (dropped) host.valueChanged();
                });
        SettingsKit.add(pane, SettingsKit.actions(ctx, follow), 36);
        SettingsKit.add(pane, SettingsKit.note(ctx, ctx.getString(R.string.follow_theme_hint)), 12);
        return pane;
    }

    @Override
    public View firstFocus() {
        return firstAction != null ? firstAction : follow;
    }

    /** The Updates block, rebuilt in place whenever Updates changes while the pane shows. */
    private void addUpdates(Context ctx, Host host, LinearLayout pane) {
        LinearLayout block = SettingsKit.column(ctx);
        SettingsKit.addWide(pane, block, 26);
        Updates updates = Updates.get(ctx);
        Runnable render = () -> renderUpdates(ctx, host, block, updates);
        // Android's "install unknown apps" screen sends no event when it is switched on: back in front, read it again.
        android.view.ViewTreeObserver.OnWindowFocusChangeListener back = hasFocus -> {
            if (hasFocus) render.run();
        };
        block.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                updates.listen(render);
                v.getViewTreeObserver().addOnWindowFocusChangeListener(back);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                updates.unlisten(render);
                v.getViewTreeObserver().removeOnWindowFocusChangeListener(back);
            }
        });
        renderUpdates(ctx, host, block, updates);
    }

    private void renderUpdates(Context ctx, Host host, LinearLayout block, Updates updates) {
        // Focus lives in the launcher's own focus layer (Shell), not Android's: read it and hand it back there.
        View was = host.shell().focused();
        boolean hadFocus = was != null && Focus.isInside(block, was);
        String wasKey = hadFocus ? Focus.key(was) : null;
        block.removeAllViews();
        TextView head = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.MEDIUM);
        head.setText(R.string.updates_title);
        block.addView(head);
        UpdateRules.Status s = updates.status();
        UpdateRules.Release r = updates.available();
        String version = r != null ? r.versionName : "";
        CharSequence line;
        switch (s) {
            case OWN: line = ctx.getString(R.string.updates_own); break;
            case OFF: line = ctx.getString(R.string.updates_off); break;
            case AVAILABLE:
                String rejected = updates.installError();
                line = rejected != null ? ctx.getString(R.string.updates_rejected, version, rejected)
                        : ctx.getString(R.string.updates_available, version);
                break;
            case DOWNLOADING: line = ctx.getString(R.string.updates_downloading, version); break;
            case NO_GRANT: line = ctx.getString(R.string.updates_no_grant, version); break;
            case NO_SCREEN: line = ctx.getString(R.string.updates_no_screen, version); break;
            case FAILED: line = ctx.getString(R.string.updates_failed); break;
            default:
                long last = updates.lastCheck();
                line = updates.checking() ? ctx.getString(R.string.updates_checking) : last <= 0
                        ? ctx.getString(R.string.updates_never)
                        : ctx.getString(R.string.updates_current, DateUtils.getRelativeTimeSpanString(last));
        }
        TextView note = SettingsKit.note(ctx, line);
        if (s == UpdateRules.Status.NO_GRANT || s == UpdateRules.Status.NO_SCREEN) note.setTextColor(Ui.STAR);
        SettingsKit.add(block, note, 8);
        if (s == UpdateRules.Status.OWN) return;
        View toggle = SettingsKit.toggleRow(ctx, ctx.getString(R.string.updates_check), ctx.getString(R.string.updates_check_sub),
                updates.checkOn(), "about", "update-check", false, on -> {
                    updates.setCheckOn(on);
                });
        SettingsKit.addWide(block, toggle, 12);
        View install = null, check = null;
        if (s == UpdateRules.Status.AVAILABLE || s == UpdateRules.Status.NO_GRANT || s == UpdateRules.Status.DOWNLOADING) {
            // Kept while downloading (it does nothing then), so focus stays on it and a second OK can't land elsewhere.
            install = SettingsKit.button(ctx, ctx.getString(R.string.updates_install), R.drawable.ic_ui_sync, true, "about",
                    "update-install", false, v -> updates.install(ctx, ok -> {
                        if (!ok) host.shell().toast(ctx.getString(R.string.updates_install_failed));
                    }));
        }
        if (s == UpdateRules.Status.CURRENT || s == UpdateRules.Status.FAILED) {
            check = SettingsKit.button(ctx, ctx.getString(R.string.updates_check_now), 0, false, "about", "update-check-now",
                    false, v -> updates.checkNow());
        }
        if (install != null || check != null) {
            View[] buttons = install != null ? (check != null ? new View[] {install, check} : new View[] {install}) : new View[] {check};
            SettingsKit.add(block, SettingsKit.actions(ctx, buttons), 16);
        }
        firstAction = install;
        // Focus stays on the control the person was on, so a second OK never lands on another (the switch above all);
        // only a control that went away hands it to the first one left.
        if (hadFocus) {
            View back = null;
            if ("update-install".equals(wasKey)) back = install;
            else if ("update-check-now".equals(wasKey)) back = check;
            else if ("update-check".equals(wasKey)) back = toggle;
            if (back == null) back = install != null ? install : check != null ? check : toggle;
            host.shell().focus(back);
        }
    }

    /** {@code .kv}: a name and its value across the pane, over a line (22 px). */
    private static View kv(Context ctx, CharSequence key, CharSequence value) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, Ui.px(18), 0, Ui.px(18));
        TextView k = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        k.setText(key);
        k.setGravity(Gravity.START);
        line.addView(k, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Ui.line(ctx, 22, Ui.TEXT_1, Ui.MEDIUM);
        v.setText(value);
        line.addView(v);
        row.addView(line);
        row.addView(SettingsKit.divider(ctx));
        return row;
    }
}
