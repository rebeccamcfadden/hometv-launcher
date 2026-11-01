package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The import pill (design_preview/README.md, Import pill; src/ui/importPill.ts): while a phone's note is looked up, a
 * small pill at the bottom centre of whatever launcher screen shows, between the guide line's ends and inside
 * title-safe: an accent dot, "Importing Rebecca · 6 of 12" and a thin bar. Launcher settings' pane runs to the bottom
 * edge, so there it sits at the top centre, on the title's line, between the title and the hint. At the end, for a few seconds, "Partner ·
 * 8 added" with a tick, or in amber "Rebecca · 9 added · 3 not matched" or "Rebecca · waiting for the internet". The
 * offline pill's shape (StateUi); amber only for a problem. Never focusable, never in the way: it sits over the stage
 * and takes no keys. Motion Reduced or Off drops its slide. What it says is {@link Notes#pill}'s; colours are read from
 * {@link Ui} each time it is drawn, so a theme restyles it.
 */
@SuppressLint("ViewConstructor")
final class ImportPill extends LinearLayout {
    /** screens.css .import-pill: 56 px from the bottom, which keeps it inside title-safe. */
    private static final float BOTTOM = 56;
    /** On Launcher settings: centred on its title's line (y 80, the 44 px title from SAFE_Y), its own ~46 px tall. */
    private static final float TOP = 57;
    /** The text's widest: 1100 px at the bottom; at the top, what fits between the title and the hint. */
    private static final float TEXT_MAX = 1100, TEXT_MAX_TOP = 760;
    private static final float BAR_W = 120, BAR_H = 4;

    private final Notes notes;
    private final Runnable listener = this::render;
    private final ImageView icon;
    private final View dot;
    private final TextView text;
    private final FrameLayout bar;
    private final View fill;
    private NoteWords.Kind shownKind;
    private boolean shown;
    private boolean top;

    ImportPill(Context ctx, Notes notes) {
        super(ctx);
        this.notes = notes;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.px(22), Ui.px(10), Ui.px(22), Ui.px(10));
        setFocusable(false);
        setClickable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        setAlpha(0f);
        setVisibility(GONE);

        dot = new View(ctx);
        addView(dot, new LayoutParams(Ui.px(10), Ui.px(10)));
        icon = new ImageView(ctx);
        addView(icon, new LayoutParams(Ui.px(22), Ui.px(22)));
        text = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_1, Ui.REGULAR);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Ui.px(TEXT_MAX));
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.px(12);
        addView(text, tlp);
        bar = new FrameLayout(ctx);
        bar.setClipToOutline(true);
        fill = new View(ctx);
        fill.setPivotX(0f);
        bar.addView(fill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LayoutParams blp = new LayoutParams(Ui.px(BAR_W), Math.max(2, Ui.px(BAR_H)));
        blp.leftMargin = Ui.px(12);
        addView(bar, blp);
    }

    /** Where the pill sits in the activity's root, over the stage: bottom centre, or top centre on Launcher settings. */
    static FrameLayout.LayoutParams place(boolean top) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL);
        if (top) lp.topMargin = Ui.px(TOP);
        else lp.bottomMargin = Ui.px(BOTTOM);
        return lp;
    }

    /** The page showing wants the pill at the top (Shell.pillAtTop), or not. Main thread. */
    void atTop(boolean top) {
        if (top == this.top) return;
        this.top = top;
        text.setMaxWidth(Ui.px(top ? TEXT_MAX_TOP : TEXT_MAX));
        setLayoutParams(place(top));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        notes.addPillListener(listener);
        render();
    }

    @Override
    protected void onDetachedFromWindow() {
        notes.removePillListener(listener);
        super.onDetachedFromWindow();
    }

    private void render() {
        NoteWords.Pill p = notes.pill();
        if (p == null) {
            hide();
            return;
        }
        if (p.kind != shownKind) restyle(p.kind);
        String said = NoteWords.pill(p, texts(getContext()));
        text.setText(said);
        setContentDescription(said);
        fill.setScaleX(p.fraction());
        show();
    }

    /** The colours and pieces for a kind of message, from the theme's tokens now. */
    private void restyle(NoteWords.Kind kind) {
        shownKind = kind;
        boolean warn = kind.warn;
        int color = warn ? StateUi.amber() : Ui.TEXT_1;
        setBackground(warn ? Ui.round(StateUi.amberPill(), Ui.RADIUS_PILL, StateUi.amberLine(), Ui.LINE_W)
                : Ui.round(Ui.TOAST_BG, Ui.RADIUS_PILL, Ui.ACCENT_SOFT, Ui.LINE_W));
        text.setTextColor(color);
        boolean importing = kind == NoteWords.Kind.IMPORTING;
        dot.setVisibility(importing ? VISIBLE : GONE);
        dot.setBackground(Ui.oval(Ui.ACCENT));
        bar.setVisibility(importing ? VISIBLE : GONE);
        bar.setBackground(Ui.round(Ui.SURFACE_3, BAR_H / 2));
        fill.setBackground(Ui.round(Ui.ACCENT, BAR_H / 2));
        icon.setVisibility(importing ? GONE : VISIBLE);
        if (!importing) {
            icon.setImageResource(warn ? R.drawable.ic_state_alert : R.drawable.ic_ui_check);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    private void show() {
        if (shown) return;
        shown = true;
        setVisibility(VISIBLE);
        animate().cancel();
        boolean still = Ui.reducedMotion();
        setTranslationY(still ? 0f : slide());
        animate().alpha(1f).translationY(0f).setDuration(Ui.ms(Ui.FAST_MS)).setListener(null).start();
    }

    private void hide() {
        if (!shown) return;
        shown = false;
        shownKind = null;
        animate().cancel();
        boolean still = Ui.reducedMotion();
        animate().alpha(0f).translationY(still ? 0f : slide()).setDuration(Ui.ms(Ui.FAST_MS))
                .withEndAction(() -> {
                    if (!shown) setVisibility(GONE);
                }).start();
    }

    /** It slides in from the nearer edge: up from below, or down from above. */
    private float slide() {
        return top ? -Ui.px(12) : Ui.px(12);
    }

    /** The pill's and the section's sentences, from strings_notes.xml. */
    static NoteWords.Texts texts(Context ctx) {
        return new NoteWords.Texts(ctx.getString(R.string.notes_pill_importing),
                ctx.getString(R.string.notes_pill_importing_count), ctx.getString(R.string.notes_pill_added),
                ctx.getString(R.string.notes_pill_added_unmatched), ctx.getString(R.string.notes_pill_offline),
                ctx.getString(R.string.notes_pill_failed), ctx.getString(R.string.notes_pill_signed_out),
                ctx.getString(R.string.notes_value_on), ctx.getString(R.string.notes_value_off),
                ctx.getString(R.string.notes_value_unmatched));
    }
}
