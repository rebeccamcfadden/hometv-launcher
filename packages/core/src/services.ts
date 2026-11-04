import type { ServiceId } from './types.ts';

export interface Service {
  id: ServiceId;
  name: string;
  /** A rent/buy store rather than a subscription. */
  store: boolean;
  /** Always included, with no subscription to tick (free YouTube). */
  free?: boolean;
  /** The app that opens it, when that isn't its own name: a store sells inside its subscription's app. */
  app?: string;
}

// Names follow current branding, as the launcher's real catalogue does (WatchProviders): Apple's subscription is
// "Apple TV" and its store the "Apple TV Store", which opens in the Apple TV app; Max is "HBO Max". Which
// subscriptions the household has, and which stores it rents from, are ticked in Launcher settings
// (core/settings.ts); data/offers.ts reads them.
export const SERVICES: Record<ServiceId, Service> = {
  netflix: { id: 'netflix', name: 'Netflix', store: false },
  prime: { id: 'prime', name: 'Prime Video', store: false },
  disney: { id: 'disney', name: 'Disney+', store: false },
  max: { id: 'max', name: 'HBO Max', store: false },
  appletvplus: { id: 'appletvplus', name: 'Apple TV', store: false },
  viaplay: { id: 'viaplay', name: 'Viaplay', store: false },
  youtube: { id: 'youtube', name: 'YouTube', store: true, free: true },
  appletv: { id: 'appletv', name: 'Apple TV Store', store: true, app: 'Apple TV' },
  viaplaystore: { id: 'viaplaystore', name: 'Viaplay Store', store: true, app: 'Viaplay' },
  sfanytime: { id: 'sfanytime', name: 'SF Anytime', store: true },
  amazonvideo: { id: 'amazonvideo', name: 'Amazon Video', store: true, app: 'Prime Video' },
  rakuten: { id: 'rakuten', name: 'Rakuten TV', store: true },
  // US stores (F12): Fandango at Home (formerly Vudu) and Google TV, which sells films in the US.
  fandango: { id: 'fandango', name: 'Fandango at Home', store: true },
  googletv: { id: 'googletv', name: 'Google TV', store: true },
};

export const appName = (id: ServiceId) => SERVICES[id].app ?? SERVICES[id].name;

/** A service in the Streaming services pane: Sweden's subscriptions and free services (WatchProviders, SE). */
export interface PickService {
  id: string;
  name: string;
  /** The wordmark on its tile, until real logos are licensed. */
  mark: string;
  weight: number;
  /** Its app is on this (mock) TV. */
  installed: boolean;
  free?: 'Free' | 'Free tier';
  /** Under "+7 More services". */
  more?: boolean;
  /** The catalogue's service, for the ones the mock catalogue has titles on. */
  catalog?: ServiceId;
}

export const SE_PICK_SERVICES: PickService[] = [
  { id: 'netflix', name: 'Netflix', mark: 'NETFLIX', weight: 700, installed: true, catalog: 'netflix' },
  { id: 'prime', name: 'Prime Video', mark: 'prime video', weight: 600, installed: true, catalog: 'prime' },
  { id: 'disney', name: 'Disney+', mark: 'Disney+', weight: 400, installed: true, catalog: 'disney' },
  { id: 'max', name: 'HBO Max', mark: 'HBO max', weight: 700, installed: true, catalog: 'max' },
  { id: 'appletvplus', name: 'Apple TV', mark: 'Apple TV', weight: 500, installed: true, catalog: 'appletvplus' },
  { id: 'viaplay', name: 'Viaplay', mark: 'viaplay', weight: 700, installed: true, catalog: 'viaplay' },
  { id: 'skyshowtime', name: 'SkyShowtime', mark: 'SkyShowtime', weight: 600, installed: false },
  { id: 'svtplay', name: 'SVT Play', mark: 'SVT Play', weight: 700, installed: true, free: 'Free' },
  { id: 'tv4play', name: 'TV4 Play', mark: 'TV4 Play', weight: 700, installed: true, free: 'Free tier' },
  { id: 'tele2play', name: 'Tele2 Play', mark: 'Tele2 Play', weight: 600, installed: false },
  { id: 'teliaplay', name: 'Telia Play', mark: 'Telia Play', weight: 600, installed: false },
  { id: 'hayu', name: 'Hayu', mark: 'hayu', weight: 700, installed: false, more: true },
  { id: 'mubi', name: 'MUBI', mark: 'MUBI', weight: 800, installed: false, more: true },
  { id: 'crunchyroll', name: 'Crunchyroll', mark: 'crunchyroll', weight: 600, installed: false, more: true },
  { id: 'britbox', name: 'BritBox', mark: 'BritBox', weight: 700, installed: false, more: true },
  { id: 'plutotv', name: 'Pluto TV', mark: 'pluto tv', weight: 700, installed: false, free: 'Free', more: true },
  { id: 'plex', name: 'Plex', mark: 'plex', weight: 700, installed: true, free: 'Free', more: true },
  { id: 'cineast', name: 'Cineast', mark: 'Cineast', weight: 600, installed: false, free: 'Free', more: true },
];

/**
 * The US's subscriptions and free services (F12; the launcher's ServicePicks.US_MAIN and US_MORE): the big
 * subscriptions, then the free ones first behind More services.
 */
export const US_PICK_SERVICES: PickService[] = [
  { id: 'netflix', name: 'Netflix', mark: 'NETFLIX', weight: 700, installed: true, catalog: 'netflix' },
  { id: 'prime', name: 'Prime Video', mark: 'prime video', weight: 600, installed: true, catalog: 'prime' },
  { id: 'disney', name: 'Disney+', mark: 'Disney+', weight: 400, installed: true, catalog: 'disney' },
  { id: 'max', name: 'HBO Max', mark: 'HBO max', weight: 700, installed: true, catalog: 'max' },
  { id: 'hulu', name: 'Hulu', mark: 'hulu', weight: 700, installed: true },
  { id: 'peacock', name: 'Peacock', mark: 'Peacock', weight: 700, installed: false, free: 'Free tier' },
  { id: 'paramountplus', name: 'Paramount+', mark: 'Paramount+', weight: 600, installed: false },
  { id: 'appletvplus', name: 'Apple TV', mark: 'Apple TV', weight: 500, installed: true, catalog: 'appletvplus' },
  { id: 'tubi', name: 'Tubi', mark: 'tubi', weight: 800, installed: false, free: 'Free', more: true },
  { id: 'plutotv', name: 'Pluto TV', mark: 'pluto tv', weight: 700, installed: false, free: 'Free', more: true },
  { id: 'rokuchannel', name: 'The Roku Channel', mark: 'Roku Channel', weight: 700, installed: false, free: 'Free', more: true },
  { id: 'plex', name: 'Plex', mark: 'plex', weight: 700, installed: true, free: 'Free', more: true },
  { id: 'crunchyroll', name: 'Crunchyroll', mark: 'crunchyroll', weight: 600, installed: false, more: true },
  { id: 'mubi', name: 'MUBI', mark: 'MUBI', weight: 800, installed: false, more: true },
  { id: 'britbox', name: 'BritBox', mark: 'BritBox', weight: 700, installed: false, more: true },
];

/** The stores for rent and buy in Sweden, in the Stores pane's order, with whether their app is on the TV. */
export const SE_PICK_STORES: { id: ServiceId; installed: boolean }[] = [
  { id: 'appletv', installed: true },
  { id: 'amazonvideo', installed: true },
  { id: 'viaplaystore', installed: true },
  { id: 'sfanytime', installed: false },
  { id: 'rakuten', installed: false },
];

/** The US's stores (F12; ServicePicks.US_STORES). Google TV sells films there. */
export const US_PICK_STORES: { id: ServiceId; installed: boolean }[] = [
  { id: 'appletv', installed: true },
  { id: 'amazonvideo', installed: true },
  { id: 'fandango', installed: false },
  { id: 'googletv', installed: true },
];

/**
 * Which price source priced each store, for the caption under Where to watch. Mock: in the launcher only Apple's
 * iTunes lookup works without a key, and Watchmode or Streaming Availability price the rest when keys are set.
 */
export const PRICE_SOURCE: Partial<Record<ServiceId, string>> = { appletv: 'Apple' };
export const DEFAULT_PRICE_SOURCE = 'Watchmode';

export interface HomeApp {
  id: string;
  name: string;
  mark: string;
  weight: number;
}

/** Apps on the Home row, by default, in order. */
export const HOME_APPS: HomeApp[] = [
  { id: 'netflix', name: 'Netflix', mark: 'NETFLIX', weight: 700 },
  { id: 'youtube', name: 'YouTube', mark: '▶ YouTube', weight: 600 },
  { id: 'prime', name: 'Prime Video', mark: 'prime video', weight: 600 },
  { id: 'disney', name: 'Disney+', mark: 'Disney+', weight: 400 },
  { id: 'max', name: 'HBO Max', mark: 'HBO max', weight: 700 },
  { id: 'appletvplus', name: 'Apple TV', mark: 'Apple TV', weight: 400 },
  { id: 'spotify', name: 'Spotify', mark: 'Spotify', weight: 600 },
];

/** Other apps installed on the (mock) TV, off the Home row until Launcher settings puts them on it. */
export const OTHER_APPS: HomeApp[] = [
  { id: 'svtplay', name: 'SVT Play', mark: 'SVT Play', weight: 700 },
  { id: 'tv4play', name: 'TV4 Play', mark: 'TV4 Play', weight: 700 },
  { id: 'viaplay', name: 'Viaplay', mark: 'viaplay', weight: 600 },
];

/**
 * Full colour (F43): a stand-in for each app's own banner, which the TV draws and the prototype has no licence for:
 * the brand's tile colour and mark colour only. Minimal shows every app as the Figma Home frames do (off-white on glass).
 */
export const APP_BRAND: Record<string, { bg: string; fg: string }> = {
  netflix: { bg: '#000000', fg: '#e50914' },
  youtube: { bg: '#ffffff', fg: '#ff0000' },
  prime: { bg: '#00a8e1', fg: '#ffffff' },
  disney: { bg: 'linear-gradient(135deg, #0b1f4a, #0f6e7a)', fg: '#ffffff' },
  max: { bg: 'linear-gradient(135deg, #1b1f3b, #2a1b5e)', fg: '#ffffff' },
  appletvplus: { bg: '#1c1c1e', fg: '#ffffff' },
  spotify: { bg: '#000000', fg: '#1ed760' },
  svtplay: { bg: '#003a2b', fg: '#ffffff' },
  tv4play: { bg: '#e2002b', fg: '#ffffff' },
  viaplay: { bg: '#170a3a', fg: '#ffffff' },
};

/** Home's row holds at most this many tiles. */
export const HOME_ROW_MAX = 7;
