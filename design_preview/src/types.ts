export type Kind = 'movie' | 'show';

export type ServiceId =
  | 'netflix'
  | 'prime'
  | 'disney'
  | 'max'
  | 'appletvplus'
  | 'viaplay'
  | 'youtube'
  | 'appletv'
  | 'viaplaystore'
  | 'sfanytime'
  | 'amazonvideo'
  | 'rakuten';

/** sub = included with a subscription the household has. */
export type OfferType = 'sub' | 'free' | 'rent' | 'buy';

/** Picture qualities a store prices separately. */
export type Quality = 'SD' | 'HD' | '4K';

export interface Offer {
  service: ServiceId;
  type: OfferType;
  /**
   * Rent and buy only: the price in kronor per picture quality, as the launcher's PricedOffer keeps it. Missing when
   * no price source priced the store (most Swedish stores today): the offer then reads just "Rent" or "Buy", and the
   * store's app shows the price.
   */
  prices?: Partial<Record<Quality, number>>;
  /** The prices are per season (a show), so the cheapest reads "from". */
  seasonal?: boolean;
  /** Subscription and free offers: the picture quality, e.g. "4K · HDR". */
  quality?: string;
}

export type ListId = 'my' | 'household' | 'friends';

export interface Person {
  name: string;
  role: string;
}

export interface Episode {
  n: number;
  name: string;
  runtime: number;
}

export interface Season {
  n: number;
  episodes: Episode[];
}

export interface Title {
  id: string;
  kind: Kind;
  name: string;
  year: number;
  genres: string[];
  /** Minutes. Movies only. */
  runtime?: number;
  rating: string;
  score: number;
  synopsis: string;
  /** File name in public/img. */
  art: string;
  offers: Offer[];
  /** Imported watchlists the title is on. Empty for catalogue-only titles. */
  lists: ListId[];
  cast: Person[];
  crew: Person[];
  seasons?: Season[];
  /** Next episode to watch, for shows. */
  next?: { season: number; episode: number };
}
