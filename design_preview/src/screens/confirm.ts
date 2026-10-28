import { app, type Screen } from '../core/app';
import { h } from '../core/dom';
import { button } from '../ui/widgets';

export interface DialogAction {
  label: string;
  primary?: boolean;
  onOk: () => void;
}

/** A small dialog over whatever is showing, e.g. before a rental opens another app. Cancel is added. */
export function confirmDialog(opts: { title: string; body: string; detail?: string; actions: DialogAction[] }): Screen {
  const buttons = opts.actions.map((a, i) =>
    button(a.label, {
      primary: a.primary,
      key: `a${i}`,
      onOk: () => {
        app.pop();
        a.onOk();
      },
    }),
  );
  buttons.push(button('Cancel', { key: 'cancel', onOk: () => app.pop() }));
  const el = h(
    'div.screen.layer-dialog',
    h('div.scrim'),
    h(
      'div.dialog',
      h('div.dialog-title', opts.title),
      h('div.dialog-body', opts.body),
      opts.detail ? h('div.dialog-detail', opts.detail) : null,
      h('div.dialog-actions', buttons),
    ),
  );
  return { label: opts.title, el, layer: 'dialog', defaultFocus: () => buttons[0] };
}
