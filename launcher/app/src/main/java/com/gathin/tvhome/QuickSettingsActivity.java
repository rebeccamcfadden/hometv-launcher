package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService): the same
 * quick-settings dropdown as on Home (MenuPanel, Menus), hanging where the Settings icon sits, over Home or whatever
 * app is playing. Translucent and in a task of its own, so the app behind keeps running and Back returns to it.
 * Pressing Settings again closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    private View panel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Config cfg = Config.load(this);
        Ui.init(this, cfg.reducedMotion());
        Theme theme = ThemeStore.current(this);
        Ui.apply(theme);
        PerfChoices.apply(PerfPrefs.load(this, cfg));
        ThemeFonts.apply(this, theme);
        FrameLayout root = new FrameLayout(this);
        View catcher = new View(this);
        catcher.setOnClickListener(v -> finish());
        root.addView(catcher, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Through Home, where Launcher settings is a page.
        List<MenuPanel.Row> rows = Menus.quickSettings(this, this::open, () -> open(
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS)));
        LinearLayout panel = MenuPanel.build(this, null, rows, row -> {
            row.action.run();
            finish();
        });
        this.panel = panel;
        // Under the nav's Settings icon: the device controls end 6 px inside the title-safe edge, 112 px down.
        root.addView(panel, MenuPanel.hang(Ui.px(126), Ui.px(Ui.SAFE_X + 6)));
        setContentView(root);
        MenuPanel.dropIn(panel);
        // This panel uses Android's own focus: a single column, where it moves exactly as the D-pad expects.
        View first = Focus.all(panel).isEmpty() ? null : Focus.all(panel).get(0);
        if (first != null) first.requestFocus();
    }

    /** A D-pad move past the panel's edge closes it, like the dropdowns on Home. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int dir = focusDirection(event.getKeyCode());
        if (dir != 0 && event.getAction() == KeyEvent.ACTION_DOWN && panel != null) {
            View focused = getCurrentFocus();
            View next = focused == null ? null : focused.focusSearch(dir);
            if (next == null || !Focus.isInside(panel, next)) {
                finish();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static int focusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return View.FOCUS_RIGHT;
            default:
                return 0;
        }
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void open(Intent target) {
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
