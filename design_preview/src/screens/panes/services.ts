import { app } from '../../core/app';
import { h } from '../../core/dom';
import { focusable } from '../../core/focus';
import { settings } from '../../core/settings';
import { appName, PICK_SERVICES, PICK_STORES, SERVICES, type PickService } from '../../data/services';
import { icon } from '../../ui/icons';
import { paneHeadCount, type Pane, type PaneHost } from './pane';

const MORE = PICK_SERVICES.filter((s) => s.more);
const MAIN = PICK_SERVICES.filter((s) => !s.more);

/** "Free Installed", "No app". */
const caption = (s: { installed: boolean; free?: string }) =>
  h('span.tile-hints', s.free ? h('span.hint', s.free) : null, h(`span.hint${s.installed ? '.hint--good' : ''}`, s.installed ? 'Installed' : 'No app'));

/**
 * Streaming services (V2 design r2-services B, tiles): a four-column grid of wordmark tiles with a corner tick and a
 * caption (Installed, No app, Free). OK ticks; ticked services count as Included in Movies and TV Shows at once.
 * "+7 More services" opens a second grid, which stays open while anything in it is ticked. Free services tick like
 * the rest.
 */
export function servicesPane(host: PaneHost): Pane {
  let open = false;
  const ticked = () => settings.get().services;
  const moreTicked = () => MORE.some((s) => ticked().includes(s.id));

  const tile = (s: PickService, isEntry: boolean) => {
    const on = ticked().includes(s.id);
    const el = focusable(
      h(
        'div.svc-tile',
        h(
          `div.stile${on ? '.is-on' : ''}`,
          h('span.mark', { style: `font-weight:${s.weight}` }, s.mark),
          h(`div.tick${on ? '' : '.is-off'}`, on ? { html: icon('check', 20) } : null),
        ),
        h('div.stile-cap', h('span', s.name), caption(s)),
      ),
      {
        group: 'svc',
        key: `svc-${s.id}`,
        isDefault: isEntry,
        onOk: () =>
          settings.set({ services: on ? ticked().filter((x) => x !== s.id) : [...ticked(), s.id] }),
      },
    );
    if (isEntry) el.dataset.entry = '';
    return el;
  };

  const moreTile = () => {
    const shown = open || moreTicked();
    return focusable(
      h(
        'div.svc-tile',
        h('div.stile.is-more', h('span.more-big', shown ? '−' : `+${MORE.length}`), h('span', shown ? 'Fewer services' : 'More services')),
        h('div.stile-cap', h('span', shown ? (moreTicked() ? 'Open while one is ticked' : `Hide the ${MORE.length} below`) : 'Hayu, MUBI, Plex…')),
      ),
      {
        group: 'svc',
        key: 'svc-more',
        onOk: () => {
          if (shown && moreTicked()) return app.toast('Untick the services below to hide them');
          open = !shown;
          host.rerender();
        },
      },
    );
  };

  return {
    render() {
      const shown = open || moreTicked();
      const all = shown ? PICK_SERVICES : MAIN;
      const n = all.filter((s) => ticked().includes(s.id)).length;
      return [
        ...paneHeadCount(
          'Streaming services',
          'What does the household pay for?',
          `${n} of ${all.length} ticked`,
          'Titles on ticked services show as Included. Installed apps were ticked to start with.',
        ),
        h('div.tgrid', MAIN.map((s, i) => tile(s, i === 0)), moreTile()),
        shown ? [h('div.grp-h', 'More services'), h('div.tgrid', MORE.map((s) => tile(s, false)))] : null,
      ];
    },
  };
}

/**
 * Stores for rent and buy (V2 design r2-services A, rows for stores): one row each, with a checkbox, the app that
 * renting opens and whether it is on the TV. Prices on cards and in Where to watch come only from ticked stores.
 */
export function storesPane(): Pane {
  return {
    render() {
      const ticked = settings.get().stores;
      return [
        ...paneHeadCount(
          'Stores for rent and buy',
          'Where may we show prices from?',
          `${ticked.length} of ${PICK_STORES.length} ticked`,
          'Prices in Where to watch and on cards come from ticked stores. Renting or buying opens the store’s app.',
        ),
        h(
          'div.store-rows',
          PICK_STORES.map((s, i) => {
            const on = ticked.includes(s.id);
            const el = focusable(
              h(
                'div.lrow.lrow--svc',
                h(`div.cbox${on ? '.is-on' : ''}`, { html: icon('check', 22) }),
                h('div.lr-main', h('div.lr-title', SERVICES[s.id].name)),
                h('span.opens', `Opens in the ${appName(s.id)} app`),
                h(`span.hint${s.installed ? '.hint--good' : ''}`, s.installed ? 'Installed' : 'No app'),
              ),
              {
                group: 'stores',
                key: `store-${s.id}`,
                isDefault: i === 0,
                onOk: () => settings.set({ stores: on ? ticked.filter((x) => x !== s.id) : [...ticked, s.id] }),
              },
            );
            if (i === 0) el.dataset.entry = '';
            return el;
          }),
        ),
        h('div.pane-note', 'Prices are in kronor and may differ in the store. Google Play isn’t sold in Sweden.'),
      ];
    },
  };
}
