// The part of the day a Seasonal theme's picture shows (F22), as the launcher's DayPhase: dawn from 40 minutes before
// sunrise to 50 after, sunset from 50 minutes before sunset to 40 after, day and night in between. Sunrise and sunset
// come from the sunrise equation at the mock's place (New York, the build's city), worked out offline. Both functions
// read one sorted timeline of edges across neighbouring dates, so a window that crosses midnight (in the browser's
// time zone) is still read right and the next edge is always in the future.

export type Phase = 'dawn' | 'day' | 'sunset' | 'night';

export const PHASES: Phase[] = ['dawn', 'day', 'sunset', 'night'];
export const PHASE_LABELS: Record<Phase, string> = { dawn: 'Dawn', day: 'Day', sunset: 'Sunset', night: 'Night' };

const LAT = 40.71;
const LON = -74.01;
const MIN = 60_000;
const DAY_MS = 86_400_000;
const rad = (d: number) => (d * Math.PI) / 180;
const norm = (d: number) => ((d % 360) + 360) % 360;

interface Edge {
  at: number;
  starts: Phase;
}

/** Sunrise and sunset (ms) for the solar day around a moment; 'up' or 'down' when the sun doesn't cross the horizon. */
function sun(noonMs: number): [number, number] | 'up' | 'down' {
  const julian = noonMs / DAY_MS + 2440587.5;
  const n = Math.ceil(julian - 2451545.0 + 0.0008 - 0.5);
  const jStar = n - LON / 360;
  const m = norm(357.5291 + 0.98560028 * jStar);
  const mr = rad(m);
  const c = 1.9148 * Math.sin(mr) + 0.02 * Math.sin(2 * mr) + 0.0003 * Math.sin(3 * mr);
  const lambda = rad(norm(m + c + 180 + 102.9372));
  const transit = 2451545.0 + jStar + 0.0053 * Math.sin(mr) - 0.0069 * Math.sin(2 * lambda);
  const sinDec = Math.sin(lambda) * Math.sin(rad(23.4397));
  const cosDec = Math.cos(Math.asin(sinDec));
  const cosW = (Math.sin(rad(-0.833)) - Math.sin(rad(LAT)) * sinDec) / (Math.cos(rad(LAT)) * cosDec);
  if (cosW < -1) return 'up';
  if (cosW > 1) return 'down';
  const w = Math.acos(cosW) / (2 * Math.PI);
  const ms = (j: number) => (j - 2440587.5) * DAY_MS;
  return [ms(transit - w), ms(transit + w)];
}

/**
 * The edges of the dates from the day before `d` to two days after, in time order. A polar date is one edge at its
 * midnight, and no other date's edge falls inside it; a sunset that runs into the next dawn hands straight over to it
 * halfway between the two (as DayPhase.handOver).
 */
function timeline(d: Date): Edge[] {
  const out: Edge[] = [];
  const polar: [number, number][] = [];
  for (let i = -1; i <= 2; i++) {
    const start = new Date(d.getFullYear(), d.getMonth(), d.getDate() + i).getTime();
    const end = new Date(d.getFullYear(), d.getMonth(), d.getDate() + i + 1).getTime();
    const s = sun(new Date(d.getFullYear(), d.getMonth(), d.getDate() + i, 12).getTime());
    if (s === 'up' || s === 'down') {
      polar.push([start, end]);
      out.push({ at: start, starts: s === 'up' ? 'day' : 'night' });
      continue;
    }
    const [rise, set] = s;
    const e = [rise - 40 * MIN, rise + 50 * MIN, set - 50 * MIN, set + 40 * MIN];
    if (e[1] > e[2]) e[1] = e[2] = (rise + set) / 2;
    PHASES.forEach((p, k) => out.push({ at: e[k], starts: p }));
  }
  const sorted = out.filter((e) => !polar.some(([a, b]) => e.at > a && e.at < b)).sort((a, b) => a.at - b.at);
  const kept: Edge[] = [];
  for (const e of sorted) {
    const last = kept[kept.length - 1];
    if (last && last.starts === 'dawn' && e.starts === 'night') {
      kept[kept.length - 1] = { at: (last.at + e.at) / 2, starts: 'dawn' };
      continue;
    }
    if (last && last.starts === e.starts) continue;
    kept.push(e);
  }
  return kept;
}

/** The phase started by the last edge at or before the moment. */
export function phaseAt(d: Date): Phase {
  const t = d.getTime();
  let phase: Phase = 'night';
  for (const e of timeline(d)) {
    if (e.at > t) break;
    phase = e.starts;
  }
  return phase;
}

/** Milliseconds until the part of the day next changes: the first edge strictly after the moment. */
export function msToNextPhase(d: Date): number {
  const t = d.getTime();
  for (const e of timeline(d)) if (e.at > t) return e.at - t;
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime() - t;
}
