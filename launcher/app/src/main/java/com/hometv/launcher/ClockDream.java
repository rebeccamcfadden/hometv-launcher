package com.hometv.launcher;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F36: the clock screensaver as the TV's own screensaver ("HomeTV Clock" in the TV's Screensaver settings, or
 * {@code settings put secure screensaver_components com.hometv.launcher/.ClockDream} over adb, as upstream's lines
 * do). The TV starts it after its own idle time, wherever it is; Home's clock (Launcher settings › Screensaver) starts
 * only from Home. Same scene and same Behind the clock choice: Theme picture is decoded once, off the main thread, from
 * the picture Home has already downloaded (nothing is fetched here). Any key wakes the TV back to where it was.
 */
public class ClockDream extends DreamService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService decode;
    private ClockScene scene;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        Config cfg = Config.load(this);
        Ui.init(this, cfg.motion);
        Theme theme = ThemeStore.current(this);
        Ui.apply(theme);
        ThemeFonts.apply(this, theme);
        scene = new ClockScene(this, MotionPrefs.load(this, cfg) == MotionChoice.OFF);
        Screensaver.Back back = Screensaver.back(this);
        scene.setBack(back);
        setContentView(scene);
        if (back == Screensaver.Back.PICTURE) {
            boolean plain = PerfPrefs.load(this, cfg).plainBackdrop;
            ClockScene s = scene;
            decode = Executors.newSingleThreadExecutor();
            decode.execute(() -> {
                Drawable d = Backdrops.still(this, theme, plain);
                main.post(() -> {
                    if (scene == s) s.setPicture(d);
                });
            });
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (scene != null) scene.start();
    }

    @Override
    public void onDreamingStopped() {
        if (scene != null) scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.stop();
        scene = null;
        if (decode != null) decode.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
