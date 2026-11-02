package com.hometv.launcher;

import android.content.Context;
import android.view.View;

/**
 * Movies, TV Shows and Search need a TMDB account (F14, the project lead 2026-11-01): while TMDB account is switched
 * off in Features, or on but the TV isn't signed in, they show only a state that says so (design_preview's needsTmdb),
 * and nothing is fetched for them. A launcher running without a TMDB key has no account to sign in to: a release
 * build (every family TV) says it needs a key, with Add a TMDB key (F16, the lead for the project lead 2026-11-02); any
 * other build runs on the mock catalogue, so it isn't held back (the emulator, CI). Pure rules in
 * {@link #need(boolean, boolean, boolean, boolean)}, tested.
 */
final class TmdbGate {
    private TmdbGate() {}

    enum Need {
        /** The page shows. */
        NONE,
        /** TMDB account is off in Features: Turn on. */
        SWITCHED_OFF,
        /** On, but not signed in: Sign in (Launcher settings › Account). */
        SIGN_IN,
        /** On, but a release build without a TMDB key: Add a TMDB key (Launcher settings › API keys). */
        KEY
    }

    static Need need(boolean featureOn, boolean tokenBuild, boolean signedIn, boolean publicRelease) {
        if (!featureOn) return Need.SWITCHED_OFF;
        if (!tokenBuild) return publicRelease ? Need.KEY : Need.NONE;
        return signedIn ? Need.NONE : Need.SIGN_IN;
    }

    /** For this launcher as built: Features, and the account (whose token is empty in a build without one). */
    static Need need(Accounts accounts) {
        return need(Features.isOn(Features.Feature.TMDB), accounts.available(), accounts.signedIn(),
                BuildConfig.PUBLIC_RELEASE);
    }

    /**
     * The state: the person icon, "Movies needs a TMDB account", why, and the one button (first focus, key
     * {@link #KEY}): Turn on or Sign in, straight into Account's sign-in (MainActivity#signInFromPage). Without a key:
     * the key icon, "Movies needs a TMDB key", and Add a TMDB key, straight into API keys (MainActivity#addKeyFromPage).
     */
    static View block(Context ctx, CharSequence page, Need need) {
        if (need == Need.KEY) {
            View add = Kit.button(ctx, ctx.getString(R.string.account_add_key), R.drawable.ic_ui_key, Kit.PRIMARY,
                    false, null, KEY, true, v -> ((MainActivity) ctx).addKeyFromPage());
            return StateUi.offlineBlock(ctx, R.drawable.ic_ui_key, ctx.getString(R.string.needs_tmdb_key_title, page),
                    ctx.getString(R.string.needs_tmdb_key_body), add);
        }
        boolean off = need == Need.SWITCHED_OFF;
        View button = Kit.button(ctx, ctx.getString(off ? R.string.feature_turn_on : R.string.needs_tmdb_sign_in),
                off ? R.drawable.ic_ui_power : R.drawable.ic_ui_user, Kit.PRIMARY, false, null, KEY, true,
                v -> ((MainActivity) ctx).signInFromPage(off));
        return StateUi.offlineBlock(ctx, R.drawable.ic_ui_user, ctx.getString(R.string.needs_tmdb_title, page),
                ctx.getString(off ? R.string.needs_tmdb_off : R.string.needs_tmdb_signed_out), button);
    }

    /** The state's button. */
    static final String KEY = "needs-tmdb";
}
