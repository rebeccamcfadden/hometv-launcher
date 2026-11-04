package com.hometv.launcher;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The person page, Rebecca's pick B (2026-11-04 18:55; Figma "Person page" 266:704; design_preview/src/screens/person.ts),
 * pushed from Cast &amp; crew or Search. On the left, fixed: Back, the round portrait (TMDB's photo over the
 * initials), what they do over their name, born when and where, the biography's first paragraph (six lines at most) and
 * Read more when there's more. On the right: chips (All, On your services, Rent or buy, one per job when they have
 * more than one), Sort, and every title as C cards, five across (Rebecca, 19:11: every title, no cap).
 * <p>
 * Light on a 1 GB TV: the grid is a column of empty rows of the cards' height; a row gets its cards only when it
 * comes near the screen, and its posters bind and let go with {@link ArtWindow}, as the library grid's do. The
 * titles the catalogue already knows show at once; TMDB's answer (facts, every title, roles) replaces them.
 */
final class PersonPage extends Screen {
    private static final String GROUP_CHIPS = "person-chips";
    private static final String GROUP_GRID = "person-grid";
    private static final String KEY_BIO = "person-bio";
    private static final String KEY_SORT = "person-sort";
    /** Figma 266:704: the side column and the grid's top, in design px. */
    private static final int TOP = 132;
    private static final int SIDE_W = 480;
    private static final int GRID_W = PersonRules.COLUMNS * Kit.POSTER_W + (PersonRules.COLUMNS - 1) * 24;
    private static final int GAP_X = 24;
    private static final int GAP_Y = 32;
    /** Room around the grid for a focused card's ring and scale-up. */
    private static final int RING = 24;
    /** Rows built straight away, so the first focus and the first screen don't wait for a layout. */
    private static final int ROWS_AT_ONCE = 3;

    private final Context ctx;
    private final Router router;
    private final Shell shell;
    private final Data data;
    private final View back;
    private final FrameLayout portraitBox;
    private final TextView job, facts, bio;
    private final FrameLayout bioMore;
    private final LinearLayout chips;
    private final View sortButton;
    private final ViewGroup box;
    private final LinearLayout grid;
    private final ViewTreeObserver.OnPreDrawListener windowBeforeDraw;
    private boolean windowQueued;

    private Person person;
    private Catalog.Credits credits;
    private View readMore;
    private String filter = "all";
    private PersonRules.Sort sort = PersonRules.Sort.KNOWN;
    /** The titles the grid shows, in order; a row holds the five from row × COLUMNS. */
    private List<Title> shown = Collections.emptyList();

    static Screen create(Context ctx, Router router, Person p) {
        return new PersonPage(ctx, router, p, StubPage.frame(ctx));
    }

    private PersonPage(Context ctx, Router router, Person p, FrameLayout page) {
        super(p.name, page, Layer.PAGE);
        this.ctx = ctx;
        this.router = router;
        this.shell = router.shell();
        this.data = router.data();
        this.person = p;

        back = Kit.button(ctx, ctx.getString(R.string.back), R.drawable.ic_ui_back, Kit.SMALL, false, null,
                StubPage.KEY_BACK, false, v -> shell.back());
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        backLp.leftMargin = Ui.px(Ui.SAFE_X);
        backLp.topMargin = Ui.px(Ui.SAFE_Y);
        page.addView(back, backLp);

        // .pp-side: the person, fixed while the grid scrolls.
        LinearLayout side = Kit.unclip(new LinearLayout(ctx));
        side.setOrientation(LinearLayout.VERTICAL);
        portraitBox = new FrameLayout(ctx);
        portraitBox.addView(portrait(p), matchParent());
        side.addView(portraitBox, new LinearLayout.LayoutParams(Ui.px(240), Ui.px(240)));
        job = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        job.setLetterSpacing(0.08f);
        side.addView(job, below(32));
        TextView name = Ui.text(ctx, 40, Ui.TEXT_1, Ui.display(Ui.MEDIUM));
        name.setText(p.name);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        side.addView(name, below(4));
        facts = Ui.text(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
        facts.setLineSpacing(0f, 1.3f);
        side.addView(facts, below(10));
        bio = Ui.text(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
        bio.setLineSpacing(0f, 1.4f);
        bio.setMaxLines(6);
        bio.setEllipsize(TextUtils.TruncateAt.END);
        side.addView(bio, below(20));
        bioMore = Kit.unclip(new FrameLayout(ctx));
        side.addView(bioMore, below(24));
        FrameLayout.LayoutParams sideLp = new FrameLayout.LayoutParams(Ui.px(SIDE_W),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sideLp.leftMargin = Ui.px(Ui.SAFE_X);
        sideLp.topMargin = Ui.px(TOP);
        page.addView(side, sideLp);

        // .pp-main: the chips and Sort on one line, the grid under them.
        FrameLayout main = Kit.unclip(new FrameLayout(ctx));
        LinearLayout bar = Kit.unclip(new LinearLayout(ctx));
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        chips = Kit.unclip(new LinearLayout(ctx));
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams stripLp = Kit.chipStripParams(0);
        stripLp.width = 0;
        stripLp.weight = 1f;
        // The strip reaches RING_ROOM past the chips on each side (chipStripParams): this leaves 24 px before Sort.
        stripLp.rightMargin = Ui.px(24) + stripLp.leftMargin;
        bar.addView(Kit.chipStrip(ctx, chips, 24), stripLp);
        sortButton = Kit.button(ctx, sortLabel(), R.drawable.ic_ui_sort, Kit.SMALL, false, null, KEY_SORT, false,
                v -> openSort());
        bar.addView(sortButton, wrap());
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.leftMargin = Ui.px(RING);
        barLp.rightMargin = Ui.px(RING);
        main.addView(bar, barLp);

        box = ScrollBox.vertical(ctx, 40, Kit.BOTTOM_FADE);
        LinearLayout col = Kit.unclip(new LinearLayout(ctx));
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(RING), Ui.px(16), Ui.px(RING), 0);
        grid = Kit.unclip(new LinearLayout(ctx));
        grid.setOrientation(LinearLayout.VERTICAL);
        col.addView(grid, wrap());
        col.addView(new View(ctx), new LinearLayout.LayoutParams(1, Ui.px(Kit.BOTTOM_FADE)));
        box.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        boxLp.topMargin = Ui.px(82);
        // Rows scrolled up stop at the box's top edge rather than draw over the chips; the column's top padding
        // and ScrollBox's pad leave a focused card's ring inside it.
        FrameLayout clip = new FrameLayout(ctx);
        clip.setClipChildren(true);
        clip.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        main.addView(clip, boxLp);
        // RING wider on each side than the grid, so a focused card's ring at either edge stays inside it.
        FrameLayout.LayoutParams mainLp = new FrameLayout.LayoutParams(Ui.px(GRID_W + 2 * RING),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        mainLp.rightMargin = Ui.px(Ui.SAFE_X - RING);
        mainLp.topMargin = Ui.px(TOP);
        page.addView(main, mainLp);

        page.addView(Kit.bottomFade(ctx));
        page.addView(Kit.guide(ctx, ctx.getString(data.catalog.isRemote() ? R.string.prices_library_guide
                : R.string.library_guide_left), ctx.getString(R.string.person_guide_right)));

        // Rows get their cards and posters as they come near the screen (ArtWindow's window).
        windowBeforeDraw = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                windowQueued = false;
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                updateWindow();
                return true;
            }
        };
        box.setOnScrollChangeListener((v, x, y, oldX, oldY) -> updateWindow());
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> queueWindow());
        // The first layout comes before the page is attached, when no window can be queued yet.
        page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                queueWindow();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (!windowQueued) return;
                windowQueued = false;
                ViewTreeObserver o = v.getViewTreeObserver();
                if (o.isAlive()) o.removeOnPreDrawListener(windowBeforeDraw);
            }
        });

        show(Catalog.Credits.fromCatalogue(p, PreviewRules.titlesFor(p.name, data.catalog.all())), false);
        data.catalog.person(p, c -> show(c, true));
    }

    @Override
    View defaultFocus() {
        View first = firstCard();
        return first != null ? first : back;
    }

    /** The catalogue's answer: their photo, facts, titles and roles. */
    private void show(Catalog.Credits c, boolean answer) {
        credits = c;
        if (c.person.photo != null && person.photo == null) {
            person = c.person;
            portraitBox.removeAllViews();
            portraitBox.addView(portrait(person), matchParent());
        }
        String dept = !c.department.isEmpty() ? c.department : person.role.isEmpty() ? ""
                : PersonRules.departmentOf(person.role);
        String noun = jobNoun(dept);
        job.setText(noun.toUpperCase(Locale.getDefault()));
        job.setVisibility(noun.isEmpty() ? View.GONE : View.VISIBLE);
        List<String> lines = new ArrayList<>();
        String born = PersonRules.born(c.birthday, Locale.getDefault());
        if (!born.isEmpty()) lines.add(ctx.getString(R.string.person_born, born));
        if (!c.birthplace.isEmpty()) lines.add(c.birthplace);
        facts.setText(TextUtils.join("\n", lines));
        facts.setVisibility(lines.isEmpty() ? View.GONE : View.VISIBLE);
        bio.setText(PersonRules.firstParagraph(c.biography));
        bio.setVisibility(c.biography.isEmpty() ? View.GONE : View.VISIBLE);
        bioMore.removeAllViews();
        readMore = null;
        if (!c.biography.isEmpty()) {
            readMore = Kit.button(ctx, ctx.getString(R.string.person_read_more), 0, Kit.SMALL, false, null, KEY_BIO,
                    false, v -> shell.push(biography()));
            bioMore.addView(readMore);
            // Read more only when there's more: another paragraph, or the first one cut at six lines.
            boolean more = PersonRules.hasMoreParagraphs(c.biography);
            readMore.setVisibility(more ? View.VISIBLE : View.INVISIBLE);
            if (!more) bio.post(() -> {
                android.text.Layout l = bio.getLayout();
                if (readMore != null && l != null && l.getLineCount() > 0
                        && l.getEllipsisCount(l.getLineCount() - 1) > 0) {
                    readMore.setVisibility(View.VISIBLE);
                }
            });
        }
        // A filter for a job the answer doesn't have goes back to All.
        if (filter.startsWith("job:") && !jobs().containsKey(filter.substring(4))) filter = "all";
        render();
        if (answer && shell.top() == this) shell.refocus();
    }

    /** The chips with their counts, Sort's label and the grid, for the filter and sort in force. */
    private void render() {
        List<Title> all = credits.titles;
        List<Title> included = new ArrayList<>(), paid = new ArrayList<>();
        for (Title t : all) {
            Offer o = data.cardOffer(t);
            if (o == null) continue;
            (PriceCards.isPaid(o) ? paid : included).add(t);
        }
        Map<String, List<Title>> jobs = jobs();
        chips.removeAllViews();
        chip("all", ctx.getString(R.string.person_all, all.size()));
        if (!included.isEmpty()) chip("included", ctx.getString(R.string.person_included, included.size()));
        if (!paid.isEmpty()) chip("paid", ctx.getString(R.string.person_paid, paid.size()));
        if (jobs.size() > 1) {
            for (Map.Entry<String, List<Title>> e : jobs.entrySet()) {
                chip("job:" + e.getKey(), ctx.getString(R.string.person_job_chip, department(e.getKey()),
                        e.getValue().size()));
            }
        }
        List<Title> chosen = filter.equals("included") ? included : filter.equals("paid") ? paid
                : filter.startsWith("job:") ? jobs.get(filter.substring(4)) : all;
        if (chosen == null) chosen = all;
        Kit.setLabel(sortButton, sortLabel());
        fill(PersonRules.sorted(chosen, sort, Locale.getDefault()));
    }

    private void chip(String id, String label) {
        boolean on = filter.equals(id);
        LinearLayout.LayoutParams lp = wrap();
        lp.leftMargin = chips.getChildCount() == 0 ? 0 : Ui.px(12);
        chips.addView(Kit.chip(ctx, label, on, false, GROUP_CHIPS, "person-" + id, on, v -> {
            if (filter.equals(id)) return;
            filter = id;
            render();
            shell.refocus();
        }), lp);
    }

    private Map<String, List<Title>> jobs() {
        return PersonRules.byDepartment(credits.titles, credits.roles, credits.department);
    }

    /** One empty row per five titles, the first few filled at once; the rest fill as they come near the screen. */
    private void fill(List<Title> titles) {
        shown = titles;
        grid.removeAllViews();
        box.scrollTo(0, 0);
        int rows = (titles.size() + PersonRules.COLUMNS - 1) / PersonRules.COLUMNS;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = Kit.unclip(new LinearLayout(ctx));
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.px(Kit.POSTER_H));
            lp.topMargin = r == 0 ? 0 : Ui.px(GAP_Y);
            grid.addView(row, lp);
            if (r < ROWS_AT_ONCE) build(r);
        }
        if (titles.isEmpty()) {
            TextView none = Ui.text(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
            none.setText(R.string.person_none);
            grid.addView(none, below(24));
        }
        queueWindow();
    }

    /** Row {@code r}'s cards, their posters left to the window. */
    private void build(int r) {
        LinearLayout row = (LinearLayout) grid.getChildAt(r);
        if (row == null || row.getChildCount() > 0) return;
        int from = r * PersonRules.COLUMNS;
        for (int i = from; i < Math.min(from + PersonRules.COLUMNS, shown.size()); i++) {
            Title t = shown.get(i);
            View card = Kit.smallCard(ctx, t, null, data, GROUP_GRID, "p-" + t.id, i == 0, false,
                    v -> router.openTitle(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(card.getLayoutParams());
            lp.leftMargin = i == from ? 0 : Ui.px(GAP_X);
            row.addView(card, lp);
        }
    }

    private void updateWindow() {
        if (!box.isLaidOut() || !view.isAttachedToWindow()) return;
        int h = box.getHeight();
        for (int r = 0; r < grid.getChildCount(); r++) {
            View row = grid.getChildAt(r);
            if (!(row instanceof LinearLayout)) continue;
            float[] at = Focus.rectIn(row, box);
            ArtWindow.Act act = ArtWindow.decide(at[1], at[3], h);
            if (act == ArtWindow.Act.KEEP) continue;
            boolean on = act == ArtWindow.Act.BIND;
            if (on) build(r);
            ViewGroup cards = (ViewGroup) row;
            for (int c = 0; c < cards.getChildCount(); c++) Kit.libraryArt(cards.getChildAt(c), on);
        }
    }

    private void queueWindow() {
        if (windowQueued || !view.isAttachedToWindow()) return;
        windowQueued = true;
        view.getViewTreeObserver().addOnPreDrawListener(windowBeforeDraw);
    }

    private View firstCard() {
        View row = grid.getChildAt(0);
        return row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 0 ? ((ViewGroup) row).getChildAt(0)
                : null;
    }

    /**
     * The grid is a closed box on its right and foot: the D-pad stops there rather than jumping to the chips or the
     * side. Left from its first column goes to the person's side (Read more, else Back). Moves into the grid from
     * outside it only land on cards in sight, never one scrolled under the chips.
     */
    @Override
    boolean onKey(Key key, View focused) {
        SpatialNav.Dir dir = Shell.dirOf(key);
        if (dir == null || focused == null) return false;
        if (Focus.isInside(grid, focused)) {
            ViewGroup row = (ViewGroup) focused.getParent();
            int r = grid.indexOfChild(row), c = row.indexOfChild(focused);
            if (dir == SpatialNav.Dir.LEFT && c == 0) {
                shell.focus(readMore != null && readMore.getVisibility() == View.VISIBLE ? readMore : back);
            } else if (dir == SpatialNav.Dir.RIGHT && c == row.getChildCount() - 1) {
                return true;
            } else if (dir == SpatialNav.Dir.DOWN && (r + 1) * PersonRules.COLUMNS >= shown.size()) {
                return true;
            } else if (dir == SpatialNav.Dir.DOWN) {
                // Below, the same column, or the shorter last row's last card.
                build(r + 1);
                ViewGroup next = (ViewGroup) grid.getChildAt(r + 1);
                shell.focus(next.getChildAt(Math.min(c, next.getChildCount() - 1)));
            } else {
                return false;
            }
            box.playSoundEffect(sound(dir));
            return true;
        }
        View next = nextFromOutside(focused, dir);
        if (next == null) return true;
        shell.focus(next);
        box.playSoundEffect(sound(dir));
        return true;
    }

    /** Shell's move (nearest, then the entered group's memory or default) over the items not scrolled out of sight. */
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

    private String sortLabel() {
        return ctx.getString(R.string.library_sort_chip, ctx.getString(sortWords(sort)));
    }

    private static int sortWords(PersonRules.Sort s) {
        switch (s) {
            case NEWEST:
                return R.string.person_sort_newest;
            case AZ:
                return R.string.library_sort_az_short;
            default:
                return R.string.person_sort_known;
        }
    }

    /** OK on Sort: the orders in a dropdown under it, the one in force checked and focused. */
    private void openSort() {
        String label = ctx.getString(R.string.library_sort);
        if (shell.isOpen(label)) return;
        List<MenuPanel.Row> rows = new ArrayList<>();
        for (PersonRules.Sort s : PersonRules.Sort.values()) {
            rows.add(new MenuPanel.Row(ctx.getString(sortWords(s)), 0, () -> {
                if (s == sort) return;
                sort = s;
                render();
            }).checked(s == sort));
        }
        shell.push(Dropdown.create(shell, label, sortButton, null, rows));
    }

    /** "Actor" over the name for TMDB's "Acting", and so on; a department it doesn't know as TMDB names it. */
    private String jobNoun(String dept) {
        switch (dept) {
            case "": return "";
            case "Acting": return ctx.getString(R.string.person_job_acting);
            case "Directing": return ctx.getString(R.string.person_job_directing);
            case "Writing": return ctx.getString(R.string.person_job_writing);
            case "Production": return ctx.getString(R.string.person_job_production);
            case "Creator": return ctx.getString(R.string.person_job_creator);
            default: return dept;
        }
    }

    /** A job chip's name: "Acting", "Directing"; a department it doesn't know as TMDB names it. */
    private String department(String dept) {
        switch (dept) {
            case "Acting": return ctx.getString(R.string.person_dept_acting);
            case "Directing": return ctx.getString(R.string.person_dept_directing);
            case "Writing": return ctx.getString(R.string.person_dept_writing);
            case "Production": return ctx.getString(R.string.person_dept_production);
            case "Creator": return ctx.getString(R.string.person_dept_creator);
            default: return dept;
        }
    }

    /**
     * Read more: the whole biography in a dialog over the page. Up and Down scroll a long one; Close or Back returns
     * to Read more.
     */
    private Screen biography() {
        FrameLayout root = new FrameLayout(ctx);
        View scrim = new View(ctx);
        scrim.setBackgroundColor(Ui.SCRIM);
        root.addView(scrim, matchParent());
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.px(56), Ui.px(52), Ui.px(56), Ui.px(52));
        panel.setBackground(Ui.round(Ui.BG_RAISED, Ui.RADIUS_XL, Ui.LINE_STRONG, Ui.LINE_W));
        panel.setElevation(Ui.px(40));
        TextView title = Ui.text(ctx, 38, Ui.TEXT_1, Ui.REGULAR);
        title.setText(person.name);
        panel.addView(title);
        TextView text = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        text.setText(credits.biography.trim());
        text.setLineSpacing(0f, 1.45f);
        ScrollView scroll = new ScrollView(ctx) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Ui.px(640), MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.addView(text);
        panel.addView(scroll, below(20));
        View close = Kit.button(ctx, ctx.getString(R.string.person_close), 0, Kit.PRIMARY, false, null, "person-close",
                true, v -> shell.pop());
        panel.addView(close, below(40));
        root.addView(panel, new FrameLayout.LayoutParams(Ui.px(1100), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return new Screen(person.name, root, Layer.DIALOG) {
            @Override
            View defaultFocus() {
                return close;
            }

            @Override
            boolean onKey(Key key, View focused) {
                SpatialNav.Dir dir = Shell.dirOf(key);
                if (dir == SpatialNav.Dir.UP || dir == SpatialNav.Dir.DOWN) {
                    scroll.smoothScrollBy(0, Ui.px(dir == SpatialNav.Dir.UP ? -200 : 200));
                    return true;
                }
                return dir != null;
            }
        };
    }

    private RingFrame portrait(Person p) {
        return Kit.portrait(ctx, p.name, p.photo, 240, 72);
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams below(int top) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = Ui.px(top);
        return lp;
    }
}
