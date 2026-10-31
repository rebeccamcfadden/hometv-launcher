package com.gathin.tvhome;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Set;

/**
 * Stores for rent and buy in Launcher settings (design_preview/README.md, Streaming services and stores; V2 design
 * r2-services A; src/screens/panes/services.ts, storesPane): "Where may we show prices from?", one check row per
 * store ({@link ServicePicks#STORES}) saying which app renting opens and whether it is on the TV, and a note on
 * kronor. All start ticked. The ticks are saved ({@link ServiceChoices}) and the real catalogue leaves the unticked
 * stores' rent and buy offers out at once ({@link Catalog#setPriceStores}). The row's value is "N ticked".
 */
final class StoresSection implements SettingsSection {
    private static final String GROUP = "stores";

    private final Router router;
    private TextView count;
    private View first;

    StoresSection(Router router) {
        this.router = router;
    }

    @Override
    public String id() {
        return "stores";
    }

    @Override
    public int label() {
        return R.string.settings_stores;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_cart;
    }

    @Override
    public CharSequence value(Context ctx) {
        return ctx.getString(R.string.settings_ticked, ServicePicks.count(ServiceChoices.stores(ctx), ServicePicks.STORES));
    }

    @Override
    public View pane(Context ctx, Host host) {
        LinearLayout column = PaneParts.column(ctx);
        TextView[] c = new TextView[1];
        PaneParts.headCount(ctx, column, ctx.getString(R.string.settings_stores), ctx.getString(R.string.stores_title),
                ctx.getString(R.string.stores_sub), c);
        count = c[0];
        updateCount(ctx);

        Services services = router.data().services;
        ServiceTable table = services.table();
        LinearLayout rows = PaneParts.column(ctx);
        first = null;
        for (ServiceId s : ServicePicks.STORES) {
            // Viaplay streams and sells under one name; its rent and buy is the Viaplay Store.
            String name = table.isStore(s) ? table.name(s) : ctx.getString(R.string.stores_store_name, table.name(s));
            TextView opens = PaneParts.hint(ctx, ctx.getString(R.string.stores_opens, table.appName(s)), false);
            boolean installed = services.isInstalled(s);
            TextView app = PaneParts.hint(ctx, ctx.getString(installed ? R.string.settings_hint_installed
                    : R.string.settings_hint_no_app), installed);
            LinearLayout[] row = new LinearLayout[1];
            row[0] = PaneParts.checkRow(ctx, name, Arrays.asList(opens, app), ServiceChoices.stores(ctx).contains(s),
                    GROUP, "store-" + s.id, first == null, v -> {
                        Set<ServiceId> next = ServicePicks.toggle(ServiceChoices.stores(ctx), s);
                        ServiceChoices.setStores(ctx, next);
                        router.data().catalog.setPriceStores(next);
                        PaneParts.setTicked(row[0], next.contains(s));
                        updateCount(ctx);
                        host.valueChanged();
                    });
            if (first == null) first = row[0];
            rows.addView(row[0], PaneParts.wide(rows.getChildCount() == 0 ? 0 : 8));
        }
        column.addView(rows, PaneParts.wide(26));
        column.addView(PaneParts.note(ctx, ctx.getString(R.string.stores_note), false), PaneParts.wide(22));
        return PaneParts.scroller(ctx, column);
    }

    @Override
    public View firstFocus() {
        return first;
    }

    private void updateCount(Context ctx) {
        count.setText(ctx.getString(R.string.settings_ticked_of,
                ServicePicks.count(ServiceChoices.stores(ctx), ServicePicks.STORES), ServicePicks.STORES.size()));
    }
}
