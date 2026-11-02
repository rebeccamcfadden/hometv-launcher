import { app } from '../core/app';
import { h, replace } from '../core/dom';
import { NEW_VERSION, updates } from '../core/updates';
import { icon } from './icons';

/**
 * Home's update notice (Family install step 5, the project lead's Q4 a): once per new version, when Home shows with a
 * newer version out, a pill for 8 s, "HomeTV Launcher 2.8 is available · Launcher settings › About". It sits where the
 * toast does (bottom 110), above Phone notes' import pill (bottom 56), so the two never cover each other; the launcher
 * shows it as its toast, in the same place. Never focusable, never in the way of the nav, gone for that version after
 * once, and taken away by any screen over Home.
 */
export function updateNotice(): HTMLElement {
  const el = h('div.import-pill.update-notice', { 'aria-live': 'polite', 'aria-hidden': 'true' });
  let hideTimer = 0;
  // Startup opens Home first and may route straight on (?jump=…): the first look waits until that is done, so a
  // temporary Home doesn't use up the notice, and a reload with an unseen update still shows it.
  let ready = false;
  const hide = () => {
    clearTimeout(hideTimer);
    el.classList.remove('is-shown');
    el.setAttribute('aria-hidden', 'true');
  };
  const show = () => {
    replace(el, h('span', { html: icon('sync', 22) }), `HomeTV Launcher ${NEW_VERSION} is available · Launcher settings › About`);
    el.classList.add('is-shown');
    el.setAttribute('aria-hidden', 'false');
    clearTimeout(hideTimer);
    hideTimer = window.setTimeout(hide, 8000);
  };
  const check = () => {
    if (!ready) return;
    if (app.top()?.label !== 'Home') hide();
    else if (updates.takeNotice()) show();
  };
  app.onChange(check);
  updates.on(check);
  window.setTimeout(() => {
    ready = true;
    check();
  }, 0);
  return el;
}
