package com.hometv.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * F36: the clock screensaver's scene (design_preview/src/ui/screensaver.ts; upstream's ClockScene in this launcher's
 * type). The time with AM/PM and the date under it, on black; behind it a slow colour glow, the theme's picture
 * darkened, or nothing ({@link Screensaver.Back}). Every minute the clock fades out, moves to a new spot and fades in,
 * and the glow follows it and shifts colour, so nothing burns in; with Motion Off it just moves. Nothing else runs:
 * one post a minute for the move and the time, and sixteen for the glow's colour change.
 *
 * <p>MainActivity lays it over Home ({@link #setPicture} gets a copy of Home's backdrop, sharing its bitmap);
 * {@link ClockDream} decodes the picture for it.
 */
final class ClockScene extends FrameLayout {
    /** Home's clock face: Roboto Light, or the theme's display face. */
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View glowView;
    private final GradientDrawable glow = new GradientDrawable();
    private final LinearLayout clock;
    private final TextView time, ampm, date;
    private final boolean still;
    private Screensaver.Back back = Screensaver.Back.GLOW;
    private int colour;
    private int glowColour;
    private boolean running;

    private final Runnable move = this::move;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, Screensaver.toNextMinute(System.currentTimeMillis()));
        }
    };

    /** With fades (Motion not Off). */
    ClockScene(Context ctx) {
        this(ctx, false);
    }

    /** still: Motion Off, so the clock jumps to its new spot instead of fading. */
    ClockScene(Context ctx, boolean still) {
        super(ctx);
        this.still = still;
        setBackgroundColor(0xFF000000);
        setClickable(false);
        setFocusable(false);
        glowView = new View(ctx);
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowView.setBackground(glow);
        addView(glowView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        glowView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius(Math.max(1, (r - l) * 0.55f)));

        clock = new LinearLayout(ctx);
        clock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        time = Ui.text(ctx, 150, Ui.TEXT_1, Ui.display(LIGHT));
        time.setSingleLine(true);
        row.addView(time);
        ampm = Ui.text(ctx, 44, Ui.TEXT_2, Ui.REGULAR);
        ampm.setSingleLine(true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = Ui.px(20);
        alp.bottomMargin = Ui.px(14);
        row.addView(ampm, alp);
        clock.addView(row);
        date = Ui.text(ctx, 34, Ui.TEXT_2, Ui.REGULAR);
        date.setSingleLine(true);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.px(16);
        clock.addView(date, dlp);
        addView(clock, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        colour = random.nextInt(Screensaver.PALETTE.length);
        setGlow(Screensaver.PALETTE[colour]);
    }

    /** What is behind the clock; Theme picture shows black until {@link #setPicture} gives the picture. */
    void setBack(Screensaver.Back b) {
        back = b;
        glowView.setVisibility(b == Screensaver.Back.GLOW ? VISIBLE : GONE);
        setBackgroundColor(0xFF000000);
    }

    /**
     * Theme picture: the theme's picture as Home shows it, darkened so the clock reads; null keeps black. Over Home
     * it is a copy of Home's own backdrop (the same bitmap); in ClockDream, one decoded for it. Main thread.
     */
    void setPicture(Drawable picture) {
        if (back != Screensaver.Back.PICTURE || picture == null) return;
        setBackground(new LayerDrawable(new Drawable[]{picture, new ColorDrawable(Screensaver.PICTURE_SHADE << 24)}));
    }

    boolean isRunning() {
        return running;
    }

    void start() {
        if (running) return;
        running = true;
        handler.removeCallbacksAndMessages(null);
        // The text first, so the layout before the first placement measures the real clock.
        tick();
        handler.postDelayed(tick, Screensaver.toNextMinute(System.currentTimeMillis()));
        clock.setAlpha(still ? 1f : 0f);
        // Placed once laid out, so the clock's size is known.
        post(() -> {
            if (!running) return;
            place();
            if (!still) clock.animate().alpha(1f).setDuration(Screensaver.FADE_IN_MS).start();
        });
        handler.postDelayed(move, Screensaver.MOVE_MS);
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        clock.animate().cancel();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean plausible = HomeClock.plausible(now);
        boolean is24 = DateFormat.is24HourFormat(getContext());
        clock.setVisibility(plausible ? VISIBLE : INVISIBLE);
        ampm.setVisibility(is24 ? GONE : VISIBLE);
        if (!plausible) return;
        Locale locale = Locale.getDefault();
        Date d = new Date(now);
        time.setText(new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", locale).format(d));
        ampm.setText(new SimpleDateFormat("a", locale).format(d));
        date.setText(DateUtils.formatDateTime(getContext(), now,
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR));
    }

    private void move() {
        if (!running) return;
        if (still) {
            place();
        } else {
            clock.animate().alpha(0f).setDuration(Screensaver.FADE_OUT_MS).withEndAction(() -> {
                if (!running) return;
                place();
                clock.animate().alpha(1f).setDuration(Screensaver.FADE_IN_MS).start();
            }).start();
        }
        shiftGlow();
        handler.postDelayed(move, Screensaver.MOVE_MS);
    }

    /** A new spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void place() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int x = Screensaver.place(random.nextFloat(), w - clock.getWidth());
        int y = Screensaver.place(random.nextFloat(), h - clock.getHeight());
        clock.setTranslationX(x);
        clock.setTranslationY(y);
        glow.setGradientCenter((x + clock.getWidth() / 2f) / w, (y + clock.getHeight() / 2f) / h);
    }

    private void shiftGlow() {
        if (back != Screensaver.Back.GLOW) return;
        int from = glowColour;
        colour = (colour + 1) % Screensaver.PALETTE.length;
        int to = Screensaver.PALETTE[colour];
        for (int i = 1; i <= Screensaver.GLOW_STEPS; i++) {
            float f = (float) i / Screensaver.GLOW_STEPS;
            handler.postDelayed(() -> setGlow(Screensaver.mix(from, to, f)), i * Screensaver.GLOW_STEP_MS);
        }
    }

    private void setGlow(int c) {
        glowColour = c;
        glow.setColors(Screensaver.glowStops(c));
    }
}
