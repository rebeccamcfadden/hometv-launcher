import { msToNextPhase, phaseAt, type Phase } from './daylight';
import { imgUrl } from './dom';
import { net } from './net';
import { settings } from './settings';

// The themes (M9), as the launcher has them: Classic ones keep their look all year; Seasonal ones follow the date
// through ten periods, and their picture follows the part of the day (F22: dawn, day, sunset, night, from the sun;
// Launcher settings › Theme › Time of day can keep it on day). Colours, radii and type are CSS variables in
// styles/themes.css, keyed by the data-theme and data-period attributes set here on <html>; this module picks the
// theme, its period, its fonts and its backdrop. Seasonal · Nature was removed (2026-11-01): a saved Nature shows Vector
// art.

export type ThemeId =
  | 'midnight'
  | 'daylight'
  | 'oled'
  | 'high-contrast'
  | 'cinema'
  | 'landscapes'
  | 'seasonal-vector'
  | 'seasonal-8bit';

export type Period = 'snow' | 'thaw' | 'spring-rain' | 'bloom' | 'pride' | 'midsummer' | 'late-summer' | 'autumn' | 'halloween' | 'christmas';

export const PERIOD_LABELS: Record<Period, string> = {
  snow: 'Snow',
  thaw: 'Thaw',
  'spring-rain': 'Spring rain',
  bloom: 'Bloom',
  pride: 'Pride',
  midsummer: 'Midsummer',
  'late-summer': 'Late summer',
  autumn: 'Autumn',
  halloween: 'Halloween',
  christmas: 'Christmas',
};

/** Where the photos and drawings are downloaded from: launcher/backdrops/ in the repo, never in the APK. */
export const BACKDROP_BASE = 'https://cdn.jsdelivr.net/gh/rebeccamcfadden/cleanyourtv@main/launcher/backdrops/';

/** A Google Fonts family and the weights to ask for (none for single-weight faces). */
interface Face {
  family: string;
  weights?: string;
}

/** What is behind Home: the bundled evening landscape, nothing, a downloaded picture, or 8-bit's pixel art. */
type Backdrop = { kind: 'res' | 'url' | 'pixel'; src: string } | { kind: 'none' };

export interface ThemeDef {
  id: ThemeId;
  name: string;
  group: 'classic' | 'seasonal';
  display: Face;
  ui: Face;
  backdrop(period: Period, hour: number, phase: Phase): Backdrop;
}

const UI = '400;500;600;700';
const url = (path: string): Backdrop => ({ kind: 'url', src: BACKDROP_BASE + path });

export const THEMES: ThemeDef[] = [
  {
    id: 'midnight',
    name: 'Midnight',
    group: 'classic',
    display: { family: 'Inter', weights: '300' },
    ui: { family: 'Inter', weights: UI },
    backdrop: () => ({ kind: 'res', src: imgUrl('bg-evening.jpg') }),
  },
  {
    id: 'daylight',
    name: 'Daylight',
    group: 'classic',
    display: { family: 'Figtree', weights: '300' },
    ui: { family: 'Figtree', weights: UI },
    backdrop: () => url('classic/daylight.webp'),
  },
  {
    id: 'oled',
    name: 'OLED Black',
    group: 'classic',
    display: { family: 'Manrope', weights: '300' },
    ui: { family: 'Manrope', weights: UI },
    backdrop: () => ({ kind: 'none' }),
  },
  {
    id: 'high-contrast',
    name: 'High Contrast',
    group: 'classic',
    display: { family: 'Atkinson Hyperlegible Next', weights: '400' },
    ui: { family: 'Atkinson Hyperlegible Next', weights: UI },
    backdrop: () => ({ kind: 'none' }),
  },
  {
    id: 'cinema',
    name: 'Cinema',
    group: 'classic',
    display: { family: 'DM Serif Display' },
    ui: { family: 'DM Sans', weights: UI },
    backdrop: () => url('classic/cinema.webp'),
  },
  {
    id: 'landscapes',
    name: 'Landscapes',
    group: 'classic',
    display: { family: 'Outfit', weights: '300' },
    ui: { family: 'Outfit', weights: UI },
    // A new photo every hour, from four.
    backdrop: (_p, hour) => url(`classic/landscapes-${(hour % 4) + 1}.webp`),
  },
  {
    id: 'seasonal-vector',
    name: 'Vector art',
    group: 'seasonal',
    display: { family: 'Baloo 2', weights: '500' },
    ui: { family: 'Nunito', weights: UI },
    backdrop: (p, _h, phase) => url(`seasonal/vector-5/${p}-${phase}.webp`),
  },
  {
    id: 'seasonal-8bit',
    name: '8-bit',
    group: 'seasonal',
    display: { family: 'Press Start 2P' },
    ui: { family: 'Pixelify Sans', weights: UI },
    backdrop: (p, _h, phase) => ({ kind: 'pixel', src: `${BACKDROP_BASE}seasonal/8bit/${p}${phase === 'day' ? '' : `-${phase}`}.png` }),
  },
];

export const themeById = (id: string): ThemeDef =>
  THEMES.find((t) => t.id === (id === 'seasonal-nature' ? 'seasonal-vector' : id)) ?? THEMES[0];
export const isThemeId = (id: string | null): id is ThemeId => THEMES.some((t) => t.id === id);

const MONTHS: Period[] = ['snow', 'snow', 'thaw', 'spring-rain', 'bloom', 'pride', 'midsummer', 'late-summer', 'autumn', 'autumn', 'autumn', 'christmas'];

/** The period on a date: by month, except Halloween, 15 Oct – 2 Nov (as the launcher's SeasonCalendar). */
export function periodAt(d: Date): Period {
  const m = d.getMonth() + 1;
  const day = d.getDate();
  if ((m === 10 && day >= 15) || (m === 11 && day <= 2)) return 'halloween';
  return MONTHS[m - 1];
}

/** A date in each period, for the dev bar's Date menu. */
export const PERIOD_DATES: [Period, string][] = [
  ['snow', '01-15'],
  ['thaw', '03-15'],
  ['spring-rain', '04-15'],
  ['bloom', '05-15'],
  ['pride', '06-15'],
  ['midsummer', '07-15'],
  ['late-summer', '08-15'],
  ['autumn', '09-20'],
  ['halloween', '10-31'],
  ['christmas', '12-24'],
];

/** "2026-10-31" as a local date, or null when it isn't one. */
export function parseDate(iso: string | null): Date | null {
  const m = iso ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso) : null;
  if (!m) return null;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 12);
  return d.getMonth() === Number(m[2]) - 1 ? d : null;
}

let dateOverride: Date | null = null;
let phaseOverride: Phase | null = null;
const listeners = new Set<() => void>();
/** Pictures that have arrived once; the browser's cache stands in for the TV's disk cache. */
const loaded = new Set<string>();
let wanted = '';
let timer = 0;

const root = document.documentElement;

function link(id: string): HTMLLinkElement {
  let el = document.getElementById(id) as HTMLLinkElement | null;
  if (!el) {
    el = document.createElement('link');
    el.id = id;
    el.rel = 'stylesheet';
    document.head.append(el);
  }
  return el;
}

/** One css2 URL for the faces, with each family's weights merged ("Figtree:wght@300;400;500;600;700"). */
function fontsHref(faces: Face[]): string {
  const weights = new Map<string, Set<string>>();
  for (const f of faces) {
    const set = weights.get(f.family) ?? new Set<string>();
    f.weights?.split(';').forEach((w) => set.add(w));
    weights.set(f.family, set);
  }
  const families = [...weights].map(([family, ws]) => {
    const list = [...ws].sort((a, b) => Number(a) - Number(b));
    return `family=${family.replace(/ /g, '+')}${list.length ? `:wght@${list.join(';')}` : ''}`;
  });
  return `https://fonts.googleapis.com/css2?${families.join('&')}&display=swap`;
}

/** Only the active theme's faces are fetched; Midnight's Inter is in index.html. Roboto stands in for the TV's font. */
function applyFonts(t: ThemeDef, system: boolean) {
  const faces = system ? [{ family: 'Roboto', weights: `300;${UI}` }] : t.id === 'midnight' ? [] : [t.display, t.ui];
  const el = link('theme-fonts');
  const href = faces.length ? fontsHref(faces) : '';
  if (el.getAttribute('href') !== href) {
    if (href) el.href = href;
    else el.removeAttribute('href');
  }
}

let swatchFonts = false;
/** The picker's swatches show "Aa" in each theme's display face; those are fetched when the picker first shows. */
export function loadSwatchFonts() {
  if (swatchFonts) return;
  swatchFonts = true;
  link('swatch-fonts').href = fontsHref(THEMES.filter((t) => t.id !== 'midnight').map((t) => t.display));
}

function setImage(src: string | null) {
  root.style.setProperty('--bg-image', src ? `url("${src}")` : 'none');
}

/**
 * Downloaded once, then kept. The sky shows while it loads, when it can't be fetched, and offline when it never
 * arrived ("Offline, nothing saved" has nothing cached at all).
 */
function applyBackdrop(b: Backdrop) {
  root.dataset.backdrop = b.kind;
  if (b.kind === 'none') {
    wanted = '';
    return setImage(null);
  }
  wanted = b.src;
  if (b.kind === 'res' || (loaded.has(b.src) && net.get() !== 'offline-empty')) return setImage(b.src);
  setImage(null);
  if (net.isOffline()) return;
  const img = new Image();
  img.onload = () => {
    loaded.add(b.src);
    if (wanted === b.src) setImage(b.src);
  };
  img.src = b.src;
}

function apply() {
  const s = settings.get();
  const t = themeById(s.theme);
  const now = theme.date();
  root.dataset.theme = t.id;
  if (t.group === 'seasonal') root.dataset.period = periodAt(now);
  else delete root.dataset.period;
  root.dataset.font = s.themeFont;
  applyFonts(t, s.themeFont === 'system');
  // The date override picks the period; the hour stays real, so Landscapes keeps rotating while previewing a date.
  // Launcher settings › Performance › Backdrop picture: Plain colour shows the theme's sky gradient without the picture
  // (a theme with no backdrop keeps its plain background).
  const phase = theme.phase();
  root.dataset.phase = phase;
  const b = t.backdrop(periodAt(now), new Date().getHours(), phase);
  if (s.backdropPicture === 'plain' && b.kind !== 'none') {
    root.dataset.backdrop = 'plain';
    wanted = '';
    setImage(null);
  } else {
    applyBackdrop(b);
  }
  // Landscapes changes on the hour, the period at midnight and a Seasonal picture when the sun moves on: look again at
  // whichever comes first.
  clearTimeout(timer);
  let wait = 3_600_000 - (Date.now() % 3_600_000);
  if (t.group === 'seasonal' && s.timeOfDay === 'sun' && !phaseOverride) wait = Math.min(wait, msToNextPhase(new Date()));
  timer = window.setTimeout(apply, wait + 1000);
  listeners.forEach((fn) => fn());
}

export const theme = {
  /** Applies the saved theme and follows changes to it, the network and the hour. */
  init() {
    settings.on(apply);
    net.on(apply);
    apply();
  },
  current: () => themeById(settings.get().theme),
  /** Today, or the date the dev bar (or ?date=) put the preview on. */
  date: (): Date => dateOverride ?? new Date(),
  isDateOverridden: () => dateOverride !== null,
  setDate(d: Date | null) {
    dateOverride = d;
    apply();
  },
  period: () => periodAt(theme.date()),
  /** The part of the day a Seasonal picture shows: the dev bar's choice, Always day, or the sun now. */
  phase: (): Phase => phaseOverride ?? (settings.get().timeOfDay === 'day' ? 'day' : phaseAt(new Date())),
  isPhaseOverridden: () => phaseOverride !== null,
  setPhase(p: Phase | null) {
    phaseOverride = p;
    apply();
  },
  /** The name Launcher settings shows: "Midnight", or a Seasonal style with today's period, "Vector art · Halloween". */
  label(): string {
    const t = theme.current();
    return t.group === 'seasonal' ? `${t.name} · ${PERIOD_LABELS[theme.period()]}` : t.name;
  },
  /** Calls `fn` whenever the theme, its period or its backdrop changes; returns a function that stops it. */
  on(fn: () => void): () => void {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
};
