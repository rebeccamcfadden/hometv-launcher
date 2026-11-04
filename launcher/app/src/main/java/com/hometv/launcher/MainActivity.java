package com.hometv.launcher;

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
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
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
    static final String ACTION_LAUNCHER_SETTINGS = "com.hometv.launcher.action.LAUNCHER_SETTINGS";
    /** With ACTION_LAUNCHER_SETTINGS: the section to open straight into (SettingsSection.id). */
    static final String EXTRA_SETTINGS_SECTION = "com.hometv.launcher.extra.SETTINGS_SECTION";
    /** The system input switcher, forwarded here by InputsRequestActivity while handle_view_inputs is on. */
    static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
    /** Survive a rebuild with Launcher settings open (a theme change): the page reopens on the same section. */
    private static final String KEY_SETTINGS_SECTION = "settings_section";
    private static final String KEY_SETTINGS_IN_PANE = "settings_in_pane";
    /** And whether Back in its pane returns to the page it was opened from (an empty state's Turn on). */
    private static final String KEY_SETTINGS_BACK_CLOSES = "settings_back_closes";
    /** A message for the launcher rebuilt after First run switched off what wasn't set up (the rebuild drops a toast). */
    private static String toastAfterRebuild;
    private LauncherSettingsPage settingsPage;
    /** Opens First run's checklist over Home (FirstRunPage) at any time, for adb; by itself it shows once. */
    static final String ACTION_FIRST_RUN = "com.hometv.launcher.action.FIRST_RUN";
    /** F25's test-only force for the TV bench (previewFallIfAsked), with EXTRA_FALL snow, rain, leaves or off. */
    static final String ACTION_FALL_PREVIEW = "com.hometv.launcher.action.FALL_PREVIEW";
    static final String EXTRA_FALL = "fall";
    /** Survive a rebuild with First run open: the checklist reopens with the same steps done. */
    private static final String KEY_FIRST_RUN = "first_run_opened";
    /** And the panel it had open (F28), which opens again. */
    private static final String KEY_FIRST_RUN_SETUP = "first_run_setup";
    /** First run's TMDB key tab (F16), decided as it first opened. */
    private static final String KEY_FIRST_RUN_KEY_TAB = "first_run_key_tab";
    private FirstRunPage firstRun;
    /**
     * F26: where the household was (the nav's page, its focus and choices, the title previews over it), so Back from
     * an app returns there even when Android ended the process meanwhile; Comeback says how long it holds.
     */
    private static final String KEY_PLACE = "place";
    private static final String PLACE_AT = "at";
    private static final String PLACE_SLEPT = "slept";
    private static final String PLACE_TAB = "tab";
    private static final String PLACE_FOCUS = "focus";
    private static final String PLACE_PAGE = "page";
    private static final String PLACE_OVER = "over";
    /** F47: Pick for me's setup over the page, and its results' picks and focus when they showed too. */
    private static final String PLACE_PICK = "pick";
    private static final String PLACE_PICKS = "picks";
    private static final String PLACE_PICKS_FOCUS = "picksFocus";
    /** F47: the pick the results bar showed: the bar's buttons' keys alone don't say which pick they act on. */
    private static final String PLACE_PICKS_BAR = "picksBar";
    private static final String MENU_INPUTS = "Inputs";
    private static final String MENU_NETWORK = "Wi-Fi";
    private static final String MENU_QUICK = "Quick settings";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NavModel nav = new NavModel();
    private Shell shell;
    private boolean resumed;
    /** In-app updates (Family install step 5): Home's notice, once per new version, while Home is in front. */
    private final Runnable updateNotice = () -> {
        if (!resumed || shell == null) return;
        Updates u = Updates.get(this);
        UpdateRules.Release r = u.available();
        if (r != null && u.takeNotice()) shell.toast(getString(R.string.updates_notice, r.versionName), 8000);
    };
    private Config config;
    /** M3: the catalogue, services and household every screen reads (Router.data). */
    private Data data;
    private TvInputs inputs;
    private FrameLayout stage;
    private ImageView backdrop;
    /** F25: snow or rain over a Seasonal picture while Home is idle, between the backdrop and the pages. */
    private WeatherLayer weather;
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
    /**
     * Midnight passed or the clock or time zone was set: the Seasonal theme may have moved to its next period, and its
     * picture to another part of the day.
     */
    private final BroadcastReceiver dateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!recreateIfThemeChanged() && backdrops != null) backdrops.timeChanged();
        }
    };
    /** An app was installed, updated, changed or removed: its banner may be new, so the cached one goes. */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String pkg = intent.getData() == null ? null : intent.getData().getSchemeSpecificPart();
            if (pkg != null) {
                AppArt.forget(pkg);
                HomeApps.forget(context, pkg);
                return;
            }
            // Apps on removable storage came or went (EXTERNAL_APPLICATIONS_*), named in an extra, not the data.
            String[] pkgs = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            if (pkgs != null) for (String p : pkgs) AppArt.forget(p);
            HomeApps.forgetAll(context);
        }
    };

    private int[] settingsKeycodes;
    private boolean settingsKeyPanel;
    private boolean showInputs;
    private boolean showNetwork;
    private String vpnApp;

    private final Runnable refreshNav = this::refreshNav;
    /** F27: the phone remote's icon in the nav (off, on, a phone connected). */
    private final Runnable remoteChanged = () -> {
        PhoneRemote r = PhoneRemote.get(this);
        nav.setRemote(!r.on() ? NavModel.REMOTE_OFF : r.connected() != null ? NavModel.REMOTE_CONNECTED : NavModel.REMOTE_ON);
    };
    /** F39: titles a phone has seen in its search, so My list can find one the catalogue doesn't hold. */
    private final java.util.Map<String, Title> phoneTitles = new java.util.LinkedHashMap<String, Title>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Title> eldest) {
            return size() > 100;
        }
    };
    /** F27 with F39: what a phone remote does on the launcher's screens (PhoneRemote.Bridge); main thread. */
    private final PhoneRemote.Bridge remoteBridge = new PhoneRemote.Bridge() {
        @Override
        public void press(String key) {
            if ("home".equals(key)) {
                // As the remote's Home: everything over Home closes (onNewIntent).
                onNewIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
                return;
            }
            int code = PhoneRemoteKeys.keyCode(key);
            if (code == KeyEvent.KEYCODE_UNKNOWN) return;
            long now = SystemClock.uptimeMillis();
            dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
            dispatchKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0));
        }

        @Override
        public Screen.TextField textField() {
            Screen top = shell.top();
            return top == null ? null : top.textField();
        }

        @Override
        public String screenName() {
            Screen top = shell.top();
            if (top == null) return getString(R.string.nav_home);
            if (top.title() != null) return top.title().name;
            if (top.tab != null) {
                switch (top.tab) {
                    case MOVIES: return getString(R.string.nav_movies);
                    case TV: return getString(R.string.nav_tv);
                    case SEARCH: return getString(R.string.nav_search);
                    default: return getString(R.string.nav_home);
                }
            }
            return top.label;
        }

        @Override
        public Title title() {
            Screen top = shell.top();
            Title t = top == null ? null : top.title();
            if (t == null) t = Kit.titleOf(shell.focused());
            if (t != null) phoneTitles.put(t.id, t);
            return t;
        }

        @Override
        public Title titleById(String id) {
            Title t = data.catalog.get(id);
            return t != null ? t : phoneTitles.get(id);
        }

        @Override
        public boolean inMyList(String id) {
            return data.household.inMyList(id);
        }

        @Override
        public boolean setMyList(Title t, boolean mine, String phone) {
            if (data.household.inMyList(t.id) == mine) return mine;
            boolean now = data.household.toggleMyList(t.id);
            shell.toast(getString(now ? R.string.remote_added_from : R.string.remote_removed_from, t.name, phone));
            Screen top = shell.top();
            if (top instanceof TitlePreview) ((TitlePreview) top).refreshToggles();
            // Movies and TV Shows show My list as it is now, as when the TV's own preview changes it (Search listens).
            if (top instanceof LibraryPage) ((LibraryPage) top).render();
            Kit.refreshTitleCard(shell.focused(), data);
            return now;
        }

        @Override
        public void search(String query, java.util.function.Consumer<List<Title>> done) {
            List<Title> found = PhoneRemoteKeys.matching(data.catalog.all(), query);
            boolean remote = data.catalog.isRemote() && StateUi.online(MainActivity.this)
                    && TmdbGate.need(data.accounts) == TmdbGate.Need.NONE && PhoneRemoteKeys.asksTmdb(query);
            java.util.function.Consumer<List<Title>> answer = PhoneRemoteKeys.once(ts -> {
                remember(ts);
                done.accept(ts);
            });
            if (!remote) {
                answer.accept(found);
                return;
            }
            // TMDB never calls back on a failed fetch: the TV's own matches answer after a while instead.
            Runnable fallback = () -> answer.accept(found);
            handler.postDelayed(fallback, PhoneRemoteKeys.SEARCH_FALLBACK_MS);
            data.catalog.search(query, f -> {
                handler.removeCallbacks(fallback);
                answer.accept(PhoneRemoteKeys.merge(found, f.titles));
            });
        }

        private void remember(List<Title> ts) {
            for (Title t : ts) phoneTitles.put(t.id, t);
        }

        @Override
        public void paired(String phone) {
            shell.toast(getString(R.string.remote_paired_toast, phone));
        }
    };
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
        // The package receiver first, then a clean memory of which apps open: a change before the receiver listened
        // (none listened while no activity was alive) can't stay remembered.
        IntentFilter packages = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);
        IntentFilter storage = new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        storage.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        registerReceiver(packageReceiver, storage);
        HomeApps.forgetAll(this);
        Ui.init(this, cfg.reducedMotion());
        theme = ThemeStore.current(this);
        systemFont = ThemeStore.systemFont(this);
        Ui.apply(theme);
        PerfChoices.apply(PerfPrefs.load(this, cfg));
        // F14: which features run, before anything that starts one is built.
        Features.apply(FeaturePrefs.load(this));
        // The Phone notes inbox lives as long as the process: it opens or closes with the switches the launcher is
        // built with now.
        Notes.get(this).allow(Features.inboxAllowed(Features.current()));
        // F27: the phone remote's server also lives as long as the process; it opens or closes with its switch.
        PhoneRemote.get(this).sync();
        PhoneRemote.get(this).addListener(remoteChanged);
        remoteChanged.run();
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
        // F16: a key the TV couldn't reach when it was entered is checked again, once a start.
        ApiKeys.get(this).recheckUnreached(true);
        ApiKeys.get(this).addKeyListener(keyRefused);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        backdrops = new Backdrops(this, backdrop);
        Overlays.add(root, this, theme);
        weather = new WeatherLayer(this);
        backdrops.setOnPhase(weather::setPhase);
        root.addView(weather, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
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
        shell.setOnShown(() -> {
            pill.atTop(shell.pillAtTop());
            weather.setOnTop(shell.top() instanceof HomePage);
        });
        // F3: holding OK on a title card, wherever one shows, opens the title menu.
        shell.setLongOk(v -> TitleMenu.open(this, v));
        refreshNav();
        if (!restorePlace(savedInstanceState)) shell.goTab(Screen.Tab.HOME, false);

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);

        // First run cut short without ending (a power cut mid-setup, Home brought back cold): it ends now, switching off
        // what isn't set up, and the launcher is rebuilt with that. Cut short before any step was opened, it shows again.
        if (savedInstanceState == null && FirstRunPage.interrupted(this)
                && !FirstRunPage.endSwitchesOff(this, false)) {
            FirstRunPage.leftUntouched(this);
        } else if (savedInstanceState == null && FirstRunPage.interrupted(this)) {
            List<String> off = FirstRunPage.switchOffUnfinished(this);
            if (!off.isEmpty()) {
                toastAfterRebuild = getString(R.string.first_run_switched_off, TextUtils.join(", ", off));
                recreate();
                return;
            }
        }
        // Not after recreate(): the launch intent survives it and would open them again.
        if (savedInstanceState == null && !openLauncherSettingsIfAsked(getIntent())
                && !openFirstRunIfAsked(getIntent()) && !previewFallIfAsked(getIntent()) && !openInputsIfAsked(getIntent())
                && FirstRunPage.dueOnStart(this)) {
            openFirstRun(null);
        }
        // Rebuilt with First run open: it reopens with the same steps done (under Launcher settings, if that was).
        ArrayList<String> firstRunDone = savedInstanceState == null ? null
                : savedInstanceState.getStringArrayList(KEY_FIRST_RUN);
        if (firstRunDone != null) {
            openFirstRun(firstRunDone, savedInstanceState.containsKey(KEY_FIRST_RUN_KEY_TAB)
                    ? savedInstanceState.getBoolean(KEY_FIRST_RUN_KEY_TAB) : null);
        }
        String firstRunSetup = savedInstanceState == null ? null : savedInstanceState.getString(KEY_FIRST_RUN_SETUP);
        if (firstRunDone != null && firstRunSetup != null && firstRun != null) firstRun.reopen(firstRunSetup);
        // Rebuilt with Launcher settings open (a theme change, or a choice made before a trip to another app).
        String section = savedInstanceState == null ? null : savedInstanceState.getString(KEY_SETTINGS_SECTION);
        if (section != null) openLauncherSettings(section, savedInstanceState.getBoolean(KEY_SETTINGS_IN_PANE),
                savedInstanceState.getBoolean(KEY_SETTINGS_BACK_CLOSES));
        if (toastAfterRebuild != null) {
            shell.toast(toastAfterRebuild);
            toastAfterRebuild = null;
        }
    }

    @Override
    public Screen create(Screen.Tab tab) {
        return Pages.create(this, this, tab);
    }

    WeatherLayer weatherLayer() {
        return weather;
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

    /** F3: the title preview on its More like this tab (the title menu's More like this). */
    @Override
    public void openMoreLike(Title t) {
        shell.push(TitlePreview.create(this, this, t, true));
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
        // An app can claim the link and still refuse it (an activity it doesn't export): then its home screen, as
        // without a link.
        if (link != null && tryStart(link)) return;
        start(i);
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
        // A link YouTube claims but refuses moves on to the next way in, as Watch does (tryStart). Each starts in a
        // cleared task of its own, as title links do: a YouTube already running would otherwise come to the front and
        // drop the trailer, and Back from the trailer would walk YouTube's own screens instead of coming back (F26).
        for (Intent i : tries) {
            i.addFlags(Services.LINK_FLAGS);
            if (i.resolveActivity(getPackageManager()) != null && tryStart(i)) return;
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

    /** F27: the phone remote's icon opens its pane. */
    @Override
    public void openRemote(View anchor) {
        openLauncherSettings("remote", true);
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
        openLauncherSettings(section, enter, false);
    }

    /** backCloses: Back in the pane returns to the page underneath (an empty state's Turn on or Sign in). */
    void openLauncherSettings(String section, boolean enter, boolean backCloses) {
        shell.closeDialogs();
        if (settingsPage != null && shell.has(settingsPage)) return;
        settingsPage = LauncherSettingsPage.create(this, section, enter, backCloses);
        shell.push(settingsPage);
    }

    /**
     * Movies, TV Shows or Search's "needs a TMDB account" (TmdbGate): Turn on (switched off) or Sign in, straight into
     * Account's sign-in; Back returns to the page (the project lead, 2026-11-02). Turned on, the launcher is rebuilt with
     * TMDB account first (it makes the TMDB client only with it on), and comes back to the page with Account over it.
     */
    void signInFromPage(boolean turnOn) {
        if (turnOn) FeaturePrefs.turnOn(this, Features.Feature.TMDB);
        openLauncherSettings("account", true, true);
        if (turnOn && !Features.isOn(Features.Feature.TMDB)) recreate();
    }

    /**
     * Movies, TV Shows or Search's "needs a TMDB key" on a release build without one (TmdbGate, F16): straight into
     * Launcher settings › API keys; Back returns to the page, rebuilt with the key once one was added.
     */
    void addKeyFromPage() {
        openLauncherSettings(KeysSection.ID, true, true);
    }

    /**
     * F16: a check again refused a key this launcher was built with (at start, a key the TV couldn't reach before). In
     * front with Home, it is rebuilt at once without it; with Launcher settings or First run open, when that closes
     * (the key moved the settings revision); in the background, when it comes back (onResume).
     */
    private final Runnable keyRefused = () -> {
        if (!started || isFinishing() || isDestroyed()) return;
        if ((settingsPage != null && shell.has(settingsPage)) || (firstRun != null && shell.has(firstRun))) return;
        recreate();
    };

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
        openFirstRun(done, null);
    }

    /** As {@link #openFirstRun(Collection)}, with First run's TMDB key tab decision kept over a rebuild (or null). */
    void openFirstRun(Collection<String> done, Boolean keyTab) {
        shell.closeDialogs();
        if (firstRun != null && shell.has(firstRun)) return;
        // Never over Launcher settings: the two would restore in the wrong order after a rebuild.
        if (settingsPage != null && shell.has(settingsPage)) {
            shell.goTab(Screen.Tab.HOME, false);
            settingsClosed();
        }
        // Opened afresh, not rebuilt: what an earlier First run switched off as it ended starts on again (F34).
        boolean restored = done == null && FeaturePrefs.restoreFirstRunOff(this);
        if (restored) FirstRunPage.markRestored(this);
        firstRun = FirstRunPage.create(this, done, keyTab);
        FirstRunPage.markShown(this);
        shell.push(firstRun);
        // The launcher runs without them: rebuilt with them on, First run reopens over it as after any rebuild.
        if (restored && !FeaturePrefs.live(this).same(Features.current())) recreate();
    }

    /**
     * Start using Home ({@code start}), or Back: Home, rebuilt if a step changed a choice, as Launcher settings does on
     * close.
     */
    void closeFirstRun(boolean start) {
        shell.goTab(Screen.Tab.HOME, false);
        firstRunEnded(start);
        firstRunClosed();
        if (settingsPage != null && !shell.has(settingsPage)) settingsClosed();
        if (Config.settingsChanged(settingsRevision, settingsRevision())) recreate();
    }

    private void firstRunClosed() {
        if (firstRun != null) firstRun.closed();
        firstRun = null;
    }

    /**
     * First run has ended (Start, Back, the remote's Home; not a rebuild): every feature on whose setup isn't finished
     * is switched off, so nothing shows half set up (the project lead, 2026-11-02), and a toast names them. Left by Back
     * or Home before any step was opened, nothing is, and it shows itself again at the next start (F34).
     */
    private void firstRunEnded(boolean start) {
        if (firstRun == null) return;
        if (!FirstRunPage.endSwitchesOff(this, start)) {
            FirstRunPage.leftUntouched(this);
            return;
        }
        List<String> off = FirstRunPage.switchOffUnfinished(this);
        if (off.isEmpty()) return;
        String msg = getString(R.string.first_run_switched_off, TextUtils.join(", ", off));
        if (Config.settingsChanged(settingsRevision, settingsRevision())) toastAfterRebuild = msg;
        else shell.toast(msg);
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
        if (!tryStart(i)) shell.toast(getString(R.string.not_available));
    }

    /** Starts i in a task of its own; false when nothing would take it or it is refused. */
    private boolean tryStart(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
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

    /**
     * F25's test-only force for the TV bench, ignored in public release builds (BuildConfig.PUBLIC_RELEASE):
     * {@code adb shell am start -a com.hometv.launcher.action.FALL_PREVIEW --es fall snow -n com.hometv.launcher/.MainActivity}
     * ({@code snow}, {@code rain}, {@code leaves} or {@code off}). Until the launcher restarts; nothing is saved.
     */
    private boolean previewFallIfAsked(Intent intent) {
        if (intent == null || !ACTION_FALL_PREVIEW.equals(intent.getAction())) return false;
        setIntent(new Intent(Intent.ACTION_MAIN));
        if (!BuildConfig.PUBLIC_RELEASE) weather.setPreview(WeatherFall.preview(intent.getStringExtra(EXTRA_FALL)));
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        savePlace(out);
        if (firstRun != null && shell.has(firstRun)) {
            out.putStringArrayList(KEY_FIRST_RUN, firstRun.opened());
            out.putString(KEY_FIRST_RUN_SETUP, firstRun.panelShowing());
            out.putBoolean(KEY_FIRST_RUN_KEY_TAB, firstRun.tmdbKeyTab());
        }
        if (settingsPage != null && shell.has(settingsPage)) {
            out.putString(KEY_SETTINGS_SECTION, settingsPage.section());
            // Opened for one setup from a page (backCloses), it is about its pane: it reopens there.
            out.putBoolean(KEY_SETTINGS_IN_PANE, settingsPage.inPane() || settingsPage.backCloses());
            out.putBoolean(KEY_SETTINGS_BACK_CLOSES, settingsPage.backCloses());
        }
    }

    /**
     * F26: the nav's page with its focus and choices, Pick for me's pages over it (F47: its choices, and the same picks
     * with their focus), and the title previews over those (KEY_PLACE).
     */
    private void savePlace(Bundle out) {
        List<Screen> stack = shell.screens();
        if (stack.isEmpty() || stack.get(0).tab == null) return;
        Screen page = stack.get(0);
        Bundle place = new Bundle();
        place.putLong(PLACE_AT, SystemClock.elapsedRealtime());
        place.putLong(PLACE_SLEPT, slept());
        place.putString(PLACE_TAB, page.tab.name());
        // A restored item still on its way (Comeback's pending focus) is where the household was, not the fallback.
        place.putString(PLACE_FOCUS, page.pendingFocusKey != null ? page.pendingFocusKey : page.lastFocusKey);
        Bundle state = new Bundle();
        page.save(state);
        place.putBundle(PLACE_PAGE, state);
        int i = 1;
        if (i < stack.size() && stack.get(i) instanceof PickSetupPage) {
            place.putBundle(PLACE_PICK, ((PickSetupPage) stack.get(i++)).saved());
            if (i < stack.size() && stack.get(i) instanceof PickResultsPage) {
                Screen results = stack.get(i++);
                place.putStringArrayList(PLACE_PICKS, ((PickResultsPage) results).ids());
                place.putString(PLACE_PICKS_BAR, ((PickResultsPage) results).barId());
                place.putString(PLACE_PICKS_FOCUS, results.pendingFocusKey != null ? results.pendingFocusKey
                        : results.lastFocusKey);
            }
        }
        ArrayList<Bundle> over = new ArrayList<>();
        for (; i < stack.size() && stack.get(i) instanceof TitlePreview; i++) {
            over.add(((TitlePreview) stack.get(i)).saved());
        }
        place.putParcelableArrayList(PLACE_OVER, over);
        out.putBundle(KEY_PLACE, place);
        SavedPlace.write(this, place, getTaskId());
    }

    /**
     * F26: the place savePlace kept, when it is still fresh (Comeback): its page with its choices and focus, and the
     * previews over it. From the saved Bundle, else from disk (SavedPlace), since Android may bring Home back without
     * its Bundle. False when there is none, and Home starts as usual. A Home press still starts Home again:
     * onNewIntent comes after this.
     */
    private boolean restorePlace(Bundle saved) {
        Bundle place = saved != null ? saved.getBundle(KEY_PLACE) : SavedPlace.read(this, getTaskId());
        if (place == null || !Comeback.fresh(place.getLong(PLACE_AT), place.getLong(PLACE_SLEPT),
                SystemClock.elapsedRealtime(), slept())) {
            return false;
        }
        Screen.Tab tab = null;
        for (Screen.Tab t : Screen.Tab.values()) if (t.name().equals(place.getString(PLACE_TAB))) tab = t;
        if (tab == null) return false;
        Screen page = shell.tab(tab);
        Bundle state = place.getBundle(PLACE_PAGE);
        if (state != null) page.restore(state);
        String focus = place.getString(PLACE_FOCUS);
        page.lastFocusKey = focus;
        shell.goTab(tab, false);
        if (focus != null && !focus.equals(Focus.key(shell.focused()))) page.pendingFocusKey = focus;
        boolean pick = restorePick(place, page, focus);
        ArrayList<Bundle> over = place.getParcelableArrayList(PLACE_OVER);
        if (over == null || over.isEmpty()) return true;
        if (!pick) {
            // The item focused may not be there yet (a search result TMDB sends again): Back from the preview looks
            // for it once more, rather than staying on the page's first item.
            page.lastFocus = null;
            page.lastFocusKey = focus;
        }
        for (Bundle b : over) {
            TitlePreview preview = TitlePreview.reopen(this, this, b);
            if (preview == null) break;
            shell.push(preview);
        }
        return true;
    }

    /**
     * F47: Pick for me's setup page as the place kept it, and its results with the same picks (not a new roll) and the
     * pick that was focused, which may arrive after the page (a cold start: Comeback's pending focus). False when the
     * place has no Pick for me.
     */
    private boolean restorePick(Bundle place, Screen page, String pageFocus) {
        Bundle saved = place.getBundle(PLACE_PICK);
        PickSetupPage setup = saved == null ? null : PickSetupPage.reopen(this, saved);
        if (setup == null) return false;
        page.lastFocus = null;
        page.lastFocusKey = pageFocus;
        shell.push(setup);
        ArrayList<String> ids = place.getStringArrayList(PLACE_PICKS);
        if (ids == null) return true;
        PickResultsPage results = setup.results(ids, place.getString(PLACE_PICKS_BAR));
        String focus = place.getString(PLACE_PICKS_FOCUS);
        // Before the push, so it focuses the pick at once when the catalogue already has it...
        results.lastFocusKey = focus;
        shell.push(results);
        if (focus != null && !focus.equals(Focus.key(shell.focused()))) results.pendingFocusKey = focus;
        // ...and after, since the push focused whatever was there (Adjust filters while the kept picks are still to
        // arrive): Back from a preview over it looks for the pick again.
        results.lastFocus = null;
        results.lastFocusKey = focus;
        return true;
    }

    /** How long the TV has slept since it started: the elapsed clock counts sleep, the uptime clock doesn't. */
    private static long slept() {
        return SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
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
        if (previewFallIfAsked(intent)) return;
        if (openInputsIfAsked(intent)) return;
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        shell.home();
        if (settingsPage != null && !shell.has(settingsPage)) settingsClosed();
        if (firstRun != null && !shell.has(firstRun)) {
            firstRunEnded(false);
            firstRunClosed();
        }
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
        // Any key stops the snow or rain before it is handled, so the frame it moves in has none to draw.
        if (event.getAction() == KeyEvent.ACTION_DOWN && weather != null) weather.onKey();
        if (handleSettingsKey(event)) return true;
        if (shell.dispatch(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    // ---- Lifecycle ----------------------------------------------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        // F16: a keys pane left showing opens its page's server again.
        ApiKeys.get(this).setForeground(true);
        // F27: a phone's keys and typing reach this Home while it is in front; elsewhere only Home works.
        PhoneRemote.get(this).attach(remoteBridge);
        // F26: in front again, so the place kept on disk for the trip away is out of date.
        SavedPlace.forget(this);
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
        Updates.get(this).listen(updateNotice);
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
        resumed = true;
        // Once a day while Home shows; its own thread, and only a release build (or a debug build's test feed) checks.
        Updates.get(this).maybeCheck();
        stage.post(updateNotice);
        // Callbacks keep the nav current while Home is in the background; this catches anything missed, after the
        // first frame back on Home so its binder calls never hold that frame up.
        stage.post(refreshNav);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        // Nothing of ours stays open over another app.
        shell.closeDialogs();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        // F16: the keys page's server runs only while its pane is on screen, not with the launcher in the background.
        ApiKeys.get(this).setForeground(false);
        PhoneRemote.get(this).detach(remoteBridge);
        backdrops.setVisible(false);
        unregisterReceiver(dateReceiver);
        Updates.get(this).unlisten(updateNotice);
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
        // M3: the artwork caches are only caches (the views on screen keep their images), so they go on any real
        // pressure, but not on UI_HIDDEN, the ordinary trip to an app.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            Art.trim();
            AppArt.clear();
        }
        if (!MemoryTrimPolicy.shouldRelease(level, started)) return;
        weather.release();
        backdrops.release();
        backdropLoaded = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ApiKeys.get(this).removeKeyListener(keyRefused);
        PhoneRemote.get(this).removeListener(remoteChanged);
        PhoneRemote.get(this).detach(remoteBridge);
        settingsClosed();
        firstRunClosed();
        handler.removeCallbacksAndMessages(null);
        ThemeFonts.forget(fontsArrived);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        unregisterReceiver(packageReceiver);
        // recreate() builds a new Data in the next onCreate; this one's price thread would otherwise live on.
        if (data != null) data.close();
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
