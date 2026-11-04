// F40 (Rebecca, 2026-11-02): Movies and TV Shows' gallery cards. She picked the poster card with all its details (22:03,
// Figma option A), which the launcher shows; the landscape card (before) and the compact poster stay in the dev bar's
// Cards menu (?cards=) and compare.html, for comparison. Not a Launcher setting.

// F46 (Rebecca, 2026-11-04 09:57): the C card, posters only with the service pill on them, eight across, on every page with
// title cards. F40's poster card and the landscape card stay in the menu for comparison.
export type CardStyle = 'mini' | 'landscape' | 'poster' | 'poster-compact';

export const CARD_STYLES: [CardStyle, string][] = [
  ['mini', 'Cards: posters with the service pill (F46, chosen)'],
  ['poster', 'Cards: poster with details (F40)'],
  ['landscape', 'Cards: landscape (before F40)'],
  ['poster-compact', 'Cards: poster, name and service only'],
];

export const isCardStyle = (s: string | null): s is CardStyle => CARD_STYLES.some(([id]) => id === s);

let style: CardStyle = 'mini';
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
