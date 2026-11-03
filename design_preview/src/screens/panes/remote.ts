import { app } from '../../core/app';
import { h } from '../../core/dom';
import { REMOTE_URL, remote, type PairedPhone } from '../../core/remote';
import { icon } from '../../ui/icons';
import { qrSvg } from '../../ui/qr';
import { button, segmented } from '../../ui/widgets';
import { confirmDialog } from '../confirm';
import type { Pane, PaneHost } from './pane';

const STEPS = [
  'Scan the code with the phone’s camera',
  'If the phone says the connection isn’t private, tap Advanced (Show Details on iPhone), then continue',
  'Name the phone and tap Pair',
];

const benefit = (ic: Parameters<typeof icon>[0], text: string) => h('div.ben-row', h('div.ben-ico', { html: icon(ic, 26) }), h('div.ben-t.rem-ben', text));

/** "Connected now", "Used today", "Last used yesterday", "Last used 3 days ago". */
function lastUsed(p: PairedPhone): string {
  if (remote.isConnected(p)) return 'Connected now';
  const days = Math.floor((Date.now() - p.lastUsed) / 86_400_000);
  return days <= 0 ? 'Used today' : days === 1 ? 'Last used yesterday' : `Last used ${days} days ago`;
}

/**
 * Phone remote (F27; Figma "Phone remote (F27)", pane option A, the project lead 2026-11-02): off by default. Off, the
 * pane says what the remote does, with the On/Off switch. On, the QR, the three steps, the address and code to type
 * instead, then the paired phones (the connected one in green) with Forget each, New code and Forget all. Forget asks
 * first; Forget all has Cancel focused, since it also replaces the code and, on the TV, the key and certificate.
 */
export function remotePane(host: PaneHost): Pane {
  const onOff = () =>
    segmented<'on' | 'off'>({
      options: [
        ['on', 'On'],
        ['off', 'Off'],
      ],
      get: () => (remote.on() ? 'on' : 'off'),
      set: (v) => {
        remote.setOn(v === 'on');
        host.rerender();
      },
      group: 'remote-on',
      entry: true,
    });

  const forgetOne = (p: PairedPhone) =>
    app.push(
      confirmDialog({
        title: `Forget “${p.name}”?`,
        body: 'It stops working as a remote straight away. To use it again, pair it with the code.',
        actions: [{ label: 'Forget', primary: true, onOk: () => remote.forget(p.id) }],
      }),
    );

  const forgetAll = () =>
    app.push(
      confirmDialog({
        title: 'Forget all phones?',
        body: 'Every phone stops working as a remote. The TV also makes a new code and a new certificate, so phones show the browser’s warning again when they pair.',
        // Cancel first and focused: this can't be undone.
        actions: [{ label: 'Forget all', onOk: remote.forgetAll }],
        cancelFirst: true,
      }),
    );

  const phoneRow = (p: PairedPhone) => {
    const live = remote.isConnected(p);
    return h(
      `div.rem-phone${live ? '.is-live' : ''}`,
      h('div.rem-phone-ico', { html: icon('phone', 26) }),
      h('div.rem-phone-text', h('div.rem-phone-name', p.name), h('div.rem-phone-sub', live ? h('i.rem-dot') : null, lastUsed(p))),
      button('Forget', { small: true, key: `remote-forget-${p.id}`, onOk: () => forgetOne(p) }),
    );
  };

  const off = () => [
    h('div.eyebrow', 'Features'),
    h('div.pane-title', 'Phone remote'),
    h('div.pane-sub', 'Use a phone on the same Wi-Fi as a remote and keyboard for this launcher.'),
    h('div.pane-seg', onOff()),
    h(
      'div.ben.rem-what',
      benefit('tv', 'Arrows, OK, Back, Home and typing on Home, Movies, TV Shows, Search and these settings. Other apps keep using the TV remote.'),
      benefit('plus', 'Add the title on the TV, or any you search for, to My list.'),
      benefit('phone', 'Each phone pairs once with a code shown here, and can be forgotten on its own.'),
      benefit('lock', 'Phones on your Wi-Fi only, over an encrypted link. No account, and nothing leaves your home.'),
    ),
    h('div.pane-note', 'While it is on, Home shows a phone icon by Settings. Off means the TV doesn’t listen at all.'),
  ];

  const on = () => {
    const phones = remote.phones();
    const live = remote.connected();
    return [
      h('div.eyebrow', 'Features'),
      h('div.pane-title', 'Phone remote'),
      h('div.pane-sub', 'Use a phone on the same Wi-Fi as a remote and keyboard for this launcher.'),
      h('div.pane-seg', onOff()),
      h('div.rem-rule'),
      h(
        'div.acc-row.rem-pair',
        h('div.qr-wrap.rem-qr', { html: qrSvg(380) }),
        h(
          'div.acc-side',
          h('div.pane-title.pane-title--small', 'Pair a phone'),
          h(
            'div.steps.rem-steps',
            STEPS.map((text, i) => h(`div.st${i === 0 ? '.is-now' : ''}`, h('div.n', String(i + 1)), h('div', text))),
          ),
          h(
            'div.rem-type',
            h('div.rem-type-cap', 'No camera? Type the address, then the code'),
            h('div.rem-type-url', REMOTE_URL),
            h('div.rem-code', remote.code()),
          ),
        ),
      ),
      h('div.rem-rule'),
      h(
        'div.rem-head',
        h('div.pane-title.pane-title--small', 'Paired phones'),
        live ? h('div.rem-live', h('i.rem-dot'), 'A phone is connected') : null,
      ),
      phones.length ? h('div.rem-phones', phones.map(phoneRow)) : h('div.pane-sub', 'None yet.'),
      h(
        'div.pane-actions',
        button('New code', {
          icon: 'retry',
          key: 'remote-code',
          onOk: () =>
            app.push(
              confirmDialog({
                title: 'Make a new pairing code?',
                body: 'Phones already paired keep working. The old code and QR no longer pair a phone.',
                actions: [{ label: 'New code', primary: true, onOk: remote.newCode }],
              }),
            ),
        }),
        phones.length ? button('Forget all phones', { key: 'remote-forget-all', onOk: forgetAll }) : null,
      ),
      h(
        'div.pane-note.pane-note--tiny',
        'Each phone warns once that the connection isn’t private: the TV makes its own certificate. Keep the TV’s address fixed in your router, or phones warn again.',
      ),
    ];
  };

  return { render: () => (remote.on() ? on() : off()) };
}
