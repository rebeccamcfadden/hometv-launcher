package com.hometv.launcher;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Account on a TV whose launcher was built without a TMDB key (F16; design_preview's account.ts): signing in asks
 * TMDB, so it needs the household's key first. In Launcher settings the pane says so and offers Add a TMDB key (API
 * keys); in First run the TMDB key tab above it is where the key goes. Once a key is on the TV, Sign in rebuilds the
 * launcher with it (the account is made with the key it is built with) and comes back to Account. With a key built
 * in, it is Account itself.
 */
final class AccountKeyGate implements SettingsSection {
    private final SettingsSection inner;
    private final MainActivity home;
    private boolean gatePane;
    private View open;

    AccountKeyGate(SettingsSection inner, MainActivity home) {
        this.inner = inner;
        this.home = home;
    }

    /** The launcher runs without a TMDB key: its account can't sign in until it is rebuilt with one. */
    private boolean gated() {
        return !home.data().accounts.available();
    }

    @Override
    public String id() {
        return inner.id();
    }

    @Override
    public int label() {
        return inner.label();
    }

    @Override
    public int icon() {
        return inner.icon();
    }

    @Override
    public CharSequence value(Context ctx) {
        return inner.value(ctx);
    }

    @Override
    public View pane(Context ctx, Host host) {
        gatePane = gated();
        if (!gatePane) return inner.pane(ctx, host);
        LinearLayout pane = SettingsKit.column(ctx);
        pane.addView(SettingsKit.eyebrow(ctx, ctx.getString(inner.label())));
        SettingsKit.add(pane, SettingsKit.title(ctx, ctx.getString(R.string.account_no_key_title), false), 10);
        SettingsKit.add(pane, SettingsKit.sub(ctx, ctx.getString(R.string.account_no_key_sub)), 8);
        boolean keyNow = ApiKeys.get(ctx).tmdbReady();
        if (keyNow) {
            SettingsKit.addWide(pane, SettingsKit.note(ctx, ctx.getString(R.string.account_key_added)), 22);
            open = SettingsKit.button(ctx, ctx.getString(R.string.account_key_sign_in), R.drawable.ic_ui_phone, true,
                    null, "acc-signin", true, v -> host.rebuildInto(id()));
        } else {
            TextView note = SettingsKit.note(ctx, ctx.getString(host.inSetup() ? R.string.account_no_key_setup
                    : R.string.account_no_key_settings));
            note.setTextColor(StateUi.amber());
            SettingsKit.addWide(pane, note, 22);
            // First run's TMDB key tab is above this one: no button, focus goes on to the footer.
            open = host.inSetup() ? null : SettingsKit.button(ctx, ctx.getString(R.string.account_add_key),
                    R.drawable.ic_ui_key, true, null, "acc-key", true, v -> host.enter(KeysSection.ID));
        }
        if (open != null) SettingsKit.add(pane, open, 22);
        return pane;
    }

    @Override
    public View firstFocus() {
        return gatePane ? open : inner.firstFocus();
    }

    @Override
    public boolean onBack() {
        return !gatePane && inner.onBack();
    }

    @Override
    public boolean onKey(Screen.Key key, View focused) {
        return !gatePane && inner.onKey(key, focused);
    }

    @Override
    public void onClose() {
        inner.onClose();
    }

    @Override
    public boolean onText(char c) {
        return !gatePane && inner.onText(c);
    }
}
