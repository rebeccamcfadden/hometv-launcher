import { app } from '../../core/app';
import { h } from '../../core/dom';
import { account, IMPORT_STEPS, USERNAME, type ListRole } from '../../core/account';
import { store } from '../../core/store';
import { icon } from '../../ui/icons';
import { qrSvg } from '../../ui/qr';
import { button, segmented } from '../../ui/widgets';
import { confirmDialog } from '../confirm';
import type { Pane, PaneHost } from './pane';

const STEPS = ['Scan the code with your phone’s camera', 'Sign in to TMDB and press Approve', 'This TV carries on by itself'];

/** The three steps beside the QR; `done` of them ticked, the next one current. */
const steps = (done: number) =>
  h(
    'div.steps',
    STEPS.map((text, i) =>
      h(`div.st${i < done ? '.is-done' : i === done ? '.is-now' : ''}`, h('div.n', i < done ? { html: icon('check', 20) } : String(i + 1)), h('div', text)),
    ),
  );

const benefit = (ic: Parameters<typeof icon>[0], title: string, sub: string) =>
  h('div.ben-row', h('div.ben-ico', { html: icon(ic, 26) }), h('div', h('div.ben-t', title), h('div.ben-s', sub)));

const entry = (el: HTMLElement) => ((el.dataset.entry = ''), el);

/**
 * Account (V2 design r2-account A, with B's Choose lists): the whole TMDB sign-in in the pane. The QR sits beside
 * three steps and a countdown; ← leaves the pane and the code keeps waiting, the section reading "Waiting…". Signed
 * in: the username, list counts, Sync now, Choose lists (a sub-state) and Sign out.
 */
export function accountPane(host: PaneHost): Pane {
  let choosing = false;

  const qrSide = (content: Node) => h('div.acc-split', content);

  const signedOut = () => [
    h('div.eyebrow', 'Account'),
    h('div.pane-title', 'Sign in to TMDB'),
    h('div.pane-sub', 'TMDB is a free film and TV database. Signing in brings the household’s lists to this TV.'),
    h(
      'div.ben',
      benefit('sync', 'My list, Seen and Favorites, in step', 'Add a title on your phone and it shows here too'),
      benefit('list', 'Household and Friends lists', 'Pick which of your TMDB lists they come from'),
      benefit('lock', 'Your password stays with TMDB', 'This TV only gets a sign-in key; sign out any time'),
    ),
    h('div.pane-actions', entry(button('Sign in with your phone', { icon: 'phone', primary: true, key: 'acc-signin', onOk: account.signIn }))),
    h('div.pane-note', 'Not signed in, My list and Seen are kept on this TV only.'),
  ];

  const cancel = (primary = false) => button('Cancel', { key: 'acc-cancel', primary, onOk: account.cancel });
  const newCode = (primary = false) => button('New code', { icon: 'retry', key: 'acc-new', primary, onOk: account.signIn });

  const side = (title: string, ...rest: (Node | Node[] | null)[]) =>
    h('div.acc-side', h('div.eyebrow', 'Account'), h('div.pane-title.pane-title--qr', title), ...rest.flat().filter((x): x is Node => !!x));

  const getting = () =>
    qrSide(
      h(
        'div.acc-row',
        h('div.qr-wrap', h('div.qr-hold', h('div.qr-spin'), 'Getting a code from TMDB…')),
        side('Sign in to TMDB', h('div.acc-steps', steps(0)), h('div.pane-actions', entry(cancel()))),
      ),
    );

  const waiting = () =>
    qrSide(
      h(
        'div.acc-row',
        h('div.qr-wrap', { html: qrSvg(486) }),
        side(
          'Sign in to TMDB',
          h('div.acc-steps', steps(0)),
          h(
            'div.acc-count',
            h('div.count', h('b', { 'data-countdown': '' }, account.countdown()), h('span', 'left on this code')),
            h('div.bar', h('i', { 'data-bar': '', style: `width:${Math.round(account.fraction() * 100)}%` })),
          ),
          h('div.pane-actions', entry(cancel(true)), newCode()),
          h('div.pane-note.pane-note--tiny', 'The link is too long to type; use the camera. ← goes to the sections and keeps this code waiting.'),
        ),
      ),
    );

  const approved = () =>
    qrSide(
      h(
        'div.acc-row',
        h('div.qr-wrap', h('div.qr-hold.qr-hold--good', h('div.bigico.bigico--good', { html: icon('check', 48) }), 'Approved on your phone')),
        side(
          'Signing in…',
          h('div.acc-steps', steps(2)),
          h(
            'div.acc-count',
            h('div.acc-import', h('span', 'Importing your lists…'), h('span', `${account.imported()} of ${IMPORT_STEPS}`)),
            h('div.bar.good', h('i', { style: `width:${Math.round((account.imported() / IMPORT_STEPS) * 100)}%` })),
            h('div.pane-note.pane-note--tiny', account.imported() < 3 ? 'My list, Seen and Favorites first; Household and Friends next' : 'My list, Seen and Favorites done; Household and Friends next'),
          ),
        ),
      ),
    );

  const expired = () =>
    qrSide(
      h(
        'div.acc-row',
        h('div.qr-wrap', h('div.qr-dim', { html: qrSvg(486) }), h('div.qr-over', h('div.bigico', { html: icon('clock', 48) }), 'Code expired')),
        side(
          'That code has expired',
          h('div.pane-sub', 'Codes last 15 minutes. Nothing changed on this TV; get a new code and scan again.'),
          h('div.pane-actions', entry(newCode(true)), cancel()),
        ),
      ),
    );

  const refused = () =>
    qrSide(
      h(
        'div.acc-row',
        h('div.qr-wrap', h('div.qr-hold.qr-hold--warn', h('div.bigico.bigico--warn', { html: icon('wifiOff', 48) }), 'No code')),
        side(
          'Couldn’t reach TMDB',
          h('div.pane-sub', 'This TV is offline, or TMDB said no (for example after Deny on the phone).'),
          h('div.pane-note.pane-note--tiny', 'TMDB · no connection'),
          h(
            'div.pane-actions',
            entry(button('Try again', { icon: 'retry', primary: true, key: 'acc-try', onOk: account.signIn })),
            button('Wi-Fi settings', { icon: 'wifi', key: 'acc-wifi', onOk: () => app.toast('Opening Wi-Fi settings…') }),
          ),
        ),
      ),
    );

  const signedIn = () => {
    const hh = account.roleOf('household');
    const fr = account.roleOf('friends');
    return [
      h('div.eyebrow', 'Account'),
      h('div.acc-user', h('div.avatar', USERNAME[0].toUpperCase()), h('div', h('div.pane-title', USERNAME), h('div.pane-good', h('span', { html: icon('check', 22) }), 'Signed in to TMDB'))),
      h(
        'div.kvs',
        h('div.kv', h('span', 'My list · Seen · Favorites'), h('b', `${store.myListIds().length} · 118 · 12`)),
        h('div.kv', h('span', 'Household'), h('b', hh ? `${hh.name} · ${hh.count} titles` : 'Not used')),
        h('div.kv', h('span', 'Friends'), h('b', fr ? `${fr.name} · ${fr.count} titles` : 'Not used')),
        h('div.kv', h('span', 'Last synced'), h('b', account.lastSynced())),
      ),
      h(
        'div.pane-actions',
        entry(
          button('Sync now', {
            icon: 'sync',
            key: 'acc-sync',
            onOk: () => app.toast(account.sync() ? 'Synced with TMDB' : 'Offline: syncs when the TV is back online'),
          }),
        ),
        button('Choose lists', {
          icon: 'list',
          key: 'acc-lists',
          onOk: () => {
            choosing = true;
            host.rerender();
            host.focusKey('');
          },
        }),
        button('Sign out', {
          key: 'acc-signout',
          onOk: () =>
            app.push(
              confirmDialog({
                title: 'Sign out of TMDB?',
                body: 'My list, Seen and Favorites stay on this TV. Household and Friends are hidden until you sign in again.',
                actions: [{ label: 'Sign out', primary: true, onOk: account.signOut }],
              }),
            ),
        }),
      ),
    ];
  };

  const ROLES: [ListRole, string][] = [
    ['household', 'Household'],
    ['friends', 'Friends'],
    ['none', 'Not used'],
  ];

  // Choose lists (r2-account B): one row per TMDB list with a Household / Friends / Not used switch. Giving a list a
  // role takes it from the list that had it.
  const chooseLists = () => [
    h('div.crumb', h('span', { html: icon('back', 20) }), 'Account'),
    h('div.pane-title', 'Choose lists'),
    h('div.pane-sub', 'All your TMDB lists. Mark one as Household and one as Friends; ←/→ moves along a row, OK picks.'),
    h(
      'div.lists',
      account.lists().map((l, i) =>
        h(
          'div.lists-row',
          h('div.lists-name', h('div.nm', l.name), h('div.ct', `${l.count} titles · updated ${l.updated}`)),
          segmented<ListRole>({
            options: ROLES,
            get: () => l.role,
            set: (role) => account.setRole(l.id, role),
            group: `list-${l.id}`,
            small: true,
            entry: i === 0,
          }),
        ),
      ),
    ),
    h('div.pane-note', 'My list, Seen and Favorites always come from the account itself. Back returns.'),
  ];

  return {
    render() {
      const s = account.get();
      if (s !== 'in') choosing = false;
      if (choosing) return chooseLists();
      switch (s) {
        case 'getting':
          return getting();
        case 'waiting':
          return waiting();
        case 'approved':
          return approved();
        case 'in':
          return signedIn();
        case 'expired':
          return expired();
        case 'refused':
          return refused();
        default:
          return signedOut();
      }
    },
    back() {
      if (!choosing) return false;
      choosing = false;
      host.rerender();
      host.focusKey('acc-lists');
      return true;
    },
  };
}
