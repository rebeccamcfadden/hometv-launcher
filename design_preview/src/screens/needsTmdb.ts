import { account } from '../core/account';
import { app } from '../core/app';
import { h, replace } from '../core/dom';
import { featureOn } from '../core/settings';
import { icon } from '../ui/icons';
import { button } from '../ui/widgets';
import { turnOn } from './panes/features';
import { launcherSettings } from './settings';

/**
 * F14 (the project lead, 2026-11-01): Movies, TV Shows and Search need a TMDB account. They show only this state while
 * Launcher settings › Features has TMDB account off, or while the TV isn't signed in. Nothing is fetched from TMDB; the
 * button (Turn on, or Sign in) opens Account's sign-in. Built like the offline state (06-states A).
 */
export const tmdbReady = () => featureOn('tmdb') && account.get() === 'in';

export function needsTmdb(page: string): { el: HTMLElement; paint(): void } {
  const el = h('div.state.needs-tmdb');
  const paint = () => {
    const off = !featureOn('tmdb');
    replace(
      el,
      h('div.state-icon', { html: icon('user', 48) }),
      h('div.empty-title', `${page} needs a TMDB account`),
      h(
        'div.empty-note.state-note',
        `Titles, artwork, your lists and search come from TMDB, a free film and TV database. ${
          off
            ? 'TMDB account is switched off on this TV: Turn on switches it on and signs in.'
            : 'Sign in once with your phone in Launcher settings › Account.'
        } Apps, inputs and the clock on Home work without it.`,
      ),
      h(
        'div.state-actions',
        // The project lead, 2026-11-02: Turn on is right here; it goes straight into the sign-in, and Back returns.
        button(off ? 'Turn on' : 'Sign in', {
          icon: off ? 'power' : 'user',
          key: 'needs-tmdb',
          isDefault: true,
          onOk: () => {
            if (off) turnOn('tmdb');
            app.push(launcherSettings('account', { enter: true, backCloses: true }));
          },
        }),
      ),
    );
  };
  paint();
  return { el, paint };
}
