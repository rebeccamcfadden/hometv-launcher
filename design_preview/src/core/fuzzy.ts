// Search over the prototype's mock catalogue. The matching lives in packages/core (shared with HomeTV for Samsung).
import { createSearch } from '@hometv/core/fuzzy';
import { GENRES, TITLES } from '../data/catalog';

export { fuzzyMatch, type Hit, type HitKind, type Match } from '@hometv/core/fuzzy';

export const { search, titlesForPerson, titlesInGenre } = createSearch(TITLES, GENRES);