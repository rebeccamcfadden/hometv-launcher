package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Home's weather from Open-Meteo (no key), for the location and unit in launcher/tv.properties (BuildConfig). The
 * last good answer is kept in the app's files and shown at once; Home asks for a fresh one when it comes into view
 * and the answer is over 30 minutes old. Nothing polls in the background, and nothing waits on the network: offline
 * Home shows the cached answer, or no weather at all when there has never been one.
 */
final class WeatherStore {
    interface Listener {
        void onWeather(Weather weather);
    }

    private static final String TAG = "TvHome.Weather";
    private static final String FILE = "weather.json";
    static final long MAX_AGE_MS = 30 * 60 * 1000L;
    /** A failed fetch (offline) is not tried again sooner than this, however often Home comes back. */
    private static final long RETRY_MS = 2 * 60 * 1000L;
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BYTES = 256 * 1024;

    private final File file;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String query;
    private final Listener listener;
    private Weather weather;
    private long fetchedAt;
    private long triedAt;
    private boolean fetching;
    /** A retry asked for while a fetch was running: fetch again if that one fails. */
    private boolean retryAfter;
    private boolean loaded;

    WeatherStore(Context ctx, Listener listener) {
        this.file = new File(ctx.getFilesDir(), FILE);
        this.listener = listener;
        query = query(BuildConfig.WEATHER_LATITUDE, BuildConfig.WEATHER_LONGITUDE, BuildConfig.FAHRENHEIT);
    }

    /** The Open-Meteo request; the cache is only used for the same one, so a new location starts fresh. */
    static String query(double lat, double lon, boolean fahrenheit) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,is_day"
                + "&hourly=temperature_2m,weather_code,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=3%s", lat, lon, fahrenheit ? "&temperature_unit=fahrenheit" : "");
    }

    Weather current() {
        return weather;
    }

    /**
     * Home came into view: the cached answer first (read off the main thread the first time), then a fresh one if
     * it is stale. Results arrive through the listener on the main thread.
     */
    void refresh() {
        if (!loaded) {
            loaded = true;
            new Thread(() -> {
                Cached c = read();
                main.post(() -> {
                    if (c != null && weather == null) {
                        weather = c.weather;
                        fetchedAt = c.fetchedAt;
                        listener.onWeather(weather);
                    }
                    fetchIfStale();
                });
            }, "weather-cache").start();
            return;
        }
        fetchIfStale();
    }

    /**
     * The network came back, or the clock was set (a TV that boots at the epoch fails every HTTPS request until
     * then: the certificates aren't valid yet). Try again now rather than after the retry pause; if a fetch is
     * running, fetch again once it fails.
     */
    void retry() {
        triedAt = 0;
        if (fetching) {
            retryAfter = true;
            return;
        }
        if (loaded) fetchIfStale();
    }

    /**
     * Whether a change on the nav means the weather should be fetched again now: the internet came back (a network
     * validated, which the icon does not show) or the network changed. Not on the first state a listener is told.
     */
    static boolean networkBack(int lastIcon, int icon, boolean hadInternet, boolean internet) {
        if (lastIcon == 0) return false;
        return (internet && !hadInternet) || icon != lastIcon;
    }

    private void fetchIfStale() {
        long now = System.currentTimeMillis();
        boolean fresh = weather != null && now >= fetchedAt && now - fetchedAt < MAX_AGE_MS;
        if (fresh || fetching || (triedAt != 0 && now >= triedAt && now - triedAt < RETRY_MS)) return;
        fetching = true;
        triedAt = now;
        new Thread(() -> {
            String body = download(query);
            Weather w = null;
            if (body != null) {
                try {
                    w = Weather.parse(body);
                    write(body);
                } catch (JSONException e) {
                    Log.w(TAG, "unexpected answer: " + e.getMessage());
                }
            }
            Weather result = w;
            main.post(() -> {
                fetching = false;
                if (result == null) {
                    if (retryAfter) {
                        retryAfter = false;
                        triedAt = 0;
                        fetchIfStale();
                    }
                    return;
                }
                retryAfter = false;
                weather = result;
                fetchedAt = System.currentTimeMillis();
                listener.onWeather(weather);
            });
        }, "weather-fetch").start();
    }

    private static String download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + c.getResponseCode());
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > MAX_BYTES) throw new IOException("answer too large");
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            // Offline, a timeout, DNS: the cached answer stays.
            Log.i(TAG, "no fresh weather: " + e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static final class Cached {
        final Weather weather;
        final long fetchedAt;

        Cached(Weather weather, long fetchedAt) {
            this.weather = weather;
            this.fetchedAt = fetchedAt;
        }
    }

    /** {"query": the request, "fetched": ms, "data": the answer}. */
    private void write(String body) {
        AtomicFile af = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            JSONObject o = new JSONObject();
            o.put("query", query);
            o.put("fetched", System.currentTimeMillis());
            o.put("data", new JSONObject(body));
            out = af.startWrite();
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            af.finishWrite(out);
        } catch (IOException | JSONException e) {
            if (out != null) af.failWrite(out);
            Log.w(TAG, "cannot cache: " + e);
        }
    }

    private Cached read() {
        if (!file.exists()) return null;
        try {
            JSONObject o = new JSONObject(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
            if (!query.equals(o.optString("query"))) return null;
            return new Cached(Weather.parse(o.getJSONObject("data").toString()), o.optLong("fetched"));
        } catch (IOException | JSONException e) {
            Log.w(TAG, "cache ignored: " + e);
            return null;
        }
    }
}
