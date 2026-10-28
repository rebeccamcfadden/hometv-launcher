import { app, type TabId } from '../core/app';
import { h, imgUrl, initials } from '../core/dom';
import { focusable, type FocusOpts } from '../core/focus';
import { LIST_NAMES } from '../data/catalog';
import { bestOffer, isIncludedOffer, offerLabel, price, runtimeLabel } from '../data/offers';
import { SERVICES } from '../data/services';
import type { Title } from '../types';
import { icon, type IconName } from './icons';
import { inputsMenu, topBarButton, wifiMenu } from '../screens/dropdown';

export function navBar(active: TabId | null): HTMLElement {
  const tabs: [TabId, string][] = [
    ['home', 'Home'],
    ['movies', 'Movies'],
    ['tv', 'TV Shows'],
    ['music', 'Music'],
  ];
  // OK (or a click) on a section switches to it; focusing it only highlights it. OK on the page you are
  // already on does nothing, so pressing it on a pushed page (e.g. Pick for me) never resets that page.
  let tabsRoot: HTMLElement | null = null;
  const section = (id: TabId, label: string | null) => {
    const el = h(`div.${label ? 'nav-tab' : 'nav-search'}${id === active ? '.is-active' : ''}`, label ?? { html: icon('search') });
    const open = () => {
      if (el.classList.contains('is-active')) return;
      const from = app.top();
      app.goTab(id, { focusNav: true });
      // Coming back to the page we left should put focus on its own section, not on the one we switched to.
      const own = tabsRoot?.querySelector<HTMLElement>('.is-active');
      if (from && own) {
        from.lastFocus = own;
        from.lastFocusKey = own.dataset.key;
        from.memory?.set('nav', own);
      }
    };
    return focusable(el, { group: 'nav', key: `nav-${id}`, isDefault: id === active, onOk: open });
  };
  const tabEls = tabs.map(([id, label]) => section(id, label));
  const searchEl = section('search', null);
  const device = (
    [
      ['wifi', () => app.push(wifiMenu(topBarButton('dev-wifi')))],
      ['input', () => app.push(inputsMenu(topBarButton('dev-input')))],
      // As in the base launcher: OK on Settings opens the TV's own settings. The remote's Settings key
      // (S in the preview) opens the quick-settings dropdown instead.
      ['settings', () => app.toast('Opening TV system settings…')],
    ] as [IconName, () => void][]
  ).map(([ic, onOk]) => focusable(h('div.nav-icon', { html: icon(ic, 28) }), { group: 'device', key: `dev-${ic}`, onOk }));
  tabsRoot = h('div.nav-left', h('div.nav-tabs', tabEls), searchEl);
  return h('div.nav', tabsRoot, h('div.nav-device', device));
}

export function button(
  label: string,
  opts: FocusOpts & { icon?: IconName; primary?: boolean; small?: boolean; iconOnly?: boolean; on?: boolean } = {},
): HTMLElement {
  const cls = [
    'btn',
    opts.primary && 'btn--primary',
    opts.small && 'btn--small',
    opts.iconOnly && 'btn--icon',
    opts.on && 'is-on',
  ]
    .filter(Boolean)
    .join('.');
  const el = h(
    `div.${cls}`,
    opts.icon ? h('span.btn-icon', { html: icon(opts.icon, opts.small ? 22 : 26, opts.icon === 'play' || (opts.on && opts.icon === 'heart')) }) : null,
    h('span.btn-label', label),
  );
  return focusable(el, opts);
}

export function chip(label: string, opts: FocusOpts & { selected?: boolean; check?: boolean } = {}): HTMLElement {
  const el = h(
    `div.chip${opts.selected ? '.is-selected' : ''}`,
    opts.check && opts.selected ? h('span.chip-check', { html: icon('check', 20) }) : null,
    label,
  );
  return focusable(el, opts);
}

export const guide = (left: string, right: string) => h('div.guide', h('span', left), h('span', right));

export function titleMeta(t: Title): string {
  if (t.kind === 'movie') return `${runtimeLabel(t.runtime ?? 0)} · ${t.rating} · ${t.score.toFixed(1)}/10`;
  const n = t.seasons?.length ?? 0;
  return `${n} season${n === 1 ? '' : 's'} · ${t.rating} · ${t.score.toFixed(1)}/10`;
}

export function provenance(t: Title): string {
  const list = t.lists[0];
  return `${list ? LIST_NAMES[list] : 'Catalogue'} · ${t.genres[0]}`;
}

/** The library card from the V1 Movies and TV Shows screens. */
export function libraryCard(t: Title, opts: FocusOpts): HTMLElement {
  const o = bestOffer(t);
  const avail = !o ? '' : isIncludedOffer(o) ? (o.type === 'free' ? 'Free' : 'Included') : `${o.type === 'rent' ? 'Rent' : 'Buy'} ${price(o.price ?? 0)}`;
  return focusable(
    h(
      'div.card.card--library',
      h('div.card-art', h('img', { src: imgUrl(t.art), alt: '' })),
      h(
        'div.card-row',
        h('div.card-name', t.name),
        h('div.card-provider', h('div', o ? SERVICES[o.service].name : ''), h('div.card-avail', avail)),
      ),
      h('div.card-meta', titleMeta(t)),
      h('div.card-sub', provenance(t)),
    ),
    opts,
  );
}

/** The smaller card used by Search and the title preview's rows. */
export function smallCard(t: Title, opts: FocusOpts & { name?: Node | string } = {}): HTMLElement {
  return focusable(
    h(
      'div.card.card--small',
      h('div.card-art', h('img', { src: imgUrl(t.art), alt: '' })),
      h('div.card-name', opts.name ?? t.name),
      h('div.card-meta', `${t.kind === 'movie' ? 'Movie' : 'TV show'} · ${t.year}`),
      h('div.card-sub', offerLabel(bestOffer(t))),
    ),
    opts,
  );
}

export function personCard(name: string, role: string, opts: FocusOpts & { label?: Node | string } = {}): HTMLElement {
  return focusable(
    h('div.person', h('div.person-portrait', initials(name)), h('div.person-name', opts.label ?? name), h('div.person-role', role)),
    opts,
  );
}

export function rowOf(label: string, items: HTMLElement[]): HTMLElement {
  return h('div.row', h('div.row-label', label), h('div.row-items', { 'data-scroll': 'x', 'data-scroll-pad': 0 }, items));
}
