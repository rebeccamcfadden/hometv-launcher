import { net } from './net';

// The TMDB account, as the Account pane shows it (V2 design r2-account A). The sign-in is mocked: a code is "fetched",
// the QR waits until the dev bar's Account menu approves it (or it runs out), then the lists are "imported". On the TV
// this is TMDB's request-token flow, polled while Launcher settings is open.

export type AccountState = 'out' | 'getting' | 'waiting' | 'approved' | 'in' | 'expired' | 'refused';

export const ACCOUNT_STATES: [AccountState, string][] = [
  ['out', 'Signed out'],
  ['getting', 'Getting a code'],
  ['waiting', 'Waiting (QR)'],
  ['approved', 'Approved on the phone'],
  ['in', 'Signed in'],
  ['expired', 'Code expired'],
  ['refused', 'Couldn’t reach TMDB'],
];

export type ListRole = 'household' | 'friends' | 'none';

export interface TmdbList {
  id: string;
  name: string;
  count: number;
  updated: string;
  role: ListRole;
}

export const USERNAME = 'evening_cinema';
/** Codes last 15 minutes. */
const CODE_SECONDS = 15 * 60;
/** Import steps: My list, Seen, Favorites, Household, Friends. */
export const IMPORT_STEPS = 5;

const lists: TmdbList[] = [
  { id: 'family', name: 'Family picks', count: 23, updated: 'today', role: 'household' },
  { id: 'weekend', name: 'Weekend movies', count: 41, updated: '3 days ago', role: 'none' },
  { id: 'tips', name: 'Friends’ tips', count: 18, updated: 'yesterday', role: 'friends' },
  { id: 'club', name: 'Film club', count: 35, updated: 'last week', role: 'none' },
  { id: 'together', name: 'Watch together', count: 12, updated: 'last month', role: 'none' },
];

let state: AccountState = 'out';
let expiresAt = 0;
let imported = 0;
let lastSynced = 'Today 19:52';
let timer = 0;
const listeners = new Set<() => void>();
const tickers = new Set<() => void>();

const emit = () => listeners.forEach((fn) => fn());

function stopTimer() {
  clearInterval(timer);
  clearTimeout(timer);
  timer = 0;
}

function go(s: AccountState) {
  stopTimer();
  state = s;
  if (s === 'getting') timer = window.setTimeout(() => go('waiting'), 1200);
  if (s === 'waiting') {
    expiresAt = Date.now() + CODE_SECONDS * 1000;
    timer = window.setInterval(() => {
      if (secondsLeft() <= 0) return go('expired');
      tickers.forEach((fn) => fn());
    }, 1000);
  }
  if (s === 'approved') {
    imported = 0;
    timer = window.setInterval(() => {
      imported++;
      if (imported >= IMPORT_STEPS) {
        lastSynced = 'Just now';
        return go('in');
      }
      emit();
    }, 700);
  }
  emit();
}

const secondsLeft = () => Math.max(0, Math.round((expiresAt - Date.now()) / 1000));

export const account = {
  get: () => state,
  on(fn: () => void): () => void {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  /** Every second while a code waits, for the countdown. */
  onTick(fn: () => void): () => void {
    tickers.add(fn);
    return () => tickers.delete(fn);
  },
  /** "14:32". */
  countdown(): string {
    const s = secondsLeft();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  },
  /** How much of the code's time is left, 0–1. */
  fraction: () => secondsLeft() / CODE_SECONDS,
  imported: () => imported,
  lastSynced: () => lastSynced,
  lists: () => lists,
  roleOf: (role: ListRole) => lists.find((l) => l.role === role),
  /** Sign in, New code and Try again: offline, TMDB can't be reached. */
  signIn: () => go(net.isOffline() ? 'refused' : 'getting'),
  cancel: () => go('out'),
  signOut: () => go('out'),
  sync() {
    if (net.isOffline()) return false;
    lastSynced = 'Just now';
    emit();
    return true;
  },
  /** One list per role: giving a list a role takes it from the one that had it. */
  setRole(id: string, role: ListRole) {
    for (const l of lists) {
      if (l.id === id) l.role = role;
      else if (role !== 'none' && l.role === role) l.role = 'none';
    }
    emit();
  },
  /** Section values in Launcher settings: the username, or what the sign-in is doing. */
  value(): { text: string; warn: boolean } {
    switch (state) {
      case 'getting':
        return { text: 'Getting a code…', warn: true };
      case 'waiting':
        return { text: 'Waiting…', warn: true };
      case 'approved':
        return { text: 'Signing in…', warn: true };
      case 'in':
        return { text: USERNAME, warn: false };
      case 'expired':
        return { text: 'Code expired', warn: true };
      default:
        return { text: 'Not signed in', warn: false };
    }
  },
  /** The dev bar's Account menu. */
  devSet: (s: AccountState) => go(s),
};
