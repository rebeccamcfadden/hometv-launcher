package com.gathin.tvhome;

import java.io.IOException;

/**
 * The one thing the account code needs from the network (BACKLOG.md, TMDB sign-in and watchlist sync): send a
 * {@link Call} and get its {@link Reply}. Stage 2 binds it to the catalogue's HTTP client; the tests use a fake. A
 * call names which token goes in its Authorization header ({@link Auth}) instead of holding it, so a call can be
 * logged or shown in a test failure without leaking a token. Account calls are never cached on disk: the answers
 * are the household's own lists, and v3 writes carry the session id in the URL.
 */
interface AccountHttp {
    Reply send(Call call) throws IOException;

    /** Which bearer token a call sends. */
    enum Auth {
        /** The app's read access token from tv.properties. */
        APP,
        /** The signed-in account's access token (TmdbSession.accessToken). */
        USER
    }

    enum Method { GET, POST, PUT, DELETE }

    /** A request: method, full URL, token and JSON body (null for none). */
    final class Call {
        final Method method;
        final String url;
        final Auth auth;
        final String body;

        Call(Method method, String url, Auth auth, String body) {
            this.method = method;
            this.url = url;
            this.auth = auth;
            this.body = body;
        }

        /** The method and URL with any session id hidden; the body is left out, as it can hold a token. */
        @Override
        public String toString() {
            return method + " " + url.replaceAll("session_id=[^&]*", "session_id=…") + " (" + auth + ")";
        }
    }

    /** An answer: the HTTP status and the body as text. */
    final class Reply {
        final int status;
        final String body;

        Reply(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }

        boolean ok() {
            return status >= 200 && status < 300;
        }
    }
}
