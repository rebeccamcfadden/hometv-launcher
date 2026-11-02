// F40 (Rebecca, 2026-11-02): Movies and TV Shows' gallery cards, as today (landscape art) or with the poster art, for
// her to pick. A dev-bar choice (Cards menu, ?cards=), not a Launcher setting: the launcher gets the one she picks.

export type CardStyle = 'landscape' | 'poster' | 'poster-compact';

export const CARD_STYLES: [CardStyle, string][] = [
  ['landscape', 'Cards: landscape (today)'],
  ['poster', 'Cards: poster'],
  ['poster-compact', 'Cards: poster, name and service only'],
];

export const isCardStyle = (s: string | null): s is CardStyle => CARD_STYLES.some(([id]) => id === s);

let style: CardStyle = 'landscape';
const listeners = new Set<() => void>();

export const cards = {
  get: () => style,
  set(s: CardStyle) {
    if (s === style) return;
    style = s;
    listeners.forEach((fn) => fn());
  },
  on(fn: () => void) {
    listeners.add(fn);
  },
};
