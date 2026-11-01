package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An app tile's art: the app's TV banner (16:9), cropped to the tile, or its icon where it has none. Drawn off the
 * main thread into a bitmap of exactly the size shown, so a full All Apps grid holds a few MB rather than every
 * app's full-size drawables, and kept in a small cache for the next time the tile is built.
 */
final class AppArt {
    private AppArt() {}

    interface Callback {
        void art(Bitmap banner, Bitmap icon);
    }

    private static final class Art {
        final Bitmap banner;
        final Bitmap icon;

        Art(Bitmap banner, Bitmap icon) {
            this.banner = banner;
            this.icon = icon;
        }

        int bytes() {
            return (banner != null ? banner.getByteCount() : 0) + (icon != null ? icon.getByteCount() : 0);
        }
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "app-art");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Art> CACHE = new LruCache<String, Art>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Art value) {
            return Math.max(1, value.bytes());
        }
    };

    /** Calls back on the main thread, at once when the art is cached. */
    static void load(Context context, String pkg, int width, int height, int iconSize, Callback cb) {
        String key = pkg + '@' + width + 'x' + height;
        Art cached = CACHE.get(key);
        if (cached != null) {
            cb.art(cached.banner, cached.icon);
            return;
        }
        Context app = context.getApplicationContext();
        int asked = version, askedTrim = trims;
        WORKER.execute(() -> {
            Art art = draw(app, pkg, width, height, iconSize);
            if (art == null) return;
            MAIN.post(() -> {
                // Drawn from an app that has changed since (forget), or asked for before a memory trim (clear):
                // shown this once, but not kept.
                if (asked == version && askedTrim == trims) CACHE.put(key, art);
                cb.art(art.banner, art.icon);
            });
        });
    }

    /** Bumped whenever an app's cached art is let go as stale; the tiles built before then may show old art. */
    private static int version;
    /** Bumped by each memory trim, so loads asked for before it don't fill the cache again. Main thread. */
    private static int trims;

    /** Changes each time an app's art went stale: Home's row and All Apps rebuild their tiles when it differs. */
    static int version() {
        return version;
    }

    /** Forgets everything (memory pressure): nothing goes stale, the tiles on screen keep their art. */
    static void clear() {
        trims++;
        CACHE.evictAll();
    }

    /** Forgets one app's art: it was installed, updated, changed or removed, and may have a new banner. */
    static void forget(String pkg) {
        version++;
        String prefix = pkg + '@';
        for (String key : CACHE.snapshot().keySet()) if (key.startsWith(prefix)) CACHE.remove(key);
    }

    private static Art draw(Context ctx, String pkg, int width, int height, int iconSize) {
        PackageManager pm = ctx.getPackageManager();
        try {
            ActivityInfo activity = entry(pm, pkg);
            Drawable banner = activity != null ? activity.loadBanner(pm) : pm.getApplicationBanner(pkg);
            if (banner != null) return new Art(crop(banner, width, height), null);
            Drawable icon = activity != null ? activity.loadIcon(pm) : pm.getApplicationIcon(pkg);
            return new Art(null, crop(icon, iconSize, iconSize));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /** The activity the tile opens: the TV entry point, else the phone one. */
    private static ActivityInfo entry(PackageManager pm, String pkg) {
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(pkg);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) return ri.activityInfo;
        }
        return null;
    }

    /** The drawable scaled to cover width × height, centred, the overflow cut off. */
    private static Bitmap crop(Drawable d, int width, int height) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            d.setBounds(0, 0, width, height);
        } else {
            float scale = Math.max(width / (float) iw, height / (float) ih);
            int w = Math.round(iw * scale), h = Math.round(ih * scale);
            int left = (width - w) / 2, top = (height - h) / 2;
            d.setBounds(left, top, left + w, top + h);
        }
        d.draw(c);
        return b;
    }
}
