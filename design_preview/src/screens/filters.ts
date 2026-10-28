import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import type { Kind } from '../types';
import { icon } from '../ui/icons';
import { button } from '../ui/widgets';
import { filterDefs, type FilterState } from './filterDefs';

/** Side drawer. Changes apply live to the page behind it; Back or Done closes it. */
export function filtersDrawer(kind: Kind, state: FilterState, onChange: () => void): Screen {
  const defs = filterDefs(kind);
  const rows = defs.map((d) => {
    const value = h('span.setting-value');
    const render = () => replace(value, d.options[state[d.id] ?? 0]);
    render();
    const cycle = (step: number) => {
      const n = d.options.length;
      state[d.id] = ((state[d.id] ?? 0) + step + n) % n;
      render();
      onChange();
    };
    const row = focusable(
      h(
        'div.setting',
        h('span.setting-label', d.label),
        h('span.setting-control', h('span.setting-arrow', { html: icon('chevLeft', 22) }), value, h('span.setting-arrow', { html: icon('chevRight', 22) })),
      ),
      { group: 'settings', key: `f-${d.id}`, onOk: () => cycle(1) },
    );
    (row as HTMLElement & { cycle?: (s: number) => void }).cycle = cycle;
    return { row, render };
  });

  const clear = button('Clear all', {
    small: true,
    key: 'f-clear',
    onOk: () => {
      for (const d of defs) state[d.id] = 0;
      rows.forEach((r) => r.render());
      onChange();
    },
  });
  const done = button('Done', { small: true, primary: true, key: 'f-done', onOk: () => app.back() });

  const el = h(
    'div.screen.layer-dialog',
    h('div.scrim.scrim--light'),
    h(
      'div.drawer',
      h('div.drawer-title', 'Filters'),
      h('div.drawer-note', `${kind === 'movie' ? 'Movies' : 'TV Shows'} · ← → changes a value`),
      h('div.drawer-rows', rows.map((r) => r.row)),
      h('div.drawer-actions', clear, done),
    ),
  );

  return {
    label: 'Filters',
    el,
    layer: 'dialog',
    defaultFocus: () => rows[0].row,
    onKey(key, f) {
      const cycle = (f as (HTMLElement & { cycle?: (s: number) => void }) | null)?.cycle;
      if (cycle && (key === 'left' || key === 'right')) {
        cycle(key === 'left' ? -1 : 1);
        return true;
      }
      return false;
    },
  };
}
