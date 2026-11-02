package com.hometv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AnimationUtils;

import java.time.LocalDate;
import java.util.Random;

/**
 * F25 · snow or rain over a Seasonal picture (the project lead's decisions of 2026-11-01): it sits between the backdrop
 * and the pages, so Home's shade and text stay on top of it.
 *
 * <p>On the TCL every frame redraws the whole window, so it moves only while Home is idle: it starts 3 s after the
 * last key and stops on the next one, before that key is handled, so moving around Home never pays for it. It runs
 * only while Home is on screen, with Performance › Animated details on (off by default for every TV), motion at Full
 * and the backdrop picture on, in a Seasonal theme, when {@link WeatherFall} says something falls. Vector art runs at
 * 12 fps; 8-bit at 10 fps, one scene pixel per flake. Nothing is allocated per frame.
 */
final class WeatherLayer extends View {
    static final long IDLE_MS = 3000;
    private static final int SNOW = 80, RAIN = 70;
    /** About half a vsync: a frame posted this early lands on the vsync it is due at. */
    private static final long VSYNC_SLACK_MS = 8;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Runnable start = this::start;
    private final Runnable frame = this::frame;

    private boolean homeVisible;
    private boolean onTop = true;
    /** The conditions now at the Weather place, or null to go by the season (offline, or no forecast for now). */
    private Weather.Condition condition;
    /** What falls instead, for the TV bench (MainActivity's FALL_PREVIEW); null to follow the weather. Until restart. */
    private static WeatherFall.Fall preview;
    private boolean running;
    private boolean pixel;
    private WeatherFall.Fall fall = WeatherFall.Fall.NONE;
    private long last;
    private int tint = tint(DayPhase.DAY);
    private float[] x, y, vx, vy, size, alpha, sway;

    WeatherLayer(Context ctx) {
        super(ctx);
        setWillNotDraw(false);
        setFocusable(false);
    }

    /** Home came on screen or left it. */
    void setHomeVisible(boolean on) {
        homeVisible = on;
        rearm();
    }

    /** The weather Home shows changed; null when it has none to go by. A change in what falls starts or stops it. */
    void setCondition(Weather.Condition c) {
        SeasonCalendar.Period period = SeasonCalendar.at(LocalDate.now());
        boolean differs = WeatherFall.changes(condition, c, period);
        condition = c;
        if (differs) rearm();
    }

    /** Whether Home is the top screen, with no dropdown, popup, dialog or page over it (Shell's onShown). */
    void setOnTop(boolean on) {
        if (on == onTop) return;
        onTop = on;
        rearm();
    }

    /** The part of the day the backdrop shows (Backdrops): the particles take its tint, even mid-fall. */
    void setPhase(DayPhase phase) {
        tint = tint(phase);
    }

    /** The test-only force (MainActivity's FALL_PREVIEW, WeatherFall.preview): what falls instead of the weather. */
    void setPreview(WeatherFall.Fall f) {
        if (f == preview) return;
        preview = f;
        rearm();
    }

    /** A key: stop at once, and start again after {@link #IDLE_MS} with no key. */
    void onKey() {
        rearm();
    }

    /** Lets go of the particles (memory pressure); they are made again when it next starts. */
    void release() {
        stop();
        x = null;
    }

    private void rearm() {
        stop();
        removeCallbacks(start);
        if (homeVisible && onTop && hasWindowFocus()) postDelayed(start, IDLE_MS);
    }

    /** A translucent panel over Home (Quick settings, the TV's settings, the input picker) takes the keys: stop under it. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        rearm();
    }

    private static boolean allowed() {
        PerfChoices p = PerfChoices.current();
        return p.details && !p.plainBackdrop && !Ui.reducedMotion() && Ui.theme().group == Theme.Group.SEASONAL;
    }

    private void start() {
        if (running || !homeVisible || !onTop || !isAttachedToWindow() || !hasWindowFocus() || !allowed()) return;
        WeatherFall.Fall f = preview != null ? preview : WeatherFall.of(condition, SeasonCalendar.at(LocalDate.now()));
        if (f == WeatherFall.Fall.NONE) return;
        boolean px = Ui.theme().backdrop.startsWith("pixel:");
        if (x == null || f != fall || px != pixel) seed(f, px);
        fall = f;
        pixel = px;
        running = true;
        last = AnimationUtils.currentAnimationTimeMillis();
        postOnAnimation(frame);
    }

    private void stop() {
        if (!running) return;
        running = false;
        removeCallbacks(frame);
        invalidate();
    }

    private void seed(WeatherFall.Fall f, boolean px) {
        int n = f == WeatherFall.Fall.SNOW ? SNOW : RAIN;
        x = new float[n];
        y = new float[n];
        vx = new float[n];
        vy = new float[n];
        size = new float[n];
        alpha = new float[n];
        sway = new float[n];
        float w = Math.max(1, getWidth()), h = Math.max(1, getHeight()), k = h / 1080f;
        for (int i = 0; i < n; i++) {
            float layer = random.nextFloat();
            x[i] = random.nextFloat() * w;
            y[i] = random.nextFloat() * h;
            sway[i] = random.nextFloat() * 6.283f;
            if (f == WeatherFall.Fall.SNOW) {
                vy[i] = (40 + layer * 70) * k;
                vx[i] = 0;
                size[i] = (2 + layer * 4) * k;
                alpha[i] = 0.35f + layer * 0.5f;
            } else {
                vy[i] = (900 + layer * 500) * k;
                vx[i] = -160 * k;
                size[i] = (18 + layer * 22) * k;
                alpha[i] = 0.18f + layer * 0.17f;
            }
        }
    }

    /** The particles' colour for the part of the day, as in the prototype. */
    static int tint(DayPhase phase) {
        switch (phase) {
            case DAWN:
                return 0xF3E4F0;
            case SUNSET:
                return 0xFFE2C8;
            case NIGHT:
                return 0xB8C6E0;
            default:
                return 0xFFFFFF;
        }
    }

    private void frame() {
        if (!running) return;
        // The vsync this frame belongs to (it runs as an animation callback), so the next one is timed from it.
        long vsync = AnimationUtils.currentAnimationTimeMillis();
        float dt = Math.min(0.2f, (vsync - last) / 1000f);
        last = vsync;
        float w = getWidth(), h = getHeight(), margin = 40 * h / 1080f;
        boolean snow = fall == WeatherFall.Fall.SNOW;
        for (int i = 0; i < x.length; i++) {
            sway[i] += dt * 1.3f;
            y[i] += vy[i] * dt;
            x[i] += (vx[i] + (snow ? (float) Math.sin(sway[i]) * 18 * h / 1080f : 0)) * dt;
            if (y[i] > h + margin) {
                y[i] = -margin;
                x[i] = random.nextFloat() * w;
            }
            if (x[i] < -margin) x[i] += w + 2 * margin;
            if (x[i] > w + margin) x[i] -= w + 2 * margin;
        }
        invalidate();
        // Posted half a vsync before the next one due, so it lands on it: a plain delay waited a vsync more (15.7 fps
        // for 20 on the TCL), and a running due time drifted against the vsyncs (16.8 for 15). 12 fps for Vector art,
        // 10 for 8-bit (the project lead, 2026-11-02): on the TCL the cost follows the frame rate, about 0.7 % of a
        // core per fps.
        long interval = pixel ? 100 : 83;
        postOnAnimationDelayed(frame, Math.max(0, vsync + interval - SystemClock.uptimeMillis() - VSYNC_SLACK_MS));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running || x == null) return;
        boolean snow = fall == WeatherFall.Fall.SNOW;
        float cell = getHeight() / 90f;
        paint.setStrokeWidth(2 * getHeight() / 1080f);
        for (int i = 0; i < x.length; i++) {
            paint.setColor(((int) (alpha[i] * 255) << 24) | tint);
            if (pixel) {
                // 8-bit: one scene pixel per flake, two tall per drop, on the scene's grid.
                float gx = Math.round(x[i] / cell) * cell, gy = Math.round(y[i] / cell) * cell;
                canvas.drawRect(gx, gy, gx + cell, gy + (snow ? cell : 2 * cell), paint);
            } else if (snow) {
                canvas.drawCircle(x[i], y[i], size[i], paint);
            } else {
                canvas.drawLine(x[i], y[i], x[i] + vx[i] * 0.02f, y[i] + size[i], paint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        removeCallbacks(start);
        super.onDetachedFromWindow();
    }
}
