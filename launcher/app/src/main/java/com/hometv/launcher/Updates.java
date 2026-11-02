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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app updates on the TV (BACKLOG.md, Family install step 5; the rules are {@link UpdateRules}). Only a public
 * release build checks: once a day while Home shows, it reads the latest release's version.json, downloads a newer APK
 * into the app's cache, and keeps it only when its SHA-256 and its one signer are the release's and the signer is this
 * launcher's own. Install hands it to Android's PackageInstaller, and Android asks the person first ("Install this
 * update?"). Without the "install unknown apps" grant, About opens Android's screen to allow it. Nothing blocks Home:
 * the check and the download run on their own thread, and a failure only waits for tomorrow.
 */
final class Updates {

    static final String ACTION_INSTALL_STATUS = "com.hometv.launcher.action.UPDATE_INSTALL_STATUS";
    /** A release APK is under 1 MB (CI's limit); anything far above that is not one. */
    private static final int MAX_APK_BYTES = 8 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 64 * 1024;
    private static final int CONNECT_MS = 10000, READ_MS = 20000;

    private static final String PREFS = "updates";
    private static final String K_CHECK = "check_on", K_LAST = "last_check", K_FAILED = "last_failed",
            K_CODE = "ready_code", K_NAME = "ready_name", K_FILE = "ready_file", K_NOTICED = "noticed";

    private static Updates instance;

    private final Context app;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "updates");
        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true);
        return t;
    });
    private final List<Runnable> listeners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;

    static synchronized Updates get(Context ctx) {
        if (instance == null) instance = new Updates(ctx.getApplicationContext());
        return instance;
    }

    private Updates(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

    /** The downloaded, accepted update, or null: only while it is newer than what runs. */
    String readyVersion() {
        File f = readyFile();
        return f != null ? prefs.getString(K_NAME, null) : null;
    }

    private File readyFile() {
        String path = prefs.getString(K_FILE, null);
        if (path == null || prefs.getInt(K_CODE, 0) <= BuildConfig.VERSION_CODE) return null;
        File f = new File(path);
        return f.isFile() ? f : null;
    }

    boolean canInstall() {
        return app.getPackageManager().canRequestPackageInstalls();
    }

    /** Whether this TV has Android's "install unknown apps" screen for the launcher. */
    boolean hasGrantScreen() {
        return grantIntent().resolveActivity(app.getPackageManager()) != null;
    }

    UpdateRules.Status status() {
        return UpdateRules.status(BuildConfig.PUBLIC_RELEASE, checkOn(), running, readyFile() != null,
                prefs.getBoolean(K_FAILED, false), canInstall(), hasGrantScreen());
    }

    /** Home's notice for this version, once: true the first time it is asked while an update waits. */
    boolean takeNotice() {
        String ready = readyVersion();
        if (!UpdateRules.notice(status(), ready, prefs.getString(K_NOTICED, ""))) return false;
        prefs.edit().putString(K_NOTICED, ready).apply();
        return true;
    }

    /** Home showed: checks if a day has passed. Never blocks the caller. */
    void maybeCheck() {
        if (running || !UpdateRules.checkDue(BuildConfig.PUBLIC_RELEASE, checkOn(), lastCheck(), System.currentTimeMillis())) return;
        checkNow();
    }

    /** About's Check now, and the daily check. */
    void checkNow() {
        if (!BuildConfig.PUBLIC_RELEASE || running) return;
        running = true;
        changed();
        worker.execute(() -> {
            boolean failed = false;
            try {
                check();
            } catch (IOException | RuntimeException e) {
                failed = true;
            } finally {
                prefs.edit().putLong(K_LAST, System.currentTimeMillis()).putBoolean(K_FAILED, failed).apply();
                running = false;
                changed();
            }
        });
    }

    private void check() throws IOException {
        UpdateRules.Release r = UpdateRules.parse(new String(fetch(UpdateRules.LATEST_VERSION_JSON, MAX_JSON_BYTES, null),
                StandardCharsets.UTF_8));
        if (r == null) throw new IOException("version.json unreadable");
        if (!UpdateRules.isUpdate(r, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)) {
            dropReady();
            return;
        }
        if (r.versionCode == prefs.getInt(K_CODE, 0) && readyFile() != null) return;
        File dir = new File(app.getCacheDir(), "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no cache folder");
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        File part = new File(dir, r.apk + ".part");
        String sha = UpdateRules.hex(fetchToFile(r.apkUrl(), part));
        List<String> apkSigners = signers(app.getPackageManager(), part.getPath());
        List<String> own = signers(app.getPackageManager(), null);
        if (!UpdateRules.acceptable(r, sha, apkSigners, own)) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new IOException("update refused: hash or signer");
        }
        File apk = new File(dir, r.apk);
        if (!part.renameTo(apk)) throw new IOException("rename failed");
        prefs.edit().putInt(K_CODE, r.versionCode).putString(K_NAME, r.versionName).putString(K_FILE, apk.getPath()).apply();
    }

    private void dropReady() {
        String path = prefs.getString(K_FILE, null);
        if (path != null) //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        prefs.edit().remove(K_CODE).remove(K_NAME).remove(K_FILE).apply();
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "HomeTV-Launcher/" + BuildConfig.VERSION_NAME);
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        return c;
    }

    private static byte[] fetch(String url, int max, MessageDigest md) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out, max, md);
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
        } catch (java.security.NoSuchAlgorithmException e) {
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

    /** About's Install update without the grant: Android's own screen, where the person switches the launcher on. */
    void openGrantScreen(Context ctx) {
        ctx.startActivity(grantIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /**
     * About's Install update: the downloaded APK goes into a PackageInstaller session, and Android answers on
     * {@link StatusReceiver} with its own "Install this update?" screen. The launcher is replaced and starts again.
     */
    void install() throws IOException {
        File apk = readyFile();
        if (apk == null) throw new IOException("no update ready");
        PackageInstaller installer = app.getPackageManager().getPackageInstaller();
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
            Intent status = new Intent(ACTION_INSTALL_STATUS).setPackage(BuildConfig.APPLICATION_ID);
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
