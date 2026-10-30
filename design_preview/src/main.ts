import './styles/tokens.css';
import './styles/base.css';
import './styles/screens.css';
import { ACCOUNT_STATES, account, type AccountState } from './core/account';
import { app } from './core/app';
import { imgUrl } from './core/dom';
import { DATA_STATES, net, type DataState } from './core/net';
import { settings } from './core/settings';
import { settingsMenu } from './screens/quickSettings';
import { store } from './core/store';
import { getTitle } from './data/catalog';
import { homeScreen } from './screens/home';
import { quickSettings, topBarButton } from './screens/dropdown';
import { libraryScreen } from './screens/library';
import { pickSetup } from './screens/pick';
import { placeholder } from './screens/placeholder';
import { titlePreview } from './screens/preview';
import { searchScreen } from './screens/search';
import { launcherSettings } from './screens/settings';
import { setupScreen } from './screens/setup';

document.documentElement.style.setProperty('--bg-image', `url("${imgUrl('bg-evening.jpg')}")`);

// Launcher settings' Motion: the stage drops slides and scale-ups (Reduced) or every animation (Off).
const stage = document.getElementById('stage')!;
const applyMotion = () => (stage.dataset.motion = settings.get().motion);
applyMotion();
settings.on(applyMotion);

// ?state=<data state> starts in a loading, offline or error state (see the State menu in the dev bar).
const params = new URLSearchParams(location.search);
const startState = params.get('state') as DataState | null;
if (startState && DATA_STATES.some(([id]) => id === startState)) net.set(startState);

app.init(stage, {
  home: homeScreen,
  movies: () => libraryScreen('movie'),
  tv: () => libraryScreen('show'),
  music: () => placeholder('Music', 'Spotify, YouTube Music and your playlists. Not part of V1.', { tab: 'music' }),
  search: searchScreen,
});

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
  ...(['account', 'services', 'stores', 'weather', 'apps'] as const).map(
    (id): [string, string, () => void] => [
      `settings-${id}`,
      `Launcher settings · ${{ account: 'Account', services: 'Streaming services', stores: 'Stores', weather: 'Weather', apps: 'Home row apps' }[id]}`,
      () => {
        app.goTab('home');
        app.push(launcherSettings(id, { enter: true }));
      },
    ],
  ),
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
