// F25 · Animated snow and rain over Home's Seasonal picture (the project lead's decisions of 2026-11-01, 23:02):
// - off unless Launcher settings › Performance › Animated details is on (off by default for every TV);
// - it falls by the real weather at the Weather place (here the mock place's description), by the season offline;
// - it moves only while Home is idle: it starts 3 s after the last key and stops on the next key, so moving around
//   Home never pays for it;
// - 12 fps for Vector art, 10 fps for 8-bit, where it snaps to the scene's 12 px pixels.
// F31: leaves fall in Autumn and Halloween while it's dry: rain or snow at the place wins, and offline they fall.
// It also needs Motion Full and Backdrop picture = Photo. The launcher's WeatherLayer follows the same rules.

import { app } from './app';
import { net } from './net';
import { settings } from './settings';
import { theme, type Period } from './theme';
import { placeById } from '../data/places';

export type Fall = 'snow' | 'rain' | 'leaves';
export type FallChoice = 'auto' | 'off' | Fall;

const IDLE_MS = 3000;
const W = 1920;
const H = 1080;
/** Snow is only drawn in the cold periods; rain in any. */
const SNOW_PERIODS: Period[] = ['snow', 'thaw', 'autumn', 'halloween', 'christmas'];
/** Leaves fall in these periods whenever nothing else does (F31). */
const LEAF_PERIODS: Period[] = ['autumn', 'halloween'];

let devChoice: FallChoice = 'auto';

/** The dev bar's Weather menu: the real weather (auto), or forced off, snow, rain or leaves. */
export function setFall(c: FallChoice) {
  devChoice = c;
  changed.forEach((fn) => fn());
}
export const fallChoice = () => devChoice;
const changed = new Set<() => void>();

/** What falls now: the real weather at the place, the season when offline, leaves in autumn when dry, or nothing. */
export function fallNow(): Fall | null {
  if (devChoice === 'off') return null;
  if (devChoice !== 'auto') return devChoice;
  const period = theme.period();
  const dry = LEAF_PERIODS.includes(period) ? 'leaves' : null;
  if (net.isOffline()) return period === 'snow' || period === 'christmas' ? 'snow' : period === 'spring-rain' ? 'rain' : dry;
  const desc = placeById(settings.get().place).desc.toLowerCase();
  if (/snow/.test(desc)) return SNOW_PERIODS.includes(period) ? 'snow' : dry;
  if (/rain|drizzle|shower|thunder/.test(desc)) return 'rain';
  return dry;
}

/** Whether the layer may run at all, before idle: the settings, a Seasonal theme with its picture, and something to fall. */
function allowed(): boolean {
  const s = settings.get();
  return s.movingDetails && s.motion === 'full' && s.backdropPicture === 'photo' && theme.current().group === 'seasonal' &&
    fallNow() !== null;
}

/** The particles' colour for the part of the day. */
const TINT = { dawn: '243, 228, 240', day: '255, 255, 255', sunset: '255, 226, 200', night: '184, 198, 224' };
/** Leaves: rust, amber, brick and ochre, darkened for the part of the day (they are lit, not glowing). */
const LEAF = [[200, 85, 28], [224, 138, 44], [156, 59, 30], [217, 164, 65]];
const LEAF_LIGHT = { dawn: 0.8, day: 1, sunset: 0.85, night: 0.42 };

interface P {
  x: number;
  y: number;
  vy: number;
  vx: number;
  size: number;
  alpha: number;
  phase: number;
  /** Leaves: their colour, angle and spin. */
  color: number;
  angle: number;
  spin: number;
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
    const n = k === 'snow' ? 80 : k === 'rain' ? 70 : 26;
    parts = Array.from({ length: n }, (_, i) => {
      const layer = Math.random();
      const leaf = k === 'leaves';
      return {
        x: Math.random() * W,
        y: Math.random() * H,
        vy: k === 'snow' ? 40 + layer * 70 : leaf ? 55 + layer * 65 : 900 + layer * 500,
        vx: k === 'rain' ? -160 : leaf ? -30 + Math.random() * 20 : 0,
        size: k === 'snow' ? 2 + layer * 4 : leaf ? 9 + layer * 9 : 18 + layer * 22,
        alpha: k === 'snow' ? 0.35 + layer * 0.5 : leaf ? 0.75 + layer * 0.25 : 0.18 + layer * 0.17,
        phase: Math.random() * Math.PI * 2,
        color: i % LEAF.length,
        angle: Math.random() * Math.PI * 2,
        spin: (Math.random() < 0.5 ? -1 : 1) * (1 + Math.random() * 2),
      };
    });
  };

  const draw = (dt: number) => {
    const rgb = TINT[theme.phase()];
    const light = LEAF_LIGHT[theme.phase()];
    const grid = pixel();
    const leaves = kind === 'leaves';
    ctx.clearRect(0, 0, W, H);
    for (const p of parts) {
      p.phase += dt * (leaves ? 1.1 : 1.3);
      p.angle += p.spin * dt;
      p.y += p.vy * dt;
      p.x += (p.vx + (kind === 'snow' ? Math.sin(p.phase) * 18 : leaves ? Math.sin(p.phase) * 60 : 0)) * dt;
      if (p.y > H + 40) { p.y = -40; p.x = Math.random() * W; }
      if (p.x < -40) p.x += W + 80;
      if (p.x > W + 40) p.x -= W + 80;
      if (leaves) {
        const c = LEAF[p.color];
        ctx.fillStyle = `rgba(${Math.round(c[0] * light)}, ${Math.round(c[1] * light)}, ${Math.round(c[2] * light)}, ${p.alpha})`;
      } else {
        ctx.fillStyle = ctx.strokeStyle = `rgba(${rgb}, ${p.alpha})`;
      }
      if (grid) {
        // 8-bit: one scene pixel (12 px) per flake, a 1×2 pixel streak per drop, on the scene's grid; a leaf is two
        // pixels that turn over as it tumbles.
        const x = Math.round(p.x / 12) * 12, y = Math.round(p.y / 12) * 12;
        if (leaves) ctx.fillRect(x, y, Math.cos(p.angle) > 0 ? 24 : 12, Math.cos(p.angle) > 0 ? 12 : 24);
        else ctx.fillRect(x, y, 12, kind === 'snow' ? 12 : 24);
      } else if (leaves) {
        // A leaf: an oval that tumbles (its width follows the turn, so it seems to flip over).
        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.rotate(p.angle);
        ctx.beginPath();
        ctx.ellipse(0, 0, p.size, p.size * (0.25 + 0.2 * Math.abs(Math.cos(p.phase * 1.7))), 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
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
