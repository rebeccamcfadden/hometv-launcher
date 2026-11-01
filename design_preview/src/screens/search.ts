import { app, type Screen } from '../core/app';
import { h, highlight, initials, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { search, titlesInGenre, type Hit } from '../core/fuzzy';
import { lists } from '../core/lists';
import { net } from '../core/net';
import { store } from '../core/store';
import { byId, TITLES } from '../data/catalog';
import { isOwnIncluded } from '../data/offers';
import type { Title } from '../types';
import { icon } from '../ui/icons';
import { button, chip, navBar, rowOf, smallCard } from '../ui/widgets';
import { personScreen } from './person';
import { titlePreview } from './preview';

type Filter = 'all' | 'movie' | 'show' | 'person' | 'included';
const FILTERS: [Filter, string][] = [
  ['all', 'All'],
  ['movie', 'Movies'],
  ['show', 'TV shows'],
  ['person', 'People'],
  ['included', 'Included only'],
];
const BROWSE_GENRES = ['Sci-fi', 'Drama', 'Comedy', 'Documentary', 'Animation', 'Thriller'];
const KEY_ROWS = ['abcdef', 'ghijkl', 'mnopqr', 'stuvwx', 'yz1234', '567890'];
const TYPEABLE = /^[a-z0-9 \-:'’&.!?]$/i;

export function searchScreen(): Screen {
  let query = '';
  let expanded = false;
  let filter: Filter = 'all';
  let hasMatches = false;

  // ---- search bar ----
  const typed = h('span.sb-typed');
  const ghost = h('span.sb-ghost');
  const hint = h('span.sb-hint');
  // Focusable only on the full results page, where OK on it brings the keyboard panel back to edit the query.
  const bar = focusable(h('div.search-bar', h('span.sb-icon', { html: icon('search', 30) }), typed, ghost, h('span.sb-spacer'), hint), {
    key: 'bar',
    onOk: () => collapse(),
  });

  // ---- keyboard ----
  const key = (label: string, onOk: () => void, cls = '') =>
    focusable(h(`div.key${cls}`, label), { group: 'kb', key: `k-${label}`, isDefault: label === 'a', onOk });
  const keyboard = h(
    'div.keyboard',
    KEY_ROWS.map((row) => h('div.key-row', [...row].map((c) => key(c, () => setQuery(query + c))))),
    h(
      'div.key-row',
      key('Space', () => setQuery(query + ' '), '.key--space'),
      focusable(h('div.key', { html: icon('backspace', 26) }), { group: 'kb', key: 'k-del', onOk: () => setQuery(query.slice(0, -1)) }),
      key('Clear', () => setQuery(''), '.key--clear'),
    ),
  );

  const listHead = h('div.sp-head');
  const list = h('div.sp-list');
  const panel = h('div.search-panel', listHead, list, keyboard);

  const results = h('div.search-results', { 'data-scroll': 'y', 'data-scroll-pad': 40 });
  const viewport = h('div.search-viewport', results, h('div.bottom-fade-local'));

  // No guide line on Search (decided 2026-10-29): the panel and the results use the height.
  const el = h('div.screen.screen--base.search', navBar('search'), bar, panel, viewport);

  function setQuery(q: string) {
    query = q.replace(/^\s+/, '').replace(/\s{2,}/g, ' ');
    if (!query) expanded = false;
    render();
  }

  const openTitle = (t: Title) => {
    if (query) store.pushRecent(query);
    app.push(titlePreview(t));
  };
  const openPerson = (name: string) => {
    if (query) store.pushRecent(query);
    app.push(personScreen(name));
  };

  const listItem = (label: Node | string, tag: string | null, ic: 'clock' | 'search', k: string, onOk: () => void) =>
    focusable(h('div.sp-item', h('span.sp-icon', { html: icon(ic, 22) }), h('span.sp-text', label), tag ? h('span.sp-tag', tag) : null), {
      group: 'list',
      key: k,
      onOk,
    });

  const suggestionAction = (s: Hit) => () => {
    filter = s.kind === 'person' ? 'person' : 'all';
    setQuery(s.label.toLowerCase());
  };

  const resultCard = (hit: Hit, i: number) => {
    const opts = { group: 'grid', key: `r-${hit.kind}-${hit.label}`, isDefault: i === 0 };
    if (hit.kind === 'person') {
      return focusable(
        h(
          'div.card.card--small.card--person',
          h('div.card-art', h('div.person-portrait', initials(hit.label))),
          h('div.card-name', highlight(hit.label, hit.match.indices)),
          h('div.card-meta', 'Person'),
          h('div.card-sub', `${hit.titles?.length ?? 0} title${hit.titles?.length === 1 ? '' : 's'} in the catalogue`),
        ),
        { ...opts, onOk: () => openPerson(hit.label) },
      );
    }
    return smallCard(hit.title!, { ...opts, name: highlight(hit.label, hit.match.indices), onOk: () => openTitle(hit.title!) });
  };

  const titleRow = (label: string, titles: Title[], group: string) => {
    const row = rowOf(
      label,
      titles.map((t) => smallCard(t, { group, key: `${group}-${t.id}`, onOk: () => openTitle(t) })),
    );
    // The row runs off the screen edge; scrolling stops a safe margin short of it so the focused card stays whole.
    row.querySelector<HTMLElement>('.row-items')!.dataset.scrollPad = '96';
    return row;
  };

  /**
   * Offline (06-states A): the keyboard, recent searches and the rows of titles saved on the TV still work; results
   * come from the saved titles only (those on the lists and those viewed), and say so. People need the internet.
   */
  const savedIds = () => new Set([...TITLES.filter(lists.saved).map((t) => t.id), ...store.viewed()]);
  const offlinePill = (text: string) => h('span.offline-pill', h('span', { html: icon('wifiOff', 24) }), text);

  function render() {
    const offline = net.isOffline();
    const nothingSaved = net.get() === 'offline-empty';
    let res = query ? search(query) : null;
    if (res && offline) {
      const saved = nothingSaved ? new Set<string>() : savedIds();
      res = { ...res, titles: res.titles.filter((x) => saved.has(x.title!.id)), people: [], genres: [] };
      res.suggestions = res.suggestions.filter((x) => x.kind === 'title' && saved.has(x.title!.id));
    }
    hasMatches = !!res && (res.titles.length > 0 || res.people.length > 0);

    // Bar
    typed.classList.toggle('is-placeholder', !query);
    replace(typed, query || 'Search movies, shows, people and genres');
    const top = res?.suggestions[0];
    replace(ghost, !expanded && top && top.label.toLowerCase() !== query.toLowerCase() ? `→ ${top.label.toLowerCase()}` : '');
    replace(
      hint,
      offline
        ? offlinePill(nothingSaved ? 'Offline' : 'Offline · saved titles only')
        : !expanded && hasMatches
          ? h('span.pill', h('span', { html: icon('chevRight', 20) }), 'Results')
          : '',
    );

    // Left panel list
    if (!query) {
      replace(listHead, 'Recent searches');
      replace(list, store.recent().map((r) => listItem(r, null, 'clock', `li-${r}`, () => setQuery(r))));
    } else {
      replace(listHead, 'Suggestions');
      const sugg = res?.suggestions ?? [];
      replace(
        list,
        sugg.length
          ? sugg.map((s) => listItem(highlight(s.label.toLowerCase(), s.match.indices), s.tag, 'search', `li-${s.kind}-${s.label}`, suggestionAction(s)))
          : h('div.sp-none', 'No suggestions yet'),
      );
    }

    // Results area
    if (nothingSaved && (!query || !hasMatches)) {
      replace(
        results,
        h(
          'div.state',
          h('div.state-icon', { html: icon('wifiOff', 48) }),
          h('div.empty-title', query ? `No saved titles match “${query}”` : 'You’re offline, and nothing is saved yet'),
          h('div.empty-note.state-note', 'Search needs the internet once to fetch the catalogue. Recent searches still work.'),
          h('div.state-actions', button('Wi-Fi settings', { icon: 'wifi', key: 'search-wifi', onOk: () => app.toast('Opening Wi-Fi settings…') })),
        ),
      );
    } else if (!query) {
      const viewed = store.viewed().map((id) => byId.get(id)).filter((t): t is Title => !!t);
      const mine = store.myListIds().map((id) => byId.get(id)).filter((t): t is Title => !!t);
      replace(
        results,
        h(
          'div.browse',
          titleRow('Recently viewed', viewed, 'viewed'),
          titleRow('From My list', mine, 'mine'),
          h(
            'div.row',
            h('div.row-label', 'Browse by genre'),
            h(
              'div.chips',
              BROWSE_GENRES.map((g) =>
                chip(g, { group: 'genres', key: `g-${g}`, onOk: () => setQuery(g.toLowerCase()) }),
              ),
            ),
          ),
        ),
      );
    } else if (!hasMatches) {
      const viewed = store.viewed().map((id) => byId.get(id)).filter((t): t is Title => !!t);
      replace(
        results,
        h(
          'div.no-matches',
          h('div.nm-title', offline ? `No saved titles match “${query}”` : `No matches for “${query}”`),
          h('div.nm-note', offline ? 'Offline, Search looks only at the titles saved on this TV. The full catalogue needs the internet.' : 'Try fewer letters, or search for a person or a genre.'),
          titleRow('Recently viewed', viewed, 'viewed'),
        ),
      );
    } else {
      const r = res!;
      const genreHit = r.genres[0];
      const genreTitles: Hit[] =
        genreHit && genreHit.match.score > 60
          ? titlesInGenre(genreHit.label)
              .filter((t) => !r.titles.some((x) => x.title === t))
              .map((t) => ({ kind: 'title', label: t.name, tag: '', match: { score: 0, indices: [] }, title: t }))
          : [];
      const all: Hit[] = [...r.titles, ...genreTitles, ...r.people];
      const items = all.filter((x) => {
        if (filter === 'all') return true;
        if (filter === 'person') return x.kind === 'person';
        if (x.kind === 'person') return false;
        if (filter === 'included') return isOwnIncluded(x.title!);
        return x.title!.kind === filter;
      });
      replace(
        results,
        h(
          'div.results-head',
          h('span.results-count', `${items.length} result${items.length === 1 ? '' : 's'}${offline ? ' from saved titles' : ''}`),
          h(
            'div.chips',
            FILTERS.map(([f, label]) =>
              chip(label, {
                selected: f === filter,
                group: 'filters',
                key: `rf-${f}`,
                isDefault: f === filter,
                onOk: () => {
                  filter = f;
                  render();
                },
              }),
            ),
          ),
        ),
        items.length
          ? h('div.results-grid', items.map(resultCard))
          : h('div.nm-note.results-empty', `Nothing under “${FILTERS.find((x) => x[0] === filter)?.[1]}”. Try All.`),
      );
    }

    // Focus zones: while typing with results, the grid is only reachable by pressing → (which widens it).
    el.classList.toggle('is-expanded', expanded);
    bar.toggleAttribute('data-inert', !expanded);
    panel.toggleAttribute('data-inert', expanded);
    viewport.toggleAttribute('data-inert', !!query && hasMatches && !expanded);
    app.refocus();
  }

  function expand() {
    expanded = true;
    render();
    results.scrollTop = 0;
    app.focus(firstResult());
  }

  /** The first card of the full results, else the first thing in them (a filter chip), else the search bar. */
  const firstResult = () =>
    results.querySelector<HTMLElement>('.results-grid [data-focus]') ?? results.querySelector<HTMLElement>('[data-focus]') ?? bar;

  function collapse() {
    expanded = false;
    render();
    const s = app.top();
    app.focus(s?.memory?.get('kb') ?? keyboard.querySelector<HTMLElement>('[data-default]'));
  }

  render();
  net.on(render);

  // Set when Search is left for another tab: the next render puts the panel back without the slide.
  let snap = false;

  const screen: Screen = {
    label: 'Search',
    el,
    // On the full results the keyboard is hidden, so focus that loses its card lands on a result or the bar.
    defaultFocus: () => (expanded ? firstResult() : keyboard.querySelector<HTMLElement>('[data-default]')),
    onShow() {
      if (!snap) return render();
      snap = false;
      el.classList.add('is-snapping');
      results.scrollTop = 0;
      render();
      void el.offsetWidth;
      el.classList.remove('is-snapping');
    },
    // Leaving for another tab, Back to Home or the remote's Home forgets the search, so coming back shows the
    // empty page with focus as on the first visit. A title or person pushed over Search keeps it. Drawn on the next
    // onShow, so the page sliding out keeps its look.
    onLeave() {
      query = '';
      filter = 'all';
      expanded = false;
      snap = true;
      screen.memory?.clear();
      screen.lastFocus = null;
      screen.lastFocusKey = undefined;
    },
    onText(ch) {
      if (!TYPEABLE.test(ch)) return false;
      if (expanded) {
        expanded = false;
        app.focus(keyboard.querySelector<HTMLElement>('[data-default]'));
      }
      setQuery(query + ch.toLowerCase());
      return true;
    },
    onKey(k, f) {
      if (k === 'back' && expanded) {
        collapse();
        return true;
      }
      if (k === 'right' && !expanded && f && panel.contains(f)) {
        if (app.moveWithin(panel, 'right')) return true;
        if (query && hasMatches) expand();
        else app.moveWithin(viewport, 'right') || app.moveWithin(el, 'right');
        return true;
      }
      if (k === 'left' && expanded && f && viewport.contains(f)) {
        if (!app.moveWithin(viewport, 'left')) collapse();
        return true;
      }
      return false;
    },
  };
  return screen;
}
