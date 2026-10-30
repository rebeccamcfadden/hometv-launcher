import type { Key } from '../../core/app';
import { h, type Child } from '../../core/dom';

/**
 * One section's pane in the Launcher settings split (V2 design 05-settings A with the r2 panes). Made once per
 * settings page, so a sub-state (Choose lists, Change city, an app being moved) survives moving between sections.
 */
export interface Pane {
  /** The pane's content, built afresh each time it shows or its data changes. Focus comes back by key. */
  render(): Child;
  /** Back inside the pane: leaves a sub-state and returns true; false lets the page take focus to the section. */
  back?(): boolean;
  /** Arrows and OK inside the pane, before the page's own handling; true when used. */
  onKey?(key: Key, focused: HTMLElement): boolean;
  /** Typing from a keyboard, for the city search. */
  onText?(ch: string): boolean;
}

/** What a pane can ask of the page around it. */
export interface PaneHost {
  /** Rebuilds the pane that is showing (after a sub-state change), keeping focus by key. */
  rerender(): void;
  /** Focuses the element with this key in the pane, after a rerender. */
  focusKey(key: string): void;
  /** First run's cut-down split, where some wording differs. */
  setup: boolean;
}

export const paneHead = (eyebrow: string, title: string, sub?: string) => [
  h('div.eyebrow', eyebrow),
  h('div.pane-title', title),
  sub ? h('div.pane-sub', sub) : null,
];

/** The title line with a count on the right ("6 of 11 ticked"). */
export const paneHeadCount = (eyebrow: string, title: string, count: string, sub: string) => [
  h('div.pane-headrow', h('div', h('div.eyebrow', eyebrow), h('div.pane-title', title)), h('span.pane-count', count)),
  h('div.pane-sub', sub),
];
