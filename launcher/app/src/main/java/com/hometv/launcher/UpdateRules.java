package com.hometv.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * In-app updates (BACKLOG.md, Family install step 5), the rules without Android: which release a launcher from
 * tools/release.ps1 may take, where its files are, when to check, and what About says. Only a public release build
 * checks ({@code BuildConfig.PUBLIC_RELEASE}); every other build updates from the computer it was built on.
 *
 * <p>A release is read from its {@code version.json} ({@code tools/release.ps1}): the APK is fetched from the same tag,
 * never from "latest", so the file can't change between the two reads, and it is taken only when its SHA-256 is the one
 * {@code version.json} names and its one signer is both the release certificate {@code version.json} names and the
 * installed launcher's own signer. Android checks the signer again when it installs, and asks the person first.
 */
final class UpdateRules {

    private UpdateRules() {}

    static final String REPO = "rebeccamcfadden/hometv-launcher";
    static final String LATEST_VERSION_JSON = "https://github.com/" + REPO + "/releases/latest/download/version.json";
    /** Once a day while Home shows. */
    static final long CHECK_EVERY_MS = 24L * 60 * 60 * 1000;

    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern VERSION_NAME = Pattern.compile("[0-9][0-9A-Za-z.\\-]{0,31}");
    private static final Pattern APK_NAME = Pattern.compile("hometv-launcher-v[0-9][0-9A-Za-z.\\-]{0,31}\\.apk");

    /** One release, as its version.json describes it. */
    static final class Release {
        final int versionCode;
        final String versionName;
        final String apk;
        final String sha256;
        final String certSha256;
        final int minSdk;

        Release(int versionCode, String versionName, String apk, String sha256, String certSha256, int minSdk) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apk = apk;
            this.sha256 = sha256;
            this.certSha256 = certSha256;
            this.minSdk = minSdk;
        }

        String tag() {
            return "v" + versionName;
        }

        /** The APK, from this release's own tag. */
        String apkUrl() {
            return "https://github.com/" + REPO + "/releases/download/" + tag() + "/" + apk;
        }
    }

    /** A version.json, or null when anything in it is missing or not what release.ps1 writes. */
    static Release parse(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            int code = o.getInt("versionCode");
            String name = o.getString("versionName");
            String apk = o.getString("apk");
            String sha = o.getString("sha256").toLowerCase(Locale.ROOT);
            String cert = o.getString("certSha256").toLowerCase(Locale.ROOT);
            int minSdk = o.optInt("minSdk", 26);
            if (code <= 0 || !VERSION_NAME.matcher(name).matches()) return null;
            if (!APK_NAME.matcher(apk).matches() || !apk.equals("hometv-launcher-v" + name + ".apk")) return null;
            if (!HEX64.matcher(sha).matches() || !HEX64.matcher(cert).matches()) return null;
            return new Release(code, name, apk, sha, cert, minSdk);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Whether this release is an update this TV can take. */
    static boolean isUpdate(Release r, int installedCode, int sdk) {
        return r != null && r.versionCode > installedCode && sdk >= r.minSdk;
    }

    /** Whether to check now: the check is on, it is a release build, and the last check was a day ago or more. */
    static boolean checkDue(boolean publicRelease, boolean checkOn, long lastCheckMs, long nowMs) {
        if (!publicRelease || !checkOn) return false;
        // A clock set back counts as due, so a TV whose clock jumped doesn't wait for months.
        return lastCheckMs <= 0 || nowMs < lastCheckMs || nowMs - lastCheckMs >= CHECK_EVERY_MS;
    }

    /**
     * Whether a downloaded APK may go to Android's installer: its SHA-256 is the release's, it has exactly one signer,
     * and that signer is both the release certificate and the installed launcher's own (digests as lowercase hex).
     */
    static boolean acceptable(Release r, String apkSha256, Collection<String> apkSigners, Collection<String> installedSigners) {
        if (r == null || apkSha256 == null || apkSigners == null || installedSigners == null) return false;
        if (!r.sha256.equals(apkSha256.toLowerCase(Locale.ROOT))) return false;
        if (apkSigners.size() != 1 || installedSigners.size() != 1) return false;
        String apk = apkSigners.iterator().next().toLowerCase(Locale.ROOT);
        String installed = installedSigners.iterator().next().toLowerCase(Locale.ROOT);
        return apk.equals(r.certSha256) && installed.equals(r.certSha256);
    }

    static String sha256Hex(InputStream in) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        return hex(md.digest());
    }

    static String sha256Hex(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        return sb.toString();
    }

    /** What About says about updates (the prototype's states, design_preview core/updates.ts). */
    enum Status {
        /** A build from a computer: it updates from there; no check. */
        OWN,
        /** About › Check for updates is off. */
        OFF,
        /** Not checked yet, or the newest release is this one. */
        CURRENT,
        /** A newer release is downloading. */
        DOWNLOADING,
        /** Downloaded and checked; Install hands it to Android. */
        READY,
        /** Downloaded, but the launcher may not install apps yet; Install opens Android's screen to allow it. */
        NO_GRANT,
        /** Downloaded, and this TV has no screen to allow it: the laptop installer updates it. */
        NO_SCREEN,
        /** The last check couldn't reach GitHub. */
        FAILED
    }

    /**
     * About's status. {@code ready} is a downloaded, accepted APK of a newer version; {@code canInstall} is
     * PackageManager.canRequestPackageInstalls(); {@code grantScreen} whether ACTION_MANAGE_UNKNOWN_APP_SOURCES resolves.
     */
    static Status status(boolean publicRelease, boolean checkOn, boolean downloading, boolean ready, boolean lastFailed,
                         boolean canInstall, boolean grantScreen) {
        if (!publicRelease) return Status.OWN;
        if (!checkOn) return Status.OFF;
        if (ready) return canInstall ? Status.READY : grantScreen ? Status.NO_GRANT : Status.NO_SCREEN;
        if (downloading) return Status.DOWNLOADING;
        return lastFailed ? Status.FAILED : Status.CURRENT;
    }

    /** Home's notice: once per version, while one waits to be installed. */
    static boolean notice(Status s, String readyVersion, String noticedVersion) {
        boolean waiting = s == Status.READY || s == Status.NO_GRANT || s == Status.NO_SCREEN;
        return waiting && readyVersion != null && !readyVersion.equals(noticedVersion);
    }
}
