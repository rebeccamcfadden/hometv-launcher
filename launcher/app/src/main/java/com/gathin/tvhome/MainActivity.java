package com.gathin.tvhome;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Home: the reworked launcher UI (design_preview/README.md is its spec, docs/rework.md how it is built). The page
 * stack, focus and keys live in Shell; this activity owns the TV plumbing around them: Home and the remote's keys,
 * the Inputs, Wi-Fi and quick-settings dropdowns, the backdrop, and the checks kept from the old Home (Open on
 * start, restore_key_services, handle_view_inputs, settings changes).
 */
public class MainActivity extends Activity implements NavBar.Host, Shell.Pages, Router {

    /** Sent by QuickSettingsActivity: open Launcher settings here, over Home, optionally on one section. */
    static final String ACTION_LAUNCHER_SETTINGS = "com.gathin.tvhome.action.LAUNCHER_SETTINGS";
    /** With ACTION_LAUNCHER_SETTINGS: the section to open straight into (SettingsSection.id). */
    static final String EXTRA_SETTINGS_SECTION = "com.gathin.tvhome.extra.SETTINGS_SECTION";
    /** The system input switcher, forwarded here by InputsRequestActivity while handle_view_inputs is on. */
    static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
    /** Survive a rebuild with Launcher settings open (a theme change): the page reopens on the same section. */
    private static final String KEY_SETTINGS_SECTION = "settings_section";
    private static final String KEY_SETTINGS_IN_PANE = "settings_in_pane";
    private LauncherSettingsPage settingsPage;
    /** Opens First run's checklist over Home (FirstRunPage) at any time, for adb; by itself it shows once. */
    static final String ACTION_FIRST_RUN = "com.gathin.tvhome.action.FIRST_RUN";
    /** Survive a rebuild with First run open: the checklist reopens with the same steps done. */
    private static final String KEY_FIRST_RUN = "first_run_opened";
    private FirstRunPage firstRun;
    private static final String MENU_INPUTS = "Inputs";
    private static final String MENU_NETWORK = "Wi-Fi";
    private static final String MENU_QUICK = "Quick settings";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NavModel nav = new NavModel();
    private Shell shell;
    private Config config;
    /** M3: the catalogue, services and household every screen reads (Router.data). */
    private Data data;
    private TvInputs inputs;
    private FrameLayout stage;
    private ImageView backdrop;
    private Backdrops backdrops;
    private boolean backdropLoaded;
    private boolean started;
    /** Launcher settings' change count this Home was built with (see onResume). */
    private long settingsRevision;
    /** The theme this Home was built with; a different one today (a new choice, or a new season) rebuilds it. */
    private Theme theme;
    /** And whether the System font option was on then: switching it rebuilds Home too. */
    private boolean systemFont;
    private final Runnable recreateForFonts = () -> {
        if (!isDestroyed() && ThemeStore.current(this).id.equals(theme.id)) recreate();
    };
    /** The theme's fonts arrived after Home was built with Roboto: rebuild with them, once a burst has settled. */
    private final Runnable fontsArrived = () -> {
        handler.removeCallbacks(recreateForFonts);
        handler.postDelayed(recreateForFonts, 500);
    };
    /** Midnight passed or the clock was set: the Seasonal theme may have moved to its next period. */
    private final BroadcastReceiver dateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            recreateIfThemeChanged();
        }
    };

    private int[] settingsKeycodes;
    private boolean settingsKeyPanel;
    private boolean showInputs;
    private boolean showNetwork;
    private String vpnApp;

    private final Runnable refreshNav = this::refreshNav;
    private final Runnable refreshNetwork = this::refreshNetwork;
    private final Runnable refreshNetworkLater = this::refreshNetwork;
    private final Runnable refreshInputs = this::refreshInputs;
    /** What the nav last showed, so each kind of event re-reads only its own half. */
    private int networkIcon = R.drawable.ic_ui_wifi;
    private boolean internet;
    private boolean anyInputs;
    /** The default network's capabilities as last seen: only a change the icon shows refreshes it. */
    private boolean lastValidated, lastWifi, lastEthernet;

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            NetworkState.found(network);
            post(refreshNetwork);
        }

        // At onLost the lost network can still be listed by getAllNetworks(), so it is skipped by name, and a second
        // read follows once Android has dropped it.
        @Override
        public void onLost(Network network) {
            NetworkState.lost(network);
            lastValidated = false;
            lastWifi = false;
            lastEthernet = false;
            post(refreshNetwork);
            handler.removeCallbacks(refreshNetworkLater);
            handler.postDelayed(refreshNetworkLater, 1500);
        }

        /** Signal-strength updates land here too, several a minute: those change nothing the nav shows. */
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            if (validated == lastValidated && wifi == lastWifi && ethernet == lastEthernet) return;
            lastValidated = validated;
            lastWifi = wifi;
            lastEthernet = ethernet;
            post(refreshNetwork);
        }
    };

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            post(refreshInputs);
        }

        @Override
        public void onInputRemoved(String inputId) {
            post(refreshInputs);
        }

        /** A CEC device renamed itself, or an input's details changed. */
        @Override
        public void onInputUpdated(String inputId) {
            post(refreshInputs);
        }
    };

    /** A burst of events (a reconnect sends several) collapses into one refresh. */
    private void post(Runnable r) {
        handler.removeCallbacks(r);
        handler.post(r);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // config.xml, overlaid by config.json from a pushed theme or example (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        config = cfg;
        Ui.init(this, cfg.reducedMotion());
        theme = ThemeStore.current(this);
        systemFont = ThemeStore.systemFont(this);
        Ui.apply(theme);
        PerfChoices.apply(PerfPrefs.load(this, cfg));
        if (!ThemeFonts.apply(this, theme)) ThemeFonts.fetch(this, theme, fontsArrived);
        // restore_key_services: after a force stop, put back the remote's accessibility helpers that Android took
        // off the list (needs WRITE_SECURE_SETTINGS, granted by build-and-install with RESTORE_KEY_SERVICES=1).
        if (cfg.restoreKeyServices) ServiceRepair.restore(this);
        settingsRevision = settingsRevision();
        applyViewInputsHandler(cfg.handleViewInputs);
        settingsKeycodes = cfg.settingsKeycodes;
        settingsKeyPanel = cfg.settingsKeyPanel;
        showInputs = cfg.showInputsPill;
        showNetwork = cfg.showNetworkPill;
        vpnApp = cfg.vpnApp;
        inputs = new TvInputs(this, cfg, message -> shell.toast(getString(message)));
        data = Data.create(this, cfg);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        backdrops = new Backdrops(this, backdrop);
        Overlays.add(root, this, theme);
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Phone notes' import pill, over every page and never focusable (ImportPill).
        ImportPill pill = new ImportPill(this, Notes.get(this));
        root.addView(pill, ImportPill.place(false));
        setContentView(root);
        // root is opaque, so the window's own black under it would be a second full-screen fill on every frame.
        getWindow().setBackgroundDrawable(null);
        loadBackdrop();

        shell = new Shell(stage, this);
        shell.setOnShown(() -> pill.atTop(shell.pillAtTop()));
        refreshNav();
        shell.goTab(Screen.Tab.HOME, false);

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);

        // Not after recreate(): the launch intent survives it and would open them again.
        if (savedInstanceState == null && !openLauncherSettingsIfAsked(getIntent())
                && !openFirstRunIfAsked(getIntent()) && !openInputsIfAsked(getIntent())
                && FirstRunPage.dueOnStart(this)) {
            openFirstRun(null);
        }
        // Rebuilt with First run open: it reopens with the same steps done (under Launcher settings, if that was).
        ArrayList<String> firstRunDone = savedInstanceState == null ? null
                : savedInstanceState.getStringArrayList(KEY_FIRST_RUN);
        if (firstRunDone != null) openFirstRun(firstRunDone);
        // Rebuilt with Launcher settings open (a theme change, or a choice made before a trip to another app).
        String section = savedInstanceState == null ? null : savedInstanceState.getString(KEY_SETTINGS_SECTION);
        if (section != null) openLauncherSettings(section, savedInstanceState.getBoolean(KEY_SETTINGS_IN_PANE));
    }

    @Override
    public Screen create(Screen.Tab tab) {
        return Pages.create(this, this, tab);
    }

    @Override
    public Shell shell() {
        return shell;
    }

    @Override
    public NavModel navModel() {
        return nav;
    }

    /** The configuration Home was built with, for the pages (Home's app row reads dock_apps and hidden_apps). */
    Config config() {
        return config;
    }

    /** The TV's inputs, shared by the Inputs dropdown and Home's Inputs row. */
    TvInputs inputs() {
        return inputs;
    }

    // ---- Router (M3): what screens ask of the launcher ---------------------------------------------------------

    @Override
    public Data data() {
        return data;
    }

    /** M5: the title preview popup, over whatever page or popup is showing. */
    @Override
    public void openTitle(Title t) {
        shell.push(TitlePreview.create(this, this, t));
    }

    /** M5: the person page, pushed over the preview or the search it was opened from. */
    @Override
    public void openPerson(Person p) {
        shell.push(PersonPage.create(this, this, p));
    }

    /** M7: Pick for me's setup page, pushed over whichever page is showing, so Back returns to it. */
    @Override
    public void openPick(Kind kind) {
        shell.push(PickSetupPage.create(this, kind));
    }

    /**
     * Opens the offer's service app: on the title itself when the catalogue has a link for it (V2 Phase 2, from
     * Wikidata) and the installed app takes it, else on the app's home screen.
     */
    @Override
    public void watch(Title t, Offer o) {
        Offer offer = o != null ? o : data.bestOffer(t);
        if (offer == null) {
            shell.toast(getString(R.string.router_not_available));
            return;
        }
        Intent i = data.services.launchIntent(offer.service);
        if (i == null) {
            shell.toast(getString(R.string.router_not_installed, data.table.name(offer.service)));
            return;
        }
        Intent link = data.services.linkIntent(offer.service, data.catalog.link(t, offer.service));
        start(link != null ? link : i);
    }

    /**
     * YouTube's TV app on the title's trailer from TMDB; without one (the mock catalogue, details not loaded yet, no
     * trailer on TMDB), searching "name year trailer": its search intent, else its results page.
     */
    @Override
    public void openTrailer(Title t) {
        String youtube = data.table.packages(ServiceId.YOUTUBE).get(0);
        List<Intent> tries = new ArrayList<>();
        if (t.trailer != null) {
            tries.add(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + t.trailer))
                    .setPackage(youtube));
        }
        String query = t.name + " " + t.year + " trailer";
        tries.add(new Intent(Intent.ACTION_SEARCH).setPackage(youtube).putExtra(SearchManager.QUERY, query));
        tries.add(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))).setPackage(youtube));
        for (Intent i : tries) {
            if (i.resolveActivity(getPackageManager()) != null) {
                start(i);
                return;
            }
        }
        shell.toast(getString(R.string.router_no_trailer));
    }

    @Override
    public void toast(CharSequence text) {
        shell.toast(text);
    }

    // ---- The top bar --------------------------------------------------------------------------------------------

    /** The Wi-Fi dropdown: the network and its state, the other saved networks, then Other networks and Wi-Fi settings. */
    @Override
    public void openNetwork(View anchor) {
        if (shell.isOpen(MENU_NETWORK)) return;
        NetworkState s = NetworkState.read(this);
        List<SavedNetworks.Net> saved = WifiNetworks.saved(this);
        if (s.wifi && s.ssid == null) s.ssid = SavedNetworks.nameOf(saved, s.networkId);
        shell.push(Dropdown.create(shell, MENU_NETWORK, anchor, Menus.networkHeader(this, s),
                Menus.network(this, s, vpnApp, this::start, saved, this::connectWifi)));
    }

    /** OK on a saved network: ask Android to switch, or open Wi-Fi settings where the TV refuses (BACKLOG.md). */
    private void connectWifi(SavedNetworks.Net net) {
        if (WifiNetworks.connect(this, net.id)) {
            shell.toast(getString(R.string.wifi_switching, net.ssid));
        } else {
            shell.toast(getString(R.string.wifi_switch_refused));
            start(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        }
    }

    /** The Inputs dropdown, or the TV's own source panel where that is the whole menu. */
    @Override
    public void openInputs(View anchor) {
        if (shell.isOpen(MENU_INPUTS)) return;
        if (inputs.panelIsTheMenu()) {
            inputs.openSourcePanel();
            return;
        }
        List<MenuPanel.Row> rows = inputs.rows();
        if (rows.isEmpty()) {
            shell.toast(getString(R.string.no_inputs));
            return;
        }
        shell.push(Dropdown.create(shell, MENU_INPUTS, anchor, null, rows));
    }

    /**
     * V2 quick settings (design final-quick-settings): the Settings icon opens its dropdown, whose first two rows are
     * the TV's settings and Launcher settings, so launcher_settings_button no longer changes it. The remote's
     * Settings key is unchanged (handleSettingsKey).
     */
    @Override
    public void openSettings(View anchor) {
        if (shell.isOpen(QuickSettings.LABEL)) return;
        shell.push(QuickSettings.create(this, anchor));
    }

    /** The remote's Settings key on Home: the quick-settings dropdown under the Settings icon, or closing it. */
    private void toggleQuickSettings() {
        if (shell.isOpen(MENU_QUICK)) {
            shell.pop();
            return;
        }
        shell.closeDialogs();
        Screen page = shell.top();
        View anchor = page == null ? null : Focus.byKey(page.view, NavBar.KEY_SETTINGS);
        shell.push(Dropdown.create(shell, MENU_QUICK, anchor, null,
                Menus.quickSettings(this, this::start, this::openLauncherSettings)));
    }

    /**
     * Launcher settings, a page over whatever is showing (LauncherSettingsPage): on its first section, or straight
     * into section's pane when enter. Already open, it stays as it is.
     */
    void openLauncherSettings(String section, boolean enter) {
        shell.closeDialogs();
        if (settingsPage != null && shell.has(settingsPage)) return;
        settingsPage = LauncherSettingsPage.create(this, section, enter);
        shell.push(settingsPage);
    }

    void openLauncherSettings() {
        openLauncherSettings(null, false);
    }

    /** Back on Launcher settings' list: the page closes, and Home is rebuilt if a choice changed while it was open. */
    void closeLauncherSettings() {
        if (settingsPage != null && shell.top() == settingsPage) shell.pop();
        settingsClosed();
        if (Config.settingsChanged(settingsRevision, settingsRevision())) recreate();
    }

    /** Launcher settings has left the stack, or is about to with the activity: its sections stop what they run. */
    private void settingsClosed() {
        if (settingsPage != null) settingsPage.closed();
        settingsPage = null;
    }

    /**
     * First run's checklist, a page over whatever is showing (FirstRunPage), with done's steps already done (after a
     * rebuild); null for none. Already open, it stays as it is.
     */
    void openFirstRun(Collection<String> done) {
        shell.closeDialogs();
        if (firstRun != null && shell.has(firstRun)) return;
        // Never over Launcher settings: the two would restore in the wrong order after a rebuild.
        if (settingsPage != null && shell.has(settingsPage)) {
            shell.goTab(Screen.Tab.HOME, false);
            settingsClosed();
        }
        firstRun = FirstRunPage.create(this, done);
        FirstRunPage.markShown(this);
        shell.push(firstRun);
    }

    /** Start using Home, or Back: Home, rebuilt if a step changed a choice, as Launcher settings does on close. */
    void closeFirstRun() {
        shell.goTab(Screen.Tab.HOME, false);
        firstRunClosed();
        if (settingsPage != null && !shell.has(settingsPage)) settingsClosed();
        if (Config.settingsChanged(settingsRevision, settingsRevision())) recreate();
    }

    private void firstRunClosed() {
        if (firstRun != null) firstRun.closed();
        firstRun = null;
    }

    /** Whether there are inputs to list and which network icon to show, on every page's nav. */
    private void refreshNav() {
        if (isDestroyed()) return;
        readNetwork();
        anyInputs = showInputs && inputs.any();
        applyNav();
    }

    private void refreshNetwork() {
        if (isDestroyed()) return;
        readNetwork();
        applyNav();
    }

    /** The icon, and whether the internet is reachable, which Home's weather needs even when the icon is the same. */
    private void readNetwork() {
        NetworkState s = NetworkState.read(this);
        networkIcon = s.icon();
        boolean was = internet;
        internet = s.validated;
        // Titles whose details failed while offline (a cold start before Wi-Fi is up) load once the internet is back.
        if (internet && !was && data != null) data.catalog.retryFailed();
    }

    private void refreshInputs() {
        if (isDestroyed()) return;
        anyInputs = showInputs && inputs.any();
        applyNav();
        // Home's Inputs row shows each port and CEC device, which can change while the nav's icon stays the same.
        nav.inputsChanged();
    }

    private void applyNav() {
        nav.set(anyInputs, showNetwork, networkIcon, internet);
    }

    void start(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            shell.toast(getString(R.string.not_available));
        }
    }

    // ---- Intents ------------------------------------------------------------------------------------------------

    /**
     * handle_view_inputs: switch InputsRequestActivity, the VIEW_INPUTS handler, on or off. It is disabled in the
     * manifest, so a TV with its own input picker never sees it; only the sets that need it turn it on. Written
     * only when the state differs, so an ordinary start does not touch the package manager.
     */
    private void applyViewInputsHandler(boolean on) {
        ComponentName handlerComponent = new ComponentName(this, InputsRequestActivity.class);
        PackageManager pm = getPackageManager();
        int want = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        if (pm.getComponentEnabledSetting(handlerComponent) != want) {
            pm.setComponentEnabledSetting(handlerComponent, want, PackageManager.DONT_KILL_APP);
        }
    }

    /**
     * VIEW_INPUTS, forwarded by InputsRequestActivity or a remote key: open the Inputs dropdown once the nav is on
     * screen. The intent is replaced so a later recreate() does not open it again. Nothing to do when the TV has no
     * inputs to list (the icon is hidden then).
     */
    private boolean openInputsIfAsked(Intent intent) {
        if (intent == null || !ACTION_VIEW_INPUTS.equals(intent.getAction())) return false;
        setIntent(new Intent(Intent.ACTION_MAIN));
        afterLayout(() -> {
            if (isFinishing() || !nav.inputsShown) return;
            shell.closeDialogs();
            Screen page = shell.top();
            View anchor = page == null ? null : Focus.byKey(page.view, NavBar.KEY_INPUTS);
            if (anchor != null) shell.focus(anchor);
            openInputs(anchor);
        });
        return true;
    }

    private boolean openLauncherSettingsIfAsked(Intent intent) {
        if (intent == null || !ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) return false;
        String section = intent.getStringExtra(EXTRA_SETTINGS_SECTION);
        setIntent(new Intent(Intent.ACTION_MAIN));
        openLauncherSettings(section, section != null);
        return true;
    }

    private boolean openFirstRunIfAsked(Intent intent) {
        if (intent == null || !ACTION_FIRST_RUN.equals(intent.getAction())) return false;
        setIntent(new Intent(Intent.ACTION_MAIN));
        openFirstRun(null);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (firstRun != null && shell.has(firstRun)) out.putStringArrayList(KEY_FIRST_RUN, firstRun.opened());
        if (settingsPage != null && shell.has(settingsPage)) {
            out.putString(KEY_SETTINGS_SECTION, settingsPage.section());
            out.putBoolean(KEY_SETTINGS_IN_PANE, settingsPage.inPane());
        }
    }

    /** Runs r once the stage has been laid out, so dropdowns can hang from their buttons on a cold start. */
    private void afterLayout(Runnable r) {
        if (stage.isLaidOut()) {
            r.run();
            return;
        }
        stage.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                stage.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                stage.post(r);
            }
        });
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here. Everything over Home closes and
     * focus goes back to Home's first item, like the stock launcher. Back from an app never comes this way, so
     * returning to the page you left from is unchanged (#118).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (openLauncherSettingsIfAsked(intent)) return;
        if (openFirstRunIfAsked(intent)) return;
        if (openInputsIfAsked(intent)) return;
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        shell.home();
        if (settingsPage != null && !shell.has(settingsPage)) settingsClosed();
        if (firstRun != null && !shell.has(firstRun)) firstRunClosed();
        // Home closed Launcher settings: rebuild with what changed there, as Back would have.
        if (Config.settingsChanged(settingsRevision, settingsRevision())) recreate();
    }

    // ---- Keys ---------------------------------------------------------------------------------------------------

    /**
     * A Settings button sent as a keycode (settings_key_keycodes, e.g. 83 on TCL Google TV remotes) does on Home
     * what HomeKeyService does elsewhere: while that service runs it consumes the key first; without it the stock
     * launcher that used to handle the key is gone, and the button would do nothing even on Home.
     */
    private boolean handleSettingsKey(KeyEvent event) {
        for (int code : settingsKeycodes) {
            if (event.getKeyCode() != code) continue;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (settingsKeyPanel) toggleQuickSettings();
                else start(SystemSettings.intent(this, Settings.ACTION_SETTINGS));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleSettingsKey(event)) return true;
        if (shell.dispatch(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    // ---- Lifecycle ----------------------------------------------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        // V2 Phase 2: price lookups only while Home shows; V2 Phase 3: the TMDB account syncs only then too.
        if (data != null) {
            data.catalog.setVisible(true);
            data.accounts.setVisible(true);
        }
        if (!backdropLoaded) loadBackdrop();
        backdrops.setVisible(true);
        IntentFilter dates = new IntentFilter(Intent.ACTION_DATE_CHANGED);
        dates.addAction(Intent.ACTION_TIME_CHANGED);
        dates.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(dateReceiver, dates);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Something changed in Launcher settings: rebuild with it, however the settings were left.
        if (Config.settingsChanged(settingsRevision, settingsRevision())) {
            recreate();
            return;
        }
        if (recreateIfThemeChanged()) return;
        // Another activity (quick settings, Launcher settings) may have set Ui's tokens to another theme meanwhile:
        // pages Home builds from now on use the ones it was built with.
        if (Ui.theme() != theme) {
            Ui.apply(theme);
            ThemeFonts.apply(this, theme);
        }
        if (inputs.openOnStartIfDue()) return;
        // Callbacks keep the nav current while Home is in the background; this catches anything missed, after the
        // first frame back on Home so its binder calls never hold that frame up.
        stage.post(refreshNav);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Nothing of ours stays open over another app.
        shell.closeDialogs();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        backdrops.setVisible(false);
        unregisterReceiver(dateReceiver);
        if (data != null) {
            data.catalog.setVisible(false);
            data.accounts.setVisible(false);
        }
    }

    /**
     * Another app needs the memory while Home is in the background: let go of the backdrop and decode it again when
     * Home comes back. Not at UI_HIDDEN, the ordinary trip to an app.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // M3: the artwork cache is only a cache (the views on screen keep their images), so it goes on any real
        // pressure, but not on UI_HIDDEN, the ordinary trip to an app.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            Art.trim();
        }
        if (!MemoryTrimPolicy.shouldRelease(level, started)) return;
        backdrops.release();
        backdropLoaded = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        settingsClosed();
        firstRunClosed();
        handler.removeCallbacksAndMessages(null);
        ThemeFonts.forget(fontsArrived);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
    }

    /** Launcher settings' change count (Config.SETTINGS_REVISION_KEY); onResume rebuilds Home when it moved. */
    private long settingsRevision() {
        return getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getLong(Config.SETTINGS_REVISION_KEY, 0);
    }

    /**
     * Rebuilds Home when today's theme isn't the one it was built with: another choice saved, a Seasonal theme whose
     * period changed with the date (its id names the period), or the System font option switched. True when it does.
     */
    private boolean recreateIfThemeChanged() {
        if (ThemeStore.current(this).id.equals(theme.id) && ThemeStore.systemFont(this) == systemFont) return false;
        recreate();
        return true;
    }

    /**
     * The theme's backdrop behind every page (Backdrops): decoded off the main thread at 16 bits per pixel, or
     * downloaded first for a theme whose picture is not in the APK, so the first frame never waits for it.
     */
    private void loadBackdrop() {
        backdropLoaded = true;
        backdrops.show(Ui.theme());
    }
}
