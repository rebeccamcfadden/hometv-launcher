import { app } from '../core/app';
import { h, replace } from '../core/dom';
import { NEW_VERSION, updates } from '../core/updates';
import { icon } from './icons';

/**
 * Home's update notice (Family install step 5, the project lead's Q4 a): once per new version, when Home shows with an
 * update downloaded and waiting, the import pill's shape at the bottom centre for 8 s, "HomeTV Launcher 2.8 is ready ·
 * Launcher settings › About". Never focusable, never in the way of the nav, and gone for that version after once.
 */
export function updateNotice(): HTMLElement {
  const el = h('div.import-pill.update-notice', { 'aria-live': 'polite', 'aria-hidden': 'true' });
  let hideTimer = 0;
  const hide = () => {
    clearTimeout(hideTimer);
    el.classList.remove('is-shown');
    el.setAttribute('aria-hidden', 'true');
  };
  const show = () => {
    replace(el, h('span', { html: icon('sync', 22) }), `HomeTV Launcher ${NEW_VERSION} is ready · Launcher settings › About`);
    el.classList.add('is-shown');
    el.setAttribute('aria-hidden', 'false');
    clearTimeout(hideTimer);
    hideTimer = window.setTimeout(hide, 8000);
  };
  // Only over Home itself: another screen on top takes it away.
  const check = () => {
    if (app.top()?.label !== 'Home') hide();
    else if (updates.takeNotice()) show();
  };
  app.onChange(check);
  updates.on(check);
  return el;
}
