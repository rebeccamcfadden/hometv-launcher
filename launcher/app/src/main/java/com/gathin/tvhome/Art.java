package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Loads TMDB's artwork and portraits into ImageViews (BACKLOG.md, Real catalogue), decoded at the size they are
 * shown: the image at the TMDB size nearest the box ({@link TmdbApi#backdrop}, {@link TmdbApi#profile}), from the
 * disk cache or the network ({@link TmdbClient#image}), off the main thread on three low-priority threads, sampled
 * down and at 16 bits per pixel, and kept in a memory cache of up to 24 MB (an eighth of the heap on a small TV).
 * The view shows the raised background until its image is ready, and for good when there is none (the mock
 * catalogue, or offline with nothing cached).
 *
 * <p>Each view is tagged with the image it wants, so an image that arrives after the view was given another title
 * (a rebuilt row or grid) is dropped instead of showing the wrong art, and a load nobody waits for any more is
 * skipped before it starts. Bitmaps are shared between views and never recycled by hand: the cache only lets go of
 * them, and the views on screen keep theirs.
 */
final class Art {
    private Art() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(3, 3, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "art");
        t.setDaemon(true);
        return t;
    });

    static {
        // Idle threads go away instead of sitting on a 1 GB TV's memory between screens.
        POOL.allowCoreThreadTimeOut(true);
    }

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) Math.min(24L << 20, Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /** Loads in flight, by cache key, with the views waiting for each. Main thread only. */
    private static final Map<String, List<ImageView>> PENDING = new HashMap<>();
    /** The keys some view still waits for; a worker skips a key that has left it. */
    private static final Set<String> WANTED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static volatile TmdbClient client;

    /** The real catalogue hands over its client; without one (the mock catalogue) every view keeps the placeholder. */
    static void init(TmdbClient c) {
        client = c;
    }

    /**
     * Shows a title's art (a TMDB backdrop path) in v, cropped to fill it (centre crop), loaded for a box of widthPx
     * by heightPx screen pixels. Anything else, a mock name included, leaves the placeholder. Main thread only.
     */
    static void into(ImageView v, String art, int widthPx, int heightPx) {
        bind(v, TmdbApi.backdrop(art, widthPx), widthPx, heightPx, false);
    }

    /**
     * A person's portrait (a TMDB profile path), the same way, but transparent until it is ready (or for good), so
     * the initials under it show meanwhile.
     */
    static void photo(ImageView v, String path, int widthPx, int heightPx) {
        bind(v, TmdbApi.profile(path, widthPx), widthPx, heightPx, true);
    }

    private static void bind(ImageView v, String url, int widthPx, int heightPx, boolean clear) {
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String key = url == null || client == null ? null : url + "@" + widthPx + "x" + heightPx;
        Object shown = v.getTag(R.id.art_key);
        // Asked again for what it already shows or waits for (a rebind of the same title): leave it, no flash.
        if (key != null && key.equals(shown) && (v.getDrawable() instanceof BitmapDrawable || isPending(key, v))) return;
        if (shown instanceof String && !shown.equals(key)) forget((String) shown, v);
        v.setTag(R.id.art_key, key);
        Drawable empty = clear ? null : placeholder();
        if (key == null) {
            v.setImageDrawable(empty);
            return;
        }
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            v.setImageBitmap(cached);
            return;
        }
        v.setImageDrawable(empty);
        List<ImageView> waiting = PENDING.get(key);
        WANTED.add(key);
        if (waiting != null) {
            if (!waiting.contains(v)) waiting.add(v);
            return;
        }
        waiting = new ArrayList<>(2);
        waiting.add(v);
        PENDING.put(key, waiting);
        start(key, url, widthPx, heightPx);
    }

    private static void start(String key, String url, int w, int h) {
        TmdbClient c = client;
        POOL.execute(() -> {
            if (!WANTED.contains(key)) {
                MAIN.post(() -> skipped(key, url, w, h));
                return;
            }
            Bitmap b;
            try {
                b = decode(c.image(url), w, h);
            } catch (Exception | OutOfMemoryError e) {
                // Offline with nothing cached, or a broken image: leave the placeholder; the next bind tries again.
                b = null;
            }
            Bitmap result = b;
            MAIN.post(() -> deliver(key, result));
        });
    }

    /** Forgets what v was waiting for and shows the placeholder, e.g. when a recycled view gets no art. */
    static void clear(ImageView v) {
        Object shown = v.getTag(R.id.art_key);
        if (shown instanceof String) forget((String) shown, v);
        v.setTag(R.id.art_key, null);
        v.setImageDrawable(placeholder());
    }

    /** Memory pressure: drop the cache. Views on screen keep their bitmaps; the rest load again when shown. */
    static void trim() {
        CACHE.evictAll();
    }

    private static boolean isPending(String key, ImageView v) {
        List<ImageView> waiting = PENDING.get(key);
        return waiting != null && waiting.contains(v);
    }

    /** v no longer wants key; when nobody does, a worker that hasn't started on it yet skips it. */
    private static void forget(String key, ImageView v) {
        List<ImageView> waiting = PENDING.get(key);
        if (waiting == null) return;
        waiting.remove(v);
        if (waiting.isEmpty()) WANTED.remove(key);
    }

    /** A skipped load: done, unless a view asked for it again in the meantime. */
    private static void skipped(String key, String url, int w, int h) {
        List<ImageView> waiting = PENDING.get(key);
        if (waiting == null || waiting.isEmpty()) {
            PENDING.remove(key);
            WANTED.remove(key);
            return;
        }
        WANTED.add(key);
        start(key, url, w, h);
    }

    private static void deliver(String key, Bitmap b) {
        List<ImageView> waiting = PENDING.remove(key);
        WANTED.remove(key);
        if (b == null) return;
        CACHE.put(key, b);
        if (waiting == null) return;
        for (ImageView v : waiting) {
            // The view may have been handed another title while this loaded: only the one it still wants lands.
            if (key.equals(v.getTag(R.id.art_key))) v.setImageBitmap(b);
        }
    }

    private static Bitmap decode(byte[] data, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = ArtSize.sampleSize(o.outWidth, o.outHeight, w, h);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (b == null) return null;
        int[] size = ArtSize.cover(b.getWidth(), b.getHeight(), w, h);
        if (size == null) return b;
        Bitmap scaled = Bitmap.createScaledBitmap(b, size[0], size[1], true);
        // Only this thread has seen b, so it can go at once.
        if (scaled != b) b.recycle();
        return scaled;
    }

    private static Drawable placeholder() {
        return new ColorDrawable(Ui.BG_RAISED);
    }
}
