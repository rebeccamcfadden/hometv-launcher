package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home (design_preview/src/screens/home.ts and the .home styles in screens.css; README, Home). Resting: the greeting,
 * date, clock and the forecast for today and tomorrow over the household's app row. Moving down from the apps slides
 * the page up to Inputs, Shortcuts and the hourly weather, and dims the landscape; moving back up to the apps returns.
 * Hold-OK on an app opens its menu: Move, Remove from Home, App info.
 *
 * The clock, the weather and the rows are refreshed only while Home is on screen (HomeFrame): no receiver, no fetch
 * and no package scan run while an app or another page is in front.
 */
final class HomePage extends Screen {

    // Positions on the 1920 × 1080 stage, from screens.css.
    private static final int SHIFT = 577;
    private static final int HERO_TOP = 540;
    private static final int APPS_TOP = 777;
    private static final int LOWER_TOP = 1166;
    private static final int WEATHER_TOP = 1405;
    private static final int WEATHER_H = 190;
    private static final int APP_COLUMNS = 7;
    private static final int APP_GAP = 22;
    private static final float APP_W = (Ui.STAGE_W - 2f * Ui.SAFE_X - (APP_COLUMNS - 1) * APP_GAP) / APP_COLUMNS;
    private static final int APP_H = 136;
    /** Room above and below the app row for the lifted tile of Move and the focus scale. */
    private static final int APP_ROOM = 24;
    private static final int TILE_W = 208;
    private static final int SHORTCUT_W = 250;
    private static final int TILE_H = 141;
    private static final int TILE_GAP = 22;
    private static final int TILE_ROOM = 8;
    private static final int TILE_FILL = 0x0BFFFFFF;
    private static final int HOURS = 6;

    static final String GROUP_APPS = "apps";
    private static final String GROUP_INPUTS = "inputs";
    private static final String GROUP_SHORTCUTS = "shortcuts";
    private static final String GROUP_WEATHER = "weather";
    private static final String KEY_EMPTY = "app-empty";

    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private final MainActivity activity;
    private final Shell shell;
    private final HomeApps apps;
    private final WeatherStore weatherStore;

    private final View dim;
    private final FrameLayout content;
    private final View hero;
    private final TextView greeting, date, time, ampm;
    private final LinearLayout forecast;
    private final LinearLayout appRow;
    private final LinearLayout lower;
    private final LinearLayout inputsBox;
    private final FrameLayout inputsClip;
    private final LinearLayout inputsRow;
    private final FrameLayout weatherFrame;
    private final TextView weatherTemp, weatherDesc;
    private final LinearLayout weatherHours;
    /** 06-states A: the strip's "now" half, and what shows in the strip offline with no forecast saved. */
    private final View weatherNow, weatherNone;

    private List<String> full = new ArrayList<>();
    private List<String> shownApps = new ArrayList<>();
    private List<String> shownInputs = new ArrayList<>();
    private List<MenuPanel.Row> inputRows = new ArrayList<>();
    private AppTile moving;
    private boolean scrolled;
    private boolean visible;
    private int lastNetworkIcon;
    private boolean hadInternet;
    /** The network came back: fetch the weather now rather than after WeatherStore's pause between tries. */
    private boolean retryWeather;

    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            tick();
            // The clock was set (network time after a boot at the epoch): HTTPS works now, so fetch the weather.
            if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction())) weatherStore.retry();
        }
    };

    static HomePage create(MainActivity activity, NavBar.Host host) {
        return new HomePage(activity, host, new HomeFrame(activity));
    }

    private HomePage(MainActivity activity, NavBar.Host host, HomeFrame root) {
        super(activity.getString(R.string.nav_home), root, Layer.PAGE);
        this.activity = activity;
        this.shell = host.shell();
        apps = new HomeApps(activity, activity.config());
        weatherStore = new WeatherStore(activity, w -> renderWeather());
        Context ctx = activity;

        // The landscape shows through more than on other pages, darkening towards the apps (.home background).
        View shade = new View(ctx);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40090E15, 0x8C090E15, 0xEB090E15}));
        shade.setTag(R.id.ui_still, Boolean.TRUE);
        root.addView(shade, match());
        // .home::before: the scrolled state dims the landscape further.
        dim = new View(ctx);
        dim.setBackgroundColor(0x99090E15);
        dim.setAlpha(0f);
        dim.setTag(R.id.ui_still, Boolean.TRUE);
        root.addView(dim, match());

        content = new FrameLayout(ctx);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.px(Ui.STAGE_H + SHIFT)));
        root.addView(NavBar.build(ctx, host, Tab.HOME));

        // ---- Hero: greeting and date on the left, the clock and the forecast on the right.
        FrameLayout heroBox = new FrameLayout(ctx);
        hero = heroBox;
        LinearLayout hello = new LinearLayout(ctx);
        hello.setOrientation(LinearLayout.VERTICAL);
        greeting = Ui.line(ctx, 50, Ui.TEXT_1, LIGHT);
        hello.addView(greeting, wrap());
        date = Ui.line(ctx, 25, Ui.TEXT_2, Ui.REGULAR);
        LinearLayout.LayoutParams dlp = wrap();
        dlp.topMargin = Ui.px(10);
        hello.addView(date, dlp);
        heroBox.addView(hello, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));

        LinearLayout clock = new LinearLayout(ctx);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.END);
        LinearLayout timeRow = new LinearLayout(ctx);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setBaselineAligned(true);
        time = Ui.text(ctx, 92, Ui.TEXT_1, LIGHT);
        time.setSingleLine(true);
        timeRow.addView(time);
        ampm = Ui.text(ctx, 32, Ui.TEXT_2, Ui.REGULAR);
        ampm.setSingleLine(true);
        LinearLayout.LayoutParams alp = wrap();
        alp.leftMargin = Ui.px(18);
        timeRow.addView(ampm, alp);
        clock.addView(timeRow, wrap());
        forecast = new LinearLayout(ctx);
        forecast.setOrientation(LinearLayout.HORIZONTAL);
        forecast.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams flp = wrap();
        flp.topMargin = Ui.px(22);
        clock.addView(forecast, flp);
        heroBox.addView(clock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END));

        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        hlp.topMargin = Ui.px(HERO_TOP);
        hlp.leftMargin = hlp.rightMargin = Ui.px(Ui.SAFE_X);
        content.addView(heroBox, hlp);

        // ---- The app row; it scrolls sideways when there are more apps than its seven columns.
        appRow = new LinearLayout(ctx);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setClipChildren(false);
        appRow.setClipToPadding(false);
        appRow.setPadding(0, Ui.px(APP_ROOM), 0, Ui.px(APP_ROOM));
        content.addView(appRow, rowParams(0));

        // ---- Below the fold: Inputs and Shortcuts side by side, then the weather strip.
        lower = new LinearLayout(ctx);
        lower.setOrientation(LinearLayout.HORIZONTAL);
        lower.setClipChildren(false);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        llp.topMargin = Ui.px(LOWER_TOP - TILE_ROOM);
        content.addView(lower, llp);

        inputsBox = group(ctx, R.string.home_inputs);
        inputsClip = new FrameLayout(ctx);
        inputsClip.setClipChildren(false);
        inputsRow = new LinearLayout(ctx);
        // A focused tile scales up 5%; a parent that clips its children would cut it at its unscaled edges.
        inputsRow.setClipChildren(false);
        inputsRow.setClipToPadding(false);
        inputsRow.setOrientation(LinearLayout.HORIZONTAL);
        inputsRow.setPadding(Ui.px(TILE_ROOM), Ui.px(TILE_ROOM), Ui.px(TILE_ROOM), Ui.px(TILE_ROOM));
        inputsClip.addView(inputsRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        inputsBox.addView(inputsClip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        lower.addView(inputsBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout shortcuts = group(ctx, R.string.home_shortcuts);
        LinearLayout shortcutRow = new LinearLayout(ctx);
        shortcutRow.setClipChildren(false);
        shortcutRow.setClipToPadding(false);
        shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
        shortcutRow.setPadding(Ui.px(TILE_ROOM), Ui.px(TILE_ROOM), Ui.px(TILE_ROOM), Ui.px(TILE_ROOM));
        addTile(shortcutRow, tile(ctx, R.string.all_apps, R.drawable.ic_ui_grid, SHORTCUT_W, GROUP_SHORTCUTS,
                "sc-apps", false, v -> openAllApps()));
        addTile(shortcutRow, tile(ctx, R.string.launcher_settings, R.drawable.ic_ui_home, SHORTCUT_W, GROUP_SHORTCUTS,
                "sc-launcher", false, v -> activity.start(new Intent(activity, LauncherSettingsActivity.class))));
        addTile(shortcutRow, tile(ctx, R.string.tv_settings_short, R.drawable.ic_ui_settings, SHORTCUT_W,
                GROUP_SHORTCUTS, "sc-settings", false,
                v -> activity.start(SystemSettings.intent(activity, Settings.ACTION_SETTINGS))));
        shortcuts.addView(shortcutRow, wrap());
        lower.addView(shortcuts, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams lowerLp = (FrameLayout.LayoutParams) lower.getLayoutParams();
        lowerLp.leftMargin = lowerLp.rightMargin = Ui.px(Ui.SAFE_X - TILE_ROOM);

        // ---- The hourly weather strip.
        weatherFrame = new FrameLayout(ctx);
        int ring = Ui.px(AppTile.RING);
        weatherFrame.setPadding(ring, ring, ring, ring);
        weatherFrame.setBackground(Ui.states(Ui.round(0, 20 + AppTile.RING), null,
                Ui.round(0, 20 + AppTile.RING, Ui.ACCENT, AppTile.RING)));
        LinearLayout strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(Ui.px(36), 0, Ui.px(36), 0);
        strip.setBackground(Ui.round(TILE_FILL, 20, Ui.LINE, 1));
        weatherFrame.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(WEATHER_H)));
        // .weather-now: 330 px wide with a line on its right, as tall as its text.
        LinearLayout now = new LinearLayout(ctx);
        now.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout nowText = new LinearLayout(ctx);
        nowText.setOrientation(LinearLayout.VERTICAL);
        nowText.setPadding(0, 0, Ui.px(30), 0);
        TextView label = Ui.line(ctx, 22, Ui.TEXT_2, Ui.REGULAR);
        label.setText(R.string.weather);
        nowText.addView(label);
        weatherTemp = Ui.line(ctx, 64, Ui.TEXT_1, LIGHT);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.topMargin = Ui.px(4);
        nowText.addView(weatherTemp, tlp);
        weatherDesc = Ui.line(ctx, Ui.T_MIN, Ui.TEXT_2, Ui.REGULAR);
        LinearLayout.LayoutParams dsp = wrap();
        dsp.topMargin = Ui.px(6);
        nowText.addView(weatherDesc, dsp);
        now.addView(nowText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View divider = new View(ctx);
        divider.setBackgroundColor(Ui.LINE_STRONG);
        now.addView(divider, new LinearLayout.LayoutParams(Math.max(1, Ui.px(1)), ViewGroup.LayoutParams.MATCH_PARENT));
        strip.addView(now, new LinearLayout.LayoutParams(Ui.px(330), ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherHours = new LinearLayout(ctx);
        weatherHours.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(weatherHours, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        weatherNow = now;
        // .weather-none: the crossed-out Wi-Fi (28) and "Weather unavailable offline" (22 px, tertiary), 14 apart.
        LinearLayout none = new LinearLayout(ctx);
        none.setOrientation(LinearLayout.HORIZONTAL);
        none.setGravity(Gravity.CENTER_VERTICAL);
        none.addView(Ui.icon(ctx, R.drawable.ic_ui_wifi_off, 28, Ui.TEXT_3), new LinearLayout.LayoutParams(Ui.px(28),
                Ui.px(28)));
        TextView noneText = Ui.line(ctx, 22, Ui.TEXT_3, Ui.REGULAR);
        noneText.setText(R.string.states_weather_unavailable);
        LinearLayout.LayoutParams nlp = wrap();
        nlp.leftMargin = Ui.px(14);
        none.addView(noneText, nlp);
        none.setVisibility(View.GONE);
        weatherNone = none;
        strip.addView(none, wrap());
        weatherFrame.setVisibility(View.GONE);
        weatherFrame.setContentDescription(ctx.getString(R.string.weather));
        Focus.make(weatherFrame, GROUP_WEATHER, "weather", false,
                v -> shell.push(StubPage.create(activity, shell, R.string.weather, R.string.weather_note)));
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        wlp.topMargin = Ui.px(WEATHER_TOP - AppTile.RING);
        wlp.leftMargin = wlp.rightMargin = Ui.px(Ui.SAFE_X - AppTile.RING);
        content.addView(weatherFrame, wlp);

        root.page = this;
        // The nav's model tells Home when the network changes (back online, or the internet reachable again on the
        // same network: fetch the weather at once) and when an input is added, removed or renamed (MainActivity
        // calls NavModel.inputsChanged). A change while Home is hidden is kept for when it shows again.
        host.navModel().listen(model -> {
            if (WeatherStore.networkBack(lastNetworkIcon, model.networkIcon, hadInternet, model.internet)) {
                retryWeather = true;
            }
            // 06-states A: offline, the weather says so (renderWeather); back online it doesn't.
            boolean internetChanged = hadInternet != model.internet;
            lastNetworkIcon = model.networkIcon;
            hadInternet = model.internet;
            if (!visible) return;
            if (internetChanged) renderWeather();
            if (retryWeather) {
                retryWeather = false;
                weatherStore.retry();
            }
            refreshInputs();
        });
        refreshApps(true);
        refreshInputs();
        tick();
    }

    // ---- Screen ------------------------------------------------------------------------------------------------

    @Override
    View defaultFocus() {
        return appRow.getChildCount() > 0 ? appRow.getChildAt(0) : null;
    }

    @Override
    void onShow() {
        putDown();
        refreshApps(false);
    }

    @Override
    void onFocus(View v) {
        String group = Focus.group(v);
        setScrolled(GROUP_INPUTS.equals(group) || GROUP_SHORTCUTS.equals(group) || GROUP_WEATHER.equals(group));
        if (GROUP_APPS.equals(group)) {
            reveal(appRow, v, Ui.px(Ui.SAFE_X - AppTile.RING), Ui.px(Ui.STAGE_W - Ui.SAFE_X + AppTile.RING));
        } else if (GROUP_INPUTS.equals(group)) {
            reveal(inputsRow, v, Ui.px(TILE_ROOM), inputsClip.getWidth() - Ui.px(TILE_ROOM));
        }
    }

    @Override
    boolean onKey(Key key, View focused) {
        if (moving == null) return steer(key, focused);
        switch (key) {
            case LEFT:
                step(-1);
                break;
            case RIGHT:
                step(1);
                break;
            case OK:
            case BACK:
                putDown();
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * Keeps the D-pad on Home's rows when they are short. With few apps or inputs the nearest item past a row's end
     * can be the weather strip or a tile far below, so Left and Right stay in the apps row, or in Inputs and
     * Shortcuts, and stop at the ends. Down from the nav enters the apps, at the one last focused, as the prototype
     * does when the row is full.
     */
    private boolean steer(Key key, View focused) {
        String group = Focus.group(focused);
        boolean sideways = key == Key.LEFT || key == Key.RIGHT;
        if (sideways && GROUP_APPS.equals(group)) {
            shell.moveWithin(appRow, Shell.dirOf(key));
            return true;
        }
        if (sideways && GROUP_INPUTS.equals(group)) {
            // A tile scrolled out of the clip sits under Shortcuts, where All Apps can be nearer than the next input:
            // step along the row, and cross to Shortcuts only past its last input.
            int at = inputsRow.indexOfChild(focused);
            int next = at + (key == Key.RIGHT ? 1 : -1);
            if (at >= 0 && next >= 0 && next < inputsRow.getChildCount()) {
                shell.focus(inputsRow.getChildAt(next));
                return true;
            }
        }
        if (sideways && (GROUP_INPUTS.equals(group) || GROUP_SHORTCUTS.equals(group))) {
            shell.moveWithin(lower, Shell.dirOf(key));
            return true;
        }
        if (sideways && GROUP_WEATHER.equals(group)) return true;
        if (key == Key.DOWN && focused != null && !Focus.isInside(content, focused)) {
            View last = memory.get(GROUP_APPS);
            View target = last != null && last.isAttachedToWindow() && last.getParent() == appRow ? last : defaultFocus();
            if (target == null) return false;
            shell.focus(target);
            return true;
        }
        return false;
    }

    @Override
    boolean onLongOk(View focused) {
        if (!(focused instanceof AppTile) || moving != null || focused.getParent() != appRow) return false;
        AppTile tile = (AppTile) focused;
        List<MenuPanel.Row> rows = new ArrayList<>();
        rows.add(new MenuPanel.Row(activity.getString(R.string.tile_move), 0, () -> pickUp(tile)));
        rows.add(new MenuPanel.Row(activity.getString(R.string.library_remove_shortcut), 0, () -> remove(tile)));
        Intent info = SystemSettings.appInfo(activity, tile.pkg);
        if (info != null) {
            rows.add(new MenuPanel.Row(activity.getString(R.string.library_app_info), 0, () -> activity.start(info)));
        }
        shell.push(TileMenu.create(shell, tile, rows, hero));
        return true;
    }

    // ---- Resting and scrolled ----------------------------------------------------------------------------------

    private void setScrolled(boolean on) {
        if (on == scrolled) return;
        scrolled = on;
        long ms = Ui.ms(Ui.SLOW_MS);
        content.animate().translationY(on ? -Ui.px(SHIFT) : 0f).setDuration(ms).setInterpolator(Ui.EASE).start();
        hero.animate().alpha(on ? 0f : 1f).setDuration(ms).setInterpolator(null).start();
        dim.animate().alpha(on ? 1f : 0f).setDuration(ms).setInterpolator(null).start();
    }

    /** Slides a row so the focused item is inside view (both in the row's parent's coordinates). */
    private static void reveal(LinearLayout row, View item, int viewStart, int viewEnd) {
        Runnable r = () -> {
            if (item.getParent() != row) return;
            float target = Reveal.offset(row.getTranslationX(), row.getLeft() + item.getLeft(),
                    row.getLeft() + item.getRight(), viewStart, viewEnd);
            if (target == row.getTranslationX()) return;
            row.animate().translationX(target).setDuration(Ui.ms(240)).setInterpolator(Ui.EASE).start();
        };
        if (row.isLaidOut() && !row.isLayoutRequested()) r.run();
        else row.post(r);
    }

    // ---- The app row -------------------------------------------------------------------------------------------

    /** Rebuilds the row when the apps on it changed (installed, removed, added from All Apps). */
    private void refreshApps(boolean force) {
        full = apps.full();
        List<String> next = AppRow.visible(full, apps::shown);
        if (!force && next.equals(shownApps)) return;
        shownApps = next;
        View focused = shell.focused();
        boolean hadFocus = focused != null && focused.getParent() == appRow;
        appRow.removeAllViews();
        appRow.setTranslationX(0f);
        int gap = Ui.px(APP_GAP - 2 * AppTile.RING);
        if (next.isEmpty()) {
            View add = tile(activity, R.string.home_add_apps, R.drawable.ic_ui_grid, APP_W, GROUP_APPS, KEY_EMPTY, true,
                    v -> openAllApps());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(APP_W), Ui.px(APP_H));
            lp.leftMargin = Ui.px(AppTile.RING);
            lp.topMargin = Ui.px(AppTile.RING);
            appRow.addView(add, lp);
        }
        for (int i = 0; i < next.size(); i++) {
            String pkg = next.get(i);
            AppTile t = new AppTile(activity, pkg, apps.label(pkg), APP_W, APP_H, 14);
            Focus.make(t, GROUP_APPS, "app-" + pkg, i == 0, v -> open(pkg));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = gap;
            appRow.addView(t, lp);
        }
        int count = Math.max(1, next.size());
        FrameLayout.LayoutParams rlp = rowParams(count * (Ui.px(APP_W) + 2 * Ui.px(AppTile.RING) + gap));
        appRow.setLayoutParams(rlp);
        if (hadFocus || (focused != null && !focused.isAttachedToWindow())) shell.refocus();
    }

    private FrameLayout.LayoutParams rowParams(int width) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width > 0 ? width
                : ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.topMargin = Ui.px(APPS_TOP - AppTile.RING - APP_ROOM);
        lp.leftMargin = Ui.px(Ui.SAFE_X - AppTile.RING);
        return lp;
    }

    /** All Apps adds and removes apps; the row catches up when Home shows again (onShow). */
    private void openAllApps() {
        shell.push(AllAppsPage.create(activity, shell, apps));
    }

    private void open(String pkg) {
        Intent i = apps.launchIntent(pkg);
        if (i == null) {
            shell.toast(activity.getString(R.string.not_available));
            return;
        }
        LastOpened.record(activity, pkg);
        activity.start(i);
    }

    private void pickUp(AppTile tile) {
        moving = tile;
        tile.setMoving(true);
        shell.toast(activity.getString(R.string.home_moving));
    }

    /** One place left or right; the neighbour swaps over, so the tile being moved keeps its focus. */
    private void step(int delta) {
        int at = appRow.indexOfChild(moving);
        int to = at + delta;
        if (at < 0 || to < 0 || to >= appRow.getChildCount()) return;
        full = AppRow.move(full, shownApps, moving.pkg, delta);
        shownApps = AppRow.visible(full, apps::shown);
        View neighbour = appRow.getChildAt(to);
        appRow.removeView(neighbour);
        appRow.addView(neighbour, at);
        reveal(appRow, moving, Ui.px(Ui.SAFE_X - AppTile.RING), Ui.px(Ui.STAGE_W - Ui.SAFE_X + AppTile.RING));
    }

    /** Ends Move: the tile is put down where it is and the order saved. */
    private void putDown() {
        if (moving == null) return;
        moving.setMoving(false);
        moving = null;
        apps.save(full);
    }

    private void remove(AppTile tile) {
        int at = appRow.indexOfChild(tile);
        full = AppRow.remove(full, tile.pkg);
        apps.save(full);
        int next = LibraryApps.nextAfterRemoval(at, appRow.getChildCount());
        String nextKey = next < 0 ? KEY_EMPTY : Focus.key(appRow.getChildAt(next));
        refreshApps(true);
        View target = Focus.byKey(appRow, nextKey);
        shell.focus(target != null ? target : defaultFocus());
        shell.toast(activity.getString(R.string.home_removed));
    }

    // ---- Inputs ------------------------------------------------------------------------------------------------

    /**
     * Live TV first, then the HDMI ports and AV, by the Inputs dropdown's rules; no section when there are none. The
     * tiles are rebuilt only when the labels change; otherwise each keeps its place and focus and takes the new row's
     * action and state (a CEC device back under the same name with a new input id, or one that connected).
     */
    private void refreshInputs() {
        List<MenuPanel.Row> rows = activity.inputs().homeRows();
        List<String> labels = new ArrayList<>();
        for (MenuPanel.Row r : rows) labels.add(r.label.toString());
        inputRows = rows;
        inputsBox.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        if (labels.equals(shownInputs) && inputsRow.getChildCount() == rows.size()) {
            for (int i = 0; i < rows.size(); i++) describeInput(inputsRow.getChildAt(i), rows.get(i));
            return;
        }
        shownInputs = labels;
        inputsRow.removeAllViews();
        inputsRow.setTranslationX(0f);
        for (int i = 0; i < rows.size(); i++) {
            MenuPanel.Row r = rows.get(i);
            View t = tile(activity, r.label, R.drawable.ic_ui_tv, TILE_W, GROUP_INPUTS, "in-" + i + "-" + r.label,
                    i == 0, this::openInput);
            describeInput(t, r);
            addTile(inputsRow, t);
        }
        shell.refocus();
    }

    /** OK on an input tile runs the current row at its place, so a rebound row is never stale. */
    private void openInput(View tile) {
        int at = inputsRow.indexOfChild(tile);
        if (at < 0 || at >= inputRows.size()) return;
        Runnable action = inputRows.get(at).action;
        if (action != null) action.run();
    }

    private static void describeInput(View tile, MenuPanel.Row r) {
        tile.setContentDescription(r.status != null ? r.label + ", " + r.status : r.label);
    }

    // ---- Tiles -------------------------------------------------------------------------------------------------

    private static LinearLayout group(Context ctx, int heading) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(true);
        TextView h = Ui.line(ctx, Ui.T_H3, Ui.TEXT_1, Ui.REGULAR);
        h.setText(heading);
        LinearLayout.LayoutParams lp = wrap();
        lp.leftMargin = Ui.px(TILE_ROOM);
        lp.topMargin = Ui.px(TILE_ROOM);
        lp.bottomMargin = Ui.px(18 - TILE_ROOM);
        box.addView(h, lp);
        return box;
    }

    private View tile(Context ctx, int label, int icon, float width, String group, String key, boolean isDefault,
                      View.OnClickListener ok) {
        return tile(ctx, ctx.getString(label), icon, width, group, key, isDefault, ok);
    }

    /** .home-tile: an icon over a label, accent filled when focused. */
    private View tile(Context ctx, CharSequence label, int icon, float width, String group, String key,
                      boolean isDefault, View.OnClickListener ok) {
        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.px(14), 0, Ui.px(14), 0);
        t.setBackground(Ui.states(Ui.round(TILE_FILL, Ui.RADIUS_L, Ui.LINE, 1), null,
                Ui.round(Ui.ACCENT, Ui.RADIUS_L)));
        ImageView i = new ImageView(ctx);
        i.setImageResource(icon);
        i.setImageTintList(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        i.setDuplicateParentStateEnabled(true);
        t.addView(i, new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
        TextView l = Ui.line(ctx, 23, Ui.TEXT_1, Ui.REGULAR);
        l.setText(label);
        l.setGravity(Gravity.CENTER);
        l.setTextColor(Ui.colors(Ui.TEXT_1, Ui.TEXT_1, Ui.ON_ACCENT));
        l.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams llp = wrap();
        llp.topMargin = Ui.px(10);
        t.addView(l, llp);
        t.setContentDescription(label);
        Focus.make(t, group, key, isDefault, ok);
        Ui.scaleOnFocus(t, 1.05f);
        t.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(width), Ui.px(TILE_H)));
        return t;
    }

    /** Adds a tile to a row of tiles, 22 px after the one before it. */
    private static void addTile(LinearLayout row, View tile) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tile.getLayoutParams();
        lp.leftMargin = row.getChildCount() > 0 ? Ui.px(TILE_GAP) : 0;
        row.addView(tile, lp);
    }

    // ---- Clock and weather -------------------------------------------------------------------------------------

    private void tick() {
        long nowMs = System.currentTimeMillis();
        boolean plausible = HomeClock.plausible(nowMs);
        int show = plausible ? View.VISIBLE : View.INVISIBLE;
        greeting.setVisibility(show);
        date.setVisibility(show);
        time.setVisibility(show);
        boolean is24 = DateFormat.is24HourFormat(activity);
        ampm.setVisibility(plausible && !is24 ? View.VISIBLE : View.GONE);
        if (plausible) {
            Date now = new Date(nowMs);
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            greeting.setText(greetingText(HomeClock.greeting(cal.get(Calendar.HOUR_OF_DAY))));
            date.setText(DateUtils.formatDateTime(activity, nowMs,
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR));
            Locale locale = Locale.getDefault();
            time.setText(new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", locale).format(now));
            ampm.setText(new SimpleDateFormat("a", locale).format(now));
        }
        renderWeather();
    }

    private static int greetingText(HomeClock.Greeting g) {
        switch (g) {
            case MORNING:
                return R.string.greeting_morning;
            case AFTERNOON:
                return R.string.greeting_afternoon;
            case EVENING:
                return R.string.greeting_evening;
            default:
                return R.string.greeting_night;
        }
    }

    /**
     * The forecast under the clock and the weather strip, or neither while there is no weather to show. Offline
     * (06-states A) the last forecast stays, with "Offline · updated 19:52" in amber in place of the conditions; with
     * none saved, the strip and the line under the clock say "Weather unavailable offline".
     */
    private void renderWeather() {
        Weather w = weatherStore.current();
        boolean offline = !hadInternet;
        long nowMs = System.currentTimeMillis();
        LocalDateTime now = w == null ? null : HomeClock.plausible(nowMs) ? w.localTime(nowMs)
                : w.current != null ? w.current.time : null;
        Weather.Reading reading = now == null ? null : w.now(now);
        forecast.removeAllViews();
        if (now != null) {
            LocalDate today = now.toLocalDate();
            addForecast(w.day(today), R.string.weather_today);
            addForecast(w.day(today.plusDays(1)), R.string.weather_tomorrow);
        }
        if (offline && w == null) {
            TextView quiet = Ui.line(activity, 22, Ui.TEXT_3, Ui.REGULAR);
            quiet.setText(R.string.states_weather_unavailable);
            forecast.addView(quiet);
        }
        // Invisible rather than gone, so the greeting keeps its place, aligned with the bottom of the clock.
        forecast.setVisibility(forecast.getChildCount() > 0 ? View.VISIBLE : View.INVISIBLE);

        boolean unavailable = offline && w == null;
        weatherNone.setVisibility(unavailable ? View.VISIBLE : View.GONE);
        weatherNow.setVisibility(unavailable ? View.GONE : View.VISIBLE);
        weatherHours.setVisibility(unavailable ? View.GONE : View.VISIBLE);
        if (unavailable) {
            weatherFrame.setVisibility(View.VISIBLE);
            weatherFrame.setContentDescription(activity.getString(R.string.states_weather_unavailable));
            return;
        }
        if (reading == null) {
            boolean hadFocus = shell.focused() == weatherFrame;
            weatherFrame.setVisibility(View.GONE);
            if (hadFocus) shell.refocus();
            return;
        }
        weatherFrame.setVisibility(View.VISIBLE);
        weatherTemp.setText(degrees(reading.temp));
        Weather.Day today = w.day(now.toLocalDate());
        String words = activity.getString(conditionText(Weather.condition(reading.code)));
        String updated = offline ? StateUi.updated(activity, weatherStore.fetchedAt()) : null;
        if (updated != null) {
            weatherDesc.setText(activity.getString(R.string.states_weather_updated, updated));
            weatherDesc.setTextColor(StateUi.amber());
        } else {
            weatherDesc.setText(today == null ? words
                    : activity.getString(R.string.weather_summary, words, degrees(today.max), degrees(today.min)));
            weatherDesc.setTextColor(Ui.TEXT_2);
        }
        weatherHours.removeAllViews();
        boolean is24 = DateFormat.is24HourFormat(activity);
        for (Weather.Reading h : w.nextHours(now, HOURS)) {
            LinearLayout col = new LinearLayout(activity);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView t = Ui.line(activity, Ui.T_SMALL, Ui.TEXT_2, Ui.REGULAR);
            t.setText(Weather.hourLabel(h.time.getHour(), is24));
            col.addView(t, wrap());
            ImageView i = Ui.icon(activity, iconRes(h.icon()), 24, Ui.TEXT_2);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.px(24), Ui.px(24));
            ilp.topMargin = ilp.bottomMargin = Ui.px(10);
            col.addView(i, ilp);
            TextView d = Ui.line(activity, Ui.T_SMALL, Ui.TEXT_1, Ui.BOLD);
            d.setText(degrees(h.temp));
            col.addView(d, wrap());
            weatherHours.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        weatherFrame.setContentDescription(activity.getString(R.string.weather) + ", " + degrees(reading.temp) + ", "
                + weatherDesc.getText());
    }

    private void addForecast(Weather.Day day, int label) {
        if (day == null) return;
        if (forecast.getChildCount() > 0) {
            View sep = new View(activity);
            sep.setBackgroundColor(Ui.LINE_STRONG);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, Ui.px(1)), Ui.px(26));
            lp.leftMargin = Ui.px(12 + 14);
            lp.rightMargin = Ui.px(14);
            forecast.addView(sep, lp);
        }
        ImageView i = Ui.icon(activity, iconRes(Weather.icon(day.code, true)), 24, Ui.TEXT_2);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.px(24), Ui.px(24));
        ilp.rightMargin = Ui.px(12);
        forecast.addView(i, ilp);
        TextView t = Ui.line(activity, 22, Ui.TEXT_2, Ui.REGULAR);
        t.setText(activity.getString(label, Weather.degrees(day.max, weatherStore.fahrenheit())));
        forecast.addView(t);
    }

    private static String degrees(int t) {
        return Weather.degrees(t);
    }

    private static int iconRes(Weather.Icon icon) {
        switch (icon) {
            case SUN:
                return R.drawable.ic_ui_sun;
            case MOON:
                return R.drawable.ic_ui_moon;
            default:
                return R.drawable.ic_ui_cloud;
        }
    }

    private static int conditionText(Weather.Condition c) {
        switch (c) {
            case CLEAR:
                return R.string.wmo_clear;
            case MAINLY_CLEAR:
                return R.string.wmo_mainly_clear;
            case PARTLY_CLOUDY:
                return R.string.wmo_partly_cloudy;
            case OVERCAST:
                return R.string.wmo_overcast;
            case FOG:
                return R.string.wmo_fog;
            case DRIZZLE:
                return R.string.wmo_drizzle;
            case FREEZING_DRIZZLE:
                return R.string.wmo_freezing_drizzle;
            case RAIN:
                return R.string.wmo_rain;
            case FREEZING_RAIN:
                return R.string.wmo_freezing_rain;
            case SNOW:
                return R.string.wmo_snow;
            case SNOW_GRAINS:
                return R.string.wmo_snow_grains;
            case RAIN_SHOWERS:
                return R.string.wmo_rain_showers;
            case SNOW_SHOWERS:
                return R.string.wmo_snow_showers;
            case THUNDERSTORM:
                return R.string.wmo_thunderstorm;
            case THUNDER_HAIL:
                return R.string.wmo_thunder_hail;
            default:
                return R.string.wmo_unknown;
        }
    }

    // ---- On screen or not --------------------------------------------------------------------------------------

    /** Home came into view (its page shown, or the TV back from an app) or left it. */
    private void setVisible(boolean on) {
        if (on == visible) return;
        visible = on;
        if (on) {
            IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            activity.registerReceiver(clockReceiver, f);
            tick();
            if (retryWeather) {
                retryWeather = false;
                weatherStore.retry();
            }
            weatherStore.refresh();
            refreshApps(false);
            refreshInputs();
        } else {
            activity.unregisterReceiver(clockReceiver);
            putDown();
        }
    }

    /** Home's root: tells the page when it is attached to a visible window, which is when Home is on screen. */
    private static final class HomeFrame extends FrameLayout {
        HomePage page;

        HomeFrame(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            update();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (page != null) page.setVisible(false);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            update();
        }

        private void update() {
            if (page != null) page.setVisible(isAttachedToWindow() && getWindowVisibility() == VISIBLE);
        }
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
