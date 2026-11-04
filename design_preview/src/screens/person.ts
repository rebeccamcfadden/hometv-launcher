import { app, type Screen } from '../core/app';
import { h, initials, replace } from '../core/dom';
import { cardOffer, isOwnIncluded } from '../data/offers';
import { personData, samplePersonData, type PersonPageData } from '../data/people';
import type { Title } from '../types';
import { button, chip, guide, miniCard } from '../ui/widgets';
import { dropdown } from './dropdown';
import { titlePreview } from './preview';

/** "Acting" is shown as "Actor" over the name, and so on; anything else as TMDB names it. */
const JOB: Record<string, string> = {
  Acting: 'Actor',
  Directing: 'Director',
  Writing: 'Writer',
  Production: 'Producer',
  Creating: 'Creator',
  Sound: 'Composer',
  Camera: 'Cinematographer',
  Editing: 'Editor',
};

type Filter = 'all' | 'included' | 'paid' | `job:${string}`;
type Sort = 'known' | 'newest' | 'az';
const SORTS: [Sort, string][] = [
  ['known', 'Best known'],
  ['newest', 'Newest first'],
  ['az', 'A–Z'],
];

/** The bio's length that fits the left column's six lines; a longer one gets Read more. */
const BIO_FITS = 300;
/** Five C cards across (Figma 266:704). */
const COLUMNS = 5;

/**
 * The person page, Rebecca's pick B (2026-11-04 18:55, Figma "Person page" 266:704): the person on the left (their
 * portrait, what they do, born when and where, a six-line bio with Read more) and on the right a grid of their titles
 * as C cards, five across, under chips: All, On your services, Rent or buy, one per job when they have more than
 * one (Acting, Directing), and Sort. The left side stays while the grid scrolls.
 */
export function personScreen(name: string): Screen {
  return personPage(personData(name));
}

/** The dev bar's long-career sample (see {@link samplePersonData}). */
export const samplePersonScreen = () => personPage(samplePersonData());

function personPage(p: PersonPageData): Screen {
  let filter: Filter = 'all';
  let sort: Sort = 'known';

  const back = button('Back', { icon: 'back', small: true, key: 'back', onOk: () => app.back() });
  const facts = [p.born && `Born ${p.born}`, p.birthplace].filter(Boolean) as string[];
  const long = p.bio.length > BIO_FITS || p.bio.includes('\n\n');
  const readMore = long ? button('Read more', { small: true, key: 'bio', onOk: () => app.push(biography(p)) }) : null;
  const side = h(
    'div.pp-side',
    h('div.person-portrait.pp-portrait', initials(p.name)),
    p.department ? h('div.eyebrow.pp-job', JOB[p.department] ?? p.department) : null,
    h('h1.pp-name', p.name),
    facts.length ? h('div.pp-facts', facts.map((f) => h('div', f))) : null,
    p.bio ? h('div.pp-bio', p.bio.split('\n\n')[0]) : null,
    readMore,
  );

  const included = p.titles.filter(isOwnIncluded);
  const paid = p.titles.filter((t) => !isOwnIncluded(t) && cardOffer(t));
  const jobs = new Map<string, Title[]>();
  for (const t of p.titles) for (const d of new Set((p.roles.get(t.id) ?? []).map((r) => r.department))) jobs.set(d, [...(jobs.get(d) ?? []), t]);

  const shown = (): Title[] => {
    const list = filter === 'all' ? p.titles : filter === 'included' ? included : filter === 'paid' ? paid : (jobs.get(filter.slice(4)) ?? []);
    const out = [...list];
    if (sort === 'newest') out.sort((a, b) => b.year - a.year || a.name.localeCompare(b.name));
    else if (sort === 'az') out.sort((a, b) => a.name.localeCompare(b.name));
    // Best known: on the TV, TMDB's vote count; the mock catalogue has only the score.
    else out.sort((a, b) => b.score - a.score);
    return out;
  };

  const chipsEl = h('div.chips.pp-chips', { 'data-scroll': 'x', 'data-scroll-pad': 24 });
  const sortSlot = h('span.pp-sort');
  const grid = h('div.pp-grid');
  const scroll = h('div.pp-scroll', { 'data-scroll': 'y', 'data-scroll-pad': 40, 'data-scroll-pad-bottom': 200 }, grid, h('div.page-end'));

  const render = () => {
    const c = (id: Filter, label: string, n: number) =>
      chip(`${label} · ${n}`, {
        group: 'pp-chips',
        key: `pp-${id}`,
        selected: filter === id,
        isDefault: filter === id,
        onOk: () => {
          if (filter === id) return;
          filter = id;
          render();
          app.refocus();
        },
      });
    replace(
      chipsEl,
      c('all', 'All', p.titles.length),
      included.length ? c('included', 'On your services', included.length) : null,
      paid.length ? c('paid', 'Rent or buy', paid.length) : null,
      jobs.size > 1 ? [...jobs].map(([d, ts]) => c(`job:${d}`, d, ts.length)) : null,
      h('span.pp-chips-end'),
    );
    const sortBtn = button(`Sort · ${SORTS.find(([s]) => s === sort)![1]}`, {
      icon: 'sort',
      small: true,
      key: 'pp-sort',
      onOk: () =>
        app.push(
          dropdown(
            'Sort',
            sortBtn,
            SORTS.map(([s, label]) => ({
              label,
              checked: s === sort,
              onOk: () => {
                if (s === sort) return;
                sort = s;
                render();
                // The menu has closed and focused the old Sort button, which render() just replaced.
                app.refocus();
              },
            })),
          ),
        ),
    });
    replace(sortSlot, sortBtn);
    const list = shown();
    scroll.scrollTop = 0;
    replace(
      grid,
      list.length
        ? list.map((t, i) => miniCard(t, { group: 'pp-grid', key: `pp-${t.id}`, isDefault: i === 0, onOk: () => app.push(titlePreview(t)) }))
        : h('div.empty-note.pp-empty', 'Nothing here on your services or stores.'),
    );
  };
  const box = h('div.pp-box', scroll);
  scroll.addEventListener('scroll', () => box.classList.toggle('is-scrolled', scroll.scrollTop > 0));
  render();

  const el = h(
    'div.screen.screen--base.person-page',
    h('div.pp-back', back),
    side,
    h('div.pp-main', h('div.pp-bar', chipsEl, sortSlot), box),
    h('div.bottom-fade'),
    guide('Illustrative titles, services and prices', 'OK for title details · Back to return'),
  );
  return {
    label: p.name,
    el,
    defaultFocus: () => grid.querySelector<HTMLElement>('[data-focus]') ?? back,
    // The grid is a closed box on its right and foot: the D-pad stops there rather than jumping to the chips or the
    // left column. Left from its first column goes to the person's side (Read more, else Back).
    onKey(key, f) {
      const cards = [...grid.querySelectorAll<HTMLElement>('[data-focus]')];
      const i = f ? cards.indexOf(f) : -1;
      if (i < 0) return false;
      const n = cards.length;
      if (key === 'left' && i % COLUMNS === 0) {
        app.focus(readMore ?? back);
        return true;
      }
      if (key === 'right' && (i % COLUMNS === COLUMNS - 1 || i === n - 1)) return true;
      if (key === 'down' && i + COLUMNS >= n) {
        // A shorter last row: its last card, as the library grid does.
        if (Math.floor(i / COLUMNS) < Math.floor((n - 1) / COLUMNS)) app.focus(cards[n - 1]);
        return true;
      }
      return false;
    },
  };
}

/** Read more: the whole biography in a dialog over the page; Close or Back returns to Read more. */
function biography(p: PersonPageData): Screen {
  const close = button('Close', { primary: true, key: 'close', onOk: () => app.pop() });
  const el = h(
    'div.screen.layer-dialog',
    h('div.scrim'),
    h(
      'div.dialog.dialog--bio',
      h('div.dialog-title', p.name),
      h('div.pp-bio-full', p.bio.split('\n\n').map((para) => h('p', para))),
      h('div.dialog-actions', close),
    ),
  );
  return { label: `${p.name} · biography`, el, layer: 'dialog', defaultFocus: () => close };
}
