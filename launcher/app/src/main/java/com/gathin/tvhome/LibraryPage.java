package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
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
 * screens.css; README, Movies and TV Shows (V1)). V1 is lists-only: the household's imported watchlists and the TV's
 * own My list ({@link LibraryLists}), filtered by the Filters drawer ({@link FiltersDrawer}, {@link LibraryFilters})
 * and shown in a four-column grid of library cards that scrolls under the nav.
 *
 * <p>The page is made once per tab and kept, so it keeps its list, filters, scroll and focus when you switch tabs.
 * It reads the household again each time it shows (a title added to My list in the title preview appears here), and
 * rebuilds only the parts whose content changed, so cards don't flash their placeholder when nothing moved.
 */
final class LibraryPage extends Screen {

    private static final String GROUP_LISTS = "lists";
    private static final String GROUP_GRID = "grid";
    private static final String KEY_PICK = "pick";
    private static final String KEY_FILTERS = "filters";
    private static final String KEY_CLEAR = "clear-filters";

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
    private LibraryLists.Choice list = LibraryLists.Choice.ALL;

    private final ViewGroup box;
    private final View pick;
    private final TextView scope;
    private final TextView filterSummary;
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
        filters = new LibraryFilters(kind, data.catalog.genres(kind), data.services.subscribed(), household::isSeen);

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
        TextView title = Ui.line(ctx, Ui.T_H1, Ui.TEXT_1, Ui.REGULAR);
        title.setText(label);
        heading.addView(title);
        TextView context = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        context.setText(R.string.library_context);
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
        scope.setText(scopeText());
        scopeRow.addView(scope, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        filterSummary = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        scopeRow.addView(filterSummary, wrap());
        filtersButton = Kit.button(ctx, ctx.getString(R.string.library_filters), R.drawable.ic_ui_filter, Kit.SMALL,
                false, null, KEY_FILTERS, false, v -> openFilters());
        LinearLayout.LayoutParams flp = wrap();
        flp.leftMargin = Ui.px(22);
        scopeRow.addView(filtersButton, flp);
        col.addView(scopeRow, below(18));

        // .chips: the list chips with their counts.
        chips = Kit.unclip(new LinearLayout(ctx));
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(chips, below(16));

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
        root.addView(Kit.guide(ctx, ctx.getString(data.catalog.isRemote() ? R.string.prices_library_guide
                        : R.string.library_guide_left),
                ctx.getString(R.string.library_guide_right)));

        // Titles fill in as their details arrive: what is cached shows first, the rest as it comes.
        onCatalog = () -> {
            if (shell.top() == this) render();
        };
        data.catalog.addListener(onCatalog);
        render();
    }

    @Override
    View defaultFocus() {
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
        // The list chips lead to Pick for me: Up from any chip, and Right from the last one (project lead,
        // 2026-10-29), so it is reached before the nav and instead of the grid.
        if (Focus.isInside(chips, focused) && (dir == SpatialNav.Dir.UP
                || (dir == SpatialNav.Dir.RIGHT && isLastChip(focused)))) {
            shell.focus(pick);
            box.playSoundEffect(sound(dir));
            return true;
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
        render();
    }

    private void openFilters() {
        shell.push(new FiltersDrawer(ctx, shell, filters, label, this::render));
    }

    /** "3 imported watchlists · All 7 services": the lists are the mock's three, the services this TV's. */
    private String scopeText() {
        Resources r = ctx.getResources();
        int lists = ListId.values().length;
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

        LibraryLists.Choice[] choices = LibraryLists.Choice.values();
        int[] counts = new int[choices.length];
        StringBuilder chipKey = new StringBuilder(list.name());
        for (int i = 0; i < choices.length; i++) {
            counts[i] = LibraryLists.under(pool, choices[i], data.household).size();
            chipKey.append(',').append(counts[i]);
        }
        if (!chipKey.toString().equals(chipsBuilt)) {
            chipsBuilt = chipKey.toString();
            buildChips(choices, counts);
        }

        int n = filters.activeCount();
        String summary = filters.summary();
        filterSummary.setText(summary.isEmpty() ? ctx.getString(R.string.library_no_filters) : summary);
        Kit.setLabel(filtersButton, n == 0 ? ctx.getString(R.string.library_filters)
                : ctx.getString(R.string.library_filters_n, n));

        List<Title> chosen = LibraryLists.under(pool, list, data.household);
        List<Title> shown = filters.apply(chosen);
        listTitle.setText(listName(list));
        Resources r = ctx.getResources();
        boolean movies = kind == Kind.MOVIE;
        listStatus.setText(shown.size() == chosen.size()
                ? r.getQuantityString(movies ? R.plurals.library_movies_all : R.plurals.library_shows_all,
                shown.size(), shown.size())
                : r.getQuantityString(movies ? R.plurals.library_movies_filtered : R.plurals.library_shows_filtered,
                chosen.size(), shown.size(), chosen.size()));

        String gridKey;
        if (chosen.isEmpty()) gridKey = "empty-list:" + list;
        else if (shown.isEmpty()) gridKey = "empty-filters:" + chosen.size() + ":" + summary;
        else gridKey = "titles";
        if (!gridKey.equals(gridBuilt) || (!shown.isEmpty() && !same(shown, gridTitles))) {
            gridBuilt = gridKey;
            gridTitles = shown.isEmpty() ? null : shown;
            grid.removeAllViews();
            if (chosen.isEmpty()) emptyList();
            else if (shown.isEmpty()) emptyFilters(chosen.size(), summary);
            else buildGrid(shown);
            rememberRebuilt();
        }
        if (shell.top() == this) shell.refocus();
    }

    private void buildChips(LibraryLists.Choice[] choices, int[] counts) {
        chips.removeAllViews();
        TextView lead = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        lead.setText(R.string.library_lists_label);
        LinearLayout.LayoutParams llp = wrap();
        llp.rightMargin = Ui.px(6);
        chips.addView(lead, llp);
        for (int i = 0; i < choices.length; i++) {
            LibraryLists.Choice c = choices[i];
            View chip = Kit.chip(ctx, ctx.getString(R.string.library_chip, listName(c), counts[i]), c == list, false,
                    GROUP_LISTS, "list-" + c.name().toLowerCase(java.util.Locale.ROOT), c == list, v -> choose(c));
            LinearLayout.LayoutParams lp = wrap();
            lp.leftMargin = Ui.px(12);
            chips.addView(chip, lp);
        }
        rememberRebuilt();
    }

    private void choose(LibraryLists.Choice c) {
        if (c == list) return;
        list = c;
        render();
    }

    private String listName(LibraryLists.Choice c) {
        return c.list == null ? ctx.getString(R.string.library_all_lists) : ListNames.of(c.list);
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

    /** .empty: a list with nothing on it. */
    private void emptyList() {
        LinearLayout box = emptyBox(list == LibraryLists.Choice.ALL ? ctx.getString(R.string.library_empty_lists)
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
