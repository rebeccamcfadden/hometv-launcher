import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { store } from '../core/store';
import { LIST_NAMES, TITLES } from '../data/catalog';
import { SUBSCRIBED_COUNT } from '../data/services';
import type { Kind, ListId, Title } from '../types';
import { button, chip, guide, libraryCard, navBar } from '../ui/widgets';
import { activeCount, applyFilters, defaultFilters, filterDefs, summary } from './filterDefs';
import { filtersDrawer } from './filters';
import { pickSetup } from './pick';
import { titlePreview } from './preview';

type ListChoice = 'all' | ListId;

const inList = (t: Title, l: ListChoice) =>
  l === 'all' ? t.lists.length > 0 || store.inMyList(t.id) : l === 'my' ? store.inMyList(t.id) : t.lists.includes(l);

/** V1 Movies / TV Shows: imported watchlists only, no Explore rows. */
export function libraryScreen(kind: Kind): Screen {
  const noun = kind === 'movie' ? 'movies' : 'shows';
  const heading = kind === 'movie' ? 'Movies' : 'TV Shows';
  const defs = filterDefs(kind);
  const filters = defaultFilters();
  let list: ListChoice = 'all';

  const pool = () => TITLES.filter((t) => t.kind === kind && inList(t, 'all'));

  const chipsEl = h('div.chips');
  const filterSummary = h('span.lib-filter-summary');
  const filterBtnSlot = h('span');
  const listTitle = h('div.lib-list-title');
  const listStatus = h('div.lib-list-status');
  const grid = h('div.lib-grid');

  const render = () => {
    const all = pool();
    const choices: ListChoice[] = ['all', 'my', 'household', 'friends'];
    replace(
      chipsEl,
      h('span.chips-label', 'Imported watchlists'),
      choices.map((c) =>
        chip(`${c === 'all' ? 'All lists' : LIST_NAMES[c]} · ${all.filter((t) => inList(t, c)).length}`, {
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
    replace(listTitle, list === 'all' ? 'All lists' : LIST_NAMES[list]);
    replace(
      listStatus,
      shown.length === inChosen.length ? `${shown.length} ${noun} · Across your services` : `${shown.length} of ${inChosen.length} ${noun} · Filtered`,
    );

    if (!inChosen.length) {
      replace(
        grid,
        h(
          'div.empty',
          h('div.empty-title', `Nothing on ${list === 'all' ? 'your lists' : LIST_NAMES[list]} yet`),
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
      h('div', h('h1.page-title', heading), h('div.page-context', 'Your collection · My list, Household & Friends')),
      pickBtn,
    ),
    h(
      'div.lib-scope',
      h('span.lib-scope-text', `3 imported watchlists · All ${SUBSCRIBED_COUNT} services`),
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
    guide('Illustrative services, scores & availability', 'OK for title details'),
  );

  render();

  return {
    label: heading,
    el,
    defaultFocus: () => grid.querySelector<HTMLElement>('[data-focus]') ?? pickBtn,
    onShow: render,
    // The list chips lead to Pick for me: Up from any chip, and Right from the last one, before the nav or the grid.
    onKey(key, f) {
      if (!f || !chipsEl.contains(f)) return false;
      const all = [...chipsEl.querySelectorAll<HTMLElement>('[data-focus]')];
      if (key === 'up' || (key === 'right' && all[all.length - 1] === f)) {
        app.focus(pickBtn);
        return true;
      }
      return false;
    },
  };
}
