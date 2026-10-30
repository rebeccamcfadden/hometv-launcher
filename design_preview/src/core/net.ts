// The simulated network and data state, switched from the dev bar under the stage (or ?state= in the URL), so
// each loading, offline and error state (V2 design 06-states A) can be reviewed. On the TV these come from the
// network and the catalogue's cache; nothing here is real.

export type DataState = 'online' | 'loading' | 'preview-loading' | 'offline' | 'offline-empty' | 'error-where' | 'error-cast';

export const DATA_STATES: [DataState, string][] = [
  ['online', 'Online, everything loaded'],
  ['loading', 'Loading: Movies & TV Shows'],
  ['preview-loading', 'Loading: title preview'],
  ['offline', 'Offline, lists saved'],
  ['offline-empty', 'Offline, nothing saved'],
  ['error-where', 'Error: Where to watch tab'],
  ['error-cast', 'Error: Cast & crew tab'],
];

/** When the saved copy of the lists was made, as the offline pill says. */
export const SAVED_AT = 'today 19:52';

let state: DataState = 'online';
let previous: DataState = 'online';
const listeners = new Set<() => void>();

export const net = {
  get: () => state,
  set(s: DataState) {
    if (s === state) return;
    previous = state;
    state = s;
    listeners.forEach((fn) => fn());
  },
  /** Calls `fn` on every change; returns a function that stops it. */
  on(fn: () => void): () => void {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  /** The state before the last change, to tell "back online" apart. */
  previous: () => previous,
  isOffline: () => state === 'offline' || state === 'offline-empty',
  /** The tab whose data failed to load, by its label in the title preview. */
  failingTab: (s: DataState = state): string | null => (s === 'error-where' ? 'Where to watch' : s === 'error-cast' ? 'Cast & crew' : null),
};
