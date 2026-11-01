import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { lists } from '../core/lists';
import { net, SAVED_AT } from '../core/net';
import { TITLES } from '../data/catalog';
import { subscribedCount } from '../data/offers';
import type { Kind, Title } from '../types';
import { icon } from '../ui/icons';
import { button, chip, guide, libraryCard, libraryCardSkeleton, navBar } from '../ui/widgets';
import { activeCount, applyFilters, defaultFilters, filterDefs, summary } from './filterDefs';
import { filtersDrawer } from './filters';
import { pickSetup } from './pick';
import { titlePreview } from './preview';

/** 'all', or a list id from {@link lists}. */
type ListChoice = string;

const inList = (t: Title, l: ListChoice) => (l === 'all' ? lists.saved(t) : (lists.byId(l)?.has(t) ?? false));
const listName = (l: ListChoice) => (l === 'all' ? 'All lists' : (lists.byId(l)?.name ?? 'All lists'));

/** V1 Movies / TV Shows: imported watchlists only, no Explore rows. */
export function libraryScreen(kind: Kind): Screen {
  const noun = kind === 'movie' ? 'movies' : 'shows';
  const heading = kind === 'movie' ? 'Movies' : 'TV Shows';
  const defs = filterDefs(kind);
  const filters = defaultFilters();
  let list: ListChoice = 'all';

  const pool = () => TITLES.filter((t) => t.kind === kind && inList(t, 'all'));

  const chipsEl = h('div.chips', { 'data-scroll': 'x', 'data-scroll-pad': 48 });
  const scopeEl = h('span.lib-scope-slot');
  const guideEl = guide('Illustrative services, scores & availability', 'OK for title details');
  /** Try again on the offline page is checking: its label says so for a moment. */
  let retrying = false;
  const filterSummary = h('span.lib-filter-summary');
  const filterBtnSlot = h('span');
  const listTitle = h('div.lib-list-title');
  const contextEl = h('div.page-context');
  const listStatus = h('div.lib-list-status');
  const grid = h('div.lib-grid');

  const render = () => {
    const state = net.get();
    // Offline with nothing saved yet: no lists to count or show (06-states A).
    const empty = state === 'offline-empty';
    const all = empty ? [] : pool();
    // A list hidden or gone since (Choose lists, a sign-out) falls back to All lists.
    if (list !== 'all' && !lists.byId(list)) list = 'all';
    const choices: ListChoice[] = ['all', ...lists.all().map((l) => l.id)];
    replace(
      chipsEl,
      h('span.chips-label', 'Imported watchlists'),
      choices.map((c) =>
        chip(`${listName(c)}${empty ? '' : ` · ${all.filter((t) => inList(t, c)).length}`}`, {
          selected: c === list,
          group: 'lists',
          key: `list-${c}`,
          isDefault: c === list,
          onOk: () => {
            list = c;
            render();
            app.refocus();
          },
        }),
      ),
    );

    const n = activeCount(defs, filters);
    replace(filterSummary, summary(defs, filters) || 'No filters');
    replace(
      filterBtnSlot,
      button(n ? `Filters · ${n}` : 'Filters', {
        icon: 'filter',
        small: true,
        key: 'filters',
        onOk: () => app.push(filtersDrawer(kind, filters, render)),
      }),
    );

    const inChosen = all.filter((t) => inList(t, list));
    const shown = applyFilters(defs, filters, inChosen);
    replace(listTitle, listName(list));
    const count = shown.length === inChosen.length ? `${shown.length} ${noun}` : `${shown.length} of ${inChosen.length} ${noun}`;
    replace(
      listStatus,
      state === 'loading'
        ? 'Loading artwork and details…'
        : empty
          ? ''
          : state === 'offline'
            ? `${count} · prices may be out of date`
            : shown.length === inChosen.length
              ? `${count} · Across your services`
              : `${count} · Filtered`,
    );
    // Offline, the scope line becomes the amber pill: with the time of the saved copy, or just "Offline".
    replace(
      scopeEl,
      net.isOffline()
        ? h('span.offline-pill', h('span', { html: icon('wifiOff', 24) }), empty ? 'Offline' : `Offline · showing your lists as saved ${SAVED_AT}`)
        : h('span.lib-scope-text', `${lists.watchlists()} imported watchlist${lists.watchlists() === 1 ? '' : 's'} · All ${subscribedCount()} services`),
    );
    replace(contextEl, `Your collection · ${lists.names()}`);
    replace(guideEl.lastElementChild!, empty ? '' : 'OK for title details');

    if (empty) {
      // Focus lands on Wi-Fi settings, something real (06-states A, frame 3).
      replace(
        grid,
        h(
          'div.state',
          h('div.state-icon', { html: icon('wifiOff', 48) }),
          h('div.empty-title', 'You’re offline, and nothing is saved yet'),
          h('div.empty-note.state-note', `${heading} needs the internet once to fetch your lists. Apps, inputs and the clock on Home work without it.`),
          h(
            'div.state-actions',
            button('Wi-Fi settings', { icon: 'wifi', key: 'wifi-settings', isDefault: true, onOk: () => app.toast('Opening Wi-Fi settings…') }),
            button(retrying ? 'Trying…' : 'Try again', {
              icon: 'retry',
              key: 'try-again',
              onOk: () => {
                if (retrying) return;
                retrying = true;
                render();
                window.setTimeout(() => {
                  retrying = false;
                  if (net.get() !== 'offline-empty') return;
                  render();
                  app.toast('Still offline. Check the Wi-Fi.');
                }, 1200);
              },
            }),
          ),
        ),
      );
    } else if (state === 'loading') {
      // The page's shape at once: skeleton cards, none focusable, so focus waits on the list chips.
      replace(grid, Array.from({ length: 8 }, libraryCardSkeleton));
    } else if (!inChosen.length) {
      replace(
        grid,
        h(
          'div.empty',
          h('div.empty-title', `Nothing on ${list === 'all' ? 'your lists' : listName(list)} yet`),
          h('div.empty-note', 'Add titles with + My list in any title preview, or import a watchlist in Settings.'),
        ),
      );
    } else if (!shown.length) {
      replace(
        grid,
        h(
          'div.empty',
          h('div.empty-title', `Your filters hide all ${inChosen.length} ${noun}`),
          h('div.empty-note', summary(defs, filters)),
          button('Clear filters', {
            small: true,
            key: 'clear-filters',
            onOk: () => {
              for (const d of defs) filters[d.id] = 0;
              render();
              app.refocus();
            },
          }),
        ),
      );
    } else {
      replace(
        grid,
        shown.map((t, i) => libraryCard(t, { group: 'grid', key: `t-${t.id}`, isDefault: i === 0, onOk: () => app.push(titlePreview(t)) })),
      );
    }
    app.refocus();
  };

  const pickBtn = button('Pick for me', { icon: 'shuffle', primary: true, key: 'pick', onOk: () => app.push(pickSetup(kind)) });

  const page = h(
    'div.page-scroll',
    { 'data-scroll': 'y', 'data-scroll-pad': 70 },
    h(
      'div.lib-intro',
      h('div', h('h1.page-title', heading), contextEl),
      pickBtn,
    ),
    h(
      'div.lib-scope',
      scopeEl,
      h('span.lib-filters', filterSummary, filterBtnSlot),
    ),
    chipsEl,
    h('div.lib-list-head', listTitle, listStatus),
    grid,
    h('div.page-end'),
  );

  const el = h(
    'div.screen.screen--base.library',
    navBar(kind === 'movie' ? 'movies' : 'tv'),
    page,
    h('div.bottom-fade'),
    guideEl,
  );

  render();
  net.on(() => {
    render();
    app.refocus();
  });

  return {
    label: heading,
    el,
    defaultFocus: () =>
      net.get() === 'loading'
        ? chipsEl.querySelector<HTMLElement>('.chip.is-selected')
        : (grid.querySelector<HTMLElement>('[data-default]') ?? grid.querySelector<HTMLElement>('[data-focus]') ?? pickBtn),
    onShow: render,
    // Back on the first chip, the row returns to its start so the label shows again.
    onFocus(el) {
      if (el.dataset.key === 'list-all') chipsEl.scrollLeft = 0;
    },
    // The list chips lead to Pick for me: Up from any chip, and Right from the last one, before the nav or the grid.
    onKey(key, f) {
      if (!f) return false;
      // Offline with nothing saved: ← and → stay on Wi-Fi settings and Try again rather than jump up to the chips.
      const actions = f.closest('.state-actions');
      if (actions && (key === 'left' || key === 'right')) {
        app.moveWithin(actions, key);
        return true;
      }
      if (!chipsEl.contains(f)) return false;
      const all = [...chipsEl.querySelectorAll<HTMLElement>('[data-focus]')];
      if (key === 'up' || (key === 'right' && all[all.length - 1] === f)) {
        app.focus(pickBtn);
        return true;
      }
      return false;
    },
  };
}
