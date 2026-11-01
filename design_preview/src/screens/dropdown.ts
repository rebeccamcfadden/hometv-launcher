import { app, STAGE_H, STAGE_W, type Screen } from '../core/app';
import { h } from '../core/dom';
import { focusable } from '../core/focus';
import { net } from '../core/net';
import { icon, type IconName } from '../ui/icons';

export interface MenuRow {
  label: string;
  icon?: IconName;
  /** Right-hand note, e.g. "Connected" or "Re-enable"; a function for a value that changes while the menu is open. */
  status?: string | (() => string);
  /** A chevron after the status: the row opens a Launcher settings pane. */
  chevron?: boolean;
  /** Small position dots after the status, for a value that OK steps through: [how many, which is on]. */
  dots?: () => [number, number];
  /** OK steps the value in place and the menu stays open (Motion). */
  cycle?: () => void;
  /** Draws the status with the green dot, as the base launcher does for connected inputs. */
  good?: boolean;
  /** A line between groups, drawn above this row. */
  divider?: boolean;
  /** The choice in force (the library's Sort menu): a check at the right, and the menu's first focus. */
  checked?: boolean;
  /** A tick in front of the label while this says so (the title menu's lists); read again after each OK. */
  tick?: () => boolean;
  /** OK runs onOk and the menu stays open: the row closes it itself, or Back does. */
  stay?: boolean;
  onOk?: () => void;
}

/** The title-safe margins a menu hung from a card stays inside (the TV's Ui.SAFE_X and SAFE_Y). */
const SAFE_X = 96;
const SAFE_Y = 54;
/** The gap between a card and the menu hung from it. */
const GAP = 14;

/**
 * The base launcher's glass dropdown: hangs under a top-bar button, grows inward from the right edge,
 * and closes on Back, on OK on a row, on a click outside it, or when the D-pad moves past its edge. Focus
 * returns to the button, and a move past the left, right or bottom edge carries on one step from it.
 */
export function dropdown(
  label: string,
  anchor: HTMLElement | null,
  rows: MenuRow[],
  opts: { header?: HTMLElement; className?: string; onCard?: boolean } = {},
): Screen & { close(): void } {
  let top = 130;
  let right = 96;
  if (anchor) {
    const a = anchor.getBoundingClientRect();
    const s = app.stage.getBoundingClientRect();
    top = Math.round((a.bottom - s.top) / app.scale) + 14;
    right = Math.round(STAGE_W - (a.right - s.left) / app.scale);
  }

  const statusOf = (r: MenuRow) => {
    const text = typeof r.status === 'function' ? r.status() : r.status;
    const dots = r.dots?.();
    if (!text && !r.chevron && !dots && !r.checked) return null;
    return h(
      `span.menu-status${r.good ? '.is-good' : ''}`,
      r.good ? h('span.menu-dot') : null,
      text ?? null,
      dots ? h('span.dots', Array.from({ length: dots[0] }, (_, k) => h(`i${k === dots[1] ? '.on' : ''}`))) : null,
      r.chevron ? h('span.chev', { html: icon('chevRight', 22) }) : null,
      r.checked ? h('span.menu-check', { html: icon('check', 28) }) : null,
    );
  };

  const first = Math.max(0, rows.findIndex((r) => r.checked));
  const tickOf = (r: MenuRow) =>
    r.tick ? h(`span.menu-tick${r.tick() ? '.is-on' : ''}`, { html: icon('check', 28) }) : null;

  const rowEls = rows.map((r, i) => {
    const el: HTMLElement = focusable(
      h('div.menu-row', tickOf(r), r.icon ? h('span.menu-icon', { html: icon(r.icon, 28) }) : null, h('span.menu-label', r.label), statusOf(r)),
      {
        group: 'menu',
        key: `m-${i}`,
        isDefault: i === first,
        onOk: () => {
          if (r.cycle) {
            r.cycle();
            el.querySelector('.menu-status')?.replaceWith(statusOf(r) ?? '');
            return;
          }
          if (r.stay) {
            r.onOk?.();
            el.querySelector('.menu-tick')?.classList.toggle('is-on', !!r.tick?.());
            return;
          }
          close();
          r.onOk?.();
        },
      },
    );
    return [r.divider ? h('div.menu-divider') : null, el];
  });

  const catcher = h('div.menu-catcher');
  // A card's menu remembers the card's row or grid, nearest first, for when the card has gone on closing.
  const near = opts.onCard && anchor ? nearest(anchor) : [];
  /** Closes the menu; focus goes back to its card, or to the card's nearest neighbour when the page drew it away. */
  const close = () => {
    app.pop();
    if (!opts.onCard || !anchor) return;
    if (anchor.isConnected) return void app.focus(anchor);
    const page = app.top();
    const key = anchor.dataset.key;
    const at = (k: string) => page?.el.querySelector<HTMLElement>(`[data-key="${CSS.escape(k)}"]`);
    if (key && at(key)) return;
    for (const k of near) {
      const n = at(k);
      if (n?.getClientRects().length) return void app.focus(n);
    }
  };
  catcher.addEventListener('click', close);
  const cls = `${opts.className ? `.${opts.className}` : ''}${opts.onCard ? '.menu--card' : ''}`;
  const panel = h(`div.menu${cls}`, { style: `top:${top}px;right:${right}px` }, opts.header ?? null, rowEls);
  const el = h('div.screen.layer-dialog', catcher, panel);
  // The button it hangs from stays highlighted while the menu is open; a card shows its focus ring instead.
  if (!opts.onCard) anchor?.classList.add('is-open');
  let placed = false;
  const screen: Screen & { close(): void } = {
    label,
    el,
    layer: 'dialog',
    close,
    defaultFocus: () => el.querySelector<HTMLElement>('.menu-row[data-default]') ?? el.querySelector<HTMLElement>('.menu-row'),
    onShow() {
      if (!opts.onCard || placed || !anchor) return;
      placed = true;
      placeOnCard(panel, anchor);
    },
    onKey(key) {
      if (key === 'back' && opts.onCard) {
        close();
        return true;
      }
      if (key !== 'up' && key !== 'down' && key !== 'left' && key !== 'right') return false;
      if (app.moveWithin(panel, key)) return true;
      close();
      if (anchor?.isConnected) app.focus(anchor);
      // A card's menu just closes; a top-bar menu carries the move on one step from its button.
      if (key !== 'up' && !opts.onCard) app.move(key);
      return true;
    },
  };
  const stop = app.onChange(() => {
    if (app.has(screen)) return;
    anchor?.classList.remove('is-open');
    stop();
  });
  return screen;
}

/** The keys of the card's row or grid (its focus group) on the page, nearest first, the one after it first. */
function nearest(card: HTMLElement): string[] {
  const group = card.dataset.group;
  const page = app.top();
  if (!group || !page) return [];
  const keys = [...page.el.querySelectorAll<HTMLElement>('[data-focus]')]
    .filter((e) => e.dataset.group === group && e.dataset.key)
    .map((e) => e.dataset.key!);
  const i = keys.indexOf(card.dataset.key ?? '');
  if (i < 0) return [];
  const out: string[] = [];
  for (let d = 1; d < keys.length; d++) {
    if (i + d < keys.length) out.push(keys[i + d]);
    if (i - d >= 0) out.push(keys[i - d]);
  }
  return out;
}

/**
 * Hangs a menu from a card, as the TV's MenuPlace does: under it, else over it, else beside it (right, then left),
 * inside the title-safe area; it grows out of the card.
 */
function placeOnCard(panel: HTMLElement, card: HTMLElement) {
  const s = app.stage.getBoundingClientRect();
  const c = card.getBoundingClientRect();
  const r = {
    left: (c.left - s.left) / app.scale,
    top: (c.top - s.top) / app.scale,
    right: (c.right - s.left) / app.scale,
    bottom: (c.bottom - s.top) / app.scale,
  };
  const w = panel.offsetWidth;
  const ph = panel.offsetHeight;
  const clampX = (x: number) => Math.max(SAFE_X, Math.min(x, STAGE_W - SAFE_X - w));
  const clampY = (y: number) => Math.max(SAFE_Y, Math.min(y, STAGE_H - SAFE_Y - ph));
  let left: number;
  let top: number;
  let origin: string;
  if (r.bottom + GAP + ph <= STAGE_H - SAFE_Y) {
    [left, top, origin] = [clampX(r.left), r.bottom + GAP, 'top left'];
  } else if (r.top - GAP - ph >= SAFE_Y) {
    [left, top, origin] = [clampX(r.left), r.top - GAP - ph, 'bottom left'];
  } else if (r.right + GAP + w <= STAGE_W - SAFE_X) {
    [left, top, origin] = [r.right + GAP, clampY(r.top), 'center left'];
  } else {
    [left, top, origin] = [Math.max(SAFE_X, r.left - GAP - w), clampY(r.top), 'center right'];
  }
  panel.style.right = '';
  panel.style.left = `${Math.round(left)}px`;
  panel.style.top = `${Math.round(top)}px`;
  panel.style.transformOrigin = origin;
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
  // Offline (the dev bar's data state): connected to the router, but no internet behind it.
  const offline = net.isOffline();
  const header = h(
    'div.menu-header',
    h(`span.menu-header-icon${offline ? '.is-warn' : ''}`, { html: icon(offline ? 'wifiOff' : 'wifi', 34) }),
    h(
      'div',
      h('div.menu-header-title', 'Home Wi-Fi'),
      h('div.menu-header-sub', h(`span.menu-dot${offline ? '.is-warn' : ''}`), offline ? 'Connected · No internet' : 'Connected · Internet OK · Strong signal'),
    ),
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
