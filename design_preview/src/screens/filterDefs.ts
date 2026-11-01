import { store } from '../core/store';
import { GENRES } from '../data/catalog';
import { isOwnIncluded, offersOf } from '../data/offers';
import type { Kind, Title } from '../types';

export interface FilterDef {
  id: string;
  label: string;
  options: string[];
  /** Text for the summary line, per option. Index 0 is "no filter". */
  short: string[];
  kinds: Kind[];
  test(t: Title, option: number): boolean;
}

const genreOptions = (kind: Kind) => GENRES.filter((g) => !['Food', 'Musical', 'Western', 'Horror'].includes(g) || kind === 'movie');

export function filterDefs(kind: Kind): FilterDef[] {
  const genres = genreOptions(kind);
  const defs: FilterDef[] = [
    {
      id: 'availability',
      label: 'Availability',
      options: ['Everything', 'Included only', 'Included + rentals'],
      short: ['', 'Included only', 'Included + rentals'],
      kinds: ['movie', 'show'],
      test: (t, i) => i === 0 || (i === 1 ? isOwnIncluded(t) : isOwnIncluded(t) || offersOf(t).some((o) => o.type === 'rent')),
    },
    {
      id: 'score',
      label: 'Review score',
      options: ['Any score', '7.0 and above', '8.0 and above'],
      short: ['', 'Review 7.0+', 'Review 8.0+'],
      kinds: ['movie', 'show'],
      test: (t, i) => t.score >= [0, 7, 8][i],
    },
    {
      id: 'genre',
      label: 'Genre',
      options: ['All genres', ...genres],
      short: ['', ...genres],
      kinds: ['movie', 'show'],
      test: (t, i) => i === 0 || t.genres.includes(genres[i - 1]),
    },
    {
      id: 'runtime',
      label: 'Length',
      options: ['Any length', 'Under 90 minutes', 'Under 2 hours', 'Under 2½ hours'],
      short: ['', 'Under 90 min', 'Under 2h', 'Under 2½h'],
      kinds: ['movie'],
      test: (t, i) => i === 0 || (t.runtime ?? 0) < [0, 90, 120, 150][i],
    },
    {
      id: 'watch',
      label: 'Watch state',
      options: ['Seen or not', 'Unwatched only', 'Seen only'],
      short: ['', 'Unwatched', 'Seen'],
      kinds: ['movie', 'show'],
      test: (t, i) => i === 0 || (i === 1 ? !store.isSeen(t.id) : store.isSeen(t.id)),
    },
  ];
  return defs.filter((d) => d.kinds.includes(kind));
}

export type FilterState = Record<string, number>;

/** The V1 default from Figma: "Included only · Review 7.0+". */
export const defaultFilters = (): FilterState => ({ availability: 1, score: 1 });

export const applyFilters = (defs: FilterDef[], state: FilterState, titles: Title[]) =>
  titles.filter((t) => defs.every((d) => d.test(t, state[d.id] ?? 0)));

export const activeCount = (defs: FilterDef[], state: FilterState) => defs.filter((d) => (state[d.id] ?? 0) !== 0).length;

export const summary = (defs: FilterDef[], state: FilterState) =>
  defs
    .map((d) => d.short[state[d.id] ?? 0])
    .filter(Boolean)
    .join(' · ');
