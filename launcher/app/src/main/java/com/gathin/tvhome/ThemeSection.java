package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher settings' Theme section (Figma lwShqRaciMpDUDW5dYljNw, "Settings · Theme picker", frame 18:10): the
 * Classic and Seasonal swatches, then Typeface (Moving details moved to Performance as Animated details, the project
 * lead's decision of 2026-11-01). Every swatch is drawn from its own
 * theme (a Seasonal one in today's period), and the pane's chrome from Ui's tokens, so it is themed too.
 *
 * <p>The swatches are one grid with the option row under them, moved through by the page's spatial D-pad. Moving only
 * highlights; OK selects (the project lead's rule for segmented choices, applied to the swatches too). A new theme or
 * typeface re-applies and rebuilds the launcher through {@link Host#themeChanged()}.
 */
final class ThemeSection implements SettingsSection {
    private static final String SWATCH_GROUP = "theme-swatch";
    /** Swatch, 5 px padding and the 4 px selection ring around it (Figma "Option"). */
    private static final float OPTION_W = ThemeSwatch.W + 18, OPTION_H = 150, OPTION_GAP = 6, OPTION_RADIUS = 19;

    private View first;
    /** The two swatch rows, for DOWN from a Classic swatch past the end of the shorter Seasonal row. */
    private final List<View> classic = new ArrayList<>(), seasonal = new ArrayList<>();
    private Host host;

    @Override
    public String id() {
        return "theme";
    }

    @Override
    public int label() {
        return R.string.theme_section;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_theme;
    }

    @Override
    public CharSequence value(Context ctx) {
        String id = ThemeStore.themeId(ctx);
        LocalDate today = LocalDate.now();
        String name = name(ctx, id, today);
        SeasonCalendar.Period period = ThemeWords.shownPeriod(Themes.SEASONAL.contains(id), today);
        return period == null ? name
                : ctx.getString(R.string.theme_value_seasonal, name, ctx.getString(ThemeWords.periodRes(period)));
    }

    @Override
    public View firstFocus() {
        return first;
    }

    /**
     * DOWN from a Classic swatch with no Seasonal swatch under it lands on the nearest one (the row's last), rather
     * than skipping the Seasonal row for the option rows below.
     */
    @Override
    public boolean onKey(Screen.Key key, View focused) {
        if (key != Screen.Key.DOWN || seasonal.isEmpty() || host == null) return false;
        int i = classic.indexOf(focused);
        if (i < seasonal.size() || i < 0) return false;
        host.shell().focus(seasonal.get(seasonal.size() - 1));
        return true;
    }

    @Override
    public View pane(Context ctx, Host host) {
        this.host = host;
        first = null;
        classic.clear();
        seasonal.clear();
        String saved = ThemeStore.themeId(ctx);
        boolean systemFont = ThemeStore.systemFont(ctx);
        LocalDate today = LocalDate.now();

        LinearLayout pane = Kit.unclip(new LinearLayout(ctx));
        pane.setOrientation(LinearLayout.VERTICAL);

        TextView title = Ui.text(ctx, 40, Ui.TEXT_1, Ui.REGULAR);
        title.setText(R.string.theme_pane_title);
        pane.addView(title, wrap());
        TextView sub = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        sub.setText(R.string.theme_pane_sub);
        sub.setLineSpacing(0, 1.45f);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = Ui.px(8);
        pane.addView(sub, slp);

        List<View> swatches = new ArrayList<>();
        pane.addView(groupLabel(ctx, ctx.getString(R.string.theme_group_classic)), groupLabelParams());
        pane.addView(swatchRow(ctx, host, Themes.CLASSIC, saved, systemFont, today, swatches, classic), wrap());
        if (!Themes.SEASONAL.isEmpty()) {
            String period = ctx.getString(ThemeWords.periodRes(SeasonCalendar.at(today)));
            pane.addView(groupLabel(ctx, ctx.getString(R.string.theme_group_seasonal, period)), groupLabelParams());
            pane.addView(swatchRow(ctx, host, Themes.SEASONAL, saved, systemFont, today, swatches, seasonal), wrap());
        }
        if (first == null && !swatches.isEmpty()) first = swatches.get(0);

        LinearLayout options = Kit.unclip(new LinearLayout(ctx));
        options.setOrientation(LinearLayout.HORIZONTAL);
        options.setBaselineAligned(false);

        LinearLayout typeface = segmented(ctx, "theme-font",
                new String[]{"theme-font-theme", "theme-font-system"},
                new CharSequence[]{ctx.getString(R.string.theme_font_theme), ctx.getString(R.string.theme_font_system)},
                systemFont ? 1 : 0, (seg, i) -> {
                    boolean system = i == 1;
                    if (system == ThemeStore.systemFont(ctx)) return;
                    ThemeStore.setSystemFont(ctx, system);
                    select(seg, i);
                    host.themeChanged();
                });
        options.addView(optionRow(ctx, R.string.theme_typeface, R.string.theme_typeface_sub, typeface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));


        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = Ui.px(26);
        pane.addView(options, olp);
        return pane;
    }

    // ---- Swatches -------------------------------------------------------------------------------------------------

    private LinearLayout swatchRow(Context ctx, Host host, List<String> ids, String saved, boolean systemFont,
                                   LocalDate today, List<View> all, List<View> rowViews) {
        LinearLayout row = Kit.unclip(new LinearLayout(ctx));
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String id : ids) {
            Theme theme = Themes.resolve(id, today);
            boolean selected = id.equals(saved);
            View option = option(ctx, theme, name(ctx, id, today), selected, systemFont, id, v -> {
                if (id.equals(ThemeStore.themeId(ctx))) return;
                ThemeStore.setThemeId(ctx, id);
                for (View s : all) select(s, s == v);
                host.themeChanged();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(OPTION_W), Ui.px(OPTION_H));
            if (row.getChildCount() > 0) lp.leftMargin = Ui.px(OPTION_GAP);
            row.addView(option, lp);
            all.add(option);
            rowViews.add(option);
            if (selected) first = option;
        }
        return row;
    }

    /**
     * One choice: the swatch and its name. Selected is the 4 px accent ring round both and a semibold label (Figma
     * Inter Semi Bold, Roboto Medium here as in Kit); focused is the accent fill, as the settings page's sections and
     * segmented options are, with the cards' small scale-up.
     */
    private static View option(Context ctx, Theme theme, String label, boolean selected, boolean systemFont, String id,
                               View.OnClickListener onOk) {
        LinearLayout o = new LinearLayout(ctx);
        o.setOrientation(LinearLayout.VERTICAL);
        o.setGravity(Gravity.CENTER_HORIZONTAL);
        o.setPadding(0, Ui.px(9), 0, 0);
        o.setContentDescription(label);
        o.setBackground(Ui.states(Ui.round(Color.TRANSPARENT, OPTION_RADIUS),
                Ui.round(Color.TRANSPARENT, OPTION_RADIUS, Ui.ACCENT, 4),
                Ui.round(Ui.ACCENT, OPTION_RADIUS)));
        o.addView(new ThemeSwatch(ctx, theme, aaFace(theme, systemFont)),
                new LinearLayout.LayoutParams(Ui.px(ThemeSwatch.W), Ui.px(ThemeSwatch.H)));
        TextView name = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_2, Ui.REGULAR);
        name.setText(label);
        name.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_1, Ui.ON_ACCENT));
        name.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams nlp = wrap();
        nlp.topMargin = Ui.px(10);
        o.addView(name, nlp);
        o.setTag(R.id.kit_label, name);
        select(o, selected);
        Kit.reportsSelected(o);
        Focus.make(o, SWATCH_GROUP, "theme-" + id, selected, onOk);
        Ui.scaleOnFocus(o, 1.03f);
        return o;
    }

    /**
     * The face for a swatch's "Aa". Stand-in until the theme fonts track hands over a theme's display face once it is
     * cached on the TV (never fetched from here): Roboto Light, Figma's Inter Light, for every theme meanwhile and for
     * all of them when the System font option is on.
     */
    private static Typeface aaFace(Theme theme, boolean systemFont) {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    // ---- Typeface -------------------------------------------------------------------------------------------------

    /** A half-width row: its name and a line under it, then its segmented control (Figma "Row · Typeface"). */
    private static View optionRow(Context ctx, int label, int note, View control) {
        LinearLayout row = Kit.unclip(new LinearLayout(ctx));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(80));
        row.setPadding(Ui.px(28), Ui.px(12), Ui.px(24), Ui.px(12));
        row.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L));

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.MEDIUM);
        name.setText(label);
        text.addView(name, wrap());
        TextView sub = Ui.text(ctx, 19, Ui.TEXT_2, Ui.REGULAR);
        sub.setText(note);
        sub.setMaxLines(2);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = Ui.px(4);
        text.addView(sub, slp);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams clp = wrap();
        clp.leftMargin = Ui.px(16);
        row.addView(control, clp);
        return row;
    }

    private interface Choice {
        void chosen(LinearLayout segmented, int index);
    }

    /**
     * The small segmented control (screens.css .seg--small) in the pane's own stand-in, to swap for the settings
     * page's shared one (M9 track D1). The selected option has the accent at 14% with an accent ring and a semibold
     * accent label; focused is the accent fill, scale 1.05. Entering it lands on the selected option; OK selects.
     */
    private static LinearLayout segmented(Context ctx, String group, String[] keys, CharSequence[] labels, int selected,
                                          Choice onChoose) {
        LinearLayout seg = Kit.unclip(new LinearLayout(ctx));
        seg.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.px(4);
        seg.setPadding(pad, pad, pad, pad);
        seg.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, Ui.LINE_W));
        for (int i = 0; i < labels.length; i++) {
            TextView opt = Ui.line(ctx, 19, Ui.TEXT_2, Ui.REGULAR);
            opt.setText(labels[i]);
            opt.setGravity(Gravity.CENTER);
            opt.setPadding(Ui.px(18), 0, Ui.px(18), 0);
            opt.setTextColor(Ui.colors(Ui.TEXT_2, Ui.ACCENT, Ui.ON_ACCENT));
            opt.setBackground(Ui.states(Ui.round(Color.TRANSPARENT, Ui.RADIUS_PILL),
                    Ui.round(Ui.ACCENT_SOFT, Ui.RADIUS_PILL, Ui.ACCENT, Ui.LINE_W),
                    Ui.round(Ui.ACCENT, Ui.RADIUS_PILL)));
            opt.setTag(R.id.kit_label, opt);
            select(opt, i == selected);
            Kit.reportsSelected(opt);
            int index = i;
            Focus.make(opt, group, keys[i], i == selected, v -> onChoose.chosen(seg, index));
            opt.setOnFocusChangeListener((v, focused) -> weight((TextView) v, focused || v.isActivated()));
            Ui.scaleOnFocus(opt, 1.05f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.px(46));
            if (i > 0) lp.leftMargin = Ui.px(4);
            seg.addView(opt, lp);
        }
        return seg;
    }

    private static void select(LinearLayout seg, int index) {
        for (int i = 0; i < seg.getChildCount(); i++) select(seg.getChildAt(i), i == index);
    }

    /** Selected state on a swatch or a segmented option; its label goes semibold. */
    private static void select(View v, boolean selected) {
        v.setActivated(selected);
        Object label = v.getTag(R.id.kit_label);
        if (label instanceof TextView) weight((TextView) label, selected || v.isFocused());
    }

    private static void weight(TextView t, boolean strong) {
        t.setTypeface(strong ? Ui.MEDIUM : Ui.REGULAR);
    }

    // ---- Helpers --------------------------------------------------------------------------------------------------

    /** The theme's name from the pane's strings; a theme the pane has no string for shows its English name. */
    private static String name(Context ctx, String id, LocalDate day) {
        int res = ThemeWords.nameRes(id);
        return res != 0 ? ctx.getString(res) : Themes.resolve(id, day).name;
    }

    private static TextView groupLabel(Context ctx, CharSequence text) {
        TextView t = Ui.line(ctx, 19, Ui.TEXT_3, Ui.MEDIUM);
        t.setText(text);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    /** screens.css .grp-h: 26 above, 12 below, 4 in. */
    private static LinearLayout.LayoutParams groupLabelParams() {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = Ui.px(26);
        lp.bottomMargin = Ui.px(12);
        lp.leftMargin = Ui.px(4);
        return lp;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
