// F40 (Rebecca, 2026-11-02): Movies and TV Shows' gallery cards. She picked the poster card with all its details (22:03,
// Figma option A), which the launcher shows; the landscape card (before) and the compact poster stay in the dev bar's
// Cards menu (?cards=) and compare.html, for comparison. Not a Launcher setting.

export type CardStyle = 'landscape' | 'poster' | 'poster-compact';

export const CARD_STYLES: [CardStyle, string][] = [
  ['poster', 'Cards: poster (chosen)'],
  ['landscape', 'Cards: landscape (before F40)'],
  ['poster-compact', 'Cards: poster, name and service only'],
];

export const isCardStyle = (s: string | null): s is CardStyle => CARD_STYLES.some(([id]) => id === s);

let style: CardStyle = 'poster';
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
