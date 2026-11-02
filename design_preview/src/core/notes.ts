import { net } from './net';
import { featureOn, settings } from './settings';

// Phone notes (BACKLOG.md, Phone notes): each phone shares a note to a "Send to TV" Shortcut, which posts its text to
// the TV over the home network with a pairing code. The TV looks each new line up on TMDB and adds it to that
// person's TMDB list. Mocked here: the dev bar's Jump menu "sends" a note, and the import runs on a timer.

/** One of TMDB's answers for a line it didn't match exactly. */
export interface NoteCandidate {
  title: string;
  year: number;
  kind: 'Movie' | 'TV show';
}

/** A line not matched exactly, as written, with TMDB's best three answers (none when it had none). */
export interface NoteAsk {
  line: string;
  ticked?: boolean;
  top: NoteCandidate[];
}

export interface NoteSend {
  list: string;
  /** "Today 19:40". */
  at: string;
  lines: number;
  added: number;
  /** Lines not matched exactly: asked about in the pane (the picker). */
  unmatched: NoteAsk[];
}

export interface Importing {
  list: string;
  done: number;
  total: number;
}

/** The end of an import, shown for a few seconds in the pill. */
export interface Finished {
  list: string;
  added: number;
  unmatched: number;
  offline: boolean;
}

/** The TV's address as a phone reaches it; the docs use 192.168.1.50 for every example. */
export const INBOX_URL = 'http://192.168.1.50:8765/notes';

const CODE_CHARS = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
const newCode = () =>
  Array.from({ length: 8 }, (_, i) => (i === 4 ? '-' : '') + CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');

let on = false;
let code = 'K7QM-4PXT';
const sends: NoteSend[] = [
  { list: 'Partner', at: 'Yesterday 21:05', lines: 14, added: 14, unmatched: [] },
];
let importing: Importing | null = null;
let finished: Finished | null = null;
let timer = 0;
let finishTimer = 0;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((fn) => fn());

/** How long the end message stays, in ms. */
export const FINISHED_MS = 4000;

export const notes = {
  on: () => on,
  setOn(v: boolean) {
    on = v;
    // Off: the TV stops listening, and a note being imported stops with it.
    if (!v) stop();
    emit();
  },
  code: () => code,
  newCode() {
    code = newCode();
    emit();
  },
  sends: () => sends,
  importing: () => importing,
  finished: () => finished,
  listen(fn: () => void): () => void {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  /** Section value in Launcher settings. */
  value(): { text: string; warn?: boolean } {
    if (!on) return { text: 'Off' };
    const unmatched = sends.reduce((n, s) => n + s.unmatched.length, 0);
    return unmatched ? { text: `${unmatched} not matched`, warn: true } : { text: 'On' };
  },
  /** Search again found these answers (perhaps none) for a line: they are kept with it, as on the TV. */
  offer(list: string, line: string, top: NoteCandidate[]) {
    const ask = sends.find((x) => x.list === list)?.unmatched.find((a) => a.line === line);
    if (ask) ask.top = top;
    emit();
  },
  /**
   * The picker answered a line (the project lead, 2026-11-01): `pick` goes onto the list (Seen, for a ticked line),
   * or null is "None of these", remembered so the line isn't asked again. Either way it leaves Last sends.
   */
  answer(list: string, line: string, pick: NoteCandidate | null) {
    const s = sends.find((x) => x.list === list);
    if (!s) return;
    const ask = s.unmatched.find((a) => a.line === line);
    s.unmatched = s.unmatched.filter((a) => a.line !== line);
    if (pick && ask && !ask.ticked) {
      s.added += 1;
      clearTimeout(finishTimer);
      finish({ list, added: 1, unmatched: 0, offline: false });
      return;
    }
    emit();
  },
  /**
   * A phone sends a note (dev bar): `lines` lines, of which `misses` weren't matched exactly. The pill counts every
   * line from the start ("0 of 12"). Offline, the lines wait and
   * the pill says so. One import at a time: in this mock, a send during an import is dropped (the TV queues it).
   */
  simulate(list: string, lines: number, misses: NoteAsk[]) {
    // F14: with Phone notes off in Features the TV isn't listening at all.
    if (!on || importing || !featureOn('notes')) return;
    clearTimeout(finishTimer);
    finished = null;
    importing = { list, done: 0, total: lines };
    emit();
    timer = window.setInterval(() => {
      if (!importing) return;
      if (net.isOffline()) {
        clearInterval(timer);
        finish({ list, added: 0, unmatched: 0, offline: true });
        return;
      }
      importing = { ...importing, done: importing.done + 1 };
      if (importing.done >= importing.total) {
        clearInterval(timer);
        const added = lines - misses.length;
        const prior = sends.findIndex((s) => s.list === list);
        if (prior >= 0) sends.splice(prior, 1);
        sends.unshift({ list, at: 'Just now', lines, added, unmatched: misses });
        finish({ list, added, unmatched: misses.length, offline: false });
        return;
      }
      emit();
    }, 450);
  },
};

function finish(f: Finished) {
  importing = null;
  finished = f;
  emit();
  finishTimer = window.setTimeout(() => {
    finished = null;
    emit();
  }, FINISHED_MS);
}

/** Stops listening: a note being imported stops, and the pill goes. */
function stop() {
  clearInterval(timer);
  clearTimeout(finishTimer);
  importing = null;
  finished = null;
}

// F14: Phone notes switched off in Features (or TMDB account, which it needs) stops it as its own switch does.
settings.on(() => {
  if (featureOn('notes') || (!importing && !finished)) return;
  stop();
  emit();
});
