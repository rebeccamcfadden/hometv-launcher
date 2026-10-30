package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The TMDB sign-in page, a temporary design until one is chosen in Figma (BACKLOG.md, TMDB sign-in and watchlist
 * sync): a pushed page from Launcher settings' "TMDB account" row with the state of {@link Accounts#flow()}: getting
 * a code, the QR code with its countdown, finishing, signed in with Sign out, expired with New code, and offline or
 * refused with Try again. All the logic is in Accounts and SignInFlow; this only draws it, so a new design replaces
 * this file alone. The code is asked for as the page opens and polled only while the page is on screen.
 */
final class AccountPage {
    private AccountPage() {}

    /** The QR code's side, quiet zone included: well over 420 px so a phone reads it from the sofa. */
    private static final int QR_SIZE = 540;

    static Screen create(Context ctx, Router router) {
        Accounts accounts = router.data().accounts;
        Shell shell = router.shell();
        Root root = new Root(ctx);
        root.addView(StubPage.frame(ctx), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout body = Kit.unclip(new LinearLayout(ctx));
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.px(StubPage.ROOM), 0, 0, Ui.px(StubPage.ROOM));
        TextView eyebrow = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        eyebrow.setText(R.string.account_temporary);
        eyebrow.setAllCaps(true);
        eyebrow.setLetterSpacing(0.08f);
        body.addView(eyebrow);
        TextView title = Ui.line(ctx, 64, Ui.TEXT_1, Ui.REGULAR);
        title.setText(R.string.account_title);
        body.addView(title, margins(14));
        TextView status = Ui.text(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        status.setLineSpacing(0f, 1.4f);
        body.addView(status, margins(36));
        TextView note = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        note.setLineSpacing(0f, 1.5f);
        body.addView(note, margins(18));
        TextView countdown = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_3, Ui.REGULAR);
        body.addView(countdown, margins(18));
        LinearLayout buttons = Kit.unclip(new LinearLayout(ctx));
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(buttons, margins(44));

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.px(900), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        blp.topMargin = Ui.px(220);
        blp.leftMargin = Ui.px(Ui.SAFE_X - StubPage.ROOM);
        root.addView(body, blp);

        ImageView qr = new ImageView(ctx);
        qr.setScaleType(ImageView.ScaleType.CENTER);
        qr.setBackground(Ui.round(Color.WHITE, Ui.RADIUS_L));
        qr.setContentDescription(ctx.getString(R.string.account_qr_description));
        qr.setVisibility(View.GONE);
        FrameLayout.LayoutParams qlp = new FrameLayout.LayoutParams(Ui.px(QR_SIZE), Ui.px(QR_SIZE),
                Gravity.TOP | Gravity.END);
        qlp.topMargin = Ui.px(260);
        qlp.rightMargin = Ui.px(Ui.SAFE_X + 120);
        root.addView(qr, qlp);

        Screen[] screen = new Screen[1];
        View[] first = new View[1];
        String[] drawn = {null, null};
        Runnable[] tick = new Runnable[1];
        Runnable render = () -> {
            SignInFlow flow = accounts.flow();
            SignInFlow.Phase phase = flow.phase();
            countdown.setText(phase == SignInFlow.Phase.WAITING ? ctx.getString(R.string.account_valid_for,
                    AccountWords.countdown(flow.remaining(System.currentTimeMillis()))) : "");
            String key = phase + "|" + accounts.username() + "|" + flow.failure();
            if (key.equals(drawn[0])) return;
            drawn[0] = key;
            List<View> actions = new ArrayList<>();
            note.setText("");
            switch (phase) {
                case REQUESTING:
                    status.setText(R.string.account_getting_code);
                    break;
                case WAITING:
                    status.setText(R.string.account_scan);
                    break;
                case FINISHING:
                    status.setText(R.string.account_finishing);
                    break;
                case SIGNED_IN:
                    status.setText(ctx.getString(R.string.account_signed_in, accounts.username()));
                    note.setText(R.string.account_signed_in_note);
                    actions.add(Kit.button(ctx, ctx.getString(R.string.account_choose_lists), 0, Kit.PRIMARY, false,
                            "account", "choose-lists", true, v -> shell.push(chooseLists(ctx, router))));
                    actions.add(Kit.button(ctx, ctx.getString(R.string.account_sign_out), 0, Kit.NORMAL, false,
                            "account", "sign-out", false, v -> {
                                accounts.signOut();
                                router.toast(ctx.getString(R.string.account_signed_out_toast));
                            }));
                    break;
                case EXPIRED:
                    status.setText(R.string.account_expired);
                    actions.add(Kit.button(ctx, ctx.getString(R.string.account_new_code), 0, Kit.PRIMARY, false,
                            "account", "new-code", true, v -> accounts.startSignIn()));
                    break;
                case FAILED:
                    status.setText(flow.failure() == TmdbAccountParse.Problem.RETRY ? R.string.account_offline
                            : R.string.account_refused);
                    actions.add(Kit.button(ctx, ctx.getString(R.string.account_try_again), 0, Kit.PRIMARY, false,
                            "account", "try-again", true, v -> accounts.startSignIn()));
                    break;
                default:
                    status.setText(R.string.account_signed_out);
                    note.setText(R.string.account_intro);
                    actions.add(Kit.button(ctx, ctx.getString(R.string.account_sign_in), 0, Kit.PRIMARY, false,
                            "account", "sign-in", true, v -> accounts.startSignIn()));
                    break;
            }
            String url = flow.approveUrl();
            if (url == null) {
                qr.setImageDrawable(null);
                qr.setVisibility(View.GONE);
                drawn[1] = null;
            } else if (!url.equals(drawn[1])) {
                drawn[1] = url;
                qr.setImageBitmap(bitmap(Qr.encode(url), Ui.px(QR_SIZE)));
                qr.setVisibility(View.VISIBLE);
            }
            buttons.removeAllViews();
            actions.add(StubPage.backButton(ctx, shell));
            for (int i = 0; i < actions.size(); i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        Ui.px(66));
                if (i > 0) lp.leftMargin = Ui.px(16);
                buttons.addView(actions.get(i), lp);
            }
            first[0] = actions.get(0);
            if (shell.top() == screen[0]) {
                shell.focus(first[0]);
            }
        };
        tick[0] = () -> {
            render.run();
            root.removeCallbacks(tick[0]);
            if (accounts.flow().phase() == SignInFlow.Phase.WAITING && root.showing()) root.postDelayed(tick[0], 1000);
        };
        Runnable listener = tick[0];
        root.onChange = attached -> {
            if (attached) {
                accounts.addListener(listener);
                SignInFlow.Phase p = accounts.flow().phase();
                if (p == SignInFlow.Phase.SIGNED_OUT) accounts.startSignIn();
                accounts.setSignInVisible(root.showing());
                tick[0].run();
            } else {
                accounts.removeListener(listener);
                root.removeCallbacks(tick[0]);
                accounts.setSignInVisible(false);
                if (accounts.flow().phase() != SignInFlow.Phase.SIGNED_IN) accounts.cancelSignIn();
            }
        };
        root.onVisible = shown -> {
            accounts.setSignInVisible(shown);
            if (shown) tick[0].run();
        };
        render.run();
        screen[0] = new Screen(ctx.getString(R.string.account_title), root, Screen.Layer.PAGE) {
            @Override
            View defaultFocus() {
                return first[0];
            }
        };
        return screen[0];
    }

    /**
     * Choose lists, as temporary as the page it comes from: one row per list in the account, each Household, Friends
     * or Not used; OK moves a row to the next choice, and a role taken by another row leaves it. Saved on the TV at
     * once ({@link Accounts#choose}); a list config.xml sets for a role is shown as such and can't be changed here.
     */
    static Screen chooseLists(Context ctx, Router router) {
        Accounts accounts = router.data().accounts;
        Shell shell = router.shell();
        FrameLayout root = StubPage.frame(ctx);
        ViewGroup box = ScrollBox.vertical(ctx, 40);
        LinearLayout col = Kit.unclip(new LinearLayout(ctx));
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(Ui.SAFE_X), Ui.px(120), Ui.px(Ui.SAFE_X), Ui.px(Ui.SAFE_Y + 40));
        box.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView eyebrow = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        eyebrow.setText(R.string.account_temporary);
        eyebrow.setAllCaps(true);
        eyebrow.setLetterSpacing(0.08f);
        col.addView(eyebrow);
        TextView title = Ui.line(ctx, 64, Ui.TEXT_1, Ui.REGULAR);
        title.setText(R.string.account_choose_lists);
        col.addView(title, margins(14));
        TextView note = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_2, Ui.REGULAR);
        note.setLineSpacing(0f, 1.5f);
        note.setText(R.string.account_lists_loading);
        col.addView(note, margins(18));
        LinearLayout rows = Kit.unclip(new LinearLayout(ctx));
        rows.setOrientation(LinearLayout.VERTICAL);
        col.addView(rows, margins(28));
        View back = StubPage.backButton(ctx, shell);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.px(66));
        blp.topMargin = Ui.px(36);
        col.addView(back, blp);

        Screen screen = new Screen(ctx.getString(R.string.account_choose_lists), root, Screen.Layer.PAGE) {
            @Override
            View defaultFocus() {
                return rows.getChildCount() > 0 ? rows.getChildAt(0) : back;
            }
        };
        accounts.loadAccountLists(lists -> {
            if (lists.isEmpty()) {
                note.setText(R.string.account_lists_none);
                return;
            }
            String overrides = overrideNote(ctx, accounts);
            note.setText(overrides.isEmpty() ? ctx.getString(R.string.account_lists_note)
                    : ctx.getString(R.string.account_lists_note) + " " + overrides);
            List<TextView> labels = new ArrayList<>();
            Runnable refresh = () -> {
                for (int i = 0; i < lists.size(); i++) {
                    TmdbAccountParse.ListInfo l = lists.get(i);
                    labels.get(i).setText(ctx.getString(R.string.account_list_row, l.name,
                            ctx.getResources().getQuantityString(R.plurals.account_list_items, l.items, l.items),
                            ctx.getString(roleWord(accounts.role(l.id)))));
                }
            };
            for (TmdbAccountParse.ListInfo l : lists) {
                View row = Kit.button(ctx, "", 0, Kit.NORMAL, false, "lists", "list-" + l.id, false, v -> {
                    accounts.choose(l.id, accounts.role(l.id).next());
                    refresh.run();
                });
                labels.add((TextView) row.getTag(R.id.kit_label));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        Ui.px(66));
                lp.topMargin = rows.getChildCount() == 0 ? 0 : Ui.px(14);
                rows.addView(row, lp);
            }
            refresh.run();
            if (shell.top() == screen) shell.focus(rows.getChildAt(0));
        }, () -> note.setText(R.string.account_offline));
        return screen;
    }

    private static int roleWord(ListChoices.Role role) {
        switch (role) {
            case HOUSEHOLD:
                return R.string.account_role_household;
            case FRIENDS:
                return R.string.account_role_friends;
            default:
                return R.string.account_role_not_used;
        }
    }

    /** "Household is set in config.xml." when a role comes from config.xml, so a choice here doesn't change it. */
    private static String overrideNote(Context ctx, Accounts accounts) {
        boolean h = accounts.overridden(ListId.HOUSEHOLD), f = accounts.overridden(ListId.FRIENDS);
        if (h && f) return ctx.getString(R.string.account_lists_override_both);
        if (h) return ctx.getString(R.string.account_lists_override, ctx.getString(R.string.account_role_household));
        if (f) return ctx.getString(R.string.account_lists_override, ctx.getString(R.string.account_role_friends));
        return "";
    }

    /** The code in black on white, whole pixels per module, with the quiet zone, at most px square. */
    static Bitmap bitmap(Qr q, int px) {
        int scale = AccountWords.moduleSize(q.size, px);
        int side = (q.size + 2 * AccountWords.QUIET) * scale;
        int[] pixels = new int[side * side];
        java.util.Arrays.fill(pixels, Color.WHITE);
        int offset = AccountWords.QUIET * scale;
        for (int y = 0; y < q.size; y++) {
            for (int x = 0; x < q.size; x++) {
                if (!q.dark(x, y)) continue;
                for (int dy = 0; dy < scale; dy++) {
                    int row = (offset + y * scale + dy) * side + offset + x * scale;
                    java.util.Arrays.fill(pixels, row, row + scale, Color.BLACK);
                }
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.RGB_565);
    }

    private static LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }

    /** The page's root, which says when it joins or leaves the screen, and when Home is hidden by another app. */
    private static final class Root extends FrameLayout {
        interface Change {
            void on(boolean value);
        }

        Change onChange;
        Change onVisible;

        Root(Context ctx) {
            super(ctx);
        }

        boolean showing() {
            return isAttachedToWindow() && getWindowVisibility() == View.VISIBLE;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (onChange != null) onChange.on(true);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (onChange != null) onChange.on(false);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (onVisible != null && isAttachedToWindow()) onVisible.on(visibility == View.VISIBLE);
        }
    }
}
