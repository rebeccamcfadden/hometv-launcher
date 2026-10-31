package com.gathin.tvhome;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TMDB over HTTPS with a disk cache (BACKLOG.md, Real catalogue). Every answer is kept in the app's cache folder for
 * as long as {@link TmdbApi#ttl} says; a fresh copy is used without asking TMDB, and a stale one when TMDB can't be
 * reached, so what was seen once still shows offline. Images (from image.tmdb.org, which needs no token) are kept a
 * week the same way. The token goes in the Authorization header only, never in a URL, a log or the cache. Blocking:
 * call from a background thread. Plain HttpURLConnection and Android's org.json, no library.
 */
final class TmdbClient {
    /** JSON answers on disk: a title's details are about 60 KB, so this keeps a few hundred titles. */
    static final long JSON_MAX = 16L << 20;
    /** Images on disk: a library card's backdrop is about 60 KB, the preview's about 200 KB. */
    static final long IMAGES_MAX = 64L << 20;
    private static final int CONNECT_MS = 8000, READ_MS = 15000;

    private final String token;
    private final DiskCache json;
    private final DiskCache images;
    /** One lock per key, so two screens asking for the same thing at once make one request. */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    TmdbClient(String token, File cacheDir) {
        this.token = token;
        this.json = new DiskCache(new File(cacheDir, "tmdb"), JSON_MAX, System::currentTimeMillis);
        this.images = new DiskCache(new File(cacheDir, "tmdb-img"), IMAGES_MAX, System::currentTimeMillis);
    }

    /** Drops what is too old or over the limits; once a start, in the background. */
    void prune() {
        json.prune();
        images.prune();
    }

    /** The answer to a v3 path: cached while fresh, else from TMDB, else the stale copy. Throws with neither. */
    JSONObject get(String path) throws IOException {
        long ttl = TmdbApi.ttl(path);
        Object lock = locks.computeIfAbsent(path, k -> new Object());
        synchronized (lock) {
            try {
                DiskCache.Entry cached = json.read(path, ttl);
                if (cached != null && cached.fresh) {
                    try {
                        return new JSONObject(cached.text());
                    } catch (JSONException e) {
                        cached = null;
                    }
                }
                byte[] body;
                try {
                    body = fetch(TmdbApi.BASE + path, true);
                } catch (IOException e) {
                    if (cached != null) {
                        try {
                            return new JSONObject(cached.text());
                        } catch (JSONException ignored) {
                            // An unreadable copy is as good as none.
                        }
                    }
                    throw e;
                }
                JSONObject o;
                try {
                    o = new JSONObject(new String(body, StandardCharsets.UTF_8));
                } catch (JSONException e) {
                    throw new IOException("TMDB answered with something that is not JSON");
                }
                json.write(path, body);
                return o;
            } finally {
                locks.remove(path);
            }
        }
    }

    /** When the copy of a v3 path's answer on disk was fetched from TMDB, in epoch milliseconds; 0 for none. */
    long savedAt(String path) {
        return json.file(path).lastModified();
    }

    /** An image's bytes: from the disk cache, else image.tmdb.org; a week old is still used when offline. */
    byte[] image(String url) throws IOException {
        Object lock = locks.computeIfAbsent(url, k -> new Object());
        synchronized (lock) {
            try {
                DiskCache.Entry cached = images.read(url, TmdbApi.TTL_WEEK);
                if (cached != null && cached.fresh) return cached.bytes;
                try {
                    byte[] body = fetch(url, false);
                    images.write(url, body);
                    return body;
                } catch (IOException e) {
                    if (cached != null) return cached.bytes;
                    throw e;
                }
            } finally {
                locks.remove(url);
            }
        }
    }

    /**
     * One account call (AccountHttp), never cached and never logged: the answers are the household's own lists, and
     * a v3 write carries the session id in its URL. Any HTTP status comes back with its body, so the caller can read
     * TMDB's status code; only no answer at all throws. Blocking: call from a background thread.
     */
    static AccountHttp.Reply send(AccountHttp.Call call, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(call.url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setUseCaches(false);
        c.setRequestMethod(call.method.name());
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            if (call.body != null) {
                byte[] body = call.body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                c.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) return new AccountHttp.Reply(code, "");
            try (InputStream body = in) {
                return new AccountHttp.Reply(code, new String(DiskCache.readAll(body), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            c.disconnect();
            // The message can hold the URL, and with it a session id: only the kind of failure goes on.
            throw new IOException(e.getClass().getSimpleName());
        }
    }

    private byte[] fetch(String url, boolean api) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setUseCaches(false);
        if (api) {
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        try {
            int code = c.getResponseCode();
            // 429 means "slow down": the caller tries again later, with the stale copy until then.
            if (code != HttpURLConnection.HTTP_OK) {
                InputStream err = c.getErrorStream();
                if (err != null) err.close();
                throw new IOException("TMDB answered " + code);
            }
            // Read to the end and closed, not disconnected, so the next request can reuse the connection.
            try (InputStream in = c.getInputStream()) {
                return DiskCache.readAll(in);
            }
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
    }
}
