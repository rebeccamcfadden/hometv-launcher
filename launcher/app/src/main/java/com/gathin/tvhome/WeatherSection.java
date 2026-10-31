package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Launcher settings' Weather section (design_preview/src/screens/panes/weather.ts, V2 design r2-weather A with B's
 * quick picks). The summary: the place Home's weather is for, with the weather there now (or "Offline · updated
 * 19:52", or nothing offline), Change city and Use default, and the unit (°C, °F, or the TV default, which follows the
 * TV's region). Change city is a sub-state: the 6 × 6 keyboard on the left and the places on the right, which with
 * nothing typed are the place now showing and quick picks (the default and the places picked before). Typing, on the
 * keyboard or a real one, searches Open-Meteo's geocoding once the typing pauses (WeatherPlace); offline the quick
 * picks stay and the search says it needs the internet. OK on a place keeps it; Back leaves without a change.
 */
final class WeatherSection implements SettingsSection {
    private static final String TAG = "TvHome.Weather";
    /** Search once the typing pauses this long, not on every letter. */
    static final long DEBOUNCE_MS = 450;
    /** The matches that fit beside the keyboard. */
    private static final int SHOWN = 6;
    private static final String[] KEY_ROWS = {"abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890"};
    private static final String KEY_CHANGE = "w-change";
    private static final float KEY = 56, KEY_GAP = 8;

    private enum Search { IDLE, SEARCHING, FOUND, NONE, OFFLINE, FAILED }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable searchNow = this::search;
    private Context ctx;
    private Host host;
    private LinearLayout root;
    private View first;
    private boolean changing;
    private String query = "";
    private Search state = Search.IDLE;
    private final List<WeatherPlace> found = new ArrayList<>();
    /** Counts searches; an answer for an older one than the latest is dropped. */
    private volatile int generation;

    // The summary's card, painted again when the weather arrives.
    private WeatherStore store;
    private String storeQuery;
    private TextView nowTemp;
    private TextView nowWords;
    // Change city's bar and matches.
    private TextView typed;
    private TextView ghost;
    private LinearLayout results;

    @Override
    public String id() {
        return "weather";
    }

    @Override
    public int label() {
        return R.string.settings_weather;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_cloud;
    }

    @Override
    public CharSequence value(Context ctx) {
        WeatherPlace p = WeatherPrefs.load(ctx).place;
        return p != null ? p.name : WeatherPrefs.defaultName(ctx, R.string.weather_default_value);
    }

    @Override
    public View pane(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        root = SettingsKit.column(ctx);
        render();
        return root;
    }

    @Override
    public View firstFocus() {
        return first;
    }

    @Override
    public boolean onBack() {
        if (!changing) return false;
        closeChange();
        return true;
    }

    @Override
    public boolean onText(char c) {
        if (!changing || !WeatherPlace.typeable(c)) return false;
        setQuery(SearchQuery.type(query, c));
        return true;
    }

    /** No search answer is waited for once the page has closed. */
    @Override
    public void onClose() {
        main.removeCallbacks(searchNow);
        generation++;
    }

    private void render() {
        root.removeAllViews();
        typed = ghost = nowTemp = nowWords = null;
        results = null;
        if (changing) renderChange();
        else renderSummary();
    }

    // ---- The summary ---------------------------------------------------------------------------------------------

    private void renderSummary() {
        WeatherSettings s = WeatherPrefs.load(ctx);
        root.addView(SettingsKit.eyebrow(ctx, ctx.getString(R.string.settings_weather)));
        SettingsKit.add(root, SettingsKit.title(ctx, ctx.getString(R.string.weather_title), false), 10);
        SettingsKit.addWide(root, card(s.place), 26);

        View change = SettingsKit.button(ctx, ctx.getString(R.string.weather_change_city), R.drawable.ic_ui_search,
                false, "w-place", KEY_CHANGE, true, v -> openChange());
        View useDefault = SettingsKit.button(ctx, ctx.getString(R.string.weather_use_default), 0, false, "w-place",
                "w-default", false, v -> {
                    if (WeatherPrefs.load(ctx).place == null) host.shell().toast(ctx.getString(R.string.weather_already_default));
                    else choose(null);
                });
        SettingsKit.addWide(root, line(R.string.weather_place_label, R.string.weather_place_sub,
                SettingsKit.actions(ctx, change, useDefault)), 8);
        SettingsKit.addWide(root, SettingsKit.divider(ctx), 0);

        String tv = WeatherSettings.unitSymbol(s.defaultFahrenheit);
        View unit = SettingsKit.segmented(ctx, "w-unit", new String[]{"c", "f", "tv"},
                new CharSequence[]{WeatherSettings.unitSymbol(false), WeatherSettings.unitSymbol(true),
                        ctx.getString(R.string.weather_unit_tv, tv)},
                s.fahrenheitChoice == null ? "tv" : s.fahrenheitChoice ? "f" : "c", false, value -> {
                    WeatherPrefs.setFahrenheit(ctx, "tv".equals(value) ? null : "f".equals(value));
                    host.valueChanged();
                    paintNow();
                });
        SettingsKit.addWide(root, line(R.string.weather_unit_label, R.string.weather_unit_sub, unit), 0);
        SettingsKit.addWide(root, SettingsKit.divider(ctx), 0);
        SettingsKit.add(root, SettingsKit.note(ctx, ctx.getString(R.string.weather_pane_note)), 22);
        first = change;
        paintNow();
    }

    /** {@code .place}: the pin, the place's name over its region and country, and the weather there on the right. */
    private View card(WeatherPlace place) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.px(30), Ui.px(26), Ui.px(30), Ui.px(26));
        card.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_L, Ui.LINE, Ui.LINE_W));
        card.addView(Ui.icon(ctx, R.drawable.ic_ui_pin, 30, Ui.GOOD), new LinearLayout.LayoutParams(Ui.px(30), Ui.px(30)));
        LinearLayout names = new LinearLayout(ctx);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.line(ctx, 34, Ui.TEXT_1, Ui.REGULAR);
        name.setGravity(Gravity.START);
        name.setText(place != null ? place.name : WeatherPrefs.defaultName(ctx, R.string.weather_default_name));
        names.addView(name);
        TextView detail = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        detail.setGravity(Gravity.START);
        detail.setText(place != null ? place.detail() : ctx.getString(R.string.weather_default_detail));
        SettingsKit.add(names, detail, 4);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = Ui.px(24);
        card.addView(names, nlp);
        LinearLayout now = new LinearLayout(ctx);
        now.setOrientation(LinearLayout.VERTICAL);
        now.setGravity(Gravity.END);
        nowTemp = Ui.line(ctx, 52, Ui.TEXT_1, Ui.REGULAR);
        nowTemp.setGravity(Gravity.END);
        now.addView(nowTemp);
        nowWords = Ui.line(ctx, 19, Ui.TEXT_2, Ui.REGULAR);
        nowWords.setGravity(Gravity.END);
        now.addView(nowWords);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.leftMargin = Ui.px(24);
        card.addView(now, wlp);
        return card;
    }

    /** {@code .wrow}: what a choice is and a line about it on the left, its control on the right. */
    private View line(int title, int sub, View control) {
        LinearLayout row = Kit.unclip(new LinearLayout(ctx));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.px(22), 0, Ui.px(22));
        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView k = Ui.text(ctx, 23, Ui.TEXT_1, Ui.REGULAR);
        k.setText(title);
        words.addView(k);
        TextView s = Ui.text(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        s.setText(sub);
        SettingsKit.add(words, s, 4);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.px(24);
        row.addView(control, clp);
        return row;
    }

    /**
     * The weather now at the place, from a WeatherStore of its own for the place and unit chosen (it shares Home's
     * cache file, so the answer is often there already). Offline: the last answer with when it was fetched, or none.
     */
    private void paintNow() {
        WeatherSettings s = WeatherPrefs.load(ctx);
        String q = WeatherStore.query(s.latitude, s.longitude, s.fahrenheit);
        if (store == null || !q.equals(storeQuery)) {
            storeQuery = q;
            store = new WeatherStore(ctx, w -> paintNow());
        }
        store.refresh();
        if (nowTemp == null) return;
        boolean online = NetworkState.read(ctx).validated;
        Weather w = store.current();
        long ms = System.currentTimeMillis();
        LocalDateTime now = w == null ? null : HomeClock.plausible(ms) ? w.localTime(ms)
                : w.current != null ? w.current.time : null;
        Weather.Reading reading = now == null ? null : w.now(now);
        if (reading == null) {
            nowTemp.setVisibility(View.GONE);
            nowWords.setText(online ? R.string.weather_now_loading : R.string.weather_now_unavailable);
            return;
        }
        nowTemp.setVisibility(View.VISIBLE);
        nowTemp.setText(Weather.degrees(reading.temp));
        nowWords.setText(online ? ctx.getString(HomePage.conditionText(Weather.condition(reading.code)))
                : ctx.getString(R.string.weather_now_offline,
                        DateFormat.getTimeFormat(ctx).format(new Date(store.fetchedAt()))));
    }

    /** OK on a place (null for the default): saved, and the summary shows it, with focus back on Change city. */
    private void choose(WeatherPlace place) {
        WeatherPrefs.setPlace(ctx, place);
        host.valueChanged();
        changing = false;
        resetSearch();
        render();
        focus(KEY_CHANGE);
        host.shell().toast(ctx.getString(R.string.weather_place_saved,
                place != null ? place.label() : WeatherPrefs.defaultName(ctx, R.string.weather_default_name)));
    }

    private void focus(String key) {
        View v = Focus.byKey(root, key);
        if (v != null) host.shell().focus(v);
        else host.shell().refocus();
    }

    // ---- Change city ---------------------------------------------------------------------------------------------

    private void openChange() {
        changing = true;
        resetSearch();
        render();
        focus("wk-a");
    }

    private void closeChange() {
        changing = false;
        resetSearch();
        render();
        focus(KEY_CHANGE);
    }

    private void resetSearch() {
        main.removeCallbacks(searchNow);
        generation++;
        query = "";
        state = Search.IDLE;
        found.clear();
    }

    private void renderChange() {
        LinearLayout crumb = new LinearLayout(ctx);
        crumb.setOrientation(LinearLayout.HORIZONTAL);
        crumb.setGravity(Gravity.CENTER_VERTICAL);
        crumb.addView(Ui.icon(ctx, R.drawable.ic_ui_back, 20, Ui.TEXT_3), new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20)));
        TextView c = Ui.line(ctx, Ui.T_SMALL, Ui.TEXT_3, Ui.REGULAR);
        c.setText(R.string.weather_crumb);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.px(8);
        crumb.addView(c, clp);
        root.addView(crumb);

        // {@code .qbar}: the search icon, what was typed, and the ghost completion after it.
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        bar.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, Ui.LINE_W));
        bar.addView(Ui.icon(ctx, R.drawable.ic_ui_search, 28, Ui.TEXT_2), new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
        typed = Ui.line(ctx, 28, Ui.TEXT_1, Ui.MEDIUM);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.px(14);
        bar.addView(typed, tlp);
        ghost = Ui.line(ctx, 28, Ui.GHOST, Ui.REGULAR);
        bar.addView(ghost);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(70));
        blp.topMargin = Ui.px(14);
        root.addView(bar, blp);

        LinearLayout body = Kit.unclip(new LinearLayout(ctx));
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(keyboard(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        results = SettingsKit.column(ctx);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rlp.leftMargin = Ui.px(30);
        body.addView(results, rlp);
        SettingsKit.addWide(root, body, 20);
        first = Focus.byKey(root, "wk-a");
        paintSearch();
    }

    /** {@code .kbp}: the 6 × 6 keyboard of Search, then Space, ⌫ and Clear. */
    private View keyboard() {
        LinearLayout panel = Kit.unclip(new LinearLayout(ctx));
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.px(24), Ui.px(22), Ui.px(24), Ui.px(22));
        panel.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_XL, Ui.LINE, Ui.LINE_W));
        for (String row : KEY_ROWS) {
            LinearLayout r = keyRow(panel);
            for (char ch : row.toCharArray()) {
                key(r, String.valueOf(ch), "wk-" + ch, KEY, ch == 'a', v -> setQuery(SearchQuery.type(query, ch)));
            }
        }
        LinearLayout last = keyRow(panel);
        key(last, ctx.getString(R.string.search_key_space), "wk-space", 184, false,
                v -> setQuery(SearchQuery.clean(query + " ")));
        FrameLayout del = new FrameLayout(ctx);
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_ui_backspace);
        icon.setImageTintList(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        icon.setDuplicateParentStateEnabled(true);
        del.addView(icon, new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        del.setContentDescription(ctx.getString(R.string.search_key_delete));
        keyLook(del);
        Focus.make(del, "wkb", "wk-del", false, v -> setQuery(SearchQuery.delete(query)));
        last.addView(del, keyParams(last, KEY));
        key(last, ctx.getString(R.string.search_key_clear), "wk-clear", 120, false, v -> setQuery(""));
        return panel;
    }

    private LinearLayout keyRow(LinearLayout panel) {
        LinearLayout r = Kit.unclip(new LinearLayout(ctx));
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = panel.getChildCount() == 0 ? 0 : Ui.px(KEY_GAP);
        panel.addView(r, lp);
        return r;
    }

    /** {@code .key}: 56 px square on the surface; focused, the accent fill, semibold, scaled 1.08. */
    private void key(LinearLayout row, String label, String key, float width, boolean isDefault,
                     View.OnClickListener onOk) {
        TextView k = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        k.setOnFocusChangeListener((v, focused) -> k.setTypeface(focused ? Ui.MEDIUM : Ui.REGULAR));
        keyLook(k);
        Focus.make(k, "wkb", key, isDefault, onOk);
        row.addView(k, keyParams(row, width));
    }

    private static void keyLook(View k) {
        k.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_M, Ui.LINE, Ui.LINE_W), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_M, Ui.ACCENT, Ui.LINE_W)));
        Ui.scaleOnFocus(k, 1.08f);
    }

    private static LinearLayout.LayoutParams keyParams(LinearLayout row, float width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(width), Ui.px(KEY));
        lp.leftMargin = row.getChildCount() == 0 ? 0 : Ui.px(KEY_GAP);
        return lp;
    }

    /** A new query: the bar shows it at once; the search waits until the typing pauses. */
    private void setQuery(String q) {
        if (q.equals(query)) return;
        query = q;
        main.removeCallbacks(searchNow);
        generation++;
        found.clear();
        if (WeatherPlace.searchable(query)) {
            state = Search.SEARCHING;
            main.postDelayed(searchNow, DEBOUNCE_MS);
        } else {
            state = Search.IDLE;
        }
        paintSearch();
    }

    /**
     * Searches for what is typed now, off the main thread, in each of {@link WeatherPlace#searchLanguages}; only the
     * latest search's answer is shown, and it fails only when every language's search did.
     */
    private void search() {
        String asked = query;
        int mine = ++generation;
        Locale tv = Locale.getDefault();
        List<String> languages = WeatherPlace.searchLanguages(tv.getLanguage());
        new Thread(() -> {
            List<List<WeatherPlace>> answers = new ArrayList<>();
            boolean any = false, unreadable = false;
            for (String language : languages) {
                if (mine != generation) return;
                String body = WeatherStore.download(WeatherPlace.searchUrl(asked, language));
                if (body == null) continue;
                try {
                    answers.add(WeatherPlace.parseSearch(body, tv));
                    any = true;
                } catch (JSONException e) {
                    Log.w(TAG, "unexpected place search answer", e);
                    unreadable = true;
                }
            }
            List<WeatherPlace> answer = any ? WeatherPlace.merge(asked, answers) : null;
            boolean failed = unreadable;
            main.post(() -> {
                if (mine != generation || !changing) return;
                found.clear();
                if (answer != null) {
                    found.addAll(answer);
                    state = answer.isEmpty() ? Search.NONE : Search.FOUND;
                } else {
                    state = failed || NetworkState.read(ctx).validated ? Search.FAILED : Search.OFFLINE;
                }
                paintSearch();
            });
        }, "weather-place").start();
    }

    /** The bar and the list beside the keyboard, for the query and the search's state. */
    private void paintSearch() {
        if (typed == null || results == null) return;
        boolean empty = query.isEmpty();
        typed.setText(empty ? ctx.getString(R.string.weather_type_city) : query);
        typed.setTextColor(empty ? Ui.TEXT_3 : Ui.TEXT_1);
        typed.setTypeface(empty ? Ui.REGULAR : Ui.MEDIUM);
        String g = state == Search.FOUND ? WeatherPlace.ghost(query, found) : null;
        ghost.setText(g == null ? "" : g);
        View focused = host.shell().focused();
        String focusedKey = focused != null && Focus.isInside(results, focused) ? Focus.key(focused) : null;
        results.removeAllViews();
        WeatherPlace now = WeatherPrefs.load(ctx).place;
        switch (state) {
            case SEARCHING:
                head(R.string.weather_place_searching, 0);
                break;
            case FOUND: {
                int n = Math.min(SHOWN, found.size());
                head(0, 0).setText(ctx.getResources().getQuantityString(R.plurals.weather_places, n, n));
                for (int i = 0; i < n; i++) {
                    WeatherPlace p = found.get(i);
                    result(p, p.name, WeatherPlace.matched(p.name, query), p.detail(), false, "wr-" + i, i == 0);
                }
                break;
            }
            case NONE:
                head(R.string.weather_no_places, 0);
                tip(ctx.getString(R.string.weather_place_none, query.trim()));
                break;
            case OFFLINE:
                offline();
                tip(ctx.getString(R.string.weather_place_offline));
                quickPicks(now);
                break;
            case FAILED:
                tip(ctx.getString(R.string.weather_place_failed));
                quickPicks(now);
                break;
            default:
                head(R.string.weather_now_showing, 0);
                result(now, now != null ? now.name : WeatherPrefs.defaultName(ctx, R.string.weather_default_name), 0,
                        now != null ? now.detail() : ctx.getString(R.string.weather_default_detail), true, "wr-now",
                        true);
                if (!empty) tip(ctx.getString(R.string.weather_more_letters));
                quickPicks(now);
                break;
        }
        // A place that had focus was rebuilt: focus its new row, else the first place, else the keyboard.
        if (focusedKey == null) return;
        View again = Focus.byKey(results, focusedKey);
        if (again == null && !Focus.all(results).isEmpty()) again = Focus.all(results).get(0);
        if (again == null) again = Focus.byKey(root, "wk-a");
        if (again != null) host.shell().focus(again);
    }

    /** The default, unless it is showing, then the places picked before. */
    private void quickPicks(WeatherPlace now) {
        List<WeatherPlace> picks = new ArrayList<>();
        if (now != null) picks.add(null);
        for (WeatherPlace p : WeatherPrefs.recent(ctx)) if (!WeatherPlace.same(p, now)) picks.add(p);
        if (picks.isEmpty()) return;
        head(R.string.weather_quick_picks, results.getChildCount() == 0 ? 0 : 22);
        for (int i = 0; i < picks.size(); i++) {
            WeatherPlace p = picks.get(i);
            String line = p == null ? ctx.getString(R.string.weather_pick_default)
                    : ctx.getString(R.string.weather_pick_recent, p.detail());
            result(p, p != null ? p.name : WeatherPrefs.defaultName(ctx, R.string.weather_default_name), 0, line,
                    false, "wr-pick-" + i, false);
        }
    }

    private TextView head(int text, float top) {
        TextView h = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.MEDIUM);
        h.setGravity(Gravity.START);
        if (text != 0) h.setText(text);
        h.setPadding(Ui.px(20), 0, 0, 0);
        SettingsKit.addWide(results, h, top);
        return h;
    }

    private void tip(CharSequence text) {
        TextView t = Ui.text(ctx, 19, Ui.TEXT_3, Ui.REGULAR);
        t.setText(text);
        t.setLineSpacing(0f, 1.45f);
        t.setPadding(Ui.px(20), 0, Ui.px(20), 0);
        SettingsKit.addWide(results, t, results.getChildCount() == 0 ? 4 : 18);
    }

    /** {@code .offline-pill}: the crossed-out Wi-Fi and "Offline", in amber. */
    private void offline() {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.px(18), Ui.px(8), Ui.px(18), Ui.px(8));
        pill.setBackground(Ui.round((Ui.STAR & 0x00FFFFFF) | 0x1F000000, Ui.RADIUS_PILL));
        pill.addView(Ui.icon(ctx, R.drawable.ic_ui_wifi_off, 24, Ui.STAR), new LinearLayout.LayoutParams(Ui.px(24), Ui.px(24)));
        TextView t = Ui.line(ctx, Ui.T_SMALL, Ui.STAR, Ui.REGULAR);
        t.setText(R.string.offline);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(10);
        pill.addView(t, lp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = Ui.px(20);
        plp.topMargin = Ui.px(4);
        results.addView(pill, plp);
    }

    /**
     * {@code .res}: the pin, the name (its first {@code hit} letters in the accent) over its line, 78 px tall. The
     * place now showing has the green tint. Focused: the accent fill, dark text, scale 1.02. OK keeps the place.
     */
    private void result(WeatherPlace place, String name, int hit, String line, boolean current, String key,
                        boolean isDefault) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(78));
        row.setPadding(Ui.px(20), Ui.px(8), Ui.px(20), Ui.px(8));
        row.setBackground(Ui.states(Ui.round(current ? Ui.GOOD_SOFT : 0, Ui.RADIUS_M), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_M)));
        ImageView pin = new ImageView(ctx);
        pin.setImageResource(R.drawable.ic_ui_pin);
        int pinColor = current ? Ui.GOOD : Ui.TEXT_3;
        pin.setImageTintList(Ui.colors(pinColor, pinColor, Ui.ON_ACCENT));
        pin.setDuplicateParentStateEnabled(true);
        row.addView(pin, new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26)));
        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.line(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.REGULAR);
        n.setGravity(Gravity.START);
        n.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        n.setDuplicateParentStateEnabled(true);
        n.setText(highlight(name, hit, false));
        words.addView(n);
        TextView l = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        l.setGravity(Gravity.START);
        l.setTextColor(Ui.colors(Ui.TEXT_3, Ui.TEXT_3, Ui.ON_ACCENT));
        l.setDuplicateParentStateEnabled(true);
        l.setText(line);
        SettingsKit.add(words, l, 2);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wlp.leftMargin = Ui.px(16);
        row.addView(words, wlp);
        row.setContentDescription(name + ", " + line);
        row.setOnFocusChangeListener((v, focused) -> n.setText(highlight(name, hit, focused)));
        Focus.make(row, "wres", key, isDefault, v -> {
            if (current) closeChange();
            else choose(place);
        });
        Ui.scaleOnFocus(row, 1.02f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(6);
        results.addView(row, lp);
    }

    /** {@code .res .hl}: the typed letters in the accent, semibold; on the focused row's accent fill, dark like the rest. */
    private static CharSequence highlight(String name, int hit, boolean focused) {
        if (hit <= 0 || focused) return name;
        SpannableString s = new SpannableString(name);
        s.setSpan(new ForegroundColorSpan(Ui.ACCENT), 0, hit, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, hit, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
}
