import { app, type Screen } from '../core/app';
import { h, initials } from '../core/dom';
import { titlesForPerson } from '../core/fuzzy';
import { button, smallCard } from '../ui/widgets';
import { titlePreview } from './preview';

/** Stub person page: enough to follow the flow from Cast & crew or a People search result. */
export function personScreen(name: string): Screen {
  const titles = titlesForPerson(name);
  const back = button('Back', { icon: 'back', small: true, key: 'back', onOk: () => app.back() });
  const el = h(
    'div.screen.screen--base.person-page',
    h(
      'div.person-head',
      back,
      h('div.person-hero', h('div.person-portrait.person-portrait--big', initials(name)), h('div', h('div.eyebrow', 'Person · not designed yet'), h('h1.page-title', name))),
    ),
    h('div.row-label.person-known', `Known for · ${titles.length} title${titles.length === 1 ? '' : 's'} in the catalogue`),
    h(
      'div.person-grid',
      titles.map((t, i) => smallCard(t, { group: 'known', key: `p-${t.id}`, isDefault: i === 0, onOk: () => app.push(titlePreview(t)) })),
    ),
  );
  return { label: name, el, defaultFocus: () => el.querySelector<HTMLElement>('.person-grid [data-focus]') ?? back };
}
