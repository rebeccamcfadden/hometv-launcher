package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The person page stub (design_preview/src/screens/person.ts; screens.css .person-page), pushed from Cast &amp; crew
 * or Search: a Back button, a large portrait with initials (and their TMDB photo over them once it loads), "Person ·
 * not designed yet" over the name, and the titles the person is known for as small cards, six to a row. The titles
 * the catalogue already knows show at once; TMDB's list of their best-known titles replaces them when it arrives.
 * OK on a card opens its preview over this page; Back returns to where the page came from. First focus is the first
 * card, or Back when there is none.
 */
final class PersonPage extends Screen {
    private static final int COLUMNS = 6;

    private final Context ctx;
    private final Router router;
    private final Data data;
    private final View back;
    private final FrameLayout portraitBox;
    private final TextView known;
    private final LinearLayout grid;
    private View first;
    private Person person;

    static Screen create(Context ctx, Router router, Person p) {
        return new PersonPage(ctx, router, p, StubPage.frame(ctx));
    }

    private PersonPage(Context ctx, Router router, Person p, FrameLayout page) {
        super(p.name, page, Layer.PAGE);
        this.ctx = ctx;
        this.router = router;
        this.data = router.data();
        this.person = p;

        LinearLayout col = Kit.unclip(new LinearLayout(ctx));
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(Ui.SAFE_X), Ui.px(Ui.SAFE_Y), Ui.px(Ui.SAFE_X), Ui.px(Ui.SAFE_Y));

        back = Kit.button(ctx, ctx.getString(R.string.back), R.drawable.ic_ui_back, Kit.SMALL, false, null,
                StubPage.KEY_BACK, false, v -> router.shell().back());
        col.addView(back, wrap());

        LinearLayout hero = new LinearLayout(ctx);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        portraitBox = new FrameLayout(ctx);
        portraitBox.addView(portrait(p), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        hero.addView(portraitBox, new LinearLayout.LayoutParams(Ui.px(160), Ui.px(160)));
        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        // Upper-cased here rather than with setAllCaps, which measures the lower-case text and cuts it short.
        eyebrow.setText(ctx.getString(R.string.preview_person_eyebrow).toUpperCase(Locale.ROOT));
        eyebrow.setLetterSpacing(0.08f);
        words.addView(eyebrow);
        TextView title = Ui.line(ctx, Ui.T_H1, Ui.TEXT_1, Ui.display(Ui.REGULAR));
        title.setText(p.name);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = Ui.px(6);
        words.addView(title, tlp);
        // The column takes the rest of the row: wrapped, it took the name's width and cut the longer eyebrow short.
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wlp.leftMargin = Ui.px(36);
        hero.addView(words, wlp);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.px(40);
        col.addView(hero, hlp);

        known = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        LinearLayout.LayoutParams klp = wrap();
        klp.topMargin = Ui.px(54);
        klp.bottomMargin = Ui.px(16);
        col.addView(known, klp);

        grid = Kit.unclip(new LinearLayout(ctx));
        grid.setOrientation(LinearLayout.VERTICAL);
        col.addView(grid, wrap());

        ViewGroup scroll = ScrollBox.vertical(ctx, Ui.SAFE_Y);
        scroll.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        fill(PreviewRules.titlesFor(p.name, data.catalog.all()));
        data.catalog.person(p, this::show);
    }

    @Override
    View defaultFocus() {
        return first != null ? first : back;
    }

    /** The catalogue's answer: their photo, and the titles they are best known for. */
    private void show(Catalog.Credits c) {
        if (c.person.photo != null && person.photo == null) {
            person = c.person;
            portraitBox.removeAllViews();
            portraitBox.addView(portrait(person), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        fill(c.titles);
        if (router.shell().top() == this) router.shell().refocus();
    }

    private RingFrame portrait(Person p) {
        return Kit.portrait(ctx, p.name, p.photo, 160, 52);
    }

    private void fill(List<Title> titles) {
        known.setText(ctx.getResources().getQuantityString(R.plurals.preview_known_for, titles.size(), titles.size()));
        grid.removeAllViews();
        first = null;
        LinearLayout row = null;
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            Title t = titles.get(i);
            if (i % COLUMNS == 0) {
                row = Kit.unclip(new LinearLayout(ctx));
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = wrap();
                rlp.topMargin = i == 0 ? 0 : Ui.px(30);
                grid.addView(row, rlp);
            }
            View card = Kit.smallCard(ctx, t, null, data, "known", "p-" + t.id, i == 0, v -> router.openTitle(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(card.getLayoutParams());
            lp.leftMargin = row.getChildCount() == 0 ? 0 : Ui.px(20);
            row.addView(card, lp);
            cards.add(card);
        }
        if (!cards.isEmpty()) first = cards.get(0);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
