// F25 · Animated snow and rain over Home's Seasonal picture (the project lead's decisions of 2026-11-01, 23:02):
// - off unless Launcher settings › Performance › Animated details is on (off by default for every TV);
// - it falls by the real weather at the Weather place (here the mock place's description), by the season offline;
// - it moves only while Home is idle: it starts 3 s after the last key and stops on the next key, so moving around
//   Home never pays for it;
// - 12 fps for Vector art, 10 fps for 8-bit, where it snaps to the scene's 12 px pixels.
// It also needs Motion Full and Backdrop picture = Photo. The launcher's WeatherLayer follows the same rules.

import { app } from './app';
import { net } from './net';
import { settings } from './settings';
import { theme, type Period } from './theme';
import { placeById } from '../data/places';

export type Fall = 'snow' | 'rain';
export type FallChoice = 'auto' | 'off' | Fall;

const IDLE_MS = 3000;
const W = 1920;
const H = 1080;
/** Snow is only drawn in the cold periods; rain in any. */
const SNOW_PERIODS: Period[] = ['snow', 'thaw', 'autumn', 'halloween', 'christmas'];

let devChoice: FallChoice = 'auto';

/** The dev bar's Weather menu: the real weather (auto), or forced off, snow or rain. */
export function setFall(c: FallChoice) {
  devChoice = c;
  changed.forEach((fn) => fn());
}
export const fallChoice = () => devChoice;
const changed = new Set<() => void>();

/** What falls now: the real weather at the place, the season when offline, or nothing. */
export function fallNow(): Fall | null {
  if (devChoice === 'off') return null;
  if (devChoice !== 'auto') return devChoice;
  const period = theme.period();
  if (net.isOffline()) return period === 'snow' || period === 'christmas' ? 'snow' : period === 'spring-rain' ? 'rain' : null;
  const desc = placeById(settings.get().place).desc.toLowerCase();
  if (/snow/.test(desc)) return SNOW_PERIODS.includes(period) ? 'snow' : null;
  if (/rain|drizzle|shower|thunder/.test(desc)) return 'rain';
  return null;
}

/** Whether the layer may run at all, before idle: the settings, a Seasonal theme with its picture, and something to fall. */
function allowed(): boolean {
  const s = settings.get();
  return s.movingDetails && s.motion === 'full' && s.backdropPicture === 'photo' && theme.current().group === 'seasonal' &&
    fallNow() !== null;
}

/** The particles' colour for the part of the day. */
const TINT = { dawn: '243, 228, 240', day: '255, 255, 255', sunset: '255, 226, 200', night: '184, 198, 224' };

interface P {
  x: number;
  y: number;
  vy: number;
  vx: number;
  size: number;
  alpha: number;
  phase: number;
}

/** Adds the layer to Home's screen element: a canvas behind Home's content, drawn only while it runs. */
export function attachWeather(home: HTMLElement, isHomeOnTop: () => boolean) {
  const canvas = document.createElement('canvas');
  canvas.className = 'weather-fx';
  canvas.width = W;
  canvas.height = H;
  home.insertBefore(canvas, home.firstChild);
  const ctx = canvas.getContext('2d')!;
  let parts: P[] = [];
  let kind: Fall | null = null;
  let raf = 0;
  let last = 0;
  let idleTimer = 0;

  const pixel = () => theme.current().id === 'seasonal-8bit';
  const seed = (k: Fall) => {
    const n = k === 'snow' ? 80 : 70;
    parts = Array.from({ length: n }, () => {
      const layer = Math.random();
      return {
        x: Math.random() * W,
        y: Math.random() * H,
        vy: k === 'snow' ? 40 + layer * 70 : 900 + layer * 500,
        vx: k === 'snow' ? 0 : -160,
        size: k === 'snow' ? 2 + layer * 4 : 18 + layer * 22,
        alpha: k === 'snow' ? 0.35 + layer * 0.5 : 0.18 + layer * 0.17,
        phase: Math.random() * Math.PI * 2,
      };
    });
  };

  const draw = (dt: number) => {
    const rgb = TINT[theme.phase()];
    const grid = pixel();
    ctx.clearRect(0, 0, W, H);
    for (const p of parts) {
      p.phase += dt * 1.3;
      p.y += p.vy * dt;
      p.x += (p.vx + (kind === 'snow' ? Math.sin(p.phase) * 18 : 0)) * dt;
      if (p.y > H + 40) { p.y = -40; p.x = Math.random() * W; }
      if (p.x < -40) p.x += W + 80;
      if (p.x > W + 40) p.x -= W + 80;
      ctx.fillStyle = ctx.strokeStyle = `rgba(${rgb}, ${p.alpha})`;
      if (grid) {
        // 8-bit: one scene pixel (12 px) per flake, a 1×2 pixel streak per drop, on the scene's grid.
        const x = Math.round(p.x / 12) * 12, y = Math.round(p.y / 12) * 12;
        ctx.fillRect(x, y, 12, kind === 'snow' ? 12 : 24);
      } else if (kind === 'snow') {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
        ctx.fill();
      } else {
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(p.x, p.y);
        ctx.lineTo(p.x + p.vx * 0.02, p.y + p.size);
        ctx.stroke();
      }
    }
  };

  const loop = (t: number) => {
    raf = requestAnimationFrame(loop);
    const step = 1000 / (pixel() ? 10 : 12);
    if (t - last < step) return;
    const dt = last ? Math.min(0.2, (t - last) / 1000) : 0;
    last = t;
    draw(dt);
  };

  const stop = () => {
    cancelAnimationFrame(raf);
    raf = 0;
    last = 0;
    ctx.clearRect(0, 0, W, H);
  };

  const start = () => {
    if (raf || !document.body.contains(home) || !isHomeOnTop() || !allowed()) return;
    const k = fallNow()!;
    if (k !== kind || !parts.length) seed(k);
    kind = k;
    raf = requestAnimationFrame(loop);
  };

  /** Any key stops it at once; it starts again after 3 s with no key, if it may. */
  const rearm = () => {
    stop();
    clearTimeout(idleTimer);
    idleTimer = window.setTimeout(start, IDLE_MS);
  };

  window.addEventListener('keydown', rearm, true);
  settings.on(rearm);
  theme.on(rearm);
  net.on(rearm);
  app.onChange(rearm);
  changed.add(rearm);
  rearm();
}
