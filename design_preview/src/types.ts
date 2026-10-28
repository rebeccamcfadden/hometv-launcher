export type Kind = 'movie' | 'show';

export type ServiceId =
  | 'netflix'
  | 'prime'
  | 'disney'
  | 'max'
  | 'appletvplus'
  | 'hulu'
  | 'youtube'
  | 'appletv'
  | 'googletv';

/** sub = included with a subscription the household has. */
export type OfferType = 'sub' | 'free' | 'rent' | 'buy';

export interface Offer {
  service: ServiceId;
  type: OfferType;
  price?: number;
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
