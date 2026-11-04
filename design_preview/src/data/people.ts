import { TITLES } from './catalog';
import type { Title } from '../types';

/** What a person did on a title: the department ("Acting", "Directing") and the character or the job. */
export interface Role {
  department: string;
  what: string;
}

/**
 * What TMDB's /person/{id} says about someone (the launcher's TmdbParse.credits): their biography, birth date and
 * place, and the job they are known for. Written for the mock catalogue's people, in our own words; anyone else
 * shows only their name and titles, as a person with no TMDB biography does.
 */
interface Facts {
  department: string;
  born?: string;
  birthplace?: string;
  bio: string;
  /** Jobs the catalogue's crew lines don't carry (the launcher gets them from combined_credits). */
  more?: Record<string, Role[]>;
}

const FACTS: Record<string, Facts> = {
  'Denis Villeneuve': {
    department: 'Directing',
    born: '3 October 1967',
    birthplace: 'Bécancour, Quebec, Canada',
    bio:
      'A Canadian film director and writer. He made his name in Quebec with Incendies, which was nominated for an Academy Award, then moved to larger English-language films: Prisoners, Sicario and Arrival. ' +
      'He went on to direct Blade Runner 2049 and both parts of Dune, which he also co-wrote.\n\n' +
      'He is known for slow, patient pacing, huge quiet landscapes and long working partnerships with the same cinematographers and composers.',
    more: { dune: [{ department: 'Writing', what: 'Screenplay' }] },
  },
  'Christopher Nolan': {
    department: 'Directing',
    born: '30 July 1970',
    birthplace: 'London, England',
    bio:
      'A British and American film director, writer and producer. His films often play with time and memory: Memento, Inception, Interstellar and Tenet. ' +
      'He also made The Dark Knight trilogy and Oppenheimer, which won him the Academy Award for Best Director. He prefers shooting on film and building effects for real.',
  },
  'Timothée Chalamet': {
    department: 'Acting',
    born: '27 December 1995',
    birthplace: 'New York City, New York, USA',
    bio:
      'An American and French actor, born and raised in New York City. He first drew wide attention as Elio in Call Me by Your Name, which earned him an Academy Award nomination, ' +
      'and has since played Paul Atreides in both Dune films, Laurie in Little Women and the young Willy Wonka.',
  },
  'Ana de Armas': {
    department: 'Acting',
    born: '30 April 1988',
    birthplace: 'Havana, Cuba',
    bio: 'A Cuban and Spanish actress. She started in Spanish television, then moved to Hollywood with Knives Out, Blade Runner 2049 and No Time to Die, and played Marilyn Monroe in Blonde.',
  },
  'Willem Dafoe': {
    department: 'Acting',
    born: '22 July 1955',
    birthplace: 'Appleton, Wisconsin, USA',
    bio: 'An American actor with more than a hundred films, from Platoon and The Last Temptation of Christ to Spider-Man, The Florida Project and Poor Things. He was a founding member of the New York theatre company The Wooster Group.',
  },
  'Amy Adams': {
    department: 'Acting',
    born: '20 August 1974',
    birthplace: 'Vicenza, Veneto, Italy',
    bio: 'An American actress, nominated for six Academy Awards. She is known for Enchanted, Doubt, The Fighter, American Hustle and Arrival.',
  },
};

/** A catalogue credit's department: cast is Acting; crew by its job. */
function departmentOf(job: string): string {
  if (/director/i.test(job)) return 'Directing';
  if (/writer|screenplay|story/i.test(job)) return 'Writing';
  if (/creator/i.test(job)) return 'Creating';
  if (/producer/i.test(job)) return 'Production';
  if (/composer|music/i.test(job)) return 'Sound';
  return 'Crew';
}

/** The person's titles and what they did on each, from the catalogue's cast and crew. */
function credits(name: string): { titles: Title[]; roles: Map<string, Role[]> } {
  const titles: Title[] = [];
  const roles = new Map<string, Role[]>();
  for (const t of TITLES) {
    const r: Role[] = [];
    for (const c of t.cast) if (c.name === name) r.push({ department: 'Acting', what: c.role });
    for (const c of t.crew) if (c.name === name) r.push({ department: departmentOf(c.role), what: c.role });
    if (!r.length) continue;
    r.push(...(FACTS[name]?.more?.[t.id] ?? []));
    titles.push(t);
    roles.set(t.id, r);
  }
  return { titles, roles };
}

export interface PersonPageData {
  name: string;
  department: string;
  born: string;
  birthplace: string;
  bio: string;
  titles: Title[];
  roles: Map<string, Role[]>;
}

/** Everything the person page shows; the department is the facts', else the one most of their titles have. */
export function personData(name: string): PersonPageData {
  const f = FACTS[name];
  const { titles, roles } = credits(name);
  let department = f?.department ?? '';
  if (!department) {
    const n = new Map<string, number>();
    for (const r of roles.values()) for (const d of new Set(r.map((x) => x.department))) n.set(d, (n.get(d) ?? 0) + 1);
    department = [...n].sort((a, b) => b[1] - a[1])[0]?.[0] ?? '';
  }
  return { name, department, born: f?.born ?? '', birthplace: f?.birthplace ?? '', bio: f?.bio ?? '', titles, roles };
}

/**
 * The dev bar's "a long career" sample: the mock catalogue has at most three titles per person, so this lends a
 * made-up person 24 of its titles (with made-up parts) to show how the grid scrolls. Not a real filmography.
 */
export function samplePersonData(): PersonPageData {
  const titles = TITLES.slice(0, 24);
  const roles = new Map<string, Role[]>();
  titles.forEach((t, i) =>
    roles.set(t.id, i % 6 === 5 ? [{ department: 'Directing', what: 'Director' }] : [{ department: 'Acting', what: t.cast[1]?.role ?? 'Self' }]),
  );
  return {
    name: 'Sample Person',
    department: 'Acting',
    born: '1 January 1980',
    birthplace: 'Springfield, USA',
    bio:
      'A made-up person for the prototype, to show a long career: the mock catalogue has at most three titles for anyone real. ' +
      'Their titles are lent from the catalogue, with made-up parts, and every sixth one is one they directed, so the job chips show.\n\n' +
      'On the TV, this text is the biography TMDB has for the person, in the TV’s language when TMDB has one, else in English. ' +
      'The first six lines show here; Read more opens all of it.',
    titles,
    roles,
  };
}
