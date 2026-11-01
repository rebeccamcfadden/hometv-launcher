import { HOME_APPS, PICK_SERVICES, PICK_STORES } from '../data/services';
import type { ServiceId } from '../types';
import type { ThemeId } from './theme';

// Launcher settings that the prototype acts on, saved at once in localStorage as the TV saves them.

export type Motion = 'full' | 'reduced' | 'off';
export type OpenOnStart = 'home' | 'last' | 'live';
export type Unit = 'c' | 'f' | 'tv';
export type ThemeFont = 'theme' | 'system';
export type CardShadows = 'on' | 'focus' | 'off';
export type BackdropPicture = 'photo' | 'plain';

interface Saved {
  motion: Motion;
  openOnStart: OpenOnStart;
  /** App ids on Home's row, in order. */
  homeApps: string[];
  /** Ticked streaming services (PICK_SERVICES ids): their titles count as Included. */
  services: string[];
  /** Ticked stores: prices come only from these. */
  stores: ServiceId[];
  /** The weather place (data/places.ts id) and the places picked before it, newest first. */
  place: string;
  recentPlaces: string[];
  unit: Unit;
  /** Launcher settings › Theme: the chosen theme (a Seasonal one follows the date), and whether it uses its own fonts. */
  theme: ThemeId;
  themeFont: ThemeFont;
  /**
   * Launcher settings › Performance (with Motion): card shadows, the focus glow, the backdrop picture and the animated
   * details (petals, snow and leaves drifting over Seasonal backdrops; saved only, nothing moves yet, on the TV either).
   * The prototype draws no card shadows or glow, so those two are saved only; the backdrop applies.
   */
  cardShadows: CardShadows;
  focusGlow: boolean;
  backdropPicture: BackdropPicture;
  movingDetails: boolean;
}

const KEY = 'cleanyourtv-preview-settings-v2';

/** The default weather place: the build's city. */
export const DEFAULT_PLACE = 'new-york';

const defaults = (): Saved => ({
  motion: 'full',
  openOnStart: 'home',
  homeApps: HOME_APPS.map((a) => a.id),
  // The services whose app is on the TV start ticked, and every store, so prices show from the first start.
  services: PICK_SERVICES.filter((s) => s.installed && !s.more).map((s) => s.id),
  stores: PICK_STORES.map((s) => s.id),
  place: DEFAULT_PLACE,
  recentPlaces: ['malmo'],
  unit: 'tv',
  theme: 'midnight',
  themeFont: 'theme',
  cardShadows: 'on',
  focusGlow: true,
  backdropPicture: 'photo',
  movingDetails: true,
});

function load(): Saved {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...defaults(), ...(JSON.parse(raw) as Partial<Saved>) };
  } catch {
    // Corrupt or blocked storage: start fresh.
  }
  return defaults();
}

const data = load();
const listeners = new Set<() => void>();

/** Blocked or full storage keeps the change for this visit only. */
function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(data));
  } catch {
    // Nothing to tell: the prototype has no settings that must outlive the visit.
  }
}

export const settings = {
  get: (): Readonly<Saved> => data,
  set(patch: Partial<Saved>) {
    Object.assign(data, patch);
    save();
    listeners.forEach((fn) => fn());
  },
  /** Calls `fn` on every change; returns a function that stops it. */
  on(fn: () => void): () => void {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  /** Forgets every choice and repaints with the defaults; the dev bar's Reset state calls it. */
  clear() {
    for (const k of Object.keys(data) as (keyof Saved)[]) delete data[k];
    Object.assign(data, defaults());
    try {
      localStorage.removeItem(KEY);
    } catch {
      // Blocked storage: the defaults still apply for this visit.
    }
    listeners.forEach((fn) => fn());
  },
};

export const MOTION_LABELS: Record<Motion, string> = { full: 'Full', reduced: 'Reduced', off: 'Off' };
export const START_LABELS: Record<OpenOnStart, string> = { home: 'Home', last: 'Last app', live: 'Live TV' };
/** The TV's region is Sweden in the mock, so TV default resolves to °C. */
export const TV_UNIT: 'c' | 'f' = 'c';
export const UNIT_LABELS: Record<Unit, string> = { c: '°C', f: '°F', tv: `TV default (°${TV_UNIT.toUpperCase()})` };
export const FONT_LABELS: Record<ThemeFont, string> = { theme: 'Theme font', system: 'System font' };
export const SHADOW_LABELS: Record<CardShadows, string> = { on: 'On', focus: 'Focused card only', off: 'Off' };
export const BACKDROP_LABELS: Record<BackdropPicture, string> = { photo: 'Photo', plain: 'Plain colour' };

/** A temperature kept in °C, shown in the chosen unit: "14°". */
export function deg(c: number): string {
  const u = data.unit === 'tv' ? TV_UNIT : data.unit;
  return `${Math.round(u === 'f' ? (c * 9) / 5 + 32 : c)}°`;
}
export const unitSuffix = () => `°${(data.unit === 'tv' ? TV_UNIT : data.unit).toUpperCase()}`;
