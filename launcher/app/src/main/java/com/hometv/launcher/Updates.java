package com.hometv.launcher;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * In-app updates on the TV (BACKLOG.md, Family install step 5; the rules are {@link UpdateRules}). A public release
 * build checks once a day while Home shows: it reads the latest release's version.json, and when a newer version is out
 * About says so and Home shows one notice for it. Nothing is downloaded until the person presses Install (the project
 * lead, 2026-11-02). Then the APK goes into the app's cache and is kept only when its SHA-256 and its one signer are the
 * release's and the signer is this launcher's own; it goes to Android's PackageInstaller, and Android asks the person
 * first. Without the "install unknown apps" grant, Install opens Android's screen to allow it. Nothing blocks Home: the
 * network work runs on its own thread, and a failed check waits for tomorrow.
 *
 * <p>A debug build given {@code -PupdateFeed=<url>} checks that feed instead of GitHub, for testing on an emulator
 * (build.gradle refuses it with a release key or a public release).
 */
final class Updates {

    private static final String TAG = "Updates";
    static final String ACTION_INSTALL_STATUS = "com.hometv.launcher.action.UPDATE_INSTALL_STATUS";
    /** A release APK is under 1 MB (CI's limit); anything far above that is not one. */
    private static final int MAX_APK_BYTES = 8 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 64 * 1024;
    private static final int CONNECT_MS = 10000, READ_MS = 20000;

    private static final String PREFS = "updates";
    private static final String K_CHECK = "check_on", K_LAST = "last_check", K_FAILED = "last_failed",
            K_JSON = "available_json", K_NOTICED = "noticed";

    private static Updates instance;

    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "updates");
        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true);
        return t;
    });
    private final List<Runnable> listeners = new ArrayList<>();
    private volatile boolean checking;
    private volatile boolean downloading;

    static synchronized Updates get(Context ctx) {
        if (instance == null) instance = new Updates(ctx.getApplicationContext());
        return instance;
    }

    private Updates(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether this build checks at all: a public release, or a debug build with a test feed. */
    static boolean enabled() {
        return BuildConfig.PUBLIC_RELEASE || !BuildConfig.UPDATE_FEED.isEmpty();
    }

    void listen(Runnable r) {
        synchronized (listeners) {
            listeners.add(r);
        }
    }

    void unlisten(Runnable r) {
        synchronized (listeners) {
            listeners.remove(r);
        }
    }

    private void changed() {
        List<Runnable> copy;
        synchronized (listeners) {
            copy = new ArrayList<>(listeners);
        }
        for (Runnable r : copy) main.post(r);
    }

    boolean checkOn() {
        return prefs.getBoolean(K_CHECK, true);
    }

    void setCheckOn(boolean on) {
        prefs.edit().putBoolean(K_CHECK, on).apply();
        changed();
    }

    long lastCheck() {
        return prefs.getLong(K_LAST, 0);
    }

    boolean checking() {
        return checking;
    }

    /** The newer release found by the last check, or null (also once it is installed). */
    UpdateRules.Release available() {
        UpdateRules.Release r = UpdateRules.parse(prefs.getString(K_JSON, null));
        return UpdateRules.isUpdate(r, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT) ? r : null;
    }

    boolean canInstall() {
        return app.getPackageManager().canRequestPackageInstalls();
    }

    /** Whether this TV has Android's "install unknown apps" screen for the launcher. */
    boolean hasGrantScreen() {
        return grantIntent().resolveActivity(app.getPackageManager()) != null;
    }

    UpdateRules.Status status() {
        return UpdateRules.status(enabled(), checkOn(), downloading, available() != null,
                prefs.getBoolean(K_FAILED, false), canInstall(), hasGrantScreen());
    }

    /** Home's notice for this version, once: true the first time it is asked while a newer one is out. */
    boolean takeNotice() {
        UpdateRules.Release r = available();
        String version = r != null ? r.versionName : null;
        if (!UpdateRules.notice(status(), version, prefs.getString(K_NOTICED, ""))) return false;
        prefs.edit().putString(K_NOTICED, version).apply();
        return true;
    }

    /** Home showed: checks if a day has passed. Never blocks the caller. */
    void maybeCheck() {
        if (checking || !UpdateRules.checkDue(enabled(), checkOn(), lastCheck(), System.currentTimeMillis())) return;
        checkNow();
    }

    /** About's Check now, and the daily check: reads version.json only; nothing is downloaded. */
    void checkNow() {
        if (!enabled() || checking) return;
        checking = true;
        changed();
        worker.execute(() -> {
            boolean failed = false;
            try {
                String json = new String(fetch(UpdateRules.versionJsonUrl(BuildConfig.UPDATE_FEED)), StandardCharsets.UTF_8);
                UpdateRules.Release r = UpdateRules.parse(json);
                if (r == null) throw new IOException("version.json unreadable");
                SharedPreferences.Editor e = prefs.edit();
                if (UpdateRules.isUpdate(r, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)) e.putString(K_JSON, json);
                else e.remove(K_JSON);
                e.apply();
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "update check failed", e);
                failed = true;
            } finally {
                prefs.edit().putLong(K_LAST, System.currentTimeMillis()).putBoolean(K_FAILED, failed).apply();
                checking = false;
                changed();
            }
        });
    }

    /**
     * About's Install update. Without the grant it opens Android's screen to allow it (when the TV has one). With it,
     * the APK is downloaded and checked, then handed to Android, which asks the person; {@code done} hears false on the
     * main thread when the download or its checks fail.
     */
    void install(Context ui, Consumer<Boolean> done) {
        UpdateRules.Release r = available();
        if (r == null || downloading) {
            Log.i(TAG, "install ignored: " + (r == null ? "no update" : "already downloading"));
            return;
        }
        if (!canInstall()) {
            Log.i(TAG, "install: no grant, " + (hasGrantScreen() ? "opening the grant screen" : "no grant screen"));
            if (hasGrantScreen()) ui.startActivity(grantIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        downloading = true;
        changed();
        worker.execute(() -> {
            boolean ok = false;
            try {
                commit(download(r));
                ok = true;
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "update install failed", e);
                ok = false;
            } finally {
                downloading = false;
                changed();
                boolean result = ok;
                main.post(() -> done.accept(result));
            }
        });
    }

    /** The release's APK in the cache, checked: its SHA-256, one signer, the release's certificate and our own signer. */
    private File download(UpdateRules.Release r) throws IOException {
        File dir = new File(app.getCacheDir(), "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no cache folder");
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        File apk = new File(dir, r.apk);
        String sha;
        try {
            sha = UpdateRules.hex(fetchToFile(r.apkUrl(BuildConfig.UPDATE_FEED), apk));
        } catch (HttpStatus e) {
            throw e;
        } catch (IOException e) {
            // A dropped or stale connection: once more, on a fresh one.
            sha = UpdateRules.hex(fetchToFile(r.apkUrl(BuildConfig.UPDATE_FEED), apk));
        }
        PackageManager pm = app.getPackageManager();
        if (!UpdateRules.acceptable(r, sha, signers(pm, apk.getPath()), signers(pm, null))) {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
            throw new IOException("update refused: hash or signer");
        }
        return apk;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "HomeTV-Launcher/" + BuildConfig.VERSION_NAME);
        // One request a day: nothing to gain from a pooled connection, and reusing one the server has since closed is
        // what failed an Install on the emulator ("unexpected end of stream" before the request left).
        c.setRequestProperty("Connection", "close");
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect();
            throw new HttpStatus(code);
        }
        return c;
    }

    /** The server answered, with something other than 200: no point asking again. */
    private static final class HttpStatus extends IOException {
        HttpStatus(int code) {
            super("HTTP " + code);
        }
    }

    private static byte[] fetch(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out, MAX_JSON_BYTES, null);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    /** Downloads to {@code file} and returns its SHA-256, read as it is written. */
    private static byte[] fetchToFile(String url, File file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(file)) {
            copy(in, out, MAX_APK_BYTES, md);
        } finally {
            c.disconnect();
        }
        return md.digest();
    }

    private static void copy(InputStream in, OutputStream out, int max, MessageDigest md) throws IOException {
        byte[] buf = new byte[16 * 1024];
        int total = 0, n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException("too large");
            out.write(buf, 0, n);
            if (md != null) md.update(buf, 0, n);
        }
    }

    /**
     * The SHA-256 of each signing certificate (lowercase hex): of the APK at {@code archive}, or of this launcher when it
     * is null. These are the digests apksigner prints and release.ps1 pins.
     */
    @SuppressLint("PackageManagerGetSignatures")
    @SuppressWarnings("deprecation")
    static List<String> signers(PackageManager pm, String archive) {
        Signature[] sigs = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo pi = archive == null
                        ? pm.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_SIGNING_CERTIFICATES)
                        : pm.getPackageArchiveInfo(archive, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi != null && pi.signingInfo != null) sigs = pi.signingInfo.getApkContentsSigners();
            }
            if (sigs == null) {
                // Android 8 and 8.1, and Android 9 when it reads no signing info from an archive.
                PackageInfo pi = archive == null
                        ? pm.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_SIGNATURES)
                        : pm.getPackageArchiveInfo(archive, PackageManager.GET_SIGNATURES);
                if (pi != null) sigs = pi.signatures;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return Collections.emptyList();
        }
        if (sigs == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Signature s : sigs) out.add(UpdateRules.sha256Hex(s.toByteArray()));
        return out;
    }

    private Intent grantIntent() {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + BuildConfig.APPLICATION_ID));
    }

    /** The checked APK goes into a PackageInstaller session; Android answers on {@link StatusReceiver}. */
    private void commit(File apk) throws IOException {
        PackageInstaller installer = app.getPackageManager().getPackageInstaller();
        // A prompt lost earlier (the person left, or Android dropped it) leaves its session open: one session at a time.
        for (PackageInstaller.SessionInfo old : installer.getMySessions()) {
            try {
                installer.abandonSession(old.getSessionId());
            } catch (SecurityException ignored) {
                // Already finished or not ours any more.
            }
        }
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(BuildConfig.APPLICATION_ID);
        params.setSize(apk.length());
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("update.apk", 0, apk.length())) {
                copy(in, out, MAX_APK_BYTES, null);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            Intent status = new Intent(ACTION_INSTALL_STATUS).setClass(app, StatusReceiver.class);
            session.commit(PendingIntent.getBroadcast(app, id, status, flags).getIntentSender());
        } catch (IOException | RuntimeException e) {
            installer.abandonSession(id);
            throw e;
        }
    }

    /** PackageInstaller's answers: its confirmation screen to show, or how the install ended. */
    public static final class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                @SuppressWarnings("deprecation") Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                get(ctx).changed();
            }
        }
    }
}
