package dev.household.tvsearch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import dev.household.tvsearch.data.NativeMediaSnapshot;
import dev.household.tvsearch.data.NativeMediaSource;
import dev.household.tvsearch.domain.MediaEntity;
import dev.household.tvsearch.launch.ExternalAppLauncher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Unified add-on landing page; deliberately not registered as Android's default Home. */
public final class HomeActivity extends Activity {
    private static final int TV_PERMISSION_REQUEST = 10;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private RecyclerView resumeRow, appsRow;
    private MediaRowAdapter resumeAdapter, appsAdapter;
    private MediaArtworkLoader artwork;
    private Future<?> pending;
    private AlertDialog launchError;
    private String selectedResume, selectedApp;
    private int generation, pendingFocusRow;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        artwork = new MediaArtworkLoader(this);
        resumeRow = findViewById(R.id.resumeRow);
        appsRow = findViewById(R.id.appsRow);
        resumeAdapter = new MediaRowAdapter(listener(true), artwork);
        appsAdapter = new MediaRowAdapter(listener(false), artwork);
        setup(resumeRow, resumeAdapter);
        setup(appsRow, appsAdapter);
        findViewById(R.id.openSearch).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class).putExtra(SearchActivity.EXTRA_NATIVE, true)));
        findViewById(R.id.openDemo).setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.refreshHome).setOnClickListener(v -> refresh());
        findViewById(R.id.allowListings).setOnClickListener(v -> allowListings());
        if (state != null) {
            selectedResume = state.getString("resume");
            selectedApp = state.getString("app");
            pendingFocusRow = state.getInt("row");
        } else {
            findViewById(R.id.openSearch).requestFocus();
        }
    }

    private void setup(RecyclerView row, MediaRowAdapter adapter) {
        row.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        row.setItemAnimator(null);
        row.setAdapter(adapter);
    }

    private MediaRowAdapter.Listener listener(boolean resume) {
        return new MediaRowAdapter.Listener() {
            @Override public void onSelected(MediaEntity entity) { open(entity); }
            @Override public void onFocused(String id) {
                if (resume) selectedResume = id; else selectedApp = id;
            }
            @Override public void onVertical(int code, int position) {
                if (resume && code == KeyEvent.KEYCODE_DPAD_UP) {
                    findViewById(R.id.openSearch).requestFocus();
                } else if (resume && appsAdapter.getItemCount() > 0) {
                    focus(appsRow, Math.min(position, appsAdapter.getItemCount() - 1));
                } else if (!resume && code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (resumeAdapter.getItemCount() > 0)
                        focus(resumeRow, Math.min(position, resumeAdapter.getItemCount() - 1));
                    else findViewById(R.id.openSearch).requestFocus();
                }
            }
        };
    }

    private void refresh() {
        int request = ++generation;
        if (pending != null) pending.cancel(true);
        findViewById(R.id.homeLoading).setVisibility(View.VISIBLE);
        NativeMediaSource source = new NativeMediaSource(this);
        pending = worker.submit(() -> {
            NativeMediaSnapshot snapshot = source.load();
            main.post(() -> {
                if (!isDestroyed() && !isFinishing() && request == generation) renderSnapshot(snapshot);
            });
        });
    }

    void renderSnapshot(NativeMediaSnapshot snapshot) {
        int focusedRow = resumeRow.hasFocus() ? 1 : appsRow.hasFocus() ? 2 : pendingFocusRow;
        pendingFocusRow = 0;
        resumeAdapter.submit(snapshot.continueWatching);
        appsAdapter.submit(snapshot.apps);
        resumeRow.setVisibility(snapshot.continueWatching.isEmpty() ? View.GONE : View.VISIBLE);
        appsRow.setVisibility(snapshot.apps.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.resumeEmpty).setVisibility(snapshot.continueWatching.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.appsEmpty).setVisibility(snapshot.apps.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.allowListings).setVisibility(snapshot.needsPermission ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.homeStatus)).setText(snapshot.notice.isEmpty()
                ? getString(R.string.native_counts, snapshot.continueWatching.size(), snapshot.previews.size())
                : snapshot.notice);
        findViewById(R.id.homeLoading).setVisibility(View.GONE);
        if (focusedRow != 0) {
            RecyclerView row = focusedRow == 1 ? resumeRow : appsRow;
            MediaRowAdapter adapter = focusedRow == 1 ? resumeAdapter : appsAdapter;
            String selected = focusedRow == 1 ? selectedResume : selectedApp;
            if (adapter.getItemCount() > 0) focus(row, Math.max(0, adapter.positionOf(selected)));
            else findViewById(R.id.openSearch).requestFocus();
        }
    }

    private void focus(RecyclerView row, int position) {
        SearchResultsAdapter.focusPosition(row, position);
    }

    private void open(MediaEntity entity) {
        ExternalAppLauncher.MediaLaunch outcome = new ExternalAppLauncher(this).openMedia(entity);
        if (outcome.opened) {
            if (outcome.homeFallback) Toast.makeText(this, outcome.message, Toast.LENGTH_LONG).show();
        } else {
            launchError = new AlertDialog.Builder(this).setTitle(R.string.native_unavailable)
                    .setMessage(outcome.message).setPositiveButton(R.string.close, null).create();
            launchError.setOnDismissListener(dialog -> { launchError = null; });
            launchError.show();
        }
    }

    private void allowListings() {
        boolean asked = getPreferences(MODE_PRIVATE).getBoolean("askedListings", false);
        if (asked && !shouldShowRequestPermissionRationale(NativeMediaSource.READ_TV_LISTINGS)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } else {
            getPreferences(MODE_PRIVATE).edit().putBoolean("askedListings", true).apply();
            requestPermissions(new String[] {NativeMediaSource.READ_TV_LISTINGS}, TV_PERMISSION_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == TV_PERMISSION_REQUEST) {
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, R.string.listings_denied, Toast.LENGTH_LONG).show();
            refresh();
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) pendingFocusRow = 0;
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("resume", selectedResume);
        state.putString("app", selectedApp);
        state.putInt("row", resumeRow.hasFocus() ? 1 : appsRow.hasFocus() ? 2 : pendingFocusRow);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    @Override protected void onStop() {
        ++generation;
        if (pending != null) pending.cancel(true);
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (launchError != null) launchError.dismiss();
        worker.shutdownNow();
        artwork.close();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
