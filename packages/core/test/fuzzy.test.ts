// The search matching, on a small catalogue of its own.

import test from 'node:test';
import assert from 'node:assert/strict';
import { createSearch, fuzzyMatch } from '../src/fuzzy.ts';
import type { Title } from '../src/types.ts';

const title = (id: string, name: string, kind: 'movie' | 'show', genres: string[], cast: string[]): Title => ({
  id,
  kind,
  name,
  year: 2016,
  genres,
  rating: '12',
  score: 80,
  synopsis: '',
  art: '',
  offers: [],
  lists: [],
  cast: cast.map((n) => ({ name: n, role: '' })),
  crew: [],
});

const CATALOGUE = [
  title('arrival', 'Arrival', 'movie', ['Sci-Fi', 'Drama'], ['Amy Adams']),
  title('amelie', 'Amélie', 'movie', ['Comedy'], ['Audrey Tautou']),
  title('daredevil', 'Daredevil', 'show', ['Action'], ['Charlie Cox']),
];

test('a subsequence matches, word starts and runs score higher', () => {
  const m = fuzzyMatch('arvl', 'Arrival');
  assert.ok(m);
  assert.deepEqual(m.indices, [0, 1, 4, 6]);
  assert.ok(fuzzyMatch('arr', 'Arrival')!.score > fuzzyMatch('rvl', 'Arrival')!.score);
});

test('accents fold to their base letter', () => {
  assert.ok(fuzzyMatch('amelie', 'Amélie'));
});

test('one typo for words of four letters or more, two from seven', () => {
  assert.ok(fuzzyMatch('arrivsl', 'Arrival'));
  assert.ok(fuzzyMatch('darwdevol', 'Daredevil'));
  assert.equal(fuzzyMatch('xyz', 'Arrival'), null);
  assert.equal(fuzzyMatch('', 'Arrival'), null);
});

test('a typo matches a later word, at that word', () => {
  const m = fuzzyMatch('adans', 'Amy Adams');
  assert.ok(m);
  assert.equal(m.indices[0], 4);
});

test('search finds titles, people and genres, and suggests the best four', () => {
  const s = createSearch(CATALOGUE, ['Action', 'Comedy', 'Drama', 'Sci-Fi']);
  const r = s.search('a');
  assert.ok(r.titles.length >= 2);
  assert.ok(r.people.some((h) => h.label === 'Amy Adams'));
  assert.ok(r.suggestions.length <= 4);
  assert.equal(s.search('charlie').people[0].titles![0].id, 'daredevil');
  assert.deepEqual(s.titlesForPerson('Amy Adams').map((t) => t.id), ['arrival']);
  assert.deepEqual(s.titlesInGenre('Comedy').map((t) => t.id), ['amelie']);
  assert.equal(s.search('drama').genres[0].label, 'Drama');
});
