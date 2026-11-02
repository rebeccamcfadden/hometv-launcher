// F35 (b): the TV Utilities folder in All Apps (Rebecca, 2026-11-02: on for every TV, no switch). The rule is the same
// on every brand, so no maker's app list is needed: an app goes in when it came with the TV and isn't a watch app,
// unless it is on Home's row; an app the household installed goes in only when a TV maker made it. Hold OK moves an app
// in or out by hand, and that choice wins. On the TV: TvUtilities (unit-tested).

/** Apps people watch or shop in, which stay in All Apps even when the TV came with them. */
export const WATCH_APPS = new Set([
  'com.netflix.ninja',
  'com.netflix.mediaclient',
  'com.google.android.youtube.tv',
  'com.google.android.youtube.tvkids',
  'com.google.android.youtube.tvmusic',
  'com.amazon.amazonvideo.livingroom',
  'com.disney.disneyplus',
  'com.wbd.stream',
  'com.hbo.hbonow',
  'com.apple.atve.androidtv.appletv',
  'com.spotify.tv.android',
  'com.plexapp.android',
  'se.svt.android.svtplay',
  'com.android.vending',
  'com.google.android.videos',
  'com.google.android.play.games',
]);

/** Package prefixes of TV makers and their chip vendors: their apps are TV utilities even when not on the system image. */
export const VENDOR_PREFIXES = [
  'com.tcl.',
  'com.sony.',
  'com.philips.',
  'org.droidtv.',
  'com.tpvision.',
  'com.hisense.',
  'com.jamdeo.',
  'com.vidaa.',
  'com.cvte.',
  'com.xiaomi.',
  'com.mitv.',
  'com.mediatek.',
  'com.mstar.',
  'com.realtek.',
  'com.rtk.',
  'com.amlogic.',
  'com.droidlogic.',
  'com.skyworth.',
  'com.coocaa.',
  'com.vestel.',
  'com.toshiba.',
  'com.sharp.',
  'jp.co.sharp.',
  'com.panasonic.',
  'com.haier.',
  'com.changhong.',
  'com.konka.',
];

export type Moved = 'in' | 'out';

export interface AppFacts {
  pkg: string;
  /** It came on the TV's system image (also when updated since). */
  system: boolean;
  onHome: boolean;
  /** Moved in or out by hand (hold OK). */
  moved?: Moved;
}

export function isTvUtility(a: AppFacts): boolean {
  if (a.moved) return a.moved === 'in';
  if (a.onHome || WATCH_APPS.has(a.pkg)) return false;
  return a.system || VENDOR_PREFIXES.some((p) => a.pkg.startsWith(p));
}
