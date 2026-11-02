import { account } from '../../core/account';
import type { Key } from '../../core/app';
import { h, type Child } from '../../core/dom';
import { notes } from '../../core/notes';
import { featureOn, FEATURES, setFeature, settings, type FeatureId } from '../../core/settings';
import { placeById } from '../../data/places';
import { button, segmented } from '../../ui/widgets';
import type { Pane, PaneHost } from './pane';

/** "6 of 6 on": the section's value. */
export const featuresValue = () => `${FEATURES.filter((f) => featureOn(f.id)).length} of ${FEATURES.length} on`;

/**
 * Whether a feature's setup is finished (the project lead, 2026-11-02): First run switches off, when it ends, every
 * feature that is on without it, so nothing shows half set up.
 * - TMDB account: the TV is signed in.
 * - Phone notes: TMDB account is set up and the TV is listening (Phone notes' own switch, in its pane, is on). The TV
 *   can't tell a phone has paired until that phone sends a note, so listening is what counts.
 * - Price lookup: the Watchmode and Movie of the Night keys are set (F16, Builder E's keys page; never in this mock
 *   until #83 lands, so Price lookup is switched off at the end of First run here).
 * - Weather: a place is set (the TV's default counts).
 */
export function featureSetUp(id: FeatureId): boolean {
  switch (id) {
    case 'tmdb':
      return account.get() === 'in';
    case 'notes':
      return featureSetUp('tmdb') && notes.on();
    case 'prices':
      return priceKeysSet();
    case 'weather':
      return !!placeById(settings.get().place);
  }
}

/** Builder E's F16 replaces this with keys.pricesReady(). */
export const priceKeysSet = () => false;

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

/** The Launcher settings section that sets a feature up (Price lookup's keys page is F16's: none here yet). */
export const SETUP_SECTION: Partial<Record<FeatureId, 'account' | 'notes' | 'weather'>> = {
  tmdb: 'account',
  notes: 'notes',
  weather: 'weather',
};

/**
 * A Launcher settings section whose feature is off (F14, the project lead 2026-11-01): it stays in the list, reading
 * Off, and its pane says so. **Turn on** (2026-11-02) switches the feature on (and the one it needs) and goes straight
 * into its setup; Back from there returns to the page.
 */
export function featureOffPane(id: FeatureId, section: string, turnOnAndSetUp: () => void): Child {
  const f = FEATURES.find((x) => x.id === id)!;
  const needs = f.needs && !featureOn(f.needs) ? FEATURES.find((x) => x.id === f.needs)! : null;
  const on = button('Turn on', { icon: 'power', primary: true, key: 'turn-on', onOk: turnOnAndSetUp });
  on.dataset.entry = '';
  return [
    h('div.eyebrow', section),
    h('div.pane-title', `${f.label} is off`),
    h(
      'div.pane-sub',
      needs
        ? `${f.label} needs ${needs.label}, which is off. Turn on switches on both, then sets up ${needs.label}.`
        : `This household has ${f.label} switched off, so it doesn’t run. Turn on switches it on and sets it up.`,
    ),
    h('div.pane-actions', on),
    h('div.pane-note', `Off: ${f.off}. Also in Launcher settings › Features.`),
  ];
}
/**
 * Launcher settings › Features (F14, decided 2026-11-01; no Figma frame): one row per feature, each On or Off, laid
 * out like Performance. A feature switched off never starts: its pages, its settings section and its network calls
 * go. A row whose feature needs another that is off (Phone notes without TMDB account) says so and can't be changed.
 */
export const featuresPane = (host: PaneHost): Pane => ({
  /** ↓ goes to the next row's control on its selected option; → stays within the control, as in Performance. */
  onKey(key: Key, focused: HTMLElement) {
    const at = FEATURES.findIndex((f) => focused.dataset.key?.startsWith(`seg-feat-${f.id}-`));
    if (at < 0) return false;
    if (key === 'right') {
      if (focused.dataset.key?.endsWith('-on')) host.focusKey(`seg-feat-${FEATURES[at].id}-off`);
      return true;
    }
    if (key === 'left' && focused.dataset.key?.endsWith('-off')) {
      host.focusKey(`seg-feat-${FEATURES[at].id}-on`);
      return true;
    }
    if (key !== 'down' && key !== 'up') return false;
    const step = key === 'down' ? 1 : -1;
    for (let i = at + step; i >= 0 && i < FEATURES.length; i += step) {
      const f = FEATURES[i];
      if (f.needs && !featureOn(f.needs)) continue;
      host.focusKey(`seg-feat-${f.id}-${settings.get().features[f.id] ? 'on' : 'off'}`);
      return true;
    }
    // Past the last row: in First run on to the panel's footer (Next); in Launcher settings ↓ stays.
    return key === 'down' && !host.setup;
  },
  render: () => [
    h('div.eyebrow', 'Features'),
    h('div.pane-title', 'Features'),
    h(
      'div.pane-sub',
      host.setup
        ? 'Everything starts on, and each is set up next. Switch off what this household won’t use; one not set up when you finish is switched off.'
        : 'Turn on what this household uses. A feature that is off never starts: no page and nothing fetched for it.',
    ),
    h(
      'div.perf-rows',
      FEATURES.map((f, i) => {
        const blocked = !!f.needs && !featureOn(f.needs);
        const needs = blocked ? FEATURES.find((x) => x.id === f.needs)!.label : '';
        const on = settings.get().features[f.id];
        const control = blocked
          ? h('span.feat-blocked', `Off · needs ${needs}`)
          : segmented<'on' | 'off'>({
              options: [
                ['on', 'On'],
                ['off', 'Off'],
              ],
              get: () => (settings.get().features[f.id] ? 'on' : 'off'),
              set: (v) => {
                const was = featureOn(f.id);
                setFeature(f.id, v === 'on');
                // First run (F14): a feature turned on is set up at once, on its own tab; Back to Features returns here.
                if (v === 'on' && !was) host.openSetup?.(f.id);
              },
              group: `seg-feat-${f.id}`,
              small: true,
              entry: i === 0,
            });
        return h(
          'div.perf-row',
          h('div.perf-text', h('div.perf-title', f.label), h('div.perf-sub', on && !blocked ? f.sub : `Off: ${f.off}`)),
          h('div.perf-ctl', control),
        );
      }),
    ),
    h('div.pane-note', 'A pushed config.json can set these too.'),
  ],
});
