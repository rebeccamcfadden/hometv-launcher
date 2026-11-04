// API keys (F16): the TMDB, Watchmode and Movie of the Night keys a household makes for itself and enters from a
// phone or a laptop. What each key is for, the words the TV and the phone page show for its state, and the pairing
// code's letters. Shared by design_preview and HomeTV for Samsung; how keys are stored and checked stays with each.

export type KeyId = 'tmdb' | 'watchmode' | 'motn';
/**
 * unset: no key; checking: the TV is asking the service; ok: it answered; bad: it refused the key; unreachable: the TV
 * couldn't reach it, so the key has never been checked (kept and tried again later, but not counted as working until
 * a check passes); built: the build's own key (tv.properties), which a key entered here replaces, and which Remove
 * takes away too. A key that once worked and then can't be reached stays ok.
 */
export type KeyState = 'unset' | 'checking' | 'ok' | 'bad' | 'unreachable' | 'built';

export interface KeyInfo {
  id: KeyId;
  name: string;
  /** What it is for, in a few words. */
  use: string;
  /** Whether the TV needs it: TMDB for titles; Watchmode for prices; Movie of the Night only backs Watchmode up. */
  need: 'titles' | 'prices' | 'optional';
  /** The free plan, as the key page says it. */
  plan: string;
}

export const KEY_INFO: KeyInfo[] = [
  { id: 'tmdb', name: 'TMDB', use: 'Movies, TV Shows and Search', need: 'titles', plan: 'Free for personal use' },
  { id: 'watchmode', name: 'Watchmode', use: 'Rent and buy prices', need: 'prices', plan: 'Free: 2,500 credits a month' },
  {
    id: 'motn',
    name: 'Movie of the Night',
    use: 'More prices, when Watchmode has none',
    need: 'optional',
    plan: 'Free: 1,000 requests a month',
  },
];

/** A key as the TV keeps it: its state and last four characters, the only part ever shown again. */
export interface KeyEntry {
  state: KeyState;
  last4: string;
}

/** The pairing code's letters: no 0/O, 1/I/L, so it can be read off a TV and typed. */
export const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

/** A new six-letter pairing code. */
export const newCode = (random: () => number = Math.random) =>
  Array.from({ length: 6 }, () => CODE_ALPHABET[Math.floor(random() * CODE_ALPHABET.length)]).join('');

/** Line under a key's name on the TV and on the phone page. */
export function keyStatus(id: KeyId, e: KeyEntry): { text: string; tone: 'good' | 'warn' | 'bad' | 'dim' } {
  const info = KEY_INFO.find((k) => k.id === id)!;
  switch (e.state) {
    case 'ok':
      return { text: `Works · ends ${e.last4}`, tone: 'good' };
    case 'built':
      return { text: 'Works · from this TV’s build', tone: 'good' };
    case 'checking':
      return { text: `Checking with ${info.name}…`, tone: 'dim' };
    case 'unreachable':
      return { text: `Kept, not checked yet: the TV couldn’t reach ${info.name}. It tries again later, or OK tries now.`, tone: 'warn' };
    case 'bad':
      return {
        text:
          id === 'tmdb'
            ? 'TMDB refused it. Copy the API Read Access Token (the long one), all of it.'
            : `${info.name} refused it. Copy the key again, all of it.`,
        tone: 'bad',
      };
    default:
      return { text: info.need === 'optional' ? 'Not set · optional' : 'Not set', tone: 'dim' };
  }
}

/** The API keys section's value: "3 working", "Not set", "1 of 3 set", "1 not checked", "1 refused". */
export function keysValue(keys: Record<KeyId, { state: KeyState }>, only?: KeyId[]): { text: string; warn?: boolean } {
  const ids = only ?? KEY_INFO.map((k) => k.id);
  const states = ids.map((id) => keys[id].state);
  const refused = states.filter((s) => s === 'bad').length;
  if (refused) return { text: `${refused} refused`, warn: true };
  if (states.includes('checking')) return { text: 'Checking…' };
  const working = states.filter((s) => s === 'ok' || s === 'built').length;
  const unchecked = states.filter((s) => s === 'unreachable').length;
  const needed = ids.filter((id) => KEY_INFO.find((k) => k.id === id)!.need !== 'optional');
  const missing = needed.filter((id) => keys[id].state === 'unset');
  if (missing.length) {
    return { text: missing.length === needed.length ? 'Not set' : `${working + unchecked} of ${ids.length} set`, warn: false };
  }
  if (unchecked) return { text: `${unchecked} not checked`, warn: true };
  return { text: `${working} working` };
}
