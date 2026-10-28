import './styles/tokens.css';
import './styles/base.css';
import './styles/screens.css';
import { app } from './core/app';
import { imgUrl } from './core/dom';
import { store } from './core/store';
import { getTitle } from './data/catalog';
import { homeScreen } from './screens/home';
import { quickSettings, topBarButton } from './screens/dropdown';
import { libraryScreen } from './screens/library';
import { pickSetup } from './screens/pick';
import { launcherSettings, placeholder } from './screens/placeholder';
import { titlePreview } from './screens/preview';
import { searchScreen } from './screens/search';

document.documentElement.style.setProperty('--bg-image', `url("${imgUrl('bg-evening.jpg')}")`);

app.init(document.getElementById('stage')!, {
  home: homeScreen,
  movies: () => libraryScreen('movie'),
  tv: () => libraryScreen('show'),
  music: () => placeholder('Music', 'Spotify, YouTube Music and your playlists. Not part of V1.', { tab: 'music' }),
  search: searchScreen,
});

// ---- Dev bar under the stage: the screen stack, quick jumps and a state reset ----
const trail = document.getElementById('trail')!;
app.onChange(() => (trail.textContent = app.trail.join('  ›  ')));
trail.textContent = app.trail.join('  ›  ');

const jumps: Record<string, () => void> = {
  Home: () => app.goTab('home'),
  Movies: () => app.goTab('movies'),
  'TV Shows': () => app.goTab('tv'),
  Search: () => app.goTab('search'),
  'Pick for me (Movies)': () => {
    app.goTab('movies');
    app.push(pickSetup('movie'));
  },
  'Title preview · Arrival': () => {
    app.goTab('movies');
    app.push(titlePreview(getTitle('arrival')));
  },
  'Title preview · Moon (rent only)': () => {
    app.goTab('movies');
    app.push(titlePreview(getTitle('moon')));
  },
  'Title preview · Severance (TV)': () => {
    app.goTab('tv');
    app.push(titlePreview(getTitle('severance')));
  },
};
const jump = document.getElementById('jump') as HTMLSelectElement;
for (const name of Object.keys(jumps)) jump.append(new Option(name, name));
jump.addEventListener('change', () => {
  jumps[jump.value]?.();
  jump.value = '';
  jump.blur();
});
document.getElementById('reset')!.addEventListener('click', () => store.reset());

app.remoteKeys = {
  h: () => app.goTab('home', { focusNav: true }),
  // The remote's Settings key: opens the quick-settings dropdown, and closes it when pressed again.
  s: () => {
    if (app.top()?.label === 'Quick settings') return app.pop();
    app.push(quickSettings(topBarButton('dev-settings'), () => app.push(launcherSettings())));
  },
};

// Handy from the browser console while working on flows.
if (import.meta.env.DEV) (window as unknown as { app: typeof app }).app = app;
