package com.hometv.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Launcher settings' About section (design_preview/src/screens/panes/misc.ts aboutPane): the launcher's version, and
 * the notices the data's terms ask for. TMDB's, wherever its data shows: only in builds with a TMDB token, as are
 * Where to watch's sources (JustWatch through TMDB, Apple's prices, and any keyed price source). Open-Meteo's credit
 * for the weather always. Then Follow theme (Config.followTheme), which hands Motion and Open on start back to the
 * config.
 */
final class AboutSection implements SettingsSection {
    private View follow;

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
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public View pane(Context ctx, Host host) {
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_about)));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.about_title), false), 10);
        SettingsKit.addWide(pane, kv(ctx, ctx.getString(R.string.about_version),
                ctx.getString(R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)), 18);
        if (!ApiKeys.get(ctx).key(KeyRules.Id.TMDB).isEmpty()) {
            SettingsKit.addWide(pane, tmdbBox(ctx), 32);
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
        return follow;
    }

    /**
     * {@code .tmdb}: TMDB's approved logo (Primary short, R.drawable.tmdb_logo) beside their notice, in a framed box.
     * Their terms ask for the logo to be less prominent than the app's own mark: it is 18 px tall, under the pane's
     * 40 px title.
     */
    private static View tmdbBox(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.px(30), Ui.px(28), Ui.px(30), Ui.px(28));
        box.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W));
        ImageView logo = new ImageView(ctx);
        logo.setImageResource(R.drawable.tmdb_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription(ctx.getString(R.string.tmdb_logo));
        float h = 18;
        box.addView(logo, new LinearLayout.LayoutParams(Ui.px(h * 273.42f / 35.52f), Ui.px(h)));
        TextView notice = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        notice.setText(R.string.tmdb_notice);
        notice.setLineSpacing(0f, 1.5f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = Ui.px(30);
        box.addView(notice, nlp);
        return box;
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
