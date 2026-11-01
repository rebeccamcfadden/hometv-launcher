import { app, type Screen } from '../core/app';
import { h } from '../core/dom';
import { button } from '../ui/widgets';

/** A stub for a screen the design doesn't cover yet, pushed over the page with a Back button. */
export function placeholder(label: string, note: string): Screen {
  const back = button('Back', { icon: 'back', key: 'back', onOk: () => app.back() });
  const el = h(
    'div.screen.screen--base.placeholder',
    h(
      'div.placeholder-body',
      h('div.eyebrow', 'Not designed yet'),
      h('h1.placeholder-title', label),
      h('p.placeholder-note', note),
      back,
    ),
  );
  return { label, el, defaultFocus: () => back };
}
