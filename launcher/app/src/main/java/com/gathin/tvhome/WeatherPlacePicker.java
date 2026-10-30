package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The weather place picker in Launcher settings: a name typed with the TV's keyboard, Open-Meteo's matches below it
 * as the typing pauses, OK on one keeps it. A plain Android dialog for now, like the rest of LauncherSettingsActivity,
 * until the new Launcher settings page is designed (BACKLOG.md); WeatherPlace and WeatherSettings hold the logic.
 */
final class WeatherPlacePicker {
    interface OnPicked {
        /** A place, or null for "Use default" (tv.properties). */
        void picked(WeatherPlace place);
    }

    private static final String TAG = "TvHome.Weather";
    /** Search once the typing pauses this long, not on every letter. */
    static final long DEBOUNCE_MS = 450;

    private final Activity activity;
    private final OnPicked onPicked;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<WeatherPlace> places = new ArrayList<>();
    private EditText name;
    private TextView status;
    private BaseAdapter adapter;
    private AlertDialog dialog;
    /** Counts searches; an answer for an older one than the latest is dropped. */
    private int generation;
    private final Runnable searchNow = this::search;

    private WeatherPlacePicker(Activity activity, OnPicked onPicked) {
        this.activity = activity;
        this.onPicked = onPicked;
    }

    static void show(Activity activity, boolean hasPlace, OnPicked onPicked) {
        new WeatherPlacePicker(activity, onPicked).open(hasPlace);
    }

    private void open(boolean hasPlace) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        name = new EditText(activity);
        name.setSingleLine(true);
        name.setHint(R.string.weather_place_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(activity);
        status.setTextSize(14);
        status.setTextColor(0xFFACB8C1);
        status.setText(R.string.weather_place_type);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Math.round(8 * density);
        box.addView(status, slp);

        ListView list = new ListView(activity);
        adapter = new Adapter();
        list.setAdapter(adapter);
        // The theme's selector is barely lighter than the dialog; the focused place needs to stand out on a TV.
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(0x55FFFFFF));
        selector.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0x55FFFFFF));
        selector.addState(new int[]{}, new ColorDrawable(0));
        list.setSelector(selector);
        list.setOnItemClickListener((parent, view, position, id) -> {
            WeatherPlace p = places.get(position);
            close();
            onPicked.picked(p);
        });
        box.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(300 * density)));

        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                main.removeCallbacks(searchNow);
                main.postDelayed(searchNow, DEBOUNCE_MS);
            }
        });
        name.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            main.removeCallbacks(searchNow);
            search();
            hideKeyboard();
            return true;
        });
        // A single-line field keeps Down for its cursor on Android TV, so Down from it is moved on here: to the
        // matches, or to the buttons while there are none.
        name.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                hideKeyboard();
                if (!places.isEmpty()) {
                    list.requestFocus();
                    list.setSelection(0);
                } else {
                    View next = name.focusSearch(View.FOCUS_DOWN);
                    if (next != null) next.requestFocus();
                }
            }
            return true;
        });

        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(R.string.weather_place_title)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null);
        if (hasPlace) {
            b.setNeutralButton(R.string.weather_place_default, (d, w) -> {
                close();
                onPicked.picked(null);
            });
        }
        dialog = b.create();
        dialog.setOnDismissListener(d -> {
            main.removeCallbacks(searchNow);
            generation++;
        });
        dialog.show();
        name.requestFocus();
    }

    private void close() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    /** Down with the keyboard, so the matches show and Down reaches them. */
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(name.getWindowToken(), 0);
    }

    /** Searches for what is typed now, off the main thread; only the latest search's answer is shown. */
    private void search() {
        String typed = name.getText().toString();
        int mine = ++generation;
        if (!WeatherPlace.searchable(typed)) {
            places.clear();
            adapter.notifyDataSetChanged();
            status.setText(R.string.weather_place_type);
            return;
        }
        status.setText(R.string.weather_place_searching);
        String url = WeatherPlace.searchUrl(typed, Locale.getDefault().getLanguage());
        new Thread(() -> {
            String body = WeatherStore.download(url);
            List<WeatherPlace> found = null;
            int failure = R.string.weather_place_offline;
            if (body != null) {
                try {
                    found = WeatherPlace.parseSearch(body);
                } catch (JSONException e) {
                    Log.w(TAG, "unexpected place search answer", e);
                    failure = R.string.weather_place_failed;
                }
            }
            List<WeatherPlace> result = found;
            int failed = failure;
            main.post(() -> show(mine, typed.trim(), result, failed));
        }, "weather-place").start();
    }

    /** result is null when the search failed; failed then names why (offline, or an answer it could not read). */
    private void show(int mine, String typed, List<WeatherPlace> result, int failed) {
        if (mine != generation || activity.isDestroyed()) return;
        places.clear();
        if (result == null) {
            status.setText(failed);
        } else if (result.isEmpty()) {
            status.setText(activity.getString(R.string.weather_place_none, typed));
        } else {
            places.addAll(result);
            status.setText(R.string.weather_place_pick);
        }
        adapter.notifyDataSetChanged();
    }

    /** Two lines a place: the name over its region and country. */
    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return places.size();
        }

        @Override
        public Object getItem(int position) {
            return places.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView : activity.getLayoutInflater()
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            WeatherPlace p = places.get(position);
            ((TextView) v.findViewById(android.R.id.text1)).setText(p.name);
            ((TextView) v.findViewById(android.R.id.text2)).setText(p.detail());
            return v;
        }
    }
}
