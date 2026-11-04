import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { cheapest, money, qualitiesOf, type ServiceOffers } from '../data/offers';
import { appName, SERVICES } from '../data/services';
import type { Offer, OfferType, Quality, Title } from '../types';
import { button, chip } from '../ui/widgets';

/** "4K costs 10–30 kr more here", from the store's HD and 4K prices; null when it has no 4K or no difference. */
function fourKNote(s: ServiceOffers): string | null {
  const diffs: number[] = [];
  for (const o of [s.rent, s.buy]) {
    const hd = o?.prices?.HD;
    const uhd = o?.prices?.['4K'];
    if (hd !== undefined && uhd !== undefined && uhd > hd) diffs.push(uhd - hd);
  }
  if (!diffs.length) return null;
  const lo = Math.min(...diffs);
  const hi = Math.max(...diffs);
  const o = s.rent ?? s.buy;
  // The same money as the price buttons (money): dollars only for a US offer while several countries are shown.
  const dollars = !!o && money(1, o).startsWith('$');
  const unit = dollars ? (n: number) => `$${n.toFixed(2)}` : (n: number) => `${n}`;
  return `4K costs ${lo === hi ? unit(lo) : `${unit(lo)}–${unit(hi)}`}${dollars ? '' : ' kr'} more here`;
}

/**
 * The rent/buy dialog (07-prices A): the store's name, an HD/4K switch when it prices both, two big price buttons,
 * Cancel, and a line that says payment finishes in the store's app. A
 * store with no price has the same buttons without amounts; its app shows the price.
 */
export function rentDialog(t: Title, s: ServiceOffers): Screen {
  const name = SERVICES[s.service].name;
  const app_ = appName(s.service);
  const qualities = qualitiesOf(s);
  const lead = s.rent ?? s.buy;
  // The quality the dialog opens on: that of the store's cheapest rental (else purchase), HD when nothing is priced.
  let quality: Quality | null =
    qualities.find((q) => lead?.prices?.[q] === (lead && cheapest(lead))) ?? qualities[0] ?? null;

  const qualRow = h('div.qual');
  const prices = h('div.dlg-prices');
  const detail = h('div.dialog-detail');

  const priceButton = (type: OfferType, o: Offer, isDefault: boolean) => {
    const amount = quality ? o.prices?.[quality] : undefined;
    const season = o.seasonal ? ' a season' : '';
    const k = `${type === 'rent' ? 'Rent' : 'Buy'}${season}${quality ? ` · ${quality}` : ''}`;
    const v = amount !== undefined ? money(amount, o) : quality ? '–' : 'Price in the app';
    const d =
      type === 'rent'
        ? '48 hours to finish once started'
        : o.seasonal
          ? `Per season, yours to keep in ${app_}`
          : `Yours to keep in ${app_}`;
    return focusable(h('div.pbtn', h('div.k', k), h('div.v', v), h('div.d', d)), {
      group: 'prices',
      key: `p-${type}`,
      isDefault,
      onOk: () => {
        app.pop();
        app.toast(`Opening ${app_} to ${type} ${t.name}${quality ? ` in ${quality}` : ''}…`);
      },
    });
  };

  const paint = () => {
    const note = fourKNote(s);
    replace(
      qualRow,
      qualities.length > 1
        ? [
            qualities.map((q) =>
              chip(q, {
                selected: q === quality,
                group: 'qual',
                key: `q-${q}`,
                isDefault: q === quality,
                onOk: () => {
                  quality = q;
                  paint();
                  app.refocus();
                },
              }),
            ),
            note ? h('span.qual-note', note) : null,
          ]
        : null,
    );
    qualRow.hidden = qualities.length < 2;
    // Only the offers the store has in the chosen quality; both when nothing is priced.
    const shown = ([['rent', s.rent], ['buy', s.buy]] as [OfferType, Offer | undefined][]).filter(
      ([, o]) => o && (!quality || o.prices?.[quality] !== undefined),
    ) as [OfferType, Offer][];
    replace(prices, shown.map(([type, o], i) => priceButton(type, o, i === 0)));

    // No "cheaper elsewhere" line (Rebecca, 2026-11-04 18:39): Where to watch lists the stores in price order.
    replace(detail, `Payment finishes in the ${app_} app.`);
  };
  paint();

  const cancel = button('Cancel', { key: 'cancel', onOk: () => app.pop() });
  const el = h(
    'div.screen.layer-dialog',
    h('div.scrim'),
    h('div.dialog.dialog--rent', h('div.dialog-title', `${t.name} on ${name}`), qualRow, prices, h('div.dialog-actions', cancel), detail),
  );
  return {
    label: `${t.name} on ${name}`,
    el,
    layer: 'dialog',
    defaultFocus: () => prices.querySelector<HTMLElement>('[data-default]') ?? cancel,
  };
}
