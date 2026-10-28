import { app, type Screen } from '../core/app';
import { h } from '../core/dom';
import { button, navBar } from '../ui/widgets';

/** A stub for a screen the design doesn't cover yet. */
export function placeholder(label: string, note: string, opts: { tab?: 'music' } = {}): Screen {
  const back = opts.tab ? null : button('Back', { icon: 'back', key: 'back', onOk: () => app.back() });
  const el = h(
    'div.screen.screen--base.placeholder',
    opts.tab ? navBar(opts.tab) : null,
    h(
      'div.placeholder-body',
      h('div.eyebrow', 'Not designed yet'),
      h('h1.placeholder-title', label),
      h('p.placeholder-note', note),
      back,
    ),
  );
  return { label, el, defaultFocus: () => back ?? el.querySelector<HTMLElement>('[data-default]') };
}

/** Opened from the Home shortcut and from the quick-settings dropdown's last row. */
export const launcherSettings = () =>
  placeholder('Launcher settings', 'Theme, rows, the dock and the switches in docs/switches.md.');
