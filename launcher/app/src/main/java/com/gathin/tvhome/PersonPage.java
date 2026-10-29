package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * The person page stub (design_preview/src/screens/person.ts; screens.css .person-page), pushed from Cast &amp; crew:
 * a Back button, a large portrait with initials, "Person · not designed yet" over the name, and the titles the
 * person appears in as small cards, six to a row. OK on a card opens its preview over this page; Back returns to
 * the preview the page came from. First focus is the first card, or Back when there is none.
 */
final class PersonPage {
    private PersonPage() {}

    private static final int COLUMNS = 6;
    private static final int PORTRAIT_FILL = 0xFF1C2633;

    static Screen create(Context ctx, Router router, String name) {
        Data data = router.data();
        List<Title> titles = PreviewRules.titlesFor(name, data.catalog.all());
        FrameLayout page = StubPage.frame(ctx);

        LinearLayout col = Kit.unclip(new LinearLayout(ctx));
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(Ui.SAFE_X), Ui.px(Ui.SAFE_Y), Ui.px(Ui.SAFE_X), Ui.px(Ui.SAFE_Y));

        View back = Kit.button(ctx, ctx.getString(R.string.back), R.drawable.ic_ui_back, Kit.SMALL, false, null,
                StubPage.KEY_BACK, false, v -> router.shell().back());
        col.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = new LinearLayout(ctx);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        RingFrame portrait = new RingFrame(ctx, 80, true, PORTRAIT_FILL);
        TextView initials = Ui.line(ctx, 52, Ui.TEXT_2, Ui.MEDIUM);
        initials.setText(TitleText.initials(name));
        initials.setGravity(Gravity.CENTER);
        portrait.content().addView(initials, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        hero.addView(portrait, new LinearLayout.LayoutParams(Ui.px(160), Ui.px(160)));
        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        // Upper-cased here rather than with setAllCaps, which measures the lower-case text and cuts it short.
        eyebrow.setText(ctx.getString(R.string.preview_person_eyebrow).toUpperCase(Locale.ROOT));
        eyebrow.setLetterSpacing(0.08f);
        words.addView(eyebrow);
        TextView title = Ui.line(ctx, Ui.T_H1, Ui.TEXT_1, Ui.REGULAR);
        title.setText(name);
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

        TextView known = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        known.setText(ctx.getResources().getQuantityString(R.plurals.preview_known_for, titles.size(), titles.size()));
        LinearLayout.LayoutParams klp = wrap();
        klp.topMargin = Ui.px(54);
        klp.bottomMargin = Ui.px(16);
        col.addView(known, klp);

        View first = null;
        LinearLayout row = null;
        for (int i = 0; i < titles.size(); i++) {
            Title t = titles.get(i);
            if (i % COLUMNS == 0) {
                row = Kit.unclip(new LinearLayout(ctx));
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = wrap();
                rlp.topMargin = i == 0 ? 0 : Ui.px(30);
                col.addView(row, rlp);
            }
            View card = Kit.smallCard(ctx, t, null, data, "known", "p-" + t.id, i == 0, v -> router.openTitle(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(card.getLayoutParams());
            lp.leftMargin = row.getChildCount() == 0 ? 0 : Ui.px(20);
            row.addView(card, lp);
            if (first == null) first = card;
        }

        ViewGroup scroll = ScrollBox.vertical(ctx, Ui.SAFE_Y);
        scroll.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View start = first != null ? first : back;
        return new Screen(name, page, Screen.Layer.PAGE) {
            @Override
            View defaultFocus() {
                return start;
            }
        };
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
