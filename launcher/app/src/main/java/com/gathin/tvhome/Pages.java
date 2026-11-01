package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * The nav's pages: Home (HomePage, M2), Movies and TV Shows (LibraryPage, M4) and Search (SearchPage, M6), on the
 * shared page furniture: the shade over the evening landscape and the nav. Music was a placeholder until F17 (the
 * project lead, 2026-11-01) took it out of the nav.
 */
final class Pages {
    private Pages() {}

    static Screen create(Context ctx, NavBar.Host host, Screen.Tab tab) {
        switch (tab) {
            case MOVIES:
            case TV:
                // M4: Movies and TV Shows are one page, parametrised by the tab.
                return LibraryPage.create(ctx, host, tab);
            case SEARCH:
                // M6: the Search page.
                return SearchPage.create((MainActivity) ctx, host);
            default:
                // M2: Home.
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
}
