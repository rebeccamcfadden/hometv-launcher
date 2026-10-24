package dev.household.tvsearch;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.data.SearchPreferences;
import dev.household.tvsearch.domain.ProviderAvailability;
import dev.household.tvsearch.domain.Subscription;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/** Requires a TV or emulator. Navigation uses keys, never injected touch. */
@RunWith(AndroidJUnit4.class)
public class SearchUiTest {
    @Rule public final ActivityTestRule<SearchActivity> rule =
            new ActivityTestRule<>(SearchActivity.class, true, false);
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Before public void startCleanDemo() {
        assertTrue(instrumentation.getTargetContext().getSharedPreferences("search_settings", 0)
                .edit().clear().commit());
        rule.launchActivity(null);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    private <T> T onMain(Supplier<T> read) {
        if (Looper.myLooper() == Looper.getMainLooper()) return read.get();
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(read.get()));
        return result.get();
    }

    private void waitFor(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < end) {
            instrumentation.waitForIdleSync();
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(30);
        }
        fail("Timed out waiting for TV UI state");
    }

    private SearchActivity currentSearch() {
        return onMain(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof SearchActivity) return (SearchActivity) activity;
            }
            return null;
        });
    }

    private Activity resumedActivity() {
        return onMain(() -> ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .stream().findFirst().orElse(null));
    }

    private void search(String text, int expectedCount) {
        SearchActivity activity = currentSearch();
        assertNotNull(activity);
        instrumentation.runOnMainSync(() -> {
            ((EditText) activity.findViewById(R.id.query)).setText(text);
            activity.findViewById(R.id.search).performClick();
        });
        waitFor(() -> onMain(() -> ((RecyclerView) activity.findViewById(R.id.results))
                .getAdapter().getItemCount() == expectedCount));
        instrumentation.waitForIdleSync();
    }

    private void key(int code) {
        instrumentation.sendKeyDownUpSync(code);
        instrumentation.waitForIdleSync();
    }

    private String focusedTitleId() {
        SearchActivity activity = currentSearch();
        return onMain(() -> {
            RecyclerView grid = activity.findViewById(R.id.results);
            if (activity.getCurrentFocus() == null) return null;
            RecyclerView.ViewHolder holder = grid.findContainingViewHolder(activity.getCurrentFocus());
            if (holder == null) return null;
            return ((SearchResultsAdapter) grid.getAdapter()).resultAt(holder.getBindingAdapterPosition()).title.id;
        });
    }

    @Test public void emptyAndMissingMetadataRenderWithoutBreakingFocus() {
        onView(withId(R.id.status)).check(matches(withText(R.string.empty_query)));
        search("Train", 1);
        onView(withId(R.id.metadata)).check(matches(withText(R.string.movie)));
        onView(withId(R.id.placeholderLabel)).check(matches(isDisplayed()));
        assertEquals("fixture:movie:4", focusedTitleId());
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("fixture:movie:4", focusedTitleId());
        key(KeyEvent.KEYCODE_DPAD_UP);
        onView(withId(R.id.search)).check(matches(hasFocus()));
    }

    @Test public void titleSearchAndUnsupportedDestinationsKeepProviderChoice() {
        search("Lantern", 2);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        onView(withTagValue(is("provider_0"))).check(matches(hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor(() -> resumedActivity() instanceof DemoDestinationActivity);
        assertEquals("TITLE", resumedActivity().getIntent().getStringExtra(DemoDestinationActivity.EXTRA_LEVEL));
        key(KeyEvent.KEYCODE_BACK);
        waitFor(() -> currentSearch() != null);
        onView(withTagValue(is("provider_0"))).check(matches(hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        onView(withTagValue(is("provider_1"))).check(matches(hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor(() -> resumedActivity() instanceof DemoDestinationActivity);
        assertEquals("SEARCH", resumedActivity().getIntent().getStringExtra(DemoDestinationActivity.EXTRA_LEVEL));
        key(KeyEvent.KEYCODE_BACK);
        waitFor(() -> currentSearch() != null);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        onView(withTagValue(is("provider_3"))).check(matches(hasFocus()));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        onView(withText("Cannot open Unsupported Demo")).check(matches(isDisplayed()));
        key(KeyEvent.KEYCODE_BACK);
        onView(withTagValue(is("provider_3"))).check(matches(hasFocus()));
        key(KeyEvent.KEYCODE_BACK);
        assertEquals("fixture:movie:1", focusedTitleId());
    }

    @Test public void missingTitleLinkFallsBackToProviderHomeAndRestoresTvFocus() {
        search("Lantern", 2);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("fixture:tv:1", focusedTitleId());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        waitFor(() -> resumedActivity() instanceof DemoDestinationActivity);
        assertEquals("APP_HOME", resumedActivity().getIntent().getStringExtra(DemoDestinationActivity.EXTRA_LEVEL));
        key(KeyEvent.KEYCODE_BACK);
        waitFor(() -> currentSearch() != null);
        key(KeyEvent.KEYCODE_BACK);
        assertEquals("fixture:tv:1", focusedTitleId());
    }

    @Test public void errorIsRecoverableWithoutRestarting() {
        SearchActivity activity = currentSearch();
        instrumentation.runOnMainSync(() -> ((CheckBox) activity.findViewById(R.id.simulateError)).setChecked(true));
        instrumentation.runOnMainSync(() -> {
            ((EditText) activity.findViewById(R.id.query)).setText("Lantern");
            activity.findViewById(R.id.search).performClick();
        });
        waitFor(() -> onMain(() -> activity.findViewById(R.id.retry).getVisibility() == android.view.View.VISIBLE));
        onView(withId(R.id.retry)).check(matches(hasFocus()));
        instrumentation.runOnMainSync(() -> {
            ((CheckBox) activity.findViewById(R.id.simulateError)).setChecked(false);
            activity.findViewById(R.id.retry).performClick();
        });
        waitFor(() -> onMain(() -> ((RecyclerView) activity.findViewById(R.id.results)).getAdapter().getItemCount() == 2));
    }

    @Test public void vendorKeyboardEnterSubmitsTheQuery() {
        SearchActivity activity = currentSearch();
        instrumentation.runOnMainSync(() -> {
            EditText query = activity.findViewById(R.id.query);
            query.setText("Lantern");
            query.requestFocus();
        });
        key(KeyEvent.KEYCODE_ENTER);
        waitFor(() -> onMain(() -> ((RecyclerView) activity.findViewById(R.id.results))
                .getAdapter().getItemCount() == 2));
        assertEquals("fixture:movie:1", focusedTitleId());
    }

    @Test public void recreationRestoresSelectedTitleAndProviderDialog() {
        search("Lantern", 2);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        onView(withTagValue(is("provider_1"))).check(matches(hasFocus()));
        SearchActivity previous = currentSearch();
        instrumentation.runOnMainSync(previous::recreate);
        waitFor(() -> currentSearch() != null && currentSearch() != previous);
        waitFor(() -> onMain(() -> ((RecyclerView) currentSearch().findViewById(R.id.results))
                .getAdapter().getItemCount() == 2));
        onView(withTagValue(is("provider_1"))).check(matches(hasFocus()));
        key(KeyEvent.KEYCODE_BACK);
        assertEquals("fixture:tv:1", focusedTitleId());
    }

    @Test public void regionAndSubscriptionsPersistSeparatelyFromOffers() throws Exception {
        SearchPreferences preferences = new SearchPreferences(instrumentation.getTargetContext());
        preferences.setRegion("GB");
        preferences.setSubscriptions(false, true);
        SearchPreferences reloaded = new SearchPreferences(instrumentation.getTargetContext());
        assertEquals("GB", reloaded.region());
        assertFalse(reloaded.subscriptions().get(0).enabled);
        assertTrue(reloaded.subscriptions().get(1).enabled);
        ProviderAvailability offer = new FakeCatalogService()
                .getAvailability("fixture:movie:1", dev.household.tvsearch.domain.MediaTitle.Type.MOVIE, "GB").get(0);
        assertFalse(Subscription.includes(offer, reloaded.subscriptions()));
        assertTrue(SearchPreferences.validRegion("GB"));
        assertFalse(SearchPreferences.validRegion("ZZ"));
    }
}
