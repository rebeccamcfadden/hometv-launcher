package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;

/** Fixtures follow the examples in TMDB's reference (developer.themoviedb.org), cut down; no real tokens. */
public class TmdbAccountParseTest {
    private static AccountHttp.Reply ok(String body) {
        return new AccountHttp.Reply(200, body);
    }

    @Test
    public void signInSteps() throws Exception {
        assertEquals("rt-1", TmdbAccountParse.requestToken(ok(
                "{\"status_message\":\"Success.\",\"request_token\":\"rt-1\",\"success\":true,\"status_code\":1}")));
        TmdbAccountParse.Access a = TmdbAccountParse.access(ok("{\"account_id\":\"4bc8892a017a3c0z92001001\","
                + "\"access_token\":\"at-1\",\"success\":true,\"status_message\":\"Success.\",\"status_code\":1}"));
        assertEquals("at-1", a.accessToken);
        assertEquals("4bc8892a017a3c0z92001001", a.accountObjectId);
        assertEquals("s-1", TmdbAccountParse.sessionId(ok("{\"success\":true,\"session_id\":\"s-1\"}")));
        TmdbAccountParse.Account acc = TmdbAccountParse.account(ok("{\"avatar\":{},\"id\":548,\"iso_639_1\":\"en\","
                + "\"name\":\"\",\"include_adult\":false,\"username\":\"travisbell\"}"));
        assertEquals(548, acc.id);
        assertEquals("travisbell", acc.username);
    }

    @Test
    public void missingFieldsAreRejected() {
        expect(TmdbAccountParse.Problem.REJECTED, () -> TmdbAccountParse.requestToken(ok("{\"success\":true}")));
        expect(TmdbAccountParse.Problem.REJECTED, () -> TmdbAccountParse.requestToken(ok("{\"request_token\":null}")));
        expect(TmdbAccountParse.Problem.REJECTED, () -> TmdbAccountParse.account(ok("{\"username\":\"x\"}")));
        expect(TmdbAccountParse.Problem.RETRY, () -> TmdbAccountParse.sessionId(ok("<html>")));
    }

    @Test
    public void failuresByStatusCode() {
        expect(TmdbAccountParse.Problem.NOT_APPROVED, () -> TmdbAccountParse.access(new AccountHttp.Reply(422,
                "{\"success\":false,\"status_code\":41,\"status_message\":\"This request token hasn't been approved.\"}")));
        expect(TmdbAccountParse.Problem.NOT_APPROVED, () -> TmdbAccountParse.sessionId(new AccountHttp.Reply(401,
                "{\"success\":false,\"status_code\":17,\"status_message\":\"Session denied.\"}")));
        expect(TmdbAccountParse.Problem.EXPIRED, () -> TmdbAccountParse.access(new AccountHttp.Reply(401,
                "{\"success\":false,\"status_code\":33,\"status_message\":\"Invalid request token.\"}")));
        expect(TmdbAccountParse.Problem.SIGNED_OUT, () -> TmdbAccountParse.page(new AccountHttp.Reply(401,
                "{\"success\":false,\"status_code\":3,\"status_message\":\"Authentication failed.\"}"), "movie"));
        expect(TmdbAccountParse.Problem.SIGNED_OUT, () -> TmdbAccountParse.done(new AccountHttp.Reply(404,
                "{\"success\":false,\"status_code\":37}")));
        expect(TmdbAccountParse.Problem.REJECTED, () -> TmdbAccountParse.done(new AccountHttp.Reply(401,
                "{\"success\":false,\"status_code\":7,\"status_message\":\"Invalid API key.\"}")));
        expect(TmdbAccountParse.Problem.REJECTED, () -> TmdbAccountParse.page(new AccountHttp.Reply(404,
                "{\"success\":false,\"status_code\":34}"), null));
        expect(TmdbAccountParse.Problem.RETRY, () -> TmdbAccountParse.done(new AccountHttp.Reply(429,
                "{\"status_code\":25}")));
        expect(TmdbAccountParse.Problem.RETRY, () -> TmdbAccountParse.done(new AccountHttp.Reply(503, "")));
        expect(TmdbAccountParse.Problem.RETRY, () -> TmdbAccountParse.done(new AccountHttp.Reply(502, "<html>")));
        expect(TmdbAccountParse.Problem.REJECTED, () -> TmdbAccountParse.done(new AccountHttp.Reply(400, "")));
    }

    @Test
    public void failureKeepsCodeAndMessage() {
        TmdbAccountParse.Failure f = TmdbAccountParse.failure(new AccountHttp.Reply(401,
                "{\"success\":false,\"status_code\":33,\"status_message\":\"Invalid request token.\"}"));
        assertEquals(401, f.status);
        assertEquals(33, f.code);
        assertEquals("Invalid request token.", f.getMessage());
        assertEquals("HTTP 500", TmdbAccountParse.failure(new AccountHttp.Reply(500, "")).getMessage());
    }

    @Test
    public void watchlistPage() throws Exception {
        TmdbAccountParse.Page p = TmdbAccountParse.page(ok("{\"page\":1,\"results\":["
                + "{\"adult\":false,\"id\":823754,\"title\":\"Bo Burnham: Inside\",\"media_type\":\"movie\"},"
                + "{\"id\":542178,\"title\":\"The French Dispatch\"},"
                + "{\"id\":542178,\"title\":\"Again\"},"
                + "{\"id\":0}],\"total_pages\":3,\"total_results\":42}"), "movie");
        assertEquals(Arrays.asList("movie:823754", "movie:542178"), p.ids);
        assertEquals(1, p.page);
        assertEquals(3, p.totalPages);
    }

    @Test
    public void listPageReadsEachMediaType() throws Exception {
        TmdbAccountParse.Page p = TmdbAccountParse.page(ok("{\"average_rating\":6.7,\"id\":7,\"name\":\"Household\","
                + "\"page\":1,\"results\":[{\"id\":617127,\"title\":\"Blade\",\"media_type\":\"movie\"},"
                + "{\"id\":1396,\"name\":\"Breaking Bad\",\"media_type\":\"tv\"},"
                + "{\"id\":287,\"name\":\"Brad Pitt\",\"media_type\":\"person\"}],"
                + "\"total_pages\":1,\"total_results\":3}"), null);
        assertEquals(Arrays.asList("movie:617127", "tv:1396"), p.ids);
    }

    @Test
    public void emptyPage() throws Exception {
        TmdbAccountParse.Page p = TmdbAccountParse.page(ok("{\"page\":1,\"results\":[],\"total_pages\":0}"), "tv");
        assertTrue(p.ids.isEmpty());
        assertEquals(1, p.totalPages);
    }

    @Test
    public void accountLists() throws Exception {
        TmdbAccountParse.ListPage p = TmdbAccountParse.lists(ok("{\"page\":1,\"results\":["
                + "{\"account_object_id\":\"x\",\"id\":120174,\"name\":\"Test Alpha Sort\",\"number_of_items\":6,\"public\":0},"
                + "{\"id\":8221116,\"name\":\"Household\",\"number_of_items\":4,\"public\":1},"
                + "{\"id\":9,\"name\":null,\"public\":true}],\"total_pages\":2,\"total_results\":23}"));
        assertEquals(3, p.lists.size());
        assertEquals(120174, p.lists.get(0).id);
        assertEquals("Test Alpha Sort", p.lists.get(0).name);
        assertFalse(p.lists.get(0).isPublic);
        assertEquals(6, p.lists.get(0).items);
        assertTrue(p.lists.get(1).isPublic);
        assertEquals("", p.lists.get(2).name);
        assertTrue(p.lists.get(2).isPublic);
        assertEquals(2, p.totalPages);
    }

    @Test
    public void createdListAndWrites() throws Exception {
        assertEquals(5854, TmdbAccountParse.createdList(new AccountHttp.Reply(201, "{\"status_message\":\"The item/record "
                + "was created successfully.\",\"id\":5854,\"success\":true,\"status_code\":1}")));
        TmdbAccountParse.done(new AccountHttp.Reply(201, "{\"status_code\":1,\"status_message\":\"Success.\"}"));
        // One item already on the list is fine.
        TmdbAccountParse.done(ok("{\"status_message\":\"Success.\",\"results\":[{\"media_type\":\"movie\","
                + "\"media_id\":550,\"success\":false}],\"success\":true,\"status_code\":1}"));
        expect(TmdbAccountParse.Problem.REJECTED, () -> TmdbAccountParse.done(ok("{\"success\":false,\"status_code\":21}")));
    }

    private interface Body {
        void run() throws TmdbAccountParse.Failure;
    }

    private static void expect(TmdbAccountParse.Problem problem, Body body) {
        try {
            body.run();
            fail("expected " + problem);
        } catch (TmdbAccountParse.Failure f) {
            assertEquals(problem, f.problem);
        }
    }
}
