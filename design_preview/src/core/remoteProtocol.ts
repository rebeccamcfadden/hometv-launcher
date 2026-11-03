// Phone remote (F27): the messages between the phone page (remote.html) and the TV, and what both sides share. No
// side effects, so the phone page doesn't load the TV prototype. On the TV these are JSON over HTTPS
// (docs/phone-remote.md); in the prototype they go over a BroadcastChannel between two tabs.

export type RemoteKey = 'up' | 'down' | 'left' | 'right' | 'ok' | 'back' | 'home';

/** What a phone sends. Every message but hello and pair carries the token the TV gave it when it paired. */
export type PhoneMsg =
  | { type: 'hello'; token?: string }
  | { type: 'pair'; code: string; name: string }
  | { type: 'key'; token: string; key: RemoteKey }
  | { type: 'text'; token: string; value: string }
  | { type: 'enter'; token: string }
  | { type: 'mylist'; token: string; id: string; mine: boolean; seq: number }
  | { type: 'search'; token: string; q: string }
  | { type: 'forget'; token: string }
  | { type: 'bye'; token: string };

/** A title as the phone shows it. */
export interface PhoneTitle {
  id: string;
  name: string;
  year: number;
  kind: 'movie' | 'show';
  art: string;
  mine: boolean;
}

/** What the TV sends a phone. */
export type TvMsg =
  | { type: 'off' }
  | { type: 'unpaired'; locked: number }
  | { type: 'badcode'; left: number }
  | { type: 'locked'; until: number }
  | { type: 'paired'; token: string; name: string }
  | { type: 'state'; tv: string; name: string; screen: string; typing: boolean; text: string; title: PhoneTitle | null; stale?: boolean }
  | { type: 'results'; q: string; items: PhoneTitle[] }
  /** The answer to a phone's mylist (seq is the phone's): on the TV, POST /api/mylist's {mine} or its error. */
  | { type: 'mylisted'; id: string; seq: number; mine?: boolean; failed?: boolean };

export const CHANNEL = 'cleanyourtv-remote';
/** The address the QR opens; the code follows as its #fragment, which a browser never sends. */
export const REMOTE_URL = 'https://192.168.1.50:8767/remote';
/** The TV's name in the page's header (on the TV: the device name). */
export const TV_NAME = 'Living room TV';

/** The code as typed: case, spaces and dashes don't count. */
export const normalizeCode = (c: string) => c.replace(/[\s-]/g, '').toUpperCase();
