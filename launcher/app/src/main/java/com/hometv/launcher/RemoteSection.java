package com.hometv.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher settings › Phone remote (F27; Figma "Phone remote (F27)", pane option A; design_preview
 * src/screens/panes/remote.ts): off by default. Off, what the remote does and the On / Off switch. On, the QR beside
 * three steps (scan; at the certificate warning tap Advanced, or Show Details on an iPhone, then continue; name the
 * phone), the address and code to type instead, then the paired phones (the connected one in green) with Forget each,
 * New code and Forget all (Cancel focused: it also replaces the code, the key and the certificate). The state is
 * {@link PhoneRemote}'s; this only draws it, and draws again when it changes.
 */
final class RemoteSection implements SettingsSection {
    private static final int QR = 380;
    private static final String GROUP = "remote";

    private final Runnable listener = this::remoteChanged;
    private PhoneRemote remote;
    private Context ctx;
    private Host host;
    private ViewGroup root;
    private LinearLayout column;
    private View first;

    @Override
    public String id() {
        return "remote";
    }

    @Override
    public int label() {
        return R.string.remote_section;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_phone;
    }

    @Override
    public CharSequence value(Context ctx) {
        PhoneRemote r = PhoneRemote.get(ctx);
        if (!r.on()) return ctx.getString(R.string.remote_off);
        int n = r.phones().size();
        return n == 0 ? ctx.getString(R.string.remote_on) : ctx.getResources().getQuantityString(R.plurals.remote_phones, n, n);
    }

    @Override
    public View pane(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        if (remote != null) remote.removeListener(listener);
        remote = PhoneRemote.get(ctx);
        remote.addListener(listener);
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
        if (remote != null) remote.removeListener(listener);
    }

    private void remoteChanged() {
        if (host != null) host.valueChanged();
        if (root != null && root.isAttachedToWindow()) render();
    }

    /** Builds the pane for the switch's state; focus stays where it was if it was in the pane. */
    private void render() {
        View focused = host.shell().focused();
        boolean inPane = focused != null && Focus.isInside(root, focused);
        String focusKey = Focus.key(focused);
        column.removeAllViews();
        boolean on = remote.on();

        column.addView(PaneParts.eyebrow(ctx, ctx.getString(R.string.settings_features)));
        column.addView(PaneParts.title(ctx, ctx.getString(R.string.remote_section), 40), PaneParts.below(10));
        column.addView(PaneParts.sub(ctx, ctx.getString(R.string.remote_pane_sub)), PaneParts.below(8));
        CharSequence[] options = {ctx.getString(R.string.remote_on), ctx.getString(R.string.remote_off)};
        int selected = on ? 0 : 1;
        View seg = PaneParts.segmented(ctx, options, selected, false, "remote-on", true, i -> remote.setOn(i == 0));
        column.addView(seg, PaneParts.below(28));
        first = Focus.byKey(seg, "remote-on-" + selected);

        if (!on) off();
        else on();

        if (inPane) {
            View target = Focus.byKey(column, focusKey);
            if (target == null) target = first;
            if (target != null) host.shell().focus(target);
            else host.shell().refocus();
        }
    }

    /** Off: what it does, and that the TV doesn't listen. */
    private void off() {
        LinearLayout points = PaneParts.column(ctx);
        int[][] lines = {
                {R.drawable.ic_ui_tv, R.string.remote_what_screens},
                {R.drawable.ic_ui_plus, R.string.remote_what_my_list},
                {R.drawable.ic_ui_phone, R.string.remote_what_pair},
                {R.drawable.ic_ui_lock, R.string.remote_what_private},
        };
        for (int[] l : lines) points.addView(point(l[0], l[1]), PaneParts.wide(points.getChildCount() == 0 ? 0 : 22));
        column.addView(points, PaneParts.wide(36));
        column.addView(PaneParts.note(ctx, ctx.getString(R.string.remote_off_note), false), PaneParts.wide(36));
    }

    /** On: pairing, then the paired phones and what can be done with them. */
    private void on() {
        String ip = Notes.localAddress(ctx);
        String address = ip == null ? null : "https://" + ip + ":" + remote.port() + "/remote";
        column.addView(rule(), ruleAt(32));

        LinearLayout pair = PaneParts.across(ctx);
        if (address != null) {
            ImageView qr = new ImageView(ctx);
            qr.setImageBitmap(AccountSection.qrBitmap(Qr.encode(address + "#" + NoteInbox.normalize(remote.code())),
                    Ui.px(QR)));
            qr.setContentDescription(ctx.getString(R.string.remote_qr_description));
            qr.setBackground(Ui.round(0xFFFFFFFF, Ui.RADIUS_M));
            qr.setClipToOutline(true);
            pair.addView(qr, new LinearLayout.LayoutParams(Ui.px(QR), Ui.px(QR)));
        }
        LinearLayout words = PaneParts.column(ctx);
        words.addView(PaneParts.title(ctx, ctx.getString(R.string.remote_pair_title), Ui.T_H3));
        int[] steps = {R.string.remote_step_scan, R.string.remote_step_warning, R.string.remote_step_name};
        for (int i = 0; i < steps.length; i++) words.addView(step(i + 1, steps[i], i == 0), PaneParts.wide(i == 0 ? 18 : 14));
        TextView cap = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        cap.setText(R.string.remote_type_instead);
        words.addView(cap, PaneParts.below(28));
        TextView url = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        url.setText(address != null ? address : ctx.getString(R.string.notes_no_wifi));
        words.addView(url, PaneParts.below(6));
        TextView code = Ui.text(ctx, Ui.T_H1, Ui.TEXT_1, Ui.display(Ui.REGULAR));
        code.setText(remote.code());
        code.setLetterSpacing(0.1f);
        words.addView(code, PaneParts.below(4));
        pair.addView(words, address != null ? weighted(48) : weighted(0));
        column.addView(pair, PaneParts.wide(0));

        if (remote.portTaken()) column.addView(amber(ctx.getString(R.string.remote_port_taken, RemoteServer.DEFAULT_PORT,
                RemoteServer.DEFAULT_PORT + RemoteServer.PORTS_TRIED - 1)),
                PaneParts.wide(16));
        if (remote.failed()) column.addView(amber(ctx.getString(R.string.remote_failed)), PaneParts.wide(16));

        column.addView(rule(), ruleAt(32));
        LinearLayout head = PaneParts.across(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(PaneParts.title(ctx, ctx.getString(R.string.remote_phones), Ui.T_H3));
        if (remote.connected() != null) {
            TextView live = Ui.line(ctx, Ui.T_SMALL, Ui.GOOD, Ui.MEDIUM);
            live.setText(R.string.remote_connected_line);
            live.setPadding(Ui.px(16), Ui.px(6), Ui.px(16), Ui.px(6));
            live.setBackground(Ui.round(Ui.GOOD_SOFT, Ui.RADIUS_PILL));
            head.addView(live, PaneParts.after(20));
        }
        column.addView(head, PaneParts.wide(0));

        List<PhoneRemote.Paired> phones = remote.phones();
        if (phones.isEmpty()) {
            column.addView(PaneParts.sub(ctx, ctx.getString(R.string.remote_none)), PaneParts.below(12));
        } else {
            LinearLayout rows = PaneParts.column(ctx);
            for (PhoneRemote.Paired p : phones) rows.addView(phoneRow(p), PaneParts.wide(rows.getChildCount() == 0 ? 0 : 10));
            column.addView(rows, PaneParts.wide(18));
        }

        List<View> actions = new ArrayList<>();
        actions.add(Kit.button(ctx, ctx.getString(R.string.notes_new_code), R.drawable.ic_ui_retry, Kit.NORMAL, false,
                GROUP, "remote-code", false, v -> host.shell().push(PaneParts.confirm(ctx, host.shell(),
                        ctx.getString(R.string.remote_new_code_title), ctx.getString(R.string.remote_new_code_body),
                        ctx.getString(R.string.notes_new_code), ctx.getString(R.string.notes_cancel), remote::newCode))));
        if (!phones.isEmpty()) {
            actions.add(Kit.button(ctx, ctx.getString(R.string.remote_forget_all), 0, Kit.NORMAL, false, GROUP,
                    "remote-forget-all", false, v -> host.shell().push(PaneParts.confirmCancelFirst(ctx, host.shell(),
                            ctx.getString(R.string.remote_forget_all_title), ctx.getString(R.string.remote_forget_all_body),
                            ctx.getString(R.string.remote_forget_all_yes), ctx.getString(R.string.notes_cancel),
                            remote::forgetAll))));
        }
        column.addView(PaneParts.actions(ctx, actions), PaneParts.below(28));
        column.addView(PaneParts.note(ctx, ctx.getString(R.string.remote_warning_note), true), PaneParts.wide(16));
    }

    /** A paired phone: its name, Connected now (green) or when it was last used, and Forget (asks first). */
    private View phoneRow(PhoneRemote.Paired p) {
        boolean live = remote.isConnected(p);
        LinearLayout row = PaneParts.across(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(22), Ui.px(12), Ui.px(14), Ui.px(12));
        row.setBackground(Ui.round(Ui.PANEL, Ui.RADIUS_L));
        ImageView ico = Ui.icon(ctx, R.drawable.ic_ui_phone, 26, Ui.TEXT_1);
        ico.setBackground(Ui.oval(Ui.SURFACE_2));
        int pad = Ui.px(11);
        ico.setPadding(pad, pad, pad, pad);
        row.addView(ico, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(48)));
        LinearLayout text = PaneParts.column(ctx);
        TextView name = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        name.setText(p.name);
        text.addView(name);
        TextView sub = Ui.line(ctx, Ui.T_MIN, live ? Ui.GOOD : Ui.TEXT_3, Ui.REGULAR);
        sub.setText(live ? ctx.getString(R.string.remote_connected_now) : lastUsed(p.lastUsed));
        text.addView(sub, PaneParts.below(4));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.px(18);
        row.addView(text, tlp);
        View forget = Kit.button(ctx, ctx.getString(R.string.remote_forget), 0, Kit.SMALL, false, GROUP,
                "remote-forget-" + p.id, false, v -> host.shell().push(PaneParts.confirm(ctx, host.shell(),
                        ctx.getString(R.string.remote_forget_title, p.name), ctx.getString(R.string.remote_forget_body),
                        ctx.getString(R.string.remote_forget), ctx.getString(R.string.notes_cancel),
                        () -> remote.forget(p.id))));
        row.addView(forget);
        return row;
    }

    /** "Used today", "Last used yesterday", "Last used 3 days ago". */
    private String lastUsed(long at) {
        long days = (System.currentTimeMillis() - at) / 86_400_000L;
        if (days <= 0) return ctx.getString(R.string.remote_used_today);
        if (days == 1) return ctx.getString(R.string.remote_used_yesterday);
        return ctx.getResources().getQuantityString(R.plurals.remote_used_days, (int) Math.min(days, 999), (int) Math.min(days, 999));
    }

    // ---- Pieces -----------------------------------------------------------------------------------------------------

    /** A numbered step, the current one with a light badge (Account's steps). */
    private View step(int n, int text, boolean now) {
        LinearLayout row = PaneParts.across(ctx);
        TextView badge = Ui.line(ctx, 19, now ? Ui.ON_ACCENT : Ui.TEXT_1, Ui.MEDIUM);
        badge.setText(String.valueOf(n));
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.oval(now ? Ui.ACTIVE : Ui.SURFACE_2));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.px(38), Ui.px(38)));
        TextView t = Ui.text(ctx, 23, Ui.TEXT_1, Ui.REGULAR);
        t.setText(text);
        t.setLineSpacing(0f, 1.4f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.px(16);
        row.addView(t, lp);
        return row;
    }

    /** A line of what the remote does, with its icon in a round badge (Account's benefits). */
    private View point(int icon, int text) {
        LinearLayout row = PaneParts.across(ctx);
        ImageView ico = Ui.icon(ctx, icon, 22, Ui.TEXT_1);
        ico.setBackground(Ui.oval(Ui.SURFACE_2));
        int pad = Ui.px(9);
        ico.setPadding(pad, pad, pad, pad);
        row.addView(ico, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        TextView t = Ui.text(ctx, 23, Ui.TEXT_1, Ui.REGULAR);
        t.setText(text);
        t.setLineSpacing(0f, 1.4f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.px(16);
        row.addView(t, lp);
        return row;
    }

    private View rule() {
        View v = new View(ctx);
        v.setBackgroundColor(Ui.LINE);
        return v;
    }

    /** A rule's place: full width, one pixel high, with room above and below. */
    private static LinearLayout.LayoutParams ruleAt(int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1)));
        lp.topMargin = Ui.px(gap);
        lp.bottomMargin = Ui.px(gap - 4);
        return lp;
    }

    private static LinearLayout.LayoutParams weighted(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.px(left);
        return lp;
    }

    private TextView amber(CharSequence s) {
        TextView t = PaneParts.note(ctx, s, false);
        t.setTextColor(StateUi.amber());
        t.setMaxWidth(Ui.px(1000));
        return t;
    }
}
