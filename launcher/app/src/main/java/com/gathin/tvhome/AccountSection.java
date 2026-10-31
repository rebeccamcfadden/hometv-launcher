package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Account pane of Launcher settings (design_preview/README.md, Account; V2 design r2-account A with B's Choose
 * lists; src/screens/panes/account.ts): the whole TMDB sign-in in the pane, drawn from {@link Accounts#flow()}.
 * Signed out: what signing in brings and "Sign in with your phone". Getting a code: a turning arc where the QR code
 * goes. Waiting: the QR code (486 px) beside three steps, the countdown with its bar, Cancel and New code. Approved:
 * "Signing in…". Signed in: the username, the lists' counts, Household, Friends and the last sync, Sync now, Choose
 * lists (a sub-state; Back leaves it) and Sign out, which asks first. Expired: New code. Failed: Try again and Wi-Fi
 * settings.
 *
 * <p>Replaces the temporary AccountPage; the logic stays in Accounts and SignInFlow. ← to the sections keeps a code
 * waiting (polled while the settings page is open, the row reading "Waiting…" in amber); closing the page stops it
 * ({@link #onClose}). Nothing here waits on the network: the lists for Household, Friends and Choose lists arrive
 * when TMDB answers.
 */
final class AccountSection implements SettingsSection {
    /** The QR code's side, quiet zone included (screens.css .qr-wrap): a phone reads it from the sofa. */
    private static final float QR = 486;
    private static final String GROUP = "account";

    private final Router router;
    private final Runnable listener = this::accountChanged;
    private final Runnable tick = this::tick;

    /** The account listened to: the Data's when the pane was last built. */
    private Accounts accounts;
    private Context ctx;
    private Host host;
    private ViewGroup root;
    private LinearLayout column;
    private View first;
    /** What is drawn, so a change that doesn't alter it (a poll) redraws nothing. */
    private String drawn;
    private TextView countdown;
    private FrameLayout bar;

    /** Choose lists, a sub-state of Signed in. */
    private boolean choosing;
    /** The account's TMDB lists, once read for this visit; null until then. */
    private List<TmdbAccountParse.ListInfo> lists;
    private boolean listsLoading;
    private boolean listsFailed;

    AccountSection(Router router) {
        this.router = router;
    }

    @Override
    public String id() {
        return "account";
    }

    @Override
    public int label() {
        return R.string.settings_account;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_user;
    }

    /** Not signed in, Getting a code…, Waiting…, Signing in…, Code expired (those three in amber), or the username. */
    @Override
    public CharSequence value(Context ctx) {
        Accounts a = router.data().accounts;
        AccountWords.Row row = AccountWords.row(a.flow().phase());
        String text;
        switch (row) {
            case GETTING:
                text = ctx.getString(R.string.account_row_getting);
                break;
            case WAITING:
                text = ctx.getString(R.string.account_row_waiting);
                break;
            case SIGNING_IN:
                text = ctx.getString(R.string.account_row_signing_in);
                break;
            case EXPIRED:
                text = ctx.getString(R.string.account_row_expired);
                break;
            case SIGNED_IN:
                text = a.username() != null ? a.username() : ctx.getString(R.string.account_not_signed_in);
                break;
            default:
                text = ctx.getString(R.string.account_not_signed_in);
                break;
        }
        if (!row.warn) return text;
        SpannableString s = new SpannableString(text);
        s.setSpan(new ForegroundColorSpan(Ui.STAR), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    @Override
    public View pane(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        Accounts a = router.data().accounts;
        if (a != accounts) {
            if (accounts != null) accounts.removeListener(listener);
            accounts = a;
            lists = null;
        }
        a.addListener(listener);
        column = PaneParts.column(ctx);
        root = PaneParts.scroller(ctx, column);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                tick();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                v.removeCallbacks(tick);
            }
        });
        drawn = null;
        render();
        return root;
    }

    @Override
    public View firstFocus() {
        return first;
    }

    @Override
    public boolean onBack() {
        if (!choosing) return false;
        choosing = false;
        render();
        focus("acc-lists");
        return true;
    }

    /** The page closed: stop listening, and forget a code still waiting (an approved one is let finish). */
    @Override
    public void onClose() {
        if (root != null) root.removeCallbacks(tick);
        choosing = false;
        lists = null;
        if (accounts == null) return;
        listsFailed = false;
        accounts.removeListener(listener);
        accounts.setSignInVisible(false);
        if (accounts.flow().phase() != SignInFlow.Phase.SIGNED_IN) accounts.cancelSignIn();
    }

    // ---- Changes ----------------------------------------------------------------------------------------------------

    private void accountChanged() {
        if (host != null) host.valueChanged();
        if (root != null && root.isAttachedToWindow()) render();
    }

    /** Polls and sign-in steps keep going while the page is open, wherever focus is ({@link #onClose} stops them). */
    private void signIn() {
        accounts.setSignInVisible(true);
        accounts.startSignIn();
    }

    /** Cancel: back to signed out. SignInFlow says nothing to listeners for it, so the pane and row redraw here. */
    private void cancel() {
        accounts.cancelSignIn();
        accounts.setSignInVisible(false);
        accountChanged();
    }

    /** New code while one is still waiting: that one is dropped first. */
    private void newCode() {
        if (accounts.flow().phase() == SignInFlow.Phase.WAITING) accounts.cancelSignIn();
        signIn();
    }

    /** The countdown and its bar, each second while a code waits and the pane shows. */
    private void tick() {
        if (root == null) return;
        root.removeCallbacks(tick);
        SignInFlow flow = accounts.flow();
        if (flow.phase() != SignInFlow.Phase.WAITING || countdown == null) return;
        long left = flow.remaining(System.currentTimeMillis());
        countdown.setText(AccountWords.countdown(left));
        PaneParts.setBar(bar, AccountWords.fraction(left));
        if (root.isAttachedToWindow()) root.postDelayed(tick, 1000);
    }

    /** Builds the pane for the flow's state, unless it is drawn already; focus stays in the pane if it was there. */
    private void render() {
        SignInFlow flow = accounts.flow();
        SignInFlow.Phase phase = flow.phase();
        if (phase != SignInFlow.Phase.SIGNED_IN) choosing = false;
        if (phase == SignInFlow.Phase.SIGNED_IN && lists == null && !listsLoading && !listsFailed) loadLists();
        String key = phase + "|" + accounts.username() + "|" + flow.failure() + "|" + choosing + "|"
                + flow.approveUrl() + "|" + (lists == null ? listsFailed : lists.size()) + "|" + accounts.lastSynced();
        if (key.equals(drawn)) {
            tick();
            return;
        }
        drawn = key;
        View focused = host.shell().focused();
        boolean inPane = focused != null && Focus.isInside(root, focused);
        String focusKey = Focus.key(focused);

        column.removeAllViews();
        first = null;
        countdown = null;
        bar = null;
        if (!accounts.available()) unavailable();
        else if (choosing) chooseLists();
        else {
            switch (phase) {
                case REQUESTING:
                    getting();
                    break;
                case WAITING:
                    waiting(flow);
                    break;
                case FINISHING:
                    approved();
                    break;
                case SIGNED_IN:
                    signedIn();
                    break;
                case EXPIRED:
                    expired();
                    break;
                case FAILED:
                    failed();
                    break;
                default:
                    signedOut();
                    break;
            }
        }
        tick();
        if (inPane) {
            View target = Focus.byKey(column, focusKey);
            if (target == null) target = first;
            if (target != null) host.shell().focus(target);
            else host.shell().refocus();
        }
    }

    private void focus(String key) {
        View v = Focus.byKey(column, key);
        if (v != null) host.shell().focus(v);
    }

    private void loadLists() {
        listsLoading = true;
        listsFailed = false;
        Accounts a = accounts;
        a.loadAccountLists(found -> {
            if (a != accounts) return;
            listsLoading = false;
            lists = found;
            if (root != null && root.isAttachedToWindow()) render();
        }, () -> {
            if (a != accounts) return;
            listsLoading = false;
            listsFailed = true;
            if (root != null && root.isAttachedToWindow()) render();
        });
    }

    // ---- States -----------------------------------------------------------------------------------------------------

    private void signedOut() {
        column.addView(PaneParts.eyebrow(ctx, ctx.getString(R.string.settings_account)));
        column.addView(PaneParts.title(ctx, ctx.getString(R.string.account_pane_title), 40), PaneParts.below(10));
        column.addView(PaneParts.sub(ctx, ctx.getString(R.string.account_pane_sub)), PaneParts.below(8));
        LinearLayout ben = PaneParts.column(ctx);
        ben.addView(benefit(R.drawable.ic_ui_sync, R.string.account_ben_sync, R.string.account_ben_sync_sub));
        ben.addView(benefit(R.drawable.ic_ui_list, R.string.account_ben_lists, R.string.account_ben_lists_sub),
                PaneParts.wide(22));
        ben.addView(benefit(R.drawable.ic_ui_lock, R.string.account_ben_password, R.string.account_ben_password_sub),
                PaneParts.wide(22));
        column.addView(ben, PaneParts.wide(34));
        first = button(R.string.account_sign_in_phone, R.drawable.ic_ui_phone, true, "acc-signin", v -> signIn());
        column.addView(PaneParts.actions(ctx, Arrays.asList(first)), PaneParts.below(36));
        column.addView(PaneParts.note(ctx, ctx.getString(R.string.account_signed_out_pane_note), false),
                PaneParts.wide(22));
    }

    /** A build without a TMDB token can't sign in. */
    private void unavailable() {
        column.addView(PaneParts.eyebrow(ctx, ctx.getString(R.string.settings_account)));
        column.addView(PaneParts.title(ctx, ctx.getString(R.string.account_pane_title), 40), PaneParts.below(10));
        column.addView(PaneParts.sub(ctx, ctx.getString(R.string.account_unavailable)), PaneParts.below(8));
    }

    private void getting() {
        FrameLayout hold = hold(PaneParts.dashed(Ui.SURFACE, 14, Ui.LINE_STRONG));
        LinearLayout in = holdContent();
        in.addView(new Spinner(ctx), new LinearLayout.LayoutParams(Ui.px(56), Ui.px(56)));
        in.addView(holdText(ctx.getString(R.string.account_getting_hold), Ui.TEXT_2), PaneParts.below(18));
        hold.addView(in, centred());
        LinearLayout side = side(R.string.account_pane_title);
        side.addView(steps(0), PaneParts.wide(28));
        first = button(R.string.account_cancel, 0, false, "acc-cancel", v -> cancel());
        side.addView(PaneParts.actions(ctx, Arrays.asList(first)), PaneParts.below(36));
        qrRow(hold, side);
    }

    private void waiting(SignInFlow flow) {
        ImageView qr = new ImageView(ctx);
        qr.setScaleType(ImageView.ScaleType.CENTER);
        qr.setBackground(Ui.round(Color.WHITE, 14));
        qr.setContentDescription(ctx.getString(R.string.account_qr_description));
        String url = flow.approveUrl();
        if (url != null) qr.setImageBitmap(qrBitmap(Qr.encode(url), Ui.px(QR)));
        FrameLayout box = hold(null);
        box.addView(qr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout side = side(R.string.account_pane_title);
        side.addView(steps(0), PaneParts.wide(28));
        LinearLayout count = PaneParts.column(ctx);
        LinearLayout line = PaneParts.across(ctx);
        line.setGravity(Gravity.BOTTOM);
        countdown = Ui.line(ctx, 44, Ui.TEXT_1, Ui.REGULAR);
        line.addView(countdown);
        TextView left = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        left.setText(R.string.account_left);
        LinearLayout.LayoutParams llp = PaneParts.after(12);
        llp.gravity = Gravity.BOTTOM;
        llp.bottomMargin = Ui.px(6);
        line.addView(left, llp);
        count.addView(line);
        bar = PaneParts.bar(ctx, Ui.ACCENT);
        count.addView(bar, PaneParts.wide(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.px(500), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.px(40);
        side.addView(count, clp);
        first = button(R.string.account_cancel, 0, true, "acc-cancel", v -> cancel());
        View again = button(R.string.account_new_code, R.drawable.ic_ui_retry, false, "acc-new", v -> newCode());
        side.addView(PaneParts.actions(ctx, Arrays.asList(first, again)), PaneParts.below(36));
        side.addView(PaneParts.note(ctx, ctx.getString(R.string.account_waiting_note), true), PaneParts.wide(22));
        qrRow(box, side);
    }

    private void approved() {
        FrameLayout hold = hold(Ui.round(Ui.SURFACE, 14, Ui.GOOD, 1.5f));
        LinearLayout in = holdContent();
        in.addView(PaneParts.disc(ctx, 96, Ui.GOOD_SOFT, R.drawable.ic_ui_check, 48, Ui.GOOD),
                new LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)));
        in.addView(holdText(ctx.getString(R.string.account_approved), Ui.GOOD), PaneParts.below(18));
        hold.addView(in, centred());
        LinearLayout side = side(R.string.account_signing_in_title);
        side.addView(steps(2), PaneParts.wide(28));
        side.addView(PaneParts.note(ctx, ctx.getString(R.string.account_importing), true), PaneParts.wide(40));
        qrRow(hold, side);
    }

    private void expired() {
        FrameLayout hold = hold(Ui.round(Ui.SURFACE, 14));
        LinearLayout in = holdContent();
        in.addView(PaneParts.disc(ctx, 96, Ui.SURFACE_2, R.drawable.ic_ui_clock, 48, Ui.TEXT_1),
                new LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)));
        TextView over = holdText(ctx.getString(R.string.account_expired_over), Ui.TEXT_1);
        over.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(Ui.T_H3));
        in.addView(over, PaneParts.below(16));
        hold.addView(in, centred());
        LinearLayout side = side(R.string.account_expired_title);
        side.addView(PaneParts.sub(ctx, ctx.getString(R.string.account_expired_sub)), PaneParts.below(8));
        first = button(R.string.account_new_code, R.drawable.ic_ui_retry, true, "acc-new", v -> signIn());
        View no = button(R.string.account_cancel, 0, false, "acc-cancel", v -> cancel());
        side.addView(PaneParts.actions(ctx, Arrays.asList(first, no)), PaneParts.below(36));
        qrRow(hold, side);
    }

    private void failed() {
        FrameLayout hold = hold(PaneParts.dashed(Ui.SURFACE, 14, Ui.LINE_STRONG));
        LinearLayout in = holdContent();
        in.addView(PaneParts.disc(ctx, 96, Ui.SURFACE_2, R.drawable.ic_ui_wifi_off, 48, Ui.STAR),
                new LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)));
        in.addView(holdText(ctx.getString(R.string.account_no_code), Ui.STAR), PaneParts.below(18));
        hold.addView(in, centred());
        LinearLayout side = side(R.string.account_unreachable_title);
        side.addView(PaneParts.sub(ctx, ctx.getString(R.string.account_unreachable_sub)), PaneParts.below(8));
        first = button(R.string.account_try_again, R.drawable.ic_ui_retry, true, "acc-try", v -> signIn());
        View wifi = button(R.string.account_wifi_settings, R.drawable.ic_ui_wifi, false, "acc-wifi", v -> openWifi());
        side.addView(PaneParts.actions(ctx, Arrays.asList(first, wifi)), PaneParts.below(36));
        qrRow(hold, side);
    }

    private void signedIn() {
        String name = accounts.username() == null ? "" : accounts.username();
        column.addView(PaneParts.eyebrow(ctx, ctx.getString(R.string.settings_account)));
        LinearLayout user = PaneParts.across(ctx);
        user.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout avatar = PaneParts.disc(ctx, 96, Ui.SURFACE_2, 0, 0, 0);
        TextView initial = Ui.line(ctx, 42, Ui.ACCENT, Ui.BOLD);
        initial.setText(name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.ROOT));
        avatar.addView(initial, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        user.addView(avatar, new LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)));
        LinearLayout who = PaneParts.column(ctx);
        who.addView(PaneParts.title(ctx, name, 40));
        LinearLayout good = PaneParts.across(ctx);
        good.setGravity(Gravity.CENTER_VERTICAL);
        good.addView(Ui.icon(ctx, R.drawable.ic_ui_check, 22, Ui.GOOD), new LinearLayout.LayoutParams(Ui.px(22),
                Ui.px(22)));
        TextView signed = Ui.line(ctx, Ui.T_SMALL, Ui.GOOD, Ui.REGULAR);
        signed.setText(R.string.account_signed_in_good);
        good.addView(signed, PaneParts.after(8));
        who.addView(good, PaneParts.below(6));
        user.addView(who, PaneParts.after(24));
        column.addView(user, PaneParts.wide(20));

        Map<SyncList, List<String>> mine = router.data().household.syncedLists();
        LinearLayout kvs = PaneParts.column(ctx);
        kvs.addView(PaneParts.keyValue(ctx, ctx.getString(R.string.account_kv_lists),
                ctx.getString(R.string.account_kv_lists_value, size(mine, SyncList.MY_LIST), size(mine, SyncList.SEEN),
                        size(mine, SyncList.FAVORITES))), PaneParts.wide(0));
        kvs.addView(PaneParts.keyValue(ctx, ctx.getString(R.string.account_role_household), listValue(ListId.HOUSEHOLD)),
                PaneParts.wide(0));
        kvs.addView(PaneParts.keyValue(ctx, ctx.getString(R.string.account_role_friends), listValue(ListId.FRIENDS)),
                PaneParts.wide(0));
        kvs.addView(PaneParts.keyValue(ctx, ctx.getString(R.string.account_kv_last_synced), lastSynced()),
                PaneParts.wide(0));
        column.addView(kvs, PaneParts.wide(18));

        first = button(R.string.account_sync_now, R.drawable.ic_ui_sync, false, "acc-sync", v -> {
            boolean online = NetworkState.read(ctx).online;
            accounts.syncNow();
            // The lists' names and counts are read again too.
            lists = null;
            listsFailed = false;
            drawn = null;
            render();
            host.shell().toast(ctx.getString(online ? R.string.account_syncing : R.string.account_sync_offline));
        });
        View choose = button(R.string.account_choose_lists, R.drawable.ic_ui_list, false, "acc-lists", v -> {
            choosing = true;
            if (lists == null && !listsLoading) loadLists();
            render();
        });
        View out = button(R.string.account_sign_out, 0, false, "acc-signout", v ->
                host.shell().push(PaneParts.confirm(ctx, host.shell(), ctx.getString(R.string.account_sign_out_title),
                        ctx.getString(R.string.account_sign_out_body), ctx.getString(R.string.account_sign_out),
                        ctx.getString(R.string.account_cancel), () -> {
                            accounts.signOut();
                            lists = null;
                            listsFailed = false;
                            host.shell().toast(ctx.getString(R.string.account_signed_out_toast));
                        })));
        column.addView(PaneParts.actions(ctx, Arrays.asList(first, choose, out)), PaneParts.below(36));
    }

    /**
     * Choose lists (r2-account B): a back crumb, then one row per TMDB list with a Household / Friends / Not used
     * switch; OK picks, and a role given to one list leaves the list that had it ({@link Accounts#choose}).
     */
    private void chooseLists() {
        // The back crumb, focusable so the sub-state always has somewhere to land while the lists load.
        View crumb = Kit.button(ctx, ctx.getString(R.string.settings_account), R.drawable.ic_ui_back, Kit.SMALL, false,
                GROUP, "acc-crumb", false, v -> onBack());
        column.addView(crumb, PaneParts.below(0));
        first = crumb;
        column.addView(PaneParts.title(ctx, ctx.getString(R.string.account_choose_lists), 40), PaneParts.below(10));
        column.addView(PaneParts.sub(ctx, ctx.getString(R.string.account_lists_sub)), PaneParts.below(8));

        if (lists == null || lists.isEmpty()) {
            int note = lists != null ? R.string.account_lists_none
                    : listsFailed ? R.string.account_offline : R.string.account_lists_loading;
            column.addView(PaneParts.note(ctx, ctx.getString(note), false), PaneParts.wide(28));
            return;
        }
        String override = overrideNote();
        if (!override.isEmpty()) column.addView(PaneParts.note(ctx, override, false), PaneParts.wide(16));
        CharSequence[] roles = {ctx.getString(R.string.account_role_household),
                ctx.getString(R.string.account_role_friends), ctx.getString(R.string.account_role_not_used)};
        ListChoices.Role[] values = ListChoices.Role.values();
        LinearLayout rows = PaneParts.column(ctx);
        for (TmdbAccountParse.ListInfo l : lists) {
            LinearLayout row = PaneParts.across(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.px(76));
            row.setPadding(Ui.px(22), 0, Ui.px(12), 0);
            row.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W));
            LinearLayout name = PaneParts.column(ctx);
            TextView nm = Ui.line(ctx, 23, Ui.TEXT_1, Ui.REGULAR);
            nm.setText(l.name);
            name.addView(nm);
            TextView ct = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
            ct.setText(ctx.getResources().getQuantityString(R.plurals.account_list_items, l.items, l.items));
            name.addView(ct, PaneParts.below(2));
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            int selected = accounts.role(l.id).ordinal();
            View seg = PaneParts.segmented(ctx, roles, selected, true, "list-" + l.id, rows.getChildCount() == 0,
                    i -> {
                        accounts.choose(l.id, values[i]);
                        // Another list may have lost its role: draw them all again, focus staying on this option.
                        drawn = null;
                        render();
                        host.valueChanged();
                    });
            row.addView(seg, PaneParts.after(18));
            if (rows.getChildCount() == 0) first = Focus.byKey(seg, "list-" + l.id + "-" + selected);
            rows.addView(row, PaneParts.wide(rows.getChildCount() == 0 ? 0 : 10));
        }
        column.addView(rows, PaneParts.wide(28));
        column.addView(PaneParts.note(ctx, ctx.getString(R.string.account_lists_pane_note), false), PaneParts.wide(22));
    }

    // ---- Pieces -----------------------------------------------------------------------------------------------------

    private View button(int label, int icon, boolean primary, String key, View.OnClickListener onOk) {
        return Kit.button(ctx, ctx.getString(label), icon, primary ? Kit.PRIMARY : Kit.NORMAL, false, GROUP, key, false,
                onOk);
    }

    /** .ben-row: a 52 px disc with the icon, the line and its sub line. */
    private View benefit(int icon, int title, int sub) {
        LinearLayout row = PaneParts.across(ctx);
        row.addView(PaneParts.disc(ctx, 52, Ui.SURFACE_2, icon, 26, Ui.ACCENT),
                new LinearLayout.LayoutParams(Ui.px(52), Ui.px(52)));
        LinearLayout text = PaneParts.column(ctx);
        TextView t = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        t.setText(title);
        text.addView(t);
        TextView s = Ui.line(ctx, 19, Ui.TEXT_3, Ui.REGULAR);
        s.setText(sub);
        text.addView(s, PaneParts.below(4));
        row.addView(text, PaneParts.grow(20));
        return row;
    }

    /** .acc-row: the QR code's place on the left, the side column on the right, 52 px apart. */
    private void qrRow(View left, LinearLayout side) {
        LinearLayout row = PaneParts.across(ctx);
        row.addView(left, new LinearLayout.LayoutParams(Ui.px(QR), Ui.px(QR)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = Ui.px(52);
        row.addView(side, slp);
        column.addView(row, PaneParts.wide(0));
    }

    private LinearLayout side(int title) {
        LinearLayout side = PaneParts.column(ctx);
        side.addView(PaneParts.eyebrow(ctx, ctx.getString(R.string.settings_account)));
        side.addView(PaneParts.title(ctx, ctx.getString(title), 36), PaneParts.below(10));
        return side;
    }

    /** The QR code's 486 px place, with a background (.qr-hold) or none. */
    private FrameLayout hold(android.graphics.drawable.Drawable background) {
        FrameLayout f = new FrameLayout(ctx);
        f.setBackground(background);
        f.setClipToOutline(true);
        return f;
    }

    private LinearLayout holdContent() {
        LinearLayout in = PaneParts.column(ctx);
        in.setGravity(Gravity.CENTER_HORIZONTAL);
        in.setPadding(Ui.px(40), 0, Ui.px(40), 0);
        return in;
    }

    private TextView holdText(CharSequence s, int color) {
        TextView t = Ui.text(ctx, 22, color, Ui.REGULAR);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static FrameLayout.LayoutParams centred() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
    }

    /** .steps: the three steps, {@code done} of them ticked and the next one current. */
    private View steps(int done) {
        int[] texts = {R.string.account_step_scan, R.string.account_step_approve, R.string.account_step_carry_on};
        LinearLayout box = PaneParts.column(ctx);
        for (int i = 0; i < texts.length; i++) {
            LinearLayout row = PaneParts.across(ctx);
            FrameLayout n;
            if (i < done) {
                n = PaneParts.disc(ctx, 38, Ui.GOOD_SOFT, R.drawable.ic_ui_check, 20, Ui.GOOD);
            } else {
                boolean now = i == done;
                n = PaneParts.disc(ctx, 38, now ? Ui.ACTIVE : Ui.SURFACE_2, 0, 0, 0);
                TextView num = Ui.line(ctx, 19, now ? Ui.ON_ACCENT : Ui.TEXT_1, Ui.BOLD);
                num.setText(String.valueOf(i + 1));
                n.addView(num, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            }
            row.addView(n, new LinearLayout.LayoutParams(Ui.px(38), Ui.px(38)));
            TextView t = Ui.text(ctx, 23, Ui.TEXT_1, Ui.REGULAR);
            t.setText(texts[i]);
            t.setLineSpacing(0f, 1.4f);
            row.addView(t, PaneParts.grow(16));
            box.addView(row, PaneParts.wide(i == 0 ? 0 : 20));
        }
        return box;
    }

    private static int size(Map<SyncList, List<String>> lists, SyncList list) {
        List<String> ids = lists.get(list);
        return ids == null ? 0 : ids.size();
    }

    /** "Our picks · 24 titles", the name config.xml gives, "List 8221116" before the lists arrive, or Not used. */
    private String listValue(ListId list) {
        ListRef ref = accounts.ref(list);
        if (ref.isNone()) return ctx.getString(R.string.account_role_not_used);
        long id = ref.resolve(lists);
        if (lists != null) {
            for (TmdbAccountParse.ListInfo l : lists) {
                if (l.id == id) return ctx.getString(R.string.account_kv_list_value, l.name,
                        ctx.getResources().getQuantityString(R.plurals.account_list_items, l.items, l.items));
            }
        }
        return !ref.name.isEmpty() ? ref.name : ctx.getString(R.string.account_kv_list_number, ref.id);
    }

    private String lastSynced() {
        long minutes = AccountWords.minutesSince(System.currentTimeMillis(), accounts.lastSynced());
        if (minutes < 0) return ctx.getString(R.string.account_synced_not_yet);
        if (minutes == 0) return ctx.getString(R.string.account_synced_now);
        if (minutes < 60) {
            return ctx.getResources().getQuantityString(R.plurals.account_synced_minutes, (int) minutes, (int) minutes);
        }
        int hours = (int) Math.min(Integer.MAX_VALUE, minutes / 60);
        return ctx.getResources().getQuantityString(R.plurals.account_synced_hours, hours, hours);
    }

    /** "Household is set in config.xml." when a role comes from config.xml, so a choice here doesn't change it. */
    private String overrideNote() {
        boolean h = accounts.overridden(ListId.HOUSEHOLD), f = accounts.overridden(ListId.FRIENDS);
        if (h && f) return ctx.getString(R.string.account_lists_override_both);
        if (h) return ctx.getString(R.string.account_lists_override, ctx.getString(R.string.account_role_household));
        if (f) return ctx.getString(R.string.account_lists_override, ctx.getString(R.string.account_role_friends));
        return "";
    }

    private void openWifi() {
        Intent i = SystemSettings.intent(ctx, Settings.ACTION_WIFI_SETTINGS);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            host.shell().toast(ctx.getString(R.string.not_available));
        }
    }

    /** The code in black on white, whole pixels per module, with the quiet zone, at most px square. */
    static Bitmap qrBitmap(Qr q, int px) {
        int scale = AccountWords.moduleSize(q.size, px);
        int side = (q.size + 2 * AccountWords.QUIET) * scale;
        int[] pixels = new int[side * side];
        Arrays.fill(pixels, Color.WHITE);
        int offset = AccountWords.QUIET * scale;
        for (int y = 0; y < q.size; y++) {
            for (int x = 0; x < q.size; x++) {
                if (!q.dark(x, y)) continue;
                for (int dy = 0; dy < scale; dy++) {
                    int row = (offset + y * scale + dy) * side + offset + x * scale;
                    Arrays.fill(pixels, row, row + scale, Color.BLACK);
                }
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.RGB_565);
    }

    /**
     * .qr-spin: a slow turning arc while the code is fetched, one small view (no shimmer); still with reduced motion.
     * Turns only while on screen.
     */
    private static final class Spinner extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private ObjectAnimator turn;

        Spinner(Context ctx) {
            super(ctx);
            for (Paint p : new Paint[]{track, arc}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.px(4));
            }
            track.setColor(Ui.LINE_STRONG);
            arc.setColor(Ui.ACCENT);
            arc.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float half = Ui.px(4) / 2f;
            oval.set(half, half, getWidth() - half, getHeight() - half);
            c.drawOval(oval, track);
            c.drawArc(oval, -90f, 90f, false, arc);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (Ui.reducedMotion()) return;
            turn = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f);
            turn.setDuration(1000);
            turn.setInterpolator(new LinearInterpolator());
            turn.setRepeatCount(ValueAnimator.INFINITE);
            turn.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (turn != null) turn.cancel();
            turn = null;
            super.onDetachedFromWindow();
        }
    }
}
