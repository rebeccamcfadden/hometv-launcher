import { h } from '../core/dom';
import { REGION_LABELS, type Region } from '../core/settings';

// Two regions (Rebecca, 2026-11-04): a small flag wherever an offer's country shows. 27 × 18 (the 18 px rule), drawn
// here rather than as emoji, since a TV's emoji font varies; a 1 px light line keeps a flag's edge on a dark pill. The
// country's name is the flag's label, so it is never the only cue for a screen reader. Figma: page "Two regions".

const SVG: Record<Region, string> = {
  se:
    '<rect width="27" height="18" fill="#006AA7"/><rect x="8.4" width="3.4" height="18" fill="#FECC02"/>' +
    '<rect y="7.2" width="27" height="3.6" fill="#FECC02"/>',
  us:
    '<rect width="27" height="18" fill="#fff"/>' +
    [0, 2, 4, 6, 8, 10, 12].map((i) => `<rect y="${((i * 18) / 13).toFixed(2)}" width="27" height="1.385" fill="#B22234"/>`).join('') +
    '<rect width="10.8" height="9.69" fill="#3C3B6E"/>' +
    [0, 1, 2, 3]
      .flatMap((row) => [0, 1, 2, 3].map((col) => `<circle cx="${1.8 + col * 2.6 + (row % 2) * 1.3}" cy="${1.8 + row * 2.2}" r="0.6" fill="#fff"/>`))
      .join(''),
};

/** One country's flag: "Sweden" to a screen reader. */
export const flag = (r: Region) =>
  h('span.flag', {
    role: 'img',
    'aria-label': REGION_LABELS[r],
    html: `<svg viewBox="0 0 27 18" width="27" height="18" aria-hidden="true">${SVG[r]}<rect x=".5" y=".5" width="26" height="17" rx="2.5" fill="none" stroke="rgba(255,255,255,.2)"/></svg>`,
  });

/** Several flags side by side, 4 apart: "Sweden and United States". */
export const flags = (regions: Region[], cls = '') =>
  h(`span.flags${cls}`, { 'aria-label': regions.map((r) => REGION_LABELS[r]).join(' and ') }, ...regions.map(flag));
