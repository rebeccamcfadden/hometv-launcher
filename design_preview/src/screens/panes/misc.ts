import { app } from '../../core/app';
import { h } from '../../core/dom';
import { MOTION_LABELS, settings, START_LABELS, type Motion, type OpenOnStart } from '../../core/settings';
import { icon, type IconName } from '../../ui/icons';
import { button, segmented } from '../../ui/widgets';
import { confirmDialog } from '../confirm';
import { placeholder } from '../placeholder';
import { paneHead, type Pane } from './pane';

const entry = (el: HTMLElement) => ((el.dataset.entry = ''), el);

/** Motion and Open on start share one pane, as in the design ("Motion and start"); each section enters its own. */
export const motionPane = (first: 'motion' | 'start'): Pane => ({
  render: () => [
    h('div.eyebrow', 'Motion and start'),
    h('div.pane-title', 'Motion'),
    h('div.pane-sub', 'Page slides, focus scale and fades. Reduced keeps the fades only.'),
    h(
      'div.pane-seg',
      segmented<Motion>({
        options: (Object.keys(MOTION_LABELS) as Motion[]).map((m) => [m, MOTION_LABELS[m]]),
        get: () => settings.get().motion,
        set: (motion) => settings.set({ motion }),
        group: 'seg-motion',
        entry: first === 'motion',
      }),
    ),
    h('div.pane-divider'),
    h('div.pane-title.pane-title--small', 'Open on start'),
    h('div.pane-sub', 'What the TV shows after it is switched on.'),
    h(
      'div.pane-seg',
      segmented<OpenOnStart>({
        options: (Object.keys(START_LABELS) as OpenOnStart[]).map((s) => [s, START_LABELS[s]]),
        get: () => settings.get().openOnStart,
        set: (openOnStart) => settings.set({ openOnStart }),
        group: 'seg-start',
        entry: first === 'start',
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
