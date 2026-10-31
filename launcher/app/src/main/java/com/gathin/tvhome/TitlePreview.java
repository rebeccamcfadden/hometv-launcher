package com.gathin.tvhome;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The title preview, Option C (design_preview/README.md, Title preview; src/screens/preview.ts; the .popup and .pv-*
 * styles in src/styles/screens.css): a 1728×900 popup over the page it was opened from, behind a 58% scrim that
 * covers the whole page, nav included. The art runs across the top under a fade, with a Back button on it. The
 * header has the title, its details and the actions (Watch, My list, Mark seen, Favorite, Trailer); the tabs below
 * it show Overview, Episodes (shows only), Where to watch, Cast &amp; crew and More like this in an area of fixed
 * height, so the card never jumps. Landing on a tab shows it. First focus is Watch; Back closes the popup and the
 * Shell puts focus back on the card it was opened from.
 */
final class TitlePreview extends Screen {
    private static final String G_ACTIONS = "actions", G_TABS = "tabs", G_WHERE = "wtw", G_CAST = "cast",
            G_SIMILAR = "similar", G_SEASONS = "seasons", G_EPISODES = "episodes";
    static final String KEY_WATCH = "watch";
    private static final float WIDTH = 1728, HEIGHT = 900, TOP = 90, CONTENT_HEIGHT = 220;
    /** Room around a row's items for the ring and scale-up, which the row clips at (screens.css .pv-row). */
    private static final float ROOM = Kit.RING_ROOM;
    /** How far a row may run below the content area, into the popup's 44 px bottom padding. */
    private static final float OVERHANG = 40;
    /** .pv-back .btn:not(.is-focused): the art's own dark at 60%, so Back reads on a bright sky. */
    private static final int BACK_FILL = 0x990D141D;
    /** .tab.is-active: white at 12%. */
    private static final int TAB_ACTIVE = 0x1FFFFFFF;
    /** .pv-details .rating: the secondary text at 50%. */
    private static final int RATING_LINE = 0x80ADB5C0;

    private enum Tab { OVERVIEW, EPISODES, WHERE, CAST, MORE }

    private final Context ctx;
    private final Router router;
    private final Data data;
    /** The title as shown; replaced when its details or a season's episodes arrive (see apply). */
    private Title t;
    private final Set<ServiceId> subscribed;
    /**
     * The offer Watch uses: included, else the cheapest rental, else the cheapest purchase (Offers.bestOffer). Worked
     * out again when the details (and so the real availability) or the prices arrive.
     */
    private Offer offer;
    /** Whether the real prices and the links into the apps were asked for (once the details are in). */
    private boolean askedPrices;
    private final View scrim;
    private final FrameLayout popup;
    private final ImageView art;
    private final View watch;
    private TextView titleView;
    private FrameLayout detailsBox;
    private final List<Toggle> toggles = new ArrayList<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private final FrameLayout content;
    private Tab active;
    private boolean shown;
    /** More like this, once the catalogue has answered; asked for the first time the tab shows. */
    private List<Title> similar;
    private boolean askedSimilar;
    /** The seasons whose episodes were asked for, so each is asked once. */
    private final Set<Integer> askedSeasons = new java.util.HashSet<>();

    /** The Episodes tab: the season shown, its chips, and the row its episodes are painted into. */
    private int season;
    private final List<View> seasonChips = new ArrayList<>();
    private final List<Integer> seasonNumbers = new ArrayList<>();
    private ViewGroup episodeBox;
    private LinearLayout episodeStrip;

    // 06-states A: the preview while its details load, and the error a tab shows when they failed.
    /** The plain gradient in the art's place until the details are in. */
    private final View artLoading;
    private View trailer;
    /** The other actions' shapes while the preview loads: two circles and a pill. */
    private final List<View> actionSkeletons = new ArrayList<>();
    /** Whether the header shows its skeletons now; once the first load has settled it never does again. */
    private boolean headerLoading;
    private boolean headerSettled;
    /** What the tab showing was drawn as: its content, its skeleton or the error box. */
    private CatalogState.Tab drawnState = CatalogState.Tab.READY;
    /** Kept in a field: the catalogue holds its listeners weakly. */
    private final Runnable onCatalog;

    /** Opening a preview records the title in the household's Recently viewed. */
    static TitlePreview create(Context ctx, Router router, Title t) {
        router.data().household.pushViewed(t.id);
        return new TitlePreview(ctx, router, t, new FrameLayout(ctx));
    }

    private TitlePreview(Context ctx, Router router, Title t, FrameLayout root) {
        super(t.name, root, Layer.POPUP);
        this.ctx = ctx;
        this.router = router;
        this.data = router.data();
        this.t = t;
        this.subscribed = data.services.subscribed();
        this.offer = data.bestOffer(t);
        this.season = PreviewRules.startSeason(t);

        scrim = new View(ctx);
        scrim.setBackgroundColor(Ui.SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        popup = new FrameLayout(ctx);
        popup.setBackground(Ui.round(Ui.BG_RAISED, Ui.RADIUS_XL));
        popup.setForeground(Ui.round(0, Ui.RADIUS_XL, Ui.LINE, 1));
        // The rounded outline clips the art and everything on it; the rows inside may still draw past the body.
        popup.setClipToOutline(true);
        popup.setClipChildren(false);
        popup.setElevation(Ui.px(30));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.px(WIDTH), Ui.px(HEIGHT), Gravity.TOP | Gravity.START);
        plp.leftMargin = Ui.px(Ui.SAFE_X);
        plp.topMargin = Ui.px(TOP);
        root.addView(popup, plp);

        art = new ImageView(ctx);
        // While the details load, a plain gradient holds the art's place (06-states A); the art draws over it.
        artLoading = new View(ctx);
        artLoading.setBackground(StateUi.artGradient());
        artLoading.setVisibility(t.detailed ? View.GONE : View.VISIBLE);
        popup.addView(artLoading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        popup.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        Art.into(art, t.art, Ui.px(WIDTH), Ui.px(HEIGHT));
        popup.addView(fade(ctx), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        watch = Kit.button(ctx, PreviewRules.watchLabel(t, offer, subscribed, data.table), R.drawable.ic_ui_play,
                Kit.PRIMARY, false, G_ACTIONS, KEY_WATCH, true, v -> watch());
        paintWatch();

        tabs.add(Tab.OVERVIEW);
        if (!t.isMovie()) tabs.add(Tab.EPISODES);
        tabs.add(Tab.WHERE);
        tabs.add(Tab.CAST);
        tabs.add(Tab.MORE);

        LinearLayout body = Kit.unclip(new LinearLayout(ctx));
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(head(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(30);
        body.addView(tabRow(), tlp);
        View divider = new View(ctx);
        divider.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Ui.px(1)));
        dlp.topMargin = Ui.px(18);
        dlp.bottomMargin = Ui.px(22);
        body.addView(divider, dlp);
        // The fixed height keeps the card still whichever tab shows. Its children are clipped to their own bounds,
        // which is where each row stops, as the prototype's rows do with overflow hidden.
        content = new FrameLayout(ctx);
        content.setClipChildren(true);
        body.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(CONTENT_HEIGHT)));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        blp.leftMargin = Ui.px(72);
        blp.rightMargin = Ui.px(72);
        blp.bottomMargin = Ui.px(44);
        popup.addView(body, blp);

        View back = Kit.button(ctx, ctx.getString(R.string.back), R.drawable.ic_ui_back, Kit.SMALL, false, null,
                "back", false, v -> router.shell().back());
        back.setBackground(Ui.states(Ui.round(BACK_FILL, Ui.RADIUS_PILL, Ui.LINE, 1), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_PILL, Ui.ACCENT, 1)));
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        backLp.leftMargin = Ui.px(48);
        backLp.topMargin = Ui.px(40);
        popup.addView(back, backLp);

        setTab(tabs.get(0));
        // A title from a search or a list is a summary: its details fill the preview in as they arrive.
        if (!t.detailed) data.catalog.details(t, this::apply);
        else askPrices();
        refreshState();
        // The details failing, or loading again once the network is back (MainActivity retries what failed), change
        // the header and the tab showing in place (06-states A).
        onCatalog = () -> {
            Title k = data.catalog.get(this.t.id);
            if (k != null && k.detailed && !this.t.detailed) apply(k);
            else refreshState();
        };
        data.catalog.addListener(onCatalog);
    }

    /**
     * The title's details or a season's episodes arrived: the header and the tab showing are drawn again with them,
     * and focus stays where it was (by key, if its view was rebuilt).
     */
    private void apply(Title now) {
        apply(now, 0);
    }

    /**
     * As {@link #apply(Title)}, for season {@code arrived}'s episodes (0 for the details): the episode row is
     * repainted only when that is the season showing, so a late answer for another season leaves the row, its
     * scroll and the focused episode alone.
     */
    private void apply(Title now, int arrived) {
        if (now == null || now == t) return;
        boolean seasonsChanged = now.seasons.size() != t.seasons.size();
        boolean offersChanged = !now.offers.equals(t.offers);
        t = now;
        offer = data.bestOffer(t);
        if (t.detailed) askPrices();
        if (!t.seasons.isEmpty() && t.season(season) == null) {
            int start = PreviewRules.startSeason(t);
            season = t.season(start) != null ? start : t.seasons.get(0).n;
        }
        titleView.setText(t.name);
        detailsBox.removeAllViews();
        detailsBox.addView(details());
        Art.into(art, t.art, Ui.px(WIDTH), Ui.px(HEIGHT));
        paintWatch();
        // 06-states A: the header's skeletons go, and a tab that showed its skeleton or an error is drawn again.
        refreshHeader();
        if (drawnState != tabState(active)) {
            redrawTab();
            return;
        }
        if (active == Tab.EPISODES && !seasonsChanged) {
            if (arrived != 0 && arrived != season) return;
            paintEpisodes(false);
            if (router.shell().top() == this) router.shell().refocus();
        } else if (active != Tab.MORE && (active != Tab.WHERE || offersChanged)) {
            rerender();
        }
    }

    /** V2 Phase 2: the title's real prices and the links into its services' apps, once its details are in. */
    private void askPrices() {
        if (askedPrices) return;
        askedPrices = true;
        data.catalog.prices(t, true, this::apply);
    }

    /** Draws the tab showing again, from the title as it is now. */
    private void rerender() {
        Tab tab = active;
        active = null;
        setTab(tab);
        if (router.shell().top() == this) router.shell().refocus();
    }

    // ---- Loading and errors (06-states A) ---------------------------------------------------------------------------

    /** The header's skeletons and the tab showing, brought up to date with what the catalogue says of the title. */
    private void refreshState() {
        refreshHeader();
        if (active != null && drawnState != tabState(active)) redrawTab();
    }

    /**
     * While the preview first loads, the title and Watch are real (known from the card that opened it), and the
     * details row, the other actions and the art are placeholders. A later reload (Retry, back online) leaves the
     * header as it is: only the tab shows its skeleton.
     */
    private void refreshHeader() {
        boolean loading = !headerSettled
                && CatalogState.headerLoading(data.catalog.isRemote(), t.detailed, data.catalog.pending(t.id));
        if (!loading) headerSettled = true;
        artLoading.setVisibility(t.detailed ? View.GONE : View.VISIBLE);
        if (loading == headerLoading) return;
        headerLoading = loading;
        for (Toggle toggle : toggles) toggle.button.setVisibility(loading ? View.GONE : View.VISIBLE);
        trailer.setVisibility(loading ? View.GONE : View.VISIBLE);
        for (View s : actionSkeletons) s.setVisibility(loading ? View.VISIBLE : View.GONE);
        detailsBox.removeAllViews();
        detailsBox.addView(details());
    }

    private CatalogState.Tab tabState(Tab tab) {
        boolean needsDetails = tab != Tab.MORE;
        return CatalogState.tab(data.catalog.isRemote(), t.detailed, data.catalog.pending(t.id),
                data.catalog.failed(t.id), needsDetails);
    }

    /** Whether the title's availability is still unknown, so Watch shows only its play icon. */
    private boolean watchBlank() {
        return CatalogState.watchBlank(data.catalog.isRemote(), t.detailed, !t.offers.isEmpty());
    }

    /** Watch's label, or only its play icon while the availability isn't in (rather than "Not on your services"). */
    private void paintWatch() {
        Kit.setLabel(watch, PreviewRules.watchLabel(t, offer, subscribed, data.table));
        StateUi.blank(watch, watchBlank(), ctx.getString(R.string.states_watch_unknown));
    }

    /**
     * Draws the tab showing again for a new state. Focus stays where it was; if it was on something in the tab that
     * went (Retry), it moves to the tab itself, something real.
     */
    private void redrawTab() {
        Shell shell = router.shell();
        View focused = shell.focused();
        boolean inContent = focused != null && Focus.isInside(content, focused);
        Tab tab = active;
        active = null;
        setTab(tab);
        if (shell.top() != this) return;
        if (inContent && !focused.isAttachedToWindow()) shell.focus(tabViews.get(tabs.indexOf(tab)));
        else shell.refocus();
    }

    /** Retry: focus waits on the tab while it loads again (its skeleton), then its content or the error again. */
    private void retry(Tab tab) {
        router.shell().focus(tabViews.get(tabs.indexOf(tab)));
        reload();
    }

    /** Asks for the title's details again; the tab showing turns to its skeleton while they come. */
    private void reload() {
        data.catalog.details(t, this::apply);
        if (active != null && drawnState != tabState(active)) redrawTab();
    }

    /** preview.ts SKELETONS: each tab's shape while it loads; nothing in it is focusable, so focus stays on the tabs. */
    private View skeleton(Tab tab) {
        List<View> items = new ArrayList<>();
        switch (tab) {
            case EPISODES: {
                LinearLayout wrap = new LinearLayout(ctx);
                wrap.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout seasons = new LinearLayout(ctx);
                seasons.setOrientation(LinearLayout.VERTICAL);
                for (int i = 0; i < 2; i++) {
                    View chip = StateUi.sk(ctx, 168, 54, Ui.RADIUS_PILL);
                    ((LinearLayout.LayoutParams) chip.getLayoutParams()).topMargin = i == 0 ? 0 : Ui.px(10);
                    seasons.addView(chip);
                }
                wrap.addView(seasons, new LinearLayout.LayoutParams(Ui.px(180), ViewGroup.LayoutParams.WRAP_CONTENT));
                for (int i = 0; i < 4; i++) items.add(StateUi.episodeSkeleton(ctx));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = Ui.px(28);
                wrap.addView(skeletonRow(items), lp);
                wrap.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return wrap;
            }
            case WHERE:
                for (int i = 0; i < 3; i++) items.add(StateUi.serviceSkeleton(ctx));
                break;
            case CAST:
                for (int i = 0; i < 6; i++) items.add(StateUi.personSkeleton(ctx));
                break;
            case MORE:
                for (int i = 0; i < 5; i++) items.add(StateUi.smallCardSkeleton(ctx));
                break;
            default:
                return StateUi.overviewSkeleton(ctx);
        }
        View row = skeletonRow(items);
        row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** Skeleton items 20 apart in a row, as .pv-row lays out the real ones. */
    private LinearLayout skeletonRow(List<View> items) {
        LinearLayout strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (View item : items) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(item.getLayoutParams());
            lp.leftMargin = strip.getChildCount() == 0 ? 0 : Ui.px(20);
            strip.addView(item, lp);
        }
        return strip;
    }

    /**
     * .inline-err, inside the failing tab only: "Couldn't load where to watch" (Overview: "Couldn't load details"),
     * that TMDB didn't answer, and Retry. The rest of the preview is what the catalogue had saved, or what the card
     * that opened it knew.
     */
    private View tabError(Tab tab) {
        String title = tab == Tab.OVERVIEW ? ctx.getString(R.string.states_overview_error)
                : ctx.getString(R.string.states_tab_error,
                        ctx.getString(tabLabel(tab)).toLowerCase(java.util.Locale.getDefault()));
        View retry = Kit.button(ctx, ctx.getString(R.string.states_retry), R.drawable.ic_state_retry, Kit.NORMAL,
                false, null, "retry-" + tab.name(), false, v -> retry(tab));
        return StateUi.errorBox(ctx, title, ctx.getString(R.string.states_tab_error_note), retry);
    }

    @Override
    View defaultFocus() {
        return watch;
    }

    /** Landing on a tab shows it; landing on a season shows its episodes. */
    @Override
    void onFocus(View v) {
        int tab = tabViews.indexOf(v);
        if (tab >= 0) {
            setTab(tabs.get(tab));
            return;
        }
        int chip = seasonChips.indexOf(v);
        if (chip >= 0) selectSeason(seasonNumbers.get(chip));
    }

    /**
     * ← and → stay in the tabs and in a tab's row, and stop at its ends. The prototype's plain nearest-item rule would
     * carry → from the last card up to Trailer and ← from the first tab, card or season to Back, which reads as
     * focus jumping away on a TV. Up and Down, and the actions row, move as in the prototype.
     */
    @Override
    boolean onKey(Key key, View focused) {
        if (key != Key.LEFT && key != Key.RIGHT) return false;
        String group = Focus.group(focused);
        if (G_SEASONS.equals(group)) return key == Key.LEFT;
        boolean row = G_TABS.equals(group) || G_WHERE.equals(group) || G_CAST.equals(group)
                || G_SIMILAR.equals(group) || G_EPISODES.equals(group);
        if (!row || !(focused.getParent() instanceof View)) return false;
        boolean moved = router.shell().moveWithin((View) focused.getParent(),
                key == Key.LEFT ? SpatialNav.Dir.LEFT : SpatialNav.Dir.RIGHT);
        // ← from the first episode goes on to the seasons beside the row.
        return moved || !(G_EPISODES.equals(group) && key == Key.LEFT);
    }

    /**
     * The popup rises in (screens.css .popup, rise-in) the first time it shows, not again when a dialog or a popup
     * above it closes. The toggles are repainted each time, since another preview of the same title may have
     * changed them.
     */
    @Override
    void onShow() {
        for (Toggle toggle : toggles) toggle.paint();
        if (shown) return;
        shown = true;
        if (Ui.reducedMotion()) return;
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(Ui.FAST_MS).start();
        popup.setAlpha(0f);
        popup.setTranslationY(Ui.px(24));
        popup.setScaleX(0.985f);
        popup.setScaleY(0.985f);
        popup.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(Ui.SLOW_MS)
                .setInterpolator(Ui.EASE).start();
    }

    // ---- Actions --------------------------------------------------------------------------------------------------

    /**
     * Included (or free): open the service. Paid: the rent/buy dialog for that store. None: the Router's toast. Blank
     * (the availability isn't in): nothing while it loads, and after a failure the details are asked for again.
     */
    private void watch() {
        if (watchBlank()) {
            if (data.catalog.failed(t.id)) reload();
            return;
        }
        if (offer != null && !data.isIncluded(offer)) {
            ServiceOffers s = PreviewRules.forService(t, offer.service, subscribed);
            if (s != null) {
                purchase(s);
                return;
            }
        }
        router.watch(t, offer);
    }

    private void purchase(ServiceOffers s) {
        router.shell().push(RentDialog.create(ctx, router, t, s));
    }

    /**
     * A Where to watch card: open an included or free service, or ask before renting or buying. A subscription the
     * household doesn't have has nothing to rent or buy: open its app (or the Router's "not installed" toast).
     */
    private void openService(ServiceOffers s) {
        if (s.watchable()) router.watch(t, PreviewRules.includedOffer(t, s.service, subscribed));
        else if (s.rent != null || s.buy != null) purchase(s);
        else router.watch(t, PreviewRules.subscriptionOffer(t, s.service));
    }

    /** My list, Mark seen and Favorite: an icon until focused, an on state, and a toast saying what changed. */
    private final class Toggle {
        final View button;
        final int iconOff, iconOn, labelOff, labelOn;
        final Predicate<String> get;

        Toggle(String key, int iconOff, int iconOn, int labelOff, int labelOn, int toastOn, int toastOff,
               Predicate<String> get, Predicate<String> flip) {
            this.iconOff = iconOff;
            this.iconOn = iconOn;
            this.labelOff = labelOff;
            this.labelOn = labelOn;
            this.get = get;
            boolean on = get.test(t.id);
            button = Kit.button(ctx, ctx.getString(on ? labelOn : labelOff), on ? iconOn : iconOff, Kit.ICON, on,
                    G_ACTIONS, key, false, v -> {
                        boolean now = flip.test(t.id);
                        paint();
                        router.toast(ctx.getString(now ? toastOn : toastOff));
                    });
            // Favorite keeps one label in both states, so the on state is told to accessibility services as checked.
            button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setCheckable(true);
                    info.setChecked(get.test(t.id));
                }
            });
        }

        void paint() {
            boolean on = get.test(t.id);
            Kit.setOn(button, on);
            Kit.setIcon(button, on ? iconOn : iconOff);
            Kit.setLabel(button, ctx.getString(on ? labelOn : labelOff));
        }
    }

    private View head() {
        LinearLayout head = Kit.unclip(new LinearLayout(ctx));
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);

        LinearLayout block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(ctx, Ui.T_DISPLAY, Ui.TEXT_1, Ui.REGULAR);
        title.setText(t.name);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        block.addView(title);
        titleView = title;
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.px(14);
        detailsBox = new FrameLayout(ctx);
        detailsBox.addView(details());
        block.addView(detailsBox, dlp);
        head.addView(block, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Household h = data.household;
        toggles.add(new Toggle("mylist", R.drawable.ic_ui_plus, R.drawable.ic_ui_check, R.string.preview_my_list,
                R.string.preview_on_my_list, R.string.preview_added_my_list, R.string.preview_removed_my_list,
                h::inMyList, h::toggleMyList));
        toggles.add(new Toggle("seen", R.drawable.ic_ui_eye, R.drawable.ic_ui_eye, R.string.preview_mark_seen,
                R.string.preview_seen, R.string.preview_marked_seen, R.string.preview_marked_unseen,
                h::isSeen, h::toggleSeen));
        toggles.add(new Toggle("fav", R.drawable.ic_ui_heart, R.drawable.ic_ui_heart_filled, R.string.preview_favorite,
                R.string.preview_favorite, R.string.preview_added_favorite, R.string.preview_removed_favorite,
                h::isFavorite, h::toggleFavorite));
        View trailer = Kit.button(ctx, ctx.getString(R.string.preview_trailer), R.drawable.ic_ui_trailer, Kit.NORMAL,
                false, G_ACTIONS, "trailer", false, v -> {
                    // 06-states A: offline, the trailer says it needs the internet rather than open YouTube.
                    if (StateUi.online(ctx)) router.openTrailer(t);
                    else router.toast(ctx.getString(R.string.states_trailer_offline));
                });
        this.trailer = trailer;

        LinearLayout actions = Kit.unclip(new LinearLayout(ctx));
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        List<View> buttons = new ArrayList<>();
        buttons.add(watch);
        for (Toggle toggle : toggles) buttons.add(toggle.button);
        buttons.add(trailer);
        // While the preview loads, the actions after Watch are two circles and a pill (preview.ts, loading).
        actionSkeletons.add(StateUi.sk(ctx, 66, 66, 33));
        actionSkeletons.add(StateUi.sk(ctx, 66, 66, 33));
        actionSkeletons.add(StateUi.sk(ctx, 150, 66, 33));
        for (View s : actionSkeletons) s.setVisibility(View.GONE);
        buttons.addAll(actionSkeletons);
        for (View b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (actionSkeletons.contains(b)) lp = new LinearLayout.LayoutParams(b.getLayoutParams());
            lp.leftMargin = actions.getChildCount() == 0 ? 0 : Ui.px(12);
            actions.addView(b, lp);
        }
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = Ui.px(40);
        head.addView(actions, alp);
        return head;
    }

    /** "2016 · Sci-fi, Drama · 1h 56m [PG-13] ★ 7.9" (screens.css .pv-details). */
    private View details() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (headerLoading) {
            // 06-states A: the details row's shape while the preview loads.
            float[] widths = {60, 140, 80};
            for (float w : widths) {
                View bar = StateUi.sk(ctx, w, 22);
                ((LinearLayout.LayoutParams) bar.getLayoutParams()).leftMargin = row.getChildCount() == 0 ? 0 : Ui.px(12);
                row.addView(bar);
            }
            return row;
        }
        List<String> parts = new ArrayList<>();
        if (t.year > 0) parts.add(String.valueOf(t.year));
        if (!t.genres.isEmpty()) parts.add(TextUtils.join(", ", t.genres));
        // Left out while the details load: a summary has no runtime or seasons yet.
        if (t.isMovie() ? t.runtime > 0 : !t.seasons.isEmpty()) {
            parts.add(t.isMovie() ? Offers.runtimeLabel(t.runtime) : TitleText.seasons(t.seasons.size()));
        }
        for (String part : parts) {
            if (row.getChildCount() > 0) add(row, detail("·", Ui.TEXT_3), 12);
            add(row, detail(part, Ui.TEXT_2), row.getChildCount() == 0 ? 0 : 12);
        }
        if (t.rating != null && !t.rating.isEmpty()) {
            TextView rating = Ui.line(ctx, 19, Ui.TEXT_2, Ui.REGULAR);
            rating.setText(t.rating);
            rating.setPadding(Ui.px(10), Ui.px(1), Ui.px(10), Ui.px(1));
            rating.setBackground(Ui.round(0, 4, RATING_LINE, 1));
            add(row, rating, row.getChildCount() == 0 ? 0 : 18);
        }
        if (t.score <= 0 && !t.detailed) return row;
        ImageView star = Ui.icon(ctx, R.drawable.ic_ui_star, 22, Ui.STAR);
        add(row, star, row.getChildCount() == 0 ? 0 : 12, 22);
        add(row, detail(TitleText.score(t.score), Ui.TEXT_2), 6);
        return row;
    }

    private TextView detail(String text, int color) {
        TextView v = Ui.line(ctx, 22, color, Ui.REGULAR);
        v.setText(text);
        return v;
    }

    private static void add(LinearLayout row, View v, float gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(gap);
        row.addView(v, lp);
    }

    private static void add(LinearLayout row, View v, float gap, float size) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(size), Ui.px(size));
        lp.leftMargin = Ui.px(gap);
        row.addView(v, lp);
    }

    // ---- Tabs -----------------------------------------------------------------------------------------------------

    /** .tab: 52 px pills, 22 px, tertiary; the active one white at 12% and semibold; focused, the accent fill. */
    private View tabRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            TextView v = Ui.line(ctx, 22, Ui.TEXT_3, Ui.REGULAR);
            v.setText(tabLabel(tab));
            v.setGravity(Gravity.CENTER);
            v.setPadding(Ui.px(22), 0, Ui.px(22), 0);
            v.setBackground(Ui.states(Ui.round(0, Ui.RADIUS_PILL), Ui.round(TAB_ACTIVE, Ui.RADIUS_PILL),
                    Ui.round(Ui.ACCENT, Ui.RADIUS_PILL)));
            v.setTextColor(Ui.colors(Ui.TEXT_3, Ui.TEXT_1, Ui.ON_ACCENT));
            v.setContentDescription(v.getText());
            Kit.reportsSelected(v);
            Focus.make(v, G_TABS, "tab-" + tab.name(), i == 0, null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.px(52));
            lp.leftMargin = i == 0 ? 0 : Ui.px(8);
            row.addView(v, lp);
            tabViews.add(v);
        }
        return row;
    }

    private int tabLabel(Tab tab) {
        switch (tab) {
            case EPISODES:
                return R.string.preview_tab_episodes;
            case WHERE:
                return R.string.preview_tab_where;
            case CAST:
                return R.string.preview_tab_cast;
            case MORE:
                return R.string.preview_tab_more;
            default:
                return R.string.preview_tab_overview;
        }
    }

    private void setTab(Tab tab) {
        if (tab == active) return;
        active = tab;
        for (int i = 0; i < tabs.size(); i++) {
            boolean on = tabs.get(i) == tab;
            TextView v = tabViews.get(i);
            v.setActivated(on);
            v.setTypeface(on ? Ui.MEDIUM : Ui.REGULAR);
            // Landing on a tab shows it, after its focus event went out: say again that it is now the selected one.
            if (on) v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        }
        content.removeAllViews();
        seasonChips.clear();
        seasonNumbers.clear();
        episodeBox = null;
        episodeStrip = null;
        content.addView(render(tab));
    }

    /** The tab's content, with FrameLayout params for the content area. */
    private View render(Tab tab) {
        // 06-states A: the tab's skeleton while the title loads, or the error box when its data failed.
        drawnState = tabState(tab);
        if (drawnState == CatalogState.Tab.LOADING) return skeleton(tab);
        if (drawnState == CatalogState.Tab.ERROR) return tabError(tab);
        switch (tab) {
            case EPISODES:
                return episodes();
            case WHERE:
                return whereToWatch();
            case CAST:
                return cast();
            case MORE:
                return moreLikeThis();
            default:
                return overview();
        }
    }

    // ---- Overview -------------------------------------------------------------------------------------------------

    /** The synopsis (820 wide), then the facts: availability, Starring, and the director or creator. */
    private View overview() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView synopsis = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        synopsis.setText(t.synopsis);
        lineHeight(synopsis, 36);
        synopsis.setMaxLines(6);
        synopsis.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(synopsis, new LinearLayout.LayoutParams(Ui.px(820), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout facts = new LinearLayout(ctx);
        facts.setOrientation(LinearLayout.VERTICAL);
        PreviewRules.Availability a = PreviewRules.availability(t, subscribed, data.table);
        LinearLayout first = fact();
        if (a.badge != null) first.addView(badge(ctx, a.badge));
        first.addView(factText(a.text, Ui.TEXT_2), factLp(first));
        if (a.elsewhere != null) first.addView(factText(a.elsewhere, Ui.TEXT_3), factLp(first));
        facts.addView(first);
        String starring = PreviewRules.starring(t);
        if (starring != null) facts.addView(namedFact(ctx.getString(R.string.preview_starring), starring), factGap());
        String role = PreviewRules.leadRole(t);
        if (role != null) facts.addView(namedFact(role, PreviewRules.leadNames(t)), factGap());
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        flp.leftMargin = Ui.px(64);
        row.addView(facts, flp);
        row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout fact() {
        LinearLayout f = new LinearLayout(ctx);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        return f;
    }

    private LinearLayout namedFact(String name, String value) {
        LinearLayout f = fact();
        f.addView(factText(name, Ui.TEXT_3));
        f.addView(factText(value, Ui.TEXT_2), factLp(f));
        return f;
    }

    private TextView factText(String text, int color) {
        TextView v = Ui.line(ctx, 21, color, Ui.REGULAR);
        v.setText(text);
        return v;
    }

    /** .pv-fact: 10 px between the parts of a fact. */
    private static LinearLayout.LayoutParams factLp(LinearLayout f) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = f.getChildCount() == 0 ? 0 : Ui.px(10);
        return lp;
    }

    /** .pv-facts: 14 px between facts. */
    private static LinearLayout.LayoutParams factGap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(14);
        return lp;
    }

    // ---- Episodes -------------------------------------------------------------------------------------------------

    /** The seasons stacked on the left (180 wide); landing on one shows its episodes in the row beside it. */
    private View episodes() {
        int room = Ui.px(ROOM);
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        // wrap reaches ROOM past the content area on every side, and clips each box to its own bounds.
        wrap.setClipChildren(true);

        ViewGroup seasonsBox = ScrollBox.vertical(ctx, ROOM);
        seasonsBox.setPadding(room, room, room, room);
        LinearLayout column = Kit.unclip(new LinearLayout(ctx));
        column.setOrientation(LinearLayout.VERTICAL);
        for (Season s : t.seasons) {
            View chip = Kit.chip(ctx, ctx.getString(R.string.preview_season, s.n), s.n == season, false, G_SEASONS,
                    "season-" + s.n, s.n == season, null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = column.getChildCount() == 0 ? 0 : Ui.px(10);
            column.addView(chip, lp);
            seasonChips.add(chip);
            seasonNumbers.add(s.n);
        }
        seasonsBox.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(seasonsBox, new LinearLayout.LayoutParams(Ui.px(180) + 2 * room, Ui.px(CONTENT_HEIGHT) + 2 * room));

        episodeBox = ScrollBox.horizontal(ctx, ROOM);
        episodeBox.setPadding(room, room, room, room);
        episodeStrip = Kit.unclip(new LinearLayout(ctx));
        episodeStrip.setOrientation(LinearLayout.HORIZONTAL);
        episodeBox.addView(episodeStrip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        elp.leftMargin = Ui.px(28) - 2 * room;
        wrap.addView(episodeBox, elp);
        paintEpisodes(true);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(-room, -room, -room, -room);
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private void selectSeason(int n) {
        if (n == season) return;
        season = n;
        for (int i = 0; i < seasonChips.size(); i++) Kit.setSelected(seasonChips.get(i), seasonNumbers.get(i) == n);
        paintEpisodes(true);
    }

    /** The showing season's episodes; {@code fromStart} scrolls the row back to its first episode. */
    private void paintEpisodes(boolean fromStart) {
        if (episodeStrip == null) return;
        episodeStrip.removeAllViews();
        for (Season s : t.seasons) {
            if (s.n != season) continue;
            if (s.needsEpisodes() && askedSeasons.add(s.n)) {
                // From TMDB a season's episodes come on their own; apply() paints them when they arrive.
                int n = s.n;
                data.catalog.season(t, n, now -> apply(now, n));
            }
            for (Episode e : s.episodes) {
                View card = episodeCard(e);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(300), Ui.px(196));
                lp.leftMargin = episodeStrip.getChildCount() == 0 ? 0 : Ui.px(20);
                episodeStrip.addView(card, lp);
            }
        }
        if (fromStart) episodeBox.scrollTo(0, 0);
    }

    /** .episode: 300×196, "E3" with a tick or the "Up next" badge (and an accent border), the name, the runtime. */
    private View episodeCard(Episode e) {
        boolean watched = PreviewRules.watched(t, season, e.n);
        boolean next = PreviewRules.upNext(t, season, e.n);
        RingFrame card = new RingFrame(ctx, Ui.RADIUS_L, false, Ui.SURFACE);
        if (next) card.content().setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.ACCENT, 1));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(20), Ui.px(20), Ui.px(20), Ui.px(20));

        LinearLayout num = fact();
        TextView number = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        number.setText(ctx.getString(R.string.preview_episode, e.n));
        num.addView(number);
        if (next) {
            num.addView(badge(ctx, ctx.getString(R.string.preview_up_next)), factLp(num));
        } else if (watched) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20));
            lp.leftMargin = Ui.px(10);
            num.addView(Ui.icon(ctx, R.drawable.ic_ui_check, 20, Ui.GOOD), lp);
        }
        col.addView(num, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(26)));

        TextView name = Ui.text(ctx, 23, Ui.TEXT_1, Ui.REGULAR);
        name.setText(e.name);
        lineHeight(name, 30);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.px(12);
        col.addView(name, nlp);

        TextView meta = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        meta.setText(PreviewRules.episodeMeta(e, watched));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.px(10);
        col.addView(meta, mlp);

        card.content().addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Everything the card shows, so TalkBack can tell watched episodes and the next one apart.
        StringBuilder said = new StringBuilder().append(number.getText()).append(", ").append(e.name);
        if (next) said.append(", ").append(ctx.getString(R.string.preview_up_next));
        said.append(", ").append(meta.getText());
        card.setContentDescription(said);
        return ringCard(card, G_EPISODES, "ep-" + season + "-" + e.n, PreviewRules.isDefaultEpisode(t, season, e.n),
                v -> watch());
    }

    // ---- Where to watch -------------------------------------------------------------------------------------------

    /**
     * Design 07-prices A: one card per service ({@link PriceCards#cards}): included and free ones first, then the
     * stores, cheapest first, the cheapest tagged; a note when there are none. On the real catalogue, the sources'
     * credit below the row, "Availability: JustWatch · Prices: Apple", and offline when the prices were saved.
     */
    private View whereToWatch() {
        List<PriceCards.Card> services = PriceCards.cards(t, subscribed, data.stores(), data.services.installed());
        View body;
        if (services.isEmpty()) {
            body = emptyNote(t.detailed ? R.string.prices_where_none : R.string.preview_not_loaded);
        } else {
            List<View> cards = new ArrayList<>();
            for (int i = 0; i < services.size(); i++) {
                PriceCards.Card c = services.get(i);
                ServiceId id = c.offers.service;
                // The store's name only for a card that sells: an unsubscribed Viaplay is still "Viaplay".
                boolean sells = c.offers.rent != null || c.offers.buy != null;
                String name = sells && !c.offers.watchable() ? data.table.storeName(id) : data.table.name(id);
                cards.add(PriceViews.serviceCard(ctx, c, name, G_WHERE, i == 0, v -> openService(c.offers)));
            }
            body = row(cards);
        }
        String credit = PriceViews.credit(ctx, data.catalog, t);
        if (credit == null) return body;
        // The box reaches ROOM past the content area, as the row does, so the focused card's ring isn't cut off.
        int room = Ui.px(ROOM);
        FrameLayout box = Kit.unclip(new FrameLayout(ctx));
        box.setPadding(room, room, room, room);
        box.addView(body);
        TextView caption = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        caption.setText(credit);
        box.addView(caption, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(-room, -room, -room, -room);
        box.setLayoutParams(lp);
        return box;
    }

    // ---- Cast & crew and More like this ---------------------------------------------------------------------------

    private View cast() {
        List<Person> people = new ArrayList<>(t.cast);
        people.addAll(t.crew);
        if (people.isEmpty()) return emptyNote(t.detailed ? R.string.preview_no_cast : R.string.preview_not_loaded);
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < people.size(); i++) {
            Person p = people.get(i);
            cards.add(Kit.personCard(ctx, p, G_CAST, "cast-" + i, i == 0, v -> router.openPerson(p)));
        }
        return row(cards);
    }

    /**
     * Similar titles: TMDB's recommendations, asked for when the tab first shows (the mock catalogue's own rule
     * answers at once); OK opens their preview on top of this one. Until they come, the row is empty.
     */
    private View moreLikeThis() {
        if (similar == null && !askedSimilar) {
            askedSimilar = true;
            data.catalog.similar(t, list -> {
                similar = list;
                if (active == Tab.MORE && content.getChildCount() > 0) rerender();
            });
        }
        List<View> cards = new ArrayList<>();
        List<Title> shown = similar == null ? new ArrayList<>() : similar;
        int n = Math.min(shown.size(), PreviewRules.SIMILAR_MAX);
        if (similar != null && n == 0) return emptyNote(R.string.preview_no_similar);
        for (int i = 0; i < n; i++) {
            Title o = shown.get(i);
            cards.add(Kit.smallCard(ctx, o, null, data, G_SIMILAR, "sim-" + o.id, i == 0, v -> router.openTitle(o)));
        }
        return row(cards);
    }

    private View emptyNote(int text) {
        TextView empty = Ui.text(ctx, 22, Ui.TEXT_3, Ui.REGULAR);
        empty.setText(text);
        empty.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return empty;
    }

    // ---- Pieces ---------------------------------------------------------------------------------------------------

    /**
     * .pv-row: items 20 apart in a horizontal ScrollBox that reaches ROOM past the content area on every side (for
     * the focus ring and scale-up) and is clipped there by the content area.
     */
    private View row(List<View> items) {
        int room = Ui.px(ROOM);
        ViewGroup box = ScrollBox.horizontal(ctx, ROOM);
        box.setPadding(room, room, room, room);
        LinearLayout strip = Kit.unclip(new LinearLayout(ctx));
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (View item : items) {
            ViewGroup.LayoutParams given = item.getLayoutParams();
            LinearLayout.LayoutParams lp = given != null ? new LinearLayout.LayoutParams(given)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = strip.getChildCount() == 0 ? 0 : Ui.px(20);
            strip.addView(item, lp);
        }
        box.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        // Small cards (about 228 tall) run past the 220 px area, as in the prototype, into the popup's bottom
        // padding: the deeper bottom margin only lets the box measure that tall.
        lp.setMargins(-room, -room, -room, -room - Ui.px(OVERHANG));
        box.setLayoutParams(lp);
        return box;
    }

    /** A card whose focused state is the 4 px accent ring and a 3% scale-up (.service, .episode .is-focused). */
    private static View ringCard(RingFrame card, String group, String key, boolean isDefault, View.OnClickListener onOk) {
        card.setOnFocusChangeListener((v, focused) -> card.setRing(focused));
        Ui.scaleOnFocus(card, 1.03f);
        return Focus.make(card, group, key, isDefault, onOk);
    }

    /** base.css .badge: the green "Included", "Free" or "Up next" pill (18 px, the spec's minimum, for its 17). */
    static TextView badge(Context ctx, String text) {
        TextView b = Ui.line(ctx, Ui.T_MIN, Ui.GOOD, Ui.MEDIUM);
        b.setText(text);
        b.setPadding(Ui.px(12), Ui.px(3), Ui.px(12), Ui.px(3));
        b.setBackground(Ui.round(Ui.GOOD_SOFT, Ui.RADIUS_PILL));
        return b;
    }

    /** CSS line-height in design px for a multi-line text. */
    static void lineHeight(TextView v, float design) {
        int natural = v.getPaint().getFontMetricsInt(null);
        v.setLineSpacing(Ui.px(design) - natural, 1f);
    }

    /**
     * .pv-fade: the art's dark at 45% at the top for the Back button, clear from 14% to 28%, then 90% at half way
     * and solid from 60%, under the title and tabs.
     */
    private static View fade(Context ctx) {
        View v = new View(ctx);
        PaintDrawable d = new PaintDrawable();
        d.setShape(new RectShape());
        d.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(0, 0, 0, height,
                        new int[]{0x730D141D, 0x000D141D, 0x000D141D, 0xE60D141D, 0xFF0D141D, 0xFF0D141D},
                        new float[]{0f, 0.14f, 0.28f, 0.5f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            }
        });
        v.setBackground(d);
        return v;
    }
}
