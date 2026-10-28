// Spatial (D-pad) navigation. Focusable elements carry `data-focus`; `data-group` lets a row or grid
// remember where focus was, so leaving and re-entering it returns to the same item.

export type Dir = 'up' | 'down' | 'left' | 'right';

export interface FocusOpts {
  onOk?: () => void;
  onFocus?: () => void;
  group?: string;
  /** Stable id used to restore focus after a re-render. */
  key?: string;
  /** The item a group picks when entered for the first time. */
  isDefault?: boolean;
}

const handlers = new WeakMap<HTMLElement, FocusOpts>();

export function focusable<T extends HTMLElement>(el: T, opts: FocusOpts = {}): T {
  el.dataset.focus = '';
  if (opts.group) el.dataset.group = opts.group;
  if (opts.key) el.dataset.key = opts.key;
  if (opts.isDefault) el.dataset.default = '';
  handlers.set(el, opts);
  return el;
}

export const handlerOf = (el: HTMLElement | null) => (el ? handlers.get(el) : undefined);

export function candidates(root: Element): HTMLElement[] {
  return [...root.querySelectorAll<HTMLElement>('[data-focus]')].filter(
    (el) => !el.closest('[data-inert]') && el.getClientRects().length > 0,
  );
}

function score(a: DOMRect, b: DOMRect, dir: Dir): number | null {
  const ax = a.left + a.width / 2;
  const ay = a.top + a.height / 2;
  const bx = b.left + b.width / 2;
  const by = b.top + b.height / 2;
  const horizontal = dir === 'left' || dir === 'right';
  let primary: number;
  switch (dir) {
    case 'right':
      if (bx <= ax + 1 || b.right <= a.right) return null;
      primary = Math.max(0, b.left - a.right);
      break;
    case 'left':
      if (bx >= ax - 1 || b.left >= a.left) return null;
      primary = Math.max(0, a.left - b.right);
      break;
    case 'down':
      if (by <= ay + 1 || b.bottom <= a.bottom) return null;
      primary = Math.max(0, b.top - a.bottom);
      break;
    case 'up':
      if (by >= ay - 1 || b.top >= a.top) return null;
      primary = Math.max(0, a.top - b.bottom);
      break;
  }
  const overlap = horizontal
    ? Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top)
    : Math.min(a.right, b.right) - Math.max(a.left, b.left);
  const offAxis = horizontal ? Math.abs(by - ay) : Math.abs(bx - ax);
  const secondary = overlap > 0 ? 0 : offAxis;
  // When moving vertically, keep the left edges aligned so a column feels like a column.
  const align = horizontal ? offAxis : Math.abs(b.left - a.left);
  return primary + secondary * 2.5 + align * 0.08;
}

export function findNext(
  root: Element,
  from: HTMLElement,
  dir: Dir,
  memory: Map<string, HTMLElement>,
): HTMLElement | null {
  const list = candidates(root).filter((c) => c !== from);
  const a = from.getBoundingClientRect();
  let best: HTMLElement | null = null;
  let bestScore = Infinity;
  for (const c of list) {
    const s = score(a, c.getBoundingClientRect(), dir);
    if (s !== null && s < bestScore) {
      bestScore = s;
      best = c;
    }
  }
  const group = best?.dataset.group;
  if (best && group && group !== from.dataset.group) {
    const remembered = memory.get(group);
    if (remembered && remembered.isConnected && list.includes(remembered)) return remembered;
    const def = list.find((c) => c.dataset.group === group && c.dataset.default !== undefined);
    if (def) return def;
  }
  return best;
}

/** Scroll every `data-scroll="x|y"` ancestor so `el` is inside it with some padding. */
export function ensureVisible(el: HTMLElement, scale: number): void {
  for (let node = el.parentElement; node; node = node.parentElement) {
    const axis = node.dataset.scroll;
    if (!axis) continue;
    const pad = Number(node.dataset.scrollPad ?? 48);
    const c = node.getBoundingClientRect();
    const r = el.getBoundingClientRect();
    if (axis.includes('y')) {
      const top = (r.top - c.top) / scale;
      const bottom = (r.bottom - c.top) / scale;
      const height = c.height / scale;
      if (top < pad) node.scrollTop += top - pad;
      else if (bottom > height - pad) node.scrollTop += bottom - (height - pad);
    }
    if (axis.includes('x')) {
      const left = (r.left - c.left) / scale;
      const right = (r.right - c.left) / scale;
      const width = c.width / scale;
      if (left < pad) node.scrollLeft += left - pad;
      else if (right > width - pad) node.scrollLeft += right - (width - pad);
    }
  }
}
