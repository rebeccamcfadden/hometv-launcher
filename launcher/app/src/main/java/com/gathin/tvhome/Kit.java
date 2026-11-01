package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The shared UI kit the screens are built from, ported from design_preview/src/ui/widgets.ts with the sizes in
 * src/styles/base.css (all in design px): buttons, chips, the library, small and person cards, rows that run off
 * the screen's edge, the bottom guide line and fade. Every item is made focusable with {@link Focus#make} and has a
 * visible focused state: an accent fill for buttons and chips, a 4 px accent ring on card art and portraits, with
 * the prototype's scale-up.
 *
 * <p>A card's ring and scale-up draw outside the card, so the views that hold cards must not clip their children:
 * {@link #row} sees to it for its items; a grid uses {@link #unclip} on its containers.
 */
final class Kit {
    private Kit() {}

    /** Button styles; combine with |. */
    static final int NORMAL = 0, PRIMARY = 1, SMALL = 2, ICON = 4;

    /** Room a ScrollBox keeps around its items for the focus ring and scale-up (base.css .row-items padding). */
    static final float RING_ROOM = 12;

    // ---- Buttons --------------------------------------------------------------------------------------------------

    /**
     * {@code .btn}: a 66 px pill on the surface fill, 24 px label, optional 26 px icon. PRIMARY is the accent-soft fill
     * with an accent border and label, semibold; SMALL is 56 px with a 20 px label and 22 px icon; ICON is a 66 px
     * circle showing only its icon until focused, when it widens to show its label. {@code on} is a toggle's on state
     * (accent-soft fill, accent label). Focused: accent fill, dark label and icon, scale 1.04.
     */
    static View button(Context ctx, CharSequence label, int iconRes, int style, boolean on, String group, String key,
                       boolean isDefault, View.OnClickListener onOk) {
        boolean primary = (style & PRIMARY) != 0;
        boolean small = (style & SMALL) != 0;
        boolean iconOnly = (style & ICON) != 0;
        float height = small ? 56 : 66;
        LinearLayout b = new LinearLayout(ctx);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(iconOnly ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        b.setMinimumHeight(Ui.px(height));
        b.setContentDescription(label);
        int fill = primary ? Ui.ACCENT_SOFT : Ui.SURFACE;
        int border = primary ? Ui.ACCENT : Ui.LINE;
        b.setBackground(Ui.states(Ui.round(fill, Ui.RADIUS_PILL, border, Ui.LINE_W),
                Ui.round(Ui.ACCENT_SOFT, Ui.RADIUS_PILL, border, Ui.LINE_W),
                Ui.round(Ui.ACCENT, Ui.RADIUS_PILL, Ui.ACCENT, Ui.LINE_W)));
        ColorStateList color = Ui.colors(primary ? Ui.ACCENT : Ui.TEXT_1, Ui.ACCENT, Ui.ON_ACCENT);

        ImageView icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageTintList(color);
        icon.setDuplicateParentStateEnabled(true);
        int iconSize = Ui.px(small ? 22 : 26);
        b.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        if (iconRes != 0) icon.setImageResource(iconRes);
        else icon.setVisibility(View.GONE);

        TextView text = Ui.line(ctx, small ? Ui.T_SMALL : Ui.T_BODY, Ui.TEXT_1, primary ? Ui.MEDIUM : Ui.REGULAR);
        text.setText(label);
        text.setTextColor(color);
        text.setDuplicateParentStateEnabled(true);
        b.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        b.setTag(R.id.kit_icon, icon);
        b.setTag(R.id.kit_label, text);
        b.setTag(R.id.kit_icon_only, iconOnly);
        if (iconOnly) {
            b.setMinimumWidth(Ui.px(height));
            iconOnlyLayout(b, false);
            b.setOnFocusChangeListener((v, focused) -> iconOnlyLayout((LinearLayout) v, focused));
        } else {
            int left = Ui.px(small ? 18 : 24), right = Ui.px(small ? 22 : 30);
            b.setPadding(left, 0, right, 0);
            gap(b, Ui.px(12));
        }
        b.setActivated(on);
        if (on) b.setAccessibilityDelegate(TOGGLE_A11Y);
        Focus.make(b, group, key, isDefault, onOk);
        Ui.scaleOnFocus(b, 1.04f);
        return b;
    }

    /** An ICON button shows its label only while focused, and widens for it (base.css .btn--icon.is-focused). */
    private static void iconOnlyLayout(LinearLayout b, boolean focused) {
        TextView text = (TextView) b.getTag(R.id.kit_label);
        text.setVisibility(focused ? View.VISIBLE : View.GONE);
        if (focused) b.setPadding(Ui.px(20), 0, Ui.px(28), 0);
        else b.setPadding(0, 0, 0, 0);
        gap(b, focused ? Ui.px(10) : 0);
    }

    /** The space between a button's icon and label, when both show. */
    private static void gap(LinearLayout b, int gap) {
        ImageView icon = (ImageView) b.getTag(R.id.kit_icon);
        TextView text = (TextView) b.getTag(R.id.kit_label);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) text.getLayoutParams();
        lp.leftMargin = icon.getVisibility() == View.VISIBLE && text.getVisibility() == View.VISIBLE ? gap : 0;
        text.setLayoutParams(lp);
    }

    /** A toggle button's on state (My list, Seen, Favorite), also told to accessibility services as checked. */
    static void setOn(View button, boolean on) {
        button.setActivated(on);
        button.setAccessibilityDelegate(TOGGLE_A11Y);
    }

    // Chips and toggles keep their state in "activated", which a LinearLayout doesn't report to TalkBack.
    private static final View.AccessibilityDelegate TOGGLE_A11Y = new View.AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setCheckable(true);
            info.setChecked(host.isActivated());
        }
    };

    private static final View.AccessibilityDelegate CHIP_A11Y = new View.AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setSelected(host.isActivated());
        }
    };

    /**
     * Tells accessibility services that v is selected while it is activated: a chip, the title preview's current tab,
     * the nav's current section.
     */
    static void reportsSelected(View v) {
        v.setAccessibilityDelegate(CHIP_A11Y);
    }

    /** Changes a button's label, e.g. "My list" to "On My list". */
    static void setLabel(View button, CharSequence label) {
        TextView text = (TextView) button.getTag(R.id.kit_label);
        if (text != null) text.setText(label);
        button.setContentDescription(label);
    }

    /** Changes a button's icon, e.g. the outline heart to the filled one; 0 removes it. */
    static void setIcon(View button, int iconRes) {
        ImageView icon = (ImageView) button.getTag(R.id.kit_icon);
        if (icon == null) return;
        if (iconRes != 0) icon.setImageResource(iconRes);
        icon.setVisibility(iconRes != 0 ? View.VISIBLE : View.GONE);
        LinearLayout b = (LinearLayout) button;
        if (Boolean.TRUE.equals(button.getTag(R.id.kit_icon_only))) iconOnlyLayout(b, button.isFocused());
        else gap(b, Ui.px(12));
    }

    // ---- Chips ----------------------------------------------------------------------------------------------------

    /**
     * A row of chips that scrolls sideways with focus when they don't fit ({@code .chips} with data-scroll x): the
     * row in a horizontal ScrollBox that keeps the focused chip {@code revealPad} design px inside its edges and,
     * back on the first chip, returns to its start, so a label before it shows again. The chips are clipped
     * {@link #RING_ROOM} outside the row, room for a focused chip's scale-up; add the strip with
     * {@link #chipStripParams}, whose margins give that room back, so the chips sit where the bare row would.
     */
    static View chipStrip(Context ctx, LinearLayout chips, float revealPad) {
        FrameLayout clip = new FrameLayout(ctx);
        clip.setClipChildren(true);
        ViewGroup box = ScrollBox.horizontalFromStart(ctx, Math.max(RING_ROOM, revealPad));
        int room = Ui.px(RING_ROOM);
        box.setPadding(room, room, room, room);
        box.addView(chips, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        clip.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return clip;
    }

    /** Full width, {@code top} design px below the view before it, for a {@link #chipStrip} in a column. */
    static LinearLayout.LayoutParams chipStripParams(float top) {
        int room = Ui.px(RING_ROOM);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(-room, Ui.px(top) - room, -room, -room);
        return lp;
    }

    /**
     * {@code .chip}: a 54 px pill with a 20 px label. Selected is the accent at 14% with an accent border and a
     * semibold accent label, with a check before it when {@code check}; focused is the accent fill, scale 1.05.
     */
    static View chip(Context ctx, CharSequence label, boolean selected, boolean check, String group, String key,
                     View.OnClickListener onOk) {
        return chip(ctx, label, selected, check, group, key, false, onOk);
    }

    /** {@link #chip} that a group picks first when entered (isDefault). */
    static View chip(Context ctx, CharSequence label, boolean selected, boolean check, String group, String key,
                     boolean isDefault, View.OnClickListener onOk) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER);
        c.setMinimumHeight(Ui.px(54));
        c.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        c.setContentDescription(label);
        c.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, Ui.LINE_W),
                Ui.round(Ui.ACCENT_SOFT, Ui.RADIUS_PILL, Ui.ACCENT, Ui.LINE_W),
                Ui.round(Ui.ACCENT, Ui.RADIUS_PILL, Ui.ACCENT, Ui.LINE_W)));
        ColorStateList color = Ui.colors(Ui.TEXT_2, Ui.ACCENT, Ui.ON_ACCENT);
        ImageView tick = null;
        if (check) {
            tick = new ImageView(ctx);
            tick.setImageResource(R.drawable.ic_ui_check);
            tick.setImageTintList(color);
            tick.setDuplicateParentStateEnabled(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20));
            lp.rightMargin = Ui.px(8);
            c.addView(tick, lp);
        }
        TextView text = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
        text.setText(label);
        text.setTextColor(color);
        text.setDuplicateParentStateEnabled(true);
        c.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        c.setTag(R.id.kit_label, text);
        c.setTag(R.id.kit_check, tick);
        setSelected(c, selected);
        reportsSelected(c);
        Focus.make(c, group, key, isDefault, onOk);
        Ui.scaleOnFocus(c, 1.05f);
        return c;
    }

    static void setSelected(View chip, boolean selected) {
        chip.setActivated(selected);
        TextView text = (TextView) chip.getTag(R.id.kit_label);
        if (text != null) text.setTypeface(selected ? Ui.MEDIUM : Ui.REGULAR);
        View tick = (View) chip.getTag(R.id.kit_check);
        if (tick != null) tick.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    // ---- Cards ----------------------------------------------------------------------------------------------------

    /**
     * The library card from Movies and TV Shows ({@code .card--library}): 417 wide, art 172 tall with radius 16; the
     * name (25 px, medium) with the provider and "Included" or a price right-aligned (18 px), from the household's
     * services and stores only ({@link Data#cardOffer}; blank when none has it); the meta line and the
     * provenance line. Focused: the 4 px accent ring on the art and the art scaled 1.03; the text stays as it is.
     */
    static View libraryCard(Context ctx, Title t, Data data, String group, String key, boolean isDefault,
                            View.OnClickListener onOk) {
        LinearLayout card = card(ctx, 417);
        RingFrame art = art(ctx, t, 417, 172, Ui.RADIUS_L);
        card.addView(art, new LinearLayout.LayoutParams(Ui.px(417), Ui.px(172)));

        Offer o = data.cardOffer(t);
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = Ui.line(ctx, 25, Ui.TEXT_1, Ui.MEDIUM);
        name.setText(t.name);
        name.setGravity(Gravity.TOP | Gravity.START);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout provider = new LinearLayout(ctx);
        provider.setOrientation(LinearLayout.VERTICAL);
        provider.setGravity(Gravity.END);
        TextView service = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_2, Ui.REGULAR);
        service.setText(o == null ? "" : PriceCards.isPaid(o) ? data.table.storeName(o.service)
                : data.table.name(o.service));
        provider.addView(service);
        TextView avail = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        avail.setText(Offers.availability(o, data.services.subscribed()));
        provider.addView(avail);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = Ui.px(12);
        top.addView(provider, plp);
        card.addView(top, below(10));

        card.addView(textLine(ctx, TitleText.meta(t), Ui.TEXT_2), below(4));
        TextView lists = textLine(ctx, TitleText.provenance(t, data.household), Ui.TEXT_3);
        card.addView(lists, below(2));
        card.setTag(R.id.kit_provenance, lists);
        return titleCard(focusCard(card, art, 1.03f, group, key, isDefault, onOk, t.name), t);
    }

    /**
     * The smaller card of Search and the title preview's rows ({@code .card--small}): 271 wide, art 152 with radius
     * 14, the name (22 px) or {@code nameOverride} (Search's highlighted match, see {@link #highlight}), "Movie · 2016"
     * and the card line ({@link Data#offerLine}, the household's services and stores only). Focused like the library
     * card.
     */
    static View smallCard(Context ctx, Title t, CharSequence nameOverride, Data data, String group, String key,
                          boolean isDefault, View.OnClickListener onOk) {
        LinearLayout card = card(ctx, 271);
        RingFrame art = art(ctx, t, 271, 152, 14);
        card.addView(art, new LinearLayout.LayoutParams(Ui.px(271), Ui.px(152)));
        TextView name = Ui.line(ctx, 22, Ui.TEXT_1, Ui.MEDIUM);
        name.setText(nameOverride != null ? nameOverride : t.name);
        name.setGravity(Gravity.START);
        card.addView(name, below(12));
        card.addView(textLine(ctx, TitleText.kindYear(t), Ui.TEXT_2), below(4));
        card.addView(textLine(ctx, data.offerLine(t), Ui.TEXT_3), below(2));
        return titleCard(focusCard(card, art, 1.03f, group, key, isDefault, onOk, t.name), t);
    }

    /** Marks v as a card for t, so holding OK on it opens the title menu (TitleMenu). Returns v. */
    static View titleCard(View v, Title t) {
        v.setTag(R.id.kit_title, t);
        return v;
    }

    /**
     * Brings a title card's list line ("My list · Sci-fi") up to date in place, after the title menu put the title on a
     * list or took it off; cards without one (Search's) are left as they are.
     */
    static void refreshTitleCard(View card, Data data) {
        Title t = titleOf(card);
        Object line = card == null ? null : card.getTag(R.id.kit_provenance);
        if (t != null && line instanceof TextView) ((TextView) line).setText(TitleText.provenance(t, data.household));
    }

    /** The title a card was made for ({@link #titleCard}), or null. */
    static Title titleOf(View v) {
        Object t = v == null ? null : v.getTag(R.id.kit_title);
        return t instanceof Title ? (Title) t : null;
    }

    /**
     * A person in Cast &amp; crew ({@code .person}): 176 wide, a 104 px round portrait with initials (32 px), and
     * their photo over them once it has loaded, the name (20 px) and the role (18 px). Focused: the ring on the
     * portrait, scale 1.06.
     */
    static View personCard(Context ctx, Person p, String group, String key, boolean isDefault,
                           View.OnClickListener onOk) {
        LinearLayout card = card(ctx, 176);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        RingFrame portrait = portrait(ctx, p.name, p.photo, 104, 32);
        card.addView(portrait, new LinearLayout.LayoutParams(Ui.px(104), Ui.px(104)));
        TextView n = Ui.text(ctx, Ui.T_SMALL, Ui.TEXT_1, Ui.MEDIUM);
        n.setText(p.name);
        n.setGravity(Gravity.CENTER_HORIZONTAL);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(n, below(12));
        TextView r = textLine(ctx, p.role, Ui.TEXT_3);
        r.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(r, below(2));
        return focusCard(card, portrait, 1.06f, group, key, isDefault, onOk, p.name);
    }

    /**
     * The round portrait of Cast &amp; crew, Search and the person page: initials on the portrait fill, and the TMDB
     * photo over them when there is one (Art.photo), loaded for a box {@code size} design px across.
     */
    static RingFrame portrait(Context ctx, String name, String photo, float size, float initialsSize) {
        RingFrame portrait = new RingFrame(ctx, size / 2, true, Ui.PORTRAIT);
        TextView initials = Ui.line(ctx, initialsSize, Ui.TEXT_2, Ui.MEDIUM);
        initials.setText(TitleText.initials(name));
        initials.setGravity(Gravity.CENTER);
        portrait.content().addView(initials, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (photo != null) {
            ImageView img = new ImageView(ctx);
            portrait.content().addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            Art.photo(img, photo, Ui.px(size), Ui.px(size));
        }
        return portrait;
    }

    private static LinearLayout card(Context ctx, float width) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setLayoutParams(new ViewGroup.MarginLayoutParams(Ui.px(width), ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static RingFrame art(Context ctx, Title t, float w, float h, float radius) {
        RingFrame frame = new RingFrame(ctx, radius, false, Ui.BG_RAISED);
        ImageView img = new ImageView(ctx);
        frame.content().addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        Art.into(img, t.art, Ui.px(w), Ui.px(h));
        return frame;
    }

    /** Focus on the card shows the ring on its art or portrait and scales that up; the text never changes. */
    private static View focusCard(LinearLayout card, RingFrame ring, float scale, String group, String key,
                                  boolean isDefault, View.OnClickListener onOk, CharSequence description) {
        card.setContentDescription(description);
        card.setOnFocusChangeListener((v, focused) -> {
            ring.setRing(focused);
            float s = focused ? scale : 1f;
            ring.animate().scaleX(s).scaleY(s).setDuration(Ui.ms(Ui.FAST_MS)).setInterpolator(Ui.EASE).start();
        });
        return Focus.make(card, group, key, isDefault, onOk);
    }

    private static TextView textLine(Context ctx, CharSequence s, int color) {
        TextView t = Ui.line(ctx, Ui.T_MIN, color, Ui.REGULAR);
        t.setText(s);
        t.setGravity(Gravity.START);
        return t;
    }

    private static LinearLayout.LayoutParams below(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }

    /**
     * text with the characters at {@code indices} in the accent, semibold: base.css {@code .hit}, for Search's
     * matched letters. Indices out of range are ignored.
     */
    static CharSequence highlight(String text, int[] indices) {
        SpannableString s = new SpannableString(text);
        if (indices == null) return s;
        for (int i : indices) {
            if (i < 0 || i >= text.length()) continue;
            s.setSpan(new ForegroundColorSpan(Ui.ACCENT), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(Typeface.BOLD), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    // ---- Rows and page furniture ----------------------------------------------------------------------------------

    /**
     * {@code .row}: the label (26 px) over the items in a horizontal ScrollBox, 20 px apart, running off the right
     * edge, with the row's 34 px gap below. The box keeps {@link #RING_ROOM} around the items for the focus ring
     * (and takes it back with negative margins). The row clips the box to the box's own bounds, as the
     * prototype's {@code .row-items} does with overflow hidden, so cards scrolled past the left edge don't draw
     * into the page's gutter; the views holding the row must not clip it ({@link #unclip}), since the box hangs
     * {@link #RING_ROOM} outside the row.
     */
    static View row(Context ctx, CharSequence label, List<View> items) {
        return row(ctx, label, items, RING_ROOM);
    }

    /**
     * {@link #row} whose scrolling keeps the focused card revealPad design px inside the box's edges (at least
     * {@link #RING_ROOM}). A row that runs off the screen's edge uses the safe margin, 96, so the focused card stays
     * whole and inside the title-safe area (M6, Search's rows: the prototype's data-scroll-pad 96).
     */
    static View row(Context ctx, CharSequence label, List<View> items, float revealPad) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClipChildren(true);
        row.setClipToPadding(false);
        row.setPadding(0, 0, 0, Ui.px(34));
        TextView title = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        title.setText(label);
        title.setGravity(Gravity.START);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = Ui.px(16);
        row.addView(title, tlp);

        // The reveal padding equals the box's padding, so coming back to the first card scrolls to 0 and it lines up
        // with the label again, and the last card stops with its ring inside the box.
        ViewGroup box = ScrollBox.horizontal(ctx, Math.max(RING_ROOM, revealPad));
        int room = Ui.px(RING_ROOM);
        box.setPadding(room, room, room, room);
        LinearLayout strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        for (View item : items) {
            ViewGroup.LayoutParams given = item.getLayoutParams();
            LinearLayout.LayoutParams lp = given != null ? new LinearLayout.LayoutParams(given)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = strip.getChildCount() == 0 ? 0 : Ui.px(20);
            strip.addView(item, lp);
        }
        box.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(-room, -room, -room, -room);
        row.addView(box, blp);
        return row;
    }

    /** Lets a container's children draw outside it (the cards' ring and scale-up); returns it. */
    static <T extends ViewGroup> T unclip(T g) {
        g.setClipChildren(false);
        g.setClipToPadding(false);
        return g;
    }

    private static final Object GUIDE_RIGHT = new Object();

    /** The right-hand hint of a {@link #guide}, for pages that change it as they go. */
    static TextView guideRight(View guide) {
        return guide.findViewWithTag(GUIDE_RIGHT);
    }

    /**
     * {@code .guide}: the hint line at the foot of a page, left and right (18 px, tertiary), at the title-safe
     * bottom. Laid out for a FrameLayout page (Pages.page). {@link #guideRight} finds its right-hand hint.
     */
    static View guide(Context ctx, CharSequence left, CharSequence right) {
        FrameLayout g = new FrameLayout(ctx);
        TextView l = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        l.setText(left);
        g.addView(l, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
        TextView r = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        r.setText(right);
        r.setTag(GUIDE_RIGHT);
        g.addView(r, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.leftMargin = Ui.px(Ui.SAFE_X);
        lp.rightMargin = Ui.px(Ui.SAFE_X);
        lp.bottomMargin = Ui.px(Ui.SAFE_Y);
        g.setLayoutParams(lp);
        return g;
    }

    /**
     * {@code .bottom-fade}: 200 px at the foot of a page that fades the content into the background (to 97% at 70%
     * of its height), under the guide. Laid out for a FrameLayout page.
     */
    static View bottomFade(Context ctx) {
        View v = new View(ctx);
        PaintDrawable d = new PaintDrawable();
        d.setShape(new RectShape());
        int[] colors = {Ui.alpha(Ui.BG, 0), Ui.alpha(Ui.BG, 0xF7), Ui.alpha(Ui.BG, 0xF7)};
        d.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(0, 0, 0, height, colors,
                        new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP);
            }
        });
        v.setBackground(d);
        v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(200), Gravity.BOTTOM));
        return v;
    }
}
