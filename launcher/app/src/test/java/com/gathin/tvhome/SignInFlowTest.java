package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class SignInFlowTest {
    private static final AccountHttp.Reply TOKEN = new AccountHttp.Reply(200,
            "{\"success\":true,\"status_code\":1,\"request_token\":\"rt\"}");
    private static final AccountHttp.Reply NOT_YET = new AccountHttp.Reply(422,
            "{\"success\":false,\"status_code\":41,\"status_message\":\"not approved\"}");
    private static final AccountHttp.Reply ACCESS = new AccountHttp.Reply(200,
            "{\"success\":true,\"access_token\":\"at\",\"account_id\":\"obj\"}");
    private static final AccountHttp.Reply SESSION = new AccountHttp.Reply(200,
            "{\"success\":true,\"session_id\":\"sid\"}");
    private static final AccountHttp.Reply ACCOUNT = new AccountHttp.Reply(200, "{\"id\":548,\"username\":\"lead\"}");

    @Test
    public void startsSignedInWithACompleteSavedSession() {
        assertEquals(SignInFlow.Phase.SIGNED_IN, new SignInFlow(session()).phase());
        assertEquals(SignInFlow.Phase.SIGNED_OUT, new SignInFlow(null).phase());
        assertEquals(SignInFlow.Phase.SIGNED_OUT,
                new SignInFlow(new TmdbSession("at", "obj", "", 548, "lead")).phase());
    }

    @Test
    public void wholeSignIn() {
        SignInFlow f = new SignInFlow(null);
        assertNull(f.due(0, true));
        f.start(1000);
        assertEquals(SignInFlow.Phase.REQUESTING, f.phase());
        AccountHttp.Call c = f.due(1000, true);
        assertTrue(c.url.endsWith("/4/auth/request_token"));
        assertNull("one call at a time", f.due(1000, true));
        assertEquals(Long.MAX_VALUE, f.nextAt());
        f.onReply(c, TOKEN, 1200);

        assertEquals(SignInFlow.Phase.WAITING, f.phase());
        assertEquals("https://www.themoviedb.org/auth/access?request_token=rt", f.approveUrl());
        assertEquals(SignInFlow.TOKEN_LIFETIME, f.remaining(1200));
        assertEquals(1200 + SignInFlow.POLL, f.nextAt());
        assertNull("polite: not before the interval", f.due(1300, true));

        long t = 1200 + SignInFlow.POLL;
        c = f.due(t, true);
        assertTrue(c.url.endsWith("/4/auth/access_token"));
        f.onReply(c, NOT_YET, t);
        assertEquals(SignInFlow.Phase.WAITING, f.phase());
        assertEquals(t + SignInFlow.POLL, f.nextAt());

        t += SignInFlow.POLL;
        c = f.due(t, true);
        f.onReply(c, ACCESS, t);
        assertEquals(SignInFlow.Phase.FINISHING, f.phase());
        assertNull(f.approveUrl());

        c = f.due(t, true);
        assertTrue(c.url.endsWith("/3/authentication/session/convert/4"));
        f.onReply(c, SESSION, t);
        c = f.due(t, true);
        assertTrue(c.url.endsWith("/3/account?session_id=sid"));
        f.onReply(c, ACCOUNT, t);

        assertEquals(SignInFlow.Phase.SIGNED_IN, f.phase());
        TmdbSession s = f.session();
        assertEquals("at", s.accessToken);
        assertEquals("obj", s.accountObjectId);
        assertEquals("sid", s.sessionId);
        assertEquals(548, s.accountId);
        assertEquals("lead", s.username);
        assertNull(f.due(t + 100_000, true));
        assertEquals(Long.MAX_VALUE, f.nextAt());
    }

    @Test
    public void noPollingWhileHidden() {
        SignInFlow f = waiting(0);
        long t = SignInFlow.POLL;
        assertNull(f.due(t, false));
        assertNull(f.due(t * 10, false));
        AccountHttp.Call c = f.due(t * 10, true);
        assertNotNull("polls again as soon as the screen shows", c);
    }

    @Test
    public void finishingGoesOnWhileHidden() {
        SignInFlow f = waiting(0);
        AccountHttp.Call c = f.due(SignInFlow.POLL, true);
        f.onReply(c, ACCESS, SignInFlow.POLL);
        assertNotNull(f.due(SignInFlow.POLL, false));
    }

    @Test
    public void codeExpires() {
        SignInFlow f = waiting(0);
        assertEquals(SignInFlow.POLL, f.nextAt());
        long end = SignInFlow.TOKEN_LIFETIME;
        assertEquals(1000, f.remaining(end - 1000));
        assertNull(f.due(end, true));
        assertEquals(SignInFlow.Phase.EXPIRED, f.phase());
        assertNull(f.approveUrl());
        f.start(end + 10);
        assertEquals(SignInFlow.Phase.REQUESTING, f.phase());
    }

    @Test
    public void expiredAnswerEndsTheWait() {
        SignInFlow f = waiting(0);
        AccountHttp.Call c = f.due(SignInFlow.POLL, true);
        f.onReply(c, new AccountHttp.Reply(401, "{\"status_code\":33}"), SignInFlow.POLL);
        assertEquals(SignInFlow.Phase.EXPIRED, f.phase());
    }

    @Test
    public void networkErrorsBackOffWhileWaiting() {
        SignInFlow f = waiting(0);
        long t = SignInFlow.POLL;
        long[] delays = {5_000, 10_000, 20_000, 40_000, 60_000, 60_000, 60_000};
        for (long d : delays) {
            AccountHttp.Call c = f.due(t, true);
            f.onError(c, new IOException("offline"), t);
            assertEquals(SignInFlow.Phase.WAITING, f.phase());
            assertEquals(t + d, f.nextAt());
            t += d;
        }
        AccountHttp.Call c = f.due(t, true);
        f.onReply(c, NOT_YET, t);
        assertEquals("an answer resets the delay", t + SignInFlow.POLL, f.nextAt());
    }

    @Test
    public void requestingGivesUpSoonOffline() {
        SignInFlow f = new SignInFlow(null);
        f.start(0);
        long t = 0;
        for (int i = 0; i < SignInFlow.MAX_RETRIES_REQUESTING; i++) {
            AccountHttp.Call c = f.due(t, true);
            f.onReply(c, new AccountHttp.Reply(503, ""), t);
            assertEquals(SignInFlow.Phase.REQUESTING, f.phase());
            t = f.nextAt();
        }
        assertEquals("gives up within 15 s", 15_000, t);
        f.onError(f.due(t, true), new IOException(), t);
        assertEquals(SignInFlow.Phase.FAILED, f.phase());
        assertEquals(TmdbAccountParse.Problem.RETRY, f.failure());
        f.start(t);
        assertEquals(SignInFlow.Phase.REQUESTING, f.phase());
        assertNull(f.failure());
    }

    @Test
    public void finishingTriesLonger() {
        SignInFlow f = waiting(0);
        long t = SignInFlow.POLL;
        f.onReply(f.due(t, true), ACCESS, t);
        for (int i = 0; i < SignInFlow.MAX_RETRIES; i++) {
            f.onError(f.due(t, false), new IOException(), t);
            assertEquals(SignInFlow.Phase.FINISHING, f.phase());
            t = f.nextAt();
        }
        f.onError(f.due(t, false), new IOException(), t);
        assertEquals(SignInFlow.Phase.FAILED, f.phase());
    }

    @Test
    public void refusedKeyFails() {
        SignInFlow f = new SignInFlow(null);
        f.start(0);
        AccountHttp.Call c = f.due(0, true);
        f.onReply(c, new AccountHttp.Reply(401, "{\"status_code\":7,\"status_message\":\"Invalid API key\"}"), 0);
        assertEquals(SignInFlow.Phase.FAILED, f.phase());
        assertEquals(TmdbAccountParse.Problem.REJECTED, f.failure());
        assertNull(f.due(100_000, true));
    }

    @Test
    public void staleAnswersAreIgnored() {
        SignInFlow f = waiting(0);
        AccountHttp.Call c = f.due(SignInFlow.POLL, true);
        f.cancel();
        assertEquals(SignInFlow.Phase.SIGNED_OUT, f.phase());
        f.onReply(c, ACCESS, SignInFlow.POLL);
        assertEquals(SignInFlow.Phase.SIGNED_OUT, f.phase());
        f.onReply(null, ACCESS, SignInFlow.POLL);
        assertEquals(SignInFlow.Phase.SIGNED_OUT, f.phase());
    }

    @Test
    public void startDoesNothingWhileUnderWay() {
        SignInFlow f = waiting(0);
        f.start(10);
        assertEquals(SignInFlow.Phase.WAITING, f.phase());
        assertEquals("https://www.themoviedb.org/auth/access?request_token=rt", f.approveUrl());
        SignInFlow in = new SignInFlow(session());
        in.start(0);
        assertEquals(SignInFlow.Phase.SIGNED_IN, in.phase());
        in.cancel();
        assertEquals(SignInFlow.Phase.SIGNED_IN, in.phase());
    }

    @Test
    public void signOutRevokesBothTokens() {
        SignInFlow f = new SignInFlow(session());
        List<AccountHttp.Call> calls = f.signOut();
        assertEquals(SignInFlow.Phase.SIGNED_OUT, f.phase());
        assertNull(f.session());
        assertEquals(2, calls.size());
        assertEquals(AccountHttp.Method.DELETE, calls.get(0).method);
        assertTrue(calls.get(0).url.endsWith("/4/auth/access_token"));
        assertTrue(calls.get(1).url.endsWith("/3/authentication/session"));
        assertTrue(f.signOut().isEmpty());
    }

    @Test
    public void onlyAnApprovedSignInRunsHidden() {
        SignInFlow f = new SignInFlow(null);
        assertTrue(!f.runsHidden());
        f.start(0);
        assertTrue(!f.runsHidden());
        f = waiting(0);
        assertTrue(!f.runsHidden());
        f.onReply(f.due(SignInFlow.POLL, true), ACCESS, SignInFlow.POLL);
        assertTrue(f.runsHidden());
        assertTrue(!new SignInFlow(session()).runsHidden());
    }

    @Test
    public void closingThePageDuringAFinishingRetryKeepsTheApproval() {
        // Approved; the session call fails, so the next try waits. The page closes (cancel) during that wait.
        SignInFlow f = waiting(0);
        long t = SignInFlow.POLL;
        f.onReply(f.due(t, true), ACCESS, t);
        f.onError(f.due(t, true), new IOException("offline"), t);
        f.cancel();
        assertEquals(SignInFlow.Phase.FINISHING, f.phase());
        assertTrue("Accounts schedules the next step again", f.runsHidden());
        long next = f.nextAt();
        assertTrue(next != Long.MAX_VALUE && next > t);
        assertNull("not before the retry delay", f.due(next - 1, false));
        AccountHttp.Call c = f.due(next, false);
        assertTrue(c.url.endsWith("/3/authentication/session/convert/4"));
        f.onReply(c, SESSION, next);
        f.onReply(f.due(next, false), ACCOUNT, next);
        assertEquals(SignInFlow.Phase.SIGNED_IN, f.phase());
        assertEquals("sid", f.session().sessionId);
    }

    @Test
    public void sessionNeverPrintsItsTokens() {
        String s = new TmdbSession("ACCESS-SECRET", "obj", "SESSION-SECRET", 548, "lead").toString();
        assertTrue(s.contains("lead"));
        assertTrue(!s.contains("ACCESS-SECRET") && !s.contains("SESSION-SECRET"));
    }

    private static TmdbSession session() {
        return new TmdbSession("at", "obj", "sid", 548, "lead");
    }

    private static SignInFlow waiting(long now) {
        SignInFlow f = new SignInFlow(null);
        f.start(now);
        AccountHttp.Call c = f.due(now, true);
        f.onReply(c, TOKEN, now);
        assertSame(SignInFlow.Phase.WAITING, f.phase());
        return f;
    }
}
