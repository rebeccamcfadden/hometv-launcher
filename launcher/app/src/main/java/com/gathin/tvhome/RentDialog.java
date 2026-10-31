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

/**
 * The rent/buy dialog of design 07-prices A (design_preview/README.md, Prices; src/screens/rent.ts; screens.css
 * .dialog--rent, .qual, .pbtn): paid titles ask before leaving the launcher. "Moon on Apple TV Store"; an HD / 4K
 * switch when the store prices both, with a note on what 4K costs more; two big price buttons (Rent · HD 49 kr, "48
 * hours to finish once started"; Buy · HD 149 kr, "Yours to keep in Apple TV"), or the same buttons with "Price in
 * the app" for a store no source priced; Cancel; and a line saying payment finishes in the store's app, naming a
 * cheaper store when there is one. A dialog over the title preview, behind its own scrim.
 *
 * <p>First focus is the Rent button. OK on a quality selects it (segmented controls select on OK) and the buttons
 * show that quality's prices; OK on a price closes the dialog and opens the store's app ({@link Router#watch}, with
 * the title's link when there is one); Cancel and Back close it. What it says is {@link PriceCards}'.
 */
final class RentDialog {
    private RentDialog() {}

    private static final String G_QUALITY = "qual", G_PRICES = "prices", G_ACTIONS = "dialog";
    /** screens.css .dialog background. */
    private static final int FILL = 0xFF121A24;

    static Screen create(Context ctx, Router router, Title t, ServiceOffers s) {
        Data data = router.data();
        Shell shell = router.shell();
        String store = data.table.storeName(s.service);
        String app = data.table.appName(s.service);
        List<ServiceOffers> all = Offers.byService(PriceCards.ticked(t, data.stores()), data.services.subscribed());
        List<Quality> qualities = PriceCards.qualities(s);
        Quality[] quality = {PriceCards.startQuality(s)};

        FrameLayout root = new FrameLayout(ctx);
        View scrim = new View(ctx);
        scrim.setBackgroundColor(Ui.SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout box = Kit.unclip(new LinearLayout(ctx));
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.px(56), Ui.px(52), Ui.px(56), Ui.px(52));
        box.setBackground(Ui.round(FILL, Ui.RADIUS_XL, Ui.LINE_STRONG, 1));
        box.setElevation(Ui.px(40));

        TextView title = Ui.text(ctx, 38, Ui.TEXT_1, Ui.REGULAR);
        title.setText(ctx.getString(R.string.preview_dialog_title, t.name, store));
        box.addView(title);

        // The HD / 4K switch, only for a store that prices more than one quality.
        List<View> chips = new ArrayList<>();
        if (qualities.size() > 1) {
            LinearLayout row = Kit.unclip(new LinearLayout(ctx));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            box.addView(row, below(28));
            for (Quality q : qualities) {
                View chip = Kit.chip(ctx, q.label, q == quality[0], false, G_QUALITY, "q-" + q.label,
                        q == quality[0], null);
                chip.setMinimumHeight(Ui.px(50));
                chips.add(chip);
                row.addView(chip, gapped(row, 12));
            }
            String note = PriceCards.fourKNote(s);
            if (note != null) {
                TextView n = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
                n.setText(note);
                row.addView(n, gapped(row, 20));
            }
        }

        LinearLayout prices = Kit.unclip(new LinearLayout(ctx));
        prices.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(prices, below(30));

        View cancel = Kit.button(ctx, ctx.getString(R.string.preview_cancel), 0, Kit.NORMAL, false, G_ACTIONS,
                "cancel", false, v -> shell.pop());
        LinearLayout actions = Kit.unclip(new LinearLayout(ctx));
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(actions, below(40));

        TextView detail = Ui.text(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        detail.setText(PriceCards.paymentNote(app,
                PriceCards.cheaperElsewhere(all, s, data.services.installed(), data.table)));
        TitlePreview.lineHeight(detail, 27);
        box.addView(detail, below(12));

        Runnable paint = () -> {
            prices.removeAllViews();
            List<PriceCards.PriceButton> buttons = PriceCards.buttons(s, quality[0], app);
            for (int i = 0; i < buttons.size(); i++) {
                PriceCards.PriceButton b = buttons.get(i);
                View v = priceButton(ctx, b, i == 0, () -> {
                    shell.pop();
                    router.watch(t, b.offer);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = i == 0 ? 0 : Ui.px(16);
                prices.addView(v, lp);
            }
            // The buttons sit in two equal columns (.dlg-prices), so a single one takes half the width.
            if (buttons.size() == 1) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 0, 1f);
                lp.leftMargin = Ui.px(16);
                prices.addView(new View(ctx), lp);
            }
        };
        paint.run();
        for (int i = 0; i < chips.size(); i++) {
            Quality q = qualities.get(i);
            chips.get(i).setOnClickListener(v -> {
                if (quality[0] == q) return;
                quality[0] = q;
                for (int k = 0; k < chips.size(); k++) Kit.setSelected(chips.get(k), qualities.get(k) == q);
                paint.run();
                shell.refocus();
            });
        }

        root.addView(box, new FrameLayout.LayoutParams(Ui.px(900), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return new Screen(title.getText().toString(), root, Screen.Layer.DIALOG) {
            private boolean shown;

            @Override
            View defaultFocus() {
                return prices.getChildCount() > 0 && Focus.isFocusable(prices.getChildAt(0)) ? prices.getChildAt(0)
                        : cancel;
            }

            /** The scrim and the dialog fade in (screens.css .dialog, fade-in), once. */
            @Override
            void onShow() {
                if (shown || Ui.reducedMotion()) return;
                shown = true;
                for (View v : new View[]{scrim, box}) {
                    v.setAlpha(0f);
                    v.animate().alpha(1f).setDuration(Ui.FAST_MS).start();
                }
            }
        };
    }

    /**
     * .pbtn: the kind and quality (20 px), the amount (32 px) and what it means (18 px) on the surface fill; focused,
     * the accent fill with dark text and a 3% scale-up.
     */
    private static View priceButton(Context ctx, PriceCards.PriceButton b, boolean isDefault, Runnable onOk) {
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Ui.px(24), Ui.px(20), Ui.px(24), Ui.px(20));
        v.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, 1), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_L, Ui.ACCENT, 1)));
        v.addView(part(ctx, b.label, Ui.T_SMALL, Ui.TEXT_3));
        v.addView(part(ctx, b.amount, 32, Ui.TEXT_1), below(4));
        v.addView(part(ctx, b.detail, Ui.T_MIN, Ui.TEXT_3), below(4));
        v.setContentDescription(b.label + ", " + b.amount + ", " + b.detail);
        Ui.scaleOnFocus(v, 1.03f);
        return Focus.make(v, G_PRICES, "p-" + b.type.name().toLowerCase(java.util.Locale.ROOT), isDefault,
                x -> onOk.run());
    }

    private static TextView part(Context ctx, String text, float size, int color) {
        TextView t = Ui.line(ctx, size, color, Ui.REGULAR);
        t.setText(text);
        t.setTextColor(Ui.colors(color, color, Ui.ON_ACCENT));
        t.setDuplicateParentStateEnabled(true);
        return t;
    }

    private static LinearLayout.LayoutParams gapped(LinearLayout row, float gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = row.getChildCount() == 0 ? 0 : Ui.px(gap);
        return lp;
    }

    private static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }
}
