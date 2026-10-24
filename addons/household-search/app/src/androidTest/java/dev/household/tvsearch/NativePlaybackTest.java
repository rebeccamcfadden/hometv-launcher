package dev.household.tvsearch;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import dev.household.tvsearch.data.NativeMediaSnapshot;
import dev.household.tvsearch.data.NativeMediaSource;
import dev.household.tvsearch.data.TvProgramMapper;
import dev.household.tvsearch.domain.MediaEntity;
import dev.household.tvsearch.launch.ExternalAppLauncher;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.RuleChain;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NativePlaybackTest {
    public final ActivityTestRule<HomeActivity> rule = new ActivityTestRule<>(HomeActivity.class);
    @Rule public final RuleChain permissionsThenActivity = RuleChain
            .outerRule(GrantPermissionRule.grant(NativeMediaSource.READ_TV_LISTINGS)).around(rule);
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private MediaEntity playback(Intent intent, String pkg) {
        return new MediaEntity.Builder("test:resume", MediaEntity.Type.EPISODE, "Original test",
                MediaEntity.Source.WATCH_NEXT).provider("Test provider", pkg)
                .playback(intent.toUri(Intent.URI_INTENT_SCHEME)).build();
    }

    @Test public void playbackKeepsOriginalDataActionAndExtrasButStripsGrantFlags() throws Exception {
        String pkg = rule.getActivity().getPackageName();
        Intent original = new Intent("test.RESUME", Uri.parse("original-test://episode/3"))
                .putExtra("position", 1234L).setPackage(pkg)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Intent safe = ExternalAppLauncher.playbackIntent(playback(original, pkg));
        assertEquals(original.getData(), safe.getData());
        assertEquals(original.getAction(), safe.getAction());
        assertEquals(1234L, safe.getLongExtra("position", 0));
        assertEquals(pkg, safe.getPackage());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, safe.getFlags());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotRedirectNativePlaybackToAnotherPackage() throws Exception {
        Intent original = new Intent(Intent.ACTION_VIEW).setClassName("test.other", "test.other.Player");
        ExternalAppLauncher.playbackIntent(playback(original, "test.owner"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotRedirectNativePlaybackThroughAnExplicitOtherPackage() throws Exception {
        ExternalAppLauncher.playbackIntent(playback(
                new Intent(Intent.ACTION_VIEW, Uri.parse("test://video")).setPackage("test.other"), "test.owner"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fileUrisDoNotEscapeThePlaybackBoundary() throws Exception {
        ExternalAppLauncher.playbackIntent(playback(
                new Intent(Intent.ACTION_VIEW, Uri.parse("file:///not-a-playback-link")), "test.owner"));
    }

    @Test public void missingCursorColumnsStayUnknownRatherThanInventingProgress() {
        try (MatrixCursor cursor = new MatrixCursor(new String[] {"_id", "title", "package_name"})) {
            cursor.addRow(new Object[] {1L, "Original test title", "test.owner"});
            assertTrue(cursor.moveToFirst());
            TvProgramMapper.Row row = NativeMediaSource.readRow(cursor);
            assertNull(row.positionMillis);
            assertNull(row.durationMillis);
            assertNull(row.intentUri);
            assertFalse(TvProgramMapper.isResume(row));
        }
    }

    @Test public void missingProviderAndPlaybackProduceAnExplicitFailure() {
        MediaEntity entity = new MediaEntity.Builder("test:missing", MediaEntity.Type.TV, "Test",
                MediaEntity.Source.WATCH_NEXT).provider("Missing test app", "test.missing.tv.provider").build();
        ExternalAppLauncher.MediaLaunch result = new ExternalAppLauncher(rule.getActivity()).openMedia(entity);
        assertFalse(result.opened);
        assertFalse(result.homeFallback);
        assertFalse(result.message.isEmpty());
    }

    @Test public void rejectedNativeIntentFallsBackToItsOwnAppHome() {
        HomeActivity home = rule.getActivity();
        MediaEntity entity = playback(new Intent(Intent.ACTION_VIEW, Uri.parse("original-test-unhandled://resume")),
                home.getPackageName());
        AtomicReference<ExternalAppLauncher.MediaLaunch> outcome = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> outcome.set(new ExternalAppLauncher(home).openMedia(entity)));
        instrumentation.waitForIdleSync();
        assertTrue(outcome.get().opened);
        assertTrue(outcome.get().homeFallback);
        assertFalse(outcome.get().message.isEmpty());
        instrumentation.runOnMainSync(() -> {
            for (android.app.Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (activity != home && activity instanceof HomeActivity) activity.finish();
            }
        });
        instrumentation.waitForIdleSync();
    }

    @Test public void nativeProviderReadUsesOnlyOrdinaryTvPermission() throws Exception {
        HomeActivity activity = rule.getActivity();
        assertEquals(PackageManager.PERMISSION_GRANTED, activity.checkSelfPermission(NativeMediaSource.READ_TV_LISTINGS));
        String[] permissions = activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertArrayEquals(new String[] {NativeMediaSource.READ_TV_LISTINGS}, permissions);
        NativeMediaSnapshot snapshot = new NativeMediaSource(activity).load();
        assertFalse(snapshot.needsPermission);
        assertEquals("", snapshot.notice);
        assertFalse(snapshot.apps.isEmpty());
        android.util.Log.i("HouseholdSearch", "Native probe counts: resume=" + snapshot.continueWatching.size()
                + ", preview=" + snapshot.previews.size() + ", apps=" + snapshot.apps.size());
    }
}
