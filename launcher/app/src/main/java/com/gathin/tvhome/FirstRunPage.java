package com.gathin.tvhome;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First run (design_preview/README.md, First run; V2 design 01-setup B; src/screens/setup.ts): a page without the
 * nav, "Set up your Home screen", one row per step (Sign in to TMDB, Streaming services, Stores for rent and buy,
 * Weather city), each with its number, title, a line under it, the section's current value and a pill; Start using
 * Home under them; on the right a help pane for the focused row, or for Start.
 *
 * <p>OK on a row opens Launcher settings' own split and panes cut down to these steps ({@link FirstRunSplit}). Coming
 * back, every step whose pane was opened is done (FirstRunRules.done): a green check in place of its number and no
 * pill, and focus goes to the next row not done, or Start. The done steps are kept in First run's preferences, so
 * they stay ticked after the launcher restarts. With every row done the page reads "You’re all set". Start and Back
 * go Home (MainActivity#closeFirstRun). Without a TMDB token there is no Account section, and the checklist has three
 * steps. Streaming services and Stores show the ticked names ("Netflix, Prime Video +6"), Streaming services' line
 * the count.
 *
 * <p>It shows by itself once, on the first start after a fresh install, never after an update (the project lead,
 * 2026-10-31; FirstRunRules.showOnStart); first_run_checklist false turns that off. The intent action
 * MainActivity.ACTION_FIRST_RUN opens it at any time.
 */
final class FirstRunPage extends Screen {
    /**
     * Remembers that the checklist has shown once, and which steps are done (not the launcher's settings: a theme
     * push clears those).
     */
    static final String PREFS = "first_run";
    static final String KEY_SHOWN = "shown";
    static final String KEY_DONE = "done";
    private static final String GROUP_STEPS = "steps";
    private static final String KEY_START = "start";
    private static final float LEFT = Ui.SAFE_X, TOP = 100, MAIN_W = 1060;
    private static final float HELP_LEFT = 1220, HELP_TOP = 258;

    private final MainActivity activity;
    private final Context ctx;
    private final List<SettingsSection> sections;
    private final List<String> steps;
    private final Set<String> opened = new LinkedHashSet<>();
    private final Map<String, View> rows = new LinkedHashMap<>();
    private final TextView title;
    private final TextView lead;
    private final LinearLayout help;
    private final View start;
    private LauncherSettingsPage split;
    /** A step's split was opened: the next onShow is the way back from it. */
    private boolean returning;

    /** The checklist, with the steps kept done and those in opened (after a rebuild) done; opened may be null. */
    static FirstRunPage create(MainActivity activity, Collection<String> opened) {
        return new FirstRunPage(activity, Kit.unclip(StubPage.frame(activity)), opened);
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
    private static boolean freshInstall(Context ctx) {
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

    /** It has shown, by itself or through the intent: it doesn't show by itself again. */
    static void markShown(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!p.getBoolean(KEY_SHOWN, false)) p.edit().putBoolean(KEY_SHOWN, true).apply();
    }

    /** First run's own sections, Launcher settings' panes for its steps: made afresh for each split. */
    private static List<SettingsSection> stepSections(MainActivity activity) {
        List<SettingsSection> out = new ArrayList<>();
        for (SettingsSection s : SettingsSections.all(activity)) {
            if (FirstRunRules.STEPS.contains(s.id())) out.add(s);
        }
        return out;
    }

    private FirstRunPage(MainActivity activity, FrameLayout root, Collection<String> done) {
        super(activity.getString(R.string.first_run_eyebrow), root, Layer.PAGE);
        this.activity = activity;
        this.ctx = activity;
        this.sections = stepSections(activity);
        List<String> ids = new ArrayList<>();
        for (SettingsSection s : sections) ids.add(s.id());
        this.steps = FirstRunRules.steps(ids);
        opened.addAll(FirstRunRules.decode(prefs(ctx).getString(KEY_DONE, null)));
        if (done != null) opened.addAll(done);

        // ---- The main column: eyebrow, title, lead, the rows and Start (.setup-main).
        LinearLayout main = Kit.unclip(new LinearLayout(ctx));
        main.setOrientation(LinearLayout.VERTICAL);
        main.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.first_run_eyebrow)));
        title = Ui.line(ctx, 54, Ui.TEXT_1, Ui.DISPLAY);
        SettingsKit.add(main, title, 12);
        // CSS line boxes are taller than Android's (lead 1.5 × 26 px): the margins carry the difference.
        lead = Ui.text(ctx, Ui.T_H3, Ui.TEXT_2, Ui.REGULAR);
        lead.setLineSpacing(0f, 1.3f);
        SettingsKit.add(main, lead, 16);

        LinearLayout list = Kit.unclip(new LinearLayout(ctx));
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < steps.size(); i++) {
            String id = steps.get(i);
            View row = row(id, i + 1, i == 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = Ui.px(14);
            list.addView(row, lp);
            rows.put(id, row);
        }
        SettingsKit.addWide(main, list, 40);

        LinearLayout startLine = Kit.unclip(new LinearLayout(ctx));
        startLine.setOrientation(LinearLayout.HORIZONTAL);
        startLine.setGravity(Gravity.CENTER_VERTICAL);
        start = Kit.button(ctx, ctx.getString(R.string.first_run_start), R.drawable.ic_ui_home, Kit.PRIMARY, false,
                KEY_START, KEY_START, false, v -> activity.closeFirstRun());
        startLine.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView note = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        note.setText(R.string.first_run_start_note);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = Ui.px(24);
        startLine.addView(note, nlp);
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
        row.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_L, Ui.ACCENT, Ui.LINE_W)));

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

        boolean muted = "account".equals(id);
        TextView pill = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_2, Ui.MEDIUM);
        pill.setText(pillOf(id));
        pill.setTextColor(Ui.colors(muted ? Ui.TEXT_2 : Ui.ACCENT, muted ? Ui.TEXT_2 : Ui.ACCENT, Ui.ON_ACCENT));
        // .status--muted is white at 7%, .status--accent the accent's soft fill; focused, the dark text at 12%.
        pill.setBackground(Ui.states(Ui.round(muted ? Ui.alpha(Ui.TEXT_1, 0x12) : Ui.ACCENT_SOFT, Ui.RADIUS_PILL), null,
                Ui.round(Ui.alpha(Ui.ON_ACCENT, 0x1F), Ui.RADIUS_PILL)));
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
        Focus.make(row, GROUP_STEPS, "step-" + id, isDefault, v -> open(id));
        Ui.scaleOnFocus(row, 1.02f);
        return row;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** The row's 22 px gap before each item after the disc (.lrow gap). */
    private static LinearLayout.LayoutParams gap(LinearLayout.LayoutParams lp) {
        lp.leftMargin = Ui.px(22);
        return lp;
    }

    /**
     * The step's title on its row, its help and the split's list and Next button: First run's own name, else the
     * section's label, else the id itself, so an unknown step never asks for resource 0.
     */
    static CharSequence titleOf(Context ctx, String id, SettingsSection section) {
        int res = titleRes(id);
        if (res != 0) return ctx.getString(res);
        if (section != null && section.label() != 0) return ctx.getString(section.label());
        return id;
    }

    /** First run's own name for a step, or 0 for any other id: only {@link #titleOf} reads it. */
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
        switch (id) {
            case "account":
                return R.string.first_run_optional;
            case "weather":
                return R.string.first_run_default;
            default:
                return R.string.first_run_suggested;
        }
    }

    private String lineOf(String id) {
        switch (id) {
            case "account":
                return ctx.getString(activity.data().accounts.signedIn() ? R.string.first_run_account_line_in
                        : R.string.first_run_account_line);
            case "services":
                Set<ServiceId> ticked = activity.data().services.subscribed();
                return ctx.getString(R.string.settings_ticked, ServicePicks.count(ticked,
                        ServicePicks.listed(ticked)));
            case "stores":
                return ctx.getString(R.string.first_run_stores_line);
            default:
                return ctx.getString(R.string.first_run_weather_line);
        }
    }

    private SettingsSection section(String id) {
        for (SettingsSection s : sections) if (s.id().equals(id)) return s;
        return null;
    }

    /**
     * The value on a row: Streaming services' and Stores' ticked names, as the prototype has them ("Netflix, Prime
     * Video +6"); the section's own for the rest (the account, the weather place).
     */
    private CharSequence valueOf(String id) {
        Services services = activity.data().services;
        ServiceTable table = services.table();
        List<String> names = new ArrayList<>();
        switch (id) {
            case "services":
                Set<ServiceId> ticked = services.subscribed();
                for (ServiceId s : FirstRunRules.ticked(ServicePicks.listed(ticked), ticked)) names.add(table.name(s));
                return FirstRunRules.names(names);
            case "stores":
                for (ServiceId s : FirstRunRules.ticked(ServicePicks.STORES, ServiceChoices.stores(ctx))) {
                    names.add(StoresSection.storeName(ctx, table, s));
                }
                return FirstRunRules.names(names);
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
            CharSequence value = valueOf(id);
            String text = value == null ? null : value.toString();
            CharSequence pill = ((TextView) row.getTag(R.id.first_run_pill)).getText();
            if (FirstRunRules.placeholder(id, text, notSignedIn)
                    || (!done && FirstRunRules.repeatsPill(text, pill.toString()))) {
                value = "";
            }
            TextView v = (TextView) row.getTag(R.id.settings_row_value);
            v.setText(value);
            v.setVisibility(value == null || value.length() == 0 ? View.GONE : View.VISIBLE);
            CharSequence name = ((TextView) row.getTag(R.id.settings_row_title)).getText();
            CharSequence state = done ? ctx.getString(R.string.first_run_finish) : pill;
            row.setContentDescription(name + (value == null || value.length() == 0 ? "" : ", " + value) + ", " + state);
        }
    }

    /** The help pane for a row, or for Start (id null). */
    private void paintHelp(String id) {
        help.removeAllViews();
        int done = FirstRunRules.doneCount(steps, opened);
        if (id != null) {
            SettingsKit.add(help, SettingsKit.eyebrow(ctx, titleOf(ctx, id, section(id))), 0);
            helpTitle(ctx.getString(helpRes(id, 0)));
            helpBody(ctx.getString(helpRes(id, 1)));
            helpDivider();
            String note = done > 0 && !FirstRunRules.done(id, opened)
                    ? ctx.getResources().getQuantityString(R.plurals.first_run_done_note, done, done)
                    : ctx.getString(helpRes(id, 2));
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
    private static int helpRes(String id, int part) {
        switch (id) {
            case "account":
                return part == 0 ? R.string.first_run_account_help_title
                        : part == 1 ? R.string.first_run_account_help_body : R.string.first_run_account_help_note;
            case "services":
                return part == 0 ? R.string.first_run_services_help_title
                        : part == 1 ? R.string.first_run_services_help_body : R.string.first_run_services_help_note;
            case "stores":
                return part == 0 ? R.string.first_run_stores_help_title
                        : part == 1 ? R.string.first_run_stores_help_body : R.string.first_run_stores_help_note;
            default:
                return part == 0 ? R.string.first_run_weather_help_title
                        : part == 1 ? R.string.first_run_weather_help_body : R.string.first_run_weather_help_note;
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

    // ---- The split ------------------------------------------------------------------------------------------------

    /** OK on a row: the step is done, and Launcher settings' split opens on its pane. */
    private void open(String id) {
        markDone(id);
        returning = true;
        split = FirstRunSplit.open(activity, stepSections(activity), steps, id, new FirstRunSplit.Listener() {
            @Override
            public void entered(String step) {
                markDone(step);
            }

            @Override
            public void back() {
                closeSplit();
            }
        });
        activity.shell().push(split);
    }

    /** Back to the checklist: the split's sections stop what they left running (a sign-in code waiting). */
    private void closeSplit() {
        LauncherSettingsPage s = split;
        split = null;
        if (s == null) return;
        if (activity.shell().top() == s) activity.shell().pop();
        s.closed();
    }

    /** The checklist has left the stack (Start, Back, the remote's Home, or the launcher rebuilt). */
    void closed() {
        LauncherSettingsPage s = split;
        split = null;
        if (s != null) s.closed();
    }

    /** The steps done so far, kept over a rebuild. */
    ArrayList<String> opened() {
        return new ArrayList<>(opened);
    }

    // ---- Screen --------------------------------------------------------------------------------------------------

    @Override
    View defaultFocus() {
        return steps.isEmpty() ? start : rows.get(steps.get(0));
    }

    /** Back from the split: the opened rows are ticked, and focus goes to the next row not done (or Start). */
    @Override
    void onShow() {
        paint();
        if (!returning) return;
        returning = false;
        String next = FirstRunRules.nextFocus(steps, opened);
        View target = next == null ? start : rows.get(next);
        lastFocus = target;
        lastFocusKey = Focus.key(target);
    }

    @Override
    void onFocus(View v) {
        if (v == start) {
            paintHelp(null);
            return;
        }
        Object id = v.getTag(R.id.settings_section);
        if (id instanceof String) paintHelp((String) id);
    }

    @Override
    boolean onKey(Key key, View focused) {
        boolean onStart = focused == start;
        boolean onLast = !steps.isEmpty() && focused == rows.get(steps.get(steps.size() - 1));
        switch (FirstRunRules.onKey(key, onStart, onLast)) {
            case HOME:
                activity.closeFirstRun();
                return true;
            case STAY:
                return true;
            case START:
                activity.shell().focus(start);
                return true;
            default:
                return false;
        }
    }
}
