import { app, type Key } from '../../core/app';
import { h } from '../../core/dom';
import {
  BACKDROP_LABELS,
  MOTION_LABELS,
  settings,
  SHADOW_LABELS,
  START_LABELS,
  type BackdropPicture,
  type CardShadows,
  type Motion,
  type OpenOnStart,
} from '../../core/settings';
import { icon, type IconName } from '../../ui/icons';
import { button, segmented } from '../../ui/widgets';
import { confirmDialog } from '../confirm';
import { placeholder } from '../placeholder';
import { paneHead, type Pane, type PaneHost } from './pane';

const entry = (el: HTMLElement) => ((el.dataset.entry = ''), el);

/** A Performance choice's row: its name over what it is, and its segmented control on the right. */
const perfRow = (title: string, sub: string, control: HTMLElement) =>
  h('div.perf-row', h('div.perf-text', h('div.perf-title', title), h('div.perf-sub', sub)), h('div.perf-ctl', control));

const onOff = (group: string, get: () => boolean, set: (on: boolean) => void) =>
  segmented<'on' | 'off'>({
    options: [
      ['on', 'On'],
      ['off', 'Off'],
    ],
    get: () => (get() ? 'on' : 'off'),
    set: (v) => set(v === 'on'),
    group,
    small: true,
  });

/**
 * Launcher settings › Performance (the project lead's decision, 2026-11-01): Motion, card shadows, the focus glow, the
 * backdrop picture and the animated details, each a segmented choice. Light mode at the top (the project lead,
 * 2026-11-01) sets every choice to its lightest, Motion Off included; each stays a choice of its own afterwards. A low-RAM TV starts at the
 * light end (no shadows, Motion Reduced, nothing animated); the prototype is a TV with memory to spare, so it starts
 * with everything on. It draws no card shadows or glow, so those two are saved only; Motion and the backdrop apply.
 */
export const performancePane = (host: PaneHost): Pane => ({
  /**
   * ↓ goes to the next row's control, on its selected option, and → stays within its control: the controls have
   * different widths, so the spatial D-pad would skip a narrow one or jump rows. ↑ already goes row by row.
   */
  onKey(key: Key, focused: HTMLElement) {
    // → stays in its own control: at a narrow control's last option, the page's spatial D-pad would jump to a wider
    // control on the next row.
    if (key === 'right') {
      const seg = focused.closest('.seg');
      if (!seg) return false;
      const opts = [...seg.querySelectorAll<HTMLElement>('.seg-opt')];
      const next = opts[opts.indexOf(focused) + 1];
      if (next?.dataset.key) host.focusKey(next.dataset.key);
      return true;
    }
    if (key !== 'down') return false;
    const s = settings.get();
    const rows = [
      'perf-light',
      `seg-motion-${s.motion}`,
      `seg-shadows-${s.cardShadows}`,
      `seg-glow-${s.focusGlow ? 'on' : 'off'}`,
      `seg-backdrop-${s.backdropPicture}`,
      `seg-details-${s.movingDetails ? 'on' : 'off'}`,
    ];
    const groups = ['perf-light', 'seg-motion', 'seg-shadows', 'seg-glow', 'seg-backdrop', 'seg-details'];
    const at = groups.findIndex((g) => focused.dataset.key === g || focused.dataset.key?.startsWith(`${g}-`));
    if (at < 0 || at === rows.length - 1) return false;
    host.focusKey(rows[at + 1]);
    return true;
  },
  render: () => [
    h('div.eyebrow', 'Performance'),
    h('div.pane-title', 'Performance'),
    h('div.pane-sub', 'How much the launcher draws and moves. Lighter choices keep it smooth on TVs with little memory; what it shows stays the same.'),
    h(
      'div.pane-actions',
      entry(
        button('Light mode', {
          icon: 'gauge',
          primary: true,
          key: 'perf-light',
          onOk: () => {
            settings.set({ motion: 'off', cardShadows: 'off', focusGlow: false, backdropPicture: 'plain', movingDetails: false });
            app.toast('Light mode: every choice at its lightest');
          },
        }),
      ),
    ),
    h('div.pane-note', 'Sets every choice below to its lightest: Motion off, no card shadows or glow, a plain backdrop and nothing animated.'),
    h(
      'div.perf-rows',
      perfRow(
        'Motion',
        'Page slides, focus scale and fades',
        segmented<Motion>({
          options: (Object.keys(MOTION_LABELS) as Motion[]).map((m) => [m, MOTION_LABELS[m]]),
          get: () => settings.get().motion,
          set: (motion) => settings.set({ motion }),
          group: 'seg-motion',
          small: true,
        }),
      ),
      perfRow(
        'Card shadows',
        'The soft shadow under artwork and tiles (Midnight has none)',
        segmented<CardShadows>({
          options: (Object.keys(SHADOW_LABELS) as CardShadows[]).map((s) => [s, SHADOW_LABELS[s]]),
          get: () => settings.get().cardShadows,
          set: (cardShadows) => settings.set({ cardShadows }),
          group: 'seg-shadows',
          small: true,
        }),
      ),
      perfRow(
        'Focus glow',
        'The theme’s light around the focused tile; the focus ring stays',
        onOff('seg-glow', () => settings.get().focusGlow, (focusGlow) => settings.set({ focusGlow })),
      ),
      perfRow(
        'Backdrop picture',
        'The photo or drawing behind Home, or the theme’s plain sky colour',
        segmented<BackdropPicture>({
          options: (Object.keys(BACKDROP_LABELS) as BackdropPicture[]).map((b) => [b, BACKDROP_LABELS[b]]),
          get: () => settings.get().backdropPicture,
          set: (backdropPicture) => settings.set({ backdropPicture }),
          group: 'seg-backdrop',
          small: true,
        }),
      ),
      perfRow(
        'Animated details',
        'Snow, rain, leaves and bats drifting over Seasonal backdrops',
        onOff('seg-details', () => settings.get().movingDetails, (movingDetails) => settings.set({ movingDetails })),
      ),
    ),
  ],
});

/** Open on start: what the TV shows after it is switched on (Motion moved to Performance). */
export const startPane = (): Pane => ({
  render: () => [
    h('div.eyebrow', 'Open on start'),
    h('div.pane-title', 'Open on start'),
    h('div.pane-sub', 'What the TV shows after it is switched on.'),
    h(
      'div.pane-seg',
      segmented<OpenOnStart>({
        options: (Object.keys(START_LABELS) as OpenOnStart[]).map((s) => [s, START_LABELS[s]]),
        get: () => settings.get().openOnStart,
        set: (openOnStart) => settings.set({ openOnStart }),
        group: 'seg-start',
        entry: true,
      }),
    ),
  ],
});
export const homePane = (): Pane => ({
  render: () => [
    h('div.eyebrow', 'Default Home'),
    h('div.pane-title', 'This launcher is Home'),
    h('div.pane-good', h('span', { html: icon('check', 22) }), 'The remote’s Home button opens it'),
    h('div.pane-sub', 'Android sends the Home button to the app chosen as Home. To hand Home back to the TV’s own launcher, choose it there.'),
    h('div.pane-actions', entry(button('Choose Home app', { icon: 'home', key: 'choose-home', onOk: () => app.toast('Opening Android’s Home app setting…') }))),
  ],
});

export const tvPane = (): Pane => ({
  render: () => [
    ...paneHead('TV', 'TV settings', 'Picture, sound, network, apps and accounts are in the TV’s own settings. The nav’s Settings icon has shortcuts to them too.'),
    h(
      'div.pane-actions',
      (
        [
          ['Open TV settings', 'settings', 'TV system settings'],
          ['Picture', 'picture', 'picture settings'],
          ['Network', 'wifi', 'Wi-Fi settings'],
          ['Apps', 'grid', 'the TV’s Apps settings'],
        ] as [string, IconName, string][]
      ).map(([label, ic, what], i) => {
        const b = button(label, { icon: ic, key: `tv-${i}`, onOk: () => app.toast(`Opening ${what}…`) });
        return i === 0 ? entry(b) : b;
      }),
    ),
  ],
});

export const aboutPane = (): Pane => ({
  render: () => [
    h('div.eyebrow', 'About'),
    h('div.pane-title', 'Clean your TV launcher'),
    h('div.kvs', h('div.kv', h('span', 'Version'), h('b', '2.0.0 (build 214)')), h('div.kv', h('span', 'Catalogue cached'), h('b', '1,240 titles · 86 MB'))),
    h(
      'div.tmdb',
      // TMDB's official logo goes here, from their brand assets; a placeholder until it is added.
      h('div.tmdb-logo', 'TMDB'),
      h('div', h('div.tmdb-notice', 'This product uses the TMDB API but is not endorsed or certified by TMDB.'), h('div.tmdb-note', 'Logo area: TMDB’s official logo, from their brand assets.')),
    ),
    h(
      'div.pane-credits',
      'Weather data by Open-Meteo.com (CC BY 4.0). Streaming availability from JustWatch via TMDB. Apple TV Store prices from Apple’s iTunes Search API.',
    ),
    h(
      'div.pane-actions',
      entry(button('Open-source licences', { key: 'licences', onOk: () => app.push(placeholder('Open-source licences', 'The licences of the libraries the launcher uses.')) })),
      button('Clear cache', {
        key: 'clear-cache',
        onOk: () =>
          app.push(
            confirmDialog({
              title: 'Clear the cache?',
              body: 'Artwork, the catalogue and prices are fetched again. Your lists and settings stay.',
              actions: [{ label: 'Clear cache', primary: true, onOk: () => app.toast('Cache cleared') }],
            }),
          ),
      }),
    ),
  ],
});
