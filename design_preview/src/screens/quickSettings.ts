import { account } from '../core/account';
import { app, type Screen } from '../core/app';
import { MOTION_LABELS, settings, SLEEP_STEPS, sleepLabel, unitSuffix, type Motion } from '../core/settings';
import { placeById } from '../data/places';
import { dropdown } from './dropdown';
import { launcherSettings, type SectionId } from './settings';

const MOTIONS: Motion[] = ['full', 'reduced', 'off'];
const open = (what: string) => () => app.toast(`Opening ${what}…`);

/**
 * The nav's Settings icon (V2 design final-quick-settings, 5A + 5C combined): the Wi-Fi and Inputs dropdown look,
 * hung under the icon, in four groups. TV settings and Launcher settings; the TV's own pages; household rows that show
 * a value and open their Launcher settings pane (chevron); then Motion and Sleep timer, where OK steps the value in
 * place, the menu stays open, and dots show the position.
 */
export function settingsMenu(anchor: HTMLElement | null): Screen {
  const pane = (id: SectionId) => () => app.push(launcherSettings(id, { enter: true }));
  return dropdown(
    'Settings',
    anchor,
    [
      { label: 'TV settings', icon: 'settings', onOk: open('the TV’s settings') },
      { label: 'Launcher settings', icon: 'apps', chevron: true, onOk: () => app.push(launcherSettings()) },
      { label: 'Picture', icon: 'picture', divider: true, onOk: open('picture settings') },
      { label: 'Sound', icon: 'sound', onOk: open('sound settings') },
      { label: 'Network & Wi-Fi', icon: 'wifi', onOk: open('network and Wi-Fi settings') },
      { label: 'Inputs', icon: 'input', onOk: open('the TV’s inputs') },
      { label: 'TMDB account', icon: 'user', divider: true, chevron: true, status: () => account.value().text, onOk: pane('account') },
      { label: 'Streaming services', icon: 'tv', chevron: true, status: () => `${settings.get().services.length} ticked`, onOk: pane('services') },
      {
        label: 'Weather',
        icon: 'cloud',
        chevron: true,
        status: () => `${placeById(settings.get().place).name} · ${unitSuffix()}`,
        onOk: pane('weather'),
      },
      {
        label: 'Motion',
        icon: 'motion',
        divider: true,
        status: () => MOTION_LABELS[settings.get().motion],
        dots: () => [MOTIONS.length, MOTIONS.indexOf(settings.get().motion)],
        cycle: () => settings.set({ motion: MOTIONS[(MOTIONS.indexOf(settings.get().motion) + 1) % MOTIONS.length] }),
      },
      {
        label: 'Sleep timer',
        icon: 'timer',
        status: () => sleepLabel(settings.get().sleep),
        dots: () => [SLEEP_STEPS.length, SLEEP_STEPS.indexOf(settings.get().sleep)],
        cycle: () => settings.set({ sleep: SLEEP_STEPS[(SLEEP_STEPS.indexOf(settings.get().sleep) + 1) % SLEEP_STEPS.length] }),
      },
    ],
    { className: 'menu--settings' },
  );
}
