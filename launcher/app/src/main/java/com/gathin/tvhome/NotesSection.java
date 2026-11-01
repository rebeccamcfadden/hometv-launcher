package com.gathin.tvhome;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Launcher settings' Phone notes (design_preview/README.md, Phone notes; src/screens/panes/notes.ts; no Figma design
 * yet): the On / Off switch, and while on the TV's address and pairing code to type into each phone's "Send to TV"
 * Shortcut, New code (asks first), How to set up a phone, and each list's last send with the lines not matched
 * exactly, each a button that opens {@link NotePicker}. Signed out, an amber note says the lists are on the TMDB account. The row reads Off, On, or in amber
 * "3 not matched". The state is {@link Notes}'s; this only draws it, and draws again when it changes.
 */
final class NotesSection implements SettingsSection {
    /** The setup QR's side, in px at 1080p: smaller than the sign-in QR, as it sits beside the address and code. */
    private static final int QR = 220;

    private static final String GROUP = "notes";

    private final Router router;
    private final Runnable listener = this::notesChanged;
    private Notes notes;
    private Context ctx;
    private Host host;
    private ViewGroup root;
    private LinearLayout column;
    private View first;

    NotesSection(Router router) {
        this.router = router;
    }

    @Override
    public String id() {
        return "notes";
    }

    @Override
    public int label() {
        return R.string.settings_notes;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_phone;
    }

    @Override
    public CharSequence value(Context ctx) {
        Notes n = Notes.get(ctx);
        String text = NoteWords.value(n.on(), n.unmatched(), ImportPill.texts(ctx));
        if (!NoteWords.valueWarns(n.on(), n.unmatched())) return text;
        SpannableString s = new SpannableString(text);
        s.setSpan(new ForegroundColorSpan(StateUi.amber()), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    @Override
    public View pane(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        if (notes != null) notes.removeListener(listener);
        notes = Notes.get(ctx);
        notes.addListener(listener);
        column = PaneParts.column(ctx);
        root = PaneParts.scroller(ctx, column);
        render();
        return root;
    }

    @Override
    public View firstFocus() {
        return first;
    }

    @Override
    public void onClose() {
        if (notes != null) notes.removeListener(listener);
    }

    private void notesChanged() {
        if (host != null) host.valueChanged();
        if (root != null && root.isAttachedToWindow()) render();
    }

    /** Builds the pane for the switch's state; focus stays where it was if it was in the pane. */
    private void render() {
        View focused = host.shell().focused();
        boolean inPane = focused != null && Focus.isInside(root, focused);
        String focusKey = Focus.key(focused);
        column.removeAllViews();
        boolean on = notes.on();

        column.addView(PaneParts.eyebrow(ctx, ctx.getString(R.string.settings_notes)));
        column.addView(PaneParts.title(ctx, ctx.getString(R.string.notes_pane_title), 40), PaneParts.below(10));
        column.addView(PaneParts.sub(ctx, ctx.getString(R.string.notes_pane_sub)), PaneParts.below(8));
        CharSequence[] options = {ctx.getString(R.string.notes_on), ctx.getString(R.string.notes_off)};
        int selected = on ? 0 : 1;
        View seg = PaneParts.segmented(ctx, options, selected, false, "notes-on", true, i -> notes.setOn(i == 0));
        column.addView(seg, PaneParts.below(28));
        first = Focus.byKey(seg, "notes-on-" + selected);

        if (!on) {
            column.addView(PaneParts.note(ctx, ctx.getString(R.string.notes_off_note), false), PaneParts.wide(22));
        } else {
            if (!router.data().accounts.signedIn()) column.addView(amber(ctx.getString(R.string.notes_signed_out)),
                    PaneParts.wide(22));
            inbox();
            sends();
        }

        if (inPane) {
            View target = Focus.byKey(column, focusKey);
            if (target == null) target = first;
            if (target != null) host.shell().focus(target);
            else host.shell().refocus();
        }
    }

    /** The address and code to type into a phone, and what to do with them; or the QR that opens both on a phone. */
    private void inbox() {
        String ip = Notes.localAddress(ctx);
        String address = NoteWords.address(ip, NoteInbox.DEFAULT_PORT);
        LinearLayout kvs = PaneParts.column(ctx);
        kvs.addView(PaneParts.keyValue(ctx, ctx.getString(R.string.notes_address),
                address != null ? address : ctx.getString(R.string.notes_no_wifi)), PaneParts.wide(0));
        View code = PaneParts.keyValue(ctx, ctx.getString(R.string.notes_code), notes.code());
        TextView value = valueOf(code);
        if (value != null) {
            value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(30));
            value.setLetterSpacing(0.12f);
        }
        kvs.addView(code, PaneParts.wide(0));
        String setup = NoteWords.setupUrl(ip, NoteInbox.DEFAULT_PORT, notes.code());
        if (setup == null) {
            column.addView(kvs, PaneParts.wide(18));
        } else {
            // The QR beside the address and code: scanned with a phone's camera, it opens the setup page (notes_setup.html).
            LinearLayout row = PaneParts.across(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(kvs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout qrBox = PaneParts.column(ctx);
            qrBox.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView qr = new ImageView(ctx);
            qr.setImageBitmap(AccountSection.qrBitmap(Qr.encode(setup), Ui.px(QR)));
            qr.setContentDescription(ctx.getString(R.string.notes_qr_description));
            qr.setBackground(Ui.round(0xFFFFFFFF, Ui.RADIUS_M));
            qr.setClipToOutline(true);
            qrBox.addView(qr, new LinearLayout.LayoutParams(Ui.px(QR), Ui.px(QR)));
            TextView caption = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
            caption.setText(R.string.notes_qr_caption);
            caption.setGravity(Gravity.CENTER_HORIZONTAL);
            caption.setMaxWidth(Ui.px(QR + 40));
            caption.setSingleLine(false);
            qrBox.addView(caption, PaneParts.below(10));
            row.addView(qrBox, PaneParts.after(32));
            column.addView(row, PaneParts.wide(18));
        }
        if (notes.portTaken()) {
            column.addView(amber(ctx.getString(R.string.notes_port_taken, NoteInbox.DEFAULT_PORT)), PaneParts.wide(16));
        }
        column.addView(PaneParts.note(ctx, ctx.getString(R.string.notes_plain_http), true), PaneParts.wide(16));
        View again = button(R.string.notes_new_code, R.drawable.ic_ui_retry, "notes-code", v ->
                host.shell().push(PaneParts.confirm(ctx, host.shell(), ctx.getString(R.string.notes_new_code_title),
                        ctx.getString(R.string.notes_new_code_body), ctx.getString(R.string.notes_new_code),
                        ctx.getString(R.string.notes_cancel), notes::newCode)));
        View help = button(R.string.notes_help, R.drawable.ic_ui_phone, "notes-help",
                v -> host.shell().toast(ctx.getString(R.string.notes_help_toast)));
        column.addView(PaneParts.actions(ctx, Arrays.asList(again, help)), PaneParts.below(28));
    }

    /** Last sends: one row per list, newest first, with the lines that matched nothing; then notes still waiting. */
    private void sends() {
        column.addView(PaneParts.title(ctx, ctx.getString(R.string.notes_last_sends), Ui.T_H3), PaneParts.below(40));
        List<NoteBook.Send> sends = notes.sends();
        if (sends.isEmpty()) {
            column.addView(PaneParts.sub(ctx, ctx.getString(R.string.notes_nothing_sent)), PaneParts.below(12));
        } else {
            LinearLayout rows = PaneParts.column(ctx);
            for (NoteBook.Send s : sends) rows.addView(sendRow(s), PaneParts.wide(rows.getChildCount() == 0 ? 0 : 14));
            column.addView(rows, PaneParts.wide(12));
        }
        List<NoteBook.Pending> waiting = notes.pending();
        if (!waiting.isEmpty()) {
            String names = null;
            for (NoteBook.Pending p : waiting) {
                names = names == null ? p.list : ctx.getString(R.string.library_lists_comma, names, p.list);
            }
            column.addView(PaneParts.note(ctx, ctx.getString(R.string.notes_waiting, names), false),
                    PaneParts.wide(16));
        }
    }

    /** .notes-send: the list, when, lines and added, then amber "3 not matched" and those lines, or a green tick. */
    private View sendRow(NoteBook.Send s) {
        LinearLayout box = PaneParts.column(ctx);
        LinearLayout head = PaneParts.across(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_1, Ui.MEDIUM);
        name.setText(s.list);
        head.addView(name);
        TextView what = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
        what.setText(ctx.getString(R.string.notes_send_line, when(s.at),
                ctx.getResources().getQuantityString(R.plurals.notes_lines, s.lines, s.lines), s.added));
        head.addView(what, PaneParts.after(16));
        if (s.unmatched.isEmpty()) {
            head.addView(Ui.icon(ctx, R.drawable.ic_ui_check, 20, Ui.GOOD), afterIcon());
        } else {
            TextView miss = Ui.line(ctx, Ui.T_SMALL, StateUi.amber(), Ui.REGULAR);
            miss.setText(ctx.getString(R.string.notes_value_unmatched, s.unmatched.size()));
            head.addView(miss, PaneParts.after(16));
        }
        box.addView(head, PaneParts.wide(0));
        // Each line not matched exactly is a button: OK opens the picker with TMDB's best answers (NotePicker). Being
        // focusable, they also let the pane scroll down to Last sends.
        for (int i = 0; i < s.asks.size(); i++) {
            NoteMatch.Ask ask = s.asks.get(i);
            // One with no answers saved is looked up when opened (NotePicker).
            String label = ctx.getString(R.string.notes_ask_choose, ask.query);
            View b = Kit.button(ctx, label, 0, Kit.SMALL, false, GROUP,
                    "notes-ask-" + NoteLines.key(s.list) + "-" + ask.key, false,
                    v -> host.shell().push(NotePicker.create(ctx, host.shell(), notes, s.list, ask)));
            TextView text = (TextView) b.getTag(R.id.kit_label);
            if (text != null) {
                text.setMaxWidth(Ui.px(1000));
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            box.addView(b, PaneParts.below(i == 0 ? 12 : 8));
        }
        return box;
    }

    // ---- Pieces -----------------------------------------------------------------------------------------------------

    private View button(int label, int icon, String key, View.OnClickListener onOk) {
        return Kit.button(ctx, ctx.getString(label), icon, Kit.NORMAL, false, GROUP, key, false, onOk);
    }

    private TextView amber(CharSequence s) {
        TextView t = PaneParts.note(ctx, s, false);
        t.setTextColor(StateUi.amber());
        t.setMaxWidth(Ui.px(1000));
        return t;
    }

    private static LinearLayout.LayoutParams afterIcon() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20));
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.leftMargin = Ui.px(16);
        return lp;
    }

    /** The value of a {@link PaneParts#keyValue} row, to restyle. */
    private static TextView valueOf(View kv) {
        if (!(kv instanceof ViewGroup)) return null;
        View row = ((ViewGroup) kv).getChildAt(0);
        if (!(row instanceof ViewGroup) || ((ViewGroup) row).getChildCount() < 2) return null;
        View v = ((ViewGroup) row).getChildAt(1);
        return v instanceof TextView ? (TextView) v : null;
    }

    /** "Today 19:40", "Yesterday 21:05", "On 28 Oct". */
    private String when(long at) {
        String s = StateUi.saved(ctx, at);
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }
}
