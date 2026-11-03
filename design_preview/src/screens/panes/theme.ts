import { h } from '../../core/dom';
import { focusable } from '../../core/focus';
import { APP_LOGOS_LABELS, FONT_LABELS, settings, TIME_OF_DAY_LABELS } from '../../core/settings';
import { loadSwatchFonts, PERIOD_LABELS, theme, THEMES, type Period, type ThemeDef } from '../../core/theme';
import type { Pane } from './pane';

/**
 * A theme drawn from its own tokens (Figma "Settings · Theme picker"): its sky, the horizon and the shelf with three
 * mini tiles, the first focused with the accent ring, and "Aa" in its display face. The swatch carries the theme's
 * data-theme (and period), so themes.css gives it that theme's variables whatever the page is wearing.
 */
export function swatch(t: ThemeDef, period: Period): HTMLElement {
  return h(
    'div.swatch',
    { 'data-theme': t.id, 'data-period': t.group === 'seasonal' ? period : null },
    h('span.swatch-aa', 'Aa'),
    h('div.swatch-shelf', h('i.is-focus'), h('i'), h('i')),
  );
}

/**
 * Launcher settings › Theme (Figma page "Settings · Theme picker", frame 18:10): Classic and Seasonal swatches, then
 * Typeface, Time of day and App logos (F43: Home row apps in full colour, or as wordmarks on the glass tile; F22, F29: a Seasonal picture follows the sun, or stays on its Day or Night picture; OK steps
 * through the three; Moving details moved to Performance as Animated details). Moving only highlights a swatch; OK
 * selects it, and the whole preview wears it at once.
 */
const NEXT_TIME_OF_DAY = { sun: 'day', day: 'night', night: 'sun' } as const;

export const themePane = (): Pane => ({
  render: () => {
    loadSwatchFonts();
    const s = settings.get();
    const period = theme.period();
    const option = (t: ThemeDef) => {
      const on = t.id === s.theme;
      const el = focusable(h(`div.theme-opt${on ? '.is-selected' : ''}`, h('div.theme-pic', swatch(t, period)), h('div.theme-name', t.name)), {
        group: 'themes',
        key: `theme-${t.id}`,
        isDefault: on,
        onOk: () => settings.set({ theme: t.id }),
      });
      if (on) el.dataset.entry = '';
      return el;
    };
    const row = (key: string, title: string, sub: string, value: string, on: boolean, onOk: () => void) =>
      focusable(
        h('div.lrow.theme-row', h('div.lr-main', h('div.lr-title', title), h('div.lr-sub', sub)), h(`span.theme-val${on ? '.is-on' : ''}`, value)),
        { group: 'theme-rows', key, onOk },
      );
    return [
      h('div.pane-title', 'Theme'),
      h('div.pane-sub', 'Colours, type and the picture behind Home. Rows, apps and data stay as they are.'),
      h('div.grp-h', 'Classic'),
      h('div.theme-grid', THEMES.filter((t) => t.group === 'classic').map(option)),
      h('div.grp-h', `Seasonal · follows the date · today: ${PERIOD_LABELS[period]}`),
      h('div.theme-grid', THEMES.filter((t) => t.group === 'seasonal').map(option)),
      h(
        'div.theme-rows',
        row('theme-font', 'Typeface', 'System font uses Roboto', FONT_LABELS[s.themeFont], false, () =>
          settings.set({ themeFont: s.themeFont === 'theme' ? 'system' : 'theme' }),
        ),
        row('theme-time', 'Time of day', 'For Seasonal themes', TIME_OF_DAY_LABELS[s.timeOfDay], false, () =>
          settings.set({ timeOfDay: NEXT_TIME_OF_DAY[s.timeOfDay] }),
        ),
        // F43: OK flips between Full colour and Minimal.
        row('theme-logos', 'App logos', 'On Home’s row of apps', APP_LOGOS_LABELS[s.appLogos], false, () =>
          settings.set({ appLogos: s.appLogos === 'full' ? 'minimal' : 'full' }),
        ),
      ),
    ];
  },
});
