import { app } from '../core/app';
import { h } from '../core/dom';
import { settings } from '../core/settings';

/**
 * F36: the clock screensaver, back from the old launcher (upstream's ClockScene), in this launcher's type. The time and
 * date float over a slow coloured glow, the theme's picture dimmed, or black (Launcher settings › Screensaver). Every
 * minute the clock fades out, moves to a new spot and fades in, and the glow follows it and shifts colour, so nothing
 * burns in. It starts only from Home, after Home has sat untouched for the chosen time, never over an app or Live TV.
 * Any key wakes it and is swallowed, so the key that wakes it doesn't also act on Home (upstream's rule).
 *
 * On the TV it is also a system screensaver ("HomeTV Clock") a household can pick in the TV's own settings; the TV
 * then starts it after its own idle time, anywhere. The prototype only shows the Home part.
 */

const MOVE_MS = 60_000;
/** Deep, low-brightness glow colours, drawn at partial alpha on black (upstream's palette). */
const PALETTE = ['#2b5cff', '#00a39a', '#8a3ffc', '#e0661b', '#d6336c'];
/** The glow's colour change: 16 steps over 8 s, as the launcher draws it (a full-screen gradient isn't animated per frame). */
const GLOW_STEPS = 16;
const GLOW_STEP_MS = 500;

const params = new URLSearchParams(location.search);
/** ?saver=fast: the clock moves every 8 s and "5 min" means 10 s, to see it in a review without waiting. */
const fast = params.get('saver') === 'fast';
const moveMs = () => (fast ? 8_000 : MOVE_MS);

let el: HTMLElement | null = null;
let clock!: HTMLElement;
let time!: HTMLElement;
let ampm!: HTMLElement;
let date!: HTMLElement;
let glow!: HTMLElement;
let shown = false;
let moveTimer = 0;
/** The fade's halfway point in move(): cleared by hide(), so it can't move the clock in a later showing. */
let fadeTimer = 0;
let tickTimer = 0;
let glowTimers: number[] = [];
let colour = Math.floor(Math.random() * PALETTE.length);
let glowColour = PALETTE[colour];
let lastInput = Date.now();
/** Keys that woke the screensaver: their repeats and their release are swallowed too. */
const waking = new Set<string>();

function build() {
  time = h('span.saver-time');
  ampm = h('span.saver-ampm');
  date = h('div.saver-date');
  clock = h('div.saver-clock', h('div', time, ampm), date);
  glow = h('div.saver-glow');
  el = h('div.saver', glow, clock);
  el.addEventListener('click', hide);
  app.overlay(el);
}

function tick() {
  const now = new Date();
  time.textContent = now.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }).replace(/\s?[AP]M/, '');
  ampm.textContent = now.getHours() < 12 ? 'AM' : 'PM';
  date.textContent = now.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
}

/** A spot that keeps the whole clock on screen, 10-90% of the room each way, with the glow centred behind it. */
function place() {
  const stage = el!.getBoundingClientRect();
  const box = clock.getBoundingClientRect();
  const scale = app.scale || 1;
  const maxX = Math.max(0, stage.width - box.width) / scale;
  const maxY = Math.max(0, stage.height - box.height) / scale;
  const x = maxX * (0.1 + 0.8 * Math.random());
  const y = maxY * (0.1 + 0.8 * Math.random());
  clock.style.left = `${x}px`;
  clock.style.top = `${y}px`;
  const cx = x + box.width / scale / 2;
  const cy = y + box.height / scale / 2;
  glow.style.setProperty('--gx', `${cx}px`);
  glow.style.setProperty('--gy', `${cy}px`);
}

function setGlow(c: string) {
  glowColour = c;
  glow.style.setProperty('--glow', c);
}

/** Mixes two #rrggbb colours. */
function mix(a: string, b: string, f: number): string {
  const p = (s: string, i: number) => parseInt(s.slice(1 + i * 2, 3 + i * 2), 16);
  const c = [0, 1, 2].map((i) => Math.round(p(a, i) + (p(b, i) - p(a, i)) * f));
  return `#${c.map((v) => v.toString(16).padStart(2, '0')).join('')}`;
}

function shiftGlow() {
  if (settings.get().screensaverBack !== 'glow') return;
  const from = glowColour;
  colour = (colour + 1) % PALETTE.length;
  const to = PALETTE[colour];
  for (let i = 1; i <= GLOW_STEPS; i++) {
    glowTimers.push(window.setTimeout(() => setGlow(mix(from, to, i / GLOW_STEPS)), i * GLOW_STEP_MS));
  }
}

/** Fades the clock out, moves it and fades it in; with Motion off it just moves. */
function move() {
  const still = settings.get().motion === 'off';
  if (still) {
    place();
  } else {
    clock.classList.add('is-out');
    fadeTimer = window.setTimeout(() => {
      place();
      clock.classList.remove('is-out');
    }, 900);
  }
  shiftGlow();
  moveTimer = window.setTimeout(move, moveMs());
}

export function showScreensaver() {
  if (shown) return;
  if (!el) build();
  shown = true;
  el!.dataset.back = settings.get().screensaverBack;
  setGlow(glowColour);
  tick();
  tickTimer = window.setInterval(tick, 15_000);
  el!.classList.add('is-shown');
  clock.classList.add('is-out');
  requestAnimationFrame(() => {
    place();
    clock.classList.remove('is-out');
  });
  moveTimer = window.setTimeout(move, moveMs());
}

function hide() {
  if (!shown) return;
  shown = false;
  el!.classList.remove('is-shown');
  clearTimeout(moveTimer);
  clearTimeout(fadeTimer);
  clearInterval(tickTimer);
  glowTimers.forEach(clearTimeout);
  glowTimers = [];
  lastInput = Date.now();
}

/**
 * Watches for Home sitting untouched, and wakes the screensaver on any key. Keys are caught before the app sees them,
 * as the launcher's screensaver is its own window on top.
 */
export function initScreensaver() {
  window.addEventListener(
    'keydown',
    (e) => {
      lastInput = Date.now();
      if (shown || waking.has(e.key)) {
        e.preventDefault();
        e.stopImmediatePropagation();
        waking.add(e.key);
        hide();
      }
    },
    true,
  );
  window.addEventListener(
    'keyup',
    (e) => {
      if (!waking.delete(e.key)) return;
      e.preventDefault();
      e.stopImmediatePropagation();
    },
    true,
  );
  window.addEventListener('pointerdown', () => (lastInput = Date.now()), true);
  // A phone remote's button (F27) is a key too: it holds the clock off, and the one that wakes it is swallowed.
  app.beforePress = () => {
    lastInput = Date.now();
    if (!shown) return false;
    hide();
    return true;
  };
  window.setInterval(() => {
    const after = settings.get().screensaverAfter;
    if (shown || after === 'off' || document.visibilityState !== 'visible') return;
    // Only on Home itself: no popup, dropdown or dialog over it, and never over another page or an app.
    const top = app.top();
    if (top?.tab !== 'home' || (top.layer ?? 'screen') !== 'screen') return;
    const ms = Number(after) * (fast ? 2_000 : 60_000);
    if (Date.now() - lastInput >= ms) showScreensaver();
  }, 1_000);
}
