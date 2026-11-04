package com.hometv.launcher;

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
 * setup), and a bar under the tiles for the focused pick with Title details, Trailer (F47: YouTube straight from here)
 * and Open in its service. Each roll counts the matches again, so a title marked seen meanwhile drops out. The picks
 * showing are kept across a trip to another app, also when Android ends the process (MainActivity's place, F26).
 */
final class PickResultsPage extends Screen {
    private static final String GROUP_PICKS = "picks";
    private static final String GROUP_BAR = "bar";
    /** {@code .pick-cards}: four columns, 20 apart, across the title-safe width. */
    private static final int GAP = 20;
    private static final float TILE_W = (Ui.STAGE_W - 2f * Ui.SAFE_X - (PickRules.PICKS - 1) * GAP) / PickRules.PICKS;
    private static final int ART_H = 243;
    /** F47: how long Trailer waits for a pick's details before it opens YouTube's search for the trailer. */
    static final long TRAILER_WAIT_MS = 3000;

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
    private final View adjust, reroll, details, trailer, open;
    private List<Title> picks = new ArrayList<>();
    private Title current;
    /** F47: the Trailer press waiting for its title's details (TMDB's trailer id); a newer press or a pick moves on. */
    private int trailerAsk;
    /** F47: the ids of kept picks still to show after a restore (null once all are in, or after Reroll). */
    private List<String> kept;
    private final Runnable onCatalog;

    PickResultsPage(Context ctx, NavBar.Host host, Router router, PickRules.State state, PickRules.Household household,
                    Random random) {
        this(ctx, host, router, state, household, random, null);
    }

    /**
     * {@code keptIds}: F47, the picks that were showing before Android ended the process (MainActivity's place, F26),
     * shown again rather than a new roll; null rolls as usual.
     */
    PickResultsPage(Context ctx, NavBar.Host host, Router router, PickRules.State state, PickRules.Household household,
                    Random random, List<String> keptIds) {
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
        TextView title = Ui.line(ctx, 54, Ui.TEXT_1, Ui.display(Ui.REGULAR));
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
        bar.setBackground(Ui.round(Ui.SURFACE, 20, Ui.LINE, Ui.LINE_W));
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
        trailer = Kit.button(ctx, ctx.getString(R.string.preview_trailer), R.drawable.ic_ui_trailer, Kit.NORMAL, false,
                GROUP_BAR, "trailer", false, v -> trailer());
        LinearLayout.LayoutParams tlp = PickSetupPage.wrap();
        tlp.leftMargin = Ui.px(14);
        bar.addView(trailer, tlp);
        open = Kit.button(ctx, "", R.drawable.ic_ui_play, Kit.PRIMARY, false, GROUP_BAR, "open", true, v -> open());
        LinearLayout.LayoutParams olp = PickSetupPage.wrap();
        olp.leftMargin = Ui.px(14);
        bar.addView(open, olp);
        body.addView(bar, PickSetupPage.below(26));

        page.addView(body, PickSetupPage.bodyParams());
        page.addView(Kit.guide(ctx, ctx.getString(data.catalog.isRemote() ? R.string.prices_pick_guide
                        : R.string.pick_results_guide_left),
                ctx.getString(R.string.pick_results_guide_right)));
        // On a cold start the catalogue learns the lists' titles after Home has drawn: kept picks fill in as they
        // arrive (the catalogue holds this listener weakly).
        onCatalog = () -> {
            if (kept != null) showKept();
        };
        data.catalog.addListener(onCatalog);
        if (keptIds != null) {
            kept = new ArrayList<>(keptIds);
            showKept();
        } else {
            roll();
        }
    }

    /** F47: the ids of the picks showing, in order, for MainActivity's place (F26). */
    ArrayList<String> ids() {
        if (kept != null) return new ArrayList<>(kept);
        ArrayList<String> out = new ArrayList<>();
        for (Title t : picks) out.add(t.id);
        return out;
    }

    /** The kept picks the catalogue knows now; done once all of them are in. Focus follows Comeback's pending key. */
    private void showKept() {
        List<Title> have = PickRules.kept(kept, data.catalog::get);
        if (have.size() == kept.size()) kept = null;
        if (have.size() == picks.size() && kept != null) return;
        picks = have;
        draw(PickRules.matches(state, data.catalog.all(), household, data.services.subscribed(),
                data.services.installed()).size());
        if (router.shell().top() == this) router.shell().refocus();
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
        kept = null;
        List<Title> matches = PickRules.matches(state, data.catalog.all(), household, data.services.subscribed(),
                data.services.installed());
        picks = PickRules.roll(matches, picks, random);
        draw(matches.size());
    }

    /** The tiles for {@link #picks}, the context line and Reroll's count of {@code matches}. */
    private void draw(int matches) {
        Set<ServiceId> subscribed = data.services.subscribed();
        int n = PickRules.pickCount(matches);
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
        // Another pick in the bar: a Trailer press still waiting for the last one's details doesn't open.
        if (t != current) trailerAsk++;
        current = t;
        Offer o = data.bestOffer(t);
        Set<ServiceId> subscribed = data.services.subscribed();
        barTitle.setText(t.name);
        barNote.setText(PickRules.barNote(o, subscribed, data.table));
        Kit.setLabel(open, PickRules.openLabel(o, subscribed, data.table));
    }

    /**
     * F47: the pick's trailer in YouTube straight from here, as the preview's Trailer plays it (Router.openTrailer).
     * Focus goes to the pick's tile first, so Back from YouTube lands on the title that played, among the same picks
     * (F26 keeps this page, and MainActivity's place keeps it when Android ends the process). A pick's details, with
     * TMDB's trailer id, may not be in yet: they are asked for, and after {@link #TRAILER_WAIT_MS} without them the
     * trailer opens anyway, as YouTube's search for it.
     */
    private void trailer() {
        Title t = current;
        if (t == null) return;
        View tile = tileOf(t);
        if (tile != null) router.shell().focus(tile);
        if (!StateUi.online(ctx)) {
            router.toast(ctx.getString(R.string.states_trailer_offline));
            return;
        }
        int ask = ++trailerAsk;
        if (t.detailed) {
            router.openTrailer(t);
            return;
        }
        data.catalog.details(t, d -> playIfStill(ask, d));
        view.postDelayed(() -> playIfStill(ask, t), TRAILER_WAIT_MS);
    }

    /** The trailer asked for by press {@code ask}, unless the household has moved on (another pick, Reroll, Back). */
    private void playIfStill(int ask, Title t) {
        if (ask != trailerAsk) return;
        trailerAsk++;
        if (router.shell().top() == this) router.openTrailer(t);
    }

    private View tileOf(Title t) {
        int i = picks.indexOf(t);
        return i >= 0 && i < cards.getChildCount() ? cards.getChildAt(i) : null;
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
        TextView lists = small(TitleText.provenance(t, household), Ui.TEXT_3);
        tile.addView(lists, PickSetupPage.below(2));
        tile.setTag(R.id.kit_provenance, lists);
        View rule = new View(ctx);
        rule.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams rulelp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.linePx());
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
        // Keyed by the title, so a kept focus (F26/F47) finds it even while other kept picks are still to arrive.
        return Kit.titleCard(Focus.make(tile, GROUP_PICKS, "pk-" + t.id, i == 0, v -> router.openTitle(t)), t);
    }

    private TextView small(CharSequence text, int color) {
        TextView t = Ui.line(ctx, Ui.T_MIN, color, Ui.REGULAR);
        t.setGravity(Gravity.START);
        t.setText(text);
        return t;
    }
}
