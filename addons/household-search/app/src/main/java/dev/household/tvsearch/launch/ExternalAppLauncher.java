package dev.household.tvsearch.launch;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import dev.household.tvsearch.DemoDestinationActivity;
import dev.household.tvsearch.R;
import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.domain.LaunchTarget;
import dev.household.tvsearch.domain.MediaEntity;

import java.net.URISyntaxException;

/** Android execution boundary for explicit demos and owner-pinned native playback. */
public final class ExternalAppLauncher implements DeeplinkResolver.Launcher {
    private final Activity activity;

    public ExternalAppLauncher(Activity activity) {
        this.activity = activity;
    }

    public static final class MediaLaunch {
        public final boolean opened;
        public final boolean homeFallback;
        public final String message;

        MediaLaunch(boolean opened, boolean homeFallback, String message) {
            this.opened = opened;
            this.homeFallback = homeFallback;
            this.message = message;
        }
    }

    public MediaLaunch openMedia(MediaEntity entity) {
        String pkg = entity.providerPackage;
        if (pkg == null || pkg.trim().isEmpty()) {
            Log.w("HouseholdSearch", "Native media has no owning package");
            return new MediaLaunch(false, false, activity.getString(R.string.native_no_owner));
        }
        if (entity.playbackIntent != null) {
            try {
                Intent intent = playbackIntent(entity);
                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(intent);
                    return new MediaLaunch(true, false, "");
                }
                Log.i("HouseholdSearch", "Native playback has no activity handler");
            } catch (URISyntaxException | IllegalArgumentException | ActivityNotFoundException | SecurityException e) {
                // Do not log the URI or extras: they can contain playback tokens.
                Log.w("HouseholdSearch", "Native playback rejected or unavailable: " + e.getClass().getSimpleName());
            }
        }
        PackageManager pm = activity.getPackageManager();
        Intent home = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (home == null) home = pm.getLaunchIntentForPackage(pkg);
        if (home != null) {
            try {
                activity.startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return new MediaLaunch(true, true, activity.getString(R.string.native_home_fallback));
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w("HouseholdSearch", "Native provider home could not be opened: " + e.getClass().getSimpleName());
            }
        }
        Log.w("HouseholdSearch", "Native playback and provider-home targets exhausted");
        return new MediaLaunch(false, false, activity.getString(R.string.native_launch_failed));
    }

    public static Intent playbackIntent(MediaEntity entity) throws URISyntaxException {
        if (entity.providerPackage == null || entity.playbackIntent == null)
            throw new IllegalArgumentException("Playback and its owning package are required");
        Intent intent = Intent.parseUri(entity.playbackIntent, Intent.URI_INTENT_SCHEME);
        String owner = entity.providerPackage;
        if ((intent.getComponent() != null && !owner.equals(intent.getComponent().getPackageName()))
                || (intent.getPackage() != null && !owner.equals(intent.getPackage()))
                || intent.getSelector() != null) {
            throw new IllegalArgumentException("Playback must remain in its originating app");
        }
        Uri data = intent.getData();
        if (data != null && ("file".equals(data.getScheme()) || "javascript".equals(data.getScheme())
                || "data".equals(data.getScheme()))) {
            throw new IllegalArgumentException("Unsupported playback URI scheme");
        }
        if (intent.getAction() == null) intent.setAction(Intent.ACTION_VIEW);
        intent.setPackage(owner);
        intent.setClipData(null);
        intent.removeExtra("browser_fallback_url");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override public boolean isAppInstalled(String packageName) {
        if (packageName == null) return false;
        try {
            return activity.getPackageManager().getApplicationInfo(packageName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override public DeeplinkResolver.Attempt launch(LaunchTarget target) {
        if (target.verification != LaunchTarget.Verification.DEMO
                || !activity.getPackageName().equals(target.packageName)
                || (!FakeCatalogService.CINEMA.equals(target.providerId)
                && !FakeCatalogService.SERIES.equals(target.providerId))) {
            Log.w("HouseholdSearch", "Refused a non-demo destination in the offline build");
            return new DeeplinkResolver.Attempt(false, "Real streaming-app launching is not enabled in this demo.");
        }
        Intent intent = new Intent(activity, DemoDestinationActivity.class)
                .putExtra(DemoDestinationActivity.EXTRA_PROVIDER, target.providerId)
                .putExtra(DemoDestinationActivity.EXTRA_LEVEL, target.level.name())
                .putExtra(DemoDestinationActivity.EXTRA_CONTENT, target.contentId)
                .putExtra(DemoDestinationActivity.EXTRA_QUERY, target.query);
        try {
            activity.startActivity(intent);
            return new DeeplinkResolver.Attempt(true, "Opened the in-app demo destination.");
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w("HouseholdSearch", "Could not open demo destination", e);
            return new DeeplinkResolver.Attempt(false, "The demo destination could not be opened.");
        }
    }
}
