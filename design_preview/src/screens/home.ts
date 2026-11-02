import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { net, SAVED_AT } from '../core/net';
import { deg, settings } from '../core/settings';
import { placeById } from '../data/places';
import { attachWeather } from '../core/weatherfx';
import { HOME_APPS, OTHER_APPS } from '../data/services';
import { icon, type IconName } from '../ui/icons';
import { navBar } from '../ui/widgets';
import { placeholder } from './placeholder';
import { launcherSettings } from './settings';

/** The hourly strip, as offsets from the place's temperature now (mock). */
const HOURS: [string, IconName, number][] = [
  ['9 PM', 'moon', 0],
  ['10 PM', 'moon', 0],
  ['11 PM', 'cloud', -1],
  ['12 AM', 'cloud', -1],
  ['1 AM', 'moon', -2],
  ['2 AM', 'moon', -2],
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

  // The row follows Launcher settings' Home row apps.
  const appsRow = h('div.home-apps');
  let painted = '';
  const paintApps = () => {
    // Only when the row changed, so other settings don't reset focus on it.
    if (painted === settings.get().homeApps.join()) return;
    painted = settings.get().homeApps.join();
    const all = [...HOME_APPS, ...OTHER_APPS];
    const shown = settings.get().homeApps.map((id) => all.find((a) => a.id === id)).filter((a) => !!a);
    replace(
      appsRow,
      shown.map((a, i) =>
        focusable(
          h('div.app', h('div.app-tile', h('span', { style: `font-weight:${a.weight}` }, a.mark)), h('div.app-name', a.name)),
          { group: 'apps', key: `app-${a.id}`, isDefault: i === 0, onOk: () => app.toast(`Opening ${a.name}…`) },
        ),
      ),
    );
  };
  paintApps();
  settings.on(paintApps);

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

  // The weather follows Launcher settings' place and unit. Offline it shows the last forecast with when it was
  // fetched, or, with none saved, a quiet "Weather unavailable offline" (06-states A).
  const weather = focusable(h('div.weather'), {
    group: 'weather',
    key: 'weather',
    onOk: () => app.push(placeholder('Weather', 'Hourly and 7-day forecast.')),
  });
  const forecast = h('div.home-forecast');
  const paintWeather = () => {
    const p = placeById(settings.get().place);
    if (net.get() === 'offline-empty') {
      replace(weather, h('div.weather-none', h('span', { html: icon('wifiOff', 28) }), 'Weather unavailable offline'));
      replace(forecast, h('span.weather-quiet', 'Weather unavailable offline'));
      return;
    }
    const offline = net.isOffline();
    replace(
      weather,
      h(
        'div.weather-now',
        h('div.weather-label', p.name),
        h('div.weather-temp', deg(p.temp)),
        offline
          ? h('div.weather-desc.weather-stale', `Offline · updated ${SAVED_AT.replace('today ', '')}`)
          : h('div.weather-desc', `${p.desc} · H:${deg(p.temp + 2)} L:${deg(p.temp - 5)}`),
      ),
      h(
        'div.weather-hours',
        HOURS.map(([t, ic, d]) => h('div.weather-hour', h('div', t), h('span', { html: icon(ic, 24) }), h('b', deg(p.temp + d)))),
      ),
    );
    replace(
      forecast,
      h('span', { html: icon('sun', 24) }),
      `Today · ${deg(p.temp + 2)}`,
      h('span.sep'),
      h('span', { html: icon('cloud', 24) }),
      `Tomorrow · ${deg(p.temp)}`,
    );
  };
  paintWeather();
  settings.on(paintWeather);
  net.on(paintWeather);

  const content = h(
    'div.home-content',
    h(
      'div.home-hero',
      h('div.home-hello', greeting, date),
      h(
        'div.home-clock',
        h('div', time, ampm),
        forecast,
      ),
    ),
    appsRow,
    h(
      'div.home-lower',
      h('div.home-group', h('div.home-heading', 'Inputs'), h('div.home-tiles', inputs)),
      h('div.home-group', h('div.home-heading', 'Shortcuts'), h('div.home-tiles', shortcuts)),
    ),
    weather,
  );

  const el = h('div.screen.screen--base.home', navBar('home'), content);
  // F25: snow or rain over the Seasonal picture while Home is idle.
  attachWeather(el, () => app.top()?.el === el);

  return {
    label: 'Home',
    el,
    defaultFocus: () => appsRow.querySelector<HTMLElement>('.app') ?? el.querySelector<HTMLElement>('.home-tile'),
    onFocus(f) {
      const lower = ['inputs', 'shortcuts', 'weather'].includes(f.dataset.group ?? '');
      el.classList.toggle('is-scrolled', lower);
    },
  };
}
