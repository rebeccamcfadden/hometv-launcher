package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZoneId;

/**
 * The views of design 07-prices A that the title preview's Where to watch tab shows (design_preview/src/screens/
 * preview.ts, serviceCard; screens.css .service, .price-lines, .pl, .tag-best): one card per service, its price
 * lines with the amounts in semibold and the Cheapest store's deciding price in green, and the credit under the row.
 * What they say is {@link PriceCards}'; this class only draws it. Its colours live here, so the shared tokens in
 * {@link Ui} stay as they are.
 */
final class PriceViews {
    private PriceViews() {}

    /** tokens.css --good: the Cheapest tag and the deciding price. */
    static final int CHEAPEST = 0xFF8FD9A8;
    /** tokens.css --good-soft: the Cheapest tag's fill. */
    static final int CHEAPEST_SOFT = 0x298FD9A8;

    /**
     * A Where to watch card (.service): 376 wide, the name with "Included", "Free" or the green "Cheapest" tag, then
     * the offer or the price lines and the detail. Its height follows the row's tallest card. Focused: the 4 px
     * accent ring and a 3% scale-up.
     */
    static View serviceCard(Context ctx, PriceCards.Card c, String name, String group, boolean isDefault,
                            View.OnClickListener onOk) {
        RingFrame card = new RingFrame(ctx, Ui.RADIUS_L, false, Ui.SURFACE);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(24), Ui.px(22), Ui.px(24), Ui.px(22));

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.MEDIUM);
        title.setText(name);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tag = c.badge != null ? TitlePreview.badge(ctx, c.badge) : c.cheapest ? cheapestTag(ctx) : null;
        if (tag != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.px(12);
            head.addView(tag, lp);
        }
        col.addView(head);

        if (!c.lines.isEmpty()) {
            LinearLayout lines = new LinearLayout(ctx);
            lines.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < c.lines.size(); i++) {
                LinearLayout.LayoutParams lp = full();
                lp.topMargin = i == 0 ? 0 : Ui.px(6);
                lines.addView(priceLine(ctx, c, i), lp);
            }
            col.addView(lines, below(12));
        } else if (c.text != null) {
            // A store with no known price keeps the price line's look (.pl); a service's offer is .service-offer.
            boolean store = !c.offers.watchable() && (c.offers.rent != null || c.offers.buy != null);
            TextView text = Ui.line(ctx, 22, Ui.TEXT_1, Ui.REGULAR);
            text.setText(c.text);
            col.addView(text, below(store ? 12 : 10));
        }
        if (c.detail != null) {
            TextView detail = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
            detail.setText(c.detail);
            col.addView(detail, below(4));
        }

        card.content().addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // MATCH_PARENT in the row's strip makes every card as tall as the tallest, as the prototype's flex row does.
        card.setLayoutParams(new ViewGroup.LayoutParams(Ui.px(376), ViewGroup.LayoutParams.MATCH_PARENT));
        card.setContentDescription(name + ", " + c.said());
        card.setOnFocusChangeListener((v, focused) -> card.setRing(focused));
        Ui.scaleOnFocus(card, 1.03f);
        return Focus.make(card, group, "svc-" + c.offers.service.id, isDefault, onOk);
    }

    /** .pl: "Rent 49 kr · Buy 149 kr" (amounts semibold, the deciding one green) with the quality on the right. */
    private static View priceLine(Context ctx, PriceCards.Card c, int i) {
        PriceCards.Line l = c.lines.get(i);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (l.rent != null) amount(b, "Rent ", l.rent, c.isGreen(i, OfferType.RENT));
        if (l.rent != null && l.buy != null) b.append(" · ");
        if (l.buy != null) amount(b, "Buy ", l.buy, c.isGreen(i, OfferType.BUY));
        // Two lines at most: a long pair of prices wraps, as in the prototype, rather than hiding an amount.
        TextView prices = Ui.text(ctx, 22, Ui.TEXT_1, Ui.REGULAR);
        prices.setMaxLines(2);
        prices.setEllipsize(TextUtils.TruncateAt.END);
        prices.setText(b);
        row.addView(prices, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView quality = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        quality.setText(l.quality);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.leftMargin = Ui.px(16);
        row.addView(quality, qlp);
        return row;
    }

    private static void amount(SpannableStringBuilder b, String verb, Money m, boolean green) {
        b.append(verb);
        int start = b.length();
        b.append(m.format());
        b.setSpan(new FaceSpan(Ui.MEDIUM), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (green) b.setSpan(new ForegroundColorSpan(CHEAPEST), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** .tag-best: the green "Cheapest" pill, 18 px. */
    static TextView cheapestTag(Context ctx) {
        TextView t = Ui.line(ctx, Ui.T_MIN, CHEAPEST, Ui.MEDIUM);
        t.setText(R.string.prices_cheapest);
        t.setPadding(Ui.px(10), Ui.px(2), Ui.px(10), Ui.px(2));
        t.setBackground(Ui.round(CHEAPEST_SOFT, Ui.RADIUS_PILL));
        return t;
    }

    /**
     * The caption under the row ({@link PriceCards#credit}): the sources, and offline when the prices were saved on
     * the TV's clock. Null for the mock catalogue.
     */
    static String credit(Context ctx, Catalog catalog, Title t) {
        String attribution = catalog.attribution(t);
        if (attribution == null) return null;
        boolean offline = !NetworkState.read(ctx).online;
        long at = offline ? catalog.pricesSavedAt(t) : 0;
        String saved = at > 0 ? PriceCards.savedWhen(at, System.currentTimeMillis(), ZoneId.systemDefault(),
                DateFormat.is24HourFormat(ctx)) : null;
        return PriceCards.credit(attribution, offline, saved);
    }

    private static LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = full();
        lp.topMargin = Ui.px(top);
        return lp;
    }

    /** A span that sets a typeface, which TypefaceSpan only takes from API 28. */
    private static final class FaceSpan extends MetricAffectingSpan {
        private final Typeface face;

        FaceSpan(Typeface face) {
            this.face = face;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setTypeface(face);
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            tp.setTypeface(face);
        }
    }
}
