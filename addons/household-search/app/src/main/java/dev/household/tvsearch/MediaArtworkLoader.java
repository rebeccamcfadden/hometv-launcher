package dev.household.tvsearch;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import dev.household.tvsearch.domain.MediaEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Offline artwork only; remote posters keep their placeholder rather than adding a core network path. */
final class MediaArtworkLoader implements AutoCloseable {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(48));
    private final LruCache<String, Drawable> cache = new LruCache<String, Drawable>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Drawable image) {
            return image instanceof BitmapDrawable ? ((BitmapDrawable) image).getBitmap().getAllocationByteCount() : 16384;
        }
    };
    private volatile boolean closed;

    MediaArtworkLoader(Context context) { this.context = context.getApplicationContext(); }

    void bind(MediaEntity entity, ImageView image, View icon, View label) {
        Object request = new Object();
        image.setTag(request);
        image.setImageDrawable(null);
        icon.setVisibility(View.VISIBLE);
        label.setVisibility(View.VISIBLE);
        if ("fixture:lantern".equals(entity.artworkUri)) {
            image.setImageResource(R.drawable.poster_lantern);
            icon.setVisibility(View.GONE);
            label.setVisibility(View.GONE);
            return;
        }
        boolean app = entity.type == MediaEntity.Type.APP;
        String key = app ? "app:" + entity.providerPackage : entity.artworkUri;
        if (key == null || (!app && !key.startsWith("content:") && !key.startsWith("android.resource:"))) return;
        Drawable cached = cache.get(key);
        if (cached != null) {
            image.setImageDrawable(cached);
            icon.setVisibility(View.GONE);
            label.setVisibility(View.GONE);
            return;
        }
        try {
            worker.execute(() -> {
                try {
                    Drawable loaded = app ? context.getPackageManager().getApplicationIcon(entity.providerPackage)
                            : localImage(key);
                    if (closed) return;
                    cache.put(key, loaded);
                    main.post(() -> {
                        if (closed || image.getTag() != request) return;
                        image.setImageDrawable(loaded);
                        icon.setVisibility(View.GONE);
                        label.setVisibility(View.GONE);
                    });
                } catch (IOException | PackageManager.NameNotFoundException | SecurityException | IllegalArgumentException e) {
                    Log.w("HouseholdSearch", "Local artwork unavailable: " + e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            Log.w("HouseholdSearch", "Artwork queue is full or closed; keeping placeholder");
        }
    }

    private Drawable localImage(String value) throws IOException {
        byte[] bytes;
        try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(value));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("No artwork stream");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > 2 * 1024 * 1024) throw new IOException("Artwork exceeds read limit");
                out.write(buffer, 0, count);
            }
            bytes = out.toByteArray();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 32768 || options.outHeight > 32768)
            throw new IOException("Invalid artwork dimensions");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 640) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Artwork could not be decoded");
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    @Override public void close() {
        closed = true;
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        cache.evictAll();
    }
}
