package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The nav's pages. Home is HomePage (M2); the others are still placeholders (design_preview/src/screens/placeholder.ts)
 * on the shared page furniture: the shade over the evening landscape and the nav. Later milestones replace them one
 * by one (docs/rework.md, Milestones).
 */
final class Pages {
    private Pages() {}

    static Screen create(Context ctx, NavBar.Host host, Screen.Tab tab) {
        switch (tab) {
            case MOVIES:
            case TV:
                // M4: Movies and TV Shows are one page, parametrised by the tab.
                return LibraryPage.create(ctx, host, tab);
            case MUSIC:
                return placeholder(ctx, host, tab, R.string.page_not_designed, R.string.nav_music, R.string.page_music_note);
            case SEARCH:
                // M6: the Search page.
                return SearchPage.create((MainActivity) ctx, host);
            default:
                // M2: Home is built (HomePage); the other tabs follow in their milestones.
                return HomePage.create((MainActivity) ctx, host);
        }
    }

    /** A page: the shade, the nav with this tab active, and whatever content goes above them. */
    static FrameLayout page(Context ctx, NavBar.Host host, Screen.Tab tab) {
        FrameLayout page = new FrameLayout(ctx);
        View shade = new View(ctx);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.SHADE_TOP, Ui.SHADE_BOTTOM}));
        shade.setTag(R.id.ui_still, Boolean.TRUE);
        page.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(NavBar.build(ctx, host, tab));
        return page;
    }

    private static Screen placeholder(Context ctx, NavBar.Host host, Screen.Tab tab, int eyebrow, int title, int note) {
        FrameLayout page = page(ctx, host, tab);
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView e = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        e.setText(eyebrow);
        e.setAllCaps(true);
        e.setLetterSpacing(0.08f);
        body.addView(e);
        TextView t = Ui.line(ctx, 64, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(14);
        body.addView(t, tlp);
        TextView n = Ui.text(ctx, Ui.T_H3, Ui.TEXT_2, Ui.REGULAR);
        n.setText(note);
        n.setLineSpacing(0f, 1.5f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.px(18);
        body.addView(n, nlp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(1100), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        lp.topMargin = Ui.px(300);
        lp.leftMargin = Ui.px(Ui.SAFE_X);
        page.addView(body, lp);
        return new Screen(ctx.getString(title), page, Screen.Layer.PAGE) {
            @Override
            View defaultFocus() {
                return Focus.byKey(page, Shell.navKey(tab));
            }
        };
    }
}
