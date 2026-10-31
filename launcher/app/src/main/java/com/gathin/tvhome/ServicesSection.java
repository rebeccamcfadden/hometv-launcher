package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Streaming services in Launcher settings (design_preview/README.md, Streaming services and stores; V2 design
 * r2-services B; src/screens/panes/services.ts): "What does the household pay for?" over a four-column grid of tiles,
 * each with the service's name, a corner tick and a caption (Free or Free tier, then Installed in green or No app).
 * OK ticks or unticks any tile, free ones included. "+7 More services" opens a second grid ({@link ServicePicks#MORE})
 * and becomes "Fewer services"; it stays open while anything in it is ticked.
 *
 * <p>The ticks are the household's subscribed services from then on ({@link Services#choose}, kept by {@link
 * ServiceChoices}), so "Included" and "All N services" follow them; until the first tick the pane shows what the TV
 * worked out (the installed apps, or config.xml's subscribed_services). The row's value is "N ticked".
 */
final class ServicesSection implements SettingsSection {
    private static final String GROUP = "svc";
    private static final int COLUMNS = 4;

    private final Router router;
    private boolean opened;
    private LinearLayout column;
    private TextView count;
    private View first;

    ServicesSection(Router router) {
        this.router = router;
    }

    @Override
    public String id() {
        return "services";
    }

    @Override
    public int label() {
        return R.string.settings_services;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_tv;
    }

    @Override
    public CharSequence value(Context ctx) {
        Set<ServiceId> ticked = ticked();
        return ctx.getString(R.string.settings_ticked, ServicePicks.count(ticked, ServicePicks.listed(ticked)));
    }

    @Override
    public View pane(Context ctx, Host host) {
        column = PaneParts.column(ctx);
        fill(ctx, host);
        return PaneParts.scroller(ctx, column);
    }

    @Override
    public View firstFocus() {
        return first;
    }

    private Set<ServiceId> ticked() {
        return router.data().services.subscribed();
    }

    private void fill(Context ctx, Host host) {
        column.removeAllViews();
        Set<ServiceId> ticked = ticked();
        boolean shown = ServicePicks.moreShown(opened, ticked);
        // Shown because one of its services is ticked, it stays open when that one is unticked under the D-pad.
        opened = shown;
        TextView[] c = new TextView[1];
        PaneParts.headCount(ctx, column, ctx.getString(R.string.settings_services),
                ctx.getString(R.string.services_title), ctx.getString(R.string.services_sub), c);
        count = c[0];
        updateCount(ctx);

        List<View> main = new ArrayList<>();
        for (int i = 0; i < ServicePicks.MAIN.size(); i++) main.add(tile(ctx, host, ServicePicks.MAIN.get(i), i == 0));
        main.add(moreTile(ctx, host, shown, ticked));
        first = main.get(0);
        column.addView(grid(ctx, main), PaneParts.wide(24));
        if (shown) {
            column.addView(PaneParts.groupHeader(ctx, ctx.getString(R.string.services_more)), PaneParts.wide(26));
            List<View> more = new ArrayList<>();
            for (ServiceId s : ServicePicks.more(ticked)) more.add(tile(ctx, host, s, false));
            column.addView(grid(ctx, more), PaneParts.wide(12));
        }
    }

    private void updateCount(Context ctx) {
        Set<ServiceId> ticked = ticked();
        List<ServiceId> all = ServicePicks.moreShown(opened, ticked) ? ServicePicks.listed(ticked) : ServicePicks.MAIN;
        count.setText(ctx.getString(R.string.settings_ticked_of, ServicePicks.count(ticked, all), all.size()));
    }

    /** Rows of four tiles, each a quarter of the width less the 18 px gaps; 16 px between rows. */
    private static View grid(Context ctx, List<View> tiles) {
        LinearLayout rows = PaneParts.column(ctx);
        for (int i = 0; i < tiles.size(); i += COLUMNS) {
            LinearLayout row = PaneParts.across(ctx);
            for (int j = 0; j < COLUMNS; j++) {
                View t = i + j < tiles.size() ? tiles.get(i + j) : new View(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = j == 0 ? 0 : Ui.px(18);
                row.addView(t, lp);
            }
            rows.addView(row, PaneParts.wide(i == 0 ? 0 : 16));
        }
        return rows;
    }

    /**
     * .svc-tile: the 92 px face with the name and the corner tick, the caption under it. Ticked is the activated
     * state; focused, the face takes the accent ring and scales up 5%.
     */
    private View tile(Context ctx, Host host, ServiceId s, boolean isDefault) {
        Services services = router.data().services;
        String name = services.name(s);
        LinearLayout tile = PaneParts.column(ctx);
        tile.setContentDescription(name);

        FrameLayout face = new FrameLayout(ctx);
        face.setBackground(PaneParts.ticked(Ui.round(Ui.SURFACE_2, Ui.RADIUS_L, Ui.ACCENT, 4),
                Ui.round(Ui.SURFACE_2, Ui.RADIUS_L, Ui.ACCENT, 4), Ui.round(Ui.ACCENT_SOFT, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W),
                Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W)));
        face.setDuplicateParentStateEnabled(true);
        TextView mark = Ui.line(ctx, 26, Ui.TEXT_3, Ui.BOLD);
        mark.setText(name);
        mark.setTextColor(Ui.colors(Ui.TEXT_3, Ui.TEXT_1, Ui.TEXT_1));
        mark.setDuplicateParentStateEnabled(true);
        mark.setPadding(Ui.px(16), 0, Ui.px(16), 0);
        face.addView(mark, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        FrameLayout tick = new FrameLayout(ctx);
        GradientDrawable off = Ui.oval(Color.TRANSPARENT);
        off.setStroke(Ui.px(2), Ui.LINE_STRONG);
        tick.setBackground(PaneParts.ticked(Ui.oval(Ui.ACCENT), off, Ui.oval(Ui.ACCENT), off));
        tick.setDuplicateParentStateEnabled(true);
        ImageView check = new ImageView(ctx);
        check.setImageResource(R.drawable.ic_ui_check);
        check.setImageTintList(new ColorStateList(new int[][]{{android.R.attr.state_activated}, {}},
                new int[]{Ui.ON_ACCENT, Color.TRANSPARENT}));
        check.setDuplicateParentStateEnabled(true);
        tick.addView(check, new FrameLayout.LayoutParams(Ui.px(20), Ui.px(20), Gravity.CENTER));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(Ui.px(30), Ui.px(30), Gravity.TOP | Gravity.END);
        tlp.topMargin = tlp.rightMargin = Ui.px(8);
        face.addView(tick, tlp);
        tile.addView(face, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(92)));

        LinearLayout cap = PaneParts.across(ctx);
        TextView n = Ui.line(ctx, 19, Ui.TEXT_2, Ui.REGULAR);
        n.setText(name);
        cap.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ServicePicks.Hint h = ServicePicks.hint(s);
        if (h != ServicePicks.Hint.NONE) {
            cap.addView(PaneParts.hint(ctx, ctx.getString(h == ServicePicks.Hint.FREE ? R.string.settings_hint_free
                    : R.string.settings_hint_free_tier), false), PaneParts.after(10));
        }
        boolean installed = services.isInstalled(s);
        cap.addView(PaneParts.hint(ctx, ctx.getString(installed ? R.string.settings_hint_installed
                : R.string.settings_hint_no_app), installed), PaneParts.after(6));
        tile.addView(cap, PaneParts.wide(8));

        PaneParts.setTicked(tile, ticked().contains(s));
        Focus.make(tile, GROUP, "svc-" + s.id, isDefault, v -> {
            Set<ServiceId> next = ServicePicks.toggle(ticked(), s);
            services.choose(next);
            host.valueChanged();
            if (ServicePicks.MAIN.contains(s)) {
                PaneParts.setTicked(tile, next.contains(s));
                updateCount(ctx);
                return;
            }
            // The More tile's caption depends on what is ticked below it. A service only config.xml named (Hulu)
            // leaves the grid once unticked; focus then goes to More.
            fill(ctx, host);
            View again = Focus.byKey(column, "svc-" + s.id);
            host.shell().focus(again != null ? again : Focus.byKey(column, "svc-more"));
        });
        tile.setOnFocusChangeListener((v, focused) -> {
            float sc = focused ? 1.05f : 1f;
            face.animate().scaleX(sc).scaleY(sc).setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
        });
        return tile;
    }

    /** "+7 More services" with "Hayu, MUBI, Plex…" under it; open, "Fewer services". */
    private View moreTile(Context ctx, Host host, boolean shown, Set<ServiceId> ticked) {
        boolean moreTicked = ServicePicks.moreShown(false, ticked);
        LinearLayout tile = PaneParts.column(ctx);
        LinearLayout face = PaneParts.column(ctx);
        face.setGravity(Gravity.CENTER);
        face.setBackground(Ui.states(PaneParts.dashed(Color.TRANSPARENT, Ui.RADIUS_L, Ui.LINE_STRONG), null,
                Ui.round(Ui.SURFACE_2, Ui.RADIUS_L, Ui.ACCENT, 4)));
        face.setDuplicateParentStateEnabled(true);
        TextView big = Ui.line(ctx, Ui.T_H3, Ui.TEXT_2, Ui.REGULAR);
        big.setText(shown ? ctx.getString(R.string.services_fewer_mark)
                : ctx.getString(R.string.services_more_count, ServicePicks.MORE.size()));
        face.addView(big);
        TextView label = Ui.line(ctx, 19, Ui.TEXT_2, Ui.REGULAR);
        label.setText(shown ? R.string.services_fewer : R.string.services_more);
        face.addView(label, PaneParts.below(4));
        tile.addView(face, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(92)));
        TextView cap = Ui.line(ctx, 19, Ui.TEXT_2, Ui.REGULAR);
        cap.setText(!shown ? ctx.getString(R.string.services_more_cap) : moreTicked
                ? ctx.getString(R.string.services_fewer_cap_ticked)
                : ctx.getString(R.string.services_fewer_cap, ServicePicks.MORE.size()));
        tile.addView(cap, PaneParts.wide(8));
        tile.setContentDescription(label.getText());
        Focus.make(tile, GROUP, "svc-more", false, v -> {
            Set<ServiceId> now = ticked();
            boolean open = ServicePicks.moreShown(opened, now);
            if (open && ServicePicks.moreShown(false, now)) {
                host.shell().toast(ctx.getString(R.string.services_untick_first));
                return;
            }
            opened = !open;
            fill(ctx, host);
            host.shell().focus(Focus.byKey(column, "svc-more"));
        });
        tile.setOnFocusChangeListener((v, focused) -> {
            float sc = focused ? 1.05f : 1f;
            face.animate().scaleX(sc).scaleY(sc).setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
        });
        return tile;
    }
}
