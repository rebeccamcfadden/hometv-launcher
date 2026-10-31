package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher settings (design_preview/src/screens/settings.ts, V2 design 05-settings A with the round-2 panes): a full
 * page without the nav, pushed over whatever is showing. The title and "Back closes · changes save at once"; the
 * sections on the left, 470 px wide, each with its icon, label and current value in grey; the focused section's pane
 * on the right.
 *
 * <p>Landing on a section shows its pane at once, as a preview. → or OK enters the pane on its first control and the
 * pane's border brightens; the section stays lit (white at 10%, semibold) while its pane has focus. ← from the pane's
 * left edge returns to the section; ↑ and ↓ on the list stay on it. Back leaves a sub-state of the pane first (the
 * section's onBack), then the pane, then closes the page. The rules are in {@link SettingsNav}.
 *
 * <p>Every choice is saved at once and moves Launcher settings' change count; Home is rebuilt with them when the
 * page closes (MainActivity#closeLauncherSettings). A theme change rebuilds at once, and MainActivity reopens the page
 * on the same section ({@link #section()}).
 *
 * <p>First run opens the same page cut down to its steps ({@link #setup}, V2 design r2-setup-checklist): its own
 * title, hint and row labels, a footer under the pane, and Back on the list returning to the checklist. What differs
 * is all in {@link Setup}; null for Launcher settings itself.
 */
final class LauncherSettingsPage extends Screen implements SettingsSection.Host {
    /** First run's cut-down split (FirstRunSplit): what it changes on this page. */
    interface Setup {
        CharSequence title();

        CharSequence hint();

        /** The row's label ("Sign in to TMDB" for Account). */
        CharSequence label(SettingsSection s);

        /** The row's value, given the section's own ("Optional" for "Not signed in"). */
        CharSequence value(SettingsSection s, CharSequence value);

        /** The footer under the pane for the section showing; rebuilt each time another section shows. */
        View foot(String section);

        /** A section's pane was entered: the checklist ticks its row. */
        void entered(String section);

        /** Back on the list: return to the checklist. */
        void close();
    }

    static final String GROUP_SECTIONS = "sections";
    private static final float TOP = 160, LIST_LEFT = 104, LIST_W = 470, ROW_H = 70, ROW_GAP = 4;
    private static final float PANE_LEFT = 630, PANE_PAD_X = 52, PANE_PAD_Y = 44;
    /** Room inside the pane's scroll area for a focused control's ring and scale-up (.pane-scroll). */
    private static final float PANE_ROOM = 10;
    /** First run's footer (.pane-foot): a 24 px gap above its row of 56 px buttons (Kit.SMALL), 80 px in all. */
    private static final float FOOT_GAP = 24, FOOT_ROW = 56, FOOT_H = FOOT_GAP + FOOT_ROW;

    private final MainActivity activity;
    private final Context ctx;
    private final List<SettingsSection> sections;
    private final List<View> rows = new ArrayList<>();
    private final LinearLayout list;
    private final FrameLayout pane;
    private final ViewGroup paneScroll;
    private final FrameLayout paneHolder;
    /** First run's hooks and the footer they fill; both null on Launcher settings. */
    private final Setup setup;
    private final FrameLayout foot;
    private String current;
    private String shown;
    private boolean inPane;
    /** Opened straight into a pane (a quick-settings row, or back after a theme change rebuilt the launcher). */
    private boolean enterFirst;
    private boolean showedOnce;

    static LauncherSettingsPage create(MainActivity activity, String section, boolean enter) {
        return new LauncherSettingsPage(activity, StubPage.frame(activity), SettingsSections.all(activity), section,
                enter, null);
    }

    /** First run's split: only these sections, opened straight into section's pane, with setup's changes. */
    static LauncherSettingsPage setup(MainActivity activity, List<SettingsSection> sections, String section,
                                      Setup setup) {
        return new LauncherSettingsPage(activity, StubPage.frame(activity), sections, section, true, setup);
    }

    private LauncherSettingsPage(MainActivity activity, FrameLayout root, List<SettingsSection> sections,
                                 String section, boolean enter, Setup setup) {
        super(setup != null ? setup.title().toString() : activity.getString(R.string.launcher_settings), root,
                Layer.PAGE);
        this.activity = activity;
        this.ctx = activity;
        this.sections = sections;
        this.setup = setup;
        List<String> ids = new ArrayList<>();
        for (SettingsSection s : sections) ids.add(s.id());
        current = SettingsNav.initial(section, ids);
        enterFirst = enter;

        // ---- The title and the hint.
        FrameLayout head = new FrameLayout(ctx);
        TextView title = Ui.line(ctx, Ui.T_H1, Ui.TEXT_1, Ui.DISPLAY);
        if (setup != null) title.setText(setup.title());
        else title.setText(R.string.launcher_settings);
        head.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
        TextView hint = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        if (setup != null) hint.setText(setup.hint());
        else hint.setText(R.string.settings_hint);
        head.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        hlp.topMargin = Ui.px(Ui.SAFE_Y);
        hlp.leftMargin = Ui.px(Ui.SAFE_X);
        hlp.rightMargin = Ui.px(Ui.SAFE_X);
        root.addView(head, hlp);

        // ---- The sections: 8 px in from the safe margin, so a focused row's scale-up stays inside it.
        list = Kit.unclip(new LinearLayout(ctx));
        list.setOrientation(LinearLayout.VERTICAL);
        for (SettingsSection s : sections) {
            View row = row(s);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.px(ROW_H));
            if (!rows.isEmpty()) lp.topMargin = Ui.px(ROW_GAP);
            list.addView(row, lp);
            rows.add(row);
        }
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(Ui.px(LIST_W),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        llp.topMargin = Ui.px(TOP);
        llp.leftMargin = Ui.px(LIST_LEFT);
        root.addView(list, llp);

        // ---- The pane: its content scrolls inside the rounded frame, with room kept for focus rings.
        pane = new FrameLayout(ctx);
        int room = Ui.px(PANE_ROOM);
        pane.setPadding(Ui.px(PANE_PAD_X) - room, Ui.px(PANE_PAD_Y) - room, Ui.px(PANE_PAD_X) - room,
                Ui.px(PANE_PAD_Y) - room);
        paneScroll = ScrollBox.vertical(ctx, 40);
        paneScroll.setPadding(room, room, room, room);
        paneHolder = Kit.unclip(new FrameLayout(ctx));
        paneScroll.addView(paneHolder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        // First run: the footer sits under the scrolling content (.pane-foot), exactly FOOT_H tall and flush with the
        // scroll's bottom. The scroll's own room and the footer's top padding make the FOOT_GAP above the buttons;
        // the room left at the footer's sides and bottom keeps a focused button's ring inside the footer.
        if (setup != null) {
            foot = Kit.unclip(new FrameLayout(ctx));
            foot.setPadding(room, Ui.px(FOOT_GAP) - room, room, room);
            slp.bottomMargin = Ui.px(FOOT_H);
            pane.addView(foot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(FOOT_H),
                    Gravity.BOTTOM));
        } else {
            foot = null;
        }
        pane.addView(paneScroll, 0, slp);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        plp.topMargin = Ui.px(TOP);
        plp.leftMargin = Ui.px(PANE_LEFT);
        plp.rightMargin = Ui.px(Ui.SAFE_X);
        plp.bottomMargin = Ui.px(Ui.SAFE_Y);
        root.addView(pane, plp);

        paintPane();
        paintRows();
        paintValues();
        show(current);
    }

    /** {@code .sec}: 70 px, the 28 px icon, the label (24 px) and the value right-aligned (18 px, tertiary). */
    private View row(SettingsSection s) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        row.setBackground(Ui.states(Ui.round(0, Ui.RADIUS_L), Ui.round(Ui.SURFACE_2, Ui.RADIUS_L),
                Ui.round(Ui.ACCENT, Ui.RADIUS_L)));
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(s.icon());
        icon.setImageTintList(Ui.colors(Ui.TEXT_2, Ui.TEXT_1, Ui.ON_ACCENT));
        icon.setDuplicateParentStateEnabled(true);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
        TextView label = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        if (setup != null) label.setText(setup.label(s));
        else label.setText(s.label());
        label.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_1, Ui.ON_ACCENT));
        label.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.px(18);
        row.addView(label, lp);
        TextView value = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        value.setTextColor(Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT));
        value.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = Ui.px(12);
        row.addView(value, vlp);
        row.setTag(R.id.settings_row_title, label);
        row.setTag(R.id.settings_row_value, value);
        row.setTag(R.id.settings_section, s.id());
        Kit.reportsSelected(row);
        Focus.make(row, GROUP_SECTIONS, "sec-" + s.id(), s.id().equals(current), null);
        row.setOnFocusChangeListener((v, focused) -> paintRows());
        Ui.scaleOnFocus(row, 1.03f);
        return row;
    }

    // ---- What the page shows -------------------------------------------------------------------------------------

    /** The section's pane, built afresh when another section is shown (or force, to read its choices again). */
    private void show(String id, boolean force) {
        current = id;
        if (!force && id.equals(shown)) return;
        shown = id;
        paneHolder.removeAllViews();
        SettingsSection s = section(id);
        if (s == null) return;
        View v = s.pane(ctx, this);
        paneHolder.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        paneScroll.scrollTo(0, 0);
        if (foot != null) {
            foot.removeAllViews();
            View f = setup.foot(id);
            if (f != null) foot.addView(f, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void show(String id) {
        show(id, false);
    }

    /**
     * Focuses the section's first control, after showing its pane; stays on the section when there is none. Also
     * First run's Next (FirstRunSplit), which enters the next step's pane.
     */
    void enter(String id) {
        show(id);
        if (setup != null) setup.entered(id);
        SettingsSection s = section(id);
        View first = s == null ? null : s.firstFocus();
        if (first != null) shell().focus(first);
    }

    private void paintValues() {
        for (View row : rows) {
            SettingsSection s = section((String) row.getTag(R.id.settings_section));
            CharSequence value = s == null ? "" : s.value(ctx);
            if (setup != null && s != null) value = setup.value(s, value);
            TextView v = (TextView) row.getTag(R.id.settings_row_value);
            v.setText(value == null ? "" : value);
            row.setContentDescription(((TextView) row.getTag(R.id.settings_row_title)).getText()
                    + (value == null || value.length() == 0 ? "" : ", " + value));
        }
    }

    /** The focused row is semibold on the accent; the shown section's row stays lit while its pane has focus. */
    private void paintRows() {
        for (View row : rows) {
            boolean active = inPane && current != null && current.equals(row.getTag(R.id.settings_section));
            row.setActivated(active);
            ((TextView) row.getTag(R.id.settings_row_title))
                    .setTypeface(active || row.isFocused() ? Ui.MEDIUM : Ui.REGULAR);
        }
    }

    /** The pane's border brightens while it has focus (.set-pane.is-live). */
    private void paintPane() {
        pane.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_XL, inPane ? Ui.LINE_STRONG : Ui.LINE, Ui.LINE_W));
    }

    private SettingsSection section(String id) {
        for (SettingsSection s : sections) if (s.id().equals(id)) return s;
        return null;
    }

    private View rowOf(String id) {
        for (View row : rows) if (id != null && id.equals(row.getTag(R.id.settings_section))) return row;
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The section showing, for MainActivity to reopen after a rebuild. */
    String section() {
        return current;
    }

    /** Whether the pane has focus, so a rebuild reopens inside it. */
    boolean inPane() {
        return inPane;
    }

    /** The page has closed (Back, Home, or the launcher rebuilt): every section stops what it left running. */
    void closed() {
        for (SettingsSection s : sections) s.onClose();
    }

    // ---- Screen --------------------------------------------------------------------------------------------------

    @Override
    View defaultFocus() {
        if (enterFirst) {
            enterFirst = false;
            if (setup != null) setup.entered(current);
            SettingsSection s = section(current);
            View first = s == null ? null : s.firstFocus();
            if (first != null) return first;
        }
        return rowOf(current);
    }

    /** Back from a page pushed over this one (the TMDB account): the values and the pane are read again. */
    @Override
    void onShow() {
        paintValues();
        if (showedOnce && current != null) show(current, true);
        showedOnce = true;
    }

    @Override
    void onFocus(View v) {
        Object id = v.getTag(R.id.settings_section);
        if (id instanceof String && Focus.isInside(list, v)) show((String) id);
        boolean live = Focus.isInside(pane, v);
        if (live != inPane) {
            inPane = live;
            paintPane();
        }
        paintRows();
    }

    @Override
    boolean onKey(Key key, View focused) {
        if (focused == null || Focus.isInside(list, focused)) {
            switch (SettingsNav.onList(key)) {
                case ENTER:
                    enter(current);
                    return true;
                case MOVE_LIST:
                    shell().moveWithin(list, Shell.dirOf(key));
                    return true;
                case CLOSE:
                    if (setup != null) setup.close();
                    else activity.closeLauncherSettings();
                    return true;
                default:
                    return true;
            }
        }
        SettingsSection s = section(current);
        // First run's footer is the page's, not the section's: the section never sees its keys.
        boolean inFoot = foot != null && Focus.isInside(foot, focused);
        if (s != null && !inFoot && key != Key.BACK && key != Key.UP && s.onKey(key, focused)) return true;
        switch (SettingsNav.inPane(key)) {
            case BACK:
                if (s == null || !s.onBack()) shell().focus(rowOf(current));
                return true;
            case PASS:
                return false;
            default:
                SpatialNav.Dir dir = Shell.dirOf(key);
                if (!shell().moveWithin(pane, dir) && dir == SpatialNav.Dir.LEFT) shell().focus(rowOf(current));
                return true;
        }
    }

    @Override
    boolean onText(char c) {
        SettingsSection s = section(current);
        return s != null && s.onText(c);
    }

    // ---- SettingsSection.Host ------------------------------------------------------------------------------------

    @Override
    public void valueChanged() {
        changed(ctx);
        paintValues();
    }

    @Override
    public void themeChanged() {
        changed(ctx);
        activity.recreate();
    }

    @Override
    public Shell shell() {
        return activity.shell();
    }

    /**
     * Moves Launcher settings' change count, so Home is rebuilt with the new choices: when this page closes, or when
     * Home next resumes if the TV left for another app first (MainActivity#onResume).
     */
    static void changed(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }
}
