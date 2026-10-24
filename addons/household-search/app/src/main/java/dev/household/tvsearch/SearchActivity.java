package dev.household.tvsearch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.data.SearchPreferences;
import dev.household.tvsearch.data.NativeMediaSource;
import dev.household.tvsearch.data.NativeMediaSnapshot;
import dev.household.tvsearch.domain.MediaEntity;
import dev.household.tvsearch.domain.LaunchTarget;
import dev.household.tvsearch.domain.ProviderAvailability;
import dev.household.tvsearch.domain.Subscription;
import dev.household.tvsearch.launch.DeeplinkResolver;
import dev.household.tvsearch.launch.DemoProviderAdapter;
import dev.household.tvsearch.launch.ExternalAppLauncher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SearchActivity extends Activity implements SearchResultsAdapter.Listener {
    public static final String EXTRA_NATIVE = "native_sources";
    private static final int COLUMNS = 3;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FakeCatalogService catalog = new FakeCatalogService();
    private final List<Button> providerButtons = new ArrayList<>();
    private SearchPreferences preferences;
    private SearchController controller;
    private SearchResultsAdapter adapter;
    private DeeplinkResolver resolver;
    private RecyclerView grid;
    private EditText query;
    private TextView status;
    private View progress;
    private Button retry;
    private CheckBox simulateError;
    private AlertDialog details;
    private AlertDialog settings;
    private AlertDialog launchError;
    private String selectedId;
    private int providerIndex;
    private boolean restoreDetails;
    private boolean focusResults;
    private Parcelable savedGridState;
    private boolean nativeMode;
    private boolean resumedOnce;
    private MediaArtworkLoader artwork;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_search);
        nativeMode = getIntent().getBooleanExtra(EXTRA_NATIVE, false);
        preferences = new SearchPreferences(this);
        query = findViewById(R.id.query);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        retry = findViewById(R.id.retry);
        simulateError = findViewById(R.id.simulateError);
        grid = findViewById(R.id.results);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setItemAnimator(null);
        artwork = new MediaArtworkLoader(this);
        adapter = new SearchResultsAdapter(COLUMNS, this, artwork);
        grid.setAdapter(adapter);
        resolver = new DeeplinkResolver(Arrays.asList(
                new DemoProviderAdapter(FakeCatalogService.CINEMA, getPackageName()),
                new DemoProviderAdapter(FakeCatalogService.SERIES, getPackageName())));
        createController();
        findViewById(R.id.search).setOnClickListener(v -> submit());
        findViewById(R.id.settings).setOnClickListener(v -> showSettings());
        if (nativeMode) {
            ((TextView) findViewById(R.id.searchHeading)).setText(R.string.search_everywhere);
            ((TextView) findViewById(R.id.searchNotice)).setText(R.string.native_search_notice);
            query.setHint(R.string.native_query_hint);
            findViewById(R.id.settings).setVisibility(View.GONE);
            simulateError.setVisibility(View.GONE);
        }
        retry.setOnClickListener(v -> {
            focusResults = true;
            controller.search(controller.state().query);
        });
        query.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) submit();
                return true;
            }
            return false;
        });
        simulateError.setOnCheckedChangeListener((button, checked) -> catalog.setFailRequests(checked));
        if (state != null) {
            query.setText(state.getString("query", ""));
            selectedId = state.getString("selected");
            providerIndex = state.getInt("provider", 0);
            restoreDetails = state.getBoolean("details");
            savedGridState = state.getParcelable("grid");
            simulateError.setChecked(state.getBoolean("error"));
            focusResults = state.getBoolean("gridFocused") || restoreDetails;
            controller.search(state.getString("submitted", ""));
        } else {
            findViewById(R.id.search).requestFocus();
        }
        updateRegionLabel();
        if (state == null) render(controller.state());
    }

    private void createController() {
        if (controller != null) controller.close();
        if (nativeMode) {
            NativeMediaSource source = new NativeMediaSource(this);
            controller = new SearchController(text -> {
                NativeMediaSnapshot snapshot = source.load();
                List<SearchController.Result> results = new ArrayList<>();
                for (MediaEntity entity : snapshot.search(text))
                    results.add(new SearchController.Result(entity, Collections.emptyList()));
                return new SearchController.Batch(results, snapshot.notice);
            }, worker, main::post, this::render);
        } else {
            controller = new SearchController(catalog, catalog, preferences.region(), worker, main::post, this::render);
        }
    }

    private void submit() {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(query.getWindowToken(), 0);
        selectedId = null;
        providerIndex = 0;
        focusResults = true;
        findViewById(R.id.search).requestFocus();
        controller.search(query.getText().toString());
    }

    private void render(SearchController.State state) {
        if (isDestroyed() || isFinishing()) return;
        progress.setVisibility(state.status == SearchController.Status.LOADING ? View.VISIBLE : View.GONE);
        boolean warning = state.notice != null && !state.notice.isEmpty();
        retry.setVisibility(state.status == SearchController.Status.ERROR || warning ? View.VISIBLE : View.GONE);
        adapter.submit(state.results, preferences.subscriptions());
        switch (state.status) {
            case EMPTY_QUERY:
                status.setText(nativeMode ? R.string.native_empty_query : R.string.empty_query);
                focusResults = false;
                break;
            case LOADING:
                status.setText(nativeMode ? R.string.native_loading : R.string.loading);
                break;
            case NO_RESULTS:
                status.setText(getString(nativeMode ? R.string.native_no_results : R.string.no_results, state.query));
                focusResults = false;
                break;
            case ERROR:
                Log.w("HouseholdSearch", state.error);
                status.setText(state.error);
                if (focusResults) retry.requestFocus();
                focusResults = false;
                break;
            case RESULTS:
                status.setText(getResources().getQuantityString(
                        nativeMode ? R.plurals.native_results_count : R.plurals.results_count,
                        state.results.size(), state.results.size()));
                grid.post(() -> {
                    if (isDestroyed() || controller.state() != state) return;
                    if (savedGridState != null) {
                        grid.getLayoutManager().onRestoreInstanceState(savedGridState);
                        savedGridState = null;
                    }
                    int position = adapter.positionOf(selectedId);
                    if (position < 0) position = 0;
                    if (focusResults) {
                        SearchResultsAdapter.focusPosition(grid, position);
                        focusResults = false;
                    }
                    if (restoreDetails) {
                        restoreDetails = false;
                        showDetails(adapter.resultAt(position));
                    }
                });
                break;
            default:
                throw new IllegalStateException("Unhandled search state");
        }
        if (warning) status.append("\n" + state.notice);
    }

    @Override public void onSelected(SearchController.Result result) {
        selectedId = result.entity.id;
        providerIndex = 0;
        showDetails(result);
    }

    @Override public void onFocused(String contentId) { selectedId = contentId; }

    @Override public void onUpFromFirstRow() { findViewById(R.id.search).requestFocus(); }

    private void showDetails(SearchController.Result result) {
        if (details != null && details.isShowing()) return;
        LinearLayout content = column();
        addText(content, result.entity.title, 23);
        addText(content, MediaPresentation.metadata(this, result.entity), 15);
        if (result.title == null) {
            addText(content, MediaPresentation.origin(this, result.entity), 16);
            if (result.entity.type != MediaEntity.Type.APP) {
                Integer percent = result.entity.progressPercent();
                addText(content, percent == null ? getString(R.string.progress_unknown)
                        : getString(R.string.progress_percent, percent), 15);
            }
        } else {
            addText(content, result.title.description == null
                ? getString(R.string.description_missing) : result.title.description, 17);
            if (!result.title.genres.isEmpty())
                addText(content, getString(R.string.genres, String.join(", ", result.title.genres)), 14);
            addText(content, getString(R.string.region_notice, preferences.region()), 14);
            addText(content, getString(R.string.provider_heading), 18);
        }
        providerButtons.clear();
        if (result.title == null) {
            Button open = button(getString(result.entity.type == MediaEntity.Type.APP
                    ? R.string.open_app : R.string.resume_content));
            open.setTag("native_open");
            open.setOnClickListener(v -> launchNative(result.entity));
            providerButtons.add(open);
            content.addView(open);
        }
        for (int i = 0; i < result.offers.size(); i++) {
            ProviderAvailability offer = result.offers.get(i);
            Button button = button(getString(R.string.offer_label, offer.providerName, accessLabel(offer.access),
                    Subscription.includes(offer, preferences.subscriptions())
                            ? getString(R.string.included_suffix) : ""));
            final int index = i;
            button.setTag("provider_" + i);
            button.setOnFocusChangeListener((v, focused) -> {
                if (focused) providerIndex = index;
            });
            button.setOnClickListener(v -> launch(result, offer));
            providerButtons.add(button);
            content.addView(button);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        details = new AlertDialog.Builder(this).setView(scroll).setNegativeButton(R.string.close, null).create();
        details.setOnDismissListener(dialog -> {
            details = null;
            providerButtons.clear();
            if (!isFinishing() && !isDestroyed()) {
                int position = adapter.positionOf(selectedId);
                if (position >= 0) SearchResultsAdapter.focusPosition(grid, position);
            }
        });
        int requestedProvider = providerIndex;
        details.setOnShowListener(dialog -> {
            providerIndex = requestedProvider;
            focusProvider();
        });
        details.show();
    }

    private void focusProvider() {
        if (!providerButtons.isEmpty())
            providerButtons.get(Math.min(providerIndex, providerButtons.size() - 1)).requestFocus();
    }

    private void launchNative(MediaEntity entity) {
        ExternalAppLauncher.MediaLaunch outcome = new ExternalAppLauncher(this).openMedia(entity);
        if (outcome.opened) {
            if (outcome.homeFallback) Toast.makeText(this, outcome.message, Toast.LENGTH_LONG).show();
        } else {
            launchError = new AlertDialog.Builder(this).setTitle(R.string.native_unavailable)
                    .setMessage(outcome.message).setPositiveButton(R.string.close, null).create();
            launchError.setOnDismissListener(dialog -> { launchError = null; focusProvider(); });
            launchError.show();
        }
    }

    private void launch(SearchController.Result result, ProviderAvailability offer) {
        DeeplinkResolver.Outcome outcome = resolver.launch(result.title, offer, new ExternalAppLauncher(this));
        if (!outcome.opened()) {
            Log.w("HouseholdSearch", String.join("; ", outcome.failures));
            launchError = new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.launch_unavailable, offer.providerName))
                    .setMessage(getString(R.string.launch_failure, String.join("\n", outcome.failures)))
                    .setPositiveButton(R.string.close, null).create();
            launchError.setOnDismissListener(dialog -> {
                launchError = null;
                focusProvider();
            });
            launchError.show();
        } else if (outcome.target.level != LaunchTarget.Level.TITLE) {
            Toast.makeText(this, getString(R.string.launch_fallback, levelLabel(outcome.target.level)), Toast.LENGTH_LONG).show();
        }
    }

    private String accessLabel(ProviderAvailability.Access access) {
        switch (access) {
            case SUBSCRIPTION: return getString(R.string.offer_subscription);
            case FREE: return getString(R.string.offer_free);
            case FREE_WITH_ADS: return getString(R.string.offer_ads);
            case RENT: return getString(R.string.offer_rent);
            case BUY: return getString(R.string.offer_buy);
            case UNKNOWN: return getString(R.string.offer_unknown);
            default: throw new IllegalStateException("Unhandled access type");
        }
    }

    String levelLabel(LaunchTarget.Level level) {
        switch (level) {
            case TITLE: return getString(R.string.level_title);
            case SEARCH: return getString(R.string.level_search);
            case APP_HOME: return getString(R.string.level_home);
            case WEB: return getString(R.string.level_web);
            default: throw new IllegalStateException("Unhandled launch level");
        }
    }

    private void showSettings() {
        LinearLayout content = column();
        addText(content, getString(R.string.country_explanation), 16);
        EditText country = new EditText(this);
        country.setSingleLine(true);
        country.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        country.setHint(R.string.country_hint);
        country.setText(preferences.region());
        country.setSelectAllOnFocus(true);
        content.addView(country);
        List<Subscription> subscriptions = preferences.subscriptions();
        CheckBox cinema = new CheckBox(this);
        cinema.setText(R.string.cinema_subscription);
        cinema.setChecked(subscriptions.get(0).enabled);
        content.addView(cinema);
        CheckBox series = new CheckBox(this);
        series.setText(R.string.series_subscription);
        series.setChecked(subscriptions.get(1).enabled);
        content.addView(series);
        addText(content, getString(R.string.subscription_explanation), 14);
        settings = new AlertDialog.Builder(this).setTitle(R.string.demo_settings).setView(content)
                .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create();
        settings.setOnShowListener(dialog -> {
            settings.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
            settings.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String nextRegion = country.getText().toString().trim().toUpperCase(Locale.ROOT);
                if (!SearchPreferences.validRegion(nextRegion)) {
                    country.setError(getString(R.string.country_invalid));
                    country.requestFocus();
                    return;
                }
                boolean changed = !preferences.region().equals(nextRegion);
                preferences.setRegion(nextRegion);
                preferences.setSubscriptions(cinema.isChecked(), series.isChecked());
                settings.dismiss();
                updateRegionLabel();
                if (changed) {
                    String submitted = controller.state().query;
                    createController();
                    controller.search(submitted);
                } else {
                    render(controller.state());
                }
            });
        });
        settings.setOnDismissListener(dialog -> {
            settings = null;
            findViewById(R.id.settings).requestFocus();
        });
        settings.show();
    }

    private void updateRegionLabel() {
        ((TextView) findViewById(R.id.region)).setText(nativeMode ? getString(R.string.native_scope)
                : getString(R.string.region_notice, preferences.region()));
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        column.setPadding(padding, padding, padding, padding);
        return column;
    }

    private void addText(LinearLayout parent, String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Color.WHITE);
        label.setPadding(0, 0, 0, Math.round(10 * getResources().getDisplayMetrics().density));
        parent.addView(label);
    }

    private Button button(String label) {
        Button button = new Button(this, null, 0, R.style.SearchButton);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("query", query.getText().toString());
        state.putString("submitted", controller.state().query);
        state.putString("selected", selectedId);
        state.putInt("provider", providerIndex);
        state.putBoolean("details", details != null && details.isShowing());
        state.putBoolean("gridFocused", grid.hasFocus());
        state.putBoolean("error", simulateError.isChecked());
        state.putParcelable("grid", grid.getLayoutManager().onSaveInstanceState());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        controller.close();
        if (launchError != null) launchError.dismiss();
        if (details != null) details.dismiss();
        if (settings != null) settings.dismiss();
        worker.shutdownNow();
        artwork.close();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (nativeMode && resumedOnce && !controller.state().query.isEmpty()) {
            focusResults = grid.hasFocus();
            controller.search(controller.state().query);
        }
        resumedOnce = true;
    }
}
