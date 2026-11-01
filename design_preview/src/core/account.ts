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

export interface TmdbList {
  id: string;
  name: string;
  updated: string;
  /** Shown as a chip in Movies, TV Shows and Pick for me (Choose lists). */
  shown: boolean;
}

export const USERNAME = 'evening_cinema';
/** Codes last 15 minutes. */
const CODE_SECONDS = 15 * 60;
/** Import steps: My list, Seen, Favorites, then each list shown as a chip. */
const importSteps = () => 3 + lists.filter((l) => l.shown).length;

const lists: TmdbList[] = [
  { id: 'family', name: 'Family picks', updated: 'today', shown: true },
  { id: 'weekend', name: 'Weekend movies', updated: '3 days ago', shown: false },
  { id: 'tips', name: 'Friends’ tips', updated: 'yesterday', shown: true },
  { id: 'club', name: 'Film club', updated: 'last week', shown: false },
  { id: 'together', name: 'Watch together', updated: 'last month', shown: false },
  { id: 'docs', name: 'Documentaries', updated: 'last month', shown: false },
  { id: 'kids', name: 'Kids’ night', updated: 'in August', shown: false },
];

let state: AccountState = 'out';
let everSignedIn = false;
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
  if (s === 'in') everSignedIn = true;
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
      if (imported >= importSteps()) {
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
  importSteps,
  lastSynced: () => lastSynced,
  lists: () => lists,
  /** Signed in at least once: after a sign-out the mock Household and Friends don't come back. */
  everSignedIn: () => everSignedIn,
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
  /** Show a list as a chip, or hide it; any number can show. */
  setShown(id: string, shown: boolean) {
    const l = lists.find((x) => x.id === id);
    if (!l || l.shown === shown) return;
    l.shown = shown;
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
