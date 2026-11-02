// Mock weather places, as Open-Meteo's geocoding would return them, with the weather there now (in °C). Each has
// its English name and, where it differs, the local one, because the TV asks in both (V2 design r2-weather A).

export interface Place {
  id: string;
  name: string;
  /** The local name, when it isn't the English one ("Göteborg"). */
  local?: string;
  region: string;
  country: string;
  temp: number;
  desc: string;
}

export const PLACES: Place[] = [
  { id: 'new-york', name: 'New York', region: 'New York', country: 'United States', temp: 9, desc: 'Partly cloudy' },
  { id: 'gothenburg', name: 'Gothenburg', local: 'Göteborg', region: 'Västra Götaland', country: 'Sweden', temp: 11, desc: 'Cloudy' },
  { id: 'gothenburg-ne', name: 'Gothenburg', region: 'Nebraska', country: 'United States', temp: 4, desc: 'Clear' },
  { id: 'gothenburg-za', name: 'Gothenburg', region: 'Kwazulu-Natal', country: 'South Africa', temp: 22, desc: 'Sunny' },
  { id: 'gotene', name: 'Götene', region: 'Västra Götaland', country: 'Sweden', temp: 9, desc: 'Cloudy' },
  { id: 'malmo', name: 'Malmö', region: 'Skåne', country: 'Sweden', temp: 12, desc: 'Light rain' },
  { id: 'malmberget', name: 'Malmberget', region: 'Norrbotten', country: 'Sweden', temp: -2, desc: 'Snow' },
  { id: 'uppsala', name: 'Uppsala', region: 'Uppsala', country: 'Sweden', temp: 8, desc: 'Cloudy' },
  { id: 'umea', name: 'Umeå', region: 'Västerbotten', country: 'Sweden', temp: 3, desc: 'Clear' },
  { id: 'lund', name: 'Lund', region: 'Skåne', country: 'Sweden', temp: 12, desc: 'Light rain' },
  { id: 'linkoping', name: 'Linköping', region: 'Östergötland', country: 'Sweden', temp: 9, desc: 'Cloudy' },
  { id: 'lulea', name: 'Luleå', region: 'Norrbotten', country: 'Sweden', temp: 0, desc: 'Clear' },
  { id: 'kiruna', name: 'Kiruna', region: 'Norrbotten', country: 'Sweden', temp: -4, desc: 'Snow' },
  { id: 'visby', name: 'Visby', region: 'Gotland', country: 'Sweden', temp: 10, desc: 'Windy' },
  { id: 'oslo', name: 'Oslo', region: 'Oslo', country: 'Norway', temp: 7, desc: 'Cloudy' },
  { id: 'copenhagen', name: 'Copenhagen', local: 'København', region: 'Capital Region', country: 'Denmark', temp: 11, desc: 'Cloudy' },
  { id: 'helsinki', name: 'Helsinki', region: 'Uusimaa', country: 'Finland', temp: 6, desc: 'Clear' },
  { id: 'london', name: 'London', region: 'England', country: 'United Kingdom', temp: 13, desc: 'Light rain' },
];

export const placeById = (id: string) => PLACES.find((p) => p.id === id) ?? PLACES[0];

/** Lowercase, without accents: "Göteborg" → "goteborg". */
const fold = (s: string) =>
  s
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();

export interface PlaceMatch {
  place: Place;
  /** The name the query matched: the local one when only it did ("göteb" → Göteborg). */
  via: 'name' | 'local';
  /** How many leading letters matched, to highlight. */
  length: number;
}

/** Places whose English or local name starts with the query (accents ignored), Sweden first. */
export function findPlaces(query: string): PlaceMatch[] {
  const q = fold(query.trim());
  if (!q) return [];
  const out: PlaceMatch[] = [];
  for (const p of PLACES) {
    if (fold(p.name).startsWith(q)) out.push({ place: p, via: 'name', length: q.length });
    else if (p.local && fold(p.local).startsWith(q)) out.push({ place: p, via: 'local', length: q.length });
  }
  return out.sort((a, b) => Number(b.place.country === 'Sweden') - Number(a.place.country === 'Sweden')).slice(0, 5);
}

/** "Göteborg · Västra Götaland, Sweden". */
export const placeLine = (p: Place) => `${p.local ? `${p.local} · ` : ''}${p.region}, ${p.country}`;
