import { h } from './dom';
import { type Dir, ensureVisible, findNext, handlerOf, nearest } from './focus';

export type TabId = 'home' | 'movies' | 'tv' | 'search';

/**
 * screen: a full page. popup: drawn over the nearest screen, which stays visible behind a scrim.
 * dialog: drawn over a popup or screen without hiding it.
 */
export type Layer = 'screen' | 'popup' | 'dialog';

export type Key = Dir | 'ok' | 'back';

export interface Screen {
  /** Shown in the stack read-out under the stage. */
  label: string;
  el: HTMLElement;
  layer?: Layer;
  tab?: TabId;
  defaultFocus?(): HTMLElement | null;
  /** Return true to stop the default handling. */
  onKey?(key: Key, focused: HTMLElement | null): boolean;
  /** OK held down on an item (the hold-OK menus). Return true when used. */
  onLongOk?(focused: HTMLElement | null): boolean;
  /** Printable characters, for screens that take typing. Return true when used. */
  onText?(ch: string): boolean;
  onFocus?(el: HTMLElement): void;
  /** Called each time the screen becomes the top of the stack. */
  onShow?(): void;
  /** Phone notes' import pill moves to the top centre while this page shows (Launcher settings: its pane runs to the bottom). */
  pillAtTop?: boolean;
  /**
   * Called when a tab's page stops being the current tab (another tab, Back to Home, the remote's Home), not when
   * something is pushed over it. Search forgets its query here.
   */
  onLeave?(): void;
  // Managed by the app.
  memory?: Map<string, HTMLElement>;
  lastFocus?: HTMLElement | null;
  lastFocusKey?: string;
}

export const STAGE_W = 1920;
export const STAGE_H = 1080;
/** How long OK is held before it opens a hold-OK menu, as Android's long press. */
const HOLD_MS = 500;

class App {
  stage!: HTMLElement;
  scale = 1;
  focused: HTMLElement | null = null;
  private stack: Screen[] = [];
  private tabs = new Map<TabId, Screen>();
  private factories!: Record<TabId, () => Screen>;
  private toastEl!: HTMLElement;
  private readonly overlays: HTMLElement[] = [];
  private toastTimer = 0;
  private listeners: (() => void)[] = [];
  /** OK pressed and not yet released: acted on at release, or held past HOLD_MS for the hold-OK menus. */
  private okTimer = 0;
  private okDown = false;
  private okHeld = false;
  private okTarget: HTMLElement | null = null;
  private okScreen: Screen | null = null;
  /** A hold-OK the top screen didn't take, offered on any screen: the title menu on a title card (titleMenu.ts). */
  longOk: ((focused: HTMLElement) => boolean) | null = null;

  init(stage: HTMLElement, factories: Record<TabId, () => Screen>) {
    this.stage = stage;
    this.factories = factories;
    this.toastEl = h('div.toast');
    stage.append(this.toastEl);
    window.addEventListener('keydown', (e) => this.onKeyDown(e));
    window.addEventListener('keyup', (e) => this.onKeyUp(e));
    // A right-click stands in for holding OK, for the mouse.
    stage.addEventListener('contextmenu', (e) => this.onContextMenu(e));
    window.addEventListener('resize', () => this.fit());
    stage.addEventListener('click', (e) => this.onClick(e));
    this.fit();
    this.goTab('home');
  }

  /** Calls `fn` whenever the stack changes; returns a function that stops it. */
  onChange(fn: () => void): () => void {
    this.listeners.push(fn);
    return () => (this.listeners = this.listeners.filter((x) => x !== fn));
  }

  get trail(): string[] {
    return this.stack.map((s) => s.label);
  }

  top(): Screen | undefined {
    return this.stack[this.stack.length - 1];
  }

  /** Whether the page showing, under any popups and dialogs, wants the import pill at the top. */
  pillAtTop(): boolean {
    for (let i = this.stack.length - 1; i >= 0; i--) {
      const s = this.stack[i];
      if (!s.layer || s.layer === 'screen') return !!s.pillAtTop;
    }
    return false;
  }

  /** Whether the screen is anywhere in the stack, shown or not. */
  has(s: Screen): boolean {
    return this.stack.includes(s);
  }

  tab(id: TabId): Screen {
    let s = this.tabs.get(id);
    if (!s) {
      s = this.factories[id]();
      s.tab = id;
      this.tabs.set(id, s);
    }
    return s;
  }

  goTab(id: TabId, opts: { focusNav?: boolean } = {}) {
    const s = this.tab(id);
    const prev = this.stack.length === 1 ? this.stack[0] : undefined;
    const root = this.stack[0];
    if (root && root !== s) root.onLeave?.();
    this.endTabTransition();
    this.stack = [s];
    this.render();
    if (prev?.tab && prev !== s) this.startTabTransition(prev, s);
    s.onShow?.();
    const nav = opts.focusNav ? s.el.querySelector<HTMLElement>(`[data-key="nav-${id}"]`) : null;
    this.focus(nav ?? this.restoreTarget(s));
  }

  // ---- Tab transition: the old page's content slides out under the new one, which slides in from the
  // side of the tab it came from. The nav is left out of both animations so it stays still. ----
  private leaving: { el: HTMLElement; entering: HTMLElement; timer: number } | null = null;

  private startTabTransition(from: Screen, to: Screen) {
    const order: TabId[] = ['home', 'movies', 'tv', 'search'];
    const dir = Math.sign(order.indexOf(to.tab!) - order.indexOf(from.tab!)) || 1;
    for (const el of [from.el, to.el]) {
      el.classList.remove('is-entering', 'is-leaving');
      el.style.setProperty('--dir', String(dir));
    }
    void to.el.offsetWidth; // restart the animation when the same page enters twice in a row
    to.el.classList.add('is-entering');
    from.el.classList.add('is-leaving');
    from.el.setAttribute('data-inert', '');
    this.stage.insertBefore(from.el, to.el);
    this.leaving = { el: from.el, entering: to.el, timer: window.setTimeout(() => this.endTabTransition(), 380) };
  }

  private endTabTransition() {
    if (!this.leaving) return;
    const { el, entering, timer } = this.leaving;
    clearTimeout(timer);
    // Cleared so that re-inserting the page later (render moves nodes) doesn't replay the animation.
    entering.classList.remove('is-entering');
    el.classList.remove('is-leaving');
    el.removeAttribute('data-inert');
    if (!this.stack.some((s) => s.el === el)) el.remove();
    this.leaving = null;
  }

  push(s: Screen) {
    this.stack.push(s);
    this.render();
    s.onShow?.();
    this.focus(this.restoreTarget(s));
  }

  pop() {
    if (this.stack.length <= 1) return;
    this.stack.pop();
    const s = this.top()!;
    this.render();
    s.onShow?.();
    this.focus(this.restoreTarget(s));
  }

  /** Remove the top screen and push another in its place (e.g. a dialog that leads elsewhere). */
  replaceTop(s: Screen) {
    this.stack.pop();
    this.push(s);
  }

  back() {
    const s = this.top();
    if (!s) return;
    if (s.onKey?.('back', this.focused)) return;
    if (this.stack.length > 1) this.pop();
    else if (s.tab && s.tab !== 'home') this.goTab('home', { focusNav: true });
  }

  focus(el: HTMLElement | null | undefined) {
    const s = this.top();
    if (!el || !s) return;
    this.focused?.classList.remove('is-focused');
    el.classList.add('is-focused');
    this.focused = el;
    s.memory ??= new Map();
    s.lastFocus = el;
    s.lastFocusKey = el.dataset.key;
    if (el.dataset.group) s.memory.set(el.dataset.group, el);
    ensureVisible(el, this.scale);
    handlerOf(el)?.onFocus?.();
    s.onFocus?.(el);
  }

  /** Focus survives re-renders by key; falls back to the screen's default. A hidden item is passed over, as on the TV. */
  restoreTarget(s: Screen): HTMLElement | null {
    if (s.lastFocus?.isConnected && s.el.contains(s.lastFocus) && s.lastFocus.getClientRects().length) return s.lastFocus;
    if (s.lastFocusKey) {
      const el = s.el.querySelector<HTMLElement>(`[data-key="${CSS.escape(s.lastFocusKey)}"]`);
      if (el && el.getClientRects().length) return el;
    }
    return s.defaultFocus?.() ?? s.el.querySelector<HTMLElement>('[data-focus]');
  }

  /** Call after a screen re-renders part of itself. */
  refocus() {
    const s = this.top();
    if (!s) return;
    if (this.focused?.isConnected && !this.focused.closest('[data-inert]') && this.focused.getClientRects().length) return;
    this.focus(this.restoreTarget(s));
  }

  toast(text: string) {
    this.toastEl.textContent = text;
    this.toastEl.classList.add('is-shown');
    clearTimeout(this.toastTimer);
    this.toastTimer = window.setTimeout(() => this.toastEl.classList.remove('is-shown'), 2400);
  }

  private visible(): Screen[] {
    let base = this.stack.length - 1;
    while (base > 0 && (this.stack[base].layer ?? 'screen') !== 'screen') base--;
    const out = [this.stack[base]];
    const above = this.stack.slice(base + 1);
    const lastPopup = above.map((s) => s.layer).lastIndexOf('popup');
    above.forEach((s, i) => {
      if (s.layer === 'dialog' || i === lastPopup) out.push(s);
    });
    return out.filter(Boolean);
  }

  private render() {
    const show = this.visible();
    for (const child of [...this.stage.children]) {
      if (child !== this.toastEl && !this.overlays.includes(child as HTMLElement) && !show.some((s) => s.el === child)) child.remove();
    }
    for (const s of show) this.stage.insertBefore(s.el, this.toastEl);
    this.listeners.forEach((fn) => fn());
  }

  /** An element that stays over every screen, like the toast (the import pill); never focusable. */
  overlay(el: HTMLElement) {
    this.overlays.push(el);
    this.stage.append(el);
  }

  /** A D-pad move on the top screen, as if the key had been pressed: the screen sees it first. */
  move(dir: Dir) {
    const s = this.top();
    if (!s) return;
    if (!this.focused || !this.focused.isConnected || !s.el.contains(this.focused)) {
      this.focus(this.restoreTarget(s));
      return;
    }
    // Left and Right on the nav stay on it, to the nearest item, whatever the page holds and whichever section was
    // used last: ← from Wi-Fi is always Search. Past either end, nothing.
    const bar = this.focused.closest('.nav');
    if (bar && (dir === 'left' || dir === 'right')) {
      const n = nearest(bar, this.focused, dir);
      if (n) this.focus(n);
      return;
    }
    if (s.onKey?.(dir, this.focused)) return;
    const next = this.intoNav(findNext(s.el, this.focused, dir, s.memory ?? new Map()));
    if (next) this.focus(next);
  }

  /** Spatial move limited to `root`, for screens that handle edges themselves. */
  moveWithin(root: Element, dir: Dir): boolean {
    const s = this.top();
    if (!s || !this.focused) return false;
    const next = this.intoNav(findNext(root, this.focused, dir, s.memory ?? new Map()));
    if (next) this.focus(next);
    return !!next;
  }

  /**
   * A move from the page into the nav lands on the page's own section (its tab, or Movies / TV Shows for Pick for
   * me), not on the section or device icon used last or nearest: Up from Home after a look at Search is Home.
   */
  private intoNav(next: HTMLElement | null): HTMLElement | null {
    const bar = next?.closest('.nav');
    if (!bar || this.focused?.closest('.nav')) return next;
    return bar.querySelector<HTMLElement>('.nav-left .is-active') ?? next;
  }

  private ok() {
    const s = this.top();
    if (!s) return;
    if (s.onKey?.('ok', this.focused)) return;
    handlerOf(this.focused)?.onOk?.();
  }

  /** OK held on the focused item: the top screen's hold-OK menu, else the app's (a title card's). */
  longOkOn(el: HTMLElement | null): boolean {
    const s = this.top();
    if (!s || !el) return false;
    return !!s.onLongOk?.(el) || !!this.longOk?.(el);
  }

  private onKeyDown(e: KeyboardEvent) {
    const target = e.target as HTMLElement | null;
    if (target && /^(INPUT|SELECT|TEXTAREA|BUTTON)$/.test(target.tagName)) return;
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    const dirs: Record<string, Dir> = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' };
    const s = this.top();
    if (dirs[e.key]) {
      e.preventDefault();
      this.move(dirs[e.key]);
    } else if (e.key === 'Enter') {
      // As on the TV, OK acts when it's let go, unless it was held long enough to open a hold-OK menu instead.
      e.preventDefault();
      if (e.repeat || this.okDown) return;
      this.okDown = true;
      this.okHeld = false;
      const held = this.focused;
      this.okTarget = held;
      this.okScreen = s ?? null;
      this.okTimer = window.setTimeout(() => {
        this.okTimer = 0;
        this.okHeld = this.focused === held && this.longOkOn(held);
      }, HOLD_MS);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      this.back();
    } else if (e.key === 'Backspace') {
      // Backspace is Back on every screen, Search included (only its on-screen delete key deletes). A held key
      // goes Back once.
      e.preventDefault();
      if (!e.repeat) this.back();
    } else if (e.key.length === 1) {
      if (s?.onText?.(e.key)) e.preventDefault();
      else this.remoteKeys[e.key.toLowerCase()]?.();
    }
  }

  private onKeyUp(e: KeyboardEvent) {
    if (e.key !== 'Enter' || !this.okDown) return;
    e.preventDefault();
    this.okDown = false;
    clearTimeout(this.okTimer);
    this.okTimer = 0;
    // Only the item OK went down on, on the screen it was on: a move or Back while it was held cancels it, as on the TV.
    const same = this.focused === this.okTarget && this.top() === this.okScreen;
    if (!this.okHeld && same) this.ok();
    this.okHeld = false;
    this.okTarget = null;
    this.okScreen = null;
  }

  private onContextMenu(e: MouseEvent) {
    const s = this.top();
    const el = (e.target as HTMLElement).closest<HTMLElement>('[data-focus]');
    if (!s || !el || !s.el.contains(el) || el.closest('[data-inert]')) return;
    e.preventDefault();
    this.focus(el);
    this.longOkOn(el);
  }

  /** Remote buttons other than the D-pad, OK and Back (Home, Settings), mapped to letter keys by main.ts. */
  remoteKeys: Record<string, () => void> = {};

  private onClick(e: MouseEvent) {
    const s = this.top();
    const el = (e.target as HTMLElement).closest<HTMLElement>('[data-focus]');
    if (!s || !el || !s.el.contains(el) || el.closest('[data-inert]')) return;
    this.focus(el);
    this.ok();
  }

  private fit() {
    const viewport = this.stage.parentElement!;
    const w = viewport.clientWidth;
    const hgt = viewport.clientHeight;
    this.scale = Math.min(w / STAGE_W, hgt / STAGE_H);
    this.stage.style.transform = `scale(${this.scale})`;
    this.stage.style.left = `${(w - STAGE_W * this.scale) / 2}px`;
    this.stage.style.top = `${(hgt - STAGE_H * this.scale) / 2}px`;
  }
}

export const app = new App();
