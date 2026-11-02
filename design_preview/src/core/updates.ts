// In-app updates (Family install step 5). A launcher from a release (tools/release.ps1) reads the latest GitHub
// Release once a day while Home shows, downloads a newer APK in the background, checks it against the release's
// SHA-256 and signing certificate, and lets Android ask "Install this update?". About shows where that stands, and Home
// shows one notice per new version. The prototype keeps the state in localStorage and simulates each step.

export type UpdateState =
  /** A build made on a computer (tv.properties), not a release: it updates from that computer; no check runs. */
  | 'own'
  /** The newest release is this one. */
  | 'current'
  /** A newer release is downloading in the background. */
  | 'downloading'
  /** Downloaded and checked: Android will ask to install it. */
  | 'ready'
  /** Downloaded, but Android doesn't let the launcher install apps yet: About opens the screen that allows it. */
  | 'nogrant'
  /** Downloaded, and this TV has no screen to allow it: the installer on the laptop updates it. */
  | 'noscreen'
  /** The check couldn't reach GitHub; tried again tomorrow. */
  | 'failed';

export const UPDATE_STATES: [UpdateState, string][] = [
  ['own', 'own build (no check)'],
  ['current', 'up to date'],
  ['downloading', 'downloading 2.8'],
  ['ready', '2.8 ready'],
  ['nogrant', '2.8 ready, no permission'],
  ['noscreen', '2.8 ready, no permission screen'],
  ['failed', 'check failed'],
];

export const CURRENT_VERSION = '2.7';
export const NEW_VERSION = '2.8';

interface Saved {
  state: UpdateState;
  /** About › Check for updates (on unless switched off; a release build only). */
  check: boolean;
  /** The last check, as epoch ms. */
  checkedAt: number;
  /** Download progress, 0-100, while downloading. */
  progress: number;
  /** The version Home's notice was last shown for: once per version. */
  noticed: string;
}

const STORE = 'hometv-preview-updates-v1';
const DEFAULTS: Saved = { state: 'current', check: true, checkedAt: Date.now() - 3 * 3600_000, progress: 0, noticed: '' };

const load = (): Saved => {
  try {
    return { ...DEFAULTS, ...(JSON.parse(localStorage.getItem(STORE) || '{}') as Partial<Saved>) };
  } catch {
    return { ...DEFAULTS };
  }
};

let saved = load();
const listeners = new Set<() => void>();
let timer = 0;

const commit = (next: Partial<Saved>) => {
  saved = { ...saved, ...next };
  localStorage.setItem(STORE, JSON.stringify(saved));
  listeners.forEach((fn) => fn());
};

/** A newer version is on the TV and waiting for the person: Home's notice and About's Install say so. */
const waiting = (s: UpdateState) => s === 'ready' || s === 'nogrant' || s === 'noscreen';

const time = (ms: number) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

export const updates = {
  get: () => saved,
  on(fn: () => void) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  waiting: () => saved.check && waiting(saved.state),
  /** Launcher settings' About row: the version, or that an update waits. */
  value(): { text: string; warn?: boolean } {
    if (saved.check && waiting(saved.state)) return { text: `${NEW_VERSION} ready`, warn: saved.state !== 'ready' };
    return { text: CURRENT_VERSION };
  },
  /** About's line under Updates. */
  line(): string {
    const s = saved;
    if (s.state === 'own') return 'This build came from a computer, not a release: it updates from that computer.';
    if (!s.check) return 'Checking for updates is off.';
    switch (s.state) {
      case 'current':
        return `Up to date · checked today at ${time(s.checkedAt)}`;
      case 'downloading':
        return `Downloading version ${NEW_VERSION}… ${s.progress}%`;
      case 'ready':
        return `Version ${NEW_VERSION} is ready to install. Your lists and settings stay.`;
      case 'nogrant':
        return `Version ${NEW_VERSION} is ready, but this TV doesn’t let the launcher install it yet. Install update opens the screen to allow it: switch HomeTV Launcher on.`;
      case 'noscreen':
        return `Version ${NEW_VERSION} is ready, but this TV can’t install it from here. Run the installer on the laptop again to update.`;
      case 'failed':
        return `Couldn’t check for updates (no internet) · tries again tomorrow`;
    }
  },
  setCheck(on: boolean) {
    commit({ check: on });
  },
  /** Check now: the release is newer, so it downloads, then waits for Install. */
  checkNow() {
    window.clearInterval(timer);
    commit({ state: 'downloading', progress: 0, checkedAt: Date.now() });
    timer = window.setInterval(() => {
      const progress = Math.min(100, saved.progress + 20);
      if (progress >= 100) {
        window.clearInterval(timer);
        commit({ state: 'ready', progress: 100 });
      } else commit({ progress });
    }, 500);
  },
  /** Android installed it: the launcher restarts as the new version (the prototype goes back to up to date). */
  installed() {
    commit({ state: 'current', checkedAt: Date.now(), noticed: '' });
  },
  /** Home's notice, once per version. */
  takeNotice(): boolean {
    if (!updates.waiting() || saved.noticed === NEW_VERSION) return false;
    commit({ noticed: NEW_VERSION });
    return true;
  },
  devSet(state: UpdateState) {
    window.clearInterval(timer);
    commit({ state, progress: state === 'downloading' ? 40 : 0, noticed: '', checkedAt: Date.now() - 3 * 3600_000 });
  },
};
