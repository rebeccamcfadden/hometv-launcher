import { app, type Screen } from '../core/app';
import { h, imgUrl, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { store } from '../core/store';
import { TITLES } from '../data/catalog';
import { bestOffer, isIncludedOffer, offersByService, price, runtimeLabel, type ServiceOffers } from '../data/offers';
import { SERVICES } from '../data/services';
import type { Title } from '../types';
import { icon, type IconName } from '../ui/icons';
import { button, chip, personCard, smallCard } from '../ui/widgets';
import { confirmDialog } from './confirm';
import { personScreen } from './person';

const RENT_NOTE = 'You finish paying in that app. Rentals usually give you 30 days to start and 48 hours once you press play.';

function purchase(t: Title, s: ServiceOffers) {
  const name = SERVICES[s.service].name;
  const actions = [];
  if (s.rent !== undefined) actions.push({ label: `Rent · ${price(s.rent)}`, primary: true, onOk: () => app.toast(`Opening ${name} to rent ${t.name}…`) });
  if (s.buy !== undefined) actions.push({ label: `Buy · ${price(s.buy)}`, primary: !actions.length, onOk: () => app.toast(`Opening ${name} to buy ${t.name}…`) });
  app.push(confirmDialog({ title: `${t.name} on ${name}`, body: `${s.quality}. This opens the ${name} app.`, detail: RENT_NOTE, actions }));
}

function similar(t: Title): Title[] {
  return TITLES.filter((o) => o.id !== t.id)
    .map((o) => ({ o, s: o.genres.filter((g) => t.genres.includes(g)).length * 2 + (o.kind === t.kind ? 1 : 0) + o.score / 10 }))
    .filter((x) => x.s >= 2)
    .sort((a, b) => b.s - a.s)
    .slice(0, 8)
    .map((x) => x.o);
}

/** Title preview, Option C: art on top, essentials and actions on the card, tabs for the rest. */
export function titlePreview(t: Title): Screen {
  store.pushViewed(t.id);
  const isShow = t.kind === 'show';
  const tabs = isShow
    ? ['Overview', 'Episodes', 'Where to watch', 'Cast & crew', 'More like this']
    : ['Overview', 'Where to watch', 'Cast & crew', 'More like this'];
  let active = tabs[0];
  let season = t.next?.season ?? 1;
  const offer = bestOffer(t);
  const svcName = offer ? SERVICES[offer.service].name : '';

  // ---- actions ----
  const next = t.next ? `S${t.next.season} E${t.next.episode}` : '';
  const watchLabel = !offer
    ? 'Not on your services'
    : isIncludedOffer(offer)
      ? isShow
        ? `Watch ${next} on ${svcName}`
        : `Watch on ${svcName}`
      : `${offer.type === 'rent' ? 'Rent' : 'Buy'} on ${svcName} · ${price(offer.price ?? 0)}`;
  const watch = button(watchLabel, {
    icon: 'play',
    primary: true,
    group: 'actions',
    key: 'watch',
    isDefault: true,
    onOk: () => {
      if (!offer) return app.toast('Not available on your services or stores');
      if (isIncludedOffer(offer)) return app.toast(`Opening ${isShow ? `${next} of ` : ''}${t.name} in ${svcName}…`);
      const s = offersByService(t).find((x) => x.service === offer.service);
      if (s) purchase(t, s);
    },
  });

  const toggle = (
    key: string,
    icOff: IconName,
    icOn: IconName,
    off: string,
    on: string,
    get: (id: string) => boolean,
    flip: (id: string) => boolean,
    msg: (on: boolean) => string,
  ) => {
    const el = focusable(h('div.btn.btn--icon'), {
      group: 'actions',
      key,
      onOk: () => {
        app.toast(msg(flip(t.id)));
        paint();
      },
    });
    const paint = () => {
      const state = get(t.id);
      el.classList.toggle('is-on', state);
      replace(el, h('span.btn-icon', { html: icon(state ? icOn : icOff, 26, state && icOn === 'heart') }), h('span.btn-label', state ? on : off));
    };
    paint();
    return el;
  };

  const actions = h(
    'div.pv-actions',
    watch,
    toggle('mylist', 'plus', 'check', 'My list', 'On My list', store.inMyList, store.toggleMyList, (on) => (on ? 'Added to My list' : 'Removed from My list')),
    toggle('seen', 'eye', 'eye', 'Mark seen', 'Seen', store.isSeen, store.toggleSeen, (on) => (on ? 'Marked as seen' : 'Marked as not seen')),
    toggle('fav', 'heart', 'heart', 'Favorite', 'Favorite', store.isFavorite, store.toggleFavorite, (on) => (on ? 'Added to favorites' : 'Removed from favorites')),
    button('Trailer', { icon: 'trailer', group: 'actions', key: 'trailer', onOk: () => app.toast(`Opening the ${t.name} trailer on YouTube…`) }),
  );

  // ---- tab content ----
  const content = h('div.pv-content');

  const overview = () => {
    const cheapest = t.offers.filter((o) => o.type === 'rent').sort((a, b) => (a.price ?? 0) - (b.price ?? 0))[0];
    const included = offer && isIncludedOffer(offer);
    const lead = t.crew[0];
    return h(
      'div.pv-overview',
      h('p.pv-synopsis', t.synopsis),
      h(
        'div.pv-facts',
        h(
          'div.pv-fact',
          included ? h('span.badge', offer.type === 'free' ? 'Free' : 'Included') : null,
          h('span', included ? `on ${svcName}` : offer ? `${offer.type === 'rent' ? 'Rent' : 'Buy'} on ${svcName}` : 'Not on your services'),
          cheapest && included ? h('span.muted', `· Rent from ${price(cheapest.price ?? 0)} elsewhere`) : null,
        ),
        t.cast.length ? h('div.pv-fact', h('span.muted', 'Starring'), t.cast.slice(0, 3).map((c) => c.name).join(', ')) : null,
        lead ? h('div.pv-fact', h('span.muted', lead.role), t.crew.filter((c) => c.role === lead.role).map((c) => c.name).join(', ')) : null,
      ),
    );
  };

  const whereToWatch = () =>
    h(
      'div.pv-row',
      { 'data-scroll': 'x', 'data-scroll-pad': 0 },
      offersByService(t).map((s, i) =>
        focusable(
          h(
            `div.service${s.included || s.free ? '.service--included' : ''}`,
            h('div.service-head', h('span.service-name', SERVICES[s.service].name), s.included || s.free ? h('span.badge', s.free ? 'Free' : 'Included') : null),
            h(
              'div.service-offer',
              s.included ? 'With your subscription' : s.free ? 'Free with ads' : [s.rent !== undefined ? `Rent ${price(s.rent)}` : '', s.buy !== undefined ? `Buy ${price(s.buy)}` : ''].filter(Boolean).join(' · '),
            ),
            h('div.service-detail', s.quality),
          ),
          {
            group: 'wtw',
            key: `svc-${s.service}`,
            isDefault: i === 0,
            onOk: () => (s.included || s.free ? app.toast(`Opening ${t.name} in ${SERVICES[s.service].name}…`) : purchase(t, s)),
          },
        ),
      ),
    );

  const cast = () => {
    const people = [...t.cast, ...t.crew];
    if (!people.length) return h('div.pv-empty', 'No cast information for this title yet.');
    return h(
      'div.pv-row',
      { 'data-scroll': 'x', 'data-scroll-pad': 0 },
      people.map((c, i) => personCard(c.name, c.role, { group: 'cast', key: `cast-${i}`, isDefault: i === 0, onOk: () => app.push(personScreen(c.name)) })),
    );
  };

  const more = () =>
    h(
      'div.pv-row',
      { 'data-scroll': 'x', 'data-scroll-pad': 0 },
      similar(t).map((o, i) => smallCard(o, { group: 'similar', key: `sim-${o.id}`, isDefault: i === 0, onOk: () => app.push(titlePreview(o)) })),
    );

  const episodesWrap = h('div.pv-episodes');
  const episodes = () => {
    const seasons = t.seasons ?? [];
    const list = h('div.pv-row', { 'data-scroll': 'x', 'data-scroll-pad': 0 });
    const paintEpisodes = () => {
      const s = seasons.find((x) => x.n === season);
      replace(
        list,
        (s?.episodes ?? []).map((e) => {
          const watched = !!t.next && (season < t.next.season || (season === t.next.season && e.n < t.next.episode));
          const upNext = !!t.next && season === t.next.season && e.n === t.next.episode;
          return focusable(
            h(
              `div.episode${upNext ? '.episode--next' : ''}`,
              h('div.episode-num', `E${e.n}`, upNext ? h('span.badge', 'Up next') : watched ? h('span.episode-seen', { html: icon('check', 20) }) : null),
              h('div.episode-name', e.name),
              h('div.episode-meta', `${runtimeLabel(e.runtime)}${watched ? ' · Watched' : ''}`),
            ),
            {
              group: 'episodes',
              key: `ep-${season}-${e.n}`,
              isDefault: upNext || (!t.next && e.n === 1),
              onOk: () => app.toast(`Opening S${season} E${e.n} of ${t.name} in ${svcName}…`),
            },
          );
        }),
      );
      list.scrollLeft = 0;
    };
    paintEpisodes();
    replace(
      episodesWrap,
      h(
        'div.pv-seasons',
        { 'data-scroll': 'y', 'data-scroll-pad': 0 },
        seasons.map((s) =>
          chip(`Season ${s.n}`, {
            selected: s.n === season,
            group: 'seasons',
            key: `season-${s.n}`,
            isDefault: s.n === season,
            onFocus: () => {
              if (season === s.n) return;
              season = s.n;
              episodesWrap.querySelectorAll('.pv-seasons .chip').forEach((c) => c.classList.toggle('is-selected', c === app.focused));
              paintEpisodes();
            },
          }),
        ),
      ),
      list,
    );
    return episodesWrap;
  };

  const renderers: Record<string, () => HTMLElement> = {
    Overview: overview,
    Episodes: episodes,
    'Where to watch': whereToWatch,
    'Cast & crew': cast,
    'More like this': more,
  };

  const tabEls = tabs.map((name, i) =>
    focusable(h(`div.tab${i === 0 ? '.is-active' : ''}`, name), {
      group: 'tabs',
      key: `tab-${name}`,
      isDefault: i === 0,
      onFocus: () => setTab(name),
    }),
  );

  function setTab(name: string) {
    if (name === active && content.childElementCount) return;
    active = name;
    tabEls.forEach((el) => el.classList.toggle('is-active', el.textContent === name));
    replace(content, renderers[name]());
  }
  setTab(active);

  const details = h(
    'div.pv-details',
    h('span', String(t.year)),
    h('span.dot', '·'),
    h('span', t.genres.join(', ')),
    h('span.dot', '·'),
    h('span', isShow ? `${t.seasons?.length ?? 0} seasons` : runtimeLabel(t.runtime ?? 0)),
    h('span.rating', t.rating),
    h('span.score', h('span', { html: icon('star', 22, true) }), t.score.toFixed(1)),
  );

  const backBtn = button('Back', { icon: 'back', small: true, key: 'back', onOk: () => app.back() });

  const el = h(
    'div.screen.layer-popup',
    h('div.scrim'),
    h(
      'div.popup',
      h('img.pv-art', { src: imgUrl(t.art), alt: '' }),
      h('div.pv-fade'),
      h('div.pv-back', backBtn),
      h(
        'div.pv-body',
        h('div.pv-head', h('div.pv-titleblock', h('h1.pv-title', t.name), details), actions),
        h('div.pv-tabs', tabEls),
        h('div.pv-divider'),
        content,
      ),
    ),
  );

  return { label: t.name, el, layer: 'popup', defaultFocus: () => watch };
}
