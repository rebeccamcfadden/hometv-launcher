package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A pushed page the design doesn't cover yet (design_preview/src/screens/placeholder.ts, without a tab): the shade,
 * "Not designed yet", the title, a note and a Back button, which is its first focus. Also the frame and the Back
 * button All Apps uses.
 */
final class StubPage {
    private StubPage() {}

    static final String KEY_BACK = "back";
    /** Space kept around a focusable item for its scale-up (3–8%), so no parent clips it. */
    static final int ROOM = 8;

    /** v in a frame with ROOM on every side that lets it draw past its own edges. */
    static FrameLayout roomy(View v, int height) {
        FrameLayout box = new FrameLayout(v.getContext());
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setPadding(Ui.px(ROOM), Ui.px(ROOM), Ui.px(ROOM), Ui.px(ROOM));
        box.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height));
        return box;
    }

    static Screen create(Context ctx, Shell shell, int title, int note) {
        FrameLayout page = frame(ctx);
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        // Room for the Back button's focus scale-up, which a clipping parent would cut at the button's edges.
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPadding(Ui.px(ROOM), 0, 0, Ui.px(ROOM));
        TextView e = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        e.setText(R.string.page_not_designed);
        e.setAllCaps(true);
        e.setLetterSpacing(0.08f);
        body.addView(e);
        TextView t = Ui.line(ctx, 64, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = Ui.px(14);
        body.addView(t, tlp);
        TextView n = Ui.text(ctx, Ui.T_H3, Ui.TEXT_2, Ui.REGULAR);
        n.setText(note);
        n.setLineSpacing(0f, 1.5f);
        LinearLayout.LayoutParams nlp = wrap();
        nlp.topMargin = Ui.px(18);
        body.addView(n, nlp);
        View back = backButton(ctx, shell);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.px(66));
        blp.topMargin = Ui.px(44);
        body.addView(back, blp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(1100), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        lp.topMargin = Ui.px(300);
        lp.leftMargin = Ui.px(Ui.SAFE_X - ROOM);
        page.addView(body, lp);
        return new Screen(ctx.getString(title), page, Screen.Layer.PAGE) {
            @Override
            View defaultFocus() {
                return back;
            }
        };
    }

    /** A pushed page's background: the shade over the landscape, as on every page, without the nav. */
    static FrameLayout frame(Context ctx) {
        FrameLayout page = new FrameLayout(ctx);
        View shade = new View(ctx);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.SHADE_TOP, Ui.SHADE_BOTTOM}));
        shade.setTag(R.id.ui_still, Boolean.TRUE);
        page.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return page;
    }

    /** The prototype's Back button (widgets.ts, button with the back icon): a pill, accent when focused. */
    static View backButton(Context ctx, Shell shell) {
        LinearLayout b = new LinearLayout(ctx);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.px(24), 0, Ui.px(30), 0);
        b.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, 1), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_PILL, Ui.ACCENT, 1)));
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_ui_back);
        icon.setImageTintList(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        icon.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26));
        ilp.rightMargin = Ui.px(12);
        b.addView(icon, ilp);
        TextView label = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        label.setText(R.string.back);
        label.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        label.setDuplicateParentStateEnabled(true);
        b.addView(label);
        b.setContentDescription(ctx.getString(R.string.back));
        Focus.make(b, "back", KEY_BACK, true, v -> shell.pop());
        Ui.scaleOnFocus(b, 1.04f);
        return b;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
