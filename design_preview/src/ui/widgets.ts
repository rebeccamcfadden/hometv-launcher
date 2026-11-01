import { app, type TabId } from '../core/app';
import { h, imgUrl, initials } from '../core/dom';
import { focusable, type FocusOpts } from '../core/focus';
import { lists } from '../core/lists';
import { net } from '../core/net';
import { availability, cardLine, cardOffer, runtimeLabel } from '../data/offers';
import { SERVICES } from '../data/services';
import type { Title } from '../types';
import { icon, type IconName } from './icons';
import { inputsMenu, topBarButton, wifiMenu } from '../screens/dropdown';
import { settingsMenu } from '../screens/quickSettings';

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
      // V2: OK on Settings opens the quick-settings dropdown (final-quick-settings). The remote's Settings key keeps
      // the TV maker's own panel (S in the preview shows the base launcher's stand-in).
      ['settings', () => app.push(settingsMenu(topBarButton('dev-settings')))],
    ] as [IconName, () => void][]
  ).map(([ic, onOk]) => focusable(h('div.nav-icon', { html: icon(ic, 28) }), { group: 'device', key: `dev-${ic}`, onOk }));
  // Offline (06-states A): the Wi-Fi icon is crossed out on every page.
  const wifi = device[0];
  const paintWifi = () => (wifi.innerHTML = icon(net.isOffline() ? 'wifiOff' : 'wifi', 28));
  paintWifi();
  // Each page builds its own nav; a nav that left the document stops listening.
  const stop = net.on(() => (wifi.isConnected ? paintWifi() : stop()));
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

/**
 * A segmented control (two or three options in a pill): moving only highlights an option, OK selects it. The selected
 * one has the accent ring and is where focus lands when the control is entered.
 */
export function segmented<T extends string>(opts: {
  options: [T, string][];
  get: () => T;
  set: (v: T) => void;
  group: string;
  small?: boolean;
  /** Marks the selected option as its pane's first focus. */
  entry?: boolean;
}): HTMLElement {
  const els = opts.options.map(([v, label]) => {
    const on = v === opts.get();
    const el = focusable(h(`div.seg-opt${on ? '.is-selected' : ''}`, label), {
      group: opts.group,
      key: `${opts.group}-${v}`,
      isDefault: on,
      onOk: () => {
        opts.set(v);
        els.forEach((e, i) => e.classList.toggle('is-selected', opts.options[i][0] === v));
      },
    });
    if (on && opts.entry) el.dataset.entry = '';
    return el;
  });
  return h(`div.seg${opts.small ? '.seg--small' : ''}`, els);
}

export function titleMeta(t: Title): string {
  if (t.kind === 'movie') return `${runtimeLabel(t.runtime ?? 0)} · ${t.rating} · ${t.score.toFixed(1)}/10`;
  const n = t.seasons?.length ?? 0;
  return `${n} season${n === 1 ? '' : 's'} · ${t.rating} · ${t.score.toFixed(1)}/10`;
}

export function provenance(t: Title): string {
  return `${lists.provenance(t)} · ${t.genres[0]}`;
}

/** The library card from the V1 Movies and TV Shows screens; its provider is the household's own ({@link cardOffer}). */
export function libraryCard(t: Title, opts: FocusOpts): HTMLElement {
  const o = cardOffer(t);
  return focusable(
    h(
      'div.card.card--library',
      h('div.card-art', h('img', { src: imgUrl(t.art), alt: '' })),
      h(
        'div.card-row',
        h('div.card-name', t.name),
        h('div.card-provider', h('div', o ? SERVICES[o.service].name : ''), h('div.card-avail', availability(o))),
      ),
      h('div.card-meta', titleMeta(t)),
      h('div.card-sub', provenance(t)),
    ),
    opts,
  );
}

/** A library card's shape while its artwork and details load (06-states A): not focusable. */
export const libraryCardSkeleton = () =>
  h(
    'div.card.card--library.card--skeleton',
    h('div.card-art.sk-art'),
    h('div.card-row', h('div.sk', { style: 'width:190px;height:26px' }), h('div.sk', { style: 'width:90px;height:20px' })),
    h('div.sk', { style: 'width:250px;height:18px;margin-top:10px' }),
    h('div.sk', { style: 'width:150px;height:18px;margin-top:8px' }),
  );

/** The smaller card used by Search and the title preview's rows. */
export function smallCard(t: Title, opts: FocusOpts & { name?: Node | string } = {}): HTMLElement {
  return focusable(
    h(
      'div.card.card--small',
      h('div.card-art', h('img', { src: imgUrl(t.art), alt: '' })),
      h('div.card-name', opts.name ?? t.name),
      h('div.card-meta', `${t.kind === 'movie' ? 'Movie' : 'TV show'} · ${t.year}`),
      h('div.card-sub', cardLine(t)),
    ),
    opts,
  );
}

/** A small card's shape while it loads. */
export const smallCardSkeleton = () =>
  h(
    'div.card.card--small.card--skeleton',
    h('div.card-art.sk-art'),
    h('div.sk', { style: 'width:190px;height:24px;margin-top:12px' }),
    h('div.sk', { style: 'width:130px;height:18px;margin-top:8px' }),
    h('div.sk', { style: 'width:210px;height:18px;margin-top:6px' }),
  );

export function personCard(name: string, role: string, opts: FocusOpts & { label?: Node | string } = {}): HTMLElement {
  return focusable(
    h('div.person', h('div.person-portrait', initials(name)), h('div.person-name', opts.label ?? name), h('div.person-role', role)),
    opts,
  );
}

export function rowOf(label: string, items: HTMLElement[]): HTMLElement {
  return h('div.row', h('div.row-label', label), h('div.row-items', { 'data-scroll': 'x', 'data-scroll-pad': 0 }, items));
}
