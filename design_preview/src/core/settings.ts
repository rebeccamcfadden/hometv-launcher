import { HOME_APPS, SE_PICK_SERVICES, SE_PICK_STORES, US_PICK_SERVICES, US_PICK_STORES, type PickService } from '../data/services';
import type { ServiceId } from '../types';
import type { ThemeId } from './theme';
import type { Moved } from './tvUtilities';

// Launcher settings that the prototype acts on, saved at once in localStorage as the TV saves them.

export type Motion = 'full' | 'reduced' | 'off';
export type OpenOnStart = 'home' | 'last' | 'live';
export type Unit = 'c' | 'f' | 'tv';
export type ThemeFont = 'theme' | 'system';
export type CardShadows = 'on' | 'focus' | 'off';
export type BackdropPicture = 'photo' | 'plain';
export type TimeOfDay = 'sun' | 'day' | 'night';
/** The TV's region (F12; the launcher's Region): whose services, stores and prices show. */
export type Region = 'se' | 'us';
/** Launcher settings › Features (F14): what the launcher runs at all. */
export type FeatureId = 'tmdb' | 'notes' | 'prices' | 'weather' | 'remote';

interface Saved {
  motion: Motion;
  openOnStart: OpenOnStart;
  /** App ids on Home's row, in order. */
  homeApps: string[];
  /** The region (F12): Sweden unless set, so no TV's default changes. */
  region: Region;
  /** Ticked streaming services (pickServices() ids) in this region: their titles count as Included. */
  services: string[];
  /** Ticked stores in this region: prices come only from these. */
  stores: ServiceId[];
  /** The other region's store ticks, kept while it isn't the TV's, as the launcher keeps them (ServiceChoices). */
  otherRegion: Partial<Record<Region, { stores: ServiceId[] }>>;
  /**
   * Two regions (Rebecca, 2026-11-04): more countries shown with the TV's own, each offer with its flag. None by
   * default, so no TV changes.
   */
  alsoRegions: Region[];
  /**
   * A ticked service kept to one country (Rebecca's pick (c), 18:28: hold OK on it): its titles count as Included
   * only there. Unset, a ticked service is Included in every country it has.
   */
  onlyIn: Record<string, Region>;
  /** The weather place (data/places.ts id) and the places picked before it, newest first. */
  place: string;
  recentPlaces: string[];
  unit: Unit;
  /** Launcher settings › Theme: the chosen theme (a Seasonal one follows the date), and whether it uses its own fonts. */
  theme: ThemeId;
  themeFont: ThemeFont;
  /** Theme › Time of day: a Seasonal picture follows the sun (dawn, day, sunset, night) or always shows the day. */
  timeOfDay: TimeOfDay;
  /** Theme › App logos (F43): Home row apps as full-colour banners, or as wordmarks on the glass tile (Figma 2:741). */
  appLogos: AppLogos;
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
  /** F35: apps moved into or out of All Apps' TV Utilities folder by hand (hold OK), by app id. */
  utilityMoves: Record<string, Moved>;
  /**
   * F36: Launcher settings › Screensaver. The clock starts after Home has sat this long without a key (never over an
   * app or Live TV); Off by default for every TV (AGENTS.md: new behaviour off).
   */
  screensaverAfter: ScreensaverAfter;
  /** What the screensaver's clock floats over: a slow coloured glow, the theme's picture dimmed, or black (OLED). */
  screensaverBack: ScreensaverBack;
}

const KEY = 'cleanyourtv-preview-settings-v2';

/** The default weather place: the build's city. */
export const DEFAULT_PLACE = 'new-york';

/** The region's lists for the Streaming services and Stores panes. */
const PICKS = {
  se: { services: SE_PICK_SERVICES, stores: SE_PICK_STORES },
  us: { services: US_PICK_SERVICES, stores: US_PICK_STORES },
};

/** A region's first ticks: the services whose app is on the TV, and every store, so prices show from the start. */
function ticksFor(r: Region): { services: string[]; stores: ServiceId[] } {
  return {
    services: PICKS[r].services.filter((s) => s.installed && !s.more).map((s) => s.id),
    stores: PICKS[r].stores.map((s) => s.id),
  };
}

const defaults = (): Saved => ({
  motion: 'full',
  openOnStart: 'home',
  homeApps: HOME_APPS.map((a) => a.id),
  region: 'se',
  ...ticksFor('se'),
  otherRegion: {},
  alsoRegions: [],
  onlyIn: {},
  place: DEFAULT_PLACE,
  recentPlaces: ['malmo'],
  unit: 'tv',
  theme: 'midnight',
  themeFont: 'theme',
  timeOfDay: 'sun',
  // F43: full colour unless a theme or the household picks Minimal (AGENTS.md: new behaviour off by default).
  appLogos: 'full',
  cardShadows: 'on',
  focusGlow: true,
  backdropPicture: 'photo',
  movingDetails: false,
  // F14 (the project lead, 2026-11-02): every feature starts on; First run switches off any whose setup isn't finished
  // when it ends (setup.ts, featureSetUp).
  // F34 (Rebecca, 2026-11-02): Phone notes starts off, since on it opens a port; it is its listening switch. Phone
  // remote (F27) likewise: off until the household turns it on.
  features: { tmdb: true, notes: false, prices: true, weather: true, remote: false },
  utilityMoves: {},
  screensaverAfter: 'off',
  screensaverBack: 'glow',
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
      if (saved.region !== 'us') saved.region = 'se';
      saved.alsoRegions = (Array.isArray(saved.alsoRegions) ? saved.alsoRegions : []).filter((r) => (r === 'se' || r === 'us') && r !== saved.region);
      if (!saved.onlyIn || typeof saved.onlyIn !== 'object') saved.onlyIn = {};
      if (saved.appLogos !== 'minimal') saved.appLogos = 'full';
      if (!['off', '5', '15', '30'].includes(saved.screensaverAfter)) saved.screensaverAfter = 'off';
      if (!['glow', 'picture', 'black'].includes(saved.screensaverBack)) saved.screensaverBack = 'glow';
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

/**
 * The TV's region's streaming services, in the Streaming services pane's order, then any ticked service the region
 * doesn't list, behind More services, as the launcher lists them (ServicePicks.listed): ticks are the household's in
 * any region.
 */
export const pickServices = (): (PickService & { regions: Region[] })[] => {
  if (data.alsoRegions.length) return mergedServices();
  const own = PICKS[data.region].services.map((s) => ({ ...s, regions: [data.region] }));
  const others = (Object.keys(PICKS) as Region[]).filter((r) => r !== data.region).flatMap((r) => PICKS[r].services);
  const extra = others.filter((s, i) => data.services.includes(s.id) && !own.some((o) => o.id === s.id) && others.findIndex((x) => x.id === s.id) === i);
  return [...own, ...extra.map((s) => ({ ...s, more: true, regions: regionsOf(s.id) }))];
};
/** The TV's region's stores for rent and buy; with more regions, every chosen region's, one row each with its flags. */
export const pickStores = (): { id: ServiceId; installed: boolean; regions: Region[] }[] => {
  if (!data.alsoRegions.length) return PICKS[data.region].stores.map((s) => ({ ...s, regions: [data.region] }));
  const out: { id: ServiceId; installed: boolean; regions: Region[] }[] = [];
  for (const r of chosenRegions())
    for (const s of PICKS[r].stores) {
      const have = out.find((x) => x.id === s.id);
      if (have) have.regions.push(r);
      else out.push({ ...s, regions: [r] });
    }
  return out;
};

/** The TV's region first, then the ones added with Also show. */
export const chosenRegions = (): Region[] => [data.region, ...data.alsoRegions.filter((r) => r !== data.region)];

/** The chosen regions a service is in (by its Streaming services id). */
const regionsOf = (id: string): Region[] => chosenRegions().filter((r) => PICKS[r].services.some((s) => s.id === id));

/**
 * Two regions: every chosen region's services, one tile each, in the TV's region's order then the others' new ones; a
 * service in several regions is one tile with all its flags (Rebecca, 17:58). More services as the TV's region has it.
 */
function mergedServices(): (PickService & { regions: Region[] })[] {
  const out: (PickService & { regions: Region[] })[] = [];
  for (const r of chosenRegions())
    for (const s of PICKS[r].services) {
      const have = out.find((x) => x.id === s.id);
      if (have) {
        have.regions.push(r);
        if (!s.more) have.more = false;
      } else out.push({ ...s, regions: [r] });
    }
  return [...out.filter((s) => !s.more), ...out.filter((s) => s.more)];
}

/** Whether a store is ticked in a region: each region keeps its own store ticks (ServiceChoices, Region.key). */
export function storeTicked(id: ServiceId, r: Region): boolean {
  if (r === data.region) return data.stores.includes(id);
  return (data.otherRegion[r]?.stores ?? ticksFor(r).stores).includes(id);
}

/** Ticks or unticks a store in every chosen region that lists it (one row in the Stores pane). */
export function setStoreTicked(id: ServiceId, on: boolean) {
  const other = { ...data.otherRegion };
  let stores = data.stores;
  for (const r of chosenRegions()) {
    if (!PICKS[r].stores.some((s) => s.id === id)) continue;
    const cur = r === data.region ? stores : (other[r]?.stores ?? ticksFor(r).stores);
    const next = on ? [...cur.filter((x) => x !== id), id] : cur.filter((x) => x !== id);
    if (r === data.region) stores = next;
    else other[r] = { stores: next };
  }
  settings.set({ stores, otherRegion: other });
}

/** Also show (Launcher settings › Region): adds or removes a country shown with the TV's own. */
export function setAlsoRegion(r: Region, on: boolean) {
  if (r === data.region) return;
  const also = data.alsoRegions.filter((x) => x !== r);
  settings.set({ alsoRegions: on ? [...also, r] : also });
  reconcileStores();
}

/**
 * One tick per store across the chosen countries (the Stores pane shows one row for it): when countries are combined,
 * a store listed in several takes the tick it has in the first of them (the TV's own when it lists the store), so the
 * row and the prices shown agree.
 */
function reconcileStores() {
  const regions = chosenRegions();
  if (regions.length < 2) return;
  const ids = new Set(regions.flatMap((r) => PICKS[r].stores.map((s) => s.id)));
  for (const id of ids) {
    const listed = regions.filter((r) => PICKS[r].stores.some((s) => s.id === id));
    if (listed.length < 2) continue;
    const on = storeTicked(id, listed[0]);
    if (listed.some((r) => storeTicked(id, r) !== on)) setStoreTicked(id, on);
  }
}

/** Hold OK on a ticked service (pick (c)): keep it to one country, or (null) every country it has. */
export function setOnlyIn(id: string, r: Region | null) {
  const onlyIn = { ...data.onlyIn };
  if (r) onlyIn[id] = r;
  else delete onlyIn[id];
  settings.set({ onlyIn });
}

/**
 * Sets the region (F12): its stores take the place of the other region's, whose ticks are kept for when the TV goes
 * back, as the launcher keeps them per region (ServiceChoices, Region.key); a region not chosen before starts with every
 * store ticked. The streaming services are the household's in any region, as in the launcher: a service the new
 * region doesn't list stays ticked, behind More services (pickServices).
 */
export function setRegion(r: Region) {
  if (r === data.region) return;
  const other = { ...data.otherRegion, [data.region]: { stores: data.stores } };
  const stores = other[r]?.stores ?? ticksFor(r).stores;
  delete other[r];
  // The new main region can't also be an extra one.
  settings.set({ region: r, stores, otherRegion: other, alsoRegions: data.alsoRegions.filter((x) => x !== r && x !== data.region).concat(data.alsoRegions.includes(r) ? [data.region] : []) });
  reconcileStores();
}

export const REGION_LABELS: Record<Region, string> = { se: 'Sweden', us: 'United States' };

export const MOTION_LABELS: Record<Motion, string> = { full: 'Full', reduced: 'Reduced', off: 'Off' };
export const START_LABELS: Record<OpenOnStart, string> = { home: 'Home', last: 'Last app', live: 'Live TV' };
/** The TV's region is Sweden in the mock, so TV default resolves to °C. */
export const TV_UNIT: 'c' | 'f' = 'c';
export const UNIT_LABELS: Record<Unit, string> = { c: '°C', f: '°F', tv: `TV default (°${TV_UNIT.toUpperCase()})` };
export const FONT_LABELS: Record<ThemeFont, string> = { theme: 'Theme font', system: 'System font' };
export const SHADOW_LABELS: Record<CardShadows, string> = { on: 'On', focus: 'Focused card only', off: 'Off' };
export const BACKDROP_LABELS: Record<BackdropPicture, string> = { photo: 'Photo', plain: 'Plain colour' };
export type AppLogos = 'full' | 'minimal';
export const APP_LOGOS_LABELS: Record<AppLogos, string> = { full: 'Full colour', minimal: 'Minimal' };
export const TIME_OF_DAY_LABELS: Record<TimeOfDay, string> = { sun: 'Follow the sun', day: 'Day', night: 'Night' };
/** F36: minutes Home sits untouched before the clock screensaver, or off. */
export type ScreensaverAfter = 'off' | '5' | '15' | '30';
export const SCREENSAVER_AFTER_LABELS: Record<ScreensaverAfter, string> = { off: 'Off', '5': '5 min', '15': '15 min', '30': '30 min' };
/** In the pane's order: Off first (numeric keys would come first in Object.keys). */
export const SCREENSAVER_AFTER: ScreensaverAfter[] = ['off', '5', '15', '30'];
export type ScreensaverBack = 'glow' | 'picture' | 'black';
export const SCREENSAVER_BACK_LABELS: Record<ScreensaverBack, string> = { glow: 'Glow', picture: 'Theme picture', black: 'Black' };

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
  {
    // F27 (the project lead, 2026-11-02): off by default; the switch is also in its own pane, Launcher settings › Phone remote.
    id: 'remote',
    label: 'Phone remote',
    sub: 'A phone on your Wi-Fi as a remote and keyboard for this launcher, and to add to My list',
    off: 'The TV doesn’t listen for phones, and paired phones stop working',
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

// Ticks saved before stores were reconciled (an earlier visit) agree again from the start.
reconcileStores();
