import { account } from '../core/account';
import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { net } from '../core/net';
import { theme } from '../core/theme';
import { settings, START_LABELS } from '../core/settings';
import { placeById } from '../data/places';
import { icon, type IconName } from '../ui/icons';
import { button } from '../ui/widgets';
import { accountPane } from './panes/account';
import { notesPane } from './panes/notes';
import { notes } from '../core/notes';
import { appsPane } from './panes/apps';
import { aboutPane, homePane, performancePane, startPane, tvPane } from './panes/misc';
import type { Pane, PaneHost } from './panes/pane';
import { servicesPane, storesPane } from './panes/services';
import { themePane } from './panes/theme';
import { weatherPane } from './panes/weather';

/** "All on" while every Performance choice draws and moves everything, "Default" for the usual start (all on but
 * Animated details, F25), "Light mode" at the lightest, else "Custom". */
function performanceValue(): string {
  const s = settings.get();
  const full = s.motion === 'full' && s.cardShadows === 'on' && s.focusGlow && s.backdropPicture === 'photo';
  if (full) return s.movingDetails ? 'All on' : 'Default';
  const light = s.motion === 'off' && s.cardShadows === 'off' && !s.focusGlow && s.backdropPicture === 'plain' && !s.movingDetails;
  return light ? 'Light mode' : 'Custom';
}
export type SectionId = 'account' | 'notes' | 'services' | 'stores' | 'weather' | 'apps' | 'theme' | 'performance' | 'start' | 'home' | 'tv' | 'about';

interface Section {
  id: SectionId;
  label: string;
  /** First run's wording for the same section ("Sign in to TMDB"). */
  setupLabel?: string;
  icon: IconName;
  /** The current value, in grey at the row's right; amber while the sign-in is under way. */
  value: () => { text: string; warn?: boolean };
}

const SECTIONS: Section[] = [
  { id: 'account', label: 'Account', setupLabel: 'Sign in to TMDB', icon: 'user', value: () => account.value() },
  { id: 'notes', label: 'Phone notes', icon: 'phone', value: () => notes.value() },
  { id: 'services', label: 'Streaming services', icon: 'tv', value: () => ({ text: `${settings.get().services.length} ticked` }) },
  { id: 'stores', label: 'Stores for rent and buy', icon: 'cart', value: () => ({ text: `${settings.get().stores.length} ticked` }) },
  { id: 'weather', label: 'Weather', setupLabel: 'Weather city', icon: 'cloud', value: () => ({ text: placeById(settings.get().place).name }) },
  { id: 'apps', label: 'Home row apps', icon: 'apps', value: () => ({ text: String(settings.get().homeApps.length) }) },
  { id: 'theme', label: 'Theme', icon: 'palette', value: () => ({ text: theme.label() }) },
  { id: 'performance', label: 'Performance', icon: 'gauge', value: () => ({ text: performanceValue() }) },
  { id: 'start', label: 'Open on start', icon: 'power', value: () => ({ text: START_LABELS[settings.get().openOnStart] }) },
  { id: 'home', label: 'Default Home', icon: 'home', value: () => ({ text: 'On' }) },
  { id: 'tv', label: 'TV settings', icon: 'settings', value: () => ({ text: '' }) },
  { id: 'about', label: 'About', icon: 'info', value: () => ({ text: '2.0.0' }) },
];

/** First run's cut-down split (V2 design r2-setup-checklist): the four setup sections and a footer to step through. */
export interface SetupOpts {
  steps: SectionId[];
  /** A section's pane was entered: the checklist ticks its row. */
  onVisit(id: SectionId): void;
}

/**
 * Launcher settings (V2 design 05-settings A with the r2 panes): sections on the left with their values; the pane on
 * the right shows the focused section at once, like the title preview's tabs. → or OK enters the pane (its border
 * brightens), ← from its left edge returns to the section. Back in the pane leaves a sub-state first (Choose lists,
 * Change city, an app being moved), then the pane; Back on the list closes the page. Changes save at once.
 *
 * First run opens the same page cut down to its four sections, with a footer: "Back to checklist" and "Next: …".
 */
export function launcherSettings(initial: SectionId = 'account', opts: { enter?: boolean; setup?: SetupOpts } = {}): Screen {
  const setup = opts.setup;
  const sections = setup ? SECTIONS.filter((s) => setup.steps.includes(s.id)) : SECTIONS;
  let current: SectionId = sections.some((s) => s.id === initial) ? initial : sections[0].id;
  let shown: SectionId | null = null;
  let inPane = false;

  const list = h('div.sec-list', { 'data-scroll': 'y', 'data-scroll-pad': 0 });
  const scroll = h('div.pane-scroll', { 'data-scroll': 'y', 'data-scroll-pad': 40 });
  const foot = h('div.pane-foot');
  const pane = h('div.set-pane', scroll, setup ? foot : null);

  const entryOf = () =>
    scroll.querySelector<HTMLElement>('[data-entry]') ?? scroll.querySelector<HTMLElement>('[data-focus]') ?? foot.querySelector<HTMLElement>('[data-focus]');

  const host: PaneHost = {
    rerender: () => rerender(),
    focusKey(k) {
      const target = k ? pane.querySelector<HTMLElement>(`[data-key="${CSS.escape(k)}"]`) : entryOf();
      if (target) app.focus(target);
    },
    setup: !!setup,
  };
  const panes: Record<SectionId, Pane> = {
    account: accountPane(host),
    notes: notesPane(host),
    services: servicesPane(host),
    stores: storesPane(),
    weather: weatherPane(host),
    apps: appsPane(host),
    theme: themePane(),
    performance: performancePane(host),
    start: startPane(),
    home: homePane(),
    tv: tvPane(),
    about: aboutPane(),
  };

  const rows = sections.map((sec) =>
    focusable(
      h(
        'div.sec',
        { 'data-section': sec.id },
        h('span.sec-icon', { html: icon(sec.icon, 28) }),
        h('span.sec-label', setup ? (sec.setupLabel ?? sec.label) : sec.label),
        h('span.sec-value'),
      ),
      { group: 'sections', key: `sec-${sec.id}`, isDefault: sec.id === current, onFocus: () => show(sec.id), onOk: () => enter(sec.id) },
    ),
  );
  replace(list, rows);
  const rowOf = (id: SectionId) => rows[sections.findIndex((s) => s.id === id)];

  function paintValues() {
    sections.forEach((sec, i) => {
      let { text, warn } = sec.value();
      if (setup && sec.id === 'account' && text === 'Not signed in') text = 'Optional';
      const v = rows[i].querySelector<HTMLElement>('.sec-value')!;
      v.textContent = text;
      v.classList.toggle('is-warn', !!warn);
    });
  }

  function paintFoot() {
    if (!setup) return;
    const i = setup.steps.indexOf(current);
    const next = sections.find((s) => s.id === setup.steps[i + 1]);
    replace(
      foot,
      h('span.pane-foot-note', `Step ${i + 1} of ${setup.steps.length} · changes save at once`),
      h(
        'div.pane-foot-actions',
        button('Back to checklist', { icon: 'back', small: true, key: 'foot-back', onOk: () => app.pop() }),
        button(next ? `Next: ${next.id === 'stores' ? 'Stores' : (next.setupLabel ?? next.label)}` : 'Done', {
          icon: next ? 'chevRight' : 'check',
          small: true,
          primary: true,
          key: 'foot-next',
          onOk: () => (next ? enter(next.id) : app.pop()),
        }),
      ),
    );
  }

  function rerender() {
    replace(scroll, panes[current].render());
    paintFoot();
    paintValues();
    if (inPane) app.refocus();
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
    setup?.onVisit(id);
    const target = entryOf();
    if (target) app.focus(target);
  }

  show(current);

  const el = h(
    `div.screen.screen--base.settings${setup ? '.settings--setup' : ''}`,
    h(
      'div.set-head',
      h('h1.page-title', setup ? 'Set up your Home screen' : 'Launcher settings'),
      h('span.set-hint', setup ? 'First run · Back returns to the checklist' : 'Back closes · changes save at once'),
    ),
    list,
    pane,
  );

  const screen: Screen = {
    label: setup ? 'First run · Settings' : 'Launcher settings',
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
    onText: (ch) => !!panes[current].onText?.(ch),
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
      if (panes[current].onKey?.(key, f)) return true;
      if (key === 'back') {
        if (!panes[current].back?.()) app.focus(rowOf(current));
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
    net.on(() => rerender()),
    // A new period (the dev bar's date, or midnight) changes Seasonal's label and swatches.
    theme.on(() => rerender()),
    // Phone notes' sends and imports change the pane and its section value.
    notes.listen(() => rerender()),
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
