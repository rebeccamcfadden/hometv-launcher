package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * First run's cut-down split (design_preview/README.md, First run; V2 design r2-setup-checklist; settings.ts with
 * setup): Launcher settings' own page and panes, cut down to the checklist's steps. This is what it changes there
 * ({@link LauncherSettingsPage.Setup}): "Set up your Home screen" with "First run · Back returns to the checklist",
 * First run's names for Account and Weather, "Optional" for an account not signed in, and the footer under the pane:
 * "Step N of M · changes save at once", Back to checklist and Next: the following step (Done on the last).
 */
final class FirstRunSplit implements LauncherSettingsPage.Setup {
    private static final String GROUP_FOOT = "first-run-foot";

    /** What the checklist hears from its split. */
    interface Listener {
        /** A step's pane was entered (FirstRunRules.done). */
        void entered(String id);

        /** Back to checklist, Done, or Back on the sections: the split closes. */
        void back();
    }

    private final Context ctx;
    private final List<SettingsSection> sections;
    private final List<String> steps;
    private final Listener listener;

    FirstRunSplit(Context ctx, List<SettingsSection> sections, List<String> steps, Listener listener) {
        this.ctx = ctx;
        this.sections = sections;
        this.steps = steps;
        this.listener = listener;
    }

    /** Launcher settings' page for these steps, open on id's pane. */
    static LauncherSettingsPage open(MainActivity activity, List<SettingsSection> sections, List<String> steps,
                                     String id, Listener listener) {
        FirstRunSplit split = new FirstRunSplit(activity, sections, steps, listener);
        LauncherSettingsPage page = LauncherSettingsPage.setup(activity, sections, id, split);
        split.page = page;
        return page;
    }

    /** Set by {@link #open}; the footer's Next enters the next step on it. */
    private LauncherSettingsPage page;

    @Override
    public CharSequence title() {
        return ctx.getString(R.string.first_run_title);
    }

    @Override
    public CharSequence hint() {
        return ctx.getString(R.string.first_run_split_hint);
    }

    @Override
    public CharSequence label(SettingsSection s) {
        return FirstRunPage.titleOf(ctx, s.id(), s);
    }

    @Override
    public CharSequence value(SettingsSection s, CharSequence value) {
        String text = value == null ? null : value.toString();
        return FirstRunRules.placeholder(s.id(), text, ctx.getString(R.string.account_not_signed_in))
                ? ctx.getString(R.string.first_run_optional) : value;
    }

    /** {@code .pane-foot}: the step note on the left, Back to checklist and Next (or Done) on the right. */
    @Override
    public View foot(String section) {
        FrameLayout row = Kit.unclip(new FrameLayout(ctx));
        TextView note = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        note.setText(ctx.getString(R.string.first_run_step, FirstRunRules.stepNumber(steps, section), steps.size()));
        row.addView(note, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));

        String next = FirstRunRules.nextStep(steps, section);
        View back = Kit.button(ctx, ctx.getString(R.string.first_run_back_to_checklist), R.drawable.ic_ui_chev_left,
                Kit.SMALL, false, GROUP_FOOT, "foot-back", false, v -> listener.back());
        CharSequence nextLabel = next == null ? ctx.getString(R.string.first_run_finish)
                : ctx.getString(R.string.first_run_next, nextName(next));
        View forward = Kit.button(ctx, nextLabel, next == null ? R.drawable.ic_ui_check : R.drawable.ic_ui_chev_right,
                Kit.SMALL | Kit.PRIMARY, false, GROUP_FOOT, "foot-next", true, v -> {
                    if (next != null && page != null) page.enter(next);
                    else listener.back();
                });
        LinearLayout actions = SettingsKit.actions(ctx, back, forward);
        row.addView(actions, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        return row;
    }

    @Override
    public void entered(String section) {
        listener.entered(section);
    }

    @Override
    public void close() {
        listener.back();
    }

    /** The step's name on the Next button: "Stores" is short for the stores step (settings.ts paintFoot). */
    private CharSequence nextName(String id) {
        if ("stores".equals(id)) return ctx.getString(R.string.first_run_next_stores);
        SettingsSection section = null;
        for (SettingsSection s : sections) if (s.id().equals(id)) section = s;
        return FirstRunPage.titleOf(ctx, id, section);
    }
}
