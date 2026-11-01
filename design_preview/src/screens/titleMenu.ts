import { app, type Screen } from '../core/app';
import { lists, type ShownList } from '../core/lists';
import { net } from '../core/net';
import { store } from '../core/store';
import { getTitle } from '../data/catalog';
import type { Title } from '../types';
import { provenance } from '../ui/widgets';
import { dropdown, type MenuRow } from './dropdown';
import { titlePreview } from './preview';

// The hold-OK menu on a title card (F3, the project lead, 2026-11-01), wherever one shows: Movies, TV Shows, Pick for
// me's results, Search, the person page and the title preview's More like this. Mark seen, Favorite, the lists and
// More like this, worded for the title's state, in the dropdowns' glass panel hung from the card. Signed out, the
// lists row puts the title on My list itself (the TMDB lists need the account); signed in, it opens a second menu with
// My list and the shown TMDB lists, a tick on each that holds the title, where OK puts it on or takes it off and the
// menu stays. On the TV: TitleMenu and TitleMenuRules (unit-tested).

/** How long the mocked TMDB takes to answer a list change. */
const TMDB_MS = 600;

/** The lists the menu can change: My list, then, signed in, the shown TMDB lists. */
const menuLists = (): ShownList[] => [lists.my, ...lists.editable()];

/** "On My list", "On Family picks", "On 2 lists". */
const listsNote = (on: ShownList[]) =>
  on.length === 0 ? undefined : on.length === 1 ? `On ${on[0].name}` : `On ${on.length} lists`;

/** The card for t now: the page may have drawn it again when the menu closed. */
function cardNow(card: HTMLElement, t: Title): HTMLElement {
  return app.focused?.dataset.title === t.id ? app.focused : card;
}

/** Brings a card's list line ("My list · Sci-fi") up to date in place; Search's cards have none. */
function refreshCard(card: HTMLElement, t: Title) {
  const line = cardNow(card, t).querySelector('[data-lists]');
  if (line) line.textContent = provenance(t);
}

/** Opens the menu when `card` is a title card; returns whether it did. */
export function titleMenu(card: HTMLElement): boolean {
  const id = card.dataset.title;
  if (!id) return false;
  const t = getTitle(id);
  const page = app.top();
  // As on the TV, a change is made first and the menu closes after it, so the page drawn again on closing shows it.
  const done = (text: string) => {
    menu.close();
    refreshCard(card, t);
    app.toast(text);
  };
  const seen = store.isSeen(t.id);
  const fav = store.isFavorite(t.id);
  const all = menuLists();
  const rows: MenuRow[] = [
    {
      label: seen ? 'Mark unseen' : 'Mark seen',
      icon: 'eye',
      stay: true,
      onOk: () => done(store.toggleSeen(t.id) ? 'Marked as seen' : 'Marked as not seen'),
    },
    {
      label: fav ? 'Unfavorite' : 'Favorite',
      icon: 'heart',
      stay: true,
      onOk: () => done(store.toggleFavorite(t.id) ? 'Added to favorites' : 'Removed from favorites'),
    },
  ];
  if (all.length === 1) {
    const mine = store.inMyList(t.id);
    rows.push({
      label: mine ? 'Remove from My list' : 'Add to My list',
      icon: mine ? 'check' : 'plus',
      stay: true,
      onOk: () => done(store.toggleMyList(t.id) ? 'Added to My list' : 'Removed from My list'),
    });
  } else {
    const on = all.filter((l) => isOn(l, t));
    rows.push({
      label: on.length ? 'Change lists' : 'Add to a list',
      icon: on.length ? 'check' : 'plus',
      status: listsNote(on),
      onOk: () => app.push(listsMenu(page, cardNow(card, t), t)),
    });
  }
  rows.push({ label: 'More like this', icon: 'grid', onOk: () => app.push(titlePreview(t, { moreLike: true })) });
  const menu = dropdown('Title menu', card, rows, { onCard: true });
  app.push(menu);
  return true;
}

/**
 * List changes sent to the mocked TMDB and not answered yet, by "list:title": what the list held before, which a
 * refusal puts back. The list itself shows the change at once, as the TV's saved copy does (Accounts).
 */
const pending = new Map<string, boolean>();
const pendingKey = (l: ShownList, t: Title) => `${l.id}:${t.id}`;
const isOn = (l: ShownList, t: Title) => l.has(t);

/** The lists menu showing, if any: a late answer re-ticks it, whichever menu sent the change. */
let showing: { screen: Screen; title: Title; card: HTMLElement; lists: ShownList[] } | null = null;

/**
 * Brings what shows now up to date after a list change, from whichever menu sent it: the lists menu for t, if it is
 * on top (its ticks and its card's list line), else the page the menus were opened over, if it is on top again (drawn
 * again, as closing a menu does: a title can leave the list it shows). As on the TV (TitleMenu.reconcile).
 */
function reconcile(page: Screen | undefined, t: Title) {
  const top = app.top();
  if (showing && top === showing.screen) {
    if (showing.title.id !== t.id) return;
    const { screen, lists: ls, card } = showing;
    ls.forEach((l, i) =>
      screen.el.querySelector(`[data-key="m-${i}"] .menu-tick`)?.classList.toggle('is-on', isOn(l, t)),
    );
    refreshCard(card, t);
    return;
  }
  if (page && top === page) {
    page.onShow?.();
    app.refocus();
  }
}

/**
 * The lists menu: My list, then the shown TMDB lists, each ticked when the title is on it. OK puts the title on or
 * takes it off and the menu stays; a TMDB list is ticked at once, and unticked again when TMDB doesn't take it (here,
 * offline in the dev bar's State menu). While a change is out, OK on that list waits for it (a toast says so), so two
 * answers can't cross. `page`: the page under the menus, drawn again when an answer comes.
 */
function listsMenu(page: Screen | undefined, card: HTMLElement, t: Title): Screen {
  const all = menuLists();
  const rows: MenuRow[] = all.map((l) => ({
    label: l.name,
    tick: () => isOn(l, t),
    stay: true,
    onOk: () => {
      if (l.id === 'my') {
        const now = store.toggleMyList(t.id);
        reconcile(page, t);
        app.toast(now ? 'Added to My list' : 'Removed from My list');
        return;
      }
      const k = pendingKey(l, t);
      if (pending.has(k)) {
        app.toast(`Still saving ${l.name} on TMDB…`);
        return;
      }
      const was = isOn(l, t);
      const now = !was;
      pending.set(k, was);
      lists.setOn(l.id, t, now);
      reconcile(page, t);
      window.setTimeout(() => {
        pending.delete(k);
        const took = !net.isOffline();
        if (!took) lists.setOn(l.id, t, was);
        reconcile(page, t);
        app.toast(took ? (now ? `Added to ${l.name}` : `Removed from ${l.name}`) : `Couldn’t change ${l.name} on TMDB. Try again in a moment.`);
      }, TMDB_MS);
    },
  }));
  const screen = dropdown('Lists', card, rows, { onCard: true });
  showing = { screen, title: t, card, lists: all };
  return screen;
}
