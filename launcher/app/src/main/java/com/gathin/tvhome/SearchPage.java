package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search (design_preview/src/screens/search.ts, the Search styles in screens.css; README, Search). One page that
 * grows: the bar under the nav, a 440 px panel with Recent searches or Suggestions over the 6×6 keyboard, and right
 * of it the empty state's rows, the results preview while typing, or No matches. → from the keyboard's right edge
 * slides the panel away and widens the same grid into the full results; OK on the bar, Back (or Backspace), or ← from
 * the first column bring the keyboard back with the query kept. With the keyboard showing, Back and Backspace go
 * Home; only the on-screen delete key deletes. Leaving for another tab forgets the search. Typing on a hardware
 * keyboard or a remote with keys types into the bar (Shell routes the characters to {@link #onText}). Matching is
 * {@link SearchIndex} over the catalogue, with TMDB's answer for the query merged in once typing pauses.
 */
final class SearchPage extends Screen {

    // Positions and sizes on the 1920 × 1080 stage, from screens.css (.search-bar, .search-panel, .search-viewport).
    private static final int BAR_TOP = 168;
    private static final int BAR_H = 84;
    private static final int PANEL_TOP = 276;
    private static final int PANEL_W = 440;
    /** The panel slides this far left as it fades out on the full results. */
    private static final int PANEL_SHIFT = 560;
    /** The results viewport's left edge beside the panel. */
    private static final int VIEW_LEFT = 584;
    private static final int VIEW_PAD = 8;
    /**
     * On the full results the viewport's left edge sits a padding short of the safe margin, so the six-column grid
     * starts at 96 px, in line with the bar, and ends 98 px short of the right edge: title-safe on both sides.
     */
    private static final int VIEW_LEFT_EXPANDED = Ui.SAFE_X - VIEW_PAD;
    private static final int KEY = 56;
    private static final int KEY_GAP = 8;
    private static final int CARD_W = 271;
    private static final int COLUMNS = 6;
    private static final int COLUMN_GAP = 20;
    private static final int ROW_GAP = 30;
    /**
     * While typing, the preview shows a little over two rows of the grid and can't be focused, so it builds only
     * the first rows; the full results build every card. Keeps each key press light on a 1 GB TV.
     */
    private static final int PREVIEW_ROWS = 3;
    /** A row that runs off the screen's edge stops scrolling this far short of it (search.ts titleRow). */
    private static final int ROW_REVEAL = 96;
    /** How long typing must pause before TMDB is asked, and how many letters it takes. */
    private static final int DEBOUNCE_MS = 300;
    private static final int REMOTE_MIN = 2;
    private static final int GENRE_MIN = 3;

    private static final String[] KEY_ROWS = {"abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890"};
    private static final String[] BROWSE_GENRES = {"Sci-fi", "Drama", "Comedy", "Documentary", "Animation", "Thriller"};
    private static final SearchIndex.Filter[] FILTERS = SearchIndex.Filter.values();

    static final String GROUP_KEYBOARD = "kb";
    private static final String GROUP_LIST = "list";
    private static final String GROUP_GRID = "grid";
    private static final String GROUP_FILTERS = "filters";
    private static final String GROUP_GENRES = "genres";

    private final Context ctx;
    private final Router router;
    private final Shell shell;
    private final Data data;
    private final SearchIndex index;

    private final FrameLayout content;
    private final RingFrame bar;
    private final TextView typed, ghost;
    private final View hint;
    private final View offlinePill;
    private final LinearLayout panel;
    private final TextView listHead;
    private final LinearLayout list;
    private final View keyA;
    private final FrameLayout viewport;
    private final ViewGroup scroll;
    private final LinearLayout body;

    private String query = "";
    private boolean expanded;
    /** Whether the panel and viewport are drawn expanded; render() animates when it differs from expanded. */
    private boolean shownExpanded;
    /** Set when Search was left for another tab: the next render puts the panel back without the slide. */
    private boolean snap;
    private SearchIndex.Filter filter = SearchIndex.Filter.ALL;
    private SearchIndex.Results results;
    private boolean hasMatches;
    private int viewLeft = VIEW_LEFT;

    /** What TMDB found for {@link #foundFor}, merged into the results while the query is still that. */
    private Catalog.Found found;
    private String foundFor;
    /** The popular titles of a genre typed in full, and the genre asked for last. */
    private List<Title> genreFound;
    private String genreFoundName, genreAsked;
    /** Asks TMDB once typing pauses (DEBOUNCE_MS), from two letters on. */
    private final Runnable askRemote = this::askRemote;
    /** Kept in a field: the catalogue holds its listeners weakly. */
    private final Runnable onCatalog;
    /**
     * Offline, the ids of the titles saved on the TV, worked out once rather than on every key press; dropped when
     * the catalogue, the household's lists or the network change.
     */
    private java.util.Set<String> savedIds;
    private final Household.Listener onHousehold = () -> savedIds = null;

    static SearchPage create(MainActivity activity, NavBar.Host host) {
        return new SearchPage(activity, activity, Pages.page(activity, host, Tab.SEARCH));
    }

    private SearchPage(Context ctx, Router router, FrameLayout page) {
        super(ctx.getString(R.string.nav_search), page, Layer.PAGE);
        this.ctx = ctx;
        this.router = router;
        this.shell = router.shell();
        this.data = router.data();
        this.index = new SearchIndex(data.catalog);
        Kit.unclip(page);

        // Everything but the shade and the nav, in one view, so a tab switch slides it as a whole and leaves the
        // panel's and the results' own positions alone.
        content = Kit.unclip(new FrameLayout(ctx));
        page.addView(content, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- The search bar: focusable only on the full results, where OK on it brings the keyboard back.
        bar = new RingFrame(ctx, BAR_H / 2f, false, Ui.SURFACE);
        bar.content().setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL),
                null, Ui.round(Ui.SURFACE_2, Ui.RADIUS_PILL, Ui.ACCENT, 1)));
        bar.content().setDuplicateParentStateEnabled(true);
        LinearLayout barRow = new LinearLayout(ctx);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        barRow.setPadding(Ui.px(30), 0, Ui.px(16), 0);
        barRow.addView(Ui.icon(ctx, R.drawable.ic_ui_search, 30, Ui.TEXT_2), new LinearLayout.LayoutParams(Ui.px(30), Ui.px(30)));
        typed = Ui.line(ctx, Ui.T_H2, Ui.TEXT_1, Ui.MEDIUM);
        barRow.addView(typed, gapBefore(16));
        ghost = Ui.line(ctx, Ui.T_H2, Ui.GHOST, Ui.REGULAR);
        barRow.addView(ghost, gapBefore(16));
        barRow.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
        hint = resultsHint(ctx);
        barRow.addView(hint, gapBefore(16));
        // 06-states A: offline, the amber pill takes the hint's place at the bar's end.
        offlinePill = StateUi.pill(ctx, "");
        offlinePill.setVisibility(View.GONE);
        barRow.addView(offlinePill, gapBefore(16));
        bar.content().addView(barRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        bar.setOnFocusChangeListener((v, focused) -> bar.setRing(focused));
        Focus.make(bar, null, "bar", false, v -> collapse());
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(BAR_H),
                Gravity.TOP);
        blp.topMargin = Ui.px(BAR_TOP);
        blp.leftMargin = Ui.px(Ui.SAFE_X);
        blp.rightMargin = Ui.px(Ui.SAFE_X);
        content.addView(bar, blp);

        // ---- The results viewport: runs off the right edge, so the next cards are cut by the TV's edge.
        viewport = new FrameLayout(ctx);
        scroll = ScrollBox.vertical(ctx, 40);
        scroll.setPadding(Ui.px(VIEW_PAD), Ui.px(VIEW_PAD), Ui.px(VIEW_PAD), Ui.px(VIEW_PAD));
        body = Kit.unclip(new LinearLayout(ctx));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        viewport.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        View fade = new View(ctx);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00090E15, 0xF7090E15}));
        viewport.addView(fade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(110),
                Gravity.BOTTOM));
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.px(Ui.STAGE_H - PANEL_TOP - Ui.SAFE_Y), Gravity.TOP);
        vlp.topMargin = Ui.px(PANEL_TOP);
        vlp.leftMargin = Ui.px(VIEW_LEFT);
        content.addView(viewport, vlp);

        // ---- The left panel: the list, then the keyboard.
        panel = Kit.unclip(new LinearLayout(ctx));
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.px(32), Ui.px(24), Ui.px(32), Ui.px(30));
        panel.setBackground(Ui.round(0x08FFFFFF, Ui.RADIUS_XL, Ui.LINE, 1));
        listHead = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        listHead.setPadding(Ui.px(16), 0, 0, 0);
        panel.addView(listHead, below(0));
        list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setMinimumHeight(Ui.px(196));
        panel.addView(list, below(6));
        LinearLayout keyboard = Kit.unclip(new LinearLayout(ctx));
        keyboard.setOrientation(LinearLayout.VERTICAL);
        View a = null;
        for (String row : KEY_ROWS) {
            LinearLayout r = keyRow(keyboard);
            for (char c : row.toCharArray()) {
                View k = key(r, String.valueOf(c), "k-" + c, KEY, c == 'a', v -> setQuery(query + c));
                if (c == 'a') a = k;
            }
        }
        keyA = a;
        LinearLayout last = keyRow(keyboard);
        key(last, ctx.getString(R.string.search_key_space), "k-space", 184, false, v -> setQuery(query + " "));
        FrameLayout del = new FrameLayout(ctx);
        ImageView delIcon = new ImageView(ctx);
        delIcon.setImageResource(R.drawable.ic_ui_backspace);
        delIcon.setImageTintList(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        delIcon.setDuplicateParentStateEnabled(true);
        del.addView(delIcon, new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        del.setContentDescription(ctx.getString(R.string.search_key_delete));
        keyLook(del);
        Focus.make(del, GROUP_KEYBOARD, "k-del", false, v -> setQuery(SearchQuery.delete(query)));
        last.addView(del, keyParams(last, KEY));
        key(last, ctx.getString(R.string.search_key_clear), "k-clear", 120, false, v -> setQuery(""));
        panel.addView(keyboard, below(18));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.px(PANEL_W), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        plp.topMargin = Ui.px(PANEL_TOP);
        plp.leftMargin = Ui.px(Ui.SAFE_X);
        content.addView(panel, plp);

        // Recently viewed and From My list fill in as their titles' details arrive.
        onCatalog = () -> {
            savedIds = null;
            if (shell.top() == this) render();
        };
        data.catalog.addListener(onCatalog);
        // The household holds its listeners strongly: listen only while the page is on screen.
        page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                savedIds = null;
                data.household.addListener(onHousehold);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                data.household.removeListener(onHousehold);
            }
        });
        if (page.isAttachedToWindow()) data.household.addListener(onHousehold);
        // Offline and back online change the page in place (06-states A); back online, TMDB is asked at once.
        StateUi.watchInternet(page, StateUi.navModel(ctx), online -> {
            savedIds = null;
            if (online && !query.isEmpty()) {
                view.removeCallbacks(askRemote);
                view.post(askRemote);
            }
            if (shell.top() == this) render();
        });
        render();
    }

    // ---- Screen ---------------------------------------------------------------------------------------------------

    @Override
    View defaultFocus() {
        return expanded ? firstResult() : keyA;
    }

    /** Coming back from a title or another tab: Recent searches, My list and the offers may have changed. */
    @Override
    void onShow() {
        if (snap) scroll.scrollTo(0, 0);
        render();
    }

    /**
     * Leaving for another tab, or Home, forgets the search: coming back shows the empty page with focus as on the
     * first visit. Opening a title or a person pushes over Search and keeps it. Drawn on the next onShow, so the
     * page sliding out keeps its look.
     */
    @Override
    void onLeave() {
        query = "";
        filter = SearchIndex.Filter.ALL;
        expanded = false;
        snap = true;
        view.removeCallbacks(askRemote);
        found = null;
        foundFor = null;
        genreAsked = null;
        memory.clear();
        lastFocus = null;
        lastFocusKey = null;
    }

    @Override
    boolean onText(char c) {
        if (!SearchQuery.isTypeable(c)) return false;
        boolean wasExpanded = expanded;
        expanded = false;
        setQuery(SearchQuery.type(query, c));
        if (wasExpanded) shell.focus(keyA);
        return true;
    }

    @Override
    boolean onKey(Key key, View focused) {
        switch (key) {
            case BACK:
                if (!expanded) return false;
                collapse();
                return true;
            case RIGHT:
                if (expanded || focused == null || !Focus.isInside(panel, focused)) return false;
                if (shell.moveWithin(panel, SpatialNav.Dir.RIGHT)) return true;
                if (!query.isEmpty() && hasMatches) expand();
                // Searched from the whole content, not the viewport: moveWithin measures from inside its root.
                else shell.moveWithin(content, SpatialNav.Dir.RIGHT);
                return true;
            case LEFT:
                if (!expanded || focused == null || !Focus.isInside(viewport, focused)) return false;
                if (!shell.moveWithin(viewport, SpatialNav.Dir.LEFT)) collapse();
                return true;
            default:
                return false;
        }
    }

    // ---- State ----------------------------------------------------------------------------------------------------

    private void setQuery(String q) {
        query = SearchQuery.clean(q);
        if (query.isEmpty()) expanded = false;
        view.removeCallbacks(askRemote);
        // Offline, TMDB isn't asked: the results come from the saved titles (06-states A).
        if (data.catalog.isRemote() && StateUi.online(ctx) && query.trim().length() >= REMOTE_MIN) {
            view.postDelayed(askRemote, DEBOUNCE_MS);
        }
        render();
    }

    /**
     * Typing paused: asks TMDB for the query (cached for an hour), and for a genre typed well enough the genre's
     * popular titles. The answers redraw the page only while the query is still the one asked.
     */
    private void askRemote() {
        String q = query;
        if (q.trim().length() < REMOTE_MIN || !StateUi.online(ctx)) return;
        data.catalog.search(q, f -> {
            if (!q.equals(query)) return;
            found = f;
            foundFor = q;
            if (shell.top() == this) render();
        });
        String genre = results == null ? null : SearchIndex.strongGenre(results);
        if (genre == null || q.trim().length() < GENRE_MIN || genre.equals(genreAsked)) return;
        genreAsked = genre;
        data.catalog.genre(genre, list -> {
            genreFound = list;
            genreFoundName = genre;
            if (shell.top() == this && query.equals(q)) render();
        });
    }

    /** What the network found that still belongs to the query; null when nothing does. */
    private SearchIndex.Remote remote() {
        Catalog.Found f = query.equals(foundFor) ? found : null;
        if (f == null && genreFound == null) return null;
        return new SearchIndex.Remote(f, genreFoundName, genreFound);
    }

    private void expand() {
        expanded = true;
        render(false);
        scroll.scrollTo(0, 0);
        shell.focus(firstResult());
    }

    /** The first card of the full results, else the first thing in them (a filter chip), else the bar. */
    private View firstResult() {
        View first = null;
        for (View v : Focus.all(body)) {
            if (GROUP_GRID.equals(Focus.group(v))) {
                first = v;
                break;
            }
        }
        if (first == null) {
            List<View> all = Focus.all(body);
            first = all.isEmpty() ? null : all.get(0);
        }
        return first != null ? first : bar;
    }

    private void collapse() {
        View remembered = memory.get(GROUP_KEYBOARD);
        expanded = false;
        render(false);
        shell.focus(remembered != null && remembered.isAttachedToWindow() ? remembered : keyA);
    }

    private void openTitle(Title t) {
        if (!query.isEmpty()) data.household.pushRecent(query);
        router.openTitle(t);
    }

    private void openPerson(Person p) {
        if (!query.isEmpty()) data.household.pushRecent(query);
        router.openPerson(p);
    }

    // ---- Rendering ------------------------------------------------------------------------------------------------

    private void render() {
        render(true);
    }

    /**
     * Builds the page for the current state. With refocus, focus moves off whatever the rebuild removed or made
     * inert; expand and collapse pass false and place focus themselves, so the keyboard's memory survives.
     */
    private void render(boolean refocus) {
        // 06-states A, offline: the results come from the titles saved on the TV, without people; with nothing saved,
        // the results area says so beside the keyboard.
        boolean offline = data.catalog.isRemote() && !StateUi.online(ctx);
        if (!offline) {
            savedIds = null;
        } else if (savedIds == null) {
            savedIds = OfflineSearch.savedIds(data.household.titleIds(), data.catalog.all());
        }
        java.util.Set<String> saved = savedIds;
        boolean nothingSaved = offline && nothingSaved(saved);
        results = query.isEmpty() ? null : index.search(query, offline ? null : remote());
        if (offline && results != null) {
            results = OfflineSearch.filter(results, nothingSaved ? java.util.Collections.emptySet() : saved);
        }
        hasMatches = results != null && results.hasMatches();

        // The bar.
        if (query.isEmpty()) {
            typed.setText(R.string.search_placeholder);
            typed.setTextColor(Ui.TEXT_3);
            typed.setTypeface(Ui.REGULAR);
        } else {
            typed.setText(query);
            typed.setTextColor(Ui.TEXT_1);
            typed.setTypeface(Ui.MEDIUM);
        }
        String g = SearchQuery.ghost(query, results, expanded);
        ghost.setText(g == null ? "" : ctx.getString(R.string.search_ghost, g));
        ghost.setVisibility(g == null ? View.GONE : View.VISIBLE);
        hint.setVisibility(!offline && !expanded && hasMatches ? View.VISIBLE : View.GONE);
        offlinePill.setVisibility(offline ? View.VISIBLE : View.GONE);
        if (offline) {
            StateUi.setPill(offlinePill, ctx.getString(nothingSaved ? R.string.states_offline
                    : R.string.states_search_saved_only));
        }

        // The panel's list.
        list.removeAllViews();
        if (query.isEmpty()) {
            listHead.setText(R.string.search_recent);
            for (String r : data.household.recent()) {
                addListItem(r, null, R.drawable.ic_search_clock, "li-" + r, v -> setQuery(r));
            }
        } else {
            listHead.setText(R.string.search_suggestions);
            if (results.suggestions.isEmpty()) {
                TextView none = Ui.line(ctx, 22, Ui.TEXT_3, Ui.REGULAR);
                none.setText(R.string.search_no_suggestions);
                none.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(12));
                list.addView(none);
            }
            for (SearchIndex.Hit s : results.suggestions) {
                CharSequence label = listHighlight(s.label.toLowerCase(Locale.ROOT), s.match.indices);
                addListItem(label, tag(s), R.drawable.ic_ui_search,
                        "li-" + s.kind.name().toLowerCase(Locale.ROOT) + "-" + s.label, v -> {
                            filter = s.kind == SearchIndex.HitKind.PERSON ? SearchIndex.Filter.PEOPLE : SearchIndex.Filter.ALL;
                            setQuery(s.label.toLowerCase(Locale.ROOT));
                        });
            }
        }

        // The results area.
        body.removeAllViews();
        if (nothingSaved && (query.isEmpty() || !hasMatches)) {
            View wifi = Kit.button(ctx, ctx.getString(R.string.states_wifi_settings), R.drawable.ic_ui_wifi,
                    Kit.NORMAL, false, null, "search-wifi", false, v -> StateUi.openWifiSettings(ctx, shell::toast));
            body.addView(StateUi.offlineBlock(ctx, query.isEmpty() ? ctx.getString(R.string.states_nothing_saved)
                            : ctx.getString(R.string.states_search_no_match, query),
                    ctx.getString(R.string.states_search_nothing_note), wifi), below(0));
            body.setPadding(0, 0, 0, Ui.px(120));
        } else if (query.isEmpty()) {
            // A fresh install starts with every list empty (V2 Phase 3): no heading over an empty row.
            addViewed();
            List<Title> mine = ids(data.household.myListIds());
            if (!mine.isEmpty()) body.addView(titleRow(R.string.search_from_my_list, mine, "mine"));
            body.addView(genreRow());
            body.setPadding(0, 0, 0, Ui.px(120));
        } else if (!hasMatches) {
            TextView title = Ui.line(ctx, 34, Ui.TEXT_1, Ui.REGULAR);
            title.setText(ctx.getString(offline ? R.string.states_search_no_match : R.string.search_no_matches, query));
            title.setPadding(0, Ui.px(16), 0, 0);
            body.addView(title, below(0));
            TextView note = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
            note.setText(offline ? R.string.states_search_no_match_note : R.string.search_no_matches_tip);
            LinearLayout.LayoutParams nlp = below(12);
            nlp.bottomMargin = Ui.px(40);
            body.addView(note, nlp);
            addViewed();
            body.setPadding(0, 0, 0, Ui.px(120));
        } else {
            List<SearchIndex.Hit> items = index.items(results, filter, data.services.subscribed());
            body.addView(resultsHead(items.size(), offline), below(0));
            if (items.isEmpty()) {
                TextView note = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
                note.setText(ctx.getString(R.string.search_filter_empty, ctx.getString(filterLabel(filter))));
                note.setPadding(0, Ui.px(20), 0, Ui.px(20));
                LinearLayout.LayoutParams nlp = below(12);
                nlp.bottomMargin = Ui.px(40);
                body.addView(note, nlp);
            } else {
                body.addView(grid(items), new LinearLayout.LayoutParams(
                        Ui.px(COLUMNS * CARD_W + (COLUMNS - 1) * COLUMN_GAP), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            body.setPadding(0, 0, 0, Ui.px(140));
        }

        // Where the D-pad may go: the bar only on the full results, the panel only beside them, and the preview
        // only once it is widened (→); with no matches its Recently viewed row can be reached directly.
        setInert(bar, !expanded);
        setInert(panel, expanded);
        setInert(viewport, !query.isEmpty() && hasMatches && !expanded);
        slide();

        if (!refocus || shell.top() != this) return;
        View f = shell.focused();
        // A rebuilt list or grid takes its focused item with it, and an item that just went inert keeps its view;
        // either way focus moves on, by key or to defaultFocus(), which on the full results is a result or the bar
        // and never a key of the hidden keyboard.
        if (f != null && Focus.isInside(view, f) && !Focus.isFocusable(f)) {
            shell.focus(defaultFocus());
        } else {
            shell.refocus();
        }
    }

    /**
     * The full results: the panel slides 560 px left as it fades, and the viewport's left edge moves out to the safe
     * margin (screens.css .search.is-expanded). The viewport is moved once and its content slid with a translation,
     * so the grid is not laid out again on every frame.
     */
    private void slide() {
        if (shownExpanded == expanded) {
            snap = false;
            return;
        }
        shownExpanded = expanded;
        long ms = snap ? 0 : Ui.ms(Ui.SLOW_MS);
        snap = false;
        panel.animate().cancel();
        scroll.animate().cancel();
        float contentLeft = Ui.px(viewLeft) + scroll.getTranslationX();
        if (expanded) {
            setViewLeft(VIEW_LEFT_EXPANDED);
            if (ms == 0) {
                panel.setTranslationX(-Ui.px(PANEL_SHIFT));
                panel.setAlpha(0f);
                scroll.setTranslationX(0f);
                return;
            }
            panel.animate().translationX(-Ui.px(PANEL_SHIFT)).alpha(0f).setDuration(ms).setInterpolator(Ui.EASE).start();
            scroll.setTranslationX(contentLeft - Ui.px(VIEW_LEFT_EXPANDED));
            scroll.animate().translationX(0f).setDuration(ms).setInterpolator(Ui.EASE).start();
        } else {
            if (ms == 0) {
                panel.setTranslationX(0f);
                panel.setAlpha(1f);
                scroll.setTranslationX(0f);
                setViewLeft(VIEW_LEFT);
                return;
            }
            panel.animate().translationX(0f).alpha(1f).setDuration(ms).setInterpolator(Ui.EASE).start();
            // The viewport keeps its wide left edge while its content slides right, then takes the narrow one.
            scroll.animate().translationX(Ui.px(VIEW_LEFT) - Ui.px(viewLeft)).setDuration(ms).setInterpolator(Ui.EASE)
                    .withEndAction(() -> {
                        if (expanded) return;
                        scroll.setTranslationX(0f);
                        setViewLeft(VIEW_LEFT);
                    }).start();
        }
    }

    private void setViewLeft(int left) {
        viewLeft = left;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) viewport.getLayoutParams();
        lp.leftMargin = Ui.px(left);
        viewport.setLayoutParams(lp);
    }

    /** The prototype's data-inert: the D-pad skips everything under root while it is set. */
    private static void setInert(View root, boolean inert) {
        if (root.getTag(R.id.ui_focusable) != null) root.setTag(R.id.ui_focusable, !inert);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) root;
        for (int i = 0; i < g.getChildCount(); i++) setInert(g.getChildAt(i), inert);
    }

    // ---- The bar and the panel ------------------------------------------------------------------------------------

    /** The "› Results" pill at the bar's right end (.pill). */
    private static View resultsHint(Context ctx) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.px(18), Ui.px(10), Ui.px(18), Ui.px(10));
        pill.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL));
        pill.addView(Ui.icon(ctx, R.drawable.ic_ui_chev_right, 20, Ui.TEXT_3), new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20)));
        TextView t = Ui.line(ctx, 19, Ui.TEXT_3, Ui.REGULAR);
        t.setText(R.string.search_results_hint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(8);
        pill.addView(t, lp);
        return pill;
    }

    /** {@code .sp-item}: 48 px, an icon, the label and a tag, in the tertiary text; focused, the accent fill. */
    private void addListItem(CharSequence label, CharSequence tag, int icon, String key, View.OnClickListener onOk) {
        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(Ui.px(16), 0, Ui.px(16), 0);
        item.setBackground(Ui.states(Ui.round(0x00000000, Ui.RADIUS_M), null, Ui.round(Ui.ACCENT, Ui.RADIUS_M)));
        item.setContentDescription(label.toString());
        ColorStateList color = Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT);
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(icon);
        ic.setImageTintList(color);
        ic.setDuplicateParentStateEnabled(true);
        item.addView(ic, new LinearLayout.LayoutParams(Ui.px(22), Ui.px(22)));
        TextView text = Ui.line(ctx, 22, Ui.TEXT_3, Ui.REGULAR);
        text.setText(label);
        text.setTextColor(color);
        text.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.px(14);
        item.addView(text, tlp);
        if (tag != null) {
            TextView t = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
            t.setText(tag);
            t.setTextColor(color);
            t.setDuplicateParentStateEnabled(true);
            item.addView(t, gapBefore(14));
        }
        Focus.make(item, GROUP_LIST, key, false, onOk);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(48));
        lp.topMargin = list.getChildCount() == 0 ? 0 : Ui.px(2);
        list.addView(item, lp);
    }

    /**
     * Suggestions show the matched letters in the primary text, semibold, over the tertiary rest (screens.css
     * .sp-text .hit); on the focused item everything turns the dark on-accent colour. The span follows the item's
     * focus through the text's drawable state.
     */
    private static CharSequence listHighlight(String text, int[] indices) {
        SpannableString s = new SpannableString(text);
        ColorStateList hit = Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT);
        for (int i : indices) {
            if (i < 0 || i >= text.length()) continue;
            s.setSpan(new TextAppearanceSpan(null, Typeface.BOLD, -1, hit, null), i, i + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    private String tag(SearchIndex.Hit h) {
        switch (h.kind) {
            case PERSON:
                return ctx.getString(R.string.search_tag_person);
            case GENRE:
                return ctx.getString(R.string.search_tag_genre);
            default:
                return ctx.getString(h.title.isMovie() ? R.string.search_tag_movie : R.string.search_tag_show);
        }
    }

    private LinearLayout keyRow(LinearLayout keyboard) {
        LinearLayout r = Kit.unclip(new LinearLayout(ctx));
        r.setOrientation(LinearLayout.HORIZONTAL);
        keyboard.addView(r, below(keyboard.getChildCount() == 0 ? 0 : KEY_GAP));
        return r;
    }

    /** {@code .key}: 56 px square on the surface; focused, the accent fill, semibold, scaled 1.08. */
    private View key(LinearLayout row, String label, String key, int width, boolean isDefault, View.OnClickListener onOk) {
        TextView k = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        k.setOnFocusChangeListener((v, focused) -> k.setTypeface(focused ? Ui.MEDIUM : Ui.REGULAR));
        keyLook(k);
        Focus.make(k, GROUP_KEYBOARD, key, isDefault, onOk);
        row.addView(k, keyParams(row, width));
        return k;
    }

    private static void keyLook(View k) {
        k.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_M, Ui.LINE, 1), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_M, Ui.ACCENT, 1)));
        Ui.scaleOnFocus(k, 1.08f);
    }

    private static LinearLayout.LayoutParams keyParams(LinearLayout row, int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(width), Ui.px(KEY));
        lp.leftMargin = row.getChildCount() == 0 ? 0 : Ui.px(KEY_GAP);
        return lp;
    }

    // ---- The results area -----------------------------------------------------------------------------------------

    /** Recently viewed, unless nothing was viewed yet (a fresh install). */
    private void addViewed() {
        List<Title> viewed = viewed();
        if (!viewed.isEmpty()) body.addView(titleRow(R.string.search_recently_viewed, viewed, "viewed"));
    }

    private List<Title> viewed() {
        return ids(data.household.viewed());
    }

    private List<Title> ids(List<String> ids) {
        List<Title> out = new ArrayList<>();
        for (String id : ids) {
            Title t = data.catalog.get(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** Offline, whether no saved title's details are on the TV (CatalogState, as Movies and TV Shows judge it). */
    private boolean nothingSaved(java.util.Set<String> saved) {
        int known = 0, detailed = 0, pending = 0;
        for (String id : saved) {
            Title t = data.catalog.get(id);
            if (t == null) continue;
            known++;
            if (t.detailed) detailed++;
            else if (data.catalog.pending(id)) pending++;
        }
        return known == 0 || CatalogState.lists(true, false, known, detailed, pending, true)
                == CatalogState.Lists.OFFLINE_EMPTY;
    }

    /** A row of small cards running off the right edge (search.ts titleRow). */
    private View titleRow(int label, List<Title> titles, String group) {
        List<View> cards = new ArrayList<>();
        for (Title t : titles) {
            cards.add(Kit.smallCard(ctx, t, null, data, group, group + "-" + t.id, false, v -> openTitle(t)));
        }
        return Kit.row(ctx, ctx.getString(label), cards, ROW_REVEAL);
    }

    /** Browse by genre: chips that search for the genre. */
    private View genreRow() {
        LinearLayout row = Kit.unclip(new LinearLayout(ctx));
        row.setOrientation(LinearLayout.VERTICAL);
        TextView label = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        label.setText(R.string.search_browse_genre);
        label.setGravity(Gravity.START);
        LinearLayout.LayoutParams llp = below(0);
        llp.bottomMargin = Ui.px(16);
        row.addView(label, llp);
        LinearLayout chips = Kit.unclip(new LinearLayout(ctx));
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String g : BROWSE_GENRES) {
            View c = Kit.chip(ctx, g, false, false, GROUP_GENRES, "g-" + g,
                    v -> setQuery(g.toLowerCase(Locale.ROOT)));
            chips.addView(c, gapBefore(chips.getChildCount() == 0 ? 0 : 12));
        }
        row.addView(chips);
        return row;
    }

    /** "12 results" (offline, "12 results from saved titles") and the filter chips (.results-head). */
    private View resultsHead(int count, boolean offline) {
        LinearLayout head = Kit.unclip(new LinearLayout(ctx));
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, Ui.px(24));
        TextView n = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        n.setText(ctx.getResources().getQuantityString(offline ? R.plurals.states_search_saved_count
                : R.plurals.search_results_count, count, count));
        head.addView(n);
        for (SearchIndex.Filter f : FILTERS) {
            View c = Kit.chip(ctx, ctx.getString(filterLabel(f)), f == filter, false, GROUP_FILTERS,
                    "rf-" + f.name().toLowerCase(Locale.ROOT), f == filter, v -> {
                        filter = f;
                        render();
                    });
            head.addView(c, gapBefore(f == FILTERS[0] ? 28 : 12));
        }
        return head;
    }

    private static int filterLabel(SearchIndex.Filter f) {
        switch (f) {
            case MOVIES:
                return R.string.search_filter_movies;
            case SHOWS:
                return R.string.search_filter_shows;
            case PEOPLE:
                return R.string.search_filter_people;
            case INCLUDED:
                return R.string.search_filter_included;
            default:
                return R.string.search_filter_all;
        }
    }

    /** {@code .results-grid}: six 271 px columns, 20 px apart, rows 30 px apart. */
    private View grid(List<SearchIndex.Hit> items) {
        LinearLayout grid = Kit.unclip(new LinearLayout(ctx));
        grid.setOrientation(LinearLayout.VERTICAL);
        int shown = expanded ? items.size() : Math.min(items.size(), PREVIEW_ROWS * COLUMNS);
        LinearLayout row = null;
        for (int i = 0; i < shown; i++) {
            if (i % COLUMNS == 0) {
                row = Kit.unclip(new LinearLayout(ctx));
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, below(i == 0 ? 0 : ROW_GAP));
            }
            View card = resultCard(items.get(i), i == 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(CARD_W), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = i % COLUMNS == 0 ? 0 : Ui.px(COLUMN_GAP);
            row.addView(card, lp);
        }
        return grid;
    }

    private View resultCard(SearchIndex.Hit hit, boolean isDefault) {
        // Titles by id: two titles may share a name (Dune, 1984 and 2021).
        String key = hit.kind == SearchIndex.HitKind.TITLE ? "r-title-" + hit.title.id
                : "r-" + hit.kind.name().toLowerCase(Locale.ROOT) + "-" + hit.label;
        CharSequence name = Kit.highlight(hit.label, hit.match.indices);
        if (hit.kind == SearchIndex.HitKind.PERSON) return personCard(hit, name, key, isDefault);
        return Kit.smallCard(ctx, hit.title, name, data, GROUP_GRID, key, isDefault, v -> openTitle(hit.title));
    }

    /**
     * A person among the results ({@code .card--small.card--person}): the small card's 271 × 152 art box with the
     * round portrait in its middle (initials, and their photo over them when TMDB has one), the highlighted name,
     * "Person" and how many titles they are in.
     */
    private View personCard(SearchIndex.Hit hit, CharSequence name, String key, boolean isDefault) {
        LinearLayout card = Kit.unclip(new LinearLayout(ctx));
        card.setOrientation(LinearLayout.VERTICAL);
        RingFrame art = new RingFrame(ctx, 14, false, Ui.BG_RAISED);
        RingFrame portrait = Kit.portrait(ctx, hit.label, hit.person.photo, 104, 32);
        art.content().addView(portrait, new FrameLayout.LayoutParams(Ui.px(104), Ui.px(104), Gravity.CENTER));
        card.addView(art, new LinearLayout.LayoutParams(Ui.px(CARD_W), Ui.px(152)));
        TextView n = Ui.line(ctx, 22, Ui.TEXT_1, Ui.MEDIUM);
        n.setText(name);
        n.setGravity(Gravity.START);
        card.addView(n, below(12));
        TextView meta = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_2, Ui.REGULAR);
        meta.setText(R.string.search_person);
        meta.setGravity(Gravity.START);
        card.addView(meta, below(4));
        int count = hit.titles == null ? 0 : hit.titles.size();
        TextView sub = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        sub.setText(ctx.getResources().getQuantityString(R.plurals.search_person_titles, count, count));
        sub.setGravity(Gravity.START);
        card.addView(sub, below(2));
        card.setContentDescription(hit.label);
        card.setOnFocusChangeListener((v, focused) -> {
            art.setRing(focused);
            float s = focused ? 1.03f : 1f;
            art.animate().scaleX(s).scaleY(s).setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
        });
        return Focus.make(card, GROUP_GRID, key, isDefault, v -> openPerson(hit.person));
    }

    // ---- Layout helpers -------------------------------------------------------------------------------------------

    private static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }

    private static LinearLayout.LayoutParams gapBefore(float gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(gap);
        return lp;
    }
}
