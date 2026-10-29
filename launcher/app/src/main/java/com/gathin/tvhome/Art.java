package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Loads the mock artwork (ArtNames) into ImageViews, decoded at the size it is shown: off the main thread on two
 * low-priority threads, sampled down and at 16 bits per pixel, and kept in a memory cache of up to 24 MB (an eighth
 * of the heap on a small TV). The view shows the raised background until its image is ready. Each view is tagged with
 * the image it wants, so a decode that finishes after the view was given another title (a rebuilt row or grid) is
 * dropped instead of showing the wrong art. Bitmaps are shared between views and never recycled by hand: the cache
 * only lets go of them, and the views on screen keep theirs.
 */
final class Art {
    private Art() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS,
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

    /** Decodes in flight, by cache key, with the views waiting for each. Main thread only. */
    private static final Map<String, List<ImageView>> PENDING = new HashMap<>();

    /**
     * Shows the art named {@code art} in v, cropped to fill it (centre crop), decoded for a box of widthPx by
     * heightPx screen pixels. An unknown name leaves the placeholder. Call from the main thread.
     */
    static void into(ImageView v, String art, int widthPx, int heightPx) {
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int id = ArtNames.drawable(art);
        String key = id == 0 ? null : art + "@" + widthPx + "x" + heightPx;
        Object shown = v.getTag(R.id.art_key);
        // Asked again for what it already shows or waits for (a rebind of the same title): leave it, no flash.
        if (key != null && key.equals(shown) && (v.getDrawable() instanceof BitmapDrawable || isPending(key, v))) return;
        v.setTag(R.id.art_key, key);
        if (key == null) {
            v.setImageDrawable(placeholder());
            return;
        }
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            v.setImageBitmap(cached);
            return;
        }
        v.setImageDrawable(placeholder());
        List<ImageView> waiting = PENDING.get(key);
        if (waiting != null) {
            if (!waiting.contains(v)) waiting.add(v);
            return;
        }
        waiting = new ArrayList<>(2);
        waiting.add(v);
        PENDING.put(key, waiting);
        Resources res = v.getContext().getApplicationContext().getResources();
        POOL.execute(() -> {
            Bitmap b;
            try {
                b = decode(res, id, widthPx, heightPx);
            } catch (RuntimeException | OutOfMemoryError e) {
                // Leave the placeholder rather than take Home down; the next request tries again.
                b = null;
            }
            Bitmap result = b;
            MAIN.post(() -> deliver(key, result));
        });
    }

    /** Forgets what v was waiting for and shows the placeholder, e.g. when a recycled view gets no art. */
    static void clear(ImageView v) {
        v.setTag(R.id.art_key, null);
        v.setImageDrawable(placeholder());
    }

    /** Memory pressure: drop the cache. Views on screen keep their bitmaps; the rest decode again when shown. */
    static void trim() {
        CACHE.evictAll();
    }

    private static boolean isPending(String key, ImageView v) {
        List<ImageView> waiting = PENDING.get(key);
        return waiting != null && waiting.contains(v);
    }

    private static void deliver(String key, Bitmap b) {
        List<ImageView> waiting = PENDING.remove(key);
        if (b == null || waiting == null) return;
        CACHE.put(key, b);
        for (ImageView v : waiting) {
            // The view may have been handed another title while this decoded: only the one it still wants lands.
            if (key.equals(v.getTag(R.id.art_key))) v.setImageBitmap(b);
        }
    }

    private static Bitmap decode(Resources res, int id, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, id, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = ArtSize.sampleSize(o.outWidth, o.outHeight, w, h);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeResource(res, id, o);
        if (b == null) return null;
        int[] size = ArtSize.cover(b.getWidth(), b.getHeight(), w, h);
        if (size == null) return b;
        Bitmap scaled = Bitmap.createScaledBitmap(b, size[0], size[1], true);
        // Only this thread has seen b, so it can go at once.
        if (scaled != b) b.recycle();
        return scaled;
    }

    private static ColorDrawable placeholder() {
        return new ColorDrawable(Ui.BG_RAISED);
    }
}
