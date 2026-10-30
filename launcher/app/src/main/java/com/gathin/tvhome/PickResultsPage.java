package com.gathin.tvhome;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pick for me's results (design_preview/src/screens/pick.ts, pickResults; .pick-results styles in screens.css). A page
 * pushed over the setup page: up to four random matches with a blurb each, Reroll and Adjust filters (Back to the
 * setup), and a bar under the tiles for the focused pick with Title details and Open in its service. Each roll
 * counts the matches again, so a title marked seen meanwhile drops out.
 */
final class PickResultsPage extends Screen {
    private static final String GROUP_PICKS = "picks";
    private static final String GROUP_BAR = "bar";
    /** {@code .pick-cards}: four columns, 20 apart, across the title-safe width. */
    private static final int GAP = 20;
    private static final float TILE_W = (Ui.STAGE_W - 2f * Ui.SAFE_X - (PickRules.PICKS - 1) * GAP) / PickRules.PICKS;
    private static final int ART_H = 243;

    private final Context ctx;
    private final Router router;
    private final Data data;
    private final PickRules.State state;
    private final PickRules.Household household;
    private final Random random;

    private final TextView context;
    private final LinearLayout cards;
    private final TextView empty;
    private final LinearLayout bar;
    private final TextView barTitle, barNote;
    private final View adjust, reroll, details, open;
    private List<Title> picks = new ArrayList<>();
    private Title current;

    PickResultsPage(Context ctx, NavBar.Host host, Router router, PickRules.State state, PickRules.Household household,
                    Random random) {
        super(ctx.getString(R.string.pick_results_label),
                Kit.unclip(Pages.page(ctx, host, PickSetupPage.tabOf(state.kind))), Layer.PAGE);
        this.ctx = ctx;
        this.router = router;
        this.data = router.data();
        this.state = state;
        this.household = household;
        this.random = random;
        FrameLayout page = (FrameLayout) view;

        LinearLayout body = PickSetupPage.column(ctx);

        LinearLayout head = Kit.unclip(new LinearLayout(ctx));
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setBaselineAligned(false);
        LinearLayout titles = PickSetupPage.column(ctx);
        TextView title = Ui.line(ctx, 54, Ui.TEXT_1, Ui.REGULAR);
        title.setGravity(Gravity.START);
        title.setText(R.string.pick_results_title);
        titles.addView(title);
        context = PickSetupPage.context(ctx, "");
        titles.addView(context, PickSetupPage.below(8));
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        adjust = Kit.button(ctx, ctx.getString(R.string.pick_adjust), 0, Kit.SMALL, false, null, "adjust", false,
                v -> router.shell().pop());
        head.addView(adjust, PickSetupPage.wrap());
        reroll = Kit.button(ctx, PickRules.rerollLabel(0), R.drawable.ic_ui_shuffle, Kit.SMALL, false, null, "reroll",
                false, v -> roll());
        LinearLayout.LayoutParams rlp = PickSetupPage.wrap();
        rlp.leftMargin = Ui.px(14);
        head.addView(reroll, rlp);
        body.addView(head);

        TextView filters = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        filters.setGravity(Gravity.START);
        filters.setText(PickRules.filterText(state));
        body.addView(filters, PickSetupPage.below(24));

        cards = Kit.unclip(new LinearLayout(ctx));
        cards.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(cards, PickSetupPage.below(26));
        empty = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        empty.setText(R.string.pick_results_empty);
        empty.setVisibility(View.GONE);
        body.addView(empty, PickSetupPage.below(26));

        bar = Kit.unclip(new LinearLayout(ctx));
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(30), Ui.px(20), Ui.px(20), Ui.px(20));
        bar.setBackground(Ui.round(Ui.SURFACE, 20, Ui.LINE, 1));
        LinearLayout barText = PickSetupPage.column(ctx);
        barTitle = Ui.line(ctx, 28, Ui.TEXT_1, Ui.REGULAR);
        barTitle.setGravity(Gravity.START);
        barText.addView(barTitle);
        barNote = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
        barNote.setGravity(Gravity.START);
        barText.addView(barNote, PickSetupPage.below(4));
        bar.addView(barText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        details = Kit.button(ctx, ctx.getString(R.string.pick_details), 0, Kit.NORMAL, false, GROUP_BAR, "details",
                false, v -> {
                    if (current != null) router.openTitle(current);
                });
        LinearLayout.LayoutParams dlp = PickSetupPage.wrap();
        dlp.leftMargin = Ui.px(14);
        bar.addView(details, dlp);
        open = Kit.button(ctx, "", R.drawable.ic_ui_play, Kit.PRIMARY, false, GROUP_BAR, "open", true, v -> open());
        LinearLayout.LayoutParams olp = PickSetupPage.wrap();
        olp.leftMargin = Ui.px(14);
        bar.addView(open, olp);
        body.addView(bar, PickSetupPage.below(26));

        page.addView(body, PickSetupPage.bodyParams());
        page.addView(Kit.guide(ctx, ctx.getString(data.catalog.isRemote() ? R.string.prices_pick_guide
                        : R.string.pick_results_guide_left),
                ctx.getString(R.string.pick_results_guide_right)));
        roll();
    }

    @Override
    View defaultFocus() {
        View first = firstTile();
        return first != null ? first : adjust;
    }

    /** Up from a pick reaches Adjust filters or Reroll, whichever is nearer, before the nav. */
    @Override
    boolean onKey(Key key, View focused) {
        if (key != Key.UP || focused == null || !GROUP_PICKS.equals(Focus.group(focused))) return false;
        float[] f = Focus.rectIn(focused, view);
        float[] a = Focus.rectIn(adjust, view);
        float[] r = Focus.rectIn(reroll, view);
        float mid = (f[0] + f[2]) / 2;
        boolean toAdjust = Math.abs((a[0] + a[2]) / 2 - mid) < Math.abs((r[0] + r[2]) / 2 - mid);
        router.shell().focus(toAdjust ? adjust : reroll);
        return true;
    }

    /** The bar follows the focused pick. */
    @Override
    void onFocus(View v) {
        if (!GROUP_PICKS.equals(Focus.group(v))) return;
        int i = cards.indexOfChild(v);
        if (i >= 0 && i < picks.size()) showInBar(picks.get(i));
    }

    /** Draws a new set of picks: never the same four again while there are more. */
    private void roll() {
        Set<ServiceId> subscribed = data.services.subscribed();
        List<Title> matches = PickRules.matches(state, data.catalog.all(), household, subscribed);
        picks = PickRules.roll(matches, picks, random);
        int n = PickRules.pickCount(matches.size());
        context.setText(PickRules.resultsContext(picks.size(), state, subscribed.size()));
        Kit.setLabel(reroll, PickRules.rerollLabel(n));
        cards.removeAllViews();
        for (int i = 0; i < picks.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(TILE_W), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = Ui.px(GAP);
            cards.addView(tile(picks.get(i), i), lp);
        }
        boolean any = !picks.isEmpty();
        cards.setVisibility(any ? View.VISIBLE : View.GONE);
        empty.setVisibility(any ? View.GONE : View.VISIBLE);
        bar.setVisibility(any ? View.VISIBLE : View.GONE);
        if (any) showInBar(picks.get(0));
    }

    private View firstTile() {
        return cards.getChildCount() > 0 ? cards.getChildAt(0) : null;
    }

    private void showInBar(Title t) {
        current = t;
        Offer o = data.bestOffer(t);
        Set<ServiceId> subscribed = data.services.subscribed();
        barTitle.setText(t.name);
        barNote.setText(PickRules.barNote(o, subscribed, data.table));
        Kit.setLabel(open, PickRules.openLabel(o, subscribed, data.table));
    }

    /**
     * Opens the pick's service when it is included. A rental or purchase goes to the title preview instead, whose
     * rent and buy dialog asks before leaving the launcher (README, Product decisions), as the prototype's bar does.
     */
    private void open() {
        if (current == null) return;
        Offer o = data.bestOffer(current);
        if (o != null && data.isIncluded(o)) router.watch(current, o);
        else router.openTitle(current);
    }

    /**
     * {@code .pick-tile}: the art (243 tall, radius 16), the name with the service and "Included" or a price on the
     * right, the meta and provenance lines, and the blurb under a rule, two lines at most. Focused: the 4 px ring on
     * the art and the art scaled 1.03, as on the library card.
     */
    private View tile(Title t, int i) {
        LinearLayout tile = PickSetupPage.column(ctx);
        RingFrame art = new RingFrame(ctx, Ui.RADIUS_L, false, Ui.BG_RAISED);
        ImageView img = new ImageView(ctx);
        art.content().addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        Art.into(img, t.art, Ui.px(TILE_W), Ui.px(ART_H));
        tile.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(ART_H)));

        Offer o = data.bestOffer(t);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = Ui.line(ctx, 25, Ui.TEXT_1, Ui.MEDIUM);
        name.setGravity(Gravity.START);
        name.setText(t.name);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout provider = new LinearLayout(ctx);
        provider.setOrientation(LinearLayout.VERTICAL);
        provider.setGravity(Gravity.END);
        TextView service = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_2, Ui.REGULAR);
        service.setText(o == null ? "" : data.table.name(o.service));
        provider.addView(service);
        TextView avail = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        avail.setText(PickRules.tileAvailability(o, data.services.subscribed()));
        provider.addView(avail);
        LinearLayout.LayoutParams plp = PickSetupPage.wrap();
        plp.leftMargin = Ui.px(12);
        row.addView(provider, plp);
        tile.addView(row, PickSetupPage.below(10));

        tile.addView(small(TitleText.meta(t), Ui.TEXT_2), PickSetupPage.below(4));
        tile.addView(small(TitleText.provenance(t), Ui.TEXT_3), PickSetupPage.below(2));
        View rule = new View(ctx);
        rule.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams rulelp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Ui.px(1)));
        rulelp.topMargin = Ui.px(14);
        tile.addView(rule, rulelp);
        TextView blurb = Ui.text(ctx, 21, Ui.TEXT_2, Ui.REGULAR);
        blurb.setText(t.synopsis);
        blurb.setMaxLines(2);
        blurb.setEllipsize(TextUtils.TruncateAt.END);
        PickSetupPage.lineHeight(blurb, 21 * 1.45f);
        tile.addView(blurb, PickSetupPage.below(14));

        tile.setContentDescription(t.name);
        tile.setOnFocusChangeListener((v, focused) -> {
            art.setRing(focused);
            float s = focused ? 1.03f : 1f;
            art.animate().scaleX(s).scaleY(s).setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
        });
        return Focus.make(tile, GROUP_PICKS, "pk-" + i, i == 0, v -> router.openTitle(t));
    }

    private TextView small(CharSequence text, int color) {
        TextView t = Ui.line(ctx, Ui.T_MIN, color, Ui.REGULAR);
        t.setGravity(Gravity.START);
        t.setText(text);
        return t;
    }
}
