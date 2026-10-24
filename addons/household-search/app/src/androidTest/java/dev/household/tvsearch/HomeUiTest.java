package dev.household.tvsearch;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import dev.household.tvsearch.data.NativeMediaSnapshot;
import dev.household.tvsearch.data.NativeMediaSource;
import dev.household.tvsearch.domain.MediaEntity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class HomeUiTest {
    @Rule public final ActivityTestRule<HomeActivity> rule = new ActivityTestRule<>(HomeActivity.class);
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private <T> T onMain(Supplier<T> read) {
        if (Looper.myLooper() == Looper.getMainLooper()) return read.get();
        AtomicReference<T> value = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> value.set(read.get()));
        return value.get();
    }

    private void waitFor(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < end) {
            instrumentation.waitForIdleSync();
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(30);
        }
        fail("Timed out waiting for native TV UI");
    }

    private Activity resumed() {
        return onMain(() -> ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .stream().findFirst().orElse(null));
    }

    private void key(int code) {
        instrumentation.sendKeyDownUpSync(code);
        instrumentation.waitForIdleSync();
    }

    @Before public void loaded() {
        waitFor(() -> onMain(() -> rule.getActivity().findViewById(R.id.homeLoading).getVisibility() == View.GONE));
    }

    @Test public void nativeSearchFindsAppsAndBackReturnsToTheSameHomeControl() {
        HomeActivity home = rule.getActivity();
        NativeMediaSnapshot nativeData = new NativeMediaSource(home).load();
        assertFalse(nativeData.apps.isEmpty());
        String title = nativeData.apps.get(0).title;
        assertTrue(onMain(() -> home.findViewById(R.id.openSearch).hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor(() -> resumed() instanceof SearchActivity);
        SearchActivity search = (SearchActivity) resumed();
        assertEquals(search.getString(R.string.native_empty_query),
                onMain(() -> ((TextView) search.findViewById(R.id.status)).getText().toString()));
        onMain(() -> {
            ((EditText) search.findViewById(R.id.query)).setText(title);
            search.findViewById(R.id.search).performClick();
            return null;
        });
        waitFor(() -> onMain(() -> ((RecyclerView) search.findViewById(R.id.results)).getAdapter().getItemCount() > 0));
        waitFor(() -> onMain(() -> search.findViewById(R.id.results).hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        onView(withTagValue(is("native_open"))).check(matches(isDisplayed()));
        key(KeyEvent.KEYCODE_BACK);
        key(KeyEvent.KEYCODE_BACK);
        waitFor(() -> resumed() == home);
        assertTrue(onMain(() -> home.findViewById(R.id.openSearch).hasFocus()));
    }

    @Test public void horizontalResumeRowShowsRealProgressAndNavigatesBetweenRows() {
        HomeActivity home = rule.getActivity();
        NativeMediaSnapshot data = new NativeMediaSource(home).load();
        MediaEntity first = new MediaEntity.Builder("test:episode:1", MediaEntity.Type.EPISODE,
                "Original UI test episode", MediaEntity.Source.WATCH_NEXT)
                .provider("Test provider", home.getPackageName()).subtitle("S1 | E1")
                .progress(25L, 100L).build();
        MediaEntity unknown = new MediaEntity.Builder("test:episode:2", MediaEntity.Type.EPISODE,
                "Unknown progress test", MediaEntity.Source.WATCH_NEXT)
                .provider("Test provider", home.getPackageName()).build();
        onMain(() -> {
            home.renderSnapshot(new NativeMediaSnapshot(Arrays.asList(first, unknown), Collections.emptyList(),
                    data.apps, "", false));
            return null;
        });
        RecyclerView row = home.findViewById(R.id.resumeRow);
        onMain(() -> { SearchResultsAdapter.focusPosition(row, 0); return null; });
        waitFor(() -> onMain(row::hasFocus));
        assertEquals(25, onMain(() -> ((ProgressBar) row.findViewHolderForAdapterPosition(0)
                .itemView.findViewById(R.id.playbackProgress)).getProgress()).intValue());
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(onMain(() -> row.findViewHolderForAdapterPosition(0).itemView.hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(onMain(() -> row.findViewHolderForAdapterPosition(1).itemView.hasFocus()));
        assertEquals(View.INVISIBLE, onMain(() -> row.findViewHolderForAdapterPosition(1)
                .itemView.findViewById(R.id.playbackProgress).getVisibility()).intValue());
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(onMain(() -> row.findViewHolderForAdapterPosition(1).itemView.hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(onMain(() -> home.findViewById(R.id.appsRow).hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(onMain(row::hasFocus));
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(onMain(() -> home.findViewById(R.id.openSearch).hasFocus()));
    }

    @Test public void returningFromDemoPreservesTheExplicitDemoMode() {
        HomeActivity home = rule.getActivity();
        onMain(() -> { home.findViewById(R.id.openDemo).performClick(); return null; });
        waitFor(() -> resumed() instanceof SearchActivity);
        SearchActivity search = (SearchActivity) resumed();
        assertEquals(search.getString(R.string.empty_query),
                onMain(() -> ((TextView) search.findViewById(R.id.status)).getText().toString()));
        key(KeyEvent.KEYCODE_BACK);
        waitFor(() -> resumed() == home);
    }

    @Test public void homeRecreationRestoresTheFocusedAppByStableId() {
        HomeActivity original = rule.getActivity();
        RecyclerView apps = original.findViewById(R.id.appsRow);
        int position = Math.min(2, apps.getAdapter().getItemCount() - 1);
        assertTrue(position >= 0);
        String selected = onMain(() -> ((MediaRowAdapter) apps.getAdapter()).itemAt(position).id);
        onMain(() -> { SearchResultsAdapter.focusPosition(apps, position); return null; });
        waitFor(() -> onMain(apps::hasFocus));
        onMain(() -> { original.recreate(); return null; });
        waitFor(() -> resumed() instanceof HomeActivity && resumed() != original);
        HomeActivity recreated = (HomeActivity) resumed();
        waitFor(() -> onMain(() -> recreated.findViewById(R.id.homeLoading).getVisibility() == View.GONE));
        RecyclerView restored = recreated.findViewById(R.id.appsRow);
        waitFor(() -> onMain(restored::hasFocus));
        assertTrue(onMain(() -> {
            int expected = ((MediaRowAdapter) restored.getAdapter()).positionOf(selected);
            RecyclerView.ViewHolder holder = restored.findViewHolderForAdapterPosition(expected);
            return holder != null && holder.itemView.hasFocus();
        }));
    }

    @Test public void unsupportedNativeProviderKeepsTheResumeSelection() {
        HomeActivity home = rule.getActivity();
        NativeMediaSnapshot data = new NativeMediaSource(home).load();
        MediaEntity missing = new MediaEntity.Builder("test:unavailable", MediaEntity.Type.TV,
                "Original unavailable test", MediaEntity.Source.WATCH_NEXT)
                .provider("Missing test provider", "test.missing.tv.provider").build();
        onMain(() -> {
            home.renderSnapshot(new NativeMediaSnapshot(Collections.singletonList(missing),
                    Collections.emptyList(), data.apps, "", false));
            return null;
        });
        RecyclerView row = home.findViewById(R.id.resumeRow);
        onMain(() -> { SearchResultsAdapter.focusPosition(row, 0); return null; });
        waitFor(() -> onMain(row::hasFocus));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        onView(withText(R.string.native_unavailable)).check(matches(isDisplayed()));
        key(KeyEvent.KEYCODE_BACK);
        assertTrue(onMain(() -> row.findViewHolderForAdapterPosition(0).itemView.hasFocus()));
    }
}
