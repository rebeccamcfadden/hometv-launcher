package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.gathin.tvhome/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a TV's own settings (dock apps, the Live TV app, remote keys)
 * change without a rebuild: {@code tools/theme.sh examples/<tv>} pushes a folder's config.json there and
 * restarts Home.
 *
 * Only the keys the launcher reads are here, plus a few that the config.json files in examples/ still set (see
 * "Kept for examples" below). The old Home's theme keys were removed in the rework (docs/rework.md, M1b).
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
final class Config {
    private static final String TAG = "TvHome.Config";
    static final String FILE_NAME = "config.json";
    static final String SETTINGS_NAME = "launcher_settings";
    /**
     * Counts the changes made in Launcher settings. Home remembers the count it was built with and rebuilds on
     * resume when it differs, however the settings were left (see MainActivity#onResume).
     */
    static final String SETTINGS_REVISION_KEY = "settings_revision";
    /**
     * Choices the old Home's Launcher settings rows saved (apps per row, rows below the dock, advanced effects,
     * the wallpaper pick, the dock opacity). Nothing reads them now; Follow theme and a theme push still clear
     * them, so they don't linger on a TV that ran the old launcher.
     */
    static final String[] OLD_CHOICES = {"columns", "rows_below_dock", "advanced_effects", "wallpaper_choice",
            "dock_opacity"};

    /** True when Launcher settings changed something since Home was built at revision {@code seen}. */
    static boolean settingsChanged(long seen, long current) {
        return seen != current;
    }

    /** The new UI's reduced motion: reduced_motion, or Animations turned off in Launcher settings. */
    boolean reducedMotion() {
        return reducedMotion || !animations;
    }
    private static final String THEME_REVISION_NAME = "theme_revision";
    private static final String THEME_SIGNATURE_KEY = "theme_signature";

    /** The Home app row's apps, in this order, until the household reorders them (config.xml, dock_apps). */
    final List<String> dockApps;
    final List<String> hiddenApps;
    final String liveTvPackage;
    final String liveTvAction;
    final String liveTvInputId;
    /** Package to the name its tile shows instead of the app's label (config.xml, app_labels). */
    final Map<String, String> appLabels;
    final String sourcePanelService;
    final String sourcePanelAction;
    /** One int extra for the source panel, "name=value" (config.xml source_panel_extra); empty for none. */
    final String sourcePanelExtra;
    /** List Coocaa OS sources from com.skyworth.tv in the Inputs menu (config.xml coocaa_sources). */
    final boolean coocaaSources;
    final String inputAction;
    /** Scheme an Inputs row's passthrough URI is sent with instead of content:// (config.xml input_uri_scheme). */
    final String inputUriScheme;
    /** Animations on (config.xml, animations), unless turned off in Launcher settings. */
    final boolean animations;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion); see {@link #reducedMotion()}. */
    final boolean reducedMotion;
    /** Open the Live TV app on the first Home after the TV starts (config.xml, boot_to_live_tv). */
    final boolean bootToLiveTv;
    /** Show the Open on start row in Launcher settings (config.xml, open_on_start_choice). */
    final boolean openOnStartChoice;
    /** The Settings button opens Launcher settings instead of the TV's Settings (launcher_settings_button). */
    final boolean launcherSettingsButton;
    /** Show the Wi-Fi/Ethernet button in the nav bar (config.xml, show_network_pill). */
    final boolean showNetworkPill;
    /** Show the Inputs button when the TV has inputs to list (config.xml, show_inputs_pill). */
    final boolean showInputsPill;
    /** VPN app the network dropdown offers (config.xml, vpn_app); empty for none. */
    final String vpnApp;
    /** Remote keycodes that open Settings through HomeKeyService (config.xml, settings_key_keycodes). */
    final int[] settingsKeycodes;
    /** Extra keycodes that open the input switcher in InputKeyService (config.xml input_key_keycodes). */
    final int[] inputKeycodes;
    /** Remote buttons that open an app, "keycode:package" or "KEYCODE_NAME:package" (config.xml app_keys). */
    final List<String> appKeys;
    /** Whether the key services request key filtering (config.xml or theme key_filter); false keeps key
     *  auto-repeat working on firmware whose AccessibilityInputFilter drops repeats (TCL Android 12). */
    final boolean keyFilter;
    /** With keyFilter on, the key services stop filtering while a game controller is connected (gamepad_passthrough). */
    final boolean gamepadPassthrough;
    /** With keyFilter on, the key services swallow Volume Up/Down and repeat a held one themselves (consume_volume_keys). */
    final boolean consumeVolumeKeys;
    /** Stock launcher whose home screen is sent back here if the firmware revives it (config.xml stock_home_redirect). */
    final String stockHomeRedirect;
    /** The Settings button opens the quick settings panel instead of the TV's Settings (settings_key_panel). */
    final boolean settingsKeyPanel;
    /** Home puts the remote's accessibility helpers back after a force stop (restore_key_services; ServiceRepair). */
    final boolean restoreKeyServices;
    /** Answer VIEW_INPUTS with the Inputs menu (handle_view_inputs); see InputsRequestActivity. */
    final boolean handleViewInputs;
    /**
     * The streaming services the household pays for, as lowercase ids such as {@code netflix} (config.xml /
     * config.json subscribed_services). Empty works them out from the installed apps (Services, ServiceTable).
     */
    final List<String> subscribedServices;

    // Kept for examples: keys that examples/*/config.json sets, so those files keep their meaning. The current
    // launcher doesn't read them; V2 decides what becomes of them (docs/rework.md, M1b; themes/README.md).
    /** fill_dock: grid apps fill the dock slots dock_apps leaves free. */
    final boolean fillDock;
    /** app_library: the old Home's App library. */
    final boolean appLibrary;
    /** background_color, #RRGGBB; black unless a config.json says otherwise. */
    final int backgroundColor;
    /** columns: the old Home's tiles per row, 1 to 8. */
    final int columns;
    /** rows_below_dock: the old Home's grid rows under the dock, 0 to 3. */
    final int rowsBelowDock;
    /** greeting_name: a name after the greeting. */
    final String greetingName;
    /** icon_only_buttons: the old top bar's Wi-Fi and Settings pills as round icons. */
    final boolean iconOnlyButtons;
    /** wifi_label_width: with icon_only_buttons, the Wi-Fi pill's name up to this width in dp. */
    final int wifiLabelWidth;
    /** tile_menu: hold OK on a tile opens Move, Remove from Home and App info (always on in the new Home). */
    final boolean tileMenu;
    /** wallpaper: whether a theme may show a wallpaper. */
    final boolean wallpaperEnabled;

    private Config(List<String> dockApps, List<String> hiddenApps, String liveTvPackage,
                   String liveTvAction, String liveTvInputId, Map<String, String> appLabels,
                   String sourcePanelService, String sourcePanelAction, String sourcePanelExtra,
                   boolean coocaaSources, String inputAction, String inputUriScheme, boolean animations,
                   boolean reducedMotion, boolean bootToLiveTv, boolean openOnStartChoice,
                   boolean launcherSettingsButton, boolean showNetworkPill, boolean showInputsPill, String vpnApp,
                   int[] settingsKeycodes, boolean settingsKeyPanel, boolean restoreKeyServices,
                   int[] inputKeycodes, List<String> appKeys, String stockHomeRedirect, boolean keyFilter,
                   boolean handleViewInputs, boolean gamepadPassthrough, boolean consumeVolumeKeys,
                   List<String> subscribedServices,
                   boolean fillDock, boolean appLibrary, int backgroundColor, int columns, int rowsBelowDock,
                   String greetingName, boolean iconOnlyButtons, int wifiLabelWidth, boolean tileMenu,
                   boolean wallpaperEnabled) {
        this.subscribedServices = Collections.unmodifiableList(subscribedServices);
        this.handleViewInputs = handleViewInputs;
        this.inputKeycodes = inputKeycodes;
        this.appKeys = Collections.unmodifiableList(appKeys);
        this.keyFilter = keyFilter;
        this.stockHomeRedirect = stockHomeRedirect;
        this.dockApps = Collections.unmodifiableList(dockApps);
        this.hiddenApps = Collections.unmodifiableList(hiddenApps);
        this.liveTvPackage = liveTvPackage;
        this.liveTvAction = liveTvAction;
        this.liveTvInputId = liveTvInputId;
        this.appLabels = Collections.unmodifiableMap(appLabels);
        this.sourcePanelService = sourcePanelService;
        this.sourcePanelAction = sourcePanelAction;
        this.sourcePanelExtra = sourcePanelExtra;
        this.coocaaSources = coocaaSources;
        this.inputAction = inputAction;
        this.inputUriScheme = inputUriScheme;
        this.animations = animations;
        this.reducedMotion = reducedMotion;
        this.bootToLiveTv = bootToLiveTv;
        this.openOnStartChoice = openOnStartChoice;
        this.launcherSettingsButton = launcherSettingsButton;
        this.showNetworkPill = showNetworkPill;
        this.showInputsPill = showInputsPill;
        this.vpnApp = vpnApp;
        this.settingsKeycodes = settingsKeycodes;
        this.settingsKeyPanel = settingsKeyPanel;
        this.restoreKeyServices = restoreKeyServices;
        this.gamepadPassthrough = gamepadPassthrough;
        this.consumeVolumeKeys = consumeVolumeKeys;
        this.fillDock = fillDock;
        this.appLibrary = appLibrary;
        this.backgroundColor = backgroundColor;
        this.columns = columns;
        this.rowsBelowDock = rowsBelowDock;
        this.greetingName = greetingName;
        this.iconOnlyButtons = iconOnlyButtons;
        this.wifiLabelWidth = wifiLabelWidth;
        this.tileMenu = tileMenu;
        this.wallpaperEnabled = wallpaperEnabled;
    }

    /** The folder the theme tools push into; null when external storage is unavailable. */
    static File dir(Context ctx) {
        return ctx.getExternalFilesDir(null);
    }

    static Config load(Context ctx) {
        Resources res = ctx.getResources();
        List<String> dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        List<String> hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));
        String liveTv = res.getString(R.string.live_tv_package).trim();
        String liveTvAction = res.getString(R.string.live_tv_action).trim();
        String liveTvInputId = res.getString(R.string.live_tv_input_id).trim();
        List<String> appLabels = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_labels)));
        String panelService = res.getString(R.string.source_panel_service).trim();
        String panelAction = res.getString(R.string.source_panel_action).trim();
        String panelExtra = res.getString(R.string.source_panel_extra).trim();
        boolean coocaaSources = res.getBoolean(R.bool.coocaa_sources);
        String inputAction = res.getString(R.string.input_action).trim();
        String inputUriScheme = res.getString(R.string.input_uri_scheme).trim();
        boolean animations = res.getBoolean(R.bool.animations);
        boolean reducedMotion = res.getBoolean(R.bool.reduced_motion);
        boolean bootToLiveTv = res.getBoolean(R.bool.boot_to_live_tv);
        boolean openOnStartChoice = res.getBoolean(R.bool.open_on_start_choice);
        boolean launcherSettingsButton = res.getBoolean(R.bool.launcher_settings_button);
        boolean showNetworkPill = res.getBoolean(R.bool.show_network_pill);
        boolean showInputsPill = res.getBoolean(R.bool.show_inputs_pill);
        String vpn = res.getString(R.string.vpn_app).trim();
        int[] settingsKeycodes = res.getIntArray(R.array.settings_key_keycodes);
        boolean settingsKeyPanel = res.getBoolean(R.bool.settings_key_panel);
        boolean restoreKeyServices = res.getBoolean(R.bool.restore_key_services);
        int[] inputKeycodes = res.getIntArray(R.array.input_key_keycodes);
        boolean handleViewInputs = res.getBoolean(R.bool.handle_view_inputs);
        List<String> appKeys = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_keys)));
        String stockHomeRedirect = res.getString(R.string.stock_home_redirect).trim();
        boolean keyFilter = res.getBoolean(R.bool.key_filter);
        boolean gamepadPassthrough = res.getBoolean(R.bool.gamepad_passthrough);
        boolean consumeVolumeKeys = res.getBoolean(R.bool.consume_volume_keys);
        List<String> subscribedServices = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.subscribed_services)));
        boolean fillDock = res.getBoolean(R.bool.fill_dock);
        boolean library = res.getBoolean(R.bool.app_library);
        int background = 0xFF000000;
        int columns = res.getInteger(R.integer.columns);
        int rowsBelow = res.getInteger(R.integer.rows_below_dock);
        String greetingName = res.getString(R.string.greeting_name).trim();
        boolean iconOnlyButtons = res.getBoolean(R.bool.icon_only_buttons);
        int wifiLabelWidth = res.getInteger(R.integer.wifi_label_width);
        boolean tileMenu = res.getBoolean(R.bool.tile_menu);
        boolean wallpaperEnabled = true;

        File dir = dir(ctx);
        File file = dir != null ? new File(dir, FILE_NAME) : null;
        boolean mounted = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        JSONObject json = readOverlay(ctx, file, mounted);
        if (json != null) {
            dock = stringList(json, "dock_apps", dock);
            hidden = stringList(json, "hidden_apps", hidden);
            liveTv = string(json, "live_tv_package", liveTv);
            liveTvAction = string(json, "live_tv_action", liveTvAction);
            liveTvInputId = string(json, "live_tv_input_id", liveTvInputId);
            appLabels = stringList(json, "app_labels", appLabels);
            panelService = string(json, "source_panel_service", panelService);
            panelAction = string(json, "source_panel_action", panelAction);
            panelExtra = string(json, "source_panel_extra", panelExtra).trim();
            coocaaSources = bool(json, "coocaa_sources", coocaaSources);
            inputAction = string(json, "input_action", inputAction);
            inputUriScheme = string(json, "input_uri_scheme", inputUriScheme).trim();
            animations = bool(json, "animations", animations);
            reducedMotion = bool(json, "reduced_motion", reducedMotion);
            bootToLiveTv = bool(json, "boot_to_live_tv", bootToLiveTv);
            openOnStartChoice = bool(json, "open_on_start_choice", openOnStartChoice);
            launcherSettingsButton = bool(json, "launcher_settings_button", launcherSettingsButton);
            showNetworkPill = bool(json, "show_network_pill", showNetworkPill);
            showInputsPill = bool(json, "show_inputs_pill", showInputsPill);
            vpn = string(json, "vpn_app", vpn);
            settingsKeycodes = intArray(json, "settings_key_keycodes", settingsKeycodes);
            settingsKeyPanel = bool(json, "settings_key_panel", settingsKeyPanel);
            restoreKeyServices = bool(json, "restore_key_services", restoreKeyServices);
            inputKeycodes = intArray(json, "input_key_keycodes", inputKeycodes);
            handleViewInputs = bool(json, "handle_view_inputs", handleViewInputs);
            appKeys = stringList(json, "app_keys", appKeys);
            stockHomeRedirect = string(json, "stock_home_redirect", stockHomeRedirect);
            keyFilter = bool(json, "key_filter", keyFilter);
            gamepadPassthrough = bool(json, "gamepad_passthrough", gamepadPassthrough);
            consumeVolumeKeys = bool(json, "consume_volume_keys", consumeVolumeKeys);
            subscribedServices = stringList(json, "subscribed_services", subscribedServices);
            fillDock = bool(json, "fill_dock", fillDock);
            library = bool(json, "app_library", library);
            background = color(json, "background_color", background);
            columns = integer(json, "columns", columns, 1, 8);
            rowsBelow = integer(json, "rows_below_dock", rowsBelow, 0, 3);
            greetingName = string(json, "greeting_name", greetingName).trim();
            iconOnlyButtons = bool(json, "icon_only_buttons", iconOnlyButtons);
            wifiLabelWidth = integer(json, "wifi_label_width", wifiLabelWidth, 0, 300);
            tileMenu = bool(json, "tile_menu", tileMenu);
            wallpaperEnabled = bool(json, "wallpaper", true);
        }
        // A theme push or reset invalidates choices made on the TV. On first use, remember the
        // current theme without clearing choices from an older launcher version.
        SharedPreferences choices = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE);
        // Unmounted storage looks like a theme reset; compare only once the file is readable.
        if (mounted) syncThemeChoices(choices, dir, file);
        animations = choices.getBoolean("animations", animations);

        return new Config(dock, hidden, liveTv, liveTvAction, liveTvInputId, packageMap(appLabels),
                panelService, panelAction, panelExtra, coocaaSources, inputAction, inputUriScheme, animations,
                reducedMotion, bootToLiveTv, openOnStartChoice, launcherSettingsButton, showNetworkPill,
                showInputsPill, vpn, settingsKeycodes, settingsKeyPanel, restoreKeyServices, inputKeycodes,
                appKeys, stockHomeRedirect, keyFilter, handleViewInputs, gamepadPassthrough, consumeVolumeKeys,
                subscribedServices,
                fillDock, library, background, Math.max(1, columns), Math.max(0, rowsBelow), greetingName,
                iconOnlyButtons, wifiLabelWidth, tileMenu, wallpaperEnabled);
    }

    private static void syncThemeChoices(SharedPreferences choices, File dir, File config) {
        File revision = dir == null ? null : new File(dir, THEME_REVISION_NAME);
        String token = revisionToken(revision);
        String signature = fileStamp(config) + ":" + token;
        String seen = choices.getString(THEME_SIGNATURE_KEY, null);
        if (seen == null) {
            // Existing preferences survive an app upgrade, unless the first launch follows an
            // explicit theme push or reset by the updated tools.
            SharedPreferences.Editor editor = choices.edit();
            if (!"absent".equals(token)) clearChoices(editor);
            editor.putString(THEME_SIGNATURE_KEY, signature).commit();
        } else if (!seen.equals(signature)) {
            SharedPreferences.Editor editor = choices.edit();
            clearChoices(editor);
            editor.putString(THEME_SIGNATURE_KEY, signature).commit();
        }
    }

    private static void clearChoices(SharedPreferences.Editor editor) {
        editor.remove("animations");
        for (String key : OLD_CHOICES) editor.remove(key);
    }

    private static String fileStamp(File file) {
        return file != null && file.isFile() ? file.length() + "@" + file.lastModified() : "absent";
    }

    private static String revisionToken(File file) {
        if (file == null || !file.isFile() || file.length() > 64) return "absent";
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            return n > 0 ? new String(bytes, 0, n, StandardCharsets.US_ASCII) : "absent";
        } catch (Exception ignored) {
            return "absent";
        }
    }

    /** Reads and parses the overlay; any problem means "no overlay", never a crash on the home screen. */
    /**
     * The overlay, or before external storage is mounted, the copy kept from the last time it was read. Home can
     * start before the card is mounted (seen at every boot on a Xiaomi MiTV-MTEQ0), and without the copy it comes
     * up with the built-in dock order. A missing file on mounted storage still means no overlay.
     */
    private static JSONObject readOverlay(Context ctx, File file, boolean mounted) {
        File copy = new File(ctx.getFilesDir(), FILE_NAME);
        if (!mounted) return read(copy);
        JSONObject json = read(file);
        if (json == null) {
            copy.delete();
        } else {
            try (FileOutputStream out = new FileOutputStream(copy)) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "could not keep a copy of " + file + ": " + e.getMessage());
            }
        }
        return json;
    }

    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Positive keycodes only (vendor remotes use codes in the thousands); a list with anything else in it is ignored as a whole. */
    private static int[] intArray(JSONObject o, String key, int[] fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        int[] out = new int[a.length()];
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (!(item instanceof Number)) return fallback;
            out[i] = ((Number) item).intValue();
            if (out[i] < 1) return fallback;
        }
        return out;
    }

    /** "package=value" entries; an entry without both halves is skipped, and a later entry for a package wins. */
    static Map<String, String> packageMap(List<String> entries) {
        Map<String, String> out = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String pkg = entry.substring(0, eq).trim(), value = entry.substring(eq + 1).trim();
            if (!pkg.isEmpty() && !value.isEmpty()) out.put(pkg, value);
        }
        return out;
    }

    private static List<String> stringList(JSONObject o, String key, List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
