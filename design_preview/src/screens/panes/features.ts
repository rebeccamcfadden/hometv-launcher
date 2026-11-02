import { account } from '../../core/account';
import { h, type Child } from '../../core/dom';
import { keys } from '../../core/keys';
import { notes } from '../../core/notes';
import { featureOn, FEATURES, setFeature, settings, type FeatureId } from '../../core/settings';
import { placeById } from '../../data/places';
import { focusable } from '../../core/focus';
import { icon } from '../../ui/icons';
import { segmented } from '../../ui/widgets';
import type { Pane, PaneHost } from './pane';

/** "6 of 6 on": the section's value. */
export const featuresValue = () => `${FEATURES.filter((f) => featureOn(f.id)).length} of ${FEATURES.length} on`;

/**
 * Whether a feature's setup is finished (the project lead, 2026-11-02): First run switches off, when it ends, every
 * feature that is on without it, so nothing shows half set up.
 * - TMDB account: the TV is signed in.
 * - Phone notes: TMDB account is set up and the TV is listening (Phone notes' own switch, in its pane, is on). The TV
 *   can't tell a phone has paired until that phone sends a note, so listening is what counts.
 * - Price lookup: the Watchmode key works (F16's keys page); Movie of the Night is optional (the project lead,
 *   2026-11-02).
 * - Weather: a place is set (the TV's default counts).
 */
export function featureSetUp(id: FeatureId): boolean {
  switch (id) {
    case 'tmdb':
      return account.get() === 'in';
    case 'notes':
      return featureSetUp('tmdb') && notes.on();
    case 'prices':
      return keys.pricesReady();
    case 'weather':
      return !!placeById(settings.get().place);
  }
}

/**
 * Switches a feature on, and the one it needs (Phone notes needs TMDB account): an empty state's Turn on. Returns the
 * feature to set up first: the one it needed, when that was off too.
 */
export function turnOn(id: FeatureId): FeatureId {
  const f = FEATURES.find((x) => x.id === id)!;
  const first = f.needs && !featureOn(f.needs) ? f.needs : id;
  if (first !== id) setFeature(first, true);
  setFeature(id, true);
  return first;
}

/** The Launcher settings section that sets a feature up (Price lookup: F16's API keys). */
export const SETUP_SECTION: Partial<Record<FeatureId, 'account' | 'notes' | 'keys' | 'weather'>> = {
  tmdb: 'account',
  notes: 'notes',
  prices: 'keys',
  weather: 'weather',
};

/**
 * A feature's own switch (F34, Rebecca 2026-11-02): on top of its setup's pane, in First run and in Launcher settings,
 * so a feature is switched where it is set up and its row never goes. On, it turns on the one it needs too (Phone notes
 * needs TMDB account); `after` gets the feature to set up first. Phone notes' switch is its listening switch.
 */
export function featureBar(id: FeatureId, after: (first: FeatureId | null) => void, entry = false): HTMLElement {
  const f = FEATURES.find((x) => x.id === id)!;
  const on = featureOn(id);
  return h(
    'div.perf-row.feat-bar',
    h('div.perf-text', h('div.perf-title', f.label), h('div.perf-sub', on ? f.sub : `Off: ${f.off}`)),
    h(
      'div.perf-ctl',
      segmented<'on' | 'off'>({
        options: [
          ['on', 'On'],
          ['off', 'Off'],
        ],
        get: () => (featureOn(id) ? 'on' : 'off'),
        set: (v) => {
          if (v === 'on') after(featureOn(id) ? null : turnOn(id));
          else {
            setFeature(id, false);
            after(null);
          }
        },
        group: `seg-feat-${id}`,
        small: true,
        entry,
      }),
    ),
  );
}

/**
 * A feature's pane while it is off (F14; F34): its row stays, reading Off, and the pane says so above the feature's
 * switch, which turns it on right here and shows its setup.
 */
export function featureOffPane(id: FeatureId, section: string, after: (first: FeatureId | null) => void): Child {
  const f = FEATURES.find((x) => x.id === id)!;
  const needs = f.needs && !featureOn(f.needs) ? FEATURES.find((x) => x.id === f.needs)! : null;
  return [
    h('div.eyebrow', section),
    h('div.pane-title', `${f.label} is off`),
    h(
      'div.pane-sub',
      needs
        ? `${f.label} needs ${needs.label}, which is off. Switch it on to turn on both, then sign in.`
        : `It doesn’t run while it is off. Switch it on to set it up here.`,
    ),
    h('div.feat-bars', featureBar(id, after, true)),
  ];
}

/** A feature's state in a word or two, for the Features overview and First run's tabs. */
export function featureState(id: FeatureId): { text: string; done: boolean } {
  const f = FEATURES.find((x) => x.id === id)!;
  if (f.needs && !featureOn(f.needs)) return { text: `Off · needs ${FEATURES.find((x) => x.id === f.needs)!.label}`, done: false };
  if (!featureOn(id)) return { text: 'Off', done: false };
  return featureSetUp(id) ? { text: 'On · set up', done: true } : { text: 'On · not set up yet', done: false };
}

/**
 * Features (F14; F34, Rebecca 2026-11-02): no switches here any more. Each feature is switched on its own setup's
 * pane (featureBar); this lists the four with their state, and OK on one opens its setup.
 */
export const featuresPane = (host: PaneHost): Pane => ({
  render: () => [
    h('div.eyebrow', 'Features'),
    h('div.pane-title', 'Features'),
    h(
      'div.pane-sub',
      host.setup
        ? 'Everything but Phone notes starts on. Each is set up on its own row below, where you can also switch it on or off; one not set up when you finish is switched off.'
        : 'What this TV runs. Each is switched on or off in its own section; a feature that is off never starts.',
    ),
    h(
      'div.perf-rows',
      FEATURES.map((f, i) => {
        const s = featureState(f.id);
        return focusable(
          h(
            'div.lrow.feat-link',
            h('div.lr-main', h('div.lr-title', f.label), h('div.lr-sub', featureOn(f.id) ? f.sub : `Off: ${f.off}`)),
            h(`span.status.status--${s.done || !featureOn(f.id) ? 'muted' : 'accent'}`, s.text),
            h('span.lr-icon', { html: icon('chevRight', 26) }),
          ),
          { group: 'feat-links', key: `feat-link-${f.id}`, isDefault: i === 0, onOk: () => host.openSetup?.(f.id) },
        );
      }),
    ),
    h('div.pane-note', 'A pushed config.json can set these too.'),
  ],
});