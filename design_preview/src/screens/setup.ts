import { account } from '../core/account';
import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { net } from '../core/net';
import { notes } from '../core/notes';
import { FEATURES, featureOn, REGION_LABELS, setFeature, settings, type FeatureId, pickServices } from '../core/settings';
import { theme } from '../core/theme';
import { SERVICES } from '../data/services';
import { placeById } from '../data/places';
import { icon, type IconName } from '../ui/icons';
import { button } from '../ui/widgets';
import { accountPane } from './panes/account';
import { featureOffPane, featureSetUp, featuresPane, turnOn } from './panes/features';
import { notesPane } from './panes/notes';
import type { Pane, PaneHost } from './panes/pane';
import { regionPane } from './panes/region';
import { servicesPane, storesPane } from './panes/services';
import { weatherPane } from './panes/weather';
import type { SectionId } from './settings';

interface Step {
  id: SectionId;
  title: string;
  sub: () => string;
  /** The current value, from the same settings the pane changes. */
  value: () => Node | string;
  /** The pill while the row isn't done: the value is a guess the household can keep. */
  pill: () => string;
  pillKind: 'muted' | 'accent';
  help: { eyebrow: string; title: string; body: () => string; note: string };
}

/** "Netflix, Prime Video +6". */
const names = (all: string[], max = 2) => (all.length <= max ? all.join(', ') : `${all.slice(0, max).join(', ')} +${all.length - max}`);
const tickedServices = () => pickServices().filter((s) => settings.get().services.includes(s.id)).map((s) => s.name);
const tickedStores = () => settings.get().stores.map((id) => SERVICES[id].name);

const ALL_STEPS: Step[] = [
  {
    // F14 (the project lead, 2026-11-02): every feature starts on and leads into its setup (a tab under Features);
    // one whose setup isn't finished is switched off when First run ends (featureSetUp).
    id: 'features',
    title: 'Features',
    sub: () => `${FEATURES.filter((f) => featureOn(f.id)).length} of ${FEATURES.length} on`,
    value: () => names(FEATURES.filter((f) => featureOn(f.id)).map((f) => f.label)) || 'All off',
    pill: () => 'Suggested',
    pillKind: 'accent',
    help: {
      eyebrow: 'Features',
      title: 'Choose what this TV does.',
      body: () =>
        'Everything starts on: TMDB account (Movies, TV Shows, Search and Pick for me), Phone notes, Price lookup and Weather. Each is set up next; switch off what the household won’t use. One not set up when you finish is switched off.',
      note: 'Change them any time in Launcher settings › Features.',
    },
  },
  {
    // F12: the region comes before Streaming services and Stores, since it decides which of them show.
    id: 'region',
    title: 'Region',
    sub: () => 'Services, stores and prices',
    value: () => REGION_LABELS[settings.get().region],
    pill: () => 'Suggested',
    pillKind: 'accent',
    help: {
      eyebrow: 'Region',
      title: 'Where is this TV?',
      body: () => 'The streaming services and stores you tick next, and the currency of prices, are this country\u2019s: Sweden or the United States.',
      note: '\u2192 opens the same pane as Launcher settings \u203a Region.',
    },
  },
  {
    id: 'services',
    title: 'Streaming services',
    sub: () => `${tickedServices().length} ticked`,
    value: () => names(tickedServices()),
    pill: () => 'Suggested',
    pillKind: 'accent',
    help: {
      eyebrow: 'Streaming services',
      title: 'What does the household pay for?',
      body: () => `Titles on these services show as Included in Movies and TV Shows. We ticked the ${pickServices().filter((s) => s.installed && !s.more).length} with an app on this TV.`,
      note: '→ opens the same pane as Launcher settings › Streaming services.',
    },
  },
  {
    id: 'stores',
    title: 'Stores for rent and buy',
    sub: () => 'Where prices come from',
    // With Price lookup off the stores pick nothing: the row and its panel say so (the project lead, 2026-11-02).
    value: () => (featureOn('prices') ? names(tickedStores()) : ''),
    pill: () => (featureOn('prices') ? 'Suggested' : 'Price lookup off'),
    pillKind: 'accent',
    help: {
      eyebrow: 'Stores for rent and buy',
      title: 'Where may we show prices from?',
      body: () => `Rent and buy prices in ${settings.get().region === 'us' ? 'dollars' : 'kronor'} come from these stores, the cheapest first.`,
      note: '→ opens the same pane as Launcher settings › Stores for rent and buy.',
    },
  },
];

/** A setup the Features step opens for a feature turned on (F14): a sub-tab under Features while that feature is on. */
interface SubStep {
  id: PanelId;
  feature: FeatureId;
  title: string;
  icon: IconName;
  value: () => string;
}

/** F16's price keys page (Builder E) has no pane yet: a stub panel stands in for it. */
type PanelId = SectionId | 'keys';

const SUB_STEPS: SubStep[] = [
  { id: 'account', feature: 'tmdb', title: 'Sign in to TMDB', icon: 'user', value: () => account.value().text },
  { id: 'notes', feature: 'notes', title: 'Phone notes', icon: 'phone', value: () => notes.value().text },
  { id: 'keys', feature: 'prices', title: 'Price keys', icon: 'lock', value: () => 'Set' },
  { id: 'weather', feature: 'weather', title: 'Weather city', icon: 'cloud', value: () => placeById(settings.get().place).name },
];

/**
 * First run (V2 designs 01-setup B and r2-setup-checklist; F28, the project lead 2026-11-02): one page. The checklist
 * starts wide and focused, each row with its value and pill, and a help pane for the focused row on the right. → on a
 * row (or OK) opens that step's panel on the same page, focus in it: the checklist shrinks to a column of tabs on the
 * left and the panel takes the right, the same pane as Launcher settings with a footer ("Step 1 of 3", Next: … or
 * Done). While a panel is open, ↑ and ↓ on the tabs show each step's panel at once, → enters it, ← from the panel's
 * left edge returns to its tab, and Back on a tab closes the panel and widens the checklist again. A step counts as
 * done once its panel was entered.
 *
 * Features (F14, the project lead 2026-11-02): every feature starts on, with its setup as a tab under Features (Sign in
 * to TMDB, Phone notes, Price keys, Weather city); Next leads from Features through them, then on to Streaming services.
 * Turning a feature on opens its tab at once; off, the tab goes. When First run ends, any feature on whose setup isn't
 * finished (featureSetUp) is switched off, with a toast that says which.
 */
export function setupScreen(): Screen {
  const STEPS = ALL_STEPS;
  const done = new Set<SectionId>();
  /** The panel showing on the right; null while the checklist is wide. */
  let open: PanelId | null = null;
  let inPanel = false;

  const titleEl = h('h1.setup-title');
  const leadEl = h('div.setup-lead');
  const rowsEl = h('div.cl-rows');
  const help = h('div.panel.setup-help');
  const scroll = h('div.pane-scroll', { 'data-scroll': 'y', 'data-scroll-pad': 40 });
  const foot = h('div.pane-foot');
  const panel = h('div.set-pane.setup-panel', scroll, foot);

  const start = button('Start using Home', { icon: 'home', primary: true, key: 'start', onOk: () => app.goTab('home') });

  const subs = () => SUB_STEPS.filter((s) => featureOn(s.feature));
  const stepOf = (id: PanelId | null) => STEPS.find((s) => s.id === id);
  const subOf = (id: PanelId | null) => SUB_STEPS.find((s) => s.id === id);
  const tabOf = (id: PanelId) => rowsEl.querySelector<HTMLElement>(`[data-key="${id in SUB_KEYS ? 'sub' : 'step'}-${id}"]`);
  const entryOf = () =>
    scroll.querySelector<HTMLElement>('[data-entry]') ?? scroll.querySelector<HTMLElement>('[data-focus]') ?? foot.querySelector<HTMLElement>('[data-focus]');

  const host: PaneHost = {
    rerender: () => paintPanel(),
    focusKey(k) {
      const target = k ? panel.querySelector<HTMLElement>(`[data-key="${CSS.escape(k)}"]`) : entryOf();
      if (target) app.focus(target);
    },
    setup: true,
    // F14: a feature turned on in the Features step is set up next, on its own tab under Features.
    openSetup(id) {
      const sub = SUB_STEPS.find((s) => s.feature === id);
      if (!sub) return;
      paint();
      enter(sub.id);
    },
  };
  const panes: Record<Exclude<PanelId, 'keys'>, Pane> = {
    features: featuresPane(host),
    region: regionPane(),
    services: servicesPane(host),
    stores: storesPane(),
    account: accountPane(host),
    notes: notesPane(host),
    weather: weatherPane(host),
  } as Record<Exclude<PanelId, 'keys'>, Pane>;
  const paneOf = (id: PanelId) => (id === 'keys' ? null : panes[id]);
  /** The open step's pane while it shows (not Stores' off state with Price lookup off). */
  const livePane = () => (!open || (open === 'stores' && !featureOn('prices')) ? null : paneOf(open));

  /** The features on whose setup isn't finished: First run switches them off when it ends. */
  const unfinished = () => FEATURES.filter((f) => featureOn(f.id) && !featureSetUp(f.id));
  const paintHelp = (step: Step | null) => {
    const all = done.size === STEPS.length;
    const left = STEPS.length - done.size;
    const off = unfinished();
    replace(
      help,
      step
        ? [
            h('div.eyebrow', step.help.eyebrow),
            h('div.setup-help-title', step.help.title),
            h('div.setup-help-body', step.help.body()),
            h('div.setup-help-divider'),
            h('div.setup-help-note', done.size && !done.has(step.id) ? `${done.size} done. Start using Home at any time; the rest keep their defaults.` : step.help.note),
          ]
        : [
            h('div.eyebrow', all ? 'Ready' : 'Start'),
            h('div.setup-help-title', all ? 'Your Home screen is set up.' : 'Happy with the defaults?'),
            h(
              'div.setup-help-body',
              all
                ? `Movies and TV Shows show your lists, and titles on your ${settings.get().services.length} services are marked Included.`
                : `${left} row${left === 1 ? '' : 's'} still on ${left === 1 ? 'its' : 'their'} default. Start now and change any of it later.`,
            ),
            off.length
              ? h('div.setup-help-note.setup-help-off', `Not set up, so switched off when you start: ${off.map((f) => f.label).join(', ')}.`)
              : null,
            h('div.setup-help-divider'),
            h('div.setup-help-note.setup-help-key', h('span', { html: icon('settings', 24) }), 'The Settings icon → Launcher settings changes any of it.'),
          ],
    );
  };

  /** The panel for the open step: its pane, or F16's stub; and the footer to step on. */
  function paintPanel() {
    if (!open) return;
    const sub = subOf(open);
    const pane = paneOf(open);
    // Stores with Price lookup off: it says so, and Turn on switches it on and opens Price keys.
    const off = open === 'stores' && !featureOn('prices');
    replace(
      scroll,
      off
        ? featureOffPane('prices', 'Stores for rent and buy', () => {
            turnOn('prices');
            paint();
            enter('keys');
          })
        : pane
        ? pane.render()
        : [
            h('div.eyebrow', 'Prices'),
            h('div.pane-title', 'Price keys'),
            h('div.pane-sub', 'Scan a QR with your phone to enter the Watchmode and Movie of the Night keys; each is checked with a test call. Builder E’s F16 page goes here.'),
          ],
    );
    // Features leads through its setups, then on to the next step (the project lead, 2026-11-02).
    const order: PanelId[] = ['features', ...subs().map((s) => s.id), ...STEPS.slice(1).map((s) => s.id)];
    const after = order[order.indexOf(open) + 1];
    const nextLabel = (id: PanelId) => (id === 'stores' ? 'Stores' : (subOf(id)?.title ?? stepOf(id)!.title));
    if (sub) {
      replace(
        foot,
        h('span.pane-foot-note', `Set up with Features · ${featureSetUp(sub.feature) ? 'set up' : 'not set up yet'}`),
        h(
          'div.pane-foot-actions',
          button('Back to Features', { icon: 'back', small: true, key: 'foot-features', onOk: () => backToFeatures() }),
          button(`Next: ${nextLabel(after)}`, { icon: 'chevRight', small: true, primary: true, key: 'foot-next', onOk: () => enter(after) }),
        ),
      );
    } else {
      const i = STEPS.findIndex((s) => s.id === open);
      const next = after ? { id: after, title: nextLabel(after) } : undefined;
      replace(
        foot,
        h('span.pane-foot-note', `Step ${i + 1} of ${STEPS.length} · changes save at once`),
        h(
          'div.pane-foot-actions',
          button(next ? `Next: ${next.title}` : 'Done', {
            icon: next ? 'chevRight' : 'check',
            small: true,
            primary: true,
            key: 'foot-next',
            onOk: () => (next ? enter(next.id) : finish()),
          }),
        ),
      );
    }
    if (inPanel) app.refocus();
  }

  /** The tabs (wide: the checklist rows; open: the same rows, cut down, with the feature setups under Features). */
  function paint() {
    const all = done.size === STEPS.length;
    // A setup tab whose feature was just switched off goes; its panel falls back to Features.
    if (open && subOf(open) && !featureOn(subOf(open)!.feature)) open = 'features';
    el.classList.toggle('setup--open', !!open);
    replace(titleEl, all ? 'You’re all set' : 'Set up your Home screen');
    replace(leadEl, all ? 'Every row is done. Change any of them now, or start.' : 'Everything has a sensible default. Move right to open a row, or start right away.');
    const tabs: HTMLElement[] = [];
    STEPS.forEach((s, i) => {
      const isDone = done.has(s.id);
      tabs.push(
        focusable(
          h(
            `div.lrow.cl-row${open === s.id ? '.is-shown' : ''}`,
            isDone ? h('div.cl-num.is-done', { html: icon('check', 22) }) : h('div.cl-num', String(i + 1)),
            h('div.lr-main', h('div.lr-title', s.title), h('div.lr-sub', s.sub())),
            // A value that only repeats the pill (Features' All off) is left out while the pill shows.
            isDone || s.value() !== s.pill() ? h('div.lr-value', s.value()) : null,
            isDone ? null : h(`span.status.status--${s.id === 'stores' && !featureOn('prices') ? 'muted' : s.pillKind}`, s.pill()),
            h('span.lr-icon', { html: icon('chevRight', 26) }),
          ),
          { group: 'steps', key: `step-${s.id}`, isDefault: i === 0, onFocus: () => focusTab(s.id), onOk: () => enter(s.id) },
        ),
      );
      if (open && s.id === 'features') {
        for (const sub of subs()) {
          tabs.push(
            focusable(
              h(
                `div.lrow.cl-row.cl-sub${open === sub.id ? '.is-shown' : ''}`,
                // Set up: a green check in its icon's place, and its value; not yet: "Not set up yet".
                featureSetUp(sub.feature)
                  ? h('span.cl-sub-icon.is-done', { html: icon('check', 24) })
                  : h('span.cl-sub-icon', { html: icon(sub.icon, 24) }),
                h('div.lr-main', h('div.lr-title', sub.title), h('div.lr-sub', featureSetUp(sub.feature) ? sub.value() : 'Not set up yet')),
              ),
              { group: 'steps', key: `sub-${sub.id}`, onFocus: () => focusTab(sub.id), onOk: () => enter(sub.id) },
            ),
          );
        }
      }
    });
    replace(rowsEl, tabs);
    paintShown();
    if (app.top() === screen) app.refocus();
  }

  /** The open step's tab stays lit while its panel has focus; the panel's border brightens. */
  function paintShown() {
    panel.classList.toggle('is-live', inPanel);
    rowsEl.querySelectorAll<HTMLElement>('.cl-row').forEach((r) => {
      const id = r.dataset.key?.replace(/^(step|sub)-/, '');
      r.classList.toggle('is-active', inPanel && id === open);
    });
  }

  /** A tab got focus: wide, the help pane follows it; open, its panel shows at once (not entered, not done). */
  function focusTab(id: PanelId) {
    if (!open) return paintHelp(stepOf(id) ?? null);
    show(id);
  }

  function show(id: PanelId) {
    if (open === id) return;
    const wasOpen = !!open;
    open = id;
    scroll.scrollTop = 0;
    if (!wasOpen) paint();
    paintPanel();
  }

  /** → or OK on a tab, or Next: the panel opens (the checklist shrinks to tabs) and focus goes into it. */
  function enter(id: PanelId) {
    if (stepOf(id)) done.add(id as SectionId);
    const wasOpen = !!open;
    open = id;
    scroll.scrollTop = 0;
    inPanel = true;
    paint();
    paintPanel();
    if (!wasOpen) panel.classList.add('is-opening');
    const target = entryOf();
    if (target) app.focus(target);
  }

  /** Back on a tab: the panel closes and the checklist is wide again, focus on the row it was on. */
  function close(focusId?: PanelId) {
    const at = focusId ?? open;
    open = null;
    inPanel = false;
    panel.classList.remove('is-opening');
    paint();
    const row = at && stepOf(at) ? tabOf(at) : tabOf('features');
    if (row) app.focus(row);
  }

  /** Done on the last step: the checklist widens, focus on the first row not done, or Start. */
  function finish() {
    const next = STEPS.find((s) => !done.has(s.id));
    close();
    app.focus(next ? tabOf(next.id) : start);
  }

  /** A feature's setup closes: the Features panel again, on that feature's switch. */
  function backToFeatures() {
    const sub = subOf(open);
    enter('features');
    if (sub) host.focusKey(`seg-feat-${sub.feature}-on`);
  }

  const el = h(
    'div.screen.screen--setup',
    h(
      'div.setup-main',
      h('div.eyebrow', 'First run'),
      titleEl,
      leadEl,
      rowsEl,
      h('div.setup-start', start, h('span.setup-start-note', 'The same pages are in Launcher settings.')),
    ),
    help,
    panel,
  );

  const screen: Screen = {
    label: 'First run',
    el,
    defaultFocus: () => (inPanel ? entryOf() : null) ?? (open ? tabOf(open) : null) ?? rowsEl.querySelector<HTMLElement>('[data-focus]'),
    onShow: () => paint(),
    onFocus(f) {
      const was = inPanel;
      inPanel = panel.contains(f);
      if (was !== inPanel) paintShown();
      if (f === start && !open) paintHelp(null);
    },
    onText: (ch) => inPanel && !!livePane()?.onText?.(ch),
    onKey(key, f) {
      if (!f) return false;
      // Start sits alone on its line: ← and → stay on it, and ↓ doesn't slip into an open panel without entering it.
      if (f === start) return key === 'left' || key === 'right' || (key === 'down' && !!open) || (key === 'back' && !!open && (close(), true));
      if (rowsEl.contains(f)) {
        const id = f.dataset.key!.replace(/^(step|sub)-/, '') as PanelId;
        if (key === 'right') return enter(id), true;
        if (key === 'left') return true;
        if (key === 'back' && open) return close(id), true;
        return false;
      }
      if (!open || !panel.contains(f)) return false;
      if (livePane()?.onKey?.(key, f)) return true;
      const tab = () => app.focus(tabOf(open!));
      if (key === 'back') {
        if (!livePane()?.back?.()) tab();
        return true;
      }
      if (key === 'left') {
        if (!app.moveWithin(panel, 'left')) tab();
        return true;
      }
      if (key === 'up' || key === 'down' || key === 'right') {
        app.moveWithin(panel, key);
        return true;
      }
      return false;
    },
  };

  // Live values: ticks, the sign-in, the network, the theme, Phone notes. The countdown ticks in place.
  const repaint = () => {
    paint();
    paintPanel();
  };
  const offs = [
    settings.on(repaint),
    account.on(repaint),
    net.on(repaint),
    theme.on(repaint),
    notes.listen(repaint),
    account.onTick(() => {
      const c = scroll.querySelector('[data-countdown]');
      if (c) c.textContent = account.countdown();
      scroll.querySelector<HTMLElement>('[data-bar]')?.style.setProperty('width', `${Math.round(account.fraction() * 100)}%`);
    }),
  ];
  // Leaving First run stops listening, and stops a sign-in code still waiting.
  const offStack = app.onChange(() => {
    if (app.has(screen)) return;
    offs.forEach((off) => off());
    offStack();
    // First run has ended (Start, Back, Home): a feature on without its setup finished is switched off, so nothing
    // shows half set up (the project lead, 2026-11-02).
    const off = unfinished();
    for (const f of off) setFeature(f.id, false);
    if (off.length) app.toast(`Not set up, so switched off: ${off.map((f) => f.label).join(', ')}. Turn on any time on its page.`);
    if (account.get() === 'getting' || account.get() === 'waiting') account.cancel();
  });

  paint();
  paintHelp(STEPS[0]);
  return screen;
}

/** The ids that are feature setups (their tabs' keys are "sub-…"). */
const SUB_KEYS: Record<string, true> = Object.fromEntries(SUB_STEPS.map((s) => [s.id, true]));