import { account, USERNAME } from '../core/account';
import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { settings, UNIT_LABELS } from '../core/settings';
import { placeById } from '../data/places';
import { PICK_SERVICES, SERVICES } from '../data/services';
import { icon } from '../ui/icons';
import { button } from '../ui/widgets';
import { launcherSettings, type SectionId } from './settings';

interface Step {
  id: SectionId;
  title: string;
  sub: () => string;
  /** The current value, from the same settings the pane changes. */
  value: () => Node | string;
  /** The pill while the row isn't done: the value is a guess the household can keep. */
  pill: string;
  pillKind: 'muted' | 'accent';
  help: { eyebrow: string; title: string; body: () => string; note: string };
}

/** "Netflix, Prime Video +6". */
const names = (all: string[], max = 2) => (all.length <= max ? all.join(', ') : `${all.slice(0, max).join(', ')} +${all.length - max}`);
const tickedServices = () => PICK_SERVICES.filter((s) => settings.get().services.includes(s.id)).map((s) => s.name);
const tickedStores = () => settings.get().stores.map((id) => SERVICES[id].name);

const STEPS: Step[] = [
  {
    id: 'account',
    title: 'Sign in to TMDB',
    sub: () => (account.get() === 'in' ? 'Signed in · 3 lists imported' : 'Brings My list, Seen and Favorites from your account'),
    value: () => (account.get() === 'in' ? h('span.cl-user', h('span.avatar.avatar--s', USERNAME[0].toUpperCase()), USERNAME) : ''),
    pill: 'Optional',
    pillKind: 'muted',
    help: {
      eyebrow: 'Sign in to TMDB',
      title: 'Your watchlists, on the TV.',
      body: () => 'TMDB is a free film and TV database. Sign in once with your phone and My list, Seen and Favorites stay in step with your account.',
      note: 'Skip it and your lists are kept on this TV only. You can sign in later from Launcher settings.',
    },
  },
  {
    id: 'services',
    title: 'Streaming services',
    sub: () => `${tickedServices().length} ticked`,
    value: () => names(tickedServices()),
    pill: 'Suggested',
    pillKind: 'accent',
    help: {
      eyebrow: 'Streaming services',
      title: 'What does the household pay for?',
      body: () => `Titles on these services show as Included in Movies and TV Shows. We ticked the ${PICK_SERVICES.filter((s) => s.installed && !s.more).length} with an app on this TV.`,
      note: 'OK opens the same page as Launcher settings › Streaming services.',
    },
  },
  {
    id: 'stores',
    title: 'Stores for rent and buy',
    sub: () => 'Where prices come from',
    value: () => names(tickedStores()),
    pill: 'Suggested',
    pillKind: 'accent',
    help: {
      eyebrow: 'Stores for rent and buy',
      title: 'Where may we show prices from?',
      body: () => 'Rent and buy prices in kronor come from these stores, the cheapest first.',
      note: 'OK opens the same page as Launcher settings › Stores for rent and buy.',
    },
  },
  {
    id: 'weather',
    title: 'Weather city',
    sub: () => 'For the forecast on Home',
    value: () => `${placeById(settings.get().place).name} · ${settings.get().unit === 'tv' ? 'TV default' : UNIT_LABELS[settings.get().unit]}`,
    pill: 'Default',
    pillKind: 'accent',
    help: {
      eyebrow: 'Weather city',
      title: 'The forecast on Home.',
      body: () => 'Home shows today’s and tomorrow’s weather, and an hourly strip below the apps. The city starts as the build’s default.',
      note: 'Weather data by Open-Meteo.com.',
    },
  },
];

/**
 * First run (V2 designs 01-setup B and r2-setup-checklist): one checklist page with every setup item and its current
 * value, a help pane for the focused row, and Start using Home always one press away. OK on a row opens the same
 * split and panes as Launcher settings, cut down to these four sections, with "Back to checklist" and "Next: …".
 * Coming back ticks the rows that were opened and puts focus on the next one not done.
 */
export function setupScreen(): Screen {
  const done = new Set<SectionId>();
  const titleEl = h('h1.setup-title');
  const leadEl = h('div.setup-lead');
  const rowsEl = h('div.cl-rows');
  const help = h('div.panel.setup-help');

  const start = button('Start using Home', { icon: 'home', primary: true, key: 'start', onOk: () => app.goTab('home') });

  const paintHelp = (step: Step | null) => {
    const all = done.size === STEPS.length;
    const left = STEPS.length - done.size;
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
            h('div.setup-help-divider'),
            h('div.setup-help-note.setup-help-key', h('span', { html: icon('settings', 24) }), 'The Settings icon → Launcher settings changes any of it.'),
          ],
    );
  };

  const paint = () => {
    const all = done.size === STEPS.length;
    replace(titleEl, all ? 'You’re all set' : 'Set up your Home screen');
    replace(leadEl, all ? 'Every row is done. Change any of them now, or start.' : 'Everything has a sensible default. Open a row to change it, or start right away.');
    replace(
      rowsEl,
      STEPS.map((s, i) => {
        const isDone = done.has(s.id);
        return focusable(
          h(
            'div.lrow.cl-row',
            isDone ? h('div.cl-num.is-done', { html: icon('check', 22) }) : h('div.cl-num', String(i + 1)),
            h('div.lr-main', h('div.lr-title', s.title), h('div.lr-sub', s.sub())),
            h('div.lr-value', s.value()),
            isDone ? null : h(`span.status.status--${s.pillKind}`, s.pill),
            h('span.lr-icon', { html: icon('chevRight', 26) }),
          ),
          {
            group: 'steps',
            key: `step-${s.id}`,
            isDefault: i === 0,
            onFocus: () => paintHelp(s),
            onOk: () => {
              done.add(s.id);
              app.push(launcherSettings(s.id, { enter: true, setup: { steps: STEPS.map((x) => x.id), onVisit: (id) => done.add(id) } }));
            },
          },
        );
      }),
    );
  };
  paint();
  paintHelp(STEPS[0]);

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
  );

  let visited = false;
  const screen: Screen = {
    label: 'First run',
    el,
    defaultFocus: () => rowsEl.querySelector<HTMLElement>('[data-focus]'),
    // Back from the split: the opened rows are ticked, and focus goes to the next row not done (or Start).
    onShow() {
      paint();
      if (!visited) return void (visited = true);
      const next = STEPS.find((s) => !done.has(s.id));
      const target = next ? rowsEl.querySelector<HTMLElement>(`[data-key="step-${next.id}"]`) : start;
      screen.lastFocus = target;
      screen.lastFocusKey = target?.dataset.key;
    },
    onFocus: (f) => {
      if (f === start) paintHelp(null);
    },
    // Start sits alone on its line: ← and → stay on it rather than jump up into a row.
    onKey: (key, f) => f === start && (key === 'left' || key === 'right'),
  };
  return screen;
}
