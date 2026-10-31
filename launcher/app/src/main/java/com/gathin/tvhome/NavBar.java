package com.gathin.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The nav at the top of every page (design_preview/src/ui/widgets.ts, navBar; Figma 01 · Home, Navigation): Home,
 * Movies, TV Shows and Music in one pill, the search icon, and Wi-Fi, Inputs and Settings on the right. Landing on a
 * section only highlights it; OK switches to it.
 */
final class NavBar {
    private NavBar() {}

    interface Host {
        Shell shell();

        NavModel navModel();

        void openNetwork(View anchor);

        void openInputs(View anchor);

        void openSettings(View anchor);
    }

    static final String KEY_WIFI = "dev-wifi";
    static final String KEY_INPUTS = "dev-input";
    static final String KEY_SETTINGS = "dev-settings";
    private static final String DEVICE_GROUP = "device";

    static View build(Context ctx, Host host, Screen.Tab active) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setTag(R.id.ui_nav, Boolean.TRUE);

        LinearLayout sections = new LinearLayout(ctx);
        sections.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.px(6);
        sections.setPadding(pad, pad, pad, pad);
        // The focused section scales up 5%; clipped to the padding, its outer side was cut flat.
        sections.setClipToPadding(false);
        sections.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, Ui.LINE_W));
        int[] labels = {R.string.nav_home, R.string.nav_movies, R.string.nav_tv, R.string.nav_music};
        Screen.Tab[] ids = {Screen.Tab.HOME, Screen.Tab.MOVIES, Screen.Tab.TV, Screen.Tab.MUSIC};
        for (int i = 0; i < ids.length; i++) {
            TextView t = section(ctx, host, ids[i], active, labels[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.px(60));
            if (i > 0) lp.leftMargin = Ui.px(6);
            sections.addView(t, lp);
        }
        bar.addView(sections, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView search = round(ctx, R.drawable.ic_ui_search, 64, 26, Ui.TEXT_1, Ui.ACTIVE);
        search.setContentDescription(ctx.getString(R.string.nav_search));
        search.setActivated(active == Screen.Tab.SEARCH);
        Kit.reportsSelected(search);
        Focus.make(search, Shell.NAV_GROUP, Shell.navKey(Screen.Tab.SEARCH), active == Screen.Tab.SEARCH,
                v -> open(host, Screen.Tab.SEARCH, v));
        Ui.scaleOnFocus(search, 1.05f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64));
        slp.leftMargin = Ui.px(40);
        bar.addView(search, slp);

        bar.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout device = new LinearLayout(ctx);
        device.setOrientation(LinearLayout.HORIZONTAL);
        device.setPadding(pad, pad, pad, pad);
        device.setClipToPadding(false);
        device.setBackground(Ui.round(Ui.SURFACE, Ui.RADIUS_PILL, Ui.LINE, Ui.LINE_W));
        ImageView wifi = deviceIcon(ctx, R.drawable.ic_ui_wifi, R.string.wifi, KEY_WIFI, host::openNetwork);
        ImageView inputs = deviceIcon(ctx, R.drawable.ic_ui_input, R.string.inputs, KEY_INPUTS, host::openInputs);
        ImageView settings = deviceIcon(ctx, R.drawable.ic_ui_settings, R.string.settings, KEY_SETTINGS,
                host::openSettings);
        ImageView[] icons = {wifi, inputs, settings};
        for (ImageView icon : icons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(56), Ui.px(56));
            if (device.getChildCount() > 0) lp.leftMargin = Ui.px(8);
            device.addView(icon, lp);
        }
        bar.addView(device, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        NavModel.Listener listener = model -> {
            wifi.setVisibility(model.networkShown ? View.VISIBLE : View.GONE);
            wifi.setImageResource(model.networkIcon);
            inputs.setVisibility(model.inputsShown ? View.VISIBLE : View.GONE);
            // The first icon left keeps no gap before it.
            for (int i = 0, shown = 0; i < icons.length; i++) {
                if (icons[i].getVisibility() != View.VISIBLE) continue;
                ((LinearLayout.LayoutParams) icons[i].getLayoutParams()).leftMargin = shown++ == 0 ? 0 : Ui.px(8);
            }
            device.requestLayout();
        };
        // Listen only while on screen: the model lives as long as the activity, and pages pushed and popped (Pick
        // for me, the person page) would otherwise stay reachable through it forever. Attaching again catches up.
        host.navModel().listen(listener);
        bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            private boolean listening = true;

            @Override
            public void onViewAttachedToWindow(View v) {
                if (listening) return;
                listening = true;
                host.navModel().listen(listener);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                listening = false;
                host.navModel().unlisten(listener);
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(72),
                Gravity.TOP);
        lp.topMargin = Ui.px(48);
        lp.leftMargin = Ui.px(Ui.SAFE_X);
        lp.rightMargin = Ui.px(Ui.SAFE_X);
        bar.setLayoutParams(lp);
        return bar;
    }

    private static TextView section(Context ctx, Host host, Screen.Tab id, Screen.Tab active, int label) {
        boolean isActive = id == active;
        TextView t = Ui.line(ctx, 25, Ui.TEXT_2, isActive ? Ui.MEDIUM : Ui.REGULAR);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.px(30), 0, Ui.px(30), 0);
        t.setTextColor(Ui.colors(Ui.TEXT_2, Ui.ON_ACTIVE, Ui.ON_ACCENT));
        t.setBackground(Ui.states(Ui.round(0, Ui.RADIUS_PILL), Ui.round(Ui.ACTIVE, Ui.RADIUS_PILL),
                Ui.round(Ui.ACCENT, Ui.RADIUS_PILL)));
        t.setActivated(isActive);
        Kit.reportsSelected(t);
        Focus.make(t, Shell.NAV_GROUP, Shell.navKey(id), isActive, v -> open(host, id, v));
        Ui.scaleOnFocus(t, 1.05f);
        return t;
    }

    /** OK on the page already showing does nothing, so it never resets a page pushed over it. */
    private static void open(Host host, Screen.Tab id, View item) {
        if (item.isActivated()) return;
        host.shell().switchTab(id);
    }

    private interface Action {
        void run(View anchor);
    }

    private static ImageView deviceIcon(Context ctx, int icon, int label, String key, Action action) {
        ImageView v = round(ctx, icon, 56, 28, Ui.TEXT_1, 0);
        v.setContentDescription(ctx.getString(label));
        Focus.make(v, DEVICE_GROUP, key, false, action::run);
        Ui.scaleOnFocus(v, 1.05f);
        return v;
    }

    /** A round icon button: tinted icon, accent fill when focused, activeFill when it is the current section. */
    private static ImageView round(Context ctx, int icon, float size, float iconSize, int color, int activeFill) {
        ImageView v = new ImageView(ctx);
        v.setImageResource(icon);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.px((size - iconSize) / 2);
        v.setPadding(pad, pad, pad, pad);
        v.setImageTintList(Ui.colors(color, Ui.ON_ACTIVE, Ui.ON_ACCENT));
        v.setBackground(Ui.states(Ui.oval(0), activeFill != 0 ? Ui.oval(activeFill) : null, Ui.oval(Ui.ACCENT)));
        return v;
    }
}
