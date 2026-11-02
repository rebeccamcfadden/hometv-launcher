import { keys, keyStatus, KEYS_PRESETS, type KeyId, type KeysPreset } from './core/keys';

// The phone and laptop page's behaviour (keys.html). On the TV the page posts each key with the code to the TV's own
// address and shows its answer; here it hands them to the prototype's key store, which the TV tab is listening to.

const $ = <T extends HTMLElement>(sel: string) => document.querySelector<T>(sel)!;

// ?keys=none (a family TV) and the other dev bar presets, for screenshots of the page on its own.
const preset = new URLSearchParams(location.search).get('keys') as KeysPreset | null;
if (preset && KEYS_PRESETS.some(([id]) => id === preset)) keys.devSet(preset);

// The code is in the address's #fragment (from the QR), which a phone never sends over the network. A laptop types it.
const fromLink = decodeURIComponent((location.hash || '').slice(1)).replace(/[^0-9A-Za-z]/g, '').toUpperCase();
const codeInput = $<HTMLInputElement>('#code');
if (!fromLink) $('#codebox').hidden = false;
const code = () => (fromLink || codeInput.value).replace(/[^0-9A-Za-z]/g, '').toUpperCase();

$('#addr').textContent = keys.address;
// A laptop that opened the address itself needs no tip about using a laptop.
if (!fromLink) $('#laptoptip').hidden = true;

function paint() {
  document.querySelectorAll<HTMLElement>('section.key[data-key]').forEach((sec) => {
    const id = sec.dataset.key as KeyId;
    const st = keys.get(id).state;
    const out = sec.querySelector<HTMLElement>('[data-status]')!;
    // A key from the build is the TV's own business: the page just says one works.
    const s = st === 'built' ? { text: 'The TV already has a working key; one pasted here replaces it.', tone: 'good' as const } : keyStatus(id);
    out.textContent = s.text;
    out.className = `status ${s.tone}`;
  });
}

function send(id: KeyId) {
  const field = document.getElementById(`k-${id}`) as HTMLInputElement | HTMLTextAreaElement;
  const value = field.value.trim();
  if (!value) return field.focus();
  if (code() !== keys.code()) {
    $('#badcode').hidden = false;
    return;
  }
  $('#badcode').hidden = true;
  keys.submit(id, value);
  // The key leaves the page once sent: nothing to read back off the screen.
  field.value = '';
}

document.querySelectorAll<HTMLButtonElement>('[data-send]').forEach((b) => b.addEventListener('click', () => send(b.dataset.send as KeyId)));
document.querySelectorAll<HTMLButtonElement>('[data-copy]').forEach((b) =>
  b.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(b.dataset.copy!);
      b.textContent = 'Copied';
    } catch {
      b.textContent = 'Select and copy';
    }
  }),
);

keys.on(paint);
paint();
