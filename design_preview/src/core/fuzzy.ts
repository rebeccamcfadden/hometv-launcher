import { GENRES, TITLES } from '../data/catalog';
import type { Title } from '../types';

export interface Match {
  score: number;
  indices: number[];
}

// One base letter per character, so indices into the folded string match the original.
const fold = (s: string) =>
  [...s.toLowerCase()].map((c) => c.normalize('NFD')[0]).join('');

/** Damerau-Levenshtein (optimal string alignment) distance. */
function osa(a: string, b: string): number {
  const d: number[][] = Array.from({ length: a.length + 1 }, (_, i) => [i, ...Array(b.length).fill(0)]);
  for (let j = 1; j <= b.length; j++) d[0][j] = j;
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
    }
  }
  return d[a.length][b.length];
}

const isWordStart = (t: string, i: number) => i === 0 || /[\s\-:.'’&]/.test(t[i - 1]);

/**
 * Subsequence match ("arvl" → ARriVaL), scored for word starts and runs.
 * Falls back to one or two typos against a single word ("arrivsl" → Arrival).
 */
export function fuzzyMatch(query: string, text: string): Match | null {
  const q = fold(query).replace(/\s+/g, ' ').trim();
  if (!q) return null;
  const t = fold(text);

  const indices: number[] = [];
  let qi = 0;
  for (let i = 0; i < t.length && qi < q.length; i++) {
    if (t[i] === q[qi]) {
      indices.push(i);
      qi++;
    }
  }
  if (qi === q.length) {
    let score = 100 - t.length * 0.4;
    for (let k = 0; k < indices.length; k++) {
      if (isWordStart(t, indices[k])) score += 12;
      if (k > 0) {
        const gap = indices[k] - indices[k - 1] - 1;
        score += gap === 0 ? 8 : -Math.min(gap, 8);
      }
    }
    if (t.startsWith(q)) score += 30;
    if (t === q) score += 40;
    return { score, indices };
  }

  if (q.length >= 4 && !q.includes(' ')) {
    const budget = q.length >= 7 ? 2 : 1;
    for (const m of t.matchAll(/[^\s\-:.'’&]+/g)) {
      const word = m[0];
      const d = Math.min(osa(q, word), osa(q, word.slice(0, q.length)));
      if (d <= budget) {
        const start = m.index ?? 0;
        const len = Math.min(word.length, q.length);
        return { score: 40 - d * 10, indices: Array.from({ length: len }, (_, k) => start + k) };
      }
    }
  }
  return null;
}

export type HitKind = 'title' | 'person' | 'genre';

export interface Hit {
  kind: HitKind;
  label: string;
  tag: string;
  match: Match;
  title?: Title;
  /** For people: titles they appear in. */
  titles?: Title[];
}

const people = new Map<string, Title[]>();
for (const t of TITLES) {
  for (const p of [...t.cast, ...t.crew]) {
    const list = people.get(p.name) ?? [];
    if (!list.includes(t)) list.push(t);
    people.set(p.name, list);
  }
}

export const titlesForPerson = (name: string) => people.get(name) ?? [];

export function search(query: string): { titles: Hit[]; people: Hit[]; genres: Hit[]; suggestions: Hit[] } {
  const titles: Hit[] = [];
  for (const t of TITLES) {
    const m = fuzzyMatch(query, t.name);
    if (m) titles.push({ kind: 'title', label: t.name, tag: t.kind === 'movie' ? 'Movie' : 'TV show', match: m, title: t });
  }
  const ppl: Hit[] = [];
  for (const [name, list] of people) {
    const m = fuzzyMatch(query, name);
    if (m) ppl.push({ kind: 'person', label: name, tag: 'Person', match: { ...m, score: m.score - 6 }, titles: list });
  }
  const genres: Hit[] = [];
  for (const g of GENRES) {
    const m = fuzzyMatch(query, g);
    if (m) genres.push({ kind: 'genre', label: g, tag: 'Genre', match: { ...m, score: m.score - 4 } });
  }
  const byScore = (a: Hit, b: Hit) => b.match.score - a.match.score;
  titles.sort(byScore);
  ppl.sort(byScore);
  genres.sort(byScore);
  const suggestions = [...titles.slice(0, 4), ...ppl.slice(0, 2), ...genres.slice(0, 1)].sort(byScore).slice(0, 4);
  return { titles, people: ppl, genres, suggestions };
}

/** Titles whose genre list includes `genre`, for genre suggestions and chips. */
export const titlesInGenre = (genre: string) => TITLES.filter((t) => t.genres.includes(genre));
