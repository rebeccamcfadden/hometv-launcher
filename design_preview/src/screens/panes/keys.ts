import { app } from '../../core/app';
import { h } from '../../core/dom';
import { focusable } from '../../core/focus';
import { KEY_INFO, keyStatus, keys, type KeyId } from '../../core/keys';
import { featureOn } from '../../core/settings';
import { icon } from '../../ui/icons';
import { qrSvg } from '../../ui/qr';
import { button } from '../../ui/widgets';
import { confirmDialog } from '../confirm';
import { turnOn } from './features';
import type { Pane, PaneHost } from './pane';

/** Which keys a pane shows: all three (Launcher settings › API keys), or First run's TMDB key or price keys tab. */
type KeysMode = 'all' | 'tmdb' | 'prices';

const PRICE_KEYS: KeyId[] = ['watchmode', 'motn'];

const TITLES: Record<KeysMode, [string, string, string]> = {
  all: ['API keys', 'Keys for titles and prices', 'Each household makes its own free keys, so no one’s monthly limit is shared.'],
  tmdb: ['TMDB key', 'A key for titles', 'Movies, TV Shows and Search come from TMDB, which needs a free key from this household.'],
  prices: [
    'Price keys',
    'Keys for rent and buy prices',
    'Prices come from Watchmode, and from Movie of the Night when Watchmode has none. Both are free; Movie of the Night is optional.',
  ],
};

const nameOf = (id: KeyId) => KEY_INFO.find((k) => k.id === id)!.name;

/**
 * API keys (F16; no Figma design yet, drafted from the Phone notes pane's parts): the household's own TMDB, Watchmode
 * and Movie of the Night keys, entered on a phone (the QR) or a laptop (the address and code), never on the TV's
 * keyboard. Each is checked with one free request when it arrives, and shown again only by its last four characters.
 * The TV listens for keys only while this pane is open. A key in the build (tv.properties) works until one is entered
 * or it is removed; a removed key leaves none (the project lead, 2026-11-02).
 *
 * With Price lookup off, Launcher settings' pane keeps the TMDB key (the catalogue needs it) and puts the price keys
 * behind an empty state with Turn on; First run has no price keys tab then, so its pane never shows it.
 */
function pane(host: PaneHost, mode: KeysMode): Pane {
  /** The keys whose rows show now: the price keys only while Price lookup is on. */
  const ids = (): KeyId[] =>
    mode === 'tmdb' ? ['tmdb'] : mode === 'prices' ? PRICE_KEYS : featureOn('prices') ? ['tmdb', ...PRICE_KEYS] : ['tmdb'];
  const pricesOff = () => mode === 'all' && !featureOn('prices');

  const row = (id: KeyId, first: boolean) => {
    const info = KEY_INFO.find((k) => k.id === id)!;
    const status = keyStatus(id);
    const state = keys.get(id).state;
    const mark =
      status.tone === 'good' ? icon('check', 24) : status.tone === 'bad' || status.tone === 'warn' ? icon('alert', 24) : '';
    const el = focusable(
      h(
        `div.apikey-row.apikey-row--${status.tone}`,
        h(
          'div.apikey-text',
          h('div.apikey-name', info.name, h('span.apikey-use', ` · ${info.use}`)),
          h('div.apikey-status', h('span.apikey-mark', { html: mark }), status.text),
        ),
        h('div.apikey-plan', info.plan),
      ),
      {
        group: 'keys',
        key: `key-${id}`,
        isDefault: first,
        onOk: () => {
          if (state === 'bad' || state === 'unreachable' || state === 'ok') keys.recheck(id);
          else if (state === 'built') app.toast(`This ${info.name} key is from the TV’s build; one entered on the phone or laptop page replaces it`);
          else if (state === 'unset') app.toast(`Enter the ${info.name} key on the phone or laptop page: scan the code, or open the address`);
        },
      },
    );
    if (first) el.dataset.entry = '';
    return el;
  };

  /** Price lookup off (Launcher settings only): where the price keys would be, what's off and how to turn it on. */
  const pricesOffBlock = () =>
    h(
      'div.keys-off',
      h('div.apikey-name', 'Price keys · Watchmode and Movie of the Night'),
      h('div.pane-sub', 'Price lookup is off, so the TV asks for no prices and needs no price keys. Turn it on to add them here.'),
      h(
        'div.pane-actions',
        button('Turn on Price lookup', { icon: 'power', key: 'keys-prices-on', onOk: () => turnOn('prices') }),
      ),
    );

  const removable = () => ids().filter((id) => ['ok', 'bad', 'unreachable', 'built'].includes(keys.get(id).state));
  /** The pane's stops, top to bottom: each key row, Turn on Price lookup (when off), then the buttons. */
  const buttonKeys = () => [...(removable().length ? ['keys-remove'] : []), 'keys-code'];

  return {
    /**
     * ↑/↓ step through the rows, Turn on Price lookup and the buttons' row; →/← stay within a row (the QR beside the
     * rows isn't a stop), so a long → never jumps to another row.
     */
    onKey(key, focused) {
      const k = focused.dataset.key ?? '';
      const list = ids();
      const rowAt = list.findIndex((id) => k === `key-${id}`);
      const below = () => (pricesOff() ? 'keys-prices-on' : buttonKeys()[0]);
      if (rowAt >= 0) {
        if (key === 'right') return true;
        if (key === 'down') host.focusKey(rowAt < list.length - 1 ? `key-${list[rowAt + 1]}` : below());
        else if (key === 'up') {
          if (rowAt > 0) host.focusKey(`key-${list[rowAt - 1]}`);
        } else return false;
        return true;
      }
      if (k === 'keys-prices-on') {
        if (key === 'down') host.focusKey(buttonKeys()[0]);
        else if (key === 'up') host.focusKey(`key-${list[list.length - 1]}`);
        else if (key !== 'right') return false;
        return true;
      }
      const at = buttonKeys().indexOf(k);
      if (at >= 0) {
        if (key === 'up') host.focusKey(pricesOff() ? 'keys-prices-on' : `key-${list[list.length - 1]}`);
        else if (key === 'right') {
          if (at < buttonKeys().length - 1) host.focusKey(buttonKeys()[at + 1]);
        } else if (key === 'left' && at > 0) host.focusKey(buttonKeys()[at - 1]);
        // ↓ in First run goes on to the panel's footer (Next, Back to Features); Launcher settings has none.
        else if (key === 'down') return !host.setup;
        else return false;
        return true;
      }
      return false;
    },
    render() {
      const [eyebrow, title, sub] = TITLES[mode];
      const toRemove = removable();
      return [
        h('div.eyebrow', eyebrow),
        h('div.pane-title', title),
        h('div.pane-sub', sub),
        h(
          'div.keys-setup',
          h('div.keys-rows', ids().map((id, i) => row(id, i === 0)), pricesOff() ? pricesOffBlock() : null),
          h(
            'div.keys-qr',
            h('div.notes-qr-img', { html: qrSvg(200) }),
            h('div.notes-qr-cap', 'Scan with a phone’s camera'),
            h(
              'div.keys-laptop',
              h('span', 'Or on a laptop, open'),
              h('b.notes-mono', keys.address),
              h('span.keys-code-line', 'and type the code ', h('b.notes-code', keys.code())),
            ),
          ),
        ),
        h(
          'div.pane-note',
          'The page that opens shows how to get each key, step by step, and sends it here to be checked. The TV listens only while this page is open, on your home network, with the code. Keys stay on the TV, shown only by their last four characters.',
        ),
        h(
          'div.pane-actions',
          toRemove.length
            ? button(toRemove.length === 1 ? `Remove the ${nameOf(toRemove[0])} key` : 'Remove a key', {
                key: 'keys-remove',
                onOk: () =>
                  app.push(
                    confirmDialog({
                      title: toRemove.length === 1 ? `Remove the ${nameOf(toRemove[0])} key?` : 'Remove which key?',
                      body: 'This TV forgets it, the build’s own key too, and has none for that service until one is entered here.',
                      actions: toRemove.map((id, i) => ({ label: `Remove ${nameOf(id)}`, primary: i === 0, onOk: () => keys.remove(id) })),
                    }),
                  ),
              })
            : null,
          button('New code', {
            icon: 'retry',
            key: 'keys-code',
            onOk: () =>
              app.push(
                confirmDialog({
                  title: 'Make a new code?',
                  body: 'A page opened with the old code can’t send keys any more. Scan the new QR, or type the new code.',
                  actions: [{ label: 'New code', primary: true, onOk: keys.newCode }],
                }),
              ),
          }),
        ),
      ];
    },
  };
}

/** Launcher settings › API keys: the TMDB key, and the price keys while Price lookup is on. */
export const keysPane = (host: PaneHost): Pane => pane(host, 'all');
/** First run's Price keys tab under Price lookup (Builder A's hook, F28). */
export const priceKeysPane = (host: PaneHost): Pane => pane(host, 'prices');
/** First run's TMDB key tab under TMDB account, before Sign in, on a TV without a working TMDB key. */
export const tmdbKeyPane = (host: PaneHost): Pane => pane(host, 'tmdb');
