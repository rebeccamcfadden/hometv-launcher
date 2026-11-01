import { app, type Screen } from '../core/app';
import { h, imgUrl, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { lists } from '../core/lists';
import { store } from '../core/store';
import { TITLES } from '../data/catalog';
import { availability, bestOffer, subscribedCount, isOwnIncluded, isIncludedOffer, offerLabel, paidAction } from '../data/offers';
import { SERVICES } from '../data/services';
import type { Kind, Title } from '../types';
import { icon } from '../ui/icons';
import { button, chip, guide, navBar, provenance, titleMeta } from '../ui/widgets';
import { titlePreview } from './preview';

interface Field {
  id: string;
  label: (k: Kind) => string;
  options: string[];
  test: (t: Title, i: number) => boolean;
}

const FIELDS: Field[] = [
  {
    id: 'length',
    label: (k) => (k === 'movie' ? 'Movie length' : 'Episode length'),
    options: ['Any length', 'Under 2 hours', 'Under 90 minutes'],
    test: (t, i) => i === 0 || t.kind === 'show' || (t.runtime ?? 0) < [0, 120, 90][i],
  },
  {
    id: 'genre',
    label: () => 'Genre · Match either',
    options: ['Any genre', 'Drama + Sci-fi', 'Comedy + Animation', 'Mystery + Thriller', 'Documentary'],
    test: (t, i) => i === 0 || [[], ['Drama', 'Sci-fi'], ['Comedy', 'Animation'], ['Mystery', 'Thriller'], ['Documentary']][i].some((g) => t.genres.includes(g)),
  },
  {
    id: 'score',
    label: () => 'Review score · Out of 10',
    options: ['Any score', '7.0 and above', '8.0 and above'],
    test: (t, i) => t.score >= [0, 7, 8][i],
  },
  {
    id: 'rating',
    label: () => 'Age / content rating',
    options: ['Any rating', 'Up to PG-13', 'Up to R'],
    test: (t, i) => i === 0 || (i === 1 ? !['R', 'TV-MA', 'NR'].includes(t.rating) : t.rating !== 'NR'),
  },
  {
    id: 'watch',
    label: () => 'Watch state',
    options: ['Unwatched only', 'Seen or not'],
    test: (t, i) => i === 1 || !store.isSeen(t.id),
  },
  {
    id: 'avail',
    label: () => 'Availability',
    options: ['Included + paid', 'Included only'],
    test: (t, i) => i === 0 || isOwnIncluded(t),
  },
];

interface PickState {
  kind: Kind;
  source: 'lists' | 'services';
  /** List ids from {@link lists}: 'my' and shown TMDB (or mock) lists. */
  lists: Set<string>;
  values: Record<string, number>;
}

function matches(s: PickState): Title[] {
  const on = lists.all().filter((l) => s.lists.has(l.id));
  return TITLES.filter((t) => {
    if (t.kind !== s.kind) return false;
    if (s.source === 'lists') {
      if (!on.some((l) => l.has(t))) return false;
    } else if (!isOwnIncluded(t)) return false;
    return FIELDS.every((f) => f.test(t, s.values[f.id] ?? 0));
  });
}

const shuffle = <T>(a: T[]) => {
  const out = [...a];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
};

const sourceText = (s: PickState) =>
  s.source === 'services'
    ? `Everything included on your ${subscribedCount()} services`
    : lists
        .all()
        .filter((l) => s.lists.has(l.id))
        .map((l) => l.name)
        .join(' + ') || 'No lists';

const filterText = (s: PickState) =>
  [s.kind === 'movie' ? 'Movies' : 'TV Shows', ...FIELDS.map((f) => f.options[s.values[f.id] ?? 0]).filter((v) => !/^Any/.test(v))].join(' · ');

export function pickSetup(kind: Kind): Screen {
  const s: PickState = {
    kind,
    source: 'lists',
    // My list and the first imported list, as Household was before the lists came from the account.
    lists: new Set<string>(lists.all().slice(0, 2).map((l) => l.id)),
    values: { length: 1, genre: 1, score: 1, rating: 2, watch: 0, avail: 0 },
  };

  const sourceRow = h('div.chips');
  const listsRow = h('div.chips');
  const moodRow = h('div.chips');
  const fieldsEl = h('div.pick-fields');
  const count = h('div.pick-count');
  const countText = h('div.pick-count-text');
  const summary = h('div.pick-summary');
  const note = h('div.pick-note');

  const findBtn = button('Find picks', {
    icon: 'shuffle',
    primary: true,
    key: 'find',
    onOk: () => {
      const m = matches(s);
      if (!m.length) return app.toast('No matches. Loosen a filter first.');
      app.push(pickResults(s));
    },
  });

  const render = () => {
    replace(
      sourceRow,
      (['lists', 'services'] as const).map((src) =>
        chip(src === 'lists' ? 'My watchlists' : 'My services', {
          selected: s.source === src,
          group: 'source',
          key: `src-${src}`,
          onOk: () => {
            s.source = src;
            render();
          },
        }),
      ),
    );
    replace(
      listsRow,
      lists.all().map((l) =>
        chip(l.name, {
          selected: s.lists.has(l.id),
          check: true,
          group: 'lists',
          key: `pl-${l.id}`,
          onOk: () => {
            if (s.lists.has(l.id)) s.lists.delete(l.id);
            else s.lists.add(l.id);
            render();
          },
        }),
      ),
    );
    listsRow.toggleAttribute('data-inert', s.source !== 'lists');
    listsRow.classList.toggle('is-disabled', s.source !== 'lists');
    replace(
      moodRow,
      (['movie', 'show'] as Kind[]).map((k) =>
        chip(k === 'movie' ? 'Movies' : 'TV Shows', {
          selected: s.kind === k,
          group: 'mood',
          key: `mood-${k}`,
          onOk: () => {
            s.kind = k;
            render();
          },
        }),
      ),
    );
    replace(
      fieldsEl,
      FIELDS.map((f) =>
        focusable(
          h('div.field', h('div.field-label', f.label(s.kind)), h('div.field-value', f.options[s.values[f.id] ?? 0], h('span', { html: icon('chevDown', 22) }))),
          {
            group: 'fields',
            key: `fld-${f.id}`,
            onOk: () => {
              s.values[f.id] = ((s.values[f.id] ?? 0) + 1) % f.options.length;
              render();
            },
          },
        ),
      ),
    );
    const n = Math.min(4, matches(s).length);
    replace(count, String(n));
    replace(countText, n === 1 ? 'pick, just for tonight' : 'picks, just for tonight');
    replace(
      summary,
      h('div.pick-summary-lead', `Unwatched ${s.kind === 'movie' ? 'movies' : 'shows'} from ${sourceText(s)}.`),
      h('div.pick-summary-filters', filterText(s)),
    );
    replace(findBtn.querySelector('.btn-label')!, n ? `Find ${n} pick${n === 1 ? '' : 's'}` : 'No matches');
    replace(note, n ? `${matches(s).length} titles match. Reroll on the next screen, or adjust filters.` : 'Nothing matches. Loosen a filter.');
    app.refocus();
  };
  render();

  const el = h(
    'div.screen.screen--base.pick',
    navBar(kind === 'movie' ? 'movies' : 'tv'),
    h(
      'div.pick-body',
      h(
        'div.pick-left',
        h('h1.page-title', 'Pick for me'),
        h('div.page-context', 'A little less scrolling. Four possibilities for tonight.'),
        h('div.pick-label', 'Choose from'),
        sourceRow,
        h('div.pick-label', 'Imported watchlists · Select one or more'),
        listsRow,
        fieldsEl,
        h('div.pick-hint', 'OK on a field steps through its options. TV Shows use episode length.'),
      ),
      h(
        'div.pick-right',
        h('div.pick-label', 'What are you in the mood for?'),
        moodRow,
        h('div.pick-card', h('div.pick-head', count, countText), summary, h('div.pick-divider'), findBtn, note),
      ),
    ),
    guide('Illustrative filter setup · No ads. No promoted picks.', 'Use arrows to choose · OK to select'),
  );

  return { label: 'Pick for me', el, defaultFocus: () => findBtn, onShow: render };
}

function pickResults(s: PickState): Screen {
  let picks: Title[] = [];
  const cards = h('div.pick-cards');
  const barTitle = h('div.pick-bar-title');
  const barNote = h('div.pick-bar-note');
  const barActions = h('div.pick-bar-actions');

  const setFocus = (t: Title) => {
    const o = bestOffer(t);
    const name = o ? SERVICES[o.service].name : '';
    replace(barTitle, t.name);
    replace(barNote, o ? `${offerLabel(o)} · Open the title in your service` : 'Not on your services');
    replace(
      barActions,
      button('Title details', { group: 'bar', key: 'details', onOk: () => app.push(titlePreview(t)) }),
      button(o && isIncludedOffer(o) ? `Open in ${name}` : o ? paidAction(o) : 'Unavailable', {
        icon: 'play',
        primary: true,
        group: 'bar',
        key: 'open',
        isDefault: true,
        onOk: () => app.push(titlePreview(t)),
      }),
    );
  };

  const roll = () => {
    picks = shuffle(matches(s)).slice(0, 4);
    replace(
      cards,
      picks.map((t, i) => {
        const o = bestOffer(t);
        return focusable(
          h(
            'div.pick-tile',
            h('div.card-art', h('img', { src: imgUrl(t.art), alt: '' })),
            h(
              'div.card-row',
              h('div.card-name', t.name),
              h('div.card-provider', h('div', o ? SERVICES[o.service].name : ''), h('div.card-avail', availability(o))),
            ),
            h('div.card-meta', titleMeta(t)),
            h('div.card-sub', provenance(t)),
            h('div.pick-blurb', t.synopsis),
          ),
          { group: 'picks', key: `pk-${i}`, isDefault: i === 0, onFocus: () => setFocus(t), onOk: () => app.push(titlePreview(t)) },
        );
      }),
    );
    if (picks[0]) setFocus(picks[0]);
  };
  roll();

  const reroll = button(`Reroll ${Math.min(4, matches(s).length)} picks`, {
    icon: 'shuffle',
    small: true,
    key: 'reroll',
    // Focus stays on Reroll, so it can be pressed again and again.
    onOk: () => roll(),
  });
  const adjust = button('Adjust filters', { small: true, key: 'adjust', onOk: () => app.back() });

  const el = h(
    'div.screen.screen--base.pick',
    navBar(s.kind === 'movie' ? 'movies' : 'tv'),
    h(
      'div.pick-results',
      h(
        'div.pick-results-head',
        h(
          'div',
          h('h1.page-title', 'Tonight could look like this'),
          h('div.page-context', `${picks.length} random matches · ${sourceText(s)} · All ${subscribedCount()} services`),
        ),
        h('div.pick-results-actions', adjust, reroll),
      ),
      h('div.pick-filterline', filterText(s)),
      cards,
      h('div.pick-bar', h('div', barTitle, barNote), barActions),
    ),
    guide('Illustrative recommendations, scores, prices & availability', '← → Choose a pick · OK for details'),
  );

  return {
    label: 'Picks',
    el,
    defaultFocus: () => cards.querySelector<HTMLElement>('[data-focus]'),
    // Up from a pick reaches Adjust filters or Reroll, whichever is nearer, before the nav.
    onKey(key, f) {
      if (key !== 'up' || !f || !cards.contains(f)) return false;
      const fr = f.getBoundingClientRect();
      const mid = (r: DOMRect) => r.left + r.width / 2;
      const a = adjust.getBoundingClientRect();
      const r = reroll.getBoundingClientRect();
      app.focus(Math.abs(mid(a) - mid(fr)) < Math.abs(mid(r) - mid(fr)) ? adjust : reroll);
      return true;
    },
  };
}
