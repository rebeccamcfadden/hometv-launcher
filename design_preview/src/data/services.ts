import type { ServiceId } from '../types';

export interface Service {
  id: ServiceId;
  name: string;
  /** The household pays for it, so `sub` offers count as included. */
  subscribed: boolean;
  /** A rent/buy store rather than a subscription. */
  store: boolean;
}

export const SERVICES: Record<ServiceId, Service> = {
  netflix: { id: 'netflix', name: 'Netflix', subscribed: true, store: false },
  prime: { id: 'prime', name: 'Prime Video', subscribed: true, store: false },
  disney: { id: 'disney', name: 'Disney+', subscribed: true, store: false },
  max: { id: 'max', name: 'Max', subscribed: true, store: false },
  appletvplus: { id: 'appletvplus', name: 'Apple TV+', subscribed: true, store: false },
  hulu: { id: 'hulu', name: 'Hulu', subscribed: true, store: false },
  youtube: { id: 'youtube', name: 'YouTube', subscribed: true, store: true },
  appletv: { id: 'appletv', name: 'Apple TV', subscribed: false, store: true },
  googletv: { id: 'googletv', name: 'Google TV', subscribed: false, store: true },
};

export const SUBSCRIBED_COUNT = Object.values(SERVICES).filter((s) => s.subscribed).length;

/** Apps on the Home row. */
export const HOME_APPS: { id: string; name: string; mark: string; weight: number }[] = [
  { id: 'netflix', name: 'Netflix', mark: 'NETFLIX', weight: 700 },
  { id: 'youtube', name: 'YouTube', mark: '▶ YouTube', weight: 600 },
  { id: 'prime', name: 'Prime Video', mark: 'prime video', weight: 600 },
  { id: 'disney', name: 'Disney+', mark: 'Disney+', weight: 400 },
  { id: 'max', name: 'Max', mark: 'max', weight: 700 },
  { id: 'appletvplus', name: 'Apple TV+', mark: 'Apple TV+', weight: 400 },
  { id: 'spotify', name: 'Spotify', mark: 'Spotify', weight: 600 },
];
