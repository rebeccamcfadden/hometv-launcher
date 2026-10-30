import { app, type Screen } from '../core/app';
import { h, imgUrl, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { net, SAVED_AT } from '../core/net';
import { store } from '../core/store';
import { TITLES } from '../data/catalog';
import {
  attribution,
  bestOffer,
  cheapest,
  cheapestStore,
  isIncludedOffer,
  kr,
  offersByService,
  offersOf,
  paidAction,
  priceLines,
  runtimeLabel,
  watchable,
  type ServiceOffers,
} from '../data/offers';
import { SERVICES } from '../data/services';
import type { Title } from '../types';
import { icon, type IconName } from '../ui/icons';
import { button, chip, personCard, smallCard, smallCardSkeleton } from '../ui/widgets';
import { personScreen } from './person';
import { rentDialog } from './rent';

function similar(t: Title): Title[] {
  return TITLES.filter((o) => o.id !== t.id)
    .map((o) => ({ o, s: o.genres.filter((g) => t.genres.includes(g)).length * 2 + (o.kind === t.kind ? 1 : 0) + o.score / 10 }))
    .filter((x) => x.s >= 2)
    .sort((a, b) => b.s - a.s)
    .slice(0, 8)
    .map((x) => x.o);
}

const sk = (w: number, hgt: number, extra = '') => h('div.sk', { style: `width:${w}px;height:${hgt}px;${extra}` });

/** Each tab's shape while it loads (06-states A): nothing in it is focusable, so focus stays on the tabs. */
const SKELETONS: Record<string, () => HTMLElement> = {
  Overview: () =>
    h(
      'div.pv-overview',
      h('div.pv-sk-text', [800, 760, 790, 520].map((w) => sk(w, 22))),
      h('div.pv-sk-facts', [420, 300, 340].map((w) => sk(w, 22))),
    ),
  Episodes: () =>
    h(
      'div.pv-episodes',
      h('div.pv-seasons', [0, 1].map(() => sk(168, 54, 'border-radius:100px;flex:none'))),
      h('div.pv-row', [0, 1, 2, 3].map(() => h('div.episode.is-skeleton', sk(40, 18), sk(220, 23, 'margin-top:14px'), sk(90, 18, 'margin-top:14px')))),
    ),
  'Where to watch': () =>
    h('div.pv-row', [0, 1, 2].map(() => h('div.service.is-skeleton', sk(180, 28), sk(270, 22, 'margin-top:16px'), sk(70, 18, 'margin-top:12px')))),
  'Cast & crew': () =>
    h(
      'div.pv-row',
      [0, 1, 2, 3, 4, 5].map(() => h('div.person', h('div.person-portrait.is-skeleton'), sk(120, 20, 'margin-top:14px'), sk(90, 18, 'margin-top:8px'))),
    ),
  'More like this': () => h('div.pv-row', [0, 1, 2, 3, 4].map(() => smallCardSkeleton())),
};

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
  const next = t.next ? `S${t.next.season} E${t.next.episode}` : '';
  /** Tabs whose failed load was retried: they load from then on. */
  const retried = new Set<string>();
  /** The tab loading again after Retry, if any. */
  let reloading: string | null = null;

  const openPaid = (service: ServiceOffers['service']) => {
    const s = offersByService(t).find((x) => x.service === service);
    if (s) app.push(rentDialog(t, s));
  };

  // The card is rebuilt whenever the (simulated) data state changes; focus comes back by key.
  function build() {
    const loading = net.get() === 'preview-loading';

    // ---- actions ----
    const watchLabel = !offer
      ? 'Not on your services'
      : isIncludedOffer(offer)
        ? isShow
          ? `Watch ${next} on ${svcName}`
          : `Watch on ${svcName}`
        : paidAction(offer);
    // The Watch button is known from the card that opened the preview, so it works while the rest loads.
    const watch = button(watchLabel, {
      icon: 'play',
      primary: true,
      group: 'actions',
      key: 'watch',
      isDefault: true,
      onOk: () => {
        if (!offer) return app.toast('Not available on your services or stores');
        if (isIncludedOffer(offer)) return app.toast(`Opening ${isShow ? `${next} of ` : ''}${t.name} in ${svcName}…`);
        openPaid(offer.service);
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

    const actions = loading
      ? h('div.pv-actions', watch, sk(66, 66, 'border-radius:50%'), sk(66, 66, 'border-radius:50%'), sk(150, 66, 'border-radius:33px'))
      : h(
          'div.pv-actions',
          watch,
          toggle('mylist', 'plus', 'check', 'My list', 'On My list', store.inMyList, store.toggleMyList, (on) => (on ? 'Added to My list' : 'Removed from My list')),
          toggle('seen', 'eye', 'eye', 'Mark seen', 'Seen', store.isSeen, store.toggleSeen, (on) => (on ? 'Marked as seen' : 'Marked as not seen')),
          toggle('fav', 'heart', 'heart', 'Favorite', 'Favorite', store.isFavorite, store.toggleFavorite, (on) => (on ? 'Added to favorites' : 'Removed from favorites')),
          button('Trailer', {
            icon: 'trailer',
            group: 'actions',
            key: 'trailer',
            onOk: () => app.toast(net.isOffline() ? 'Offline: the trailer needs the internet' : `Opening the ${t.name} trailer on YouTube…`),
          }),
        );

    // ---- tab content ----
    const content = h('div.pv-content');

    const overview = () => {
      const rents = offersOf(t).filter((o) => o.type === 'rent').map(cheapest).filter((p): p is number => p !== undefined);
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
            rents.length && included ? h('span.muted', `· Rent from ${kr(Math.min(...rents))} elsewhere`) : null,
          ),
          t.cast.length ? h('div.pv-fact', h('span.muted', 'Starring'), t.cast.slice(0, 3).map((c) => c.name).join(', ')) : null,
          lead ? h('div.pv-fact', h('span.muted', lead.role), t.crew.filter((c) => c.role === lead.role).map((c) => c.name).join(', ')) : null,
        ),
      );
    };

    // Where to watch (07-prices A): one card per service, included ones first, then the stores, cheapest first and
    // tagged; a line per picture quality, a 4K line only when it costs more. A store no source priced reads
    // "Rent · Buy". The sources are credited under the row.
    const serviceCard = (s: ServiceOffers, isCheapest: boolean, isDefault: boolean) => {
      const name = SERVICES[s.service].name;
      let body: (HTMLElement | null)[];
      if (watchable(s)) {
        body = [h('div.service-offer', s.included ? 'With your subscription' : 'Free with ads'), h('div.service-detail', s.quality)];
      } else {
        const lines = priceLines(s);
        const seasonal = !!(s.rent?.seasonal || s.buy?.seasonal);
        // The cheapest store's deciding price, rent before buy, is the green one.
        const green = isCheapest ? (s.rent && cheapest(s.rent)) ?? (s.buy && cheapest(s.buy)) : undefined;
        const greenType = isCheapest ? (s.rent && cheapest(s.rent) !== undefined ? 'rent' : 'buy') : null;
        let greened = false;
        const amount = (type: 'rent' | 'buy', n: number) => {
          const isGreen = !greened && type === greenType && n === green;
          if (isGreen) greened = true;
          return h(`b${isGreen ? '.cheap' : ''}`, kr(n));
        };
        body = lines.length
          ? [
              h(
                'div.price-lines',
                lines.map((l) =>
                  h(
                    'div.pl',
                    h(
                      'span',
                      l.rent !== undefined ? ['Rent ', amount('rent', l.rent)] : null,
                      l.rent !== undefined && l.buy !== undefined ? ' · ' : null,
                      l.buy !== undefined ? ['Buy ', amount('buy', l.buy)] : null,
                    ),
                    h('span.q', l.quality),
                  ),
                ),
              ),
              // A show's prices are for one season.
              seasonal ? h('div.service-detail', 'Per season') : null,
            ]
          : [
              h('div.price-lines', h('div.pl', h('span', [s.rent && 'Rent', s.buy && 'Buy'].filter(Boolean).join(' · ')))),
              h('div.service-detail', 'Price shown in the app'),
            ];
      }
      return focusable(
        h(
          `div.service${watchable(s) ? '.service--included' : ''}`,
          h(
            'div.service-head',
            h('span.service-name', name),
            watchable(s) ? h('span.badge', s.free ? 'Free' : 'Included') : isCheapest ? h('span.tag-best', 'Cheapest') : null,
          ),
          body,
        ),
        {
          group: 'wtw',
          key: `svc-${s.service}`,
          isDefault,
          onOk: () => (watchable(s) ? app.toast(`Opening ${t.name} in ${name}…`) : openPaid(s.service)),
        },
      );
    };

    const whereToWatch = () => {
      const list = offersByService(t);
      const best = cheapestStore(list);
      return h(
        'div.pv-where',
        list.length
          ? h('div.pv-row', { 'data-scroll': 'x', 'data-scroll-pad': 0 }, list.map((s, i) => serviceCard(s, s.service === best, i === 0)))
          : h('div.pv-empty', 'Not available to stream, rent or buy here.'),
        h('div.pv-credit', `${attribution(t)}${net.isOffline() ? ` · Saved ${SAVED_AT}, prices may be out of date` : ''}`),
      );
    };

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

    const tabEls = tabs.map((name) =>
      focusable(h('div.tab', name), {
        group: 'tabs',
        key: `tab-${name}`,
        isDefault: name === active,
        onFocus: () => setTab(name),
      }),
    );

    // An error says itself where the data would be, with Retry (06-states A). The rest of the card is the saved copy.
    const tabError = (name: string) =>
      h(
        'div.inline-err',
        h('span.inline-err-icon', { html: icon('alert', 40) }),
        h(
          'div.inline-err-text',
          h('div.inline-err-title', `Couldn’t load ${name.toLowerCase()}`),
          h('div.inline-err-note', 'TMDB didn’t answer. The rest of the page is from the saved copy.'),
        ),
        button('Retry', {
          icon: 'retry',
          key: `retry-${name}`,
          onOk: () => {
            retried.add(name);
            reloading = name;
            // Focus waits on the tab, something real, while the tab loads again.
            app.focus(tabEls[tabs.indexOf(name)]);
            replace(content, SKELETONS[name]());
            window.setTimeout(() => {
              reloading = null;
              if (active === name && content.isConnected) replace(content, renderers[name]());
            }, 1200);
          },
        }),
      );

    const renderTab = (name: string) => {
      if (loading || reloading === name) return SKELETONS[name]();
      if (net.failingTab() === name && !retried.has(name)) return tabError(name);
      return renderers[name]();
    };

    function setTab(name: string) {
      if (name === active && content.childElementCount) return;
      active = name;
      tabEls.forEach((el) => el.classList.toggle('is-active', el.textContent === name));
      replace(content, renderTab(name));
    }
    setTab(active);

    const details = loading
      ? h('div.pv-details', sk(60, 22), sk(140, 22), sk(80, 22))
      : h(
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

    return [
      // While the art loads, a plain gradient holds its place.
      loading ? h('div.pv-art.pv-art--loading') : h('img.pv-art', { src: imgUrl(t.art), alt: '' }),
      h('div.pv-fade'),
      h('div.pv-back', backBtn),
      h(
        'div.pv-body',
        h('div.pv-head', h('div.pv-titleblock', h('h1.pv-title', t.name), details), actions),
        h('div.pv-tabs', tabEls),
        h('div.pv-divider'),
        content,
      ),
    ];
  }

  const popup = h('div.popup');
  const el = h('div.screen.layer-popup', h('div.scrim'), popup);
  replace(popup, build());
  const screen: Screen = { label: t.name, el, layer: 'popup', defaultFocus: () => popup.querySelector<HTMLElement>('[data-key="watch"]') };
  const stop = net.on(() => {
    if (!app.has(screen)) return void stop();
    // A new simulated state starts over: a failing tab fails again until Retry.
    retried.clear();
    // Back online after an error or offline: the tab that failed loads again by itself, its skeleton first, with no
    // Retry needed. (On the TV: the network callback retries what failed.)
    const was = net.previous();
    const failed = net.failingTab(was);
    if (net.get() === 'online' && (failed || was === 'offline' || was === 'offline-empty')) {
      reloading = failed ?? active;
      window.setTimeout(() => {
        if (reloading !== (failed ?? active)) return;
        reloading = null;
        if (!app.has(screen)) return;
        replace(popup, build());
        app.refocus();
      }, 1200);
    }
    replace(popup, build());
    app.refocus();
  });
  return screen;
}
