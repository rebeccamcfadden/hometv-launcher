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
 * The rent/buy dialog (design_preview/src/screens/confirm.ts and preview.ts, purchase; screens.css .dialog): paid
 * titles ask before leaving the launcher. "Arrival on Apple TV", the quality and the app it opens, a note that
 * payment finishes in that app, and Rent and Buy buttons with their prices (none for a store no source priced), then
 * Cancel. A dialog over whatever
 * shows (the title preview), behind its own scrim. First focus is the first button; Rent or Buy closes the dialog
 * and opens the store's app (Router.watch with that offer); Cancel and Back close it.
 */
final class RentDialog {
    private RentDialog() {}

    private static final String GROUP = "dialog";
    /** screens.css .dialog background. */
    private static final int FILL = 0xFF121A24;

    static Screen create(Context ctx, Router router, Title t, ServiceOffers s) {
        String service = router.data().table.name(s.service);
        Shell shell = router.shell();
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
        title.setText(ctx.getString(R.string.preview_dialog_title, t.name, service));
        box.addView(title);
        TextView body = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        String app = router.data().table.appName(s.service);
        body.setText(s.quality == null || s.quality.isEmpty() ? ctx.getString(R.string.prices_dialog_body, app)
                : ctx.getString(R.string.preview_dialog_body, s.quality, app));
        box.addView(body, below(16));
        TextView note = Ui.text(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        note.setText(R.string.preview_dialog_note);
        TitlePreview.lineHeight(note, 27);
        box.addView(note, below(12));

        List<View> buttons = new ArrayList<>();
        if (s.rent != null) {
            buttons.add(Kit.button(ctx, label(ctx, s.rent), 0, Kit.PRIMARY,
                    false, GROUP, "rent", true, v -> {
                        shell.pop();
                        router.watch(t, s.rent);
                    }));
        }
        if (s.buy != null) {
            buttons.add(Kit.button(ctx, label(ctx, s.buy), 0,
                    buttons.isEmpty() ? Kit.PRIMARY : Kit.NORMAL, false, GROUP, "buy", buttons.isEmpty(), v -> {
                        shell.pop();
                        router.watch(t, s.buy);
                    }));
        }
        buttons.add(Kit.button(ctx, ctx.getString(R.string.preview_cancel), 0, Kit.NORMAL, false, GROUP, "cancel",
                buttons.isEmpty(), v -> shell.pop()));
        LinearLayout actions = Kit.unclip(new LinearLayout(ctx));
        actions.setOrientation(LinearLayout.HORIZONTAL);
        for (View b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = actions.getChildCount() == 0 ? 0 : Ui.px(16);
            actions.addView(b, lp);
        }
        box.addView(actions, below(40));

        root.addView(box, new FrameLayout.LayoutParams(Ui.px(860), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        View first = buttons.get(0);
        return new Screen(title.getText().toString(), root, Screen.Layer.DIALOG) {
            private boolean shown;

            @Override
            View defaultFocus() {
                return first;
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

    /** "Rent · 39 kr", "Buy · from 99 kr", or just "Rent" for a store whose price isn't known (its app shows it). */
    private static String label(Context ctx, Offer o) {
        String price = Offers.price(o);
        boolean rent = o.type == OfferType.RENT;
        if (price == null) return ctx.getString(rent ? R.string.prices_rent : R.string.prices_buy);
        return ctx.getString(rent ? R.string.preview_rent : R.string.preview_buy, price);
    }

    private static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }
}
