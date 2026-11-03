import { app } from './app';
import { search } from './fuzzy';
import { featureOn, setFeature } from './settings';
import { store } from './store';
import { getTitle } from '../data/catalog';
import type { Title } from '../types';
import { CHANNEL, normalizeCode, REMOTE_URL, TV_NAME, type PhoneMsg, type PhoneTitle, type TvMsg } from './remoteProtocol';

export { REMOTE_URL };

// Phone remote (F27, with F39's Add to My list), the TV's side. On the TV the launcher serves remote.html over HTTPS on
// the home network (docs/phone-remote.md); here the phone page (remote.html) is another tab of the prototype and the
// two talk over a BroadcastChannel, which stands in for the TV's port. Everything the TV does is here: the pairing
// code and its lock, each phone's token, what a phone may do, and what the TV tells the phones it shows.

export interface PairedPhone {
  id: string;
  name: string;
  /** The TV keeps a hash of the token on the TV; the prototype keeps the token. */
  token: string;
  lastUsed: number;
}

interface Saved {
  code: string;
  phones: PairedPhone[];
  wrong: number;
  lockedUntil: number;
}

const KEY = 'cleanyourtv-preview-remote-v1';
/** As Phone notes: five wrong codes lock pairing for five minutes, for every phone. */
const MAX_WRONG = 5;
const LOCK_MS = 5 * 60 * 1000;
/** A phone that sent nothing for this long is no longer "connected" (its page sends a hello every 10 s). */
const CONNECTED_MS = 25 * 1000;
/** Letters and digits that can't be mistaken for each other (NoteInbox.ALPHABET): no 0/O, 1/I/L. */
const ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

const newCode = () => {
  let c = '';
  for (let i = 0; i < 8; i++) c += (i === 4 ? '-' : '') + ALPHABET[Math.floor(Math.random() * ALPHABET.length)];
  return c;
};
const randomToken = () => Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, '0')).join('');

function load(): Saved {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const s = JSON.parse(raw) as Partial<Saved>;
      if (typeof s.code === 'string' && Array.isArray(s.phones)) return { wrong: 0, lockedUntil: 0, ...s } as Saved;
    }
  } catch {
    // Corrupt or blocked storage: start fresh.
  }
  return { code: newCode(), phones: [], wrong: 0, lockedUntil: 0 };
}

const data = load();
const seen = new Map<string, number>();
const listeners = new Set<() => void>();
let channel: BroadcastChannel | null = null;

function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(data));
  } catch {
    // Blocked storage: the pairing lasts for this visit.
  }
  changed();
}

function changed() {
  listeners.forEach((fn) => fn());
  paintNav();
}

/** The nav's phone icon and its dot read this (base.css, .nav-remote): off, on, or a phone connected. */
function paintNav() {
  document.documentElement.dataset.remote = !featureOn('remote') ? 'off' : connectedPhone() ? 'connected' : 'on';
}

function connectedPhone(): PairedPhone | null {
  const now = Date.now();
  return data.phones.find((p) => now - (seen.get(p.id) ?? 0) < CONNECTED_MS) ?? null;
}

const phoneTitle = (t: Title): PhoneTitle => ({ id: t.id, name: t.name, year: t.year, kind: t.kind, art: t.art, mine: store.inMyList(t.id) });

/** The title the TV is showing: an open preview's, else the focused card's. */
function currentTitle(): Title | null {
  const top = app.top();
  if (top?.title) return top.title;
  const id = app.focused?.closest<HTMLElement>('[data-title]')?.dataset.title;
  if (!id) return null;
  try {
    return getTitle(id);
  } catch {
    return null;
  }
}

function field() {
  const f = app.top()?.textField;
  return f && f.ready?.() !== false ? f : null;
}

function screenName(): string {
  const top = app.top();
  if (!top) return 'Home';
  if (top.title) return top.title.name;
  return top.tab ? { home: 'Home', movies: 'Movies', tv: 'TV Shows', search: 'Search' }[top.tab] : top.label;
}

function stateFor(p: PairedPhone): TvMsg {
  const f = field();
  const t = currentTitle();
  return { type: 'state', tv: TV_NAME, name: p.name, screen: screenName(), typing: !!f, text: f?.value() ?? '', title: t ? phoneTitle(t) : null };
}

function send(to: string, msg: TvMsg) {
  channel?.postMessage({ from: 'tv', to, msg });
}

/** Tells every phone that is connected what the TV shows now; called as focus, pages and lists change. */
let pending = 0;
function broadcastState() {
  if (!channel || pending) return;
  pending = window.setTimeout(() => {
    pending = 0;
    const now = Date.now();
    for (const p of data.phones) if (now - (seen.get(p.id) ?? 0) < CONNECTED_MS) send(p.id, stateFor(p));
  }, 30);
}

function byToken(token: string | undefined): PairedPhone | null {
  return (token && data.phones.find((p) => p.token === token)) || null;
}

/** A wrong code: five lock pairing for five minutes (as Phone notes; the TV counts them per address). */
function wrongTry(now: number): TvMsg {
  data.wrong++;
  if (data.wrong >= MAX_WRONG) {
    data.wrong = 0;
    data.lockedUntil = now + LOCK_MS;
  }
  save();
  return data.lockedUntil > now ? { type: 'locked', until: data.lockedUntil } : { type: 'badcode', left: MAX_WRONG - data.wrong };
}

/** The ids of the phones connected now, to tell when the pane's rows change. */
const connectedIds = () => data.phones.filter((p) => remote.isConnected(p)).map((p) => p.id).join(',');

function onMessage(sender: string, msg: PhoneMsg) {
  if (!featureOn('remote')) return send(sender, { type: 'off' });
  const now = Date.now();
  if (msg.type === 'pair') {
    if (data.lockedUntil > now) return send(sender, { type: 'locked', until: data.lockedUntil });
    if (normalizeCode(msg.code) !== normalizeCode(data.code)) return send(sender, wrongTry(now));
    data.wrong = 0;
    const name = msg.name.trim().slice(0, 40) || 'A phone';
    const phone: PairedPhone = { id: sender, name, token: randomToken(), lastUsed: now };
    data.phones = [...data.phones.filter((p) => p.id !== sender), phone];
    seen.set(sender, now);
    save();
    app.toast(`${name} is paired as a remote`);
    send(sender, { type: 'paired', token: phone.token, name });
    return send(sender, stateFor(phone));
  }
  const phone = byToken('token' in msg ? msg.token : undefined);
  if (!phone) {
    // A token the TV doesn't know is a forgotten phone, not a wrong code: counting it would let anything on the Wi-Fi
    // lock pairing (on the TV: 401, not counted; wrong codes are counted per address there).
    return send(sender, { type: 'unpaired', locked: data.lockedUntil > now ? data.lockedUntil : 0 });
  }
  const wasConnected = connectedIds();
  // Kept at most once a minute, so "Last used" survives a restart without a write per key.
  const saveUse = now - phone.lastUsed > 60_000;
  phone.lastUsed = now;
  if (msg.type === 'forget') {
    // The phone's own Unpair: as Forget on the TV.
    data.phones = data.phones.filter((p) => p.id !== phone.id);
    seen.delete(phone.id);
    save();
    return send(sender, { type: 'unpaired', locked: 0 });
  }
  if (msg.type === 'bye') seen.delete(phone.id);
  else seen.set(phone.id, now);
  if (saveUse) save();
  else if (connectedIds() !== wasConnected) changed();
  switch (msg.type) {
    case 'hello':
      return send(phone.id, stateFor(phone));
    case 'key':
      app.press(msg.key);
      return broadcastState();
    case 'text':
      field()?.set(msg.value.slice(0, 200));
      return broadcastState();
    case 'enter':
      field()?.enter?.();
      return broadcastState();
    case 'mylist': {
      let t: Title;
      try {
        t = getTitle(msg.id);
      } catch {
        return;
      }
      const mine = store.toggleMyList(t.id);
      app.toast(`${mine ? 'Added' : 'Removed'} ${t.name} ${mine ? 'to' : 'from'} My list · From ${phone.name}`);
      return broadcastState();
    }
    case 'search': {
      const q = msg.q.trim().slice(0, 60);
      const items = q ? search(q).titles.slice(0, 8).map((h) => phoneTitle(h.title!)) : [];
      return send(phone.id, { type: 'results', q: msg.q, items });
    }
  }
}

/** Starts listening (main.ts): the channel is open while Phone remote is on, as the TV's port is. */
function start() {
  if (channel || !featureOn('remote')) return;
  channel = new BroadcastChannel(CHANNEL);
  channel.onmessage = (e: MessageEvent) => {
    const d = e.data as { from?: string; phone?: string; msg?: PhoneMsg };
    if (d?.from === 'phone' && typeof d.phone === 'string' && d.msg) onMessage(d.phone, d.msg);
  };
}

function stop() {
  if (!channel) return;
  for (const p of data.phones) send(p.id, { type: 'off' });
  channel.close();
  channel = null;
  seen.clear();
}

export const remote = {
  init() {
    start();
    paintNav();
    app.onChange(broadcastState);
    app.onFocusChange(broadcastState);
    store.on(broadcastState);
    // A phone that went quiet stops counting as connected: the icon's dot and the pane catch up.
    let was = '';
    window.setInterval(() => {
      const now = connectedIds();
      if (now !== was) changed();
      was = now;
    }, 5000);
  },
  on: () => featureOn('remote'),
  setOn(on: boolean) {
    setFeature('remote', on);
    if (on) start();
    else stop();
    changed();
  },
  /** Called when the Features page switches it (settings change). */
  sync() {
    if (featureOn('remote')) start();
    else stop();
    paintNav();
  },
  code: () => data.code,
  phones: (): readonly PairedPhone[] => data.phones,
  isConnected: (p: PairedPhone) => Date.now() - (seen.get(p.id) ?? 0) < CONNECTED_MS,
  connected: () => connectedPhone(),
  /** New code: phones already paired keep working; the old code no longer pairs. */
  newCode() {
    data.code = newCode();
    data.wrong = 0;
    save();
  },
  forget(id: string) {
    data.phones = data.phones.filter((p) => p.id !== id);
    seen.delete(id);
    send(id, { type: 'unpaired', locked: 0 });
    save();
  },
  /** Forget all: every phone, and a new code (on the TV also a new key and certificate). */
  forgetAll() {
    for (const p of data.phones) send(p.id, { type: 'unpaired', locked: 0 });
    data.phones = [];
    seen.clear();
    data.code = newCode();
    data.wrong = 0;
    data.lockedUntil = 0;
    save();
  },
  /** The section's value: Off, On, or "2 phones". */
  value(): { text: string } {
    if (!featureOn('remote')) return { text: 'Off' };
    const n = data.phones.length;
    return { text: n ? `${n} phone${n === 1 ? '' : 's'}` : 'On' };
  },
  listen(fn: () => void): () => void {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  /** Prototype only (dev bar): the phone page for this TV, with its code, in a new tab. */
  openPhonePage() {
    window.open(`./remote.html#${normalizeCode(data.code)}`, '_blank');
  },
};
