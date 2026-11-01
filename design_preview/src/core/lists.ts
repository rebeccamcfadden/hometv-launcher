import { account } from './account';
import { store } from './store';
import { TITLES } from '../data/catalog';
import type { Title } from '../types';

// The household's lists as Movies, TV Shows and Pick for me show them: My list, then one chip per list. Signed in,
// those are the account's TMDB lists ticked "Show" in Account → Choose lists, by their TMDB names and in the account's
// order. Before any sign-in, the mock Household and Friends stand in; after a sign-out, only My list is left.

export interface ShownList {
  /** 'my', a TMDB list id, or the mock 'household' / 'friends'. */
  id: string;
  name: string;
  has: (t: Title) => boolean;
}

/** Which mock titles each TMDB list holds (the TV reads them from TMDB). */
const INDEX = new Map(TITLES.map((t, i) => [t.id, i]));
const MEMBERS: Record<string, (t: Title, i: number) => boolean> = {
  family: (t) => t.lists.includes('household'),
  tips: (t) => t.lists.includes('friends'),
  weekend: (t, i) => t.kind === 'movie' && i % 2 === 0,
  club: (t, i) => t.kind === 'movie' && i % 3 === 1,
  together: (_t, i) => i % 5 === 0,
  docs: (t) => t.genres.includes('Documentary'),
  kids: (t) => t.genres.includes('Animation') || t.rating === 'PG',
};
/** Titles the title menu put on a TMDB list or took off it, by "list:title" (on the TV, the list's saved copy). */
const edits = new Map<string, boolean>();
const holdsCache = new Map<string, (t: Title) => boolean>();
const holds = (id: string) => {
  let fn = holdsCache.get(id);
  if (!fn) holdsCache.set(id, (fn = (t: Title) => edits.get(`${id}:${t.id}`) ?? MEMBERS[id]?.(t, INDEX.get(t.id) ?? 0) ?? false));
  return fn;
};

const MY: ShownList = { id: 'my', name: 'My list', has: (t) => store.inMyList(t.id) };
const MOCK: ShownList[] = [
  { id: 'household', name: 'Household', has: (t) => t.lists.includes('household') },
  { id: 'friends', name: 'Friends', has: (t) => t.lists.includes('friends') },
];

/** At most this many TMDB lists get a chip (the project lead, 2026-10-31); more shown lists are still in All lists. */
export const CHIP_CAP = 6;

/** {@link lists.imported}, rebuilt only when the account's state or its Show / Hidden ticks change. */
let importedKey = '';
let importedCache: ShownList[] = [];

export const lists = {
  my: MY,
  /**
   * The lists the title menu can put a title on (titleMenu.ts): signed in, the shown TMDB lists, past the cap too.
   * Signed out there are none: the mock Household and Friends are only read.
   */
  editable(): ShownList[] {
    return account.get() === 'in' ? lists.imported() : [];
  },
  /** Puts a title on a TMDB list or takes it off, as TMDB answered (the title menu). */
  setOn(id: string, t: Title, on: boolean) {
    edits.set(`${id}:${t.id}`, on);
  },
  /** Every shown list after My list, in the account's order: chips and those past the cap alike. */
  imported(): ShownList[] {
    const signedIn = account.get() === 'in';
    const key = signedIn
      ? 'in:' + account.lists().filter((l) => l.shown).map((l) => `${l.id}=${l.name}`).join('|')
      : account.everSignedIn() ? 'out' : 'mock';
    if (key !== importedKey) {
      importedKey = key;
      importedCache = signedIn
        ? account.lists().filter((l) => l.shown).map((l) => ({ id: l.id, name: l.name, has: holds(l.id) }))
        : key === 'out' ? [] : MOCK;
    }
    return importedCache;
  },
  /** The shown lists that get a chip: the first {@link CHIP_CAP}. */
  chipped(): ShownList[] {
    return lists.imported().slice(0, CHIP_CAP);
  },
  /** Shown lists past the cap: no chip, but their titles are in All lists. */
  overflow(): ShownList[] {
    return lists.imported().slice(CHIP_CAP);
  },
  /** My list, then {@link chipped}: the chips after All lists, and Pick for me's lists. */
  all(): ShownList[] {
    return [MY, ...lists.chipped()];
  },
  byId: (id: string) => lists.all().find((l) => l.id === id),
  /** On My list or any shown list, past the cap included: what All lists holds. */
  saved: (t: Title) => [MY, ...lists.imported()].some((l) => l.has(t)),
  /** My list and every shown list, for the scope line ("8 imported watchlists"). */
  watchlists: () => 1 + lists.imported().length,
  /** Titles on a TMDB list, shown or not, for Choose lists. */
  count: (id: string) => TITLES.filter(holds(id)).length,
  /** "My list, Family picks & Friends’ tips"; past four, "My list, Family picks & 4 more". */
  names(): string {
    const n = [MY, ...lists.imported()].map((l) => l.name);
    if (n.length > 4) return `${n[0]}, ${n[1]} & ${n.length - 2} more`;
    return n.length < 2 ? n.join('') : `${n.slice(0, -1).join(', ')} & ${n[n.length - 1]}`;
  },
  /** The card line's source: the first list in the account's order holding the title, else the catalogue. */
  provenance(t: Title): string {
    return [MY, ...lists.imported()].find((l) => l.has(t))?.name ?? 'Catalogue';
  },
};
