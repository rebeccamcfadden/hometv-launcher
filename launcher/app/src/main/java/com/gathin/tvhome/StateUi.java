package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.function.Consumer;

/**
 * The pieces of the loading, offline and error states (V2 design 06-states A; design_preview/README.md, Loading,
 * offline and errors; the .sk, .offline-pill, .state and .inline-err styles in src/styles/screens.css): the amber
 * offline pill, static skeleton blocks and cards (no shimmer, light on a 1 GB TV, never focusable), the "offline,
 * nothing saved" block, and the error box a failing preview tab shows. Which state a screen is in is
 * {@link CatalogState}'s to say. Kept out of Ui and Kit so the settings and theme work on those can go on alongside.
 */
final class StateUi {
    private StateUi() {}

    /** Offline and errors use the score star's amber (tokens.css --star), read from Ui when a view is built. */
    static int amber() {
        return Ui.STAR;
    }
    /** .offline-pill: the amber at 12%. */
    static final int AMBER_PILL = 0x1FF2C46D;
    /** .inline-err: the amber at 8%, with a 30% border. */
    static final int AMBER_BOX = 0x14F2C46D;
    static final int AMBER_LINE = 0x4DF2C46D;
    /** .sk: white at 7%; .sk-art (a card's art) at 6% with the 9% line inside. */
    static final int SKELETON = 0x12FFFFFF;
    static final int SKELETON_ART = 0x0FFFFFFF;

    // ---- The network -----------------------------------------------------------------------------------------------

    /** The nav's model, which says whether Android reaches the internet; the activity is the nav's host. */
    static NavModel navModel(Context ctx) {
        return ctx instanceof NavBar.Host ? ((NavBar.Host) ctx).navModel() : null;
    }

    /** Whether the internet is reachable now, as the nav last heard (true when there is no nav to ask). */
    static boolean online(Context ctx) {
        NavModel m = navModel(ctx);
        return m == null || m.internet;
    }

    /**
     * Calls onChange with whether the internet is reachable each time that changes, while {@code owner} is on
     * screen, and once more when it comes back on screen if it changed meanwhile. The model holds its listeners
     * strongly, so a page that is pushed and popped stops listening when it leaves the window, as the nav does.
     */
    static void watchInternet(View owner, NavModel model, Consumer<Boolean> onChange) {
        if (model == null) return;
        boolean[] last = {model.internet};
        NavModel.Listener l = m -> {
            if (m.internet == last[0]) return;
            last[0] = m.internet;
            onChange.accept(m.internet);
        };
        owner.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                model.listen(l);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                model.unlisten(l);
            }
        });
        if (owner.isAttachedToWindow()) model.listen(l);
    }

    /** Wi-Fi settings (with the vendor fallback), or a toast where the TV has none. */
    static void openWifiSettings(Context ctx, Consumer<CharSequence> toast) {
        Intent i = SystemSettings.intent(ctx, Settings.ACTION_WIFI_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (RuntimeException e) {
            toast.accept(ctx.getString(R.string.not_available));
        }
    }

    // ---- Words -----------------------------------------------------------------------------------------------------

    /** "today 19:52", "yesterday 19:52" or "on 28 Oct", in the TV's zone and clock style; null for 0. */
    static String saved(Context ctx, long savedAt) {
        StateWords.When w = when(ctx, savedAt);
        if (w == null) return null;
        switch (w.day) {
            case TODAY:
                return ctx.getString(R.string.states_saved_today, w.text);
            case YESTERDAY:
                return ctx.getString(R.string.states_saved_yesterday, w.text);
            default:
                return ctx.getString(R.string.states_saved_earlier, w.text);
        }
    }

    /** As {@link #saved}, but today's copy is just its time: the weather's "updated 19:52". */
    static String updated(Context ctx, long savedAt) {
        StateWords.When w = when(ctx, savedAt);
        if (w == null) return null;
        return w.day == StateWords.Day.TODAY ? w.text : saved(ctx, savedAt);
    }

    private static StateWords.When when(Context ctx, long savedAt) {
        return StateWords.when(savedAt, System.currentTimeMillis(), ZoneId.systemDefault(),
                DateFormat.is24HourFormat(ctx));
    }

    // ---- Pieces ----------------------------------------------------------------------------------------------------

    /** .offline-pill: the crossed-out Wi-Fi (24) and the text (20 px) in amber, on the amber at 12%. */
    static LinearLayout pill(Context ctx, CharSequence text) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.px(18), Ui.px(8), Ui.px(18), Ui.px(8));
        pill.setBackground(Ui.round(AMBER_PILL, Ui.RADIUS_PILL));
        pill.addView(Ui.icon(ctx, R.drawable.ic_ui_wifi_off, 24, amber()), new LinearLayout.LayoutParams(Ui.px(24),
                Ui.px(24)));
        TextView t = Ui.line(ctx, Ui.T_SMALL, amber(), Ui.REGULAR);
        t.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(10);
        pill.addView(t, lp);
        pill.setContentDescription(text);
        return pill;
    }

    /** Changes a {@link #pill}'s text. */
    static void setPill(View pill, CharSequence text) {
        ((TextView) ((ViewGroup) pill).getChildAt(1)).setText(text);
        pill.setContentDescription(text);
    }

    /**
     * A Kit button showing only its icon, as a circle of its own height, while what its label would say isn't known
     * (the preview's Watch before the title's availability is in); {@code blank} false brings its label and padding
     * back. {@code said} is what accessibility services hear meanwhile. It stays focusable.
     */
    static void blank(View button, boolean blank, CharSequence said) {
        View label = (View) button.getTag(R.id.kit_label);
        if (label == null) return;
        int[] padding = (int[]) button.getTag(R.id.states_padding);
        if (blank && padding == null) {
            button.setTag(R.id.states_padding, new int[]{button.getPaddingLeft(), button.getPaddingTop(),
                    button.getPaddingRight(), button.getPaddingBottom()});
            label.setVisibility(View.GONE);
            button.setPadding(0, 0, 0, 0);
            button.setMinimumWidth(button.getMinimumHeight());
            ((LinearLayout) button).setGravity(Gravity.CENTER);
        } else if (!blank && padding != null) {
            button.setTag(R.id.states_padding, null);
            label.setVisibility(View.VISIBLE);
            button.setPadding(padding[0], padding[1], padding[2], padding[3]);
            button.setMinimumWidth(0);
            ((LinearLayout) button).setGravity(Gravity.CENTER_VERTICAL);
        }
        if (blank) button.setContentDescription(said);
    }

    /** .sk: a static grey block, radius 8, w × h design px. */
    static View sk(Context ctx, float w, float h) {
        return sk(ctx, w, h, Ui.RADIUS_S);
    }

    static View sk(Context ctx, float w, float h, float radius) {
        View v = new View(ctx);
        v.setBackground(Ui.round(SKELETON, radius));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(w), Ui.px(h)));
        return v;
    }

    /** A block with its top margin, for a column. */
    private static View sk(Context ctx, float w, float h, float radius, float top) {
        View v = sk(ctx, w, h, radius);
        ((LinearLayout.LayoutParams) v.getLayoutParams()).topMargin = Ui.px(top);
        return v;
    }

    /** .card-art.sk-art: the card's art box at 6% with the line inside, keeping the card's radius. */
    private static View artSkeleton(Context ctx, float w, float h, float radius) {
        View v = new View(ctx);
        v.setBackground(Ui.round(SKELETON_ART, radius, Ui.LINE, 1));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(w), Ui.px(h)));
        return v;
    }

    /** widgets.ts libraryCardSkeleton: a library card's shape while it loads (417, art 172), not focusable. */
    static View libraryCardSkeleton(Context ctx) {
        LinearLayout card = column(ctx);
        card.addView(artSkeleton(ctx, 417, 172, Ui.RADIUS_L));
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(sk(ctx, 190, 26));
        row.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(sk(ctx, 90, 20));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.px(12);
        card.addView(row, rlp);
        card.addView(sk(ctx, 250, 18, Ui.RADIUS_S, 10));
        card.addView(sk(ctx, 150, 18, Ui.RADIUS_S, 8));
        return card;
    }

    /** widgets.ts smallCardSkeleton: a small card's shape (271, art 152), for More like this. */
    static View smallCardSkeleton(Context ctx) {
        LinearLayout card = column(ctx);
        card.addView(artSkeleton(ctx, 271, 152, 14));
        card.addView(sk(ctx, 190, 24, Ui.RADIUS_S, 12));
        card.addView(sk(ctx, 130, 18, Ui.RADIUS_S, 8));
        card.addView(sk(ctx, 210, 18, Ui.RADIUS_S, 6));
        card.setLayoutParams(new ViewGroup.LayoutParams(Ui.px(271), ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    /** preview.ts SKELETONS, Where to watch: a service card's shape (376 wide, at least 140 tall). */
    static View serviceSkeleton(Context ctx) {
        LinearLayout card = column(ctx);
        card.setPadding(Ui.px(24), Ui.px(22), Ui.px(24), Ui.px(22));
        card.setMinimumHeight(Ui.px(140));
        card.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L));
        card.addView(sk(ctx, 180, 28));
        card.addView(sk(ctx, 270, 22, Ui.RADIUS_S, 16));
        card.addView(sk(ctx, 70, 18, Ui.RADIUS_S, 12));
        card.setLayoutParams(new ViewGroup.LayoutParams(Ui.px(376), ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    /** Cast &amp; crew: a person's shape (176 wide, a 104 px round portrait, the name and role). */
    static View personSkeleton(Context ctx) {
        LinearLayout card = column(ctx);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        View portrait = new View(ctx);
        portrait.setBackground(Ui.oval(SKELETON));
        card.addView(portrait, new LinearLayout.LayoutParams(Ui.px(104), Ui.px(104)));
        card.addView(sk(ctx, 120, 20, Ui.RADIUS_S, 14));
        card.addView(sk(ctx, 90, 18, Ui.RADIUS_S, 8));
        card.setLayoutParams(new ViewGroup.LayoutParams(Ui.px(176), ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    /** Episodes: an episode card's shape (300 × 196, on the surface). */
    static View episodeSkeleton(Context ctx) {
        LinearLayout card = column(ctx);
        card.setPadding(Ui.px(20), Ui.px(20), Ui.px(20), Ui.px(20));
        card.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L));
        card.addView(sk(ctx, 40, 18));
        card.addView(sk(ctx, 220, 23, Ui.RADIUS_S, 14));
        card.addView(sk(ctx, 90, 18, Ui.RADIUS_S, 14));
        card.setLayoutParams(new ViewGroup.LayoutParams(Ui.px(300), Ui.px(196)));
        return card;
    }

    /** Overview: four lines of synopsis (820 wide) and three facts beside them. */
    static View overviewSkeleton(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout text = column(ctx);
        float[] lines = {800, 760, 790, 520};
        for (int i = 0; i < lines.length; i++) text.addView(sk(ctx, lines[i], 22, Ui.RADIUS_S, i == 0 ? 0 : 16));
        row.addView(text, new LinearLayout.LayoutParams(Ui.px(820), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout facts = column(ctx);
        float[] widths = {420, 300, 340};
        for (int i = 0; i < widths.length; i++) facts.addView(sk(ctx, widths[i], 22, Ui.RADIUS_S, i == 0 ? 0 : 18));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.leftMargin = Ui.px(64);
        row.addView(facts, flp);
        row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** .pv-art--loading: the plain gradient that holds the art's place while a preview loads. */
    static GradientDrawable artGradient() {
        // 160deg in CSS runs from the top left towards the bottom right, a little steeper than the diagonal.
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF16202C, 0xFF0F161F, 0xFF0F161F});
        return g;
    }

    /**
     * .state: the crossed-out Wi-Fi in a 96 px circle, the title (30 px), the note (22 px, at most 1000 wide) and the
     * actions in a row 16 apart. Returns the block; {@link #actions} finds the row.
     */
    static LinearLayout offlineBlock(Context ctx, CharSequence title, CharSequence note, View... actions) {
        LinearLayout box = Kit.unclip(new LinearLayout(ctx));
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.START);
        box.setPadding(0, Ui.px(16), 0, 0);
        FrameLayout circle = new FrameLayout(ctx);
        circle.setBackground(Ui.oval(Ui.SURFACE_2));
        circle.addView(Ui.icon(ctx, R.drawable.ic_ui_wifi_off, 48, Ui.TEXT_2),
                new FrameLayout.LayoutParams(Ui.px(48), Ui.px(48), Gravity.CENTER));
        box.addView(circle, new LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)));
        TextView t = Ui.line(ctx, Ui.T_H2, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        LinearLayout.LayoutParams tlp = wrap();
        // .state's 16 px gap, and the title's own 8 px.
        tlp.topMargin = Ui.px(24);
        box.addView(t, tlp);
        if (note != null) {
            TextView n = Ui.text(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
            n.setText(note);
            n.setMaxWidth(Ui.px(1000));
            LinearLayout.LayoutParams nlp = wrap();
            nlp.topMargin = Ui.px(16);
            box.addView(n, nlp);
        }
        if (actions.length > 0) {
            LinearLayout row = Kit.unclip(new LinearLayout(ctx));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setTag(R.id.states_actions, Boolean.TRUE);
            for (View a : actions) {
                LinearLayout.LayoutParams lp = wrap();
                lp.leftMargin = row.getChildCount() == 0 ? 0 : Ui.px(16);
                row.addView(a, lp);
            }
            LinearLayout.LayoutParams rlp = wrap();
            // .state-actions: the 16 px gap and its own 14.
            rlp.topMargin = Ui.px(30);
            box.addView(row, rlp);
        }
        return box;
    }

    /** The actions row of an {@link #offlineBlock} that v sits in, or null. */
    static View actions(View v) {
        for (View cur = v; cur != null; cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) {
            if (Boolean.TRUE.equals(cur.getTag(R.id.states_actions))) return cur;
        }
        return null;
    }

    /**
     * .inline-err: 1100 wide, the amber at 8% with a 30% border, radius 16; the warning sign (40) in amber, the
     * title (26 px) and the note (20 px, tertiary), and the retry button on the right.
     */
    static View errorBox(Context ctx, CharSequence title, CharSequence note, View retry) {
        LinearLayout box = Kit.unclip(new LinearLayout(ctx));
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.px(28), Ui.px(24), Ui.px(28), Ui.px(24));
        box.setBackground(Ui.round(AMBER_BOX, Ui.RADIUS_L, AMBER_LINE, 1));
        ImageView icon = Ui.icon(ctx, R.drawable.ic_state_alert, 40, amber());
        box.addView(icon, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        text.addView(t);
        TextView n = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        n.setText(note);
        LinearLayout.LayoutParams nlp = wrap();
        nlp.topMargin = Ui.px(4);
        text.addView(n, nlp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.px(24);
        box.addView(text, tlp);
        LinearLayout.LayoutParams blp = wrap();
        blp.leftMargin = Ui.px(24);
        box.addView(retry, blp);
        box.setLayoutParams(new FrameLayout.LayoutParams(Ui.px(1100), ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static LinearLayout column(Context ctx) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
