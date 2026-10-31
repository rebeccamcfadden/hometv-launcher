package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.FontRequest;
import android.provider.FontsContract;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The theme's fonts (Theme.uiFont and displayFont) from Google Fonts: through the platform's downloadable-fonts
 * provider (FontsContract, API 26, no library), or straight from Google Fonts over HTTPS when Play services can't serve
 * the family (older ones don't list newer families) or isn't on the TV. Each file is copied into filesDir/fonts/ once,
 * so every later start reads it from disk before the first view is built. Until a face is on the TV, offline, and with
 * the System font option on, the launcher shows Roboto.
 */
final class ThemeFonts {
    private ThemeFonts() {}

    private static final String AUTHORITY = "com.google.android.gms.fonts";
    private static final String PROVIDER = "com.google.android.gms";
    private static final String DIR = "fonts";
    /** A font file from the web larger than this is not a face we asked for (the largest theme font is ~200 KB). */
    private static final long MAX_FONT_BYTES = 2L * 1024 * 1024;

    /** Faces read this process, by file name, so a recreate doesn't parse them again. Main thread only. */
    private static final Map<String, Typeface> LOADED = new HashMap<>();
    /** File names being fetched now. Main thread only. */
    private static final Set<String> FETCHING = new HashSet<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable onFetched;
    private static boolean fetchedAny;
    /** No provider on this TV (no Play services): only Google Fonts is asked until the launcher restarts. */
    private static boolean noProvider;

    /**
     * Sets Ui's faces for the theme from the files on the TV, Roboto for any still missing. Call after Ui.apply and
     * before building views. False when a face still has to be fetched ({@link #fetch}).
     */
    static boolean apply(Context ctx, Theme t) {
        FontFaces.Face[] faces = FontFaces.of(t, ThemeStore.systemFont(ctx));
        Typeface[] got = new Typeface[faces.length];
        boolean all = true;
        File dir = dir(ctx);
        for (int i = 0; i < faces.length; i++) {
            if (faces[i] == null) continue;
            got[i] = load(dir, faces[i]);
            if (got[i] == null) all = false;
        }
        Ui.fonts(got[FontFaces.REGULAR], got[FontFaces.MEDIUM], got[FontFaces.BOLD], got[FontFaces.DISPLAY]);
        return all;
    }

    /**
     * Fetches the theme's faces that aren't on the TV yet, in the background, and runs then on the main thread once
     * every fetch under way is done and at least one face arrived. A later call's then replaces an earlier one's.
     * Quietly does nothing more offline: the next start tries again.
     */
    static void fetch(Context ctx, Theme t, Runnable then) {
        File dir = dir(ctx);
        List<FontFaces.Face> missing = new ArrayList<>();
        for (FontFaces.Face f : FontFaces.distinct(FontFaces.of(t, ThemeStore.systemFont(ctx)))) {
            if (!new File(dir, f.fileName()).isFile() && !FETCHING.contains(f.fileName())) missing.add(f);
        }
        onFetched = then;
        if (missing.isEmpty()) return;
        for (FontFaces.Face f : missing) FETCHING.add(f.fileName());
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            boolean any = false;
            boolean noApp = noProvider;
            List<List<byte[]>> certs = certs(app.getResources());
            for (FontFaces.Face f : missing) {
                boolean got = false;
                if (!noApp) {
                    try {
                        got = download(app, dir, certs, f);
                    } catch (PackageManager.NameNotFoundException e) {
                        noApp = true;
                    } catch (IOException | RuntimeException e) {
                        // The provider failed: Google Fonts is asked directly below.
                    }
                }
                if (!got) {
                    try {
                        got = downloadWeb(dir, f);
                    } catch (IOException | RuntimeException e) {
                        // Offline: Roboto stays, and the next start asks again.
                    }
                }
                any |= got;
            }
            boolean arrived = any, missingProvider = noApp;
            MAIN.post(() -> {
                for (FontFaces.Face f : missing) FETCHING.remove(f.fileName());
                if (missingProvider) noProvider = true;
                fetchedAny |= arrived;
                if (!FETCHING.isEmpty() || !fetchedAny) return;
                fetchedAny = false;
                Runnable r = onFetched;
                onFetched = null;
                if (r != null) r.run();
            });
        }, "theme-fonts").start();
    }

    /** Drops then if it is still waiting for fonts (its activity is going). */
    static void forget(Runnable then) {
        if (onFetched == then) onFetched = null;
    }

    private static File dir(Context ctx) {
        return new File(ctx.getFilesDir(), DIR);
    }

    private static Typeface load(File dir, FontFaces.Face f) {
        String name = f.fileName();
        Typeface cached = LOADED.get(name);
        if (cached != null) return cached;
        File file = new File(dir, name);
        if (!file.isFile() || file.length() == 0) return null;
        Typeface t = read(file);
        if (t == null) {
            // A broken file is fetched again rather than kept.
            file.delete();
            return null;
        }
        LOADED.put(name, t);
        return t;
    }

    /** The face in file, or null when it isn't one (createFromFile falls back to the default face then). */
    private static Typeface read(File file) {
        try {
            Typeface t = Typeface.createFromFile(file);
            return t == null || t == Typeface.DEFAULT ? null : t;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * One face from the provider into dir; true when it is there now. Runs off the main thread. The platform's
     * FontsContract is deprecated in favour of AndroidX's copy of it, but still works on every Android: it saves a
     * direct dependency.
     */
    @SuppressWarnings("deprecation")
    private static boolean download(Context app, File dir, List<List<byte[]>> certs, FontFaces.Face f)
            throws PackageManager.NameNotFoundException, IOException {
        FontRequest request = new FontRequest(AUTHORITY, PROVIDER, f.query(), certs);
        FontsContract.FontFamilyResult result = FontsContract.fetchFonts(app, null, request);
        if (result.getStatusCode() != FontsContract.FontFamilyResult.STATUS_OK) return false;
        for (FontsContract.FontInfo info : result.getFonts()) {
            if (info.getResultCode() != FontsContract.Columns.RESULT_CODE_OK) continue;
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File part = new File(dir, f.fileName() + ".part");
            ParcelFileDescriptor fd = app.getContentResolver().openFileDescriptor(info.getUri(), "r", null);
            if (fd == null) continue;
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd);
                 OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            if (read(part) == null || !part.renameTo(new File(dir, f.fileName()))) {
                part.delete();
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * One face straight from Google Fonts into dir: its CSS API names the TrueType file, which is downloaded and kept
     * if it reads as a font. A family without that weight is asked for the nearest one. Runs off the main thread.
     */
    private static boolean downloadWeb(File dir, FontFaces.Face f) throws IOException {
        for (int w : FontFaces.weightsToTry(f.weight)) {
            String css = fetchText(new FontFaces.Face(f.family, w).cssUrl());
            String ttf = FontFaces.ttfUrl(css);
            if (ttf == null) continue;
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File part = new File(dir, f.fileName() + ".part");
            HttpURLConnection c = open(ttf);
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                long total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX_FONT_BYTES) throw new IOException("too big");
                    out.write(buf, 0, n);
                }
            } finally {
                c.disconnect();
            }
            if (read(part) != null && part.renameTo(new File(dir, f.fileName()))) return true;
            part.delete();
            return false;
        }
        return false;
    }

    /** The CSS for one face, or null when Google Fonts doesn't have it (it answers 400 for a missing weight). */
    private static String fetchText(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8 * 1024];
                StringBuilder b = new StringBuilder();
                for (int n; (n = in.read(buf)) > 0 && b.length() < 64 * 1024; ) {
                    b.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                return b.toString();
            }
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    /** The provider's signing certificates (res/values/font_certs.xml), as FontRequest wants them. */
    private static List<List<byte[]>> certs(Resources res) {
        List<List<byte[]>> out = new ArrayList<>();
        TypedArray sets = res.obtainTypedArray(R.array.com_google_android_gms_fonts_certs);
        try {
            for (int i = 0; i < sets.length(); i++) {
                List<byte[]> set = new ArrayList<>();
                for (String cert : res.getStringArray(sets.getResourceId(i, 0))) {
                    set.add(Base64.decode(cert.trim(), Base64.DEFAULT));
                }
                out.add(set);
            }
        } finally {
            sets.recycle();
        }
        return out;
    }
}
