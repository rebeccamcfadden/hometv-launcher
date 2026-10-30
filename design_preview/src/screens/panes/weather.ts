import { app } from '../../core/app';
import { h, replace } from '../../core/dom';
import { focusable } from '../../core/focus';
import { net, SAVED_AT } from '../../core/net';
import { DEFAULT_PLACE, deg, settings, UNIT_LABELS, type Unit } from '../../core/settings';
import { findPlaces, placeById, placeLine, type Place } from '../../data/places';
import { icon } from '../../ui/icons';
import { button, segmented } from '../../ui/widgets';
import type { Pane, PaneHost } from './pane';

const KEY_ROWS = ['abcdef', 'ghijkl', 'mnopqr', 'stuvwx', 'yz1234', '567890'];
const TYPEABLE = /^[a-zåäöéü0-9 \-]$/i;

/**
 * Weather (V2 design r2-weather A): a summary of the place now showing, Change city and Use default (New York), and
 * the unit (°C, °F, TV default, which follows the TV's region). Change city is a sub-state: the 6×6 keyboard and the
 * matches inside the pane. With nothing typed the match list shows the place now showing and quick picks (r2-weather
 * B's idea). Back leaves the sub-state.
 */
export function weatherPane(host: PaneHost): Pane {
  let changing = false;
  let query = '';

  const choose = (p: Place) => {
    const s = settings.get();
    settings.set({ place: p.id, recentPlaces: [s.place, ...s.recentPlaces.filter((x) => x !== p.id && x !== s.place)].slice(0, 3) });
    changing = false;
    query = '';
    host.rerender();
    host.focusKey('w-change');
  };

  const setQuery = (q: string) => {
    query = q.replace(/^\s+/, '').replace(/\s{2,}/g, ' ');
    paintSearch();
  };

  const summary = () => {
    const p = placeById(settings.get().place);
    const offline = net.isOffline();
    return [
      h('div.eyebrow', 'Weather'),
      h('div.pane-title', 'Weather on Home'),
      h(
        'div.place',
        h('span.place-pin', { html: icon('pin', 30) }),
        h('div', h('div.nm', p.name), h('div.rg', placeLine(p))),
        h(
          'div.now',
          net.get() === 'offline-empty' ? h('div', 'Unavailable offline') : [h('b', deg(p.temp)), h('div', offline ? `Offline · updated ${SAVED_AT.replace('today ', '')}` : p.desc)],
        ),
      ),
      h(
        'div.wrow',
        h('div', h('div.k', 'Place'), h('div.s', 'For today, tomorrow and the hourly strip')),
        h(
          'div.wrow-ctl',
          (() => {
            const b = button('Change city', {
              icon: 'search',
              key: 'w-change',
              onOk: () => {
                changing = true;
                query = '';
                host.rerender();
                host.focusKey('wk-a');
              },
            });
            b.dataset.entry = '';
            return b;
          })(),
          button(`Use default (${placeById(DEFAULT_PLACE).name})`, {
            key: 'w-default',
            onOk: () => (settings.get().place === DEFAULT_PLACE ? app.toast(`Already showing ${placeById(DEFAULT_PLACE).name}`) : choose(placeById(DEFAULT_PLACE))),
          }),
        ),
      ),
      h(
        'div.wrow',
        h('div', h('div.k', 'Temperature'), h('div.s', 'TV default follows the TV’s region settings')),
        segmented<Unit>({
          options: (['c', 'f', 'tv'] as Unit[]).map((u) => [u, UNIT_LABELS[u]]),
          get: () => settings.get().unit,
          set: (unit) => settings.set({ unit }),
          group: 'w-unit',
        }),
      ),
      h('div.pane-note', 'Forecasts from Open-Meteo. Only the place name you type is sent.'),
    ];
  };

  // ---- Change city ----
  const typed = h('span.sb-typed');
  const ghost = h('span.sb-ghost');
  const results = h('div.w-results');

  const key = (label: string, onOk: () => void, cls = '') => focusable(h(`div.key${cls}`, label), { group: 'wkb', key: `wk-${label}`, isDefault: label === 'a', onOk });
  const keyboard = () =>
    h(
      'div.kbp',
      h(
        'div.keyboard',
        KEY_ROWS.map((row) => h('div.key-row', [...row].map((c) => key(c, () => setQuery(query + c))))),
        h(
          'div.key-row',
          key('Space', () => setQuery(query + ' '), '.key--space'),
          focusable(h('div.key', { html: icon('backspace', 26) }), { group: 'wkb', key: 'wk-del', onOk: () => setQuery(query.slice(0, -1)) }),
          key('Clear', () => setQuery(''), '.key--clear'),
        ),
      ),
    );

  const res = (p: Place, name: Node | string, line: string, k: string, cur = false) =>
    focusable(
      h(`div.res${cur ? '.is-cur' : ''}`, h('span.pin', { html: icon('pin', 26) }), h('div', h('div.nm', name), h('div.rg', line)), h('span.t', deg(p.temp))),
      { group: 'wres', key: k, onOk: () => choose(p) },
    );

  function paintSearch() {
    typed.classList.toggle('is-placeholder', !query);
    replace(typed, query || 'Type a city');
    const matches = findPlaces(query);
    const top = matches[0];
    replace(ghost, top && top.via === 'name' && top.place.name.length > query.length ? top.place.name.slice(query.length).toLowerCase() : '');
    const now = placeById(settings.get().place);
    const picks = [DEFAULT_PLACE, ...settings.get().recentPlaces].filter((id, i, a) => id !== now.id && a.indexOf(id) === i).map(placeById);
    if (!query) {
      replace(
        results,
        h('div.sp-head', 'Now showing'),
        h('div.res-list', res(now, now.name, placeLine(now), `wr-now`, true)),
        picks.length ? [h('div.sp-head.sp-head--gap', 'Quick picks'), h('div.res-list', picks.map((p) => res(p, p.name, `${p.id === DEFAULT_PLACE ? 'Default' : 'Recent'} · ${p.region}, ${p.country}`, `wr-${p.id}`)))] : null,
      );
    } else if (net.isOffline()) {
      replace(
        results,
        h('div.offline-pill', h('span', { html: icon('wifiOff', 24) }), 'Offline'),
        h('div.tip', 'Finding a city needs the internet. The quick picks work offline.'),
        picks.length ? [h('div.sp-head.sp-head--gap', 'Quick picks'), h('div.res-list', picks.map((p) => res(p, p.name, `${p.region}, ${p.country}`, `wr-${p.id}`)))] : null,
      );
    } else if (!matches.length) {
      replace(results, h('div.sp-head', 'No places'), h('div.tip', `Nothing called “${query}”. Try fewer letters, or the English name.`));
    } else {
      const viaLocal = matches.find((m) => m.via === 'local');
      replace(
        results,
        h('div.sp-head', `${matches.length} place${matches.length === 1 ? '' : 's'}`),
        h(
          'div.res-list',
          matches.map((m) => {
            const p = m.place;
            const name =
              m.via === 'local'
                ? [h('span.hl', p.local!.slice(0, m.length)), `${p.local!.slice(m.length)} → ${p.name}`]
                : [h('span.hl', p.name.slice(0, m.length)), p.name.slice(m.length)];
            return res(p, h('span', name), m.via === 'local' ? `${p.region}, ${p.country}` : placeLine(p), `wr-${p.id}`);
          }),
        ),
        viaLocal
          ? h(
              'div.tip',
              h('span', { html: icon('info', 22) }),
              h('span', `This TV is set to English, so places come back with English names: ${viaLocal.place.local} is listed as ${viaLocal.place.name}. We search in Swedish too and show both.`),
            )
          : null,
      );
    }
    app.refocus();
  }

  const change = () => {
    paintSearch();
    return [
      h('div.crumb', h('span', { html: icon('back', 20) }), 'Weather · Change city'),
      h('div.qbar', h('span', { html: icon('search', 28) }), typed, ghost),
      h('div.w-change', keyboard(), results),
    ];
  };

  return {
    render: () => (changing ? change() : summary()),
    back() {
      if (!changing) return false;
      changing = false;
      query = '';
      host.rerender();
      host.focusKey('w-change');
      return true;
    },
    onText(ch) {
      if (!changing || !TYPEABLE.test(ch)) return false;
      setQuery(query + ch.toLowerCase());
      return true;
    },
  };
}
