// API keys (F16): the TMDB, Watchmode and Movie of the Night keys a household makes for itself and enters from a phone
// or a laptop. On the TV each key goes into private storage, is checked with one free request, and is never shown in
// full again (only its last four characters). Here they are kept in localStorage, shared with the phone page
// (keys.html) through the storage event, so a key "sent" from that page shows on the TV prototype at once. What each
// key is, its status words and the code's letters are in packages/core (shared with HomeTV for Samsung).

import {
  keyStatus as coreKeyStatus,
  keysValue,
  newCode,
  type KeyEntry,
  type KeyId,
  type KeyState,
} from '@hometv/core/keys';

export { KEY_INFO, type KeyId, type KeyInfo, type KeyState } from '@hometv/core/keys';

interface Entry extends KeyEntry {
  /**
   * While checking: what the (mock) service answers, and when that shows. Kept with the key, so whichever tab is open
   * finishes the check (on the TV the TV itself checks); a key replaced or removed takes its pending answer with it.
   */
  pending?: KeyState;
  until?: number;
}

interface Saved {
  keys: Record<KeyId, Entry>;
  /** The code a phone or laptop needs to send keys to this TV (in the QR's #fragment, or typed on a laptop). */
  code: string;
}

const STORE = 'hometv-preview-keys-v1';

const blank = (state: KeyState = 'unset'): Entry => ({ state, last4: '' });
/** The prototype starts as the project lead's own build does: every key from tv.properties. */
const defaults = (): Saved => ({
  keys: { tmdb: blank('built'), watchmode: blank('built'), motn: blank('built') },
  code: newCode(),
});

function read(): Saved | null {
  try {
    const raw = localStorage.getItem(STORE);
    if (raw) {
      const d = defaults();
      const s = JSON.parse(raw) as Partial<Saved>;
      return { ...d, ...s, keys: { ...d.keys, ...s.keys } };
    }
  } catch {
    // Corrupt or blocked storage: start fresh.
  }
  return null;
}

function write(d: Saved) {
  try {
    localStorage.setItem(STORE, JSON.stringify(d));
  } catch {
    // Blocked storage: the change lasts for this visit only.
  }
}

/** Finishes the checks whose answer is due; true if any did. */
function settle(d: Saved, now = Date.now()): boolean {
  let changed = false;
  for (const id of Object.keys(d.keys) as KeyId[]) {
    const e = d.keys[id];
    if (e.state !== 'checking') continue;
    if (e.pending && (e.until ?? 0) > now) continue;
    // A check left without an answer (an older save) counts as not reached, never as checking forever.
    d.keys[id] = { state: e.pending ?? 'unreachable', last4: e.last4 };
    changed = true;
  }
  return changed;
}

/**
 * What is kept now, with any due checks finished. A fresh preview keeps its defaults at once, so the phone page in
 * another tab reads the same code.
 */
function load(): Saved {
  const saved = read();
  const d = saved ?? defaults();
  if (!saved || settle(d)) write(d);
  return d;
}

let data = load();
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((fn) => fn());
let timer: number | undefined;

/** Wakes when the next pending check is due, in every open tab; the first to wake writes the answer. */
function schedule() {
  window.clearTimeout(timer);
  const due = Object.values(data.keys)
    .filter((e) => e.state === 'checking' && e.until)
    .map((e) => e.until!);
  if (!due.length) return;
  timer = window.setTimeout(() => {
    data = load();
    schedule();
    emit();
  }, Math.max(0, Math.min(...due) - Date.now()) + 20);
}

function save() {
  write(data);
  schedule();
  emit();
}

// The phone page (another tab) changed a key: show it here at once.
window.addEventListener('storage', (e) => {
  if (e.key !== STORE) return;
  data = load();
  schedule();
  emit();
});
schedule();

/**
 * What the service would answer, mocked: a TMDB read access token is a long JWT ("eyJ…"), the others are letters and
 * digits. "offline" in a key stands for a TV that can't reach the service.
 */
function mockCheck(id: KeyId, value: string): KeyState {
  if (/offline/i.test(value)) return 'unreachable';
  if (id === 'tmdb') return /^eyJ[\w-]+\.[\w-]+\.[\w-]+$/.test(value) && value.length >= 100 ? 'ok' : 'bad';
  return /^[A-Za-z0-9]{20,64}$/.test(value) ? 'ok' : 'bad';
}

/** Line under a key's name on the TV and on the phone page (the words are in packages/core). */
export function keyStatus(id: KeyId, e: Entry = data.keys[id]): { text: string; tone: 'good' | 'warn' | 'bad' | 'dim' } {
  return coreKeyStatus(id, e);
}

export const keys = {
  get: (id: KeyId): Readonly<Entry> => data.keys[id],
  code: () => data.code,
  /** The address the QR opens, and a laptop types (the code is typed separately there). */
  address: 'http://192.168.1.50:8766/keys',
  on(fn: () => void): () => void {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  /** A key arrived from the phone or laptop page: kept, then checked with one free request to its service. */
  submit(id: KeyId, value: string) {
    const v = value.trim();
    data = load();
    data.keys[id] = { state: 'checking', last4: v.slice(-4), pending: mockCheck(id, v), until: Date.now() + 1400 };
    save();
  },
  /**
   * Check again: a key that couldn't be reached, or one that stopped working. In the mock the service is reachable
   * again; a key that once worked and still can't be reached would stay "Works".
   */
  recheck(id: KeyId) {
    data = load();
    const e = data.keys[id];
    if (e.state === 'unset' || e.state === 'built' || e.state === 'checking') return;
    data.keys[id] = { state: 'checking', last4: e.last4, pending: e.state === 'unreachable' ? 'ok' : e.state, until: Date.now() + 1200 };
    save();
  },
  /**
   * Forgets a key, the build's own one too (the project lead, 2026-11-02): the TV then has no key for that service until
   * one is entered, rather than going back to the build's. A check still under way goes with it.
   */
  remove(id: KeyId) {
    data = load();
    data.keys[id] = blank();
    save();
  },
  /** A new code: a page opened with the old one can't send keys any more. */
  newCode() {
    data = load();
    data.code = newCode();
    save();
  },
  /** The dev bar: the build's keys, none at all, all entered and working, or one refused. */
  devSet(preset: KeysPreset) {
    const ok = (l: string): Entry => ({ state: 'ok', last4: l });
    const k: Record<KeysPreset, Saved['keys']> = {
      built: { tmdb: blank('built'), watchmode: blank('built'), motn: blank('built') },
      none: { tmdb: blank(), watchmode: blank(), motn: blank() },
      entered: { tmdb: ok('Qx7w'), watchmode: ok('a91f'), motn: ok('3kd2') },
      refused: { tmdb: ok('Qx7w'), watchmode: { state: 'bad', last4: 'a91f' }, motn: blank() },
    };
    data = load();
    data.keys = k[preset];
    save();
  },
  /**
   * For the Features step (F14): whether a TMDB key works, and whether prices have their Watchmode key. A key that
   * has never been checked (unreachable) doesn't count until a check passes.
   */
  tmdbReady: () => ['ok', 'built'].includes(data.keys.tmdb.state),
  pricesReady: () => ['ok', 'built'].includes(data.keys.watchmode.state),
  /** The section's value: "3 working", "Not set", "1 of 3 set", "1 not checked", "1 refused". */
  value: (only?: KeyId[]): { text: string; warn?: boolean } => keysValue(data.keys, only),
};

export type KeysPreset = 'built' | 'none' | 'entered' | 'refused';
export const KEYS_PRESETS: [KeysPreset, string][] = [
  ['built', 'in this build'],
  ['none', 'none yet (a family TV)'],
  ['entered', 'entered on the TV, working'],
  ['refused', 'Watchmode refused'],
];
