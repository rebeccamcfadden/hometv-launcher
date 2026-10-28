import type { Offer, ServiceId, Title } from '../types';
import { SERVICES } from './services';

export const price = (n: number) => `$${n.toFixed(2)}`;

export const isIncludedOffer = (o: Offer) =>
  o.type === 'free' || (o.type === 'sub' && SERVICES[o.service].subscribed);

export const isIncluded = (t: Title) => t.offers.some(isIncludedOffer);

/** The offer the Watch button uses: included first, then the cheapest rental, then the cheapest purchase. */
export function bestOffer(t: Title): Offer | undefined {
  const inc = t.offers.find(isIncludedOffer);
  if (inc) return inc;
  const byPrice = (a: Offer, b: Offer) => (a.price ?? 0) - (b.price ?? 0);
  return t.offers.filter((o) => o.type === 'rent').sort(byPrice)[0] ?? t.offers.filter((o) => o.type === 'buy').sort(byPrice)[0];
}

export function offerLabel(o: Offer | undefined): string {
  if (!o) return 'Not available';
  const name = SERVICES[o.service].name;
  if (o.type === 'sub') return `${name} · Included`;
  if (o.type === 'free') return `${name} · Free`;
  return `${name} · ${o.type === 'rent' ? 'Rent' : 'Buy'} ${price(o.price ?? 0)}`;
}

/** One entry per service, with its rent and buy prices merged. */
export interface ServiceOffers {
  service: ServiceId;
  included: boolean;
  free: boolean;
  rent?: number;
  buy?: number;
  quality: string;
}

export function offersByService(t: Title): ServiceOffers[] {
  const map = new Map<ServiceId, ServiceOffers>();
  for (const o of t.offers) {
    const e = map.get(o.service) ?? { service: o.service, included: false, free: false, quality: o.quality ?? 'HD' };
    if (o.type === 'sub' && SERVICES[o.service].subscribed) e.included = true;
    if (o.type === 'free') e.free = true;
    if (o.type === 'rent') e.rent = o.price;
    if (o.type === 'buy') e.buy = o.price;
    map.set(o.service, e);
  }
  return [...map.values()].sort((a, b) => Number(b.included || b.free) - Number(a.included || a.free));
}

export function runtimeLabel(min: number): string {
  const h = Math.floor(min / 60);
  const m = min % 60;
  return h ? `${h}h ${m}m` : `${m}m`;
}
