package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The TV's inputs: what the Inputs dropdown lists and how each row opens, moved out of the old MainActivity with its
 * rules unchanged (design_preview/README.md, Requirements: HDMI ports first, CEC devices take their port's row, hidden
 * inputs left out, bogus "all connected" states dropped). Also Live TV and the Open on start check, which share the
 * Live TV app's resolution.
 */
final class TvInputs {
    /** Shows a short message (Not available on this TV) in the launcher's own toast. */
    interface Notice {
        void say(int message);
    }

    private final Activity activity;
    private final Notice notice;
    private final String liveTvPkg;
    private final String liveTvAction;
    private final String liveTvInputId;
    private final String inputAction;
    private final String inputUriScheme;
    private final String sourcePanelService;
    private final String sourcePanelAction;
    private final String sourcePanelExtra;
    private final boolean coocaaSources;
    private final boolean bootToLiveTv;
    private final boolean openOnStartChoice;

    TvInputs(Activity activity, Config cfg, Notice notice) {
        this.activity = activity;
        this.notice = notice;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        liveTvInputId = cfg.liveTvInputId;
        inputAction = cfg.inputAction;
        inputUriScheme = cfg.inputUriScheme;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;
        sourcePanelExtra = cfg.sourcePanelExtra;
        coocaaSources = cfg.coocaaSources;
        bootToLiveTv = cfg.bootToLiveTv;
        openOnStartChoice = cfg.openOnStartChoice;
    }

    private TvInputManager manager() {
        return (TvInputManager) activity.getSystemService(Activity.TV_INPUT_SERVICE);
    }

    /** Whether there is anything to list: a streaming box has no HDMI-in, AV or tuner, and its nav has no Inputs. */
    boolean any() {
        if (liveTvIntent() != null || !sourcePanelService.isEmpty()) return true;
        if (coocaaSources && !CoocaaSources.list(activity).isEmpty()) return true;
        TvInputManager tim = manager();
        if (tim == null) return false;
        for (TvInputInfo info : tim.getTvInputList()) {
            // Inputs hidden in Settings > Inputs are left out of the menu, so they don't count here either.
            if (info.isPassthroughInput() && !info.isHidden(activity)) return true;
        }
        return false;
    }

    /**
     * A configured source panel and no Live TV row to offer next to it: the panel is the whole menu. The BYINTEK U4
     * needs this, as its TvInputManager lists three HDMI ports and an AV input it doesn't have.
     */
    boolean panelIsTheMenu() {
        boolean coocaa = coocaaSources && !CoocaaSources.list(activity).isEmpty();
        return !coocaa && !sourcePanelService.isEmpty() && liveTvIntent() == null;
    }

    /** The Inputs dropdown's rows: HDMI ports and CEC devices with their state, then AV, then Live TV. */
    List<MenuPanel.Row> rows() {
        return rows(false);
    }

    /** Home's Inputs row holds at most this many tiles and never scrolls; the rest are in the nav's Inputs menu. */
    static final int HOME_TILES = 4;

    /**
     * Home's Inputs row: the dropdown's rows with Live TV first, or, where the source panel is the whole menu
     * (panelIsTheMenu), one row that opens it rather than ports the TV reports but cannot switch to.
     */
    List<MenuPanel.Row> homeRows() {
        if (!panelIsTheMenu()) {
            List<MenuPanel.Row> rows = rows(true);
            // Live TV and the first ports, by the menu's order (project lead, 2026-10-30).
            return rows.size() > HOME_TILES ? new ArrayList<>(rows.subList(0, HOME_TILES)) : rows;
        }
        List<MenuPanel.Row> rows = new ArrayList<>();
        rows.add(new MenuPanel.Row(activity.getString(R.string.inputs), R.drawable.ic_ui_input, this::openSourcePanel));
        return rows;
    }

    /**
     * The same rows by the same rules; liveTvFirst puts Live TV ahead of the ports, as Home's Inputs row has it
     * (design_preview/src/screens/home.ts), with no divider.
     */
    List<MenuPanel.Row> rows(boolean liveTvFirst) {
        List<MenuPanel.Row> rows = new ArrayList<>();
        TvInputManager tim = manager();
        String tunerId = null;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(activity)).compareToIgnoreCase(String.valueOf(b.loadLabel(activity)));
            });
            // An HDMI-CEC device (an Apple TV, a soundbar) gets an input of its own whose parent is the port it sits
            // on, so the port and the device would each get a row (Vestel MT7332: "HDMI 1" and "HDMI Apple TV"). The
            // device takes its port's row instead, and a bare port is listed only when nothing on it speaks CEC.
            Set<String> inputIds = new HashSet<>();
            for (TvInputInfo info : inputs) inputIds.add(info.getId());
            Map<String, List<TvInputInfo>> cecDevices = new HashMap<>();
            for (TvInputInfo info : inputs) {
                String parent = info.getParentId();
                if (parent == null || !inputIds.contains(parent)) continue;
                if (!info.isPassthroughInput() || info.isHidden(activity)) continue;
                List<TvInputInfo> onPort = cecDevices.get(parent);
                if (onPort == null) cecDevices.put(parent, onPort = new ArrayList<>());
                onPort.add(info);
            }
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = InputRules.hdmiStateBogus(hdmiPorts, hdmiConnected);
            boolean sawNonHdmi = false;
            String[] mappings = activity.getResources().getStringArray(R.array.input_sources);
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (liveTvInputId.isEmpty() || liveTvInputId.equals(info.getId()))
                        && InputRules.betterTuner(info.getId(), tunerId)) {
                    tunerId = info.getId();
                }
                // Inputs hidden in the TV's Settings > Inputs stay out of the menu too.
                if (!info.isPassthroughInput() || info.isHidden(activity)) continue;
                // input_sources can leave out ports the board reports but the set does not have ("HW8=-").
                if (Integer.valueOf(-1).equals(InputRules.mappedSource(info.getId(), mappings))) continue;
                // A CEC device's row is added with its port, below.
                if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
                CharSequence l = info.loadLabel(activity);
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                String label = InputRules.portLabel(l == null ? info.getId() : l.toString(), hdmi);
                CharSequence custom = info.loadCustomLabel(activity);
                boolean named = custom != null && custom.length() > 0 && !custom.toString().equals(label);
                boolean divider = !hdmi && !rows.isEmpty() && !sawNonHdmi;
                if (!hdmi) sawNonHdmi = true;
                List<TvInputInfo> devices = cecDevices.get(info.getId());
                if (devices != null) {
                    // The device's own name (or the one given to it or to its port in Settings > Inputs), then the
                    // port. Opening the device's input also makes it the CEC active source, which wakes it.
                    for (TvInputInfo device : devices) {
                        CharSequence name = device.loadCustomLabel(activity);
                        if (name == null || name.length() == 0) name = named ? custom : device.loadLabel(activity);
                        String deviceId = device.getId();
                        int state = hdmiStateBogus ? -1 : tim.getInputState(deviceId);
                        rows.add(withState(new MenuPanel.Row(InputRules.named(name, label), R.drawable.ic_ui_hdmi,
                                () -> openInput(deviceId)), state, divider));
                        divider = false;
                    }
                    continue;
                }
                String id = info.getId();
                // Analog AV jacks have no hotplug detection, and some TVs report them as always connected (TCL Smart
                // TV Pro G03 says "Connected" with nothing plugged in), so AV rows show no state.
                int state = !hdmi || hdmiStateBogus ? -1 : tim.getInputState(id);
                rows.add(withState(new MenuPanel.Row(named ? InputRules.named(custom, label) : label,
                        hdmi ? R.drawable.ic_ui_hdmi : R.drawable.ic_ui_av, () -> openInput(id)), state, divider));
            }
        }
        // Coocaa OS registers nothing with TvInputManager; com.skyworth.tv lists its sources instead.
        if (rows.isEmpty() && coocaaSources) {
            for (CoocaaSources.Source s : CoocaaSources.list(activity)) {
                int icon = "HDMI".equals(s.name) ? R.drawable.ic_ui_hdmi
                        : "VOD".equals(s.name) ? R.drawable.ic_ui_live_tv : R.drawable.ic_ui_av;
                rows.add(new MenuPanel.Row(s.label, icon, () -> {
                    if (!CoocaaSources.open(activity, s)) notice.say(R.string.not_available);
                }));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.isEmpty() && !sourcePanelService.isEmpty()) {
            rows.add(new MenuPanel.Row(activity.getString(R.string.inputs), R.drawable.ic_ui_input,
                    this::openSourcePanel));
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            // Tune that input only when the Live TV package itself handles the URI. The unrestricted input player
            // also resolves it (that is how HDMI opens), but on the AIWA AI PONT SA that player stays black, and the
            // Live TV package does not handle the URI at all, which used to toast "Not available on this TV". Keep
            // the leanback activity then: it is the same path as the Live TV tile. An explicit tuner uses its
            // channel collection (e.g. DVB-S rather than DVB-T/C); automatic selection keeps the legacy
            // passthrough route, which some OEM players need.
            boolean configured = !liveTvInputId.isEmpty();
            Intent tuned = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(configured ? TvContract.buildChannelsUriForInput(tunerId)
                                    : TvContract.buildChannelUriForPassthroughInput(tunerId),
                            configured ? TvContract.Channels.CONTENT_TYPE : TvContract.Channels.CONTENT_ITEM_TYPE)
                    .setPackage(liveTvPkg);
            if (tuned.resolveActivity(activity.getPackageManager()) != null) liveTv = tuned;
        }
        if (liveTv != null) {
            Intent open = liveTv;
            MenuPanel.Row row = new MenuPanel.Row(activity.getString(R.string.live_tv), R.drawable.ic_ui_live_tv,
                    () -> start(open));
            if (liveTvFirst) {
                rows.add(0, row);
            } else {
                if (!rows.isEmpty()) row.divider();
                rows.add(row);
            }
        }
        return rows;
    }

    /** Only definite states are shown: some TVs report "standby" for every port, plugged in or not. */
    private MenuPanel.Row withState(MenuPanel.Row row, int state, boolean divider) {
        if (state == TvInputManager.INPUT_STATE_CONNECTED) row.status(activity.getString(R.string.connected), true);
        else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) row.status(activity.getString(R.string.not_connected), false);
        if (divider) row.divider();
        return row;
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = activity.getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return activity.getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /**
     * Switches to a passthrough input (HDMI, AV). An input mapped in input_sources is opened by sending input_action
     * with its source number as the int extra "input" (Xiaomi China firmware, whose tvplayer answers
     * com.xiaomi.mitv.tvplayer.EXTSRC_PLAY and nothing else). Otherwise the standard way is ACTION_VIEW on its
     * channel URI, which the Live TV app handles; where nothing takes that, input_action is sent with the same URI
     * (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). With input_uri_scheme set (YaOS: live-tv) the
     * URI goes out with that scheme and no MIME type. A player guarded by a permission this build does not declare
     * throws SecurityException, which must not take Home down.
     */
    void openInput(String inputId) {
        // A CEC device is a child input of its HDMI port; the vendor source number belongs to the port (MiTV-MFFU0).
        // Only that lookup uses the parent: the generic path below still opens the child.
        String sourceId = inputId;
        TvInputManager tim = manager();
        if (tim != null) {
            try {
                TvInputInfo info = tim.getTvInputInfo(inputId);
                if (info != null && info.getParentId() != null) sourceId = info.getParentId();
            } catch (RuntimeException ignored) {
            }
        }
        Integer source = InputRules.mappedSource(sourceId, activity.getResources().getStringArray(R.array.input_sources));
        if (source != null && source >= 0 && !inputAction.isEmpty()) {
            try {
                activity.startActivity(new Intent(inputAction).putExtra("input", source.intValue())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                // fall through to the standard way
            }
        }
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = (inputUriScheme.isEmpty()
                ? new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                : new Intent(Intent.ACTION_VIEW, uri.buildUpon().scheme(inputUriScheme).build()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Aim it at the TV app first: apps installed later (a streaming app, a file manager) can claim the same
        // channel URI too, and with two or more handlers Android shows a chooser instead of the input (WhaleOS).
        if (!liveTvPkg.isEmpty()) {
            try {
                activity.startActivity(new Intent(view).setPackage(liveTvPkg));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        try {
            activity.startActivity(view);
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            if (inputAction.isEmpty()) {
                notice.say(R.string.not_available);
                return;
            }
        }
        start(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9): a service, or
     *  an activity on sets whose panel is a dialog (BYINTEK U4's QuickSourceActivity). */
    void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        int eq = sourcePanelExtra.indexOf('=');
        if (eq > 0) {
            try {
                i.putExtra(sourcePanelExtra.substring(0, eq).trim(),
                        Integer.parseInt(sourcePanelExtra.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            if (activity.getPackageManager().resolveActivity(i, 0) != null) {
                activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            activity.startService(i);
        } catch (Exception e) {
            notice.say(R.string.not_available);
        }
    }

    /**
     * boot_to_live_tv, or the Open on start app: the first time Home comes up after the TV starts, open that app
     * instead. Once per boot, keyed on Settings.Global.BOOT_COUNT and committed to disk before the app starts: on a
     * low-memory TV the app can push the launcher out of memory, and an apply() still in flight was lost then, so
     * every Home press opened the app again.
     */
    boolean openOnStartIfDue() {
        String app = openOnStartChoice ? activity.getSharedPreferences(Config.SETTINGS_NAME, Activity.MODE_PRIVATE)
                .getString(OpenOnStart.KEY, "") : "";
        if (!bootToLiveTv && app.isEmpty()) return false;
        int boot = OpenOnStart.boot(activity);
        SharedPreferences prefs = activity.getSharedPreferences(OpenOnStart.BOOT_PREFS, Activity.MODE_PRIVATE);
        String target = bootToLiveTv ? liveTvPkg : app;
        if (!OpenOnStart.due(boot, prefs.getInt(OpenOnStart.BOOT_KEY, -1), target, activity.getPackageName())) return false;
        if (!prefs.edit().putInt(OpenOnStart.BOOT_KEY, boot).commit()) return false;
        Intent i = bootToLiveTv ? liveTvIntent() : OpenOnStart.intent(activity.getPackageManager(), app);
        if (i == null) return false;
        start(i);
        return true;
    }

    void start(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            notice.say(R.string.not_available);
        }
    }
}
