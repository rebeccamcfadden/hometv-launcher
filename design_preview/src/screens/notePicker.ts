import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { notes, type NoteAsk, type NoteCandidate } from '../core/notes';
import { icon } from '../ui/icons';
import { button } from '../ui/widgets';

const KEY_ROWS = ['abcdef', 'ghijkl', 'mnopqr', 'stuvwx', 'yz1234', '567890'];
const TYPEABLE = /^[a-z0-9 \-:'’&.!?]$/i;

/** Mock answers for a search typed on the TV: Search's catalogue isn't searched here, so a fixed set stands in. */
function mockSearch(q: string): NoteCandidate[] {
  const t = q.trim();
  if (!t) return [];
  const title = t.replace(/\b\w/g, (c) => c.toUpperCase());
  return [
    { title, year: 1989, kind: 'Movie' },
    { title: `${title} in NYC`, year: 2014, kind: 'Movie' },
    { title: `The ${title}`, year: 2021, kind: 'TV show' },
  ];
}

/**
 * Phone notes' picker (the project lead, 2026-11-01; no Figma design yet, drawn as the confirm dialog is): for a line
 * of a list's last send that TMDB didn't match exactly, its best three answers as posters with title and year, and
 * None of these. OK on a poster puts it on the list (on Seen, for a ticked line); None of these stops asking; Back
 * leaves the line in Last sends. Search again (the project lead, F9) opens the line's text over Search's keyboard to
 * fix and look up again; the answers then come from what was typed.
 */
export function notePicker(list: string, ask: NoteAsk, searched?: string): Screen {
  const cards = ask.top.map((c, i) =>
    focusable(
      h(
        'div.note-pick',
        h('div.note-pick-poster', { 'data-kind': c.kind }, c.title),
        h('div.note-pick-title', c.title),
        h('div.note-pick-meta', `${c.year} · ${c.kind}`),
      ),
      {
        group: 'note-pick',
        key: `note-pick-${i}`,
        isDefault: i === 0,
        onOk: () => {
          app.pop();
          notes.answer(list, ask.line, c);
        },
      },
    ),
  );
  const none = button(ask.top.length ? 'None of these' : 'Stop asking', {
    key: 'note-pick-none',
    onOk: () => {
      app.pop();
      notes.answer(list, ask.line, null);
    },
  });
  const again = button('Search again', {
    icon: 'search',
    key: 'note-pick-edit',
    onOk: () => {
      app.pop();
      app.push(noteSearch(list, ask, searched));
    },
  });
  const line = searched ? `Results for “${searched}”.` : `“${ask.line}” in the note for ${list} matched nothing exactly.`;
  const then = !ask.top.length
    ? 'Fix the line in the note and send it again, or stop asking about it.'
    : ask.ticked
      ? 'It’s ticked, so the one you pick goes to Seen.'
      : `The one you pick goes onto ${list}.`;
  const title = ask.top.length ? 'Which one did you mean?' : 'Nothing close on TMDB';
  const el = h(
    'div.screen.layer-dialog',
    h('div.scrim'),
    h(
      'div.dialog',
      h('div.dialog-title', title),
      h('div.dialog-body', `${line} ${then}`),
      cards.length ? h('div.note-picks', cards) : null,
      h('div.dialog-actions', again, none),
    ),
  );
  return { label: title, el, layer: 'dialog', defaultFocus: () => cards[0] ?? none };
}

/**
 * Search again: the line's text (or what was last searched) in a bar over Search's keyboard; Search looks it up and
 * keeps the answers with the line; Cancel and Back go back to the answers as they were.
 */
function noteSearch(list: string, ask: NoteAsk, searched?: string): Screen {
  let query = searched ?? ask.line;
  const typed = h('span.sb-typed');
  const paint = () => {
    typed.classList.toggle('is-placeholder', !query.trim());
    replace(typed, query.trim() ? query : 'Type a title');
  };
  const set = (q: string) => {
    query = q.replace(/^\s+/, '').replace(/\s{2,}/g, ' ');
    paint();
  };
  const back = () => {
    app.pop();
    app.push(notePicker(list, ask, searched));
  };
  const search = () => {
    if (!query.trim()) return;
    app.pop();
    const top = mockSearch(query);
    notes.offer(list, ask.line, top);
    app.push(notePicker(list, { ...ask, top }, query.trim()));
  };
  const key = (label: string, onOk: () => void, cls = '') =>
    focusable(h(`div.key${cls}`, label), { group: 'nkb', key: `nk-${label}`, isDefault: label === 'a', onOk });
  const keyboard = h(
    'div.kbp',
    h(
      'div.keyboard',
      KEY_ROWS.map((row) => h('div.key-row', [...row].map((c) => key(c, () => set(query + c))))),
      h(
        'div.key-row',
        key('Space', () => set(query + ' '), '.key--space'),
        focusable(h('div.key', { html: icon('backspace', 26) }), { group: 'nkb', key: 'nk-del', onOk: () => set(query.slice(0, -1)) }),
        key('Clear', () => set(''), '.key--clear'),
      ),
    ),
  );
  paint();
  const el = h(
    'div.screen.layer-dialog',
    h('div.scrim'),
    h(
      'div.dialog.note-search',
      h('div.dialog-title', 'Search TMDB'),
      h('div.dialog-body', `For “${ask.line}” in the note for ${list}. Fix the spelling or shorten it, then Search.`),
      h('div.qbar', h('span', { html: icon('search', 28) }), typed),
      h(
        'div.note-search-body',
        keyboard,
        h(
          'div.note-search-actions',
          button('Search', { icon: 'search', primary: true, key: 'note-search-go', onOk: search }),
          button('Cancel', { key: 'note-search-cancel', onOk: back }),
        ),
      ),
    ),
  );
  return {
    label: 'Search TMDB',
    el,
    layer: 'dialog',
    defaultFocus: () => el.querySelector<HTMLElement>('[data-key="nk-a"]'),
    onText(ch) {
      if (!TYPEABLE.test(ch)) return false;
      set(query + ch.toLowerCase());
      return true;
    },
    onKey(k) {
      if (k !== 'back') return false;
      back();
      return true;
    },
  };
}
