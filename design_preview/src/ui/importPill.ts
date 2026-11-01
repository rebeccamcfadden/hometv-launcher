import { app } from '../core/app';
import { h, replace } from '../core/dom';
import { notes } from '../core/notes';
import { icon } from './icons';

/**
 * The import pill (Phone notes): while a phone's note is being looked up, a small pill at the bottom centre of whatever
 * screen shows, "Importing Rebecca · 3 of 12" over a thin bar; at the end, for a few seconds, "Rebecca · 12 added", or
 * "Rebecca · 9 added · 3 not matched" (amber: the lines are listed in Launcher settings → Phone notes). Never
 * focusable, never in the way of the nav. The shape is the offline pill's (06-states A); amber only for problems. On
 * Launcher settings, whose pane runs to the bottom edge, it sits at the top centre beside the title instead.
 */
export function importPill(): HTMLElement {
  const el = h('div.import-pill', { 'aria-live': 'polite', 'aria-hidden': 'true' });
  let clearTimer = 0;
  const render = () => {
    const i = notes.importing();
    const f = notes.finished();
    const shown = !!(i || f);
    el.classList.toggle('is-shown', shown);
    // Hidden, the live region says nothing, and is emptied once it has faded so it keeps no old status.
    el.setAttribute('aria-hidden', shown ? 'false' : 'true');
    clearTimeout(clearTimer);
    if (!shown) clearTimer = window.setTimeout(() => replace(el), 400);
    el.classList.toggle('is-warn', !!f && (f.unmatched > 0 || f.offline));
    if (i) {
      replace(
        el,
        h('span.import-dot'),
        h('span', `Importing ${i.list} · ${i.done} of ${i.total}`),
        h('span.import-bar', h('i', { style: `width:${i.total ? Math.round((i.done / i.total) * 100) : 0}%` })),
      );
    } else if (f) {
      replace(
        el,
        h('span', { html: icon(f.offline || f.unmatched ? 'alert' : 'check', 22) }),
        f.offline
          ? `${f.list} · waiting for the internet`
          : `${f.list} · ${f.added} added${f.unmatched ? ` · ${f.unmatched} not matched` : ''}`,
      );
    }
  };
  notes.listen(render);
  app.onChange(() => el.classList.toggle('is-top', app.pillAtTop()));
  render();
  return el;
}
