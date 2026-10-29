package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

/**
 * Pick for me's setup page (design_preview/src/screens/pick.ts, pickSetup; .pick styles in screens.css; README, Pick
 * for me). A page pushed over Movies or TV Shows, so Back returns there. Left: My watchlists or My services, the
 * lists, and six fields that OK steps through; right: Movies or TV Shows, and the card counting the picks with the
 * Find picks button. Every choice updates the count at once. The rules are in {@link PickRules}.
 */
final class PickSetupPage extends Screen {
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    /** The right column's width; the left one takes the rest (.pick-right). */
    private static final int RIGHT_W = 590;
    private static final int GAP = 56;

    private final Context ctx;
    private final NavBar.Host host;
    private final Router router;
    private final Data data;
    private final PickRules.Household household;
    private final Random random;
    private final PickRules.State state;

    private final View[] sourceChips = new View[2];
    private final View[] listChips = new View[PickRules.LISTS.length];
    private final LinearLayout listsRow;
    private final View[] moodChips = new View[2];
    private final TextView[] fieldLabels = new TextView[PickRules.IDS.length];
    private final TextView[] fieldValues = new TextView[PickRules.IDS.length];
    private final TextView count, countText, summaryLead, summaryFilters, note;
    private final View find;

    static PickSetupPage create(MainActivity activity, Kind kind) {
        return new PickSetupPage(activity, activity, activity, kind, new Random());
    }

    PickSetupPage(Context ctx, NavBar.Host host, Router router, Kind kind, Random random) {
        super(ctx.getString(R.string.pick_title), Kit.unclip(Pages.page(ctx, host, tabOf(kind))), Layer.PAGE);
        this.ctx = ctx;
        this.host = host;
        this.router = router;
        this.data = router.data();
        this.random = random;
        this.state = PickRules.defaults(kind);
        this.household = household(data);
        FrameLayout page = (FrameLayout) view;

        LinearLayout body = Kit.unclip(new LinearLayout(ctx));
        body.setOrientation(LinearLayout.HORIZONTAL);

        // ---- Left: where from, which lists, the six fields ----
        LinearLayout left = column(ctx);
        TextView title = Ui.line(ctx, 54, Ui.TEXT_1, Ui.REGULAR);
        title.setText(R.string.pick_title);
        left.addView(title);
        left.addView(context(ctx, ctx.getString(R.string.pick_context)), below(8));
        left.addView(label(ctx, R.string.pick_choose_from), labelParams());
        LinearLayout sourceRow = chipRow(ctx);
        for (int i = 0; i < 2; i++) {
            PickRules.Source src = PickRules.Source.values()[i];
            sourceChips[i] = Kit.chip(ctx, ctx.getString(i == 0 ? R.string.pick_source_lists : R.string.pick_source_services),
                    false, false, "source", "src-" + src.name().toLowerCase(java.util.Locale.ROOT), v -> {
                        state.source = src;
                        refresh();
                    });
            addChip(sourceRow, sourceChips[i]);
        }
        left.addView(sourceRow);
        left.addView(label(ctx, R.string.pick_lists_label), labelParams());
        listsRow = chipRow(ctx);
        for (int i = 0; i < PickRules.LISTS.length; i++) {
            ListId list = PickRules.LISTS[i];
            listChips[i] = Kit.chip(ctx, ListNames.of(list), false, true, "lists",
                    "pl-" + list.name().toLowerCase(java.util.Locale.ROOT), v -> {
                        state.toggle(list);
                        refresh();
                    });
            addChip(listsRow, listChips[i]);
        }
        left.addView(listsRow);

        LinearLayout fields = column(ctx);
        for (int row = 0; row < 2; row++) {
            LinearLayout r = Kit.unclip(new LinearLayout(ctx));
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                int f = row * 3 + c;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (c > 0) lp.leftMargin = Ui.px(16);
                r.addView(field(f), lp);
            }
            fields.addView(r, below(row == 0 ? 0 : 16));
        }
        left.addView(fields, below(32));
        TextView hint = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        hint.setText(R.string.pick_hint);
        left.addView(hint, below(20));
        body.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // ---- Right: movies or shows, and the card ----
        LinearLayout right = column(ctx);
        right.addView(label(ctx, R.string.pick_mood), labelParams());
        LinearLayout moodRow = chipRow(ctx);
        for (int i = 0; i < 2; i++) {
            Kind k = i == 0 ? Kind.MOVIE : Kind.SHOW;
            moodChips[i] = Kit.chip(ctx, ctx.getString(i == 0 ? R.string.pick_movies : R.string.pick_shows), false,
                    false, "mood", i == 0 ? "mood-movie" : "mood-show", v -> {
                        state.kind = k;
                        refresh();
                    });
            addChip(moodRow, moodChips[i]);
        }
        right.addView(moodRow);

        LinearLayout card = column(ctx);
        card.setPadding(Ui.px(36), Ui.px(40), Ui.px(36), Ui.px(40));
        card.setBackground(Ui.round(0x0FFFFFFF, Ui.RADIUS_XL, Ui.LINE_STRONG, 1));
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setBaselineAligned(true);
        count = Ui.line(ctx, 120, Ui.TEXT_1, LIGHT);
        count.setGravity(Gravity.START);
        head.addView(count, wrap());
        countText = Ui.line(ctx, 34, Ui.TEXT_1, Ui.REGULAR);
        countText.setGravity(Gravity.START);
        LinearLayout.LayoutParams ctlp = wrap();
        ctlp.leftMargin = Ui.px(22);
        head.addView(countText, ctlp);
        card.addView(head);
        summaryLead = Ui.text(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        lineHeight(summaryLead, Ui.T_H3 * 1.35f);
        card.addView(summaryLead, below(26));
        summaryFilters = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        lineHeight(summaryFilters, 22 * 1.5f);
        card.addView(summaryFilters, below(16));
        View divider = new View(ctx);
        divider.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1)));
        dlp.topMargin = Ui.px(28);
        dlp.bottomMargin = Ui.px(28);
        card.addView(divider, dlp);
        find = Kit.button(ctx, PickRules.findLabel(0), R.drawable.ic_ui_shuffle, Kit.PRIMARY, false, null, "find", false,
                v -> find());
        card.addView(find, wrap());
        note = Ui.text(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        card.addView(note, below(18));
        right.addView(card, below(26));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Ui.px(RIGHT_W), ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = Ui.px(GAP);
        body.addView(right, rlp);

        page.addView(body, bodyParams());
        page.addView(Kit.guide(ctx, ctx.getString(R.string.pick_guide_left), ctx.getString(R.string.pick_guide_right)));
        refresh();
    }

    @Override
    View defaultFocus() {
        return find;
    }

    /** Back on the results page or the title preview may have changed what was seen: count again. */
    @Override
    void onShow() {
        refresh();
    }

    private void find() {
        if (matches().isEmpty()) {
            router.toast(ctx.getString(R.string.pick_no_matches));
            return;
        }
        router.shell().push(new PickResultsPage(ctx, host, router, state.copy(), household, random));
    }

    private List<Title> matches() {
        return PickRules.matches(state, data.catalog.all(), household, data.services.subscribed());
    }

    /** Shows the current choices and counts the matches, as the prototype's render() does, without a rebuild. */
    private void refresh() {
        for (int i = 0; i < 2; i++) Kit.setSelected(sourceChips[i], state.source == PickRules.Source.values()[i]);
        boolean lists = state.source == PickRules.Source.LISTS;
        for (int i = 0; i < listChips.length; i++) {
            Kit.setSelected(listChips[i], state.lists.contains(PickRules.LISTS[i]));
            // .chips.is-disabled with data-inert: dimmed, and the D-pad passes them by.
            listChips[i].setEnabled(lists);
        }
        listsRow.setAlpha(lists ? 1f : 0.4f);
        for (int i = 0; i < 2; i++) Kit.setSelected(moodChips[i], state.kind == (i == 0 ? Kind.MOVIE : Kind.SHOW));
        for (int f = 0; f < PickRules.IDS.length; f++) {
            fieldLabels[f].setText(PickRules.label(f, state.kind));
            fieldValues[f].setText(PickRules.option(f, state.values[f]));
        }
        int matches = matches().size();
        int n = PickRules.pickCount(matches);
        int subscribed = data.services.subscribed().size();
        count.setText(String.valueOf(n));
        countText.setText(PickRules.countText(n));
        summaryLead.setText(PickRules.summaryLead(state, subscribed));
        summaryFilters.setText(PickRules.filterText(state));
        Kit.setLabel(find, PickRules.findLabel(n));
        note.setText(PickRules.note(matches));
    }

    /** {@code .field}: a label over the option and a chevron; OK steps to the next option. */
    private View field(int f) {
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(18));
        v.setBackground(Ui.states(Ui.round(Ui.SURFACE, 14, Ui.LINE, 1), null, Ui.round(Ui.ACCENT, 14, Ui.ACCENT, 1)));
        TextView l = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        l.setGravity(Gravity.START);
        l.setTextColor(Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT));
        l.setDuplicateParentStateEnabled(true);
        v.addView(l);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setDuplicateParentStateEnabled(true);
        TextView value = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        value.setGravity(Gravity.START);
        value.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        value.setDuplicateParentStateEnabled(true);
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView chev = new ImageView(ctx);
        chev.setImageResource(R.drawable.ic_ui_chev_down);
        chev.setImageTintList(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        chev.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22));
        clp.leftMargin = Ui.px(8);
        row.addView(chev, clp);
        v.addView(row, below(10));
        fieldLabels[f] = l;
        fieldValues[f] = value;
        Focus.make(v, "fields", "fld-" + PickRules.IDS[f], false, x -> {
            state.step(f);
            refresh();
        });
        Ui.scaleOnFocus(v, 1.03f);
        return v;
    }

    // ---- Shared with the results page ------------------------------------------------------------------------------

    static Screen.Tab tabOf(Kind kind) {
        return kind == Kind.MOVIE ? Screen.Tab.MOVIES : Screen.Tab.TV;
    }

    /** The household as the rules see it: My list and Seen, kept on the TV. */
    static PickRules.Household household(Data data) {
        return new PickRules.Household() {
            @Override
            public boolean inMyList(String id) {
                return data.household.inMyList(id);
            }

            @Override
            public boolean isSeen(String id) {
                return data.household.isSeen(id);
            }
        };
    }

    /** {@code .pick-body} and {@code .pick-results}: 170 from the top, inside the title-safe sides. */
    static FrameLayout.LayoutParams bodyParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lp.topMargin = Ui.px(170);
        lp.leftMargin = Ui.px(Ui.SAFE_X);
        lp.rightMargin = Ui.px(Ui.SAFE_X);
        return lp;
    }

    static LinearLayout column(Context ctx) {
        LinearLayout c = Kit.unclip(new LinearLayout(ctx));
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    /** {@code .page-context}: 22 px, secondary. */
    static TextView context(Context ctx, CharSequence text) {
        TextView t = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        t.setGravity(Gravity.START);
        t.setText(text);
        return t;
    }

    /** {@code .pick-label}: 22 px, secondary, 28 above and 12 below ({@link #labelParams}). */
    private static TextView label(Context ctx, int text) {
        TextView t = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        t.setGravity(Gravity.START);
        t.setText(text);
        return t;
    }

    private static LinearLayout.LayoutParams labelParams() {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = Ui.px(28);
        lp.bottomMargin = Ui.px(12);
        return lp;
    }

    private static LinearLayout chipRow(Context ctx) {
        LinearLayout r = Kit.unclip(new LinearLayout(ctx));
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    /** .chips: 12 px apart. */
    private static void addChip(LinearLayout row, View chip) {
        LinearLayout.LayoutParams lp = wrap();
        if (row.getChildCount() > 0) lp.leftMargin = Ui.px(12);
        row.addView(chip, lp);
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }

    /** CSS line-height in design px: the extra space between lines of a wrapping TextView. */
    static void lineHeight(TextView t, float designPx) {
        int natural = t.getPaint().getFontMetricsInt(null);
        t.setLineSpacing(Math.max(0, Ui.px(designPx) - natural), 1f);
    }
}
