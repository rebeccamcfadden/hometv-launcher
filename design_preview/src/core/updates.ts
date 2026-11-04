// In-app updates (Family install step 5). A launcher from a release (tools/release.ps1) reads the latest GitHub
// Release once a day while Home shows. When a newer one is out, About says so and Home shows one notice per version;
// nothing is downloaded until Install update is pressed (the project lead, 2026-11-02). Then the APK is downloaded,
// checked against the release's SHA-256 and signing certificate, and Android asks "Install this update?". The
// prototype keeps the state in localStorage and simulates each step.

export type UpdateState =
  /** A build made on a computer (tv.properties), not a release: it updates from that computer; no check runs. */
  | 'own'
  /** The newest release is this one. */
  | 'current'
  /** A newer release is out; nothing is downloaded until Install update. */
  | 'available'
  /** Install update was pressed: the update is downloading, then Android asks. */
  | 'downloading'
  /** A newer release is out, but Android doesn't let the launcher install apps yet: Install opens the screen to allow it. */
  | 'nogrant'
  /** A newer release is out, and this TV has no screen to allow it: the installer on the laptop updates it. */
  | 'noscreen'
  /** The check couldn't reach GitHub; tried again tomorrow. */
  | 'failed';

export const UPDATE_STATES: [UpdateState, string][] = [
  ['own', 'own build (no check)'],
  ['current', 'up to date'],
  ['available', '2.9 available'],
  ['downloading', 'downloading 2.9 (after Install)'],
  ['nogrant', '2.9 available, no permission'],
  ['noscreen', '2.9 available, no permission screen'],
  ['failed', 'check failed'],
];

export const CURRENT_VERSION = '2.8';
export const NEW_VERSION = '2.9';

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

const STORE = 'hometv-preview-updates-v2';
const DEFAULTS: Saved = { state: 'current', check: true, checkedAt: Date.now() - 3 * 3600_000, progress: 0, noticed: '' };

const load = (): Saved => {
  try {
    const s: Saved = { ...DEFAULTS, ...(JSON.parse(localStorage.getItem(STORE) || '{}') as Partial<Saved>) };
    // A download cut off by a reload has nothing driving it any more: it is available again, as on the TV.
    if (s.state === 'downloading') return { ...s, state: 'available', progress: 0 };
    return s;
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

/** A newer version is out and waiting for the person: Home's notice and About's Install say so. */
const waiting = (s: UpdateState) => s === 'available' || s === 'nogrant' || s === 'noscreen';

const time = (ms: number) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

export const updates = {
  get: () => saved,
  on(fn: () => void) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  waiting: () => saved.check && waiting(saved.state),
  /** Launcher settings' About row: the version, or that an update is out. */
  value(): { text: string; warn?: boolean } {
    if (saved.check && (waiting(saved.state) || saved.state === 'downloading'))
      return { text: `${NEW_VERSION} available`, warn: saved.state === 'nogrant' || saved.state === 'noscreen' };
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
      case 'available':
        return `Version ${NEW_VERSION} is available. Install update downloads it, then Android asks; your lists and settings stay.`;
      case 'downloading':
        return `Downloading version ${NEW_VERSION}… ${s.progress}% · then Android asks to install it`;
      case 'nogrant':
        return `Version ${NEW_VERSION} is available, but this TV doesn’t let the launcher install it yet. Install update opens the screen to allow it: switch HomeTV Launcher on.`;
      case 'noscreen':
        return `Version ${NEW_VERSION} is available, but this TV can’t install it from here. Run the installer on the laptop again to update.`;
      case 'failed':
        return `Couldn’t check for updates (no internet) · tries again tomorrow`;
    }
  },
  setCheck(on: boolean) {
    commit({ check: on });
  },
  /** Check now: the release is newer, so About offers Install update (nothing is downloaded yet). */
  checkNow() {
    commit({ state: 'available', checkedAt: Date.now(), noticed: '' });
  },
  /** Install update: downloads, then calls `prompt` for Android's own "Install this update?". */
  download(prompt: () => void) {
    window.clearInterval(timer);
    commit({ state: 'downloading', progress: 0 });
    timer = window.setInterval(() => {
      const progress = Math.min(100, saved.progress + 25);
      if (progress >= 100) {
        window.clearInterval(timer);
        commit({ state: 'available', progress: 0 });
        prompt();
      } else commit({ progress });
    }, 400);
  },
  /** The person allowed "install unknown apps" on Android's screen: the update is installable now. The notice marker
   * stays, so Home doesn't show the same version again. */
  granted() {
    if (saved.state === 'nogrant') commit({ state: 'available' });
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
