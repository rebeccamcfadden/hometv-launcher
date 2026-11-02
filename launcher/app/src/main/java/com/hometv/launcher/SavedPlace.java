package com.hometv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.provider.Settings;
import android.util.Base64;

/**
 * F26: where the household was, kept on disk while the launcher is in the background, for when the activity comes back
 * without its saved state. MainActivity is Home and keeps {@code stateNotNeeded} (as Launcher3 does): Android may then
 * restart it with no Bundle after ending the process, and without this the household would land on Home instead of the
 * page they left.
 *
 * <p>The place is written when the activity saves its state (on its way to the background), read once when it is made
 * again, and dropped as soon as the activity is back in front, so a stored place never outlives the trip to the other
 * app. It is the same Bundle the activity saves, marshalled: a Parcel's bytes are only read back in the boot, install
 * and Home task that wrote them ({@link Comeback#sameRun}), so the platform that wrote them is the one that reads them,
 * and a Home started afresh (after a force stop or a crash) starts on Home.
 */
final class SavedPlace {
    private static final String PREFS = "comeback";
    private static final String KEY = "place";
    private static final String KEY_BOOT = "boot";
    private static final String KEY_APK = "apk";
    private static final String KEY_TASK = "task";

    private SavedPlace() {
    }

    static void write(Context ctx, Bundle place, int task) {
        Parcel p = Parcel.obtain();
        try {
            p.writeBundle(place);
            prefs(ctx).edit()
                    .putString(KEY, Base64.encodeToString(p.marshall(), Base64.NO_WRAP))
                    .putInt(KEY_BOOT, boot(ctx))
                    .putString(KEY_APK, ctx.getApplicationInfo().sourceDir)
                    .putInt(KEY_TASK, task)
                    .apply();
        } finally {
            p.recycle();
        }
    }

    /** The place written in this boot, install and Home task, or null. */
    static Bundle read(Context ctx, int task) {
        SharedPreferences prefs = prefs(ctx);
        String s = prefs.getString(KEY, null);
        if (s == null || !Comeback.sameRun(prefs.getInt(KEY_BOOT, -2), boot(ctx), prefs.getString(KEY_APK, null),
                ctx.getApplicationInfo().sourceDir, prefs.getInt(KEY_TASK, -1), task)) {
            return null;
        }
        Parcel p = Parcel.obtain();
        try {
            byte[] bytes = Base64.decode(s, Base64.NO_WRAP);
            p.unmarshall(bytes, 0, bytes.length);
            p.setDataPosition(0);
            return p.readBundle(SavedPlace.class.getClassLoader());
        } catch (RuntimeException e) {
            return null;
        } finally {
            p.recycle();
        }
    }

    /** The activity is in front again: what was kept is out of date. */
    static void forget(Context ctx) {
        SharedPreferences prefs = prefs(ctx);
        if (prefs.contains(KEY)) prefs.edit().clear().apply();
    }

    private static int boot(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
