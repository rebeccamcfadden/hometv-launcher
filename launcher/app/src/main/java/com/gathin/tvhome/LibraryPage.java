package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Movies and TV Shows, one class for both (design_preview/src/screens/library.ts and the library styles in
 * screens.css; README, Movies and TV Shows (V1)). V1 is lists-only: the TV's own My list and the TMDB lists shown as
 * chips, by their names (the mock Household and Friends before the TV has had an account; {@link LibraryLists}),
 * filtered by the Filters drawer ({@link FiltersDrawer}, {@link LibraryFilters}) and shown in a four-column grid of
 * library cards that scrolls under the nav. The chips are one row that scrolls sideways with focus when they don't
 * fit; a chosen list that is hidden or gone since falls back to All lists.
 *
 * <p>The page is made once per tab and kept, so it keeps its list, filters, scroll and focus when you switch tabs.
 * It reads the household again each time it shows (a title added to My list in the title preview appears here), and
 * rebuilds only the parts whose content changed, so cards don't flash their placeholder when nothing moved.
 *
 * <p>F2 and F4 (the project lead, 2026-11-01): the page starts with no filters, All lists and the Sort chip's A–Z
 * ({@link LibrarySort}) each time the TV is turned on; what is set on it is kept while it is left, and cleared again
 * after an hour away ({@link LibraryRecall}). Each page keeps its own, for as long as the launcher's process runs, so
 * a page made again within the hour (a theme or a setting changing) gets them back.
 */
final class LibraryPage extends Screen {

    private static final String GROUP_LISTS = "lists";
    private static final String GROUP_GRID = "grid";
    private static final String KEY_PICK = "pick";
    private static final String KEY_FILTERS = "filters";
    private static final String KEY_SORT = "sort";
    private static final String KEY_CLEAR = "clear-filters";
    /** What each page keeps while it is left (F2): the process's, so a TV turned on starts with none. */
    private static final LibraryRecall RECALL = new LibraryRecall(SystemClock::elapsedRealtime);
    /** Whether the screen-off receiver that clears {@link #RECALL} is registered (once a process, on the app). */
    private static boolean watchingStandby;

    // Sizes from screens.css (.page-scroll, .lib-*), in design px.
    private static final int SCROLL_TOP = 150;
    private static final int SCROLL_PAD = 70;
    private static final int COLUMNS = 4;
    private static final int CARD_W = 417;
    private static final int GAP_X = 20;
    private static final int GAP_Y = 50;
    private static final int PAGE_END = 180;

    private final Context ctx;
    private final Router router;
    private final Data data;
    private final Shell shell;
    private final Kind kind;
    final LibraryFilters filters;
    /** The chosen chip ({@link ShownList#key}), and its list as the last render found it. */
    private String listKey = ShownList.ALL.key;
    private ShownList list = ShownList.ALL;
    /** The Sort chip's order (F4). */
    private LibrarySort sort = LibrarySort.AZ;
    /** {@link LibraryRecall#offs} when the page last showed: another count means the TV was off since (F2). */
    private int offsSeen = RECALL.offs();

    private final ViewGroup box;
    private final View pick;
    private final TextView context;
    private final TextView scope;
    private final TextView filterSummary;
    private final View sortButton;
    private final View filtersButton;
    private final LinearLayout chips;
    private final TextView listTitle;
    private final TextView listStatus;
    private final LinearLayout grid;

    /** What the chips and the grid were last built from; they are rebuilt only when it changes. */
    private String chipsBuilt;
    private String gridBuilt;
    /** The titles the grid shows, compared by identity: a title filled in since is a new object, so its card redraws. */
    private List<Title> gridTitles;
    /** Kept in a field: the catalogue holds its listeners weakly. */
    private final Runnable onCatalog;

    // 06-states A: loading, offline and "nothing saved" (CatalogState says which; StateUi draws them).
    private static final String KEY_WIFI = "wifi-settings";
    private static final String KEY_TRY = "try-again";
    private static final long TRYING_MS = 1200;
    private static final int SKELETON_CARDS = 8;
    /** The longest the skeletons hold the grid once some titles are in (a slow network): 8 seconds. */
    private static final long LOADING_MAX_MS = 8000;
    private long loadingSince;
    private final Runnable renderLater = this::renderIfShown;
    private CatalogState.Lists state = CatalogState.Lists.ONLINE;
    /** The lists have loaded once: later titles fill in where they are, without the skeletons. */
    private boolean settled;
    /** Try again is checking: its label says "Trying…" for a moment. */
    private boolean retrying;
    /** The amber pill that takes the scope line's place offline. */
    private final View offlinePill;
    private final TextView guideRight;
    /** How far inside the chip row's edges a focused chip stops when the row scrolls (library.ts data-scroll-pad). */
    private static final float CHIP_PAD = 48;

    static LibraryPage create(Context ctx, NavBar.Host host, Tab tab) {
        return new LibraryPage(ctx, host, tab, Pages.page(ctx, host, tab));
    }

    private LibraryPage(Context ctx, NavBar.Host host, Tab tab, FrameLayout root) {
        super(ctx.getString(tab == Tab.TV ? R.string.nav_tv : R.string.nav_movies), root, Layer.PAGE);
        this.ctx = ctx;
        this.router = (Router) ctx;
        this.data = router.data();
        this.shell = host.shell();
        this.kind = tab == Tab.TV ? Kind.SHOW : Kind.MOVIE;
        Household household = data.household;
        filters = new LibraryFilters(kind, data.catalog.genres(kind), data.services.subscribed(),
                data.services.installed(), household::isSeen);
        restoreSaved();

        box = ScrollBox.vertical(ctx, SCROLL_PAD);
        LinearLayout col = Kit.unclip(new LinearLayout(ctx));
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(Ui.SAFE_X), 0, Ui.px(Ui.SAFE_X), 0);
        box.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        blp.topMargin = Ui.px(SCROLL_TOP);
        root.addView(box, blp);

        // .lib-intro: the heading and its context on the left, Pick for me on the right.
        LinearLayout intro = Kit.unclip(new LinearLayout(ctx));
        intro.setOrientation(LinearLayout.HORIZONTAL);
        intro.setGravity(Gravity.TOP);
        LinearLayout heading = new LinearLayout(ctx);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.line(ctx, Ui.T_H1, Ui.TEXT_1, Ui.display(Ui.REGULAR));
        title.setText(label);
        heading.addView(title);
        context = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        heading.addView(context, below(8));
        intro.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pick = Kit.button(ctx, ctx.getString(R.string.library_pick), R.drawable.ic_ui_shuffle, Kit.PRIMARY, false,
                null, KEY_PICK, false, v -> router.openPick(kind));
        intro.addView(pick, wrap());
        col.addView(intro, below(30));

        // .lib-scope: what the page draws from, and the filters on it with the Filters button.
        LinearLayout scopeRow = Kit.unclip(new LinearLayout(ctx));
        scopeRow.setOrientation(LinearLayout.HORIZONTAL);
        scopeRow.setGravity(Gravity.CENTER_VERTICAL);
        scope = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        // Offline, the amber pill takes the scope line's place (06-states A).
        FrameLayout scopeSlot = new FrameLayout(ctx);
        scopeSlot.addView(scope, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        offlinePill = StateUi.pill(ctx, "");
        offlinePill.setVisibility(View.GONE);
        scopeSlot.addView(offlinePill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START));
        scopeRow.addView(scopeSlot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        filterSummary = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        scopeRow.addView(filterSummary, wrap());
        // F4: the Sort chip, a small button like Filters beside it (no Figma frame yet).
        sortButton = Kit.button(ctx, sortChipLabel(), R.drawable.ic_ui_sort, Kit.SMALL, false, null, KEY_SORT, false,
                v -> openSort());
        LinearLayout.LayoutParams slp = wrap();
        slp.leftMargin = Ui.px(22);
        scopeRow.addView(sortButton, slp);
        filtersButton = Kit.button(ctx, ctx.getString(R.string.library_filters), R.drawable.ic_ui_filter, Kit.SMALL,
                false, null, KEY_FILTERS, false, v -> openFilters());
        LinearLayout.LayoutParams flp = wrap();
        flp.leftMargin = Ui.px(22);
        scopeRow.addView(filtersButton, flp);
        col.addView(scopeRow, below(18));

        // .chips: the list chips with their counts, one row that scrolls sideways with focus when they don't fit.
        chips = Kit.unclip(new LinearLayout(ctx));
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(Kit.chipStrip(ctx, chips, CHIP_PAD), Kit.chipStripParams(16));

        // .lib-list-head: the chosen list's name and how many of its titles show.
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setBaselineAligned(true);
        listTitle = Ui.line(ctx, Ui.T_H2, Ui.TEXT_1, Ui.REGULAR);
        head.addView(listTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        listStatus = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        head.addView(listStatus, wrap());
        col.addView(head, below(40));

        grid = Kit.unclip(new LinearLayout(ctx));
        grid.setOrientation(LinearLayout.VERTICAL);
        col.addView(grid, below(22));
        col.addView(new View(ctx), new LinearLayout.LayoutParams(1, Ui.px(PAGE_END)));

        root.addView(Kit.bottomFade(ctx));
        // The real catalogue's scores and availability are not illustrative (V2): say where they come from.
        View guide = Kit.guide(ctx, ctx.getString(data.catalog.isRemote() ? R.string.prices_library_guide
                        : R.string.library_guide_left),
                ctx.getString(R.string.library_guide_right));
        guideRight = Kit.guideRight(guide);
        root.addView(guide);

        // Titles fill in as their details arrive: what is cached shows first, the rest as it comes.
        onCatalog = () -> {
            if (shell.top() == this) render();
        };
        data.catalog.addListener(onCatalog);
        // Offline and back online change the page in place (06-states A).
        StateUi.watchInternet(root, host.navModel(), online -> {
            if (shell.top() == this) render();
        });
        // F2: another app taking the screen leaves the page too, and coming back is a return. A page not showing is
        // detached, which also reads as hidden; onLeave and onShow say the same. Standby is the TV turned off: the
        // window stays visible through it on Android 9, so the page checks LibraryRecall's standby count when its
        // window has focus again.
        watchStandby(ctx);
        root.addView(new View(ctx) {
            @Override
            protected void onWindowVisibilityChanged(int visibility) {
                super.onWindowVisibilityChanged(visibility);
                if (visibility != View.VISIBLE) markLeft();
                else if (recall() && shell.top() == LibraryPage.this) startedAgain();
            }

            @Override
            public void onWindowFocusChanged(boolean hasWindowFocus) {
                super.onWindowFocusChanged(hasWindowFocus);
                if (hasWindowFocus && getWindowVisibility() == View.VISIBLE && recall()
                        && shell.top() == LibraryPage.this) {
                    startedAgain();
                }
            }
        }, new FrameLayout.LayoutParams(0, 0));
        render();
    }

    /**
     * Cleared while on screen (standby, or back from an app after the hour): render, and put focus on the page's
     * default, since the focused card may now be scrolled out of view and refocus() keeps a view that is still there.
     * Focus on the nav stays where it is; a popup or dialog over the page is never on top here.
     */
    private void startedAgain() {
        render();
        View f = shell.focused();
        if (f == null || Focus.isInside(box, f)) shell.focus(shell.restoreTarget(this));
    }

    @Override
    View defaultFocus() {
        // 06-states A: while loading, the selected list chip; offline with nothing saved, Wi-Fi settings.
        if (state == CatalogState.Lists.LOADING) {
            View chip = Focus.byKey(chips, chipKey(list));
            if (chip != null) return chip;
        } else if (state == CatalogState.Lists.OFFLINE_EMPTY) {
            View wifi = Focus.byKey(grid, KEY_WIFI);
            if (wifi != null) return wifi;
        }
        List<View> items = Focus.all(grid);
        return items.isEmpty() ? pick : items.get(0);
    }

    /**
     * Two fixes to the prototype's moves for a page that scrolls under the nav, so focus never lands on something
     * hidden (README, Requirements: focus is always visible):
     * <ul>
     * <li>Up from anything in the page while it is scrolled stays in the page, so the chips and the header that
     * scrolled under the nav are reached (and revealed) one step at a time before the nav is; otherwise the nav is
     * nearer than the hidden items, and Up from the grid's top row or the chips skipped them. Once the page is back
     * at the top, or nothing in it is above, Up goes to the nav as usual.</li>
     * <li>From the nav, items of the page scrolled out of sight (under the nav) are not candidates; otherwise Right
     * from Search can land on a Pick for me hidden under the nav, and Up from the nav goes into the page.</li>
     * </ul>
     */
    @Override
    boolean onKey(Key key, View focused) {
        SpatialNav.Dir dir = Shell.dirOf(key);
        if (dir == null || focused == null) return false;
        // Offline with nothing saved: ← and → stay on Wi-Fi settings and Try again rather than jump up to the chips.
        View actions = StateUi.actions(focused);
        if (actions != null && (dir == SpatialNav.Dir.LEFT || dir == SpatialNav.Dir.RIGHT)) {
            if (shell.moveWithin(actions, dir)) box.playSoundEffect(sound(dir));
            return true;
        }
        // The list chips lead to Pick for me: Up from any chip, and Right from the last one (project lead,
        // 2026-10-29), so it is reached before the nav and instead of the grid.
        if (Focus.isInside(chips, focused) && (dir == SpatialNav.Dir.UP
                || (dir == SpatialNav.Dir.RIGHT && isLastChip(focused)))) {
            shell.focus(pick);
            box.playSoundEffect(sound(dir));
            return true;
        }
        // F4: Sort sits left of Filters. Left from Sort stays (nothing is beside it); Up and Down go where they go
        // from Filters.
        if (focused == sortButton) {
            if (dir == SpatialNav.Dir.LEFT) return true;
            if (dir == SpatialNav.Dir.UP || dir == SpatialNav.Dir.DOWN) {
                View next = fromFilters(dir);
                if (next != null) {
                    shell.focus(next);
                    box.playSoundEffect(sound(dir));
                }
                return true;
            }
        }
        if (Focus.isInside(box, focused)) {
            if (dir != SpatialNav.Dir.UP || box.getScrollY() <= 0) return false;
            if (!shell.moveWithin(box, dir)) return false;
        } else {
            View next = nextFromOutside(focused, dir);
            if (next == null) return true;
            shell.focus(next);
        }
        box.playSoundEffect(sound(dir));
        return true;
    }

    private boolean isLastChip(View focused) {
        List<View> all = Focus.all(chips);
        return !all.isEmpty() && all.get(all.size() - 1) == focused;
    }

    /** Where Up or Down from Filters goes (this onKey's rule for the box, then Shell's own move). */
    private View fromFilters(SpatialNav.Dir dir) {
        if (dir == SpatialNav.Dir.UP && box.getScrollY() > 0) {
            View next = shell.nextFrom(box, filtersButton, dir);
            if (next != null) return next;
        }
        return shell.nextFrom(view, filtersButton, dir);
    }

    /** Shell's move (nearest, then the entered row's memory or default) over the items not hidden by scrolling. */
    private View nextFromOutside(View from, SpatialNav.Dir dir) {
        List<View> list = new ArrayList<>();
        for (View v : Focus.candidates(view)) {
            if (v != from && (!Focus.isInside(box, v) || inSight(v))) list.add(v);
        }
        List<float[]> rects = new ArrayList<>(list.size());
        for (View v : list) rects.add(Focus.rectIn(v, view));
        int i = SpatialNav.nearest(Focus.rectIn(from, view), rects, dir);
        if (i < 0) return null;
        View best = list.get(i);
        String group = Focus.group(best);
        if (group == null || group.equals(Focus.group(from))) return best;
        View remembered = memory.get(group);
        if (remembered != null && list.contains(remembered)) return remembered;
        for (View v : list) if (group.equals(Focus.group(v)) && Focus.isDefault(v)) return v;
        return best;
    }

    /** Whether v's middle shows inside the scrolling box, not scrolled up under the nav or down past the screen. */
    private boolean inSight(View v) {
        float[] r = Focus.rectIn(v, box);
        float mid = (r[1] + r[3]) / 2;
        return mid >= 0 && mid <= box.getHeight();
    }

    private static int sound(SpatialNav.Dir dir) {
        switch (dir) {
            case UP:
                return SoundEffectConstants.NAVIGATION_UP;
            case DOWN:
                return SoundEffectConstants.NAVIGATION_DOWN;
            case LEFT:
                return SoundEffectConstants.NAVIGATION_LEFT;
            default:
                return SoundEffectConstants.NAVIGATION_RIGHT;
        }
    }

    /** Each time the page shows (a tab switch, the drawer or the title preview closing): My list may have changed. */
    @Override
    void onShow() {
        recall();
        render();
    }

    @Override
    void onLeave() {
        markLeft();
    }

    // ---- F2: what the page keeps while it is left ---------------------------------------------------------------

    /** At the page's making: what was set on it before, if it was left an hour ago or less since the TV started. */
    private void restoreSaved() {
        LibraryRecall.Saved s = RECALL.back(kind);
        if (s == null) return;
        filters.restore(s.filters);
        listKey = s.list;
        sort = s.sort;
    }

    /** The page is left (another tab, an app, standby): keep its choices, and when. */
    private void markLeft() {
        RECALL.left(kind, filters.save(), listKey, sort);
    }

    /**
     * Standby clears what every page kept, even a page left for an app before the TV went off: a receiver on the
     * application, registered once, for as long as the process runs.
     */
    private static void watchStandby(Context ctx) {
        if (watchingStandby) return;
        watchingStandby = true;
        ctx.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                RECALL.turnedOff();
            }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    /**
     * Back on the page: after more than an hour away, or the TV turned off since the page last showed, its filters,
     * list chip and sort start again (no filters, All lists, A–Z), from the top. Returns whether they did.
     */
    private boolean recall() {
        // Left: the saved choices, unless the hour or a standby since the leaving cleared them. Either way, a standby
        // since the page last showed clears them too (one it never saw while on screen, before being left).
        boolean wasLeft = RECALL.isLeft(kind);
        boolean stale = (wasLeft && RECALL.back(kind) == null) || offsSeen != RECALL.offs();
        offsSeen = RECALL.offs();
        if (!stale) return false;
        filters.clear();
        listKey = ShownList.ALL.key;
        sort = LibrarySort.AZ;
        memory.clear();
        lastFocus = null;
        lastFocusKey = null;
        box.scrollTo(0, 0);
        return true;
    }

    // ---- F4: Sort -----------------------------------------------------------------------------------------------

    /** "Sort · A–Z". */
    private String sortChipLabel() {
        return ctx.getString(R.string.library_sort_chip, ctx.getString(sortWords(sort)[1]));
    }

    /** The menu row's words and the chip's short ones. */
    private static int[] sortWords(LibrarySort s) {
        switch (s) {
            case RECENT:
                return new int[]{R.string.library_sort_recent, R.string.library_sort_recent_short};
            case NEWEST:
                return new int[]{R.string.library_sort_newest, R.string.library_sort_newest_short};
            case OLDEST:
                return new int[]{R.string.library_sort_oldest, R.string.library_sort_oldest_short};
            case RATED:
                return new int[]{R.string.library_sort_rated, R.string.library_sort_rated_short};
            default:
                return new int[]{R.string.library_sort_az, R.string.library_sort_az_short};
        }
    }

    /** OK on Sort: the orders in a dropdown under it, the one in force checked and focused. */
    private void openSort() {
        String label = ctx.getString(R.string.library_sort);
        if (shell.isOpen(label)) return;
        List<MenuPanel.Row> rows = new ArrayList<>();
        for (LibrarySort s : LibrarySort.values()) {
            rows.add(new MenuPanel.Row(ctx.getString(sortWords(s)[0]), 0, () -> {
                if (s == sort) return;
                sort = s;
                render();
            }).checked(s == sort));
        }
        shell.push(Dropdown.create(shell, label, sortButton, null, rows));
    }

    private void openFilters() {
        shell.push(new FiltersDrawer(ctx, shell, filters, label, this::render));
    }

    /** "3 imported watchlists · All 7 services": My list and the shown lists, and this TV's services. */
    private String scopeText(int lists) {
        Resources r = ctx.getResources();
        int services = data.services.subscribed().size();
        String s = services == 0 ? r.getString(R.string.library_no_services)
                : r.getQuantityString(R.plurals.library_services, services, services);
        return r.getString(R.string.library_scope, r.getQuantityString(R.plurals.library_watchlists, lists, lists), s);
    }

    /** Brings everything up to date with the household, the chosen list and the filters (library.ts, render). */
    void render() {
        List<Title> pool = LibraryLists.pool(data.catalog.all(), kind, data.household);
        // The filters judge the titles on their details: load those not in yet (the cache first, then TMDB).
        data.catalog.want(ids(pool));

        // 06-states A: loading, offline from the saved copy, or offline with nothing saved yet.
        int detailed = 0, pending = 0;
        long[] savedAt = new long[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            Title t = pool.get(i);
            if (t.detailed) {
                detailed++;
                savedAt[i] = data.catalog.savedAt(t.id);
            } else if (data.catalog.pending(t.id)) {
                pending++;
            }
        }
        boolean online = StateUi.online(ctx);
        // On a slow network the skeletons give way after a while to what has arrived; the rest fills in after.
        long nowMs = android.os.SystemClock.uptimeMillis();
        if (pending == 0) loadingSince = 0;
        else if (loadingSince == 0) loadingSince = nowMs;
        boolean waitedEnough = detailed > 0 && loadingSince != 0 && nowMs - loadingSince >= LOADING_MAX_MS;
        long wait = loadingSince + LOADING_MAX_MS - nowMs;
        // Past the deadline with nothing detailed, the catalogue's listener re-renders when the first title arrives.
        if (pending > 0 && !settled && !waitedEnough && wait > 0) {
            view.removeCallbacks(renderLater);
            view.postDelayed(renderLater, wait);
        }
        CatalogState.Lists st = CatalogState.lists(data.catalog.isRemote(), online, pool.size(), detailed, pending,
                settled || waitedEnough);
        // Try again restarts the failed loads, which fail at once offline: the page stays as it is meanwhile.
        if (retrying && st == CatalogState.Lists.LOADING && !online) st = CatalogState.Lists.OFFLINE_EMPTY;
        if (st != CatalogState.Lists.LOADING) settled = true;
        state = st;
        boolean empty = st == CatalogState.Lists.OFFLINE_EMPTY;
        boolean offline = empty || st == CatalogState.Lists.OFFLINE_SAVED;

        // A list hidden or gone since it was chosen (Choose lists, a sign-out) falls back to All lists.
        List<ShownList> choices = LibraryLists.choices(data.household);
        list = LibraryLists.choice(listKey, choices);
        listKey = list.key;
        int[] counts = new int[choices.size()];
        StringBuilder chipKey = new StringBuilder(listKey).append(empty ? ",none" : "");
        for (int i = 0; i < choices.size(); i++) {
            counts[i] = LibraryLists.under(pool, choices.get(i), data.household).size();
            chipKey.append(',').append(choices.get(i).key).append('=').append(choices.get(i).name).append(':')
                    .append(counts[i]);
        }
        if (!chipKey.toString().equals(chipsBuilt)) {
            chipsBuilt = chipKey.toString();
            buildChips(choices, empty ? null : counts);
        }
        context.setText(ctx.getString(R.string.library_context, listNames(data.household.lists())));
        scope.setText(scopeText(data.household.lists().size()));

        // Offline, the scope line becomes the amber pill: with the time of the saved copy, or just "Offline".
        scope.setVisibility(offline ? View.GONE : View.VISIBLE);
        offlinePill.setVisibility(offline ? View.VISIBLE : View.GONE);
        if (offline) {
            String saved = empty ? null : StateUi.saved(ctx, StateWords.oldest(savedAt));
            StateUi.setPill(offlinePill, saved == null ? ctx.getString(R.string.states_offline)
                    : ctx.getString(R.string.states_lists_saved, saved));
        }
        guideRight.setText(empty ? "" : ctx.getString(R.string.library_guide_right));

        int n = filters.activeCount();
        String summary = filters.summary();
        filterSummary.setText(summary.isEmpty() ? ctx.getString(R.string.library_no_filters) : summary);
        Kit.setLabel(filtersButton, n == 0 ? ctx.getString(R.string.library_filters)
                : ctx.getString(R.string.library_filters_n, n));
        Kit.setLabel(sortButton, sortChipLabel());

        List<Title> chosen = LibraryLists.under(pool, list, data.household);
        List<Title> shown = sort.sort(filters.apply(chosen), sort == LibrarySort.RECENT
                ? LibraryLists.recentRanks(pool, list, data.household) : null);
        listTitle.setText(listName(list));
        Resources r = ctx.getResources();
        boolean movies = kind == Kind.MOVIE;
        if (st == CatalogState.Lists.LOADING) {
            listStatus.setText(R.string.states_loading_details);
        } else if (empty) {
            listStatus.setText("");
        } else if (offline) {
            String count = shown.size() == chosen.size()
                    ? r.getQuantityString(movies ? R.plurals.states_movies : R.plurals.states_shows, shown.size(),
                    shown.size())
                    : r.getQuantityString(movies ? R.plurals.states_movies_of : R.plurals.states_shows_of,
                    chosen.size(), chosen.size(), shown.size());
            listStatus.setText(ctx.getString(R.string.states_prices_old, count));
        } else {
            listStatus.setText(shown.size() == chosen.size()
                    ? r.getQuantityString(movies ? R.plurals.library_movies_all : R.plurals.library_shows_all,
                    shown.size(), shown.size())
                    : r.getQuantityString(movies ? R.plurals.library_movies_filtered : R.plurals.library_shows_filtered,
                    chosen.size(), shown.size(), chosen.size()));
        }

        String gridKey;
        if (st == CatalogState.Lists.LOADING) gridKey = "loading";
        else if (empty) gridKey = "offline-empty";
        else if (chosen.isEmpty()) gridKey = "empty-list:" + list.key + ":" + list.name;
        else if (shown.isEmpty()) gridKey = "empty-filters:" + chosen.size() + ":" + summary;
        else gridKey = "titles";
        boolean titles = gridKey.equals("titles");
        if (!gridKey.equals(gridBuilt) || (titles && !same(shown, gridTitles))) {
            gridBuilt = gridKey;
            gridTitles = titles ? shown : null;
            grid.removeAllViews();
            if (st == CatalogState.Lists.LOADING) buildSkeletons();
            else if (empty) offlineEmpty();
            else if (chosen.isEmpty()) emptyList();
            else if (shown.isEmpty()) emptyFilters(chosen.size(), summary);
            else buildGrid(shown);
            rememberRebuilt();
        }
        if (shell.top() == this) shell.refocus();
    }

    private void renderIfShown() {
        if (shell.top() == this) render();
    }

    /** "list-all", "list-my", "list-tmdb-8221116", the mock "list-household": stable per list across rebuilds. */
    private static String chipKey(ShownList c) {
        return "list-" + c.key;
    }

    /** buildChips' counts are null offline with nothing saved: the chips then show their names alone. */
    private void buildChips(List<ShownList> choices, int[] counts) {
        chips.removeAllViews();
        TextView lead = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        lead.setText(R.string.library_lists_label);
        LinearLayout.LayoutParams llp = wrap();
        llp.rightMargin = Ui.px(6);
        chips.addView(lead, llp);
        for (int i = 0; i < choices.size(); i++) {
            ShownList c = choices.get(i);
            String label = counts == null ? listName(c) : ctx.getString(R.string.library_chip, listName(c), counts[i]);
            boolean chosen = c.key.equals(listKey);
            View chip = Kit.chip(ctx, label, chosen, false, GROUP_LISTS, chipKey(c), chosen, v -> choose(c));
            LinearLayout.LayoutParams lp = wrap();
            lp.leftMargin = Ui.px(12);
            chips.addView(chip, lp);
        }
        rememberRebuilt();
    }

    private void choose(ShownList c) {
        if (c.key.equals(listKey)) return;
        listKey = c.key;
        render();
    }

    private String listName(ShownList c) {
        return c.isAll() ? ctx.getString(R.string.library_all_lists) : c.name;
    }

    /** "My list, Family picks & Friends' tips", or past four "My list, Family picks & 4 more" (lists.ts, names). */
    private String listNames(List<ShownList> choices) {
        List<String> names = new ArrayList<>();
        for (ShownList c : choices) if (!c.isAll()) names.add(c.name);
        Resources r = ctx.getResources();
        int more = Math.max(1, names.size() - 2);
        return LibraryLists.names(names, r.getString(R.string.library_lists_comma),
                r.getString(R.string.library_lists_and), r.getQuantityString(R.plurals.library_lists_more, more));
    }

    /** .lib-grid: four columns of 417 px, 20 apart, rows 50 apart. */
    private void buildGrid(List<Title> shown) {
        LinearLayout row = null;
        for (int i = 0; i < shown.size(); i++) {
            Title t = shown.get(i);
            if (i % COLUMNS == 0) {
                row = Kit.unclip(new LinearLayout(ctx));
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, below(i == 0 ? 0 : GAP_Y));
            }
            View card = Kit.libraryCard(ctx, t, data, GROUP_GRID, "t-" + t.id, i == 0, v -> router.openTitle(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(CARD_W),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = i % COLUMNS == 0 ? 0 : Ui.px(GAP_X);
            row.addView(card, lp);
        }
    }

    /** 06-states A: the page's shape at once, eight skeleton cards in the grid's place; none is focusable. */
    private void buildSkeletons() {
        LinearLayout row = null;
        for (int i = 0; i < SKELETON_CARDS; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, below(i == 0 ? 0 : GAP_Y));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(CARD_W),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = i % COLUMNS == 0 ? 0 : Ui.px(GAP_X);
            row.addView(StateUi.libraryCardSkeleton(ctx), lp);
        }
    }

    /**
     * 06-states A, offline with nothing saved: the crossed-out Wi-Fi, what works without the internet, Wi-Fi
     * settings (first focus) and Try again, which says "Trying…" for a moment and then, still offline, a toast.
     */
    private void offlineEmpty() {
        View wifi = Kit.button(ctx, ctx.getString(R.string.states_wifi_settings), R.drawable.ic_ui_wifi, Kit.NORMAL,
                false, null, KEY_WIFI, true, v -> StateUi.openWifiSettings(ctx, shell::toast));
        View again = Kit.button(ctx, ctx.getString(retrying ? R.string.states_trying : R.string.states_try_again),
                R.drawable.ic_state_retry, Kit.NORMAL, false, null, KEY_TRY, false, this::tryAgain);
        grid.addView(StateUi.offlineBlock(ctx, ctx.getString(R.string.states_nothing_saved),
                ctx.getString(R.string.states_lists_note, label), wifi, again), below(0));
    }

    private void tryAgain(View button) {
        if (retrying) return;
        retrying = true;
        Kit.setLabel(button, ctx.getString(R.string.states_trying));
        data.catalog.retryFailed();
        view.postDelayed(() -> {
            retrying = false;
            Kit.setLabel(button, ctx.getString(R.string.states_try_again));
            if (shell.top() != this) return;
            render();
            if (state == CatalogState.Lists.OFFLINE_EMPTY) shell.toast(ctx.getString(R.string.states_still_offline));
        }, TRYING_MS);
    }

    /** .empty: a list with nothing on it. */
    private void emptyList() {
        LinearLayout box = emptyBox(list.isAll() ? ctx.getString(R.string.library_empty_lists)
                : ctx.getString(R.string.library_empty_list, listName(list)));
        TextView note = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        note.setText(R.string.library_empty_note);
        box.addView(note, below(14));
    }

    /** .empty: filters that hide the whole list, with Clear filters. */
    private void emptyFilters(int hidden, String summary) {
        LinearLayout box = emptyBox(ctx.getResources().getQuantityString(kind == Kind.MOVIE
                ? R.plurals.library_movies_hidden : R.plurals.library_shows_hidden, hidden, hidden));
        TextView note = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        note.setText(summary);
        box.addView(note, below(14));
        View clear = Kit.button(ctx, ctx.getString(R.string.library_clear_filters), 0, Kit.SMALL, false, null,
                KEY_CLEAR, false, v -> {
                    filters.clear();
                    render();
                });
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = Ui.px(14);
        box.addView(clear, lp);
    }

    private LinearLayout emptyBox(String title) {
        LinearLayout box = Kit.unclip(new LinearLayout(ctx));
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);
        box.setPadding(0, Ui.px(40), 0, Ui.px(40));
        TextView t = Ui.line(ctx, Ui.T_H2, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        box.addView(t, wrap());
        grid.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /**
     * After a rebuild, the focus memory of each row and grid points at the new view with the same key, so coming
     * back to the grid returns to the same title (README, Focus) rather than to its first one.
     */
    private void rememberRebuilt() {
        for (Map.Entry<String, View> e : new ArrayList<>(memory.entrySet())) {
            View old = e.getValue();
            if (old == null || Focus.isInside(view, old)) continue;
            View now = Focus.byKey(view, Focus.key(old));
            if (now != null) memory.put(e.getKey(), now);
            else memory.remove(e.getKey());
        }
    }

    private static List<String> ids(List<Title> titles) {
        List<String> out = new ArrayList<>(titles.size());
        for (Title t : titles) out.add(t.id);
        return out;
    }

    /** Whether two lists hold the very same title objects, in the same order. */
    private static boolean same(List<Title> a, List<Title> b) {
        if (b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Full width, top design px below the view before it. */
    private static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }
}
