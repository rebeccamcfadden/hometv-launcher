import './styles/tokens.css';
import './styles/themes.css';
import './styles/base.css';
import './styles/screens.css';
import { ACCOUNT_STATES, account, type AccountState } from './core/account';
import { app } from './core/app';
import { DATA_STATES, net, type DataState } from './core/net';
import { settings } from './core/settings';
import { settingsMenu } from './screens/quickSettings';
import { store } from './core/store';
import { PHASE_LABELS, PHASES, type Phase } from './core/daylight';
import { setFall, type FallChoice } from './core/weatherfx';
import { isThemeId, parseDate, PERIOD_DATES, PERIOD_LABELS, periodAt, theme, THEMES } from './core/theme';
import { getTitle } from './data/catalog';
import { homeScreen } from './screens/home';
import { quickSettings, topBarButton } from './screens/dropdown';
import { libraryScreen } from './screens/library';
import { pickSetup } from './screens/pick';
import { titlePreview } from './screens/preview';
import { searchScreen } from './screens/search';
import { launcherSettings } from './screens/settings';
import { setupScreen } from './screens/setup';
import { titleMenu } from './screens/titleMenu';
import { notes } from './core/notes';
import { importPill } from './ui/importPill';

// Launcher settings' Motion: the stage drops slides and scale-ups (Reduced) or every animation (Off).
const stage = document.getElementById('stage')!;
const applyMotion = () => (stage.dataset.motion = settings.get().motion);
applyMotion();
settings.on(applyMotion);

// ?state=<data state> starts in a loading, offline or error state (see the State menu in the dev bar).
const params = new URLSearchParams(location.search);
const startState = params.get('state') as DataState | null;
if (startState && DATA_STATES.some(([id]) => id === startState)) net.set(startState);

// The theme on <html>: ?theme=<id> picks one as Launcher settings › Theme does, ?date=2026-10-31 sets the date Seasonal
// themes follow (see the Theme and Date menus in the dev bar).
const startTheme = params.get('theme');
if (isThemeId(startTheme)) settings.set({ theme: startTheme });
const startDate = parseDate(params.get('date'));
if (startDate) theme.setDate(startDate);
// ?phase=night puts a Seasonal picture on a part of the day (the Time menu in the dev bar).
const startPhase = params.get('phase') as Phase | null;
if (startPhase && PHASES.includes(startPhase)) theme.setPhase(startPhase);
theme.init();

app.init(stage, {
  home: homeScreen,
  movies: () => libraryScreen('movie'),
  tv: () => libraryScreen('show'),
  search: searchScreen,
});
app.overlay(importPill());
// Holding OK on a title card, wherever one shows, opens the title menu (F3).
app.longOk = titleMenu;
// ?notes=on starts with Launcher settings › Phone notes switched on.
if (params.get('notes') === 'on') notes.setOn(true);

// ---- Dev bar under the stage: the screen stack, the data state, quick jumps and a state reset ----
const trail = document.getElementById('trail')!;
app.onChange(() => (trail.textContent = app.trail.join('  ›  ')));
trail.textContent = app.trail.join('  ›  ');

const stateSelect = document.getElementById('state') as HTMLSelectElement;
for (const [id, label] of DATA_STATES) stateSelect.append(new Option(`State: ${label}`, id));
stateSelect.value = net.get();
stateSelect.addEventListener('change', () => {
  net.set(stateSelect.value as DataState);
  stateSelect.blur();
});

// The TMDB sign-in, beside the data state: the steps a real sign-in goes through, to review each in the Account pane.
const accountSelect = document.getElementById('account') as HTMLSelectElement;
for (const [id, label] of ACCOUNT_STATES) accountSelect.append(new Option(`Account: ${label}`, id));
const showAccount = () => (accountSelect.value = account.get());
showAccount();
account.on(showAccount);
accountSelect.addEventListener('change', () => {
  account.devSet(accountSelect.value as AccountState);
  accountSelect.blur();
});
const startAccount = params.get('account') as AccountState | null;
if (startAccount && ACCOUNT_STATES.some(([id]) => id === startAccount)) account.devSet(startAccount);

// Themes, beside them: switch the theme, and put the preview on a date in each Seasonal period.
const themeSelect = document.getElementById('theme') as HTMLSelectElement;
for (const t of THEMES) themeSelect.append(new Option(`Theme: ${t.group === 'seasonal' ? `Seasonal · ${t.name}` : t.name}`, t.id));
const dateSelect = document.getElementById('date') as HTMLSelectElement;
const phaseSelect = document.getElementById('phase') as HTMLSelectElement;
// F25: what falls while Home is idle: the real weather at the place (the season offline), or forced.
const fallSelect = document.getElementById('fall') as HTMLSelectElement;
for (const [v, l] of [['auto', 'Weather: the real weather'], ['off', 'Weather: none'], ['snow', 'Weather: snow'], ['rain', 'Weather: rain']] as const)
  fallSelect.append(new Option(l, v));
fallSelect.addEventListener('change', () => {
  setFall(fallSelect.value as FallChoice);
  fallSelect.blur();
});
const startFall = params.get('fall');
if (startFall === 'snow' || startFall === 'rain' || startFall === 'off') {
  setFall(startFall);
  fallSelect.value = startFall;
}
phaseSelect.append(new Option('Time: now (follows the sun)', ''));
for (const p of PHASES) phaseSelect.append(new Option(`Time: ${PHASE_LABELS[p]}`, p));
const year = new Date().getFullYear();
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
dateSelect.append(new Option(`Date: today (${PERIOD_LABELS[periodAt(new Date())]})`, ''));
for (const [p, md] of PERIOD_DATES) dateSelect.append(new Option(`Date: ${PERIOD_LABELS[p]} (${year}-${md})`, `${year}-${md}`));
if (startDate && ![...dateSelect.options].some((o) => o.value === iso(startDate)))
  dateSelect.append(new Option(`Date: ${iso(startDate)} (${PERIOD_LABELS[periodAt(startDate)]})`, iso(startDate)));
const showTheme = () => {
  themeSelect.value = theme.current().id;
  dateSelect.value = theme.isDateOverridden() ? iso(theme.date()) : '';
  phaseSelect.value = theme.isPhaseOverridden() ? theme.phase() : '';
};
showTheme();
theme.on(showTheme);
themeSelect.addEventListener('change', () => {
  if (isThemeId(themeSelect.value)) settings.set({ theme: themeSelect.value });
  themeSelect.blur();
});
dateSelect.addEventListener('change', () => {
  theme.setDate(parseDate(dateSelect.value));
  dateSelect.blur();
});
phaseSelect.addEventListener('change', () => {
  theme.setPhase((phaseSelect.value || null) as Phase | null);
  phaseSelect.blur();
});

const preview = (tab: 'movies' | 'tv', id: string) => () => {
  app.goTab(tab);
  app.push(titlePreview(getTitle(id)));
};

/** Jump targets; the id also works as ?jump=<id> in the URL. */
const jumps: [string, string, () => void][] = [
  ['home', 'Home', () => app.goTab('home')],
  ['movies', 'Movies', () => app.goTab('movies')],
  ['tv', 'TV Shows', () => app.goTab('tv')],
  ['search', 'Search', () => app.goTab('search')],
  [
    'pick',
    'Pick for me (Movies)',
    () => {
      app.goTab('movies');
      app.push(pickSetup('movie'));
    },
  ],
  ['arrival', 'Title preview · Arrival', preview('movies', 'arrival')],
  ['moon', 'Title preview · Moon (rent only)', preview('movies', 'moon')],
  ['interstellar', 'Title preview · Interstellar (a store with no price)', preview('movies', 'interstellar')],
  ['severance', 'Title preview · Severance (TV)', preview('tv', 'severance')],
  ['daredevil', 'Title preview · Daredevil (seasons to buy)', preview('tv', 'daredevil')],
  [
    'settings',
    'Launcher settings',
    () => {
      app.goTab('home');
      app.push(launcherSettings());
    },
  ],
  ...(['account', 'notes', 'services', 'stores', 'weather', 'apps', 'theme', 'performance'] as const).map(
    (id): [string, string, () => void] => [
      `settings-${id}`,
      `Launcher settings · ${{ account: 'Account', notes: 'Phone notes', services: 'Streaming services', stores: 'Stores', weather: 'Weather', apps: 'Home row apps', theme: 'Theme', performance: 'Performance' }[id]}`,
      () => {
        app.goTab('home');
        app.push(launcherSettings(id, { enter: true }));
      },
    ],
  ),
  [
    'notes-send',
    'Phone notes · a phone sends a note (3 not matched)',
    () => {
      notes.setOn(true);
      notes.simulate('Rebecca', 12, [
        {
          line: 'Inglorious basterds',
          top: [
            { title: 'Inglourious Basterds', year: 2009, kind: 'Movie' },
            { title: 'Basterds Reunion', year: 2020, kind: 'Movie' },
            { title: 'The Basterds', year: 2015, kind: 'TV show' },
          ],
        },
        { line: 'Dead poet society', ticked: true, top: [{ title: 'Dead Poets Society', year: 1989, kind: 'Movie' }] },
        { line: 'that one with the dog', top: [] },
      ]);
    },
  ],
  [
    'notes-send-ok',
    'Phone notes · a phone sends a note (all matched)',
    () => {
      notes.setOn(true);
      notes.simulate('Partner', 8, []);
    },
  ],
  [
    'quick',
    'Settings icon (quick settings)',
    () => {
      app.goTab('home', { focusNav: true });
      app.focus(topBarButton('dev-settings'));
      app.push(settingsMenu(topBarButton('dev-settings')));
    },
  ],
  [
    'setup',
    'First run (setup)',
    () => {
      app.goTab('home');
      app.push(setupScreen());
    },
  ],
];
const jump = document.getElementById('jump') as HTMLSelectElement;
for (const [id, label] of jumps) jump.append(new Option(label, id));
jump.addEventListener('change', () => {
  jumps.find(([id]) => id === jump.value)?.[2]();
  jump.value = '';
  jump.blur();
});
jumps.find(([id]) => id === params.get('jump'))?.[2]();

document.getElementById('reset')!.addEventListener('click', () => {
  settings.clear();
  store.reset();
});

app.remoteKeys = {
  h: () => app.goTab('home', { focusNav: true }),
  // The remote's Settings key: opens the quick-settings dropdown, and closes it when pressed again.
  s: () => {
    if (app.top()?.label === 'Quick settings') return app.pop();
    app.push(quickSettings(topBarButton('dev-settings'), () => app.push(launcherSettings())));
  },
};

// Handy from the browser console while working on flows: app.trail, net.set('offline').
if (import.meta.env.DEV) Object.assign(window, { app, net });
