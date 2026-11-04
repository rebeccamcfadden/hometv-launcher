// The API keys' words and the pairing code.

import test from 'node:test';
import assert from 'node:assert/strict';
import { CODE_ALPHABET, KEY_INFO, keyStatus, keysValue, newCode } from '../src/keys.ts';

test('each key state has its line', () => {
  assert.deepEqual(keyStatus('tmdb', { state: 'ok', last4: 'Qx7w' }), { text: 'Works · ends Qx7w', tone: 'good' });
  assert.equal(keyStatus('motn', { state: 'unset', last4: '' }).text, 'Not set · optional');
  assert.equal(keyStatus('watchmode', { state: 'unset', last4: '' }).text, 'Not set');
  assert.match(keyStatus('tmdb', { state: 'bad', last4: '' }).text, /Read Access Token/);
  assert.match(keyStatus('watchmode', { state: 'bad', last4: '' }).text, /^Watchmode refused/);
  assert.equal(keyStatus('watchmode', { state: 'unreachable', last4: '' }).tone, 'warn');
});

test("the section's value", () => {
  const k = (tmdb: string, watchmode: string, motn: string) =>
    ({ tmdb: { state: tmdb }, watchmode: { state: watchmode }, motn: { state: motn } }) as Parameters<typeof keysValue>[0];
  assert.deepEqual(keysValue(k('built', 'built', 'built')), { text: '3 working' });
  assert.deepEqual(keysValue(k('unset', 'unset', 'unset')), { text: 'Not set', warn: false });
  assert.deepEqual(keysValue(k('ok', 'unset', 'unset')), { text: '1 of 3 set', warn: false });
  assert.deepEqual(keysValue(k('ok', 'bad', 'unset')), { text: '1 refused', warn: true });
  assert.deepEqual(keysValue(k('ok', 'unreachable', 'unset')), { text: '1 not checked', warn: true });
  assert.deepEqual(keysValue(k('ok', 'checking', 'unset')), { text: 'Checking…' });
  assert.deepEqual(keysValue(k('unset', 'ok', 'unset'), ['watchmode']), { text: '1 working' });
});

test('codes are six letters from the readable alphabet', () => {
  for (let i = 0; i < 50; i++) assert.match(newCode(), /^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$/);
  assert.equal(newCode(() => 0), CODE_ALPHABET[0].repeat(6));
  assert.ok(!/[01OIL]/.test(CODE_ALPHABET));
});

test('three keys, TMDB first', () => {
  assert.deepEqual(KEY_INFO.map((k) => k.id), ['tmdb', 'watchmode', 'motn']);
});
