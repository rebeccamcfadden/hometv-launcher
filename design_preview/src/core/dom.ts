export type Child = Node | string | number | null | undefined | false | Child[];
type AttrValue = string | number | boolean | null | undefined;
export type Attrs = Record<string, AttrValue> & { html?: string; style?: string };

const isAttrs = (x: unknown): x is Attrs =>
  typeof x === 'object' && x !== null && !(x instanceof Node) && !Array.isArray(x);

/** `h('div.card.is-big#main', { 'data-x': 1 }, child, …)` */
export function h(sel: string, ...rest: (Attrs | Child)[]): HTMLElement {
  const tag = /^[a-z0-9]+/i.exec(sel)?.[0] ?? 'div';
  const el = document.createElement(tag);
  for (const m of sel.matchAll(/\.([\w-]+)/g)) el.classList.add(m[1]);
  const id = /#([\w-]+)/.exec(sel)?.[1];
  if (id) el.id = id;
  if (rest.length && isAttrs(rest[0])) {
    const attrs = rest.shift() as Attrs;
    for (const [k, v] of Object.entries(attrs)) {
      if (v === undefined || v === null || v === false) continue;
      if (k === 'html') el.innerHTML = String(v);
      else el.setAttribute(k, v === true ? '' : String(v));
    }
  }
  append(el, rest as Child[]);
  return el;
}

export function append(el: Element, children: Child[]): void {
  for (const c of children) {
    if (c === null || c === undefined || c === false) continue;
    if (Array.isArray(c)) append(el, c);
    else el.append(c instanceof Node ? c : String(c));
  }
}

export function replace(el: Element, ...children: Child[]): void {
  el.replaceChildren();
  append(el, children);
}

export const imgUrl = (file: string) => `${import.meta.env.BASE_URL}img/${file}`;

/** Text with the characters at `indices` wrapped in <b class="hit">. */
export function highlight(text: string, indices: number[]): DocumentFragment {
  const frag = document.createDocumentFragment();
  const set = new Set(indices);
  let run = '';
  let hit = false;
  const flush = () => {
    if (!run) return;
    frag.append(hit ? h('b.hit', run) : document.createTextNode(run));
    run = '';
  };
  for (let i = 0; i < text.length; i++) {
    const isHit = set.has(i);
    if (isHit !== hit) {
      flush();
      hit = isHit;
    }
    run += text[i];
  }
  flush();
  return frag;
}

export const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase();
