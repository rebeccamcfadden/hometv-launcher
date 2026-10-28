import { TITLES } from '../data/catalog';

// Household state that outlives a screen: what the toggles in the title preview change.
interface Persisted {
  myList: string[];
  seen: string[];
  favorites: string[];
  recent: string[];
  viewed: string[];
}

const KEY = 'cleanyourtv-preview-v1';

const defaults = (): Persisted => ({
  myList: TITLES.filter((t) => t.lists.includes('my')).map((t) => t.id),
  seen: ['knives-out', 'budapest'],
  favorites: ['arrival'],
  recent: ['dune', 'amy adams', 'sci-fi', 'carnival row'],
  viewed: ['arrival', 'dune', 'past-lives', 'coda', 'moon', 'severance'],
});

function load(): Persisted {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...defaults(), ...(JSON.parse(raw) as Partial<Persisted>) };
  } catch {
    // Corrupt or blocked storage: start fresh.
  }
  return defaults();
}

const data = load();
const myList = new Set(data.myList);
const seen = new Set(data.seen);
const favorites = new Set(data.favorites);

function save() {
  const out: Persisted = {
    myList: [...myList],
    seen: [...seen],
    favorites: [...favorites],
    recent: data.recent,
    viewed: data.viewed,
  };
  localStorage.setItem(KEY, JSON.stringify(out));
}

function toggle(set: Set<string>, id: string): boolean {
  if (set.has(id)) set.delete(id);
  else set.add(id);
  save();
  return set.has(id);
}

export const store = {
  inMyList: (id: string) => myList.has(id),
  isSeen: (id: string) => seen.has(id),
  isFavorite: (id: string) => favorites.has(id),
  toggleMyList: (id: string) => toggle(myList, id),
  toggleSeen: (id: string) => toggle(seen, id),
  toggleFavorite: (id: string) => toggle(favorites, id),
  myListIds: () => [...myList],
  recent: () => data.recent,
  viewed: () => data.viewed,
  pushRecent(q: string) {
    const v = q.trim().toLowerCase();
    if (!v) return;
    data.recent = [v, ...data.recent.filter((r) => r !== v)].slice(0, 5);
    save();
  },
  pushViewed(id: string) {
    data.viewed = [id, ...data.viewed.filter((r) => r !== id)].slice(0, 10);
    save();
  },
  reset() {
    localStorage.removeItem(KEY);
    location.reload();
  },
};
