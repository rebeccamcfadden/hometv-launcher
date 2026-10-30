import { app, type Key } from '../../core/app';
import { h } from '../../core/dom';
import { focusable } from '../../core/focus';
import { settings } from '../../core/settings';
import { HOME_APPS, HOME_ROW_MAX, OTHER_APPS, type HomeApp } from '../../data/services';
import { icon } from '../../ui/icons';
import { paneHead, type Pane, type PaneHost } from './pane';

const ALL = [...HOME_APPS, ...OTHER_APPS];
const byId = (id: string) => ALL.find((a) => a.id === id)!;

/**
 * Home row apps: Home's row as small tiles, in order, and the other apps below. OK on a tile on Home picks it up, as
 * Home's hold-OK Move does: ← → move it, OK puts it down, ↓ takes it off Home, Back cancels. OK on another app adds
 * it at the end of the row, already picked up so it can be placed; Back then takes it off again.
 */
export function appsPane(host: PaneHost): Pane {
  /** The app being moved, and the row as it was when it was picked up (Back restores it). */
  let moving: { id: string; before: string[] } | null = null;
  const row = () => settings.get().homeApps;

  const tile = (a: HomeApp, onHome: boolean, i: number) => {
    const lifted = moving?.id === a.id;
    const el = focusable(
      h(
        `div.ha-tile${lifted ? '.is-moving' : ''}`,
        lifted ? h('span.ha-arrow.ha-arrow--l', { html: icon('chevLeft', 22) }) : null,
        h('div.ha-face', h('span', { style: `font-weight:${a.weight}` }, a.mark)),
        lifted ? h('span.ha-arrow.ha-arrow--r', { html: icon('chevRight', 22) }) : null,
        h('div.ha-name', a.name),
      ),
      {
        group: onHome ? 'ha-home' : 'ha-other',
        key: `ha-${a.id}`,
        isDefault: i === 0,
        onOk: () => {
          if (onHome) {
            moving = { id: a.id, before: [...row()] };
            host.rerender();
            return;
          }
          if (row().length >= HOME_ROW_MAX) return app.toast(`Home’s row holds ${HOME_ROW_MAX} apps. Take one off first.`);
          moving = { id: a.id, before: [...row()] };
          settings.set({ homeApps: [...row(), a.id] });
          host.focusKey(`ha-${a.id}`);
        },
      },
    );
    if (onHome && i === 0) el.dataset.entry = '';
    return el;
  };

  const step = (d: number) => {
    const r = [...row()];
    const at = r.indexOf(moving!.id);
    const to = at + d;
    if (to < 0 || to >= r.length) return;
    [r[at], r[to]] = [r[to], r[at]];
    settings.set({ homeApps: r });
  };

  return {
    render() {
      const on = row();
      const off = ALL.filter((a) => !on.includes(a.id));
      return [
        ...paneHead('Home', 'Home row apps', `The apps on Home’s row, which holds ${HOME_ROW_MAX}. OK on one picks it up to move it.`),
        h('div.grp-h', `On Home · ${on.length} of ${HOME_ROW_MAX}`),
        h('div.ha-row', on.map((id, i) => tile(byId(id), true, i))),
        h(
          'div.ha-hint',
          moving ? '← → moves it · OK puts it down · ↓ takes it off Home · Back cancels' : 'OK picks an app up, as Hold OK → Move does on Home.',
        ),
        h('div.grp-h', 'Other apps'),
        off.length ? h('div.ha-row', off.map((a, i) => tile(a, false, i))) : h('div.pane-note', 'Every app is on Home.'),
        h('div.pane-note', 'OK on another app adds it at the end of the row, ready to place.'),
      ];
    },
    back() {
      if (!moving) return false;
      const id = moving.id;
      settings.set({ homeApps: moving.before });
      moving = null;
      host.rerender();
      host.focusKey(`ha-${id}`);
      return true;
    },
    onKey(key: Key) {
      // Back reaches back() above, which cancels the move.
      if (!moving || key === 'back') return false;
      const id = moving.id;
      if (key === 'left' || key === 'right') step(key === 'left' ? -1 : 1);
      else if (key === 'ok') {
        moving = null;
        host.rerender();
      } else if (key === 'down') {
        if (row().length === 1) {
          app.toast('Keep at least one app on Home');
          return true;
        }
        moving = null;
        settings.set({ homeApps: row().filter((x) => x !== id) });
        host.focusKey(`ha-${id}`);
        return true;
      }
      host.focusKey(`ha-${id}`);
      return true;
    },
  };
}
