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
export type TimeOfDay = 'sun' | 'day' | 'night';
/** Launcher settings › Features (F14): what the launcher runs at all. */
export type FeatureId = 'tmdb' | 'notes' | 'prices' | 'weather';

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
  /** Theme › Time of day: a Seasonal picture follows the sun (dawn, day, sunset, night) or always shows the day. */
  timeOfDay: TimeOfDay;
  /**
   * Launcher settings › Performance (with Motion): card shadows, the focus glow, the backdrop picture and the animated
   * details (F25, F31: snow, rain or leaves over Seasonal pictures while Home is idle, core/weatherfx; off by default for every TV).
   * The prototype draws no card shadows or glow, so those two are saved only; the backdrop applies.
   */
  cardShadows: CardShadows;
  focusGlow: boolean;
  backdropPicture: BackdropPicture;
  movingDetails: boolean;
  /** Launcher settings › Features (F14): each feature on or off; off, it never starts. */
  features: Record<FeatureId, boolean>;
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
  timeOfDay: 'sun',
  cardShadows: 'on',
  focusGlow: true,
  backdropPicture: 'photo',
  movingDetails: false,
  // F14 (the project lead, 2026-11-02): every feature starts on; First run switches off any whose setup isn't finished
  // when it ends (setup.ts, featureSetUp).
  features: { tmdb: true, notes: true, prices: true, weather: true },
});

function load(): Saved {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const raw2 = JSON.parse(raw) as Partial<Saved>;
      const d = defaults();
      const features = { ...d.features };
      for (const id of Object.keys(features) as FeatureId[]) if (typeof raw2.features?.[id] === 'boolean') features[id] = raw2.features[id];
      const saved = { ...d, ...raw2, features };
      // Seasonal · Nature was removed (2026-11-01): a TV that had it shows Vector art, as the launcher's ThemeStore does.
      if ((saved.theme as string) === 'seasonal-nature') saved.theme = 'seasonal-vector';
      // Time of day (F29): anything but Day or Night, an older or odd value, follows the sun, as ThemeStore reads it.
      if (saved.timeOfDay !== 'day' && saved.timeOfDay !== 'night') saved.timeOfDay = 'sun';
      return saved;
    }
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
export const TIME_OF_DAY_LABELS: Record<TimeOfDay, string> = { sun: 'Follow the sun', day: 'Day', night: 'Night' };

/**
 * Launcher settings › Features (F14, the project lead 2026-11-01; four since her review of 2026-11-02: Pick for me goes\n * with TMDB account and the lists, Themes are always there): one build, with each feature switched on or off at
 * run time; a feature switched off never starts (no network calls, no pages, no settings section). The TV build reads
 * the same switches from household.json and a pushed config.json (`features`), as a family install sets them. A
 * feature that needs another is off while that one is.
 */
export const FEATURES: { id: FeatureId; label: string; sub: string; off: string; needs?: FeatureId }[] = [
  {
    id: 'tmdb',
    label: 'TMDB account',
    sub: 'Movies, TV Shows and Search, your TMDB lists, and the Account section',
    off: 'Movies, TV Shows and Search say they need a TMDB account',
  },
  {
    id: 'notes',
    label: 'Phone notes',
    sub: 'Send a watch list from a phone to the TV',
    off: 'The TV doesn’t listen for notes, and no import pill shows',
    needs: 'tmdb',
  },
  {
    // The project lead, 2026-11-02: "Price lookup" (was Prices). Off, the Stores and price keys pages say so.
    id: 'prices',
    label: 'Price lookup',
    sub: 'Rent and buy prices for the stores you tick, from Watchmode and Movie of the Night',
    off: 'Rent and Buy still show where, without a price; Stores and the price keys say it is off',
  },
  {
    id: 'weather',
    label: 'Weather',
    sub: 'Today, tomorrow and the hourly strip on Home, from Open-Meteo',
    off: 'No weather on Home or in quick settings',
  },
];

/** Whether a feature runs: switched on, and so is the one it needs. */
export function featureOn(id: FeatureId): boolean {
  const f = FEATURES.find((x) => x.id === id)!;
  return data.features[id] && (!f.needs || featureOn(f.needs));
}

export function setFeature(id: FeatureId, on: boolean) {
  settings.set({ features: { ...data.features, [id]: on } });
}

/** A temperature kept in °C, shown in the chosen unit: "14°". */
export function deg(c: number): string {
  const u = data.unit === 'tv' ? TV_UNIT : data.unit;
  return `${Math.round(u === 'f' ? (c * 9) / 5 + 32 : c)}°`;
}
export const unitSuffix = () => `°${(data.unit === 'tv' ? TV_UNIT : data.unit).toUpperCase()}`;
