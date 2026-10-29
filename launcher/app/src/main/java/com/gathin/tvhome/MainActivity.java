package com.gathin.tvhome;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.util.List;

/**
 * Home: the reworked launcher UI (design_preview/README.md is its spec, docs/rework.md how it is built). The page
 * stack, focus and keys live in Shell; this activity owns the TV plumbing around them: Home and the remote's keys,
 * the Inputs, Wi-Fi and quick-settings dropdowns, the backdrop, and the checks kept from the old Home (Open on
 * start, restore_key_services, handle_view_inputs, settings changes).
 */
public class MainActivity extends Activity implements NavBar.Host, Shell.Pages, Router {

    /** Sent by QuickSettingsActivity: open Launcher settings from here, so Home rebuilds when they close. */
    static final String ACTION_LAUNCHER_SETTINGS = "com.gathin.tvhome.action.LAUNCHER_SETTINGS";
    /** The system input switcher, forwarded here by InputsRequestActivity while handle_view_inputs is on. */
    static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

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
    private boolean backdropLoaded;
    private boolean started;
    /** Launcher settings' change count this Home was built with (see onResume). */
    private long settingsRevision;

    private int[] settingsKeycodes;
    private boolean settingsKeyPanel;
    private boolean launcherSettingsButton;
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
        // restore_key_services: after a force stop, put back the remote's accessibility helpers that Android took
        // off the list (needs WRITE_SECURE_SETTINGS, granted by build-and-install with RESTORE_KEY_SERVICES=1).
        if (cfg.restoreKeyServices) ServiceRepair.restore(this);
        settingsRevision = settingsRevision();
        applyViewInputsHandler(cfg.handleViewInputs);
        settingsKeycodes = cfg.settingsKeycodes;
        settingsKeyPanel = cfg.settingsKeyPanel;
        launcherSettingsButton = cfg.launcherSettingsButton;
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
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        // root is opaque, so the window's own black under it would be a second full-screen fill on every frame.
        getWindow().setBackgroundDrawable(null);
        loadBackdrop();

        shell = new Shell(stage, this);
        refreshNav();
        shell.goTab(Screen.Tab.HOME, false);

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);

        // Not after recreate(): the launch intent survives it and would open them again.
        if (savedInstanceState == null && !openLauncherSettingsIfAsked(getIntent())) openInputsIfAsked(getIntent());
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

    /** M5: the person page stub, pushed over the preview it was opened from. */
    @Override
    public void openPerson(String name) {
        shell.push(PersonPage.create(this, this, name));
    }

    /** M7: Pick for me's setup page, pushed over whichever page is showing, so Back returns to it. */
    @Override
    public void openPick(Kind kind) {
        shell.push(PickSetupPage.create(this, kind));
    }

    /** Opens the offer's service app on its home screen; opening the title itself is in BACKLOG.md (deep links). */
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
        start(i);
    }

    /** YouTube's TV app, searching "name year trailer": its search intent, else its results page. */
    @Override
    public void openTrailer(Title t) {
        String query = t.name + " " + t.year + " trailer";
        String youtube = data.table.packages(ServiceId.YOUTUBE).get(0);
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(youtube).putExtra(SearchManager.QUERY, query);
        Intent results = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))).setPackage(youtube);
        for (Intent i : new Intent[]{search, results}) {
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

    /** The Wi-Fi dropdown: the network and its state, then Other networks and Wi-Fi settings. */
    @Override
    public void openNetwork(View anchor) {
        if (shell.isOpen(MENU_NETWORK)) return;
        NetworkState s = NetworkState.read(this);
        shell.push(Dropdown.create(shell, MENU_NETWORK, anchor, Menus.networkHeader(this, s),
                Menus.network(this, s, vpnApp, this::start)));
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

    /** As in the base launcher: the TV's own settings, or Launcher settings with launcher_settings_button. */
    @Override
    public void openSettings(View anchor) {
        if (launcherSettingsButton) openLauncherSettings();
        else start(SystemSettings.intent(this, Settings.ACTION_SETTINGS));
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

    private void openLauncherSettings() {
        start(new Intent(this, LauncherSettingsActivity.class));
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
        internet = s.validated;
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
        setIntent(new Intent(Intent.ACTION_MAIN));
        openLauncherSettings();
        return true;
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
        if (openInputsIfAsked(intent)) return;
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        shell.home();
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
        if (!backdropLoaded) loadBackdrop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Something changed in Launcher settings: rebuild with it, however the settings were left.
        if (Config.settingsChanged(settingsRevision, settingsRevision())) {
            recreate();
            return;
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
        backdrop.setImageDrawable(null);
        backdropLoaded = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
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
     * The evening landscape every page sits on, decoded off the main thread at 16 bits per pixel (about 2 MB) so
     * the first frame never waits for it.
     */
    private void loadBackdrop() {
        backdropLoaded = true;
        new Thread(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.bg_evening, o);
            runOnUiThread(() -> {
                if (isDestroyed() || !backdropLoaded || b == null) return;
                backdrop.setImageBitmap(b);
                backdrop.setAlpha(0f);
                backdrop.animate().alpha(1f).setDuration(Ui.ms(Ui.SLOW_MS)).start();
            });
        }, "backdrop").start();
    }
}
