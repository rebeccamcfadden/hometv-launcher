import { app, type Screen } from '../core/app';
import { h } from '../core/dom';
import { focusable } from '../core/focus';
import { HOME_APPS } from '../data/services';
import { icon, type IconName } from '../ui/icons';
import { navBar } from '../ui/widgets';
import { launcherSettings, placeholder } from './placeholder';

const HOURS: [string, IconName, string][] = [
  ['9 PM', 'moon', '14°'],
  ['10 PM', 'moon', '14°'],
  ['11 PM', 'cloud', '13°'],
  ['12 AM', 'cloud', '13°'],
  ['1 AM', 'moon', '12°'],
  ['2 AM', 'moon', '12°'],
];

export function homeScreen(): Screen {
  const greeting = h('div.home-greeting');
  const date = h('div.home-date');
  const time = h('div.home-time');
  const ampm = h('span.home-ampm');

  const tick = () => {
    const now = new Date();
    const hr = now.getHours();
    greeting.textContent = hr < 12 ? 'Good morning' : hr < 18 ? 'Good afternoon' : 'Good evening';
    date.textContent = now.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
    time.textContent = now.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }).replace(/\s?[AP]M/, '');
    ampm.textContent = hr < 12 ? 'AM' : 'PM';
  };
  tick();
  setInterval(tick, 15_000);

  const apps = HOME_APPS.map((a, i) =>
    focusable(
      h('div.app', h('div.app-tile', h('span', { style: `font-weight:${a.weight}` }, a.mark)), h('div.app-name', a.name)),
      { group: 'apps', key: `app-${a.id}`, isDefault: i === 0, onOk: () => app.toast(`Opening ${a.name}…`) },
    ),
  );

  const tile = (label: string, ic: IconName, key: string, onOk: () => void, group: string) =>
    focusable(h('div.home-tile', h('span', { html: icon(ic, 28) }), h('span', label)), { group, key, onOk, isDefault: key === 'in-live' });

  const inputs = (
    [
      ['Live TV', 'in-live'],
      ['HDMI 1', 'in-hdmi1'],
      ['HDMI 2', 'in-hdmi2'],
      ['HDMI 3', 'in-hdmi3'],
    ] as const
  ).map(([label, key]) => tile(label, 'tv', key, () => app.toast(`Switching to ${label}…`), 'inputs'));

  const shortcuts = [
    tile('All Apps', 'grid', 'sc-apps', () => app.push(placeholder('All Apps', 'Every installed app, with hide and reorder.')), 'shortcuts'),
    tile('Launcher settings', 'home', 'sc-launcher', () => app.push(launcherSettings()), 'shortcuts'),
    tile('TV Settings', 'settings', 'sc-settings', () => app.toast('Opening Android TV Settings…'), 'shortcuts'),
  ];

  const weather = focusable(
    h(
      'div.weather',
      h('div.weather-now', h('div.weather-label', 'Weather'), h('div.weather-temp', '14°'), h('div.weather-desc', 'Partly cloudy · H:16° L:9°')),
      h(
        'div.weather-hours',
        HOURS.map(([t, ic, deg]) => h('div.weather-hour', h('div', t), h('span', { html: icon(ic, 24) }), h('b', deg))),
      ),
    ),
    { group: 'weather', key: 'weather', onOk: () => app.push(placeholder('Weather', 'Hourly and 7-day forecast.')) },
  );

  const content = h(
    'div.home-content',
    h(
      'div.home-hero',
      h('div.home-hello', greeting, date),
      h(
        'div.home-clock',
        h('div', time, ampm),
        h(
          'div.home-forecast',
          h('span', { html: icon('sun', 24) }),
          'Today · 58°F',
          h('span.sep'),
          h('span', { html: icon('cloud', 24) }),
          'Tomorrow · 56°F',
        ),
      ),
    ),
    h('div.home-apps', apps),
    h(
      'div.home-lower',
      h('div.home-group', h('div.home-heading', 'Inputs'), h('div.home-tiles', inputs)),
      h('div.home-group', h('div.home-heading', 'Shortcuts'), h('div.home-tiles', shortcuts)),
    ),
    weather,
  );

  const el = h('div.screen.screen--base.home', navBar('home'), content);

  return {
    label: 'Home',
    el,
    defaultFocus: () => apps[0],
    onFocus(f) {
      const lower = ['inputs', 'shortcuts', 'weather'].includes(f.dataset.group ?? '');
      el.classList.toggle('is-scrolled', lower);
    },
  };
}
