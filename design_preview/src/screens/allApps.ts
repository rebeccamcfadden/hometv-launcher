import { app, type Screen } from '../core/app';
import { h, replace } from '../core/dom';
import { focusable } from '../core/focus';
import { settings } from '../core/settings';
import { isTvUtility, type Moved } from '../core/tvUtilities';
import { TV_APPS, type TvApp } from '../data/apps';
import { HOME_ROW_MAX } from '../data/services';
import { button } from '../ui/widgets';
import { dropdown, type MenuRow } from './dropdown';

// All Apps (pushed from Home's Shortcuts) with the TV Utilities folder (F35 b, Rebecca 2026-11-02: on for every TV, no
// switch). Every installed app sorted by name, after the folder tile, which holds the apps the TV came with that aren't
// watch apps (core/tvUtilities). OK opens an app or the folder; hold OK adds an app to Home or takes it off, moves it
// into or out of the folder, or opens App info. The folder is its own page with the same grid; Back returns to its tile.
// On the TV: AllAppsPage and TvUtilities.

const byName = (a: TvApp, b: TvApp) => a.name.localeCompare(b.name);

function inFolder(a: TvApp): boolean {
  const s = settings.get();
  return isTvUtility({ pkg: a.pkg, system: a.system, onHome: s.homeApps.includes(a.id), moved: s.utilityMoves[a.id] });
}

const utilities = () => TV_APPS.filter(inFolder).sort(byName);
const plain = () => TV_APPS.filter((a) => !inFolder(a)).sort(byName);
const count = (n: number) => `${n} app${n === 1 ? '' : 's'}`;

/** All Apps, or with `folder` the TV Utilities page. */
export function allAppsScreen(folder = false): Screen {
  const back = button('Back', { icon: 'back', small: true, key: 'back', onOk: () => app.back() });
  const title = folder ? 'TV Utilities' : 'All Apps';
  const countEl = h('span.aa-count');
  const grid = h('div.aa-grid');
  const el = h(
    'div.screen.screen--base.aa-page',
    back,
    h('div.aa-head', h('h1.page-title', title), countEl),
    folder ? h('div.aa-note', 'Apps that came with the TV. Hold OK on one to move it back to All Apps.') : null,
    h('div.aa-scroll', { 'data-scroll': 'y', 'data-scroll-pad': 40 }, grid),
  );

  const appTile = (a: TvApp, i: number) =>
    focusable(h('div.aa-app', { 'data-app': a.id }, h('div.aa-face', a.mark), h('div.aa-name', a.name)), {
      group: 'aa',
      key: `aa-${a.id}`,
      isDefault: i === 0,
      onOk: () => app.toast(`Opening ${a.name}…`),
    });

  const folderTile = (inside: TvApp[]) =>
    focusable(
      h(
        'div.aa-app.aa-folder',
        h('div.aa-face', inside.slice(0, 4).map((a) => h('span.aa-mini', a.name))),
        h('div.aa-name', 'TV Utilities ', h('span.aa-folder-count', `· ${inside.length}`)),
      ),
      { group: 'aa', key: 'aa-folder', isDefault: true, onOk: () => app.push(allAppsScreen(true)) },
    );

  let shown: TvApp[] = [];
  const paint = () => {
    const inside = utilities();
    shown = folder ? inside : plain();
    countEl.textContent = folder ? count(inside.length) : count(TV_APPS.length);
    const tiles = shown.map((a, i) => appTile(a, folder || inside.length === 0 ? i : i + 1));
    replace(grid, !folder && inside.length ? [folderTile(inside), ...tiles] : tiles);
  };
  paint();

  /** After an app left this page: focus the one that took its place, or `key`. */
  const settle = (gone: string, key?: string) => {
    const at = shown.findIndex((a) => a.id === gone);
    paint();
    if (folder && !shown.length) return app.pop();
    const target = key ?? (shown.length ? `aa-${shown[Math.min(Math.max(at, 0), shown.length - 1)].id}` : 'back');
    app.focus(el.querySelector<HTMLElement>(`[data-key="${target}"]`) ?? back);
  };

  const move = (a: TvApp, to: Moved) => {
    // Only a move against the rule is kept, so an app moved back where the rule puts it follows the rule again.
    const s = settings.get();
    const moves = { ...s.utilityMoves };
    delete moves[a.id];
    if (isTvUtility({ pkg: a.pkg, system: a.system, onHome: s.homeApps.includes(a.id) }) !== (to === 'in')) moves[a.id] = to;
    settings.set({ utilityMoves: moves });
    app.toast(to === 'in' ? `Moved ${a.name} to TV Utilities` : `Moved ${a.name} out of TV Utilities`);
    settle(a.id, to === 'in' ? 'aa-folder' : undefined);
  };

  const menu = (tile: HTMLElement): boolean => {
    const a = TV_APPS.find((x) => x.id === tile.dataset.app);
    if (!a) return false;
    const row = settings.get().homeApps;
    const onHome = row.includes(a.id);
    const rows: MenuRow[] = [
      {
        label: onHome ? 'Remove from Home' : 'Add to Home',
        icon: onHome ? 'home' : 'plus',
        onOk: () => {
          if (!onHome && row.length >= HOME_ROW_MAX) return app.toast(`Home’s row holds ${HOME_ROW_MAX} apps. Take one off first.`);
          const wasIn = inFolder(a);
          settings.set({ homeApps: onHome ? row.filter((x) => x !== a.id) : [...row, a.id] });
          // An app on Home's row is a favourite, so it leaves the folder unless it was moved in by hand.
          const left = wasIn !== inFolder(a);
          const where = !left ? '' : folder ? ' · now in All Apps' : ' · now in TV Utilities';
          app.toast((onHome ? 'Removed from Home' : 'Added to Home') + where);
          if (left) settle(a.id, folder ? undefined : 'aa-folder');
        },
      },
      folder
        ? { label: 'Move out of TV Utilities', icon: 'apps', onOk: () => move(a, 'out') }
        : { label: 'Move to TV Utilities', icon: 'apps', onOk: () => move(a, 'in') },
      { label: 'App info', icon: 'info', onOk: () => app.toast(`Opening App info for ${a.name}…`) },
    ];
    app.push(dropdown('App menu', tile, rows, { onCard: true }));
    return true;
  };

  return {
    label: title,
    el,
    defaultFocus: () => grid.querySelector<HTMLElement>('[data-focus]') ?? back,
    onShow: paint,
    onLongOk: (f) => !!f && menu(f),
  };
}
