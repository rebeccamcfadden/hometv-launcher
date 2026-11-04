import '@hometv/core/tokens.css';
import './styles/remote.css';
import { CHANNEL, normalizeCode, TV_NAME, type PhoneMsg, type PhoneTitle, type RemoteKey, type TvMsg } from './core/remoteProtocol';
import { icon } from './ui/icons';

// The phone remote page (F27 with F39; Figma "Phone remote (F27)": layout A, Add to My list option C). On the TV the
// launcher serves it over HTTPS and it talks to the TV with fetch; here it talks to the TV prototype in another tab
// over a BroadcastChannel (core/remote.ts is the TV's side). ?phone=2 is a second phone in the same browser.

type Kid = Node | string | null | undefined | false | Kid[];
type Attrs = { [k: string]: string | boolean | ((e: Event) => void) | undefined };

/** A tiny element builder: el('div.a.b', { onclick }, kids…); `html` sets innerHTML (icons). */
function el(spec: string, attrs?: Attrs | Kid, ...kids: Kid[]): HTMLElement {
  const [tag, ...cls] = spec.split('.');
  const n = document.createElement(tag || 'div');
  if (cls.length) n.className = cls.join(' ');
  if (attrs && typeof attrs === 'object' && !(attrs instanceof Node) && !Array.isArray(attrs)) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v === undefined || v === false) continue;
      if (k === 'html') n.innerHTML = String(v);
      else if (k.startsWith('on') && typeof v === 'function') n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v === true ? '' : String(v));
    }
  } else kids.unshift(attrs as Kid);
  const add = (k: Kid) => {
    if (k === null || k === undefined || k === false) return;
    if (Array.isArray(k)) k.forEach(add);
    else n.append(k);
  };
  kids.forEach(add);
  return n;
}

const svg = (d: string, size = 24, sw = 1.8) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round"><path d="${d}"/></svg>`;
const I = {
  up: 'M6 15l6-6 6 6',
  down: 'M6 9l6 6 6-6',
  left: 'M15 6l-6 6 6 6',
  right: 'M9 6l6 6-6 6',
  keyboard: 'M3 6.5h18v11H3zM7 10h.01M11 10h.01M15 10h.01M7 14h10',
  x: 'M7 7l10 10M17 7L7 17',
  more: 'M6 12h.01M12 12h.01M18 12h.01',
  enter: 'M19 6v5a3 3 0 0 1-3 3H6M9 10l-4 4 4 4',
};

// ---- This phone ----
const params = new URLSearchParams(location.search);
const ME_KEY = `cleanyourtv-remote-phone-v1${params.get('phone') ? `-${params.get('phone')}` : ''}`;
interface Me {
  id: string;
  token?: string;
  name?: string;
}
function loadMe(): Me {
  try {
    const raw = localStorage.getItem(ME_KEY);
    if (raw) {
      const m = JSON.parse(raw) as Me;
      if (typeof m.id === 'string') return m;
    }
  } catch {
    // Blocked storage: a new phone each visit.
  }
  return { id: crypto.randomUUID() };
}
const me = loadMe();
const saveMe = () => {
  try {
    localStorage.setItem(ME_KEY, JSON.stringify(me));
  } catch {
    // The pairing lasts for this visit.
  }
};
saveMe();

/** The code from the QR link's #fragment, which a browser never sends over the network; used once, to pair. */
let codeFromLink = normalizeCode(decodeURIComponent(location.hash.slice(1)).replace(/[^0-9A-Za-z-]/g, ''));
/** "K7QM4PXT" as the TV shows it, "K7QM-4PXT". */
const showCode = (c: string) => (c.length === 8 ? `${c.slice(0, 4)}-${c.slice(4)}` : c);
/** A first guess at the phone's name, which the person can change. */
function guessName(): string {
  const ua = navigator.userAgent;
  if (/iPhone/.test(ua)) return 'iPhone';
  if (/iPad/.test(ua)) return 'iPad';
  if (/Android/.test(ua)) return 'Android phone';
  return 'This phone';
}

// ---- Talking to the TV ----
const channel = new BroadcastChannel(CHANNEL);
const post = (msg: PhoneMsg) => channel.postMessage({ from: 'phone', phone: me.id, msg });
let heard = 0;
let watchdog = 0;
/** Unanswered asks in a row: a busy or briefly unreachable TV gets a second chance before "Can't reach the TV". */
let misses = 0;
/** Sends, and if the TV says nothing within 1.5 s (twice in a row while on the remote), says it can't be reached. */
function ask(msg: PhoneMsg) {
  const asked = Date.now();
  post(msg);
  clearTimeout(watchdog);
  watchdog = window.setTimeout(() => {
    if (heard >= asked) return;
    misses++;
    if (view !== 'remote' || misses >= 2) show('notv');
  }, 1500);
}
/** A message that carries the token, without it: authed() adds it. */
type DistOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never;
const authed = (m: DistOmit<Extract<PhoneMsg, { token: string }>, 'token'>) => post({ ...m, token: me.token ?? '' } as PhoneMsg);
const buzz = () => navigator.vibrate?.(8);

// ---- State ----
type View = 'connecting' | 'notv' | 'off' | 'pair' | 'locked' | 'remote';
let view: View = 'connecting';
let tv: Extract<TvMsg, { type: 'state' }> | null = null;
let typing = false;
let sheet = false;
let results: PhoneTitle[] = [];
let resultsFor = '';
let pairError = '';
let pairNotice = '';
let pairing = false;
let lockedUntil = 0;
const root = document.getElementById('app')!;

channel.onmessage = (e: MessageEvent) => {
  const d = e.data as { from?: string; to?: string; msg?: TvMsg };
  if (d?.from !== 'tv' || d.to !== me.id || !d.msg) return;
  heard = Date.now();
  misses = 0;
  receive(d.msg);
};

function receive(msg: TvMsg) {
  switch (msg.type) {
    case 'off':
      return show('off');
    case 'unpaired':
      if (me.token) {
        me.token = undefined;
        saveMe();
        pairNotice = 'This phone was forgotten on the TV. Pair it again with the code.';
      }
      if (msg.locked > Date.now()) {
        lockedUntil = msg.locked;
        return show('locked');
      }
      return show('pair');
    case 'badcode':
      pairing = false;
      pairError = `That code doesn’t match the TV. Check it on the TV screen. ${msg.left} ${msg.left === 1 ? 'try' : 'tries'} left.`;
      return show('pair');
    case 'locked':
      pairing = false;
      lockedUntil = msg.until;
      return show('locked');
    case 'paired':
      pairing = false;
      me.token = msg.token;
      me.name = msg.name;
      saveMe();
      pairError = pairNotice = '';
      // The code has done its job: drop it from the address bar, so a bookmark or home-screen icon doesn't carry it,
      // and from the page, so a phone forgotten later asks for the TV's code then (it may be a new one).
      codeFromLink = '';
      history.replaceState(null, '', location.pathname + location.search);
      toast(`Paired as “${msg.name}”`, 'Add it to your home screen to open it in one tap.', null, 'good');
      return;
    case 'state': {
      tv = msg;
      // Her rule: only a TV that has left its text box ends typing here, never a busy TV's last known state.
      if (typing && !msg.typing && !msg.stale) endTyping();
      if (view !== 'remote') show('remote');
      else update();
      return;
    }
    case 'results':
      if (msg.q !== searchInput?.value) return;
      results = msg.items;
      resultsFor = msg.q;
      return paintResults();
    case 'mylisted':
      return mylisted(msg);
  }
}

// ---- Toast ----
let toastTimer = 0;
const toastEl = el('div.toast');
function toast(text: string, sub: string | null, undo: (() => void) | null, tone: 'good' | 'plain' | 'warn' = 'plain') {
  toastEl.replaceChildren(
    ...[
      el('div.toast-ico', { html: tone === 'warn' ? icon('alert', 18) : svg('M5 12.5l4.5 4.5L19 7.5', 18, 2.4) }),
      el('div.toast-words', el('div.toast-t', text), sub ? el('div.toast-s', sub) : null),
      undo ? el('button.toast-undo', { onclick: () => (undo(), hideToast()) }, 'Undo') : null,
    ].filter((x): x is HTMLElement => !!x),
  );
  toastEl.className = `toast is-shown${tone === 'good' ? ' toast--good' : tone === 'warn' ? ' toast--warn' : ''}${view === 'remote' && !sheet ? '' : ' toast--top'}`;
  clearTimeout(toastTimer);
  toastTimer = window.setTimeout(hideToast, undo ? 5000 : 3500);
}
const hideToast = () => toastEl.classList.remove('is-shown');

// ---- Pieces ----
function header(status: string, statusTone: 'good' | 'dim' | 'warn', menu: boolean) {
  return el(
    'header.hdr',
    el('div.hdr-badge', { html: icon('tv', 22) }),
    el('div.hdr-names', el('div.hdr-tv', tv?.tv ?? TV_NAME), el('div.hdr-status', el(`i.dot.dot--${statusTone}`), status)),
    menu ? el('button.round.hdr-more', { 'aria-label': 'More', onclick: openMenu, html: svg(I.more, 22, 2.6) }) : null,
  );
}

function openMenu() {
  const close = () => sheetEl.remove();
  const sheetEl = el(
    'div.menu-scrim',
    { onclick: (e: Event) => e.target === sheetEl && close() },
    el(
      'div.menu',
      el('div.menu-t', me.name ? `This phone: ${me.name}` : 'This phone'),
      el(
        'button.menu-item',
        {
          onclick: () => {
            authed({ type: 'forget' });
            me.token = undefined;
            saveMe();
            close();
            pairNotice = 'Unpaired. The TV no longer lists this phone.';
            show('pair');
          },
        },
        'Unpair this phone',
      ),
      el('button.menu-item.menu-item--dim', { onclick: close }, 'Cancel'),
    ),
  );
  document.body.append(sheetEl);
}

/** A key that sends on press; arrows repeat while held, as a remote's do. One repeat at a time, stopped by any release. */
let stopRepeat: (() => void) | null = null;
['pointerup', 'pointercancel', 'blur'].forEach((ev) => window.addEventListener(ev, () => stopRepeat?.()));
function keyButton(spec: string, key: RemoteKey, content: Kid, label?: string) {
  let repeat = 0;
  let delay = 0;
  const stop = () => {
    clearTimeout(delay);
    clearInterval(repeat);
    if (stopRepeat === stop) stopRepeat = null;
  };
  const press = () => {
    buzz();
    authed({ type: 'key', key });
  };
  // A tap is handled on pointerdown (at once, and held arrows repeat), so a click that follows it, which has a detail
  // count, is skipped; on touch, pointerleave comes between the two. A click with no pointer (a screen reader, or
  // Enter/Space on the focused button) presses once.
  let tapped = false;
  return el(
    spec,
    {
      'aria-label': label ?? key,
      onpointerdown: (e: Event) => {
        e.preventDefault();
        tapped = true;
        stopRepeat?.();
        press();
        if (['up', 'down', 'left', 'right'].includes(key)) {
          stopRepeat = stop;
          delay = window.setTimeout(() => (repeat = window.setInterval(press, 120)), 400);
        }
      },
      onpointerup: stop,
      onpointerleave: stop,
      onpointercancel: stop,
      onkeydown: (e: Event) => {
        const k = (e as KeyboardEvent).key;
        if (k === 'Enter' || k === ' ') tapped = false;
      },
      onclick: (e: Event) => {
        const fromPointer = tapped || (e as MouseEvent).detail > 0;
        tapped = false;
        if (!fromPointer) press();
      },
      oncontextmenu: (e: Event) => e.preventDefault(),
    },
    content,
  );
}

const dpad = () =>
  el(
    'div.dpad',
    keyButton('button.dpad-arrow.dpad-up', 'up', el('span', { html: svg(I.up, 30, 2) })),
    keyButton('button.dpad-arrow.dpad-down', 'down', el('span', { html: svg(I.down, 30, 2) })),
    keyButton('button.dpad-arrow.dpad-left', 'left', el('span', { html: svg(I.left, 30, 2) })),
    keyButton('button.dpad-arrow.dpad-right', 'right', el('span', { html: svg(I.right, 30, 2) })),
    keyButton('button.dpad-ok', 'ok', 'OK'),
  );

const roundKey = (key: RemoteKey, ic: 'back' | 'home', label: string) =>
  el('div.rkey', keyButton('button.round.round--big', key, el('span', { html: icon(ic, 26) }), label), el('div.rkey-label', label));

function card(t: PhoneTitle) {
  return el(
    'div.card',
    el('img.card-art', { src: `${import.meta.env.BASE_URL}img/${t.art}`, alt: '' }),
    el('div.card-words', el('div.card-eyebrow', 'On the TV'), el('div.card-title', t.name), el('div.card-meta', `${t.year} · ${t.kind === 'show' ? 'TV show' : 'Movie'}`)),
    el(
      `button.mylist${t.mine ? '.is-on' : ''}`,
      { onclick: () => toggleMine(t) },
      el('span', { html: svg(t.mine ? 'M5 12.5l4.5 4.5L19 7.5' : 'M12 5v14M5 12h14', 18, 2.2) }),
      t.mine ? 'On My list' : 'My list',
    ),
  );
}

/** F39: add or take off (setMine); the TV toasts it too. */
function toggleMine(t: PhoneTitle) {
  buzz();
  setMine(t, !t.mine);
}

/**
 * F39: what the phone wants (on or off), never a toggle, so a stale + can't take off a title. The button moves at once;
 * the TV's answer is the truth: "Added" (with Undo) only once the TV says so, and a refusal or no answer puts the button
 * back and says why. Answers are counted per title, so a late one never undoes a later tap (or Undo).
 *
 * A failure goes back to what the TV last confirmed for that title, not to the opposite of the last tap: two quick taps
 * with no answer end where the TV is. Only the TV's answers move that value. A timeout keeps the latest request, so an
 * answer that comes late can still put the button right.
 */
let mylistSeq = 0;
const mylistPending = new Map<string, { seq: number; t: PhoneTitle; timer: number; timedOut?: boolean }>();
/** Per title: the TV's last answer (mine, and the seq it answered), or what the TV showed before the tap. */
const mylistConfirmed = new Map<string, { mine: boolean; seq: number }>();
function setMine(t: PhoneTitle, wanted: boolean) {
  const seq = ++mylistSeq;
  const old = mylistPending.get(t.id);
  if (old) clearTimeout(old.timer);
  // Nothing in flight (or only a request that timed out, whose answer may never come): the button shows the TV's own
  // value, so that is the last confirmed one. A late answer to the timed-out request still counts over it.
  if (!old || old.timedOut) mylistConfirmed.set(t.id, { mine: t.mine, seq: old ? old.seq - 1 : seq - 1 });
  // On the TV: the request's 4 s timeout; here, the TV tab answers at once or not at all.
  const timer = window.setTimeout(() => mylisted({ type: 'mylisted', id: t.id, seq, failed: true }, true), 1500);
  mylistPending.set(t.id, { seq, t, timer });
  t.mine = wanted;
  authed({ type: 'mylist', id: t.id, mine: wanted, seq });
  update();
  paintResults();
}
function mylisted(msg: Extract<TvMsg, { type: 'mylisted' }>, unanswered = false) {
  const answered = !msg.failed && typeof msg.mine === 'boolean';
  // Any real answer is the TV's truth, even for an older tap; keep the newest one.
  const known = mylistConfirmed.get(msg.id);
  if (answered && (!known || msg.seq > known.seq)) mylistConfirmed.set(msg.id, { mine: msg.mine as boolean, seq: msg.seq });
  const p = mylistPending.get(msg.id);
  if (!p || p.seq !== msg.seq) {
    // A later tap on this title decides. If it has timed out, this answer is the news: show the TV's value.
    const now = mylistConfirmed.get(msg.id)?.mine;
    if (p?.timedOut && now !== undefined && p.t.mine !== now) {
      p.t.mine = now;
      update();
      paintResults();
    }
    return;
  }
  clearTimeout(p.timer);
  const t = p.t;
  if (answered) {
    mylistPending.delete(msg.id);
    const now = msg.mine as boolean;
    t.mine = now;
    update();
    paintResults();
    toast(`${now ? 'Added' : 'Removed'} ${t.name} ${now ? 'to' : 'from'} My list`, null, () => setMine(t, !now));
    return;
  }
  // A refusal ends the request; a timeout keeps it, so the TV's late answer can still reconcile it.
  if (!unanswered) mylistPending.delete(msg.id);
  else p.timedOut = true;
  t.mine = mylistConfirmed.get(msg.id)?.mine ?? t.mine;
  update();
  paintResults();
  toast('My list didn’t change', unanswered ? 'This phone can’t reach the TV.' : 'Open HomeTV on the TV and try again.', null, 'warn');
}

// ---- Views ----
let fieldEl: HTMLInputElement | null = null;
/** What the remote view's middle holds now, so update() rebuilds it only when that changes. */
let mainMode: 'pad' | 'typing' | null = null;
let searchInput: HTMLInputElement | null = null;
let resultsEl: HTMLElement | null = null;
const slots = { top: el('div.slot'), main: el('div.slot.slot--main'), bottom: el('div.slot') };

function show(v: View) {
  view = v;
  typing = v === 'remote' && typing;
  // A view change ends any pairing under way (no answer, off, forgotten): Pair works again.
  pairing = false;
  mainMode = null;
  stopRepeat?.();
  if (v !== 'remote') {
    sheet = false;
    document.querySelector('.sheet-scrim')?.remove();
    // An Undo from the remote means nothing once the phone isn't paired.
    hideToast();
  }
  clearInterval(lockTimer);
  root.replaceChildren();
  root.className = `view view--${v}`;
  if (v === 'connecting') root.append(header('Connecting…', 'dim', false), el('div.center', el('div.spinner')));
  if (v === 'notv') root.append(header('Not connected', 'warn', false), message('Can’t reach the TV', 'Check that the TV is on, on the same Wi-Fi as this phone, and that Launcher settings › Phone remote is on. In the prototype, keep the TV page open in another tab of this browser.'));
  if (v === 'off') root.append(header('Phone remote is off', 'warn', false), message('Phone remote is off on the TV', 'Turn it on in Launcher settings › Phone remote on the TV, then try again.'));
  if (v === 'pair') root.append(pairView());
  if (v === 'locked') root.append(lockedView());
  if (v === 'remote') {
    root.append(header(`Connected · HomeTV Launcher`, 'good', true), slots.top, slots.main, slots.bottom);
    update();
  }
  root.append(toastEl);
}

function message(title: string, body: string) {
  return el(
    'div.center',
    el('div.msg-ico', { html: icon('alert', 40) }),
    el('h1.msg-t', title),
    el('p.msg-b', body),
    el('button.btn.btn--wide', { onclick: () => (show('connecting'), hello()) }, 'Try again'),
  );
}

function pairView() {
  const fromLink = !!codeFromLink;
  const code = el('input.field-input.field-input--code', {
    value: showCode(codeFromLink),
    autocomplete: 'off',
    autocapitalize: 'characters',
    spellcheck: 'false',
    maxlength: '9',
    placeholder: 'K7QM-4PXT',
    readonly: fromLink && !pairError,
    'aria-label': 'Code from the TV',
  }) as HTMLInputElement;
  const name = el('input.field-input', { value: me.name ?? guessName(), autocomplete: 'off', maxlength: '40', 'aria-label': 'Name this phone' }) as HTMLInputElement;
  const submit = (e: Event) => {
    e.preventDefault();
    if (!normalizeCode(code.value) || pairing) return;
    pairing = true;
    pairError = '';
    btn.setAttribute('disabled', '');
    btn.textContent = 'Pairing…';
    me.name = name.value.trim() || guessName();
    ask({ type: 'pair', code: code.value, name: me.name });
  };
  const btn = el('button.btn.btn--wide', { type: 'submit' }, 'Pair');
  return el(
    'div.pairwrap',
    header('Not paired yet', 'dim', false),
    el(
      'form.pair',
      { onsubmit: submit },
      el('h1.pair-t', 'Pair this phone'),
      el('p.pair-s', fromLink ? `It becomes a remote and keyboard for ${TV_NAME}.` : 'Type the code shown in Launcher settings › Phone remote on the TV.'),
      pairNotice ? el('p.note', pairNotice) : null,
      el(
        'label.group',
        el('span.group-label', 'Code from the TV'),
        el(`div.field${pairError ? '.field--warn' : ''}`, code, fromLink && !pairError ? el('span.field-ok', { html: svg('M5 12.5l4.5 4.5L19 7.5', 20, 2.2) }) : null),
        pairError
          ? el('span.help.help--warn', el('span', { html: icon('alert', 16) }), pairError)
          : fromLink
            ? el('span.help.help--good', 'Filled in from the QR code.')
            : null,
      ),
      el(
        'label.group',
        el('span.group-label', 'Name this phone'),
        el('div.field', name),
        el('span.help', 'The TV lists phones by this name, so you can forget one later.'),
      ),
      el('div.grow'),
      btn,
    ),
  );
}

let lockTimer = 0;
function lockedView() {
  const count = el('div.lock-count');
  const btn = el('button.btn.btn--wide.btn--quiet', { disabled: true, onclick: () => ((pairError = ''), show('pair')) }, 'Try again when the timer ends');
  const tick = () => {
    const left = Math.max(0, lockedUntil - Date.now());
    count.textContent = `${Math.floor(left / 60000)}:${String(Math.floor((left % 60000) / 1000)).padStart(2, '0')}`;
    if (left === 0) {
      clearInterval(lockTimer);
      btn.removeAttribute('disabled');
      btn.textContent = 'Try again';
      btn.classList.remove('btn--quiet');
    }
  };
  queueMicrotask(() => {
    tick();
    lockTimer = window.setInterval(tick, 1000);
  });
  return el(
    'div.pairwrap',
    header('Pairing paused', 'warn', false),
    el(
      'div.center',
      el('div.lock-badge', { html: icon('lock', 40) }),
      el('h1.msg-t', 'Pairing is paused'),
      el('p.msg-b', 'Too many wrong codes. The TV takes new phones again in'),
      count,
      el('p.msg-s', 'Phones that are already paired keep working.'),
      el('div.grow'),
      btn,
    ),
  );
}

/** The remote view's parts, redrawn as the TV's state changes; the typing field keeps its focus. */
function update() {
  if (view !== 'remote' || !tv) return;
  const t = tv.title;
  const hint = el('div.hint', el('i.dot.dot--accent'), `On the TV: ${tv.screen} · ${tv.typing ? 'buttons and typing' : 'buttons only'}`);
  slots.top.replaceChildren(
    t ? card(t) : hint,
    el('button.link', { onclick: openSheet }, el('span', { html: icon('search', 16) }), 'Something else? Search to add to My list'),
  );
  if (typing) {
    if (mainMode !== 'typing' || !fieldEl || !slots.main.contains(fieldEl)) slots.main.replaceChildren(typingPanel());
    mainMode = 'typing';
    slots.bottom.replaceChildren();
    return;
  }
  fieldEl = null;
  // Built once per mode: rebuilding the D-pad under a finger would lose the key being held.
  if (mainMode !== 'pad') slots.main.replaceChildren(el('div.grow'), dpad(), el('div.keys', roundKey('back', 'back', 'Back'), roundKey('home', 'home', 'Home')), el('div.grow'));
  mainMode = 'pad';
  slots.bottom.replaceChildren(
    tv.typing
      ? el('button.typebar', { onclick: startTyping }, el('span', { html: svg(I.keyboard, 22) }), 'Type on the TV')
      : el('div.typebar.typebar--off', el('span', { html: svg(I.keyboard, 22) }), 'Typing works when the TV shows a text box'),
  );
  if (sheet && !document.querySelector('.sheet-scrim')) openSheet();
}

/** The Type bar: the field, focused within the tap so the phone's keyboard opens (iOS needs that). */
function startTyping() {
  typing = true;
  update();
  if (fieldEl) {
    fieldEl.focus();
    // Carry on from the end of what the TV already shows.
    fieldEl.setSelectionRange(fieldEl.value.length, fieldEl.value.length);
  }
}

/**
 * Back to the remote, keyboard down. Rebecca's rule (2026-11-02): only when the keyboard is collapsed on the phone (the
 * field's blur), Enter, or the TV left its text box.
 */
function endTyping() {
  if (!typing) return;
  typing = false;
  flushText();
  const f = fieldEl;
  fieldEl = null;
  f?.blur();
  update();
}

/** Text goes to the TV ~120 ms after the last keystroke, one send at a time, the newest value last. */
let textTimer = 0;
let textSent = '';
function queueText() {
  clearTimeout(textTimer);
  textTimer = window.setTimeout(flushText, 120);
}
function flushText() {
  clearTimeout(textTimer);
  const v = fieldEl?.value;
  if (v === undefined || v === textSent) return;
  textSent = v;
  authed({ type: 'text', value: v });
}

function typingPanel() {
  fieldEl = el('input.field-input.field-input--big', {
    value: tv?.text ?? '',
    autocomplete: 'off',
    autocapitalize: 'none',
    spellcheck: 'false',
    enterkeyhint: 'search',
    'aria-label': 'Text on the TV',
    oninput: queueText,
    onkeydown: (e: Event) => {
      if ((e as KeyboardEvent).key !== 'Enter') return;
      e.preventDefault();
      flushText();
      authed({ type: 'enter' });
      endTyping();
    },
    // The keyboard collapsed (or a tap outside the field).
    onblur: () => endTyping(),
  }) as HTMLInputElement;
  textSent = fieldEl.value;
  const clear = el('button.round.round--small', {
    'aria-label': 'Clear the TV’s text box',
    // Keeps the field focused, so the keyboard stays up.
    onpointerdown: (e: Event) => e.preventDefault(),
    onclick: () => {
      fieldEl!.value = '';
      flushText();
    },
    html: svg(I.x, 18, 2),
  });
  return el(
    'div.typing',
    el('div.field.field--live', fieldEl, clear),
    el('p.small', 'Enter searches · close the keyboard to go back to the remote'),
  );
}

// ---- F39: search to add ----
let searchTimer = 0;
function openSheet() {
  sheet = true;
  document.querySelector('.sheet-scrim')?.remove();
  searchInput = el('input.field-input', {
    placeholder: 'Search films and shows',
    autocomplete: 'off',
    autocapitalize: 'none',
    spellcheck: 'false',
    enterkeyhint: 'search',
    'aria-label': 'Search to add to My list',
    oninput: () => {
      clearTimeout(searchTimer);
      searchTimer = window.setTimeout(() => searchInput && authed({ type: 'search', q: searchInput.value }), 150);
    },
  }) as HTMLInputElement;
  resultsEl = el('div.results');
  const close = () => {
    clearTimeout(searchTimer);
    sheet = false;
    scrim.remove();
    searchInput = resultsEl = null;
    results = [];
    resultsFor = '';
  };
  const scrim = el(
    'div.sheet-scrim',
    { onclick: (e: Event) => e.target === scrim && close() },
    el(
      'div.sheet',
      el('div.handle'),
      el('div.sheet-head', el('h2', 'Add to My list'), el('button.link.link--strong', { onclick: close }, 'Done')),
      el('div.field.field--live', el('span.field-ico', { html: icon('search', 20) }), searchInput),
      resultsEl,
      el('p.small', 'Search uses the TV’s catalogue. Posters load through the TV.'),
    ),
  );
  document.body.append(scrim);
  paintResults();
  queueMicrotask(() => searchInput?.focus());
}

function paintResults() {
  if (!resultsEl) return;
  if (!searchInput?.value.trim()) {
    resultsEl.replaceChildren(el('p.small.results-empty', 'Type a title. + adds it to My list; ✓ is already on it.'));
    return;
  }
  if (!results.length) {
    resultsEl.replaceChildren(el('p.small.results-empty', resultsFor ? `Nothing found for “${resultsFor}”.` : 'Searching…'));
    return;
  }
  resultsEl.replaceChildren(
    ...results.map((r) =>
      el(
        'div.result',
        el('img.result-art', { src: `${import.meta.env.BASE_URL}img/${r.art}`, alt: '' }),
        el('div.result-words', el('div.result-t', r.name), el('div.result-m', `${r.year} · ${r.kind === 'show' ? 'TV show' : 'Movie'}`)),
        el(`button.round${r.mine ? '.round--good' : ''}`, { 'aria-label': r.mine ? `Take ${r.name} off My list` : `Add ${r.name} to My list`, onclick: () => toggleMine(r), html: svg(r.mine ? 'M5 12.5l4.5 4.5L19 7.5' : 'M12 5v14M5 12h14', 20, 2.2) }),
      ),
    ),
  );
}

// Android Chrome closes the keyboard (Back, the keyboard's own ⌄) without taking focus from the field, so no blur comes;
// the visual viewport growing back to full height after the keyboard showed is that collapse.
let keyboardSeen = false;
window.visualViewport?.addEventListener('resize', () => {
  const vv = window.visualViewport!;
  if (!typing) {
    keyboardSeen = false;
    return;
  }
  if (window.innerHeight - vv.height > 120) keyboardSeen = true;
  else if (keyboardSeen) {
    keyboardSeen = false;
    endTyping();
  }
});

// ---- Start ----
function hello() {
  ask({ type: 'hello', token: me.token });
}

// A laptop's keyboard works as the remote too, outside a text box. Enter or Space on a focused button is that button's
// own press (its click), not OK.
window.addEventListener('keydown', (e) => {
  const target = e.target as HTMLElement;
  if (view !== 'remote' || target.tagName === 'INPUT') return;
  if (target.tagName === 'BUTTON' && (e.key === 'Enter' || e.key === ' ')) return;
  const keys: Record<string, RemoteKey> = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'ok', Escape: 'back', Backspace: 'back' };
  if (!keys[e.key]) return;
  e.preventDefault();
  authed({ type: 'key', key: keys[e.key] });
});

// Every 10 s while open, so the TV knows a phone is connected; a phone that goes quiet stops counting after 25 s.
window.setInterval(() => {
  if (view === 'remote' && document.visibilityState === 'visible') ask({ type: 'hello', token: me.token });
}, 10_000);
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && me.token) hello();
  else if (me.token) authed({ type: 'bye' });
});

show('connecting');
hello();
