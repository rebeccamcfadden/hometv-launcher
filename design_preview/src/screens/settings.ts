import { account } from '../core/account';
import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { net } from '../core/net';
import { theme } from '../core/theme';
import { featureOn, settings, START_LABELS, type FeatureId, REGION_LABELS } from '../core/settings';
import { placeById } from '../data/places';
import { icon, type IconName } from '../ui/icons';
import { accountPane } from './panes/account';
import { notesPane } from './panes/notes';
import { notes } from '../core/notes';
import { appsPane } from './panes/apps';
import { aboutPane, homePane, performancePane, startPane, tvPane } from './panes/misc';
import { featureOffPane, featuresPane, featuresValue, SETUP_SECTION, turnOn } from './panes/features';
import type { Pane, PaneHost } from './panes/pane';
import { regionPane } from './panes/region';
import { servicesPane, storesPane } from './panes/services';
import { themePane } from './panes/theme';
import { weatherPane } from './panes/weather';
import { keys } from '../core/keys';
import { keysPane } from './panes/keys';
import { updates } from '../core/updates';

/** "All on" while every Performance choice draws and moves everything, "Default" for the usual start (all on but
 * Animated details, F25), "Light mode" at the lightest, else "Custom". */
function performanceValue(): string {
  const s = settings.get();
  const full = s.motion === 'full' && s.cardShadows === 'on' && s.focusGlow && s.backdropPicture === 'photo';
  if (full) return s.movingDetails ? 'All on' : 'Default';
  const light = s.motion === 'off' && s.cardShadows === 'off' && !s.focusGlow && s.backdropPicture === 'plain' && !s.movingDetails;
  return light ? 'Light mode' : 'Custom';
}
export type SectionId =
  | 'account'
  | 'notes'
  | 'services'
  | 'stores'
  | 'keys'
  | 'weather'
  | 'apps'
  | 'theme'
  | 'performance'
  | 'features'
  | 'start'
  | 'region'
  | 'home'
  | 'tv'
  | 'about';

interface Section {
  id: SectionId;
  label: string;
  icon: IconName;
  /** The current value, in grey at the row's right; amber while the sign-in is under way. */
  value: () => { text: string; warn?: boolean };
  /** F14: while this feature is off, the section reads Off and its pane says how to turn it on. */
  feature?: FeatureId;
}

const SECTIONS: Section[] = [
  { id: 'account', label: 'Account', icon: 'user', value: () => account.value(), feature: 'tmdb' },
  { id: 'notes', label: 'Phone notes', icon: 'phone', value: () => notes.value(), feature: 'notes' },
  { id: 'services', label: 'Streaming services', icon: 'tv', value: () => ({ text: `${settings.get().services.length} ticked` }) },
  // The project lead, 2026-11-02: with Price lookup off, Stores says so (F16's price keys page will too).
  { id: 'stores', label: 'Stores for rent and buy', icon: 'cart', value: () => ({ text: `${settings.get().stores.length} ticked` }), feature: 'prices' },
  // F16: the household's own keys, from a phone or a laptop. Not behind Price lookup as a whole: the TMDB key is the
  // catalogue's; only the price keys wait behind an empty state while Price lookup is off.
  {
    id: 'keys',
    label: 'API keys',
    icon: 'key',
    value: () => keys.value(featureOn('prices') ? undefined : ['tmdb']),
  },
  {
    id: 'weather',
    label: 'Weather',
    icon: 'cloud',
    value: () => ({ text: placeById(settings.get().place).name }),
    feature: 'weather',
  },
  { id: 'apps', label: 'Home row apps', icon: 'apps', value: () => ({ text: String(settings.get().homeApps.length) }) },
  { id: 'theme', label: 'Theme', icon: 'palette', value: () => ({ text: theme.label() }) },
  { id: 'performance', label: 'Performance', icon: 'gauge', value: () => ({ text: performanceValue() }) },
  { id: 'features', label: 'Features', icon: 'list', value: () => ({ text: featuresValue() }) },
  { id: 'start', label: 'Open on start', icon: 'power', value: () => ({ text: START_LABELS[settings.get().openOnStart] }) },
  { id: 'region', label: 'Region', icon: 'pin', value: () => ({ text: REGION_LABELS[settings.get().region] }) },
  { id: 'home', label: 'Default Home', icon: 'home', value: () => ({ text: 'On' }) },
  { id: 'tv', label: 'TV settings', icon: 'settings', value: () => ({ text: '' }) },
  { id: 'about', label: 'About', icon: 'info', value: () => updates.value() },
];

/**
 * F23 (the project lead chose grouping A, 2026-11-01): the sections in four groups, under small headings, in this
 * order. Headings only: every section keeps its place in the path (Launcher settings › Phone notes), so nothing that
 * names one changes. Features (F14) is under What you watch, Region (F12) under This TV before TV settings; still to
 * come: API keys (F16) under What you watch. First run (setup.ts) shows its own steps as tabs.
 */
const GROUPS: { title: string; items: SectionId[] }[] = [
  { title: 'Accounts', items: ['account', 'notes'] },
  { title: 'What you watch', items: ['services', 'stores', 'keys', 'features'] },
  { title: 'Home & look', items: ['theme', 'performance', 'apps', 'weather'] },
  { title: 'This TV', items: ['start', 'home', 'region', 'tv', 'about'] },
];

/**
 * Launcher settings (V2 design 05-settings A with the r2 panes): sections on the left with their values; the pane on
 * the right shows the focused section at once, like the title preview's tabs. → or OK enters the pane (its border
 * brightens), ← from its left edge returns to the section. Back in the pane leaves a sub-state first (Choose lists,
 * Change city, an app being moved), then the pane; Back on the list closes the page. Changes save at once.
 *
 * First run (setup.ts) shows the same panes on its own page, as tabs (F28).
 */
/**
 * opts.backCloses: opened for one setup from a page's empty state (Turn on, Sign in): Back in the pane returns straight to
 * that page, as the project lead asked (2026-11-02), rather than to the section list first.
 */
export function launcherSettings(initial: SectionId = 'account', opts: { enter?: boolean; backCloses?: boolean } = {}): Screen {
  const groups = GROUPS;
  const byId = (id: SectionId) => SECTIONS.find((s) => s.id === id)!;
  const sections = GROUPS.flatMap((g) => g.items.map(byId));
  /** F14 (the project lead, 2026-11-01): a section whose feature is off stays, says so, and offers Turn on. */
  const isOff = (s: Section) => !!s.feature && !featureOn(s.feature);
  let current: SectionId = sections.some((s) => s.id === initial) ? initial : sections[0].id;
  /** The section showing has its own pane (its feature is on). */
  const live = () => !isOff(sections.find((s) => s.id === current)!);
  let shown: SectionId | null = null;
  let inPane = false;

  const list = h(`div.sec-list${groups ? '.sec-list--grouped' : ''}`, { 'data-scroll': 'y', 'data-scroll-pad': groups ? 56 : 0 });
  const scroll = h('div.pane-scroll', { 'data-scroll': 'y', 'data-scroll-pad': 40 });
  const pane = h('div.set-pane', scroll);

  const entryOf = () => scroll.querySelector<HTMLElement>('[data-entry]') ?? scroll.querySelector<HTMLElement>('[data-focus]');

  const host: PaneHost = {
    rerender: () => rerender(),
    focusKey(k) {
      const target = k ? pane.querySelector<HTMLElement>(`[data-key="${CSS.escape(k)}"]`) : entryOf();
      if (target) app.focus(target);
    },
    setup: false,
  };
  const panes: Record<SectionId, Pane> = {
    account: accountPane(host),
    notes: notesPane(host),
    services: servicesPane(host),
    stores: storesPane(),
    keys: keysPane(host),
    weather: weatherPane(host),
    apps: appsPane(host),
    theme: themePane(),
    performance: performancePane(host),
    features: featuresPane(host),
    start: startPane(),
    region: regionPane(),
    home: homePane(),
    tv: tvPane(),
    about: aboutPane(host),
  };

  const rows = sections.map((sec) =>
    focusable(
      h(
        'div.sec',
        { 'data-section': sec.id },
        h('span.sec-icon', { html: icon(sec.icon, 28) }),
        h('span.sec-label', sec.label),
        h('span.sec-value'),
      ),
      { group: 'sections', key: `sec-${sec.id}`, isDefault: sec.id === current, onFocus: () => show(sec.id), onOk: () => enter(sec.id) },
    ),
  );
  replace(
    list,
    groups
      ? groups.map((g) => [
          h('div.sec-head', g.title),
          g.items.map((id) => rows[sections.findIndex((s) => s.id === id)]),
        ])
      : rows,
  );
  const rowOf = (id: SectionId) => rows[sections.findIndex((s) => s.id === id)];

  function paintValues() {
    sections.forEach((sec, i) => {
      const { text, warn } = isOff(sec) ? { text: 'Off', warn: false } : sec.value();
      const v = rows[i].querySelector<HTMLElement>('.sec-value')!;
      v.textContent = text;
      v.classList.toggle('is-warn', !!warn);
    });
  }

  function rerender() {
    const sec = sections.find((s) => s.id === current)!;
    replace(scroll, isOff(sec) ? featureOffPane(sec.feature!, sec.label, () => turnOnHere(sec)) : panes[current].render());
    paintValues();
    if (inPane) app.refocus();
  }

  /** Turn on, in a switched-off section: the feature on, then straight into its setup (this section, once live). */
  function turnOnHere(sec: Section) {
    const first = turnOn(sec.feature!);
    shown = null;
    enter(SETUP_SECTION[first] ?? sec.id);
  }

  function show(id: SectionId) {
    current = id;
    if (shown === id) return;
    shown = id;
    scroll.scrollTop = 0;
    rerender();
  }

  function enter(id: SectionId) {
    if (shown === id) current = id;
    show(id);
    const target = entryOf();
    if (target) app.focus(target);
  }

  show(current);

  const el = h(
    'div.screen.screen--base.settings',
    h(
      'div.set-head',
      h('h1.page-title', 'Launcher settings'),
      h('span.set-hint', 'Back closes · changes save at once'),
    ),
    list,
    pane,
  );

  const screen: Screen = {
    label: 'Launcher settings',
    pillAtTop: true,
    el,
    defaultFocus: () => (inPane ? entryOf() : null) ?? rowOf(current),
    onShow: () => rerender(),
    // While the pane has focus, its section stays lit and the pane's border brightens.
    onFocus(f) {
      inPane = pane.contains(f);
      pane.classList.toggle('is-live', inPane);
      rows.forEach((r) => r.classList.toggle('is-active', inPane && r.dataset.section === current));
    },
    onText: (ch) => live() && !!panes[current].onText?.(ch),
    onKey(key, f) {
      if (!f) return false;
      if (list.contains(f)) {
        if (key === 'right') {
          enter(current);
          return true;
        }
        if (key === 'left') return true;
        if (key === 'up' || key === 'down') {
          app.moveWithin(list, key);
          return true;
        }
        return false;
      }
      if (live() && panes[current].onKey?.(key, f)) return true;
      if (key === 'back') {
        if (live() && panes[current].back?.()) return true;
        if (opts.backCloses) app.pop();
        else app.focus(rowOf(current));
        return true;
      }
      if (key === 'left') {
        if (!app.moveWithin(pane, 'left')) app.focus(rowOf(current));
        return true;
      }
      if (key === 'up' || key === 'down' || key === 'right') {
        app.moveWithin(pane, key);
        return true;
      }
      return false;
    },
  };

  // Live values: ticks, the sign-in, the network. The countdown ticks in place, without rebuilding the pane.
  const offs = [
    settings.on(() => rerender()),
    account.on(() => rerender()),
    // F16: a key sent from the phone or laptop page, and its check.
    keys.on(() => rerender()),
    net.on(() => rerender()),
    // A new period (the dev bar's date, or midnight) changes Seasonal's label and swatches.
    theme.on(() => rerender()),
    // Phone notes' sends and imports change the pane and its section value.
    notes.listen(() => rerender()),
    // Family install step 5: an update's check, download and install show in About as they happen.
    updates.on(() => rerender()),
    account.onTick(() => {
      const c = scroll.querySelector('[data-countdown]');
      if (c) c.textContent = account.countdown();
      scroll.querySelector<HTMLElement>('[data-bar]')?.style.setProperty('width', `${Math.round(account.fraction() * 100)}%`);
    }),
  ];
  // Leaving the page stops listening, and stops a code still waiting: it is polled only while settings is open.
  const offStack = app.onChange(() => {
    if (app.has(screen)) return;
    offs.forEach((off) => off());
    offStack();
    if (account.get() === 'getting' || account.get() === 'waiting') account.cancel();
  });

  // Opened from a quick-settings row: straight into that section's pane.
  if (opts.enter) queueMicrotask(() => app.top() === screen && enter(current));
  return screen;
}
