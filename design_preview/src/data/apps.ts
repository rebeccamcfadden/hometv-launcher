import { HOME_APPS, OTHER_APPS, type HomeApp } from './services';

/** An installed app on the (mock) TV, for All Apps. `system`: it came with the TV. */
export interface TvApp extends HomeApp {
  pkg: string;
  system: boolean;
}

/** Package and origin of the Home row's apps and the other streaming apps (services.ts), by id. */
const KNOWN: Record<string, [pkg: string, system: boolean]> = {
  netflix: ['com.netflix.ninja', true],
  youtube: ['com.google.android.youtube.tv', true],
  prime: ['com.amazon.amazonvideo.livingroom', true],
  disney: ['com.disney.disneyplus', false],
  max: ['com.wbd.stream', false],
  appletvplus: ['com.apple.atve.androidtv.appletv', false],
  spotify: ['com.spotify.tv.android', false],
  svtplay: ['se.svt.android.svtplay', false],
  tv4play: ['se.tv4.tv4playtab', false],
  viaplay: ['com.viaplay.android', false],
};

const app = (id: string, name: string, pkg: string, system: boolean): TvApp => ({ id, name, mark: name, weight: 500, pkg, system });

/**
 * Every app the mock TV shows in All Apps: the streaming apps, a few the household installed, and what a TCL TV comes
 * with. Eros Now came with it too and isn't one the launcher knows, so it starts in TV Utilities: hold OK moves it out.
 */
export const TV_APPS: TvApp[] = [
  ...[...HOME_APPS, ...OTHER_APPS].map((a) => ({ ...a, pkg: KNOWN[a.id][0], system: KNOWN[a.id][1] })),
  app('plex', 'Plex', 'com.plexapp.android', false),
  app('kodi', 'Kodi', 'org.xbmc.kodi', false),
  app('steamlink', 'Steam Link', 'com.valvesoftware.steamlink', false),
  app('twitch', 'Twitch', 'tv.twitch.android.app', false),
  app('playstore', 'Google Play Store', 'com.android.vending', true),
  app('erosnow', 'Eros Now', 'com.eros.now', true),
  app('settings', 'Settings', 'com.android.tv.settings', true),
  app('livechannels', 'Live Channels', 'com.google.android.tv', true),
  app('tcl-tv', 'Live TV', 'com.tcl.tv', true),
  app('tcl-source', 'Source', 'com.tcl.sourcemananger', true),
  app('tcl-settings', 'TV Settings', 'com.tcl.settings', true),
  app('tcl-media', 'Media Center', 'com.tcl.ui_mediaCenter', true),
  app('tcl-video', 'Video Player', 'com.tcl.videoplayer', true),
  app('tcl-music', 'Music Player', 'com.tcl.audioplayer', true),
  app('tcl-photo', 'Photo Player', 'com.tcl.imageplayer', true),
  app('tcl-pvr', 'PVR Player', 'com.tcl.pvr.pvrplayer', true),
  app('tcl-update', 'System Update', 'com.tcl.versionUpdateApp', true),
  app('tcl-remote', 'Remote Update', 'com.tcl.UpdatePeripheral', true),
];
