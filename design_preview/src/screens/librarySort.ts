import type { Title } from '../types';

// F4 (the project lead, 2026-11-01): the Sort chip's orders on Movies and TV Shows. The TV build's LibrarySort.java
// follows this. Every order but A–Z falls back to A–Z between titles it ranks the same, and puts the titles it can't
// rank (no year, no score, on no list it knows the order of) last, in A–Z.

export type SortId = 'az' | 'recent' | 'newest' | 'oldest' | 'rated';

export const SORTS: { id: SortId; label: string; short: string }[] = [
  { id: 'az', label: 'Alphabetical (A–Z)', short: 'A–Z' },
  { id: 'recent', label: 'Recently added to my lists', short: 'Recently added' },
  { id: 'newest', label: 'Newest release first', short: 'Newest' },
  { id: 'oldest', label: 'Oldest release first', short: 'Oldest' },
  { id: 'rated', label: 'Highest rated', short: 'Highest rated' },
];

/** What A–Z compares: no leading "The", "A" or "An", no accents or case, from the first letter or digit. */
export function sortName(name: string): string {
  return name
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/^[^\p{L}\p{N}]+/u, '')
    .trim()
    .replace(/^(the|an|a)\s+(?=\S)/i, '')
    .toLowerCase();
}

const cmp = <T>(a: T, b: T) => (a < b ? -1 : a > b ? 1 : 0);
const byName = (a: Title, b: Title) => cmp(sortName(a.name), sortName(b.name)) || cmp(a.name, b.name) || cmp(a.id, b.id);
/** A known value first; two unknown the same. */
const known = (a: boolean, b: boolean) => (a === b ? 0 : a ? -1 : 1);

/**
 * Ranks for "Recently added" from the lists' ids, each oldest first, in chip order (0 is newest): every list's last
 * title before any list's second-to-last, the earlier chip's first; a title on two lists takes its better place. No
 * list says when a title was added, so its place on the list stands in (BACKLOG.md).
 */
export function recentRanks(orders: string[][]): Map<string, number> {
  const best = new Map<string, [number, number]>();
  orders.forEach((ids, li) =>
    ids.forEach((id, i) => {
      const place: [number, number] = [ids.length - 1 - i, li];
      const was = best.get(id);
      if (!was || place[0] < was[0] || (place[0] === was[0] && place[1] < was[1])) best.set(id, place);
    }),
  );
  const all = [...best.entries()].sort((a, b) => a[1][0] - b[1][0] || a[1][1] - b[1][1]);
  return new Map(all.map(([id], i) => [id, i]));
}

export function sortTitles(titles: Title[], sort: SortId, recent?: Map<string, number>): Title[] {
  const out = [...titles];
  switch (sort) {
    case 'recent':
      return out.sort((a, b) => {
        const ra = recent?.get(a.id);
        const rb = recent?.get(b.id);
        return known(ra !== undefined, rb !== undefined) || (ra !== undefined && rb !== undefined ? ra - rb : 0) || byName(a, b);
      });
    case 'newest':
    case 'oldest': {
      const dir = sort === 'newest' ? -1 : 1;
      return out.sort((a, b) => known(a.year > 0, b.year > 0) || (a.year > 0 && b.year > 0 ? dir * (a.year - b.year) : 0) || byName(a, b));
    }
    case 'rated':
      return out.sort((a, b) => known(a.score > 0, b.score > 0) || (a.score > 0 && b.score > 0 ? b.score - a.score : 0) || byName(a, b));
    default:
      return out.sort(byName);
  }
}
