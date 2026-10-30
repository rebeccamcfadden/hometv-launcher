package com.gathin.tvhome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Signing in to TMDB on the TV (BACKLOG.md, TMDB sign-in and watchlist sync), as a state machine with no threads or
 * clock of its own, so it is unit-tested (SignInFlowTest). The sign-in screen asks {@link #due} for the next call,
 * sends it off the main thread, and hands back the answer to {@link #onReply} or the error to {@link #onError}; it
 * asks again at {@link #nextAt}.
 *
 * <p>The steps: a request token (v4), shown as a QR code for {@link TmdbAccountApi#approveUrl}; while the code
 * shows, every {@link #POLL} ms ask for the access token, which TMDB refuses until the person approves on their
 * phone; then turn it into a v3 session and read the account. A request token lasts 15 minutes
 * (developer.themoviedb.org/v4/docs/authentication-user), then the code is EXPIRED and the screen offers a new one.
 * Nothing is asked while the screen is hidden, except to finish a sign-in already approved.
 */
final class SignInFlow {
    enum Phase {
        SIGNED_OUT,
        /** Asking TMDB for a request token. */
        REQUESTING,
        /** The QR code shows; polling for the person's approval. */
        WAITING,
        /** Approved: getting the session and the account. */
        FINISHING,
        SIGNED_IN,
        /** The code ran out before it was approved. */
        EXPIRED,
        /** TMDB refused, or could not be reached; see {@link #failure()}. */
        FAILED
    }

    static final long TOKEN_LIFETIME = 15 * 60_000L;
    /** How often to ask whether the code is approved: polite, and still quick after tapping Approve. */
    static final long POLL = 5_000L;
    static final long RETRY_MAX = 60_000L;
    /**
     * Network failures in a row before giving up: getting a code gives up soon (5 + 10 s), so the screen says the
     * TV is offline instead of "Getting a code…"; finishing an approved sign-in tries longer. The wait for approval
     * keeps trying until the code runs out.
     */
    static final int MAX_RETRIES_REQUESTING = 2;
    static final int MAX_RETRIES = 5;

    private Phase phase;
    private String requestToken;
    private String approveUrl;
    private long expiresAt;
    private TmdbAccountParse.Access access;
    private String sessionId;
    private TmdbSession session;
    private long nextAt;
    private long backoff;
    private int retries;
    private AccountHttp.Call inFlight;
    private TmdbAccountParse.Problem failure;

    /** Starts signed in with a saved account, otherwise signed out. */
    SignInFlow(TmdbSession saved) {
        if (saved != null && saved.complete()) {
            session = saved;
            phase = Phase.SIGNED_IN;
        } else {
            phase = Phase.SIGNED_OUT;
        }
    }

    Phase phase() {
        return phase;
    }

    /** The link the QR code holds while {@link Phase#WAITING}, else null. */
    String approveUrl() {
        return phase == Phase.WAITING ? approveUrl : null;
    }

    /** Milliseconds until the code runs out, for a countdown on the screen. */
    long remaining(long now) {
        return phase == Phase.WAITING ? Math.max(0, expiresAt - now) : 0;
    }

    /** The account once {@link Phase#SIGNED_IN}, else null. */
    TmdbSession session() {
        return phase == Phase.SIGNED_IN ? session : null;
    }

    /** Why it {@link Phase#FAILED}: RETRY for no network, REJECTED or SIGNED_OUT for TMDB saying no. */
    TmdbAccountParse.Problem failure() {
        return phase == Phase.FAILED ? failure : null;
    }

    /**
     * Whether its calls go on with the page (or Home) hidden: only to finish a sign-in already approved, whose access
     * token is in memory alone until the session is saved. Leaving it would lose the approval.
     */
    boolean runsHidden() {
        return phase == Phase.FINISHING;
    }

    /** Opens the sign-in, or asks for a new code after it expired or failed. Does nothing while one is under way. */
    void start(long now) {
        if (phase != Phase.SIGNED_OUT && phase != Phase.EXPIRED && phase != Phase.FAILED) return;
        clearAttempt();
        phase = Phase.REQUESTING;
        nextAt = now;
    }

    /** The screen closed before the code was approved: forget the code. A sign-in being finished is let finish. */
    void cancel() {
        if (phase == Phase.REQUESTING || phase == Phase.WAITING || phase == Phase.EXPIRED || phase == Phase.FAILED) {
            clearAttempt();
            phase = Phase.SIGNED_OUT;
        }
    }

    /**
     * Signs out at once and returns the calls that revoke the tokens on TMDB, to send when the network allows (a
     * failure there changes nothing on the TV). Empty when not signed in.
     */
    List<AccountHttp.Call> signOut() {
        List<AccountHttp.Call> calls = new ArrayList<>();
        if (session != null) {
            calls.add(TmdbAccountApi.logout(session.accessToken));
            calls.add(TmdbAccountApi.deleteSession(session.sessionId));
        }
        session = null;
        clearAttempt();
        phase = Phase.SIGNED_OUT;
        return Collections.unmodifiableList(calls);
    }

    /**
     * The call to send now, or null: nothing to do, one already out, not time yet, or the screen hidden. Moves to
     * EXPIRED once the code has run out.
     */
    AccountHttp.Call due(long now, boolean visible) {
        if (phase == Phase.WAITING && now >= expiresAt) {
            phase = Phase.EXPIRED;
            requestToken = null;
            approveUrl = null;
            return null;
        }
        if (inFlight != null || now < nextAt) return null;
        if (!visible && !runsHidden()) return null;
        AccountHttp.Call call;
        switch (phase) {
            case REQUESTING:
                call = TmdbAccountApi.requestToken();
                break;
            case WAITING:
                call = TmdbAccountApi.accessToken(requestToken);
                break;
            case FINISHING:
                call = sessionId == null ? TmdbAccountApi.session(access.accessToken) : TmdbAccountApi.account(sessionId);
                break;
            default:
                return null;
        }
        inFlight = call;
        return call;
    }

    /** When {@link #due} may next give a call; Long.MAX_VALUE when it won't until something else happens. */
    long nextAt() {
        if (inFlight != null) return Long.MAX_VALUE;
        switch (phase) {
            case REQUESTING:
            case FINISHING:
                return nextAt;
            case WAITING:
                return Math.min(nextAt, expiresAt);
            default:
                return Long.MAX_VALUE;
        }
    }

    void onReply(AccountHttp.Call call, AccountHttp.Reply reply, long now) {
        if (call == null || call != inFlight) return;
        inFlight = null;
        try {
            switch (phase) {
                case REQUESTING:
                    requestToken = TmdbAccountParse.requestToken(reply);
                    approveUrl = TmdbAccountApi.approveUrl(requestToken);
                    expiresAt = now + TOKEN_LIFETIME;
                    phase = Phase.WAITING;
                    succeeded(now + POLL);
                    break;
                case WAITING:
                    access = TmdbAccountParse.access(reply);
                    requestToken = null;
                    approveUrl = null;
                    phase = Phase.FINISHING;
                    succeeded(now);
                    break;
                case FINISHING:
                    if (sessionId == null) {
                        sessionId = TmdbAccountParse.sessionId(reply);
                        succeeded(now);
                    } else {
                        TmdbAccountParse.Account a = TmdbAccountParse.account(reply);
                        session = new TmdbSession(access.accessToken, access.accountObjectId, sessionId, a.id,
                                a.username);
                        clearAttempt();
                        phase = Phase.SIGNED_IN;
                    }
                    break;
                default:
                    break;
            }
        } catch (TmdbAccountParse.Failure f) {
            fail(f.problem, now);
        }
    }

    void onError(AccountHttp.Call call, IOException e, long now) {
        if (call == null || call != inFlight) return;
        inFlight = null;
        fail(TmdbAccountParse.Problem.RETRY, now);
    }

    private void fail(TmdbAccountParse.Problem problem, long now) {
        if (phase == Phase.WAITING && problem == TmdbAccountParse.Problem.NOT_APPROVED) {
            succeeded(now + POLL);
            return;
        }
        int max = phase == Phase.REQUESTING ? MAX_RETRIES_REQUESTING : MAX_RETRIES;
        if (problem == TmdbAccountParse.Problem.RETRY && (phase == Phase.WAITING || ++retries <= max)) {
            backoff = backoff == 0 ? POLL : Math.min(backoff * 2, RETRY_MAX);
            nextAt = now + backoff;
            return;
        }
        if (problem == TmdbAccountParse.Problem.EXPIRED && (phase == Phase.WAITING || phase == Phase.FINISHING)) {
            clearAttempt();
            phase = Phase.EXPIRED;
            return;
        }
        clearAttempt();
        failure = problem;
        phase = Phase.FAILED;
    }

    private void succeeded(long next) {
        nextAt = next;
        backoff = 0;
        retries = 0;
    }

    private void clearAttempt() {
        requestToken = null;
        approveUrl = null;
        expiresAt = 0;
        access = null;
        sessionId = null;
        nextAt = 0;
        backoff = 0;
        retries = 0;
        inFlight = null;
        failure = null;
    }
}
