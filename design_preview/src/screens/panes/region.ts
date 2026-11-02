import { h } from '../../core/dom';
import { REGION_LABELS, setRegion, settings, type Region } from '../../core/settings';
import { segmented } from '../../ui/widgets';
import type { Pane } from './pane';

/**
 * Launcher settings › Region and First run's Region step (F12, moved to Builder A 2026-11-02; Family install step 2,
 * the launcher's Region and RegionChoice): Sweden or the United States. The region decides which streaming services
 * and stores Launcher settings lists, the currency prices are in, and the country TMDB's availability is read for.
 * Each region keeps its own ticks, so switching back finds them as they were. Sweden unless set.
 */
export const regionPane = (): Pane => ({
  render: () => {
    const us = settings.get().region === 'us';
    return [
      h('div.eyebrow', 'Region'),
      h('div.pane-title', 'Where is this TV?'),
      h('div.pane-sub', 'The streaming services, stores and prices this TV shows are this country\u2019s.'),
      h(
        'div.pane-seg',
        segmented<Region>({
          options: (Object.keys(REGION_LABELS) as Region[]).map((r) => [r, REGION_LABELS[r]]),
          get: () => settings.get().region,
          set: (r) => setRegion(r),
          group: 'seg-region',
          entry: true,
        }),
      ),
      h(
        'div.pane-note',
        us
          ? 'US streaming services and stores (Hulu, Peacock, Fandango at Home, Google TV\u2026), prices in dollars, and what TMDB says is on in the US.'
          : 'Swedish streaming services and stores (Viaplay, SVT Play, SF Anytime\u2026), prices in kronor, and what TMDB says is on in Sweden.',
      ),
      h('div.pane-note', 'Each region keeps its own store ticks; your streaming services stay ticked. In this prototype the titles keep their Swedish offers and prices.'),
    ];
  },
});
