// A QR code's look for the sign-in pane: finder, timing and alignment patterns of a version-14 code (73 modules)
// around seeded noise. It does not scan; the TV draws a real one from TMDB's approval link.

const SIZE = 73;
const ALIGN = [6, 26, 46, 66];

let cached: string | null = null;

function modules(): boolean[][] {
  const m: (boolean | null)[][] = Array.from({ length: SIZE }, () => Array<boolean | null>(SIZE).fill(null));
  const finder = (r0: number, c0: number) => {
    for (let r = -1; r <= 7; r++)
      for (let c = -1; c <= 7; c++) {
        const rr = r0 + r;
        const cc = c0 + c;
        if (rr < 0 || cc < 0 || rr >= SIZE || cc >= SIZE) continue;
        const ring = Math.max(Math.abs(r - 3), Math.abs(c - 3));
        m[rr][cc] = r >= 0 && r <= 6 && c >= 0 && c <= 6 && ring !== 2;
      }
  };
  finder(0, 0);
  finder(0, SIZE - 7);
  finder(SIZE - 7, 0);
  for (let i = 8; i < SIZE - 8; i++) {
    m[6][i] = i % 2 === 0;
    m[i][6] = i % 2 === 0;
  }
  for (const r of ALIGN)
    for (const c of ALIGN) {
      if (m[r][c] !== null) continue;
      for (let dr = -2; dr <= 2; dr++)
        for (let dc = -2; dc <= 2; dc++) m[r + dr][c + dc] = Math.max(Math.abs(dr), Math.abs(dc)) !== 1;
    }
  let seed = 20261030;
  const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  return m.map((row) => row.map((v) => (v === null ? rnd() < 0.5 : v)));
}

/** An SVG of the code, `px` wide, black on white with a two-module quiet zone. */
export function qrSvg(px: number): string {
  if (!cached) {
    let d = '';
    modules().forEach((row, r) => row.forEach((on, c) => on && (d += `M${c + 2} ${r + 2}h1v1h-1z`)));
    cached = d;
  }
  const n = SIZE + 4;
  return `<svg width="${px}" height="${px}" viewBox="0 0 ${n} ${n}" shape-rendering="crispEdges"><rect width="${n}" height="${n}" fill="#f4f5f7"/><path d="${cached}" fill="#090e15"/></svg>`;
}
