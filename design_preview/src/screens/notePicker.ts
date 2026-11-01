import { app, type Screen } from '../core/app';
import { h } from '../core/dom';
import { focusable } from '../core/focus';
import { notes, type NoteAsk } from '../core/notes';
import { button } from '../ui/widgets';

/**
 * Phone notes' picker (the project lead, 2026-11-01; no Figma design yet, drawn as the confirm dialog is): for a line
 * of a list's last send that TMDB didn't match exactly, its best three answers as posters with title and year, and
 * None of these. OK on a poster puts it on the list (on Seen, for a ticked line); None of these stops asking; Back
 * leaves the line in Last sends.
 */
export function notePicker(list: string, ask: NoteAsk): Screen {
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
  const line = `“${ask.line}” in the note for ${list} matched nothing exactly.`;
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
      h('div.dialog-actions', none),
    ),
  );
  return { label: title, el, layer: 'dialog', defaultFocus: () => cards[0] ?? none };
}
