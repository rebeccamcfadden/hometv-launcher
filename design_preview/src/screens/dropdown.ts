import { app, STAGE_W, type Screen } from '../core/app';
import { h } from '../core/dom';
import { focusable } from '../core/focus';
import { icon, type IconName } from '../ui/icons';

export interface MenuRow {
  label: string;
  icon?: IconName;
  /** Right-hand note, e.g. "Connected" or "Re-enable". */
  status?: string;
  /** Draws the status with the green dot, as the base launcher does for connected inputs. */
  good?: boolean;
  /** A line between groups, drawn above this row. */
  divider?: boolean;
  onOk?: () => void;
}

/**
 * The base launcher's glass dropdown: hangs under a top-bar button, grows inward from the right edge,
 * and closes on Back, on OK on a row, on a click outside it, or when the D-pad moves past its edge. Focus
 * returns to the button, and a move past the left, right or bottom edge carries on one step from it.
 */
export function dropdown(label: string, anchor: HTMLElement | null, rows: MenuRow[], opts: { header?: HTMLElement } = {}): Screen {
  let top = 130;
  let right = 96;
  if (anchor) {
    const a = anchor.getBoundingClientRect();
    const s = app.stage.getBoundingClientRect();
    top = Math.round((a.bottom - s.top) / app.scale) + 14;
    right = Math.round(STAGE_W - (a.right - s.left) / app.scale);
  }

  const rowEls = rows.map((r, i) => [
    r.divider ? h('div.menu-divider') : null,
    focusable(
      h(
        'div.menu-row',
        r.icon ? h('span.menu-icon', { html: icon(r.icon, 28) }) : null,
        h('span.menu-label', r.label),
        r.status ? h(`span.menu-status${r.good ? '.is-good' : ''}`, r.good ? h('span.menu-dot') : null, r.status) : null,
      ),
      {
        group: 'menu',
        key: `m-${i}`,
        isDefault: i === 0,
        onOk: () => {
          app.pop();
          r.onOk?.();
        },
      },
    ),
  ]);

  const catcher = h('div.menu-catcher');
  catcher.addEventListener('click', () => app.pop());
  const panel = h('div.menu', { style: `top:${top}px;right:${right}px` }, opts.header ?? null, rowEls);
  const el = h('div.screen.layer-dialog', catcher, panel);
  return {
    label,
    el,
    layer: 'dialog',
    defaultFocus: () => el.querySelector<HTMLElement>('.menu-row'),
    onKey(key) {
      if (key !== 'up' && key !== 'down' && key !== 'left' && key !== 'right') return false;
      if (app.moveWithin(panel, key)) return true;
      app.pop();
      if (anchor?.isConnected) app.focus(anchor);
      if (key !== 'up') app.move(key);
      return true;
    },
  };
}

/** The top-bar button a menu hangs from, on whichever page is showing. */
export function topBarButton(key: string): HTMLElement | null {
  const all = [...app.stage.querySelectorAll<HTMLElement>(`[data-key="${key}"]`)];
  return all.find((el) => !el.closest('.is-leaving')) ?? null;
}

const open = (what: string) => () => app.toast(`Opening ${what}…`);

/** Inputs: HDMI ports with their connection state, then AV, then Live TV. */
export function inputsMenu(anchor: HTMLElement | null): Screen {
  const hdmi = (name: string, connected = false): MenuRow => ({
    label: name,
    icon: 'hdmi',
    status: connected ? 'Connected' : undefined,
    good: connected,
    onOk: () => app.toast(`Switching to ${name}…`),
  });
  return dropdown('Inputs', anchor, [
    hdmi('HDMI 1'),
    hdmi('HDMI 2', true),
    hdmi('HDMI 3'),
    hdmi('HDMI PlayStation 5', true),
    { label: 'AV', icon: 'av', divider: true, onOk: () => app.toast('Switching to AV…') },
    { label: 'Live TV', icon: 'liveTv', divider: true, onOk: () => app.toast('Opening Live TV…') },
  ]);
}

/**
 * Wi-Fi: new in this design (the base pill opens Wi-Fi settings directly). The current network and its
 * internet state on top, other saved networks to switch to, and the full Wi-Fi settings.
 */
export function wifiMenu(anchor: HTMLElement | null): Screen {
  const header = h(
    'div.menu-header',
    h('span.menu-header-icon', { html: icon('wifi', 34) }),
    h('div', h('div.menu-header-title', 'Home Wi-Fi'), h('div.menu-header-sub', h('span.menu-dot'), 'Connected · Internet OK · Strong signal')),
  );
  return dropdown(
    'Wi-Fi',
    anchor,
    [
      { label: 'Home Wi-Fi 5G', icon: 'wifi', status: 'Saved', onOk: () => app.toast('Switching to Home Wi-Fi 5G…') },
      { label: 'Guest network', icon: 'lock', status: 'Saved', onOk: () => app.toast('Switching to Guest network…') },
      { label: 'Other networks', icon: 'wifi', divider: true, onOk: open('Wi-Fi settings to join a network') },
      { label: 'Wi-Fi settings', icon: 'settings', onOk: open('Android Wi-Fi settings') },
    ],
    { header },
  );
}

/** The remote's Settings key: the base launcher's quick-settings panel. */
export function quickSettings(anchor: HTMLElement | null, onLauncherSettings: () => void): Screen {
  return dropdown('Quick settings', anchor, [
    { label: 'TV system settings', icon: 'settings', onOk: open('TV system settings') },
    { label: 'Picture', icon: 'picture', onOk: open('picture settings') },
    { label: 'Network', icon: 'wifi', onOk: open('Wi-Fi settings') },
    { label: 'Apps', icon: 'grid', status: 'Re-enable', onOk: open('the TV’s Apps settings to re-enable an app') },
    { label: 'Launcher settings', icon: 'home', onOk: onLauncherSettings },
  ]);
}
