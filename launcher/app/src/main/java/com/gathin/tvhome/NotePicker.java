package com.gathin.tvhome;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phone notes' picker (the project lead, 2026-11-01; no Figma design yet, see design_preview/README.md, Differences
 * from Figma): for a line of a list's last send that TMDB didn't match exactly, TMDB's best three answers as posters
 * with their title and year, and None of these. OK on a poster puts that title on the list (on Seen, for a ticked
 * line); None of these stops asking about the line; Back leaves it in Last sends for later. A line with no answers
 * saved (an older TV kept only its text) is looked up first. Search again (the project lead, F9) opens the line's text
 * with Search's keyboard ({@link KeyPad}) to fix and look up again: "Dead poet society" finds only a band, "Dead poets"
 * the film. Drawn as the confirm dialog is ({@link PaneParts#confirm}).
 */
final class NotePicker {
    private NotePicker() {}

    private static final int POSTER_W = 150, POSTER_H = 225, CARD_PAD = 12;
    /** The confirm dialog's width, so the line and what a pick does wrap rather than run off. */
    private static final int WIDTH = 860;
    private static final String GROUP = "note-pick";

    private enum Mode { LOOKING, OFFLINE, READY }

    static Screen create(Context ctx, Shell shell, Notes notes, String list, NoteMatch.Ask ask) {
        if (ask.looked) return build(ctx, shell, notes, list, ask, Mode.READY, null);
        return look(ctx, shell, notes, list, ask, ask.query, null);
    }

    /** "Looking on TMDB…" while {@code query} is looked up for the line, then the answers (or Couldn't reach TMDB). */
    private static Screen look(Context ctx, Shell shell, Notes notes, String list, NoteMatch.Ask ask, String query,
                               String searched) {
        // No answers while waiting, so OK can't pick one from the search being replaced; offline, they come back.
        Screen looking = build(ctx, shell, notes, list, ask.unanswered(), Mode.LOOKING, searched);
        notes.lookUp(list, ask, query, found -> {
            // Only if the household is still waiting on this one.
            if (shell.top() != looking) return;
            shell.pop();
            shell.push(build(ctx, shell, notes, list, found != null ? found : ask,
                    found != null ? Mode.READY : Mode.OFFLINE, searched));
        });
        return looking;
    }

    /** {@code searched}: the text the household typed to look the line up again, or null for the line itself. */
    private static Screen build(Context ctx, Shell shell, Notes notes, String list, NoteMatch.Ask ask, Mode mode,
                                String searched) {
        FrameLayout root = new FrameLayout(ctx);
        View scrim = new View(ctx);
        scrim.setBackgroundColor(Ui.SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout box = PaneParts.column(ctx);
        box.setPadding(Ui.px(56), Ui.px(52), Ui.px(56), Ui.px(52));
        box.setBackground(Ui.round(Ui.BG_RAISED, Ui.RADIUS_XL, Ui.LINE_STRONG, Ui.LINE_W));
        box.setElevation(Ui.px(40));

        String title = ctx.getString(mode == Mode.LOOKING ? R.string.notes_pick_looking
                : mode == Mode.OFFLINE ? R.string.notes_pick_title_offline
                : ask.top.isEmpty() ? R.string.notes_pick_title_none : R.string.notes_pick_title);
        TextView t = Ui.text(ctx, 38, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        box.addView(t);
        TextView b = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        b.setText(mode == Mode.READY ? body(ctx, list, ask, searched)
                : ctx.getString(R.string.notes_pick_line, ask.query, list) + (mode == Mode.OFFLINE
                ? " " + ctx.getString(R.string.notes_pick_offline) : ""));
        b.setLineSpacing(0f, 1.45f);
        box.addView(b, PaneParts.wide(16));

        List<View> cards = new ArrayList<>();
        if (!ask.top.isEmpty()) {
            LinearLayout row = Kit.unclip(PaneParts.across(ctx));
            row.setGravity(Gravity.TOP);
            for (int i = 0; i < ask.top.size(); i++) {
                NoteMatch.Candidate c = ask.top.get(i);
                View card = card(ctx, c, i, v -> {
                    shell.pop();
                    notes.answer(list, ask, c.id);
                });
                cards.add(card);
                row.addView(card, i == 0 ? PaneParts.after(0) : PaneParts.after(24));
            }
            box.addView(row, PaneParts.below(32));
        }
        int noneLabel = ask.top.isEmpty() ? R.string.notes_pick_stop : R.string.notes_pick_none;
        View none = Kit.button(ctx, ctx.getString(noneLabel), 0, Kit.NORMAL, false, GROUP + "-actions",
                GROUP + "-none", cards.isEmpty(), v -> {
                    shell.pop();
                    notes.answer(list, ask, null);
                });
        List<View> actions = new ArrayList<>();
        if (mode != Mode.LOOKING) {
            actions.add(Kit.button(ctx, ctx.getString(R.string.notes_pick_edit), R.drawable.ic_ui_search, Kit.NORMAL,
                    false, GROUP + "-actions", GROUP + "-edit", false, v -> {
                        shell.pop();
                        shell.push(editor(ctx, shell, notes, list, ask, searched));
                    }));
        }
        actions.add(none);
        box.addView(PaneParts.actions(ctx, actions), PaneParts.below(36));
        root.addView(box, new FrameLayout.LayoutParams(Ui.px(WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        View first = cards.isEmpty() ? none : cards.get(0);
        return new Screen(title, root, Screen.Layer.DIALOG) {
            @Override
            View defaultFocus() {
                return first;
            }
        };
    }

    /**
     * Search again: the line's text (or what was last searched) in a bar over Search's keyboard, then Search, which
     * looks it up and shows its answers to pick from for the same line, or Cancel and Back, which go back to the
     * answers as they were, with what was last searched.
     */
    private static Screen editor(Context ctx, Shell shell, Notes notes, String list, NoteMatch.Ask ask,
                                 String searched) {
        String start = searched != null ? searched : ask.query;
        FrameLayout root = new FrameLayout(ctx);
        View scrim = new View(ctx);
        scrim.setBackgroundColor(Ui.SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout box = PaneParts.column(ctx);
        box.setPadding(Ui.px(56), Ui.px(52), Ui.px(56), Ui.px(52));
        box.setBackground(Ui.round(Ui.BG_RAISED, Ui.RADIUS_XL, Ui.LINE_STRONG, Ui.LINE_W));
        box.setElevation(Ui.px(40));
        String title = ctx.getString(R.string.notes_pick_edit_title);
        TextView t = Ui.text(ctx, 38, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        box.addView(t);
        TextView b = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        b.setText(ctx.getString(R.string.notes_pick_edit_body, ask.query, list));
        b.setLineSpacing(0f, 1.45f);
        box.addView(b, PaneParts.wide(12));

        // {@code .qbar}, as Weather's: the search icon and what is typed, or a hint while it is empty.
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        bar.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, Ui.LINE_W));
        bar.addView(Ui.icon(ctx, R.drawable.ic_ui_search, 28, Ui.TEXT_2), new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
        TextView typed = Ui.line(ctx, 28, Ui.TEXT_1, Ui.MEDIUM);
        typed.setEllipsize(TextUtils.TruncateAt.START);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.px(14);
        bar.addView(typed, tlp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(70));
        blp.topMargin = Ui.px(24);
        box.addView(bar, blp);

        String[] query = {SearchQuery.clean(start)};
        Runnable paint = () -> {
            boolean empty = query[0].trim().isEmpty();
            typed.setText(empty ? ctx.getString(R.string.notes_pick_edit_hint) : query[0]);
            typed.setTextColor(empty ? Ui.TEXT_3 : Ui.TEXT_1);
        };
        paint.run();
        Runnable back = () -> {
            shell.pop();
            shell.push(build(ctx, shell, notes, list, ask, Mode.READY, searched));
        };
        Runnable search = () -> {
            String q = query[0].trim();
            if (q.isEmpty()) return;
            shell.pop();
            shell.push(look(ctx, shell, notes, list, ask, q, q));
        };

        LinearLayout row = Kit.unclip(PaneParts.across(ctx));
        row.setGravity(Gravity.TOP);
        LinearLayout keys = KeyPad.panel(ctx, GROUP + "-kb", GROUP + "-k-", change -> {
            query[0] = change.apply(query[0]);
            paint.run();
        });
        row.addView(keys, PaneParts.after(0));
        LinearLayout buttons = PaneParts.column(ctx);
        buttons.addView(Kit.button(ctx, ctx.getString(R.string.notes_pick_search), R.drawable.ic_ui_search,
                Kit.PRIMARY, false, GROUP + "-edit-actions", GROUP + "-search", false, v -> search.run()),
                PaneParts.below(0));
        buttons.addView(Kit.button(ctx, ctx.getString(R.string.notes_cancel), 0, Kit.NORMAL, false,
                GROUP + "-edit-actions", GROUP + "-cancel", false, v -> back.run()), PaneParts.below(16));
        row.addView(buttons, PaneParts.after(32));
        box.addView(row, PaneParts.below(24));

        root.addView(box, new FrameLayout.LayoutParams(Ui.px(WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        View first = Focus.byKey(keys, GROUP + "-k-a");
        return new Screen(title, root, Screen.Layer.DIALOG) {
            @Override
            View defaultFocus() {
                return first;
            }

            /** A keyboard or a remote with keys types into the bar, as on Search. */
            @Override
            boolean onText(char c) {
                if (!SearchQuery.isTypeable(c)) return false;
                query[0] = SearchQuery.type(query[0], c);
                paint.run();
                return true;
            }

            /** Back goes back to the answers, not out of the picker. */
            @Override
            boolean onKey(Key key, View focused) {
                if (key != Key.BACK) return false;
                back.run();
                return true;
            }
        };
    }

    /** "“Inglorious basterds” in Rebecca’s note matched nothing exactly." and what a pick does. */
    private static CharSequence body(Context ctx, String list, NoteMatch.Ask ask, String searched) {
        String line = searched != null ? ctx.getString(R.string.notes_pick_results, searched)
                : ctx.getString(R.string.notes_pick_line, ask.query, list);
        if (ask.top.isEmpty()) return line + " " + ctx.getString(R.string.notes_pick_nothing);
        return line + " " + ctx.getString(ask.ticked ? R.string.notes_pick_to_seen : R.string.notes_pick_to_list, list);
    }

    /** A poster with its title and "2009 · Movie" under it; the accent ring and scale-up when focused. */
    private static View card(Context ctx, NoteMatch.Candidate c, int i, View.OnClickListener onOk) {
        LinearLayout card = PaneParts.column(ctx);
        int pad = Ui.px(CARD_PAD);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_M, Ui.LINE, Ui.LINE_W),
                Ui.round(Ui.SURFACE, Ui.RADIUS_M, Ui.LINE, Ui.LINE_W),
                Ui.round(Ui.ACCENT_SOFT, Ui.RADIUS_M, Ui.ACCENT, 4)));
        ImageView poster = new ImageView(ctx);
        poster.setBackground(Ui.round(Ui.SURFACE_3, Ui.RADIUS_S));
        poster.setClipToOutline(true);
        card.addView(poster, new LinearLayout.LayoutParams(Ui.px(POSTER_W), Ui.px(POSTER_H)));
        Art.photo(poster, c.poster, Ui.px(POSTER_W), Ui.px(POSTER_H));
        TextView name = Ui.text(ctx, Ui.T_SMALL, Ui.TEXT_1, Ui.MEDIUM);
        name.setText(c.title);
        // Two lines' room for every title, so the cards line up.
        name.setMinLines(2);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(Ui.px(POSTER_W));
        card.addView(name, PaneParts.below(12));
        TextView meta = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        String kind = ctx.getString(c.id.startsWith("tv:") ? R.string.notes_pick_show : R.string.notes_pick_movie);
        meta.setText(c.year > 0 ? ctx.getString(R.string.notes_pick_meta, c.year, kind) : kind);
        card.addView(meta, PaneParts.below(4));
        card.setContentDescription(c.year > 0 ? c.title + ", " + c.year : c.title);
        Focus.make(card, GROUP, GROUP + "-" + i, i == 0, onOk);
        Ui.scaleOnFocus(card, 1.05f);
        return card;
    }
}
