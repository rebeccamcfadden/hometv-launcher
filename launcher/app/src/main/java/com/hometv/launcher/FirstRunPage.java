package com.hometv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First run (design_preview/README.md, First run; V2 design 01-setup B; src/screens/setup.ts), one page since F28 (the
 * project lead, 2026-11-02): no nav, "Set up your Home screen", one row per step (Features, Region, Streaming services,
 * Stores for rent and buy), each with its number, title, a line under it, the current value and a pill; Start using Home
 * under them; on the right a help pane for the focused row, or for Start.
 *
 * <p>→ on a row (or OK) opens that step's panel on the same page, focus in it: the checklist shrinks to a column of
 * tabs on the left and the panel takes the right. The panel is the step's Launcher settings pane, with a footer: "Step
 * N of 3 · changes save at once" and Next (Done on the last). While a panel is open, ↑ and ↓ on the tabs show each
 * step's panel at once, → enters it, ← from the panel's left edge (or Back in it) returns to its tab, and Back on a tab
 * closes the panel and widens the checklist again. Done does too, focus on the first row not done, or Start. A step is
 * done once its panel was entered (FirstRunRules.done): a green check in place of its number and no pill, kept in
 * First run's preferences across restarts. With every row done the page reads "You’re all set". Start and Back on the
 * wide checklist go Home (MainActivity#closeFirstRun).
 *
 * <p>Features (F14, the project lead 2026-11-01 and 2026-11-02): every feature starts on, and each has its setup as a
 * tab under Features (Sign in to TMDB, Phone notes, Weather city); Next leads from Features through them. Turning one on
 * again opens its tab ({@link #openSetup}). When First run ends, every feature on whose setup isn't finished is
 * switched off ({@link #switchOffUnfinished}); Start's help pane lists them first. TMDB account and Phone notes need the launcher rebuilt with them
 * first, so the launcher rebuilds and comes back to that tab (MainActivity keeps it).
 *
 * <p>It shows by itself once, on the first start after a fresh install, never after an update (the project lead,
 * 2026-10-31; FirstRunRules.showOnStart); first_run_checklist false turns that off. The intent action
 * MainActivity.ACTION_FIRST_RUN opens it at any time.
 */
final class FirstRunPage extends Screen implements SettingsSection.Host {
    /**
     * Remembers that the checklist has shown once, and which steps are done (not the launcher's settings: a theme
     * push clears those).
     */
    static final String PREFS = "first_run";
    static final String KEY_SHOWN = "shown";
    static final String KEY_DONE = "done";
    /** Shown and not yet ended (markShown, switchOffUnfinished). */
    static final String KEY_IN_PROGRESS = "in_progress";
    /** It switched features back on as it opened, until it ends (markRestored; F34). */
    private static final String KEY_RESTORED = "restored";
    private static final String GROUP_STEPS = "steps";
    private static final String GROUP_FOOT = "first-run-foot";
    private static final String KEY_START = "start";
    private static final float LEFT = Ui.SAFE_X, TOP = 100, MAIN_W = 1060;
    private static final float HELP_LEFT = 1220, HELP_TOP = 258;
    /** Open: the tabs as wide as Launcher settings' sections, and the panel where its pane is (.setup--open). */
    private static final float TABS_W = 470, PANEL_LEFT = 630, PANEL_PAD_X = 52, PANEL_PAD_Y = 44;
    /** Room inside the panel's scroll area for a focused control's ring and scale-up (.pane-scroll). */
    private static final float PANEL_ROOM = 10;
    /** The footer (.pane-foot): a 24 px gap above its row of 56 px buttons (Kit.SMALL), 80 px in all. */
    private static final float FOOT_GAP = 24, FOOT_H = FOOT_GAP + 56;

    private final MainActivity activity;
    private final Context ctx;
    /** Launcher settings' sections the page shows panels of: the steps and the feature setups, made once. */
    private final Map<String, SettingsSection> sections = new LinkedHashMap<>();
    private final List<String> steps;
    private final Set<String> opened = new LinkedHashSet<>();
    private final Map<String, View> rows = new LinkedHashMap<>();
    private final LinearLayout main;
    private final LinearLayout list;
    private final TextView title;
    private final TextView lead;
    private final TextView startNote;
    private final LinearLayout help;
    private final View start;
    private final FrameLayout panel;
    private final ViewGroup panelScroll;
    private final FrameLayout panelHolder;
    private final FrameLayout foot;
    /** The panel showing; null while the checklist is wide. */
    private String open;
    /** The section whose pane is built in the panel now. */
    private String shown;
    private boolean inPanel;
    /** The feature setups' tabs as last built, to rebuild them only when they change. */
    private String subsBuilt = "";
    /** A setup to open once the launcher is rebuilt with its feature on (MainActivity keeps it over the rebuild). */
    private String pendingPanel;
    /** Whether the open panel was entered (not just shown from its tab), kept over a rebuild. */
    private boolean entered;
    /** The list is being rebuilt: focus moving back onto a tab doesn't show that tab's panel. */
    private boolean rebuilding;
    /**
     * Whether TMDB account's TMDB key tab shows (F16): the TV had no working TMDB key when First run opened. Decided as
     * it opens, and kept over its rebuilds, process death included (MainActivity saves it), so a key added there
     * doesn't make the tab vanish.
     */
    private final boolean tmdbKeyTab;

    /**
     * The checklist, with the steps kept done and those in opened (after a rebuild) done; opened may be null. keyTab is
     * the TMDB key tab's decision kept over a rebuild, or null to decide it now.
     */
    static FirstRunPage create(MainActivity activity, Collection<String> opened, Boolean keyTab) {
        boolean tab = keyTab != null ? keyTab : !ApiKeys.get(activity).tmdbReady();
        return new FirstRunPage(activity, Kit.unclip(StubPage.frame(activity)), opened, tab);
    }

    /** The TMDB key tab's decision, for MainActivity to keep over a rebuild. */
    boolean tmdbKeyTab() {
        return tmdbKeyTab;
    }

    /**
     * Whether the checklist may show by itself: config.xml's first_run_checklist, overlaid by a pushed config.json
     * like every other key (Config.overlay). Kept here so First run stays in its own files, as AccountConfig does.
     */
    static boolean configured(Context ctx) {
        Resources res = ctx.getResources();
        boolean on = res.getBoolean(R.bool.first_run_checklist);
        JSONObject json = Config.overlay(ctx);
        return json != null ? json.optBoolean("first_run_checklist", on) : on;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether the launcher is as first installed, not updated since; false when the package can't be read. */
    static boolean freshInstall(Context ctx) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return FirstRunRules.freshInstall(info.firstInstallTime, info.lastUpdateTime);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** True once, the first start after a fresh install (FirstRunRules.showOnStart). */
    static boolean dueOnStart(Context ctx) {
        return FirstRunRules.showOnStart(configured(ctx), freshInstall(ctx), prefs(ctx).getBoolean(KEY_SHOWN, false));
    }

    /**
     * It has shown, by itself or through the intent: it doesn't show by itself again. Until it ends (and switches off
     * what isn't set up) it is in progress, so a launcher that comes back cold mid-setup (a power cut) still does that.
     */
    static void markShown(Context ctx) {
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor e = p.edit().putBoolean(KEY_IN_PROGRESS, true);
        if (!p.getBoolean(KEY_SHOWN, false)) e.putBoolean(KEY_SHOWN, true);
        e.apply();
    }

    /** First run was left in progress by a launcher that ended without it (MainActivity, on a cold start). */
    static boolean interrupted(Context ctx) {
        return prefs(ctx).getBoolean(KEY_IN_PROGRESS, false);
    }

    /**
     * Whether ending First run now switches off what isn't set up (FirstRunRules.endSwitchesOff): by Start, once a step
     * was opened (now or in an earlier First run; the steps opened are kept), or when it switched features back on as it
     * opened (markRestored).
     */
    static boolean endSwitchesOff(Context ctx, boolean start) {
        SharedPreferences p = prefs(ctx);
        return FirstRunRules.endSwitchesOff(start, FirstRunRules.decode(p.getString(KEY_DONE, null)),
                p.getBoolean(KEY_RESTORED, false));
    }

    /** This First run switched back on, as it opened, what an earlier one had switched off (F34). */
    static void markRestored(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_RESTORED, true).apply();
    }

    /**
     * Left before any step was opened (F34): nothing is switched off, and it is as if it had never shown, so it shows
     * itself again at the launcher's next start.
     */
    static void leftUntouched(Context ctx) {
        prefs(ctx).edit().remove(KEY_IN_PROGRESS).remove(KEY_SHOWN).remove(KEY_RESTORED).apply();
    }

    private FirstRunPage(MainActivity activity, FrameLayout root, Collection<String> done, boolean tmdbKeyTab) {
        super(activity.getString(R.string.first_run_eyebrow), root, Layer.PAGE);
        this.activity = activity;
        this.tmdbKeyTab = tmdbKeyTab;
        this.ctx = activity;
        List<SettingsSection> all = new ArrayList<>(SettingsSections.all(activity));
        all.sort(Comparator.comparingInt(s -> FirstRunRules.STEPS.indexOf(s.id())));
        List<String> ids = new ArrayList<>();
        for (SettingsSection s : all) {
            if (FirstRunRules.STEPS.contains(s.id()) || FirstRunRules.SETUPS.contains(s.id())) sections.put(s.id(), s);
            if (FirstRunRules.STEPS.contains(s.id())) ids.add(s.id());
        }
        // The TMDB key and Price keys tabs (F16), which Launcher settings has in API keys.
        for (SettingsSection s : SettingsSections.setupOnly()) sections.put(s.id(), s);
        this.steps = FirstRunRules.steps(ids);
        opened.addAll(FirstRunRules.decode(prefs(ctx).getString(KEY_DONE, null)));
        if (done != null) opened.addAll(done);

        // ---- The main column: eyebrow, title, lead, the rows and Start (.setup-main).
        main = Kit.unclip(new LinearLayout(ctx));
        main.setOrientation(LinearLayout.VERTICAL);
        main.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.first_run_eyebrow)));
        title = Ui.line(ctx, 54, Ui.TEXT_1, Ui.DISPLAY);
        SettingsKit.add(main, title, 12);
        // CSS line boxes are taller than Android's (lead 1.5 × 26 px): the margins carry the difference.
        lead = Ui.text(ctx, Ui.T_H3, Ui.TEXT_2, Ui.REGULAR);
        lead.setLineSpacing(0f, 1.3f);
        SettingsKit.add(main, lead, 16);

        list = Kit.unclip(new LinearLayout(ctx));
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < steps.size(); i++) {
            String id = steps.get(i);
            rows.put(id, row(id, i + 1, i == 0));
        }
        SettingsKit.addWide(main, list, 40);

        LinearLayout startLine = Kit.unclip(new LinearLayout(ctx));
        startLine.setOrientation(LinearLayout.HORIZONTAL);
        startLine.setGravity(Gravity.CENTER_VERTICAL);
        start = Kit.button(ctx, ctx.getString(R.string.first_run_start), R.drawable.ic_ui_home, Kit.PRIMARY, false,
                KEY_START, KEY_START, false, v -> activity.closeFirstRun(true));
        startLine.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        startNote = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        startNote.setText(R.string.first_run_start_note);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = Ui.px(24);
        startLine.addView(startNote, nlp);
        SettingsKit.add(main, startLine, 36);

        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(Ui.px(MAIN_W), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        mlp.topMargin = Ui.px(TOP);
        mlp.leftMargin = Ui.px(LEFT);
        root.addView(main, mlp);

        // ---- The help pane (.panel.setup-help): nothing in it takes focus.
        help = new LinearLayout(ctx);
        help.setOrientation(LinearLayout.VERTICAL);
        help.setPadding(Ui.px(40), Ui.px(40), Ui.px(40), Ui.px(40));
        help.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_XL, Ui.LINE_STRONG, Ui.LINE_W));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        hlp.topMargin = Ui.px(HELP_TOP);
        hlp.leftMargin = Ui.px(HELP_LEFT);
        hlp.rightMargin = Ui.px(Ui.SAFE_X);
        root.addView(help, hlp);

        // ---- The panel (.setup-panel, Launcher settings' .set-pane): hidden until a step opens. Its content scrolls
        // inside the rounded frame, with room kept for focus rings; the footer sits under it, FOOT_H tall.
        panel = new FrameLayout(ctx);
        int room = Ui.px(PANEL_ROOM);
        panel.setPadding(Ui.px(PANEL_PAD_X) - room, Ui.px(PANEL_PAD_Y) - room, Ui.px(PANEL_PAD_X) - room,
                Ui.px(PANEL_PAD_Y) - room);
        panelScroll = ScrollBox.vertical(ctx, 40);
        panelScroll.setPadding(room, room, room, room);
        panelHolder = Kit.unclip(new FrameLayout(ctx));
        panelScroll.addView(panelHolder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        slp.bottomMargin = Ui.px(FOOT_H);
        panel.addView(panelScroll, slp);
        foot = Kit.unclip(new FrameLayout(ctx));
        foot.setPadding(room, Ui.px(FOOT_GAP) - room, room, room);
        panel.addView(foot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(FOOT_H),
                Gravity.BOTTOM));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        plp.topMargin = Ui.px(TOP);
        plp.leftMargin = Ui.px(PANEL_LEFT);
        plp.rightMargin = Ui.px(Ui.SAFE_X);
        plp.bottomMargin = Ui.px(Ui.SAFE_Y);
        root.addView(panel, plp);

        layout();
        paint();
        paintHelp(steps.isEmpty() ? null : steps.get(0));
    }
    // ---- The rows ------------------------------------------------------------------------------------------------

    /**
     * {@code .lrow.cl-row}: 104 px or taller; the number in a 44 px disc (a green check once done), the title over its
     * line, the value, the pill while not done, and a chevron, 22 px apart. Focused: the accent fill, scale 1.02.
     */
    private View row(String id, int number, boolean isDefault) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(104));
        row.setPadding(Ui.px(26), Ui.px(12), Ui.px(26), Ui.px(12));
        row.setBackground(rowBackground());

        FrameLayout disc = new FrameLayout(ctx);
        disc.setDuplicateParentStateEnabled(true);
        TextView digit = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.MEDIUM);
        digit.setText(String.valueOf(number));
        digit.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.ON_ACCENT));
        digit.setDuplicateParentStateEnabled(true);
        disc.addView(digit, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ImageView check = Ui.icon(ctx, R.drawable.ic_ui_check, 22, Ui.GOOD);
        check.setImageTintList(Ui.colors(Ui.GOOD, Ui.GOOD, Ui.ON_ACCENT));
        check.setDuplicateParentStateEnabled(true);
        disc.addView(check, new FrameLayout.LayoutParams(Ui.px(22), Ui.px(22), Gravity.CENTER));
        row.addView(disc, new LinearLayout.LayoutParams(Ui.px(44), Ui.px(44)));

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setDuplicateParentStateEnabled(true);
        TextView t = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        t.setText(titleOf(ctx, id, section(id)));
        t.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        t.setDuplicateParentStateEnabled(true);
        text.addView(t);
        TextView line = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        line.setTextColor(Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT));
        line.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.px(4);
        text.addView(line, llp);
        row.addView(text, gap(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)));

        TextView value = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        value.setTextColor(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.ON_ACCENT));
        value.setDuplicateParentStateEnabled(true);
        row.addView(value, gap(wrap()));

        TextView pill = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_2, Ui.MEDIUM);
        pill.setText(pillOf(id));
        pillStyle(pill, "features".equals(id));
        pill.setPadding(Ui.px(14), Ui.px(4), Ui.px(14), Ui.px(4));
        pill.setDuplicateParentStateEnabled(true);
        row.addView(pill, gap(wrap()));

        ImageView chevron = Ui.icon(ctx, R.drawable.ic_ui_chev_right, 26, Ui.TEXT_2);
        chevron.setImageTintList(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.ON_ACCENT));
        chevron.setDuplicateParentStateEnabled(true);
        row.addView(chevron, gap(new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26))));

        row.setTag(R.id.settings_section, id);
        row.setTag(R.id.first_run_disc, disc);
        row.setTag(R.id.first_run_digit, digit);
        row.setTag(R.id.first_run_check, check);
        row.setTag(R.id.settings_row_title, t);
        row.setTag(R.id.settings_row_sub, line);
        row.setTag(R.id.settings_row_value, value);
        row.setTag(R.id.first_run_pill, pill);
        row.setTag(R.id.first_run_chevron, chevron);
        Focus.make(row, GROUP_STEPS, "step-" + id, isDefault, v -> enter(id));
        Ui.scaleOnFocus(row, 1.02f);
        return row;
    }

    /**
     * A feature's setup tab under Features (F14), while a panel is open: indented, 62 px, the section's icon in the
     * number's place, its First run name over its value.
     */
    private View subRow(String id) {
        SettingsSection s = sections.get(id);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(62));
        row.setPadding(Ui.px(20), Ui.px(8), Ui.px(20), Ui.px(8));
        row.setBackground(rowBackground());
        ImageView icon = Ui.icon(ctx, s.icon(), 24, Ui.TEXT_2);
        icon.setImageTintList(Ui.colors(Ui.TEXT_2, Ui.TEXT_2, Ui.ON_ACCENT));
        icon.setDuplicateParentStateEnabled(true);
        FrameLayout slot = new FrameLayout(ctx);
        slot.setDuplicateParentStateEnabled(true);
        slot.addView(icon, new FrameLayout.LayoutParams(Ui.px(24), Ui.px(24), Gravity.CENTER));
        row.addView(slot, new LinearLayout.LayoutParams(Ui.px(44), Ui.px(24)));
        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setDuplicateParentStateEnabled(true);
        TextView t = Ui.line(ctx, 22, Ui.TEXT_1, Ui.REGULAR);
        t.setText(titleOf(ctx, id, s));
        t.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        t.setDuplicateParentStateEnabled(true);
        text.addView(t);
        TextView line = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        line.setTextColor(Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT));
        line.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.px(2);
        text.addView(line, llp);
        row.addView(text, gap(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f), 18));
        row.setTag(R.id.settings_section, id);
        row.setTag(R.id.settings_row_title, t);
        row.setTag(R.id.settings_row_sub, line);
        CharSequence v = s.value(ctx);
        line.setText(v == null ? "" : v);
        row.setContentDescription(t.getText() + (v == null || v.length() == 0 ? "" : ", " + v));
        Focus.make(row, GROUP_STEPS, "sub-" + id, false, x -> enter(id));
        Ui.scaleOnFocus(row, 1.02f);
        return row;
    }

    /** {@code .lrow.cl-row}: the surface, white at 10% while its panel has focus (.is-active), the accent focused. */
    private static android.graphics.drawable.Drawable rowBackground() {
        return Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W),
                Ui.round(Ui.alpha(Ui.TEXT_1, 0x1A), Ui.RADIUS_L, Ui.LINE_STRONG, Ui.LINE_W),
                Ui.round(Ui.ACCENT, Ui.RADIUS_L, Ui.ACCENT, Ui.LINE_W));
    }

    /** .status--muted is white at 7%, .status--accent the accent's soft fill; focused, the dark text at 12%. */
    private static void pillStyle(TextView pill, boolean muted) {
        pill.setTextColor(Ui.colors(muted ? Ui.TEXT_2 : Ui.ACCENT, muted ? Ui.TEXT_2 : Ui.ACCENT, Ui.ON_ACCENT));
        pill.setBackground(Ui.states(Ui.round(muted ? Ui.alpha(Ui.TEXT_1, 0x12) : Ui.ACCENT_SOFT, Ui.RADIUS_PILL), null,
                Ui.round(Ui.alpha(Ui.ON_ACCENT, 0x1F), Ui.RADIUS_PILL)));
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** The row's 22 px gap before each item after the disc (.lrow gap). */
    private static LinearLayout.LayoutParams gap(LinearLayout.LayoutParams lp) {
        return gap(lp, 22);
    }

    private static LinearLayout.LayoutParams gap(LinearLayout.LayoutParams lp, float px) {
        lp.leftMargin = Ui.px(px);
        return lp;
    }

    /**
     * The step's title on its row and help, a setup's tab and the footer's Next button: First run's own name, else the
     * section's label, else the id itself, so an unknown step never asks for resource 0.
     */
    static CharSequence titleOf(Context ctx, String id, SettingsSection section) {
        int res = titleRes(id);
        if (res != 0) return ctx.getString(res);
        if (section != null && section.label() != 0) return ctx.getString(section.label());
        return id;
    }

    /**
     * First run's own name for a step, or a feature's setup opened from it, or 0 for any other id: only
     * {@link #titleOf} reads it.
     */
    private static int titleRes(String id) {
        switch (id) {
            case "account":
                return R.string.first_run_account;
            case "services":
                return R.string.first_run_services;
            case "stores":
                return R.string.first_run_stores;
            case "weather":
                return R.string.first_run_weather;
            default:
                return 0;
        }
    }

    /** The pill while a row isn't done: its value is a guess the household can keep. */
    private static int pillOf(String id) {
        return "features".equals(id) ? R.string.first_run_all_off : R.string.first_run_suggested;
    }

    private String lineOf(String id) {
        switch (id) {
            case "features":
                return ctx.getString(R.string.features_value, FeaturePrefs.live(ctx).onCount(),
                        Features.Feature.values().length);
            case RegionSection.ID:
                return ctx.getString(R.string.first_run_region_line);
            case "services":
                Set<ServiceId> ticked = activity.data().services.subscribed();
                return ctx.getString(R.string.settings_ticked, ServicePicks.count(ticked,
                        ServicePicks.listed(activity.data().region, ticked)));
            default:
                return ctx.getString(R.string.first_run_stores_line);
        }
    }

    private SettingsSection section(String id) {
        return sections.get(id);
    }

    /**
     * The value on a row: Streaming services' and Stores' ticked names, as the prototype has them ("Netflix, Prime
     * Video +6"); Features' names on, or "All off"; the section's own for anything else.
     */
    private CharSequence valueOf(String id) {
        Services services = activity.data().services;
        Region region = activity.data().region;
        ServiceTable table = services.table();
        List<String> names = new ArrayList<>();
        switch (id) {
            case "services":
                Set<ServiceId> ticked = services.subscribed();
                for (ServiceId s : FirstRunRules.ticked(ServicePicks.listed(region, ticked), ticked)) {
                    names.add(table.name(s));
                }
                return FirstRunRules.names(names);
            case "stores":
                // Price lookup off: the stores pick nothing, and the pill says so (the project lead, 2026-11-02).
                if (!FeaturePrefs.live(ctx).on(Features.Feature.PRICES)) return "";
                for (ServiceId s : FirstRunRules.ticked(ServicePicks.stores(region),
                        ServiceChoices.stores(ctx, region))) {
                    names.add(StoresSection.storeName(ctx, table, s));
                }
                return FirstRunRules.names(names);
            case "features":
                Features on = FeaturePrefs.live(ctx);
                for (Features.Feature f : Features.Feature.values()) {
                    if (on.on(f)) names.add(ctx.getString(FeaturesSection.label(f)));
                }
                return names.isEmpty() ? ctx.getString(R.string.first_run_all_off) : FirstRunRules.names(names);
            default:
                SettingsSection s = section(id);
                return s == null ? "" : s.value(ctx);
        }
    }

    /** A step's pane was opened: it is done, now and after a restart. */
    private void markDone(String id) {
        if (!opened.add(id)) return;
        prefs(ctx).edit().putString(KEY_DONE, FirstRunRules.encode(opened)).apply();
    }

    // ---- What the page shows -------------------------------------------------------------------------------------

    /** The title and lead, and each row's line, value and done state, from the choices as saved now. */
    private void paint() {
        boolean all = FirstRunRules.allDone(steps, opened);
        title.setText(all ? R.string.first_run_title_done : R.string.first_run_title);
        lead.setText(all ? R.string.first_run_lead_done : R.string.first_run_lead);
        String notSignedIn = ctx.getString(R.string.account_not_signed_in);
        for (Map.Entry<String, View> e : rows.entrySet()) {
            String id = e.getKey();
            View row = e.getValue();
            boolean done = FirstRunRules.done(id, opened);
            ((View) row.getTag(R.id.first_run_digit)).setVisibility(done ? View.GONE : View.VISIBLE);
            ((View) row.getTag(R.id.first_run_check)).setVisibility(done ? View.VISIBLE : View.GONE);
            ((View) row.getTag(R.id.first_run_pill)).setVisibility(done ? View.GONE : View.VISIBLE);
            // .cl-num, .cl-num.is-done; focused, the dark text at 14% (.is-focused .cl-num).
            ((View) row.getTag(R.id.first_run_disc)).setBackground(Ui.states(
                    Ui.round(done ? Ui.GOOD_SOFT : Ui.SURFACE_2, 22), null,
                    Ui.round(Ui.alpha(Ui.ON_ACCENT, 0x24), 22)));
            ((TextView) row.getTag(R.id.settings_row_sub)).setText(lineOf(id));
            // Features' pill says "All off" only while all are (a pushed config.json or an older TV may have some on).
            TextView pillView = (TextView) row.getTag(R.id.first_run_pill);
            boolean noPill = "features".equals(id) && FeaturePrefs.live(ctx).onCount() > 0;
            if ("stores".equals(id)) {
                boolean off = !FeaturePrefs.live(ctx).on(Features.Feature.PRICES);
                pillView.setText(off ? R.string.first_run_price_lookup_off : R.string.first_run_suggested);
                pillStyle(pillView, off);
            }
            if (noPill) pillView.setVisibility(View.GONE);
            CharSequence value = valueOf(id);
            String text = value == null ? null : value.toString();
            CharSequence pill = noPill ? "" : pillView.getText();
            if (FirstRunRules.placeholder(id, text, notSignedIn)
                    || (!done && FirstRunRules.repeatsPill(text, pill.toString()))) {
                value = "";
            }
            TextView v = (TextView) row.getTag(R.id.settings_row_value);
            v.setText(value);
            // Open, the rows are tabs: no value, pill or chevron (.setup--open).
            v.setVisibility(open != null || value == null || value.length() == 0 ? View.GONE : View.VISIBLE);
            if (open != null) pillView.setVisibility(View.GONE);
            ((View) row.getTag(R.id.first_run_chevron)).setVisibility(open != null ? View.GONE : View.VISIBLE);
            CharSequence name = ((TextView) row.getTag(R.id.settings_row_title)).getText();
            CharSequence state = done ? ctx.getString(R.string.first_run_finish) : pill;
            row.setContentDescription(name + (value == null || value.length() == 0 ? "" : ", " + value)
                    + (state.length() == 0 ? "" : ", " + state));
        }
        paintSubs();
    }

    /** The help pane for a row, or for Start (id null). */
    private void paintHelp(String id) {
        help.removeAllViews();
        int done = FirstRunRules.doneCount(steps, opened);
        if (id != null) {
            SettingsKit.add(help, SettingsKit.eyebrow(ctx, titleOf(ctx, id, section(id))), 0);
            helpTitle(ctx.getString(helpRes(id, 0, activity.data().region)));
            helpBody(ctx.getString(helpRes(id, 1, activity.data().region)));
            helpDivider();
            String note = done > 0 && !FirstRunRules.done(id, opened)
                    ? ctx.getResources().getQuantityString(R.plurals.first_run_done_note, done, done)
                    : ctx.getString(helpRes(id, 2, activity.data().region));
            help.addView(helpNote(note));
            return;
        }
        boolean all = FirstRunRules.allDone(steps, opened);
        int left = steps.size() - done;
        SettingsKit.add(help, SettingsKit.eyebrow(ctx, ctx.getString(all ? R.string.first_run_ready_eyebrow
                : R.string.first_run_start_eyebrow)), 0);
        helpTitle(ctx.getString(all ? R.string.first_run_ready_help_title : R.string.first_run_start_help_title));
        helpBody(all ? ctx.getString(R.string.first_run_ready_help_body)
                : ctx.getResources().getQuantityString(R.plurals.first_run_start_help_body, left, left));
        List<String> off = unfinished(activity);
        if (!off.isEmpty()) {
            TextView warn = helpNote(ctx.getString(R.string.first_run_will_switch_off, android.text.TextUtils.join(", ", off)));
            warn.setTextColor(StateUi.amber());
            SettingsKit.addWide(help, warn, 16);
        }
        helpDivider();
        // .setup-help-key: the Settings icon before the note.
        LinearLayout key = new LinearLayout(ctx);
        key.setOrientation(LinearLayout.HORIZONTAL);
        key.setGravity(Gravity.CENTER_VERTICAL);
        key.addView(Ui.icon(ctx, R.drawable.ic_ui_settings, 24, Ui.TEXT_3), new LinearLayout.LayoutParams(Ui.px(24),
                Ui.px(24)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.px(12);
        key.addView(helpNote(ctx.getString(R.string.first_run_settings_note)), lp);
        help.addView(key, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** A row's help: 0 the title, 1 the body, 2 the note. */
    private static int helpRes(String id, int part, Region region) {
        switch (id) {
            case "features":
                return part == 0 ? R.string.first_run_features_help_title
                        : part == 1 ? R.string.first_run_features_help_body : R.string.first_run_features_help_note;
            case RegionSection.ID:
                return part == 0 ? R.string.first_run_region_help_title
                        : part == 1 ? R.string.first_run_region_help_body : R.string.first_run_region_help_note;
            case "services":
                return part == 0 ? R.string.first_run_services_help_title
                        : part == 1 ? R.string.first_run_services_help_body : R.string.first_run_services_help_note;
            default:
                int body = region == Region.US ? R.string.first_run_stores_help_body_us
                        : R.string.first_run_stores_help_body;
                return part == 0 ? R.string.first_run_stores_help_title
                        : part == 1 ? body : R.string.first_run_stores_help_note;
        }
    }

    /** {@code .setup-help-title}: 30 px, 14 px under the eyebrow (16 here, for CSS's taller line box). */
    private void helpTitle(CharSequence s) {
        TextView t = Ui.text(ctx, Ui.T_H2, Ui.TEXT_1, Ui.REGULAR);
        t.setText(s);
        t.setLineSpacing(0f, 1.15f);
        SettingsKit.addWide(help, t, 16);
    }

    /** {@code .setup-help-body}: 24 px, secondary, 16 px under the title (22 here, as above). */
    private void helpBody(CharSequence s) {
        TextView t = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        t.setText(s);
        t.setLineSpacing(0f, 1.3f);
        SettingsKit.addWide(help, t, 22);
    }

    /** {@code .setup-help-divider}: 28 px above and below. */
    private void helpDivider() {
        View d = SettingsKit.divider(ctx);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
        lp.topMargin = Ui.px(28);
        lp.bottomMargin = Ui.px(28);
        help.addView(d, lp);
    }

    /** {@code .setup-help-note}: 20 px, tertiary. */
    private TextView helpNote(CharSequence s) {
        TextView t = Ui.text(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        t.setText(s);
        t.setLineSpacing(0f, 1.3f);
        return t;
    }


    // ---- The panel ------------------------------------------------------------------------------------------------

    /** Wide, the checklist and its help; open, the tabs on the left and the panel (.setup--open). */
    private void layout() {
        boolean isOpen = open != null;
        main.getLayoutParams().width = Ui.px(isOpen ? TABS_W : MAIN_W);
        main.requestLayout();
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(isOpen ? 38 : 54));
        lead.setVisibility(isOpen ? View.GONE : View.VISIBLE);
        startNote.setVisibility(isOpen ? View.GONE : View.VISIBLE);
        help.setVisibility(isOpen ? View.GONE : View.VISIBLE);
        panel.setVisibility(isOpen ? View.VISIBLE : View.GONE);
        for (View row : rows.values()) {
            row.setMinimumHeight(Ui.px(isOpen ? 78 : 104));
            row.setPadding(Ui.px(isOpen ? 20 : 26), Ui.px(12), Ui.px(isOpen ? 20 : 26), Ui.px(12));
        }
        buildList();
    }

    /** The rows in order, and while a panel is open each feature setup's tab under Features (FirstRunRules.setups). */
    private void buildList() {
        List<String> subs = open != null ? setupsShown() : new ArrayList<>();
        String key = (open != null) + ":" + subs;
        if (key.equals(subsBuilt) && list.getChildCount() > 0) return;
        subsBuilt = key;
        View focused = shell().focused();
        String focusKey = focused != null && Focus.isInside(list, focused) ? Focus.key(focused) : null;
        list.removeAllViews();
        for (Map.Entry<String, View> e : rows.entrySet()) {
            addRow(e.getValue(), isOpen() ? 8 : 14);
            if (FeaturesSection.ID.equals(e.getKey())) {
                for (String id : subs) addRow(subRow(id), 8);
            }
        }
        if (focusKey != null) {
            View again = Focus.byKey(list, focusKey);
            rebuilding = true;
            try {
                shell().focus(again != null ? again : rows.get(FeaturesSection.ID));
            } finally {
                rebuilding = false;
            }
        }
    }

    /** The setup tabs' lines (a username, a city) and what they read out, from their sections as they are now. */
    private void paintSubs() {
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            Object id = row.getTag(R.id.settings_section);
            if (!(id instanceof String) || rows.containsKey(id)) continue;
            SettingsSection s = sections.get(id);
            Features.Feature f = FirstRunRules.featureOf((String) id);
            // Its feature off (F34): "Off", or what it needs; else its value once set up.
            CharSequence v = f != null && !FeaturePrefs.live(ctx).on(f)
                    ? FeaturesSection.stateText(ctx, FeaturePrefs.live(ctx), f)
                    : !subDone((String) id) ? ctx.getString(R.string.first_run_not_set_up)
                    : s == null ? null : s.value(ctx);
            ((TextView) row.getTag(R.id.settings_row_sub)).setText(v == null ? "" : v);
            row.setContentDescription(((TextView) row.getTag(R.id.settings_row_title)).getText()
                    + (v == null || v.length() == 0 ? "" : ", " + v));
        }
    }

    private boolean isOpen() {
        return open != null;
    }

    private void addRow(View row, float gapPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (list.getChildCount() > 0) lp.topMargin = Ui.px(gapPx);
        if (row.getTag(R.id.first_run_disc) == null) lp.leftMargin = Ui.px(40);
        list.addView(row, lp);
    }

    /** The section's pane in the panel, built afresh when another one shows (or force, to read its choices again). */
    private void show(String id, boolean force) {
        if (!force && id.equals(shown)) return;
        shown = id;
        panelHolder.removeAllViews();
        SettingsSection s = sections.get(id);
        if (s != null) {
            panelHolder.addView(s.pane(ctx, this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        panelScroll.scrollTo(0, 0);
        foot.removeAllViews();
        foot.addView(footFor(id), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * {@code .pane-foot}: a step's "Step N of 3 · changes save at once" and Next (Done on the last); a feature setup's
     * "Set up with Features · changes save at once" and Back to Features.
     */
    private View footFor(String id) {
        FrameLayout row = Kit.unclip(new FrameLayout(ctx));
        TextView note = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        boolean isStep = steps.contains(id);
        note.setText(isStep ? ctx.getString(R.string.first_run_step, FirstRunRules.stepNumber(steps, id), steps.size())
                : ctx.getString(subOff(id) ? R.string.first_run_setup_note_off
                        : subDone(id) ? R.string.first_run_setup_note_done : R.string.first_run_setup_note));
        row.addView(note, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
        // Next steps from Features through each setup, then on (FirstRunRules.panels; the project lead, 2026-11-02).
        String next = FirstRunRules.nextPanel(FirstRunRules.panels(steps, setupsShown()), id);
        CharSequence label = next == null ? ctx.getString(R.string.first_run_finish)
                : ctx.getString(R.string.first_run_next, nextName(next));
        View forward = Kit.button(ctx, label, next == null ? R.drawable.ic_ui_check : R.drawable.ic_ui_chev_right,
                Kit.SMALL | Kit.PRIMARY, false, GROUP_FOOT, "foot-next", true, v -> {
                    if (next != null) enter(next);
                    else finish();
                });
        View actions = forward;
        if (!isStep) {
            View back = Kit.button(ctx, ctx.getString(R.string.first_run_back_to_features), R.drawable.ic_ui_chev_left,
                    Kit.SMALL, false, GROUP_FOOT, "foot-features", false, v -> backToFeatures());
            actions = SettingsKit.actions(ctx, back, forward);
        }
        row.addView(actions, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        return row;
    }

    /** The setup tabs under Features: one per feature with a setup this build has, on or off (FirstRunRules.setups). */
    private List<String> setupsShown() {
        List<String> subs = new ArrayList<>();
        for (String id : FirstRunRules.setups(tmdbKeyTab)) {
            if (sections.containsKey(id)) subs.add(id);
        }
        return subs;
    }

    /**
     * Whether a setup tab is set up: the TMDB key tab once the TV has a working TMDB key (F16), any other its feature's
     * setup (or a tab with no feature).
     */
    private boolean subDone(String id) {
        if (KeysSection.TMDB_ID.equals(id)) return ApiKeys.get(ctx).tmdbReady();
        Features.Feature f = FirstRunRules.featureOf(id);
        return f == null || setUp(f);
    }

    /** Whether a feature's setup is finished (FirstRunRules.setUp), from the TV as it is now. */
    private boolean setUp(Features.Feature f) {
        return setUp(activity, f);
    }

    /** A setup tab whose feature is off (F34): its pane says so, with the feature's On/Off. */
    private boolean subOff(String id) {
        Features.Feature f = FirstRunRules.featureOf(id);
        return f != null && !FeaturePrefs.live(ctx).on(f);
    }

    /** Whether a feature's setup is finished (FirstRunRules.setUp), from the TV as it is now; also Features' rows. */
    static boolean setUp(MainActivity a, Features.Feature f) {
        return FirstRunRules.setUp(f, a.data().accounts.available(), a.data().accounts.signedIn(), Notes.get(a).on(),
                PriceKeys.ready(a));
    }

    /** The features on whose setup isn't finished, by name: First run switches them off when it ends. */
    private static List<String> unfinished(MainActivity a) {
        List<String> out = new ArrayList<>();
        Features on = FeaturePrefs.live(a);
        for (Features.Feature f : Features.Feature.values()) {
            if (on.on(f) && !setUp(a, f)) out.add(a.getString(FeaturesSection.label(f)));
        }
        return out;
    }

    /**
     * First run has ended (MainActivity#firstRunEnded): every feature on whose setup isn't finished is switched off
     * (the project lead, 2026-11-02). Returns their names, for the toast.
     */
    static List<String> switchOffUnfinished(MainActivity a) {
        List<String> names = new ArrayList<>();
        Features on = FeaturePrefs.live(a);
        // Judged by what runs: Phone notes, off only because TMDB account is, keeps its own switch (FeaturesTest).
        prefs(a).edit().remove(KEY_IN_PROGRESS).remove(KEY_RESTORED).apply();
        for (Features.Feature f : Features.Feature.values()) {
            if (!on.on(f) || setUp(a, f)) continue;
            FeaturePrefs.switchOffUnfinished(a, f);
            names.add(a.getString(FeaturesSection.label(f)));
        }
        return names;
    }

    /** The step's name on the Next button: "Stores" is short for the stores step (settings.ts paintFoot). */
    private CharSequence nextName(String id) {
        if ("stores".equals(id)) return ctx.getString(R.string.first_run_next_stores);
        return titleOf(ctx, id, sections.get(id));
    }

    /** A tab got focus: wide, the help pane follows it; open, its panel shows at once (not entered, not done). */
    private void focusTab(String id) {
        if (rebuilding) return;
        if (open == null) {
            paintHelp(steps.contains(id) ? id : null);
            return;
        }
        open = id;
        entered = false;
        show(id, false);
        paintActive();
    }

    /**
     * → or OK on a tab, or Next: the step is done, the panel opens (the checklist shrinks to tabs) and focus goes into
     * it, on the section's first control or else the footer's button. Also a switched-off feature's Open Features.
     */
    @Override
    public void enter(String id) {
        if (!sections.containsKey(id)) return;
        // Sign in after the TMDB key tab: the launcher was built without the key, so it is rebuilt with it first (F16).
        // Only while it runs with TMDB account on, so a launcher built with it off can't rebuild again and again.
        if ("account".equals(id) && Features.isOn(Features.Feature.TMDB) && FirstRunRules.signInNeedsRebuild(
                ApiKeys.get(ctx).tmdbReady(), activity.data().accounts.available())) {
            rebuildInto(id);
            return;
        }
        if (steps.contains(id)) markDone(id);
        boolean wasOpen = open != null;
        open = id;
        entered = true;
        inPanel = true;
        if (!wasOpen) layout();
        else buildList();
        // Rebuilding the list may have moved focus onto a tab; this panel stays the one open.
        open = id;
        paint();
        show(id, true);
        SettingsSection s = sections.get(id);
        View first = s.firstFocus();
        if (first == null) first = Focus.byKey(foot, steps.contains(id) ? "foot-next" : "foot-features");
        if (first != null) shell().focus(first);
        paintActive();
    }

    /** Back on a tab: the panel closes and the checklist is wide again, focus on the row it was on (or Features). */
    private void close(String at) {
        open = null;
        entered = false;
        shown = null;
        inPanel = false;
        panelHolder.removeAllViews();
        layout();
        paint();
        View row = rows.get(at != null && rows.containsKey(at) ? at : FeaturesSection.ID);
        if (row != null) shell().focus(row);
        paintActive();
    }

    /** Done on the last step: the checklist widens, focus on the first row not done, or Start. */
    private void finish() {
        String next = FirstRunRules.nextFocus(steps, opened);
        close(next);
        if (next == null) shell().focus(start);
    }

    /** Back to Features from a setup: the Features panel again, on that feature's switch. */
    private void backToFeatures() {
        Features.Feature f = FirstRunRules.featureOf(open);
        SettingsSection s = sections.get(FeaturesSection.ID);
        if (f != null && s instanceof FeaturesSection) ((FeaturesSection) s).focusOn(f);
        enter(FeaturesSection.ID);
    }

    /** The open tab is lit while focus is in its panel, as Launcher settings' section is (.is-active). */
    private void paintActive() {
        panel.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_XL, inPanel ? Ui.LINE_STRONG : Ui.LINE, Ui.LINE_W));
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            row.setActivated(inPanel && open != null && open.equals(row.getTag(R.id.settings_section)));
        }
    }

    /**
     * A feature just turned on (in the Features step, or by Turn on): its setup's tab appears under Features and opens
     * (FirstRunRules.setupOf), after a rebuild when the launcher must run with the feature first
     * (FirstRunRules.setupNeedsRebuild).
     */
    private void openSetup(Features.Feature f) {
        String id = FirstRunRules.firstSetupOf(f, tmdbKeyTab);
        if (id == null || !sections.containsKey(id)) {
            buildList();
            return;
        }
        if (FirstRunRules.setupNeedsRebuild(f, FeaturePrefs.runs(ctx, f))) {
            pendingPanel = id;
            activity.recreate();
            return;
        }
        enter(id);
    }

    /**
     * The panel showing (or about to, once rebuilt), for MainActivity to reopen after a rebuild; else null. A panel
     * only shown from its tab, not entered, is kept as "?id", so the rebuild shows it without marking it done.
     */
    String panelShowing() {
        if (pendingPanel != null) return pendingPanel;
        return open == null ? null : (entered ? open : "?" + open);
    }

    /** Rebuilt with a panel open: entered, it opens again with focus in it; only shown, its tab shows it again. */
    void reopen(String saved) {
        if (!saved.startsWith("?")) {
            enter(saved);
            return;
        }
        String id = saved.substring(1);
        if (!sections.containsKey(id)) return;
        open = id;
        entered = false;
        inPanel = false;
        layout();
        paint();
        show(id, true);
        View tab = Focus.byKey(list, (steps.contains(id) ? "step-" : "sub-") + id);
        if (tab != null) shell().focus(tab);
        paintActive();
    }

    /** The checklist has left the stack (Start, Back, the remote's Home, or the launcher rebuilt). */
    void closed() {
        for (SettingsSection s : sections.values()) s.onClose();
    }

    /** The steps done so far, kept over a rebuild. */
    ArrayList<String> opened() {
        return new ArrayList<>(opened);
    }

    // ---- SettingsSection.Host ------------------------------------------------------------------------------------

    @Override
    public void valueChanged() {
        LauncherSettingsPage.changed(ctx);
        buildList();
        paint();
        refreshFoot();
    }

    /**
     * The footer again, from the switches as they are now: Next follows the setup tabs shown, and a setup's note says
     * whether it is set up. Focus on one of its buttons stays on the same button.
     */
    private void refreshFoot() {
        if (open == null) return;
        View focused = shell().focused();
        String key = focused != null && Focus.isInside(foot, focused) ? Focus.key(focused) : null;
        foot.removeAllViews();
        foot.addView(footFor(open), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (key != null) {
            View again = Focus.byKey(foot, key);
            if (again != null) shell().focus(again);
        }
    }

    @Override
    public void themeChanged() {
        LauncherSettingsPage.changed(ctx);
        activity.recreate();
    }

    @Override
    public boolean inSetup() {
        return true;
    }

    /** Features' overview (F34): the feature's first setup tab; one on but not running yet rebuilds first (openSetup). */
    @Override
    public void openFeature(Features.Feature f) {
        if (FeaturePrefs.live(ctx).on(f)) {
            openSetup(f);
            return;
        }
        String id = FirstRunRules.firstSetupOf(f, tmdbKeyTab);
        if (id != null && sections.containsKey(id)) enter(id);
    }

    /** A feature switched off on its own tab (F34): the tab's pane again, now saying it is off, focus on its switch. */
    @Override
    public void featureSwitchedOff(Features.Feature f) {
        if (open == null) return;
        show(open, true);
        SettingsSection s = sections.get(open);
        View first = s == null ? null : s.firstFocus();
        if (first != null) shell().focus(first);
    }

    /** Rebuilt with the TMDB key (Account's Sign in, F16), First run comes back with that panel entered. */
    @Override
    public void rebuildInto(String id) {
        pendingPanel = id;
        activity.recreate();
    }

    /** Turn on in a switched-off panel (Stores, while Price lookup is off): the feature on, then its setup. */
    @Override
    public void turnOn(Features.Feature f) {
        Features.Feature first = FeaturePrefs.turnOn(ctx, f);
        LauncherSettingsPage.changed(ctx);
        // Phone notes on with TMDB account, which already runs: its inbox still needs the rebuild (F34).
        String firstId = FirstRunRules.firstSetupOf(first, tmdbKeyTab);
        if (first != f && FirstRunRules.turnOnNeedsRebuild(first, FeaturePrefs.runs(ctx, first), f, FeaturePrefs.runs(ctx, f))
                && firstId != null && sections.containsKey(firstId)) {
            pendingPanel = firstId;
            activity.recreate();
            return;
        }
        buildList();
        paint();
        if (FirstRunRules.setupOf(first) != null) {
            openSetup(first);
            return;
        }
        if (open != null) enter(open);
    }

    @Override
    public Shell shell() {
        return activity.shell();
    }

    // ---- Screen --------------------------------------------------------------------------------------------------

    @Override
    View defaultFocus() {
        if (open != null) {
            SettingsSection s = inPanel ? sections.get(open) : null;
            View first = s == null ? null : s.firstFocus();
            if (first != null) return first;
            View row = rows.get(open);
            if (row != null) return row;
        }
        return steps.isEmpty() ? start : rows.get(steps.get(0));
    }

    /** Back from a page pushed over this one (the TMDB account's): the values and the panel are read again. */
    @Override
    void onShow() {
        buildList();
        paint();
        if (open != null) show(open, true);
    }

    @Override
    void onFocus(View v) {
        boolean was = inPanel;
        inPanel = Focus.isInside(panel, v);
        if (v == start) {
            if (open == null) paintHelp(null);
        } else if (Focus.isInside(list, v)) {
            Object id = v.getTag(R.id.settings_section);
            if (id instanceof String) focusTab((String) id);
        }
        if (was != inPanel) paintActive();
    }

    @Override
    boolean onText(char c) {
        SettingsSection s = inPanel && open != null ? sections.get(open) : null;
        return s != null && s.onText(c);
    }

    @Override
    boolean onKey(Key key, View focused) {
        if (focused == null) return false;
        if (focused == start) {
            // Start sits alone on its line: ← and → stay; ↓ doesn't slip into an open panel without entering it.
            if (key == Key.LEFT || key == Key.RIGHT || (key == Key.DOWN && open != null)) return true;
            if (key == Key.BACK) {
                if (open != null) close(null);
                else activity.closeFirstRun(false);
                return true;
            }
            return false;
        }
        if (Focus.isInside(list, focused)) {
            Object tag = focused.getTag(R.id.settings_section);
            String id = tag instanceof String ? (String) tag : null;
            switch (key) {
                case RIGHT:
                    if (id != null) enter(id);
                    return true;
                case LEFT:
                    return true;
                case BACK:
                    if (open != null) close(id);
                    else activity.closeFirstRun(false);
                    return true;
                case UP:
                case DOWN:
                    if (!shell().moveWithin(list, Shell.dirOf(key)) && key == Key.DOWN) shell().focus(start);
                    return true;
                default:
                    return false;
            }
        }
        if (open == null || !Focus.isInside(panel, focused)) return false;
        SettingsSection s = sections.get(open);
        // The footer is the page's, not the section's: the section never sees its keys.
        boolean inFoot = Focus.isInside(foot, focused);
        // Features handles ↑ itself (row to row, on the selected option); other sections leave it to the page.
        if (s != null && !inFoot && key != Key.BACK && (key != Key.UP || s instanceof FeaturesSection)
                && s.onKey(key, focused)) {
            return true;
        }
        View tab = Focus.byKey(list, (steps.contains(open) ? "step-" : "sub-") + open);
        switch (key) {
            case BACK:
                if ((s == null || !s.onBack()) && tab != null) shell().focus(tab);
                return true;
            case LEFT:
                if (!shell().moveWithin(panel, SpatialNav.Dir.LEFT) && tab != null) shell().focus(tab);
                return true;
            case UP:
            case DOWN:
            case RIGHT:
                shell().moveWithin(panel, Shell.dirOf(key));
                return true;
            default:
                return false;
        }
    }
}