// The package runs on 2018 Samsung TVs (Chromium 56). Their bundler turns new syntax into old, but not new built-in
// functions, so the source must not call any that Chromium 56 lacks.

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const src = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src');

/** Built-ins newer than Chromium 56, with the version that added them. */
const TOO_NEW: [RegExp, string][] = [
  [/\.matchAll\(/, 'String.prototype.matchAll (Chromium 73)'],
  [/\.padStart\(|\.padEnd\(/, 'padStart/padEnd (Chromium 57)'],
  [/Object\.fromEntries\(/, 'Object.fromEntries (Chromium 73)'],
  [/\.flat\(|\.flatMap\(/, 'Array.prototype.flat/flatMap (Chromium 69)'],
  [/\.replaceAll\(/, 'String.prototype.replaceAll (Chromium 85)'],
  [/\.at\(-?\d/, 'Array.prototype.at (Chromium 92)'],
  [/\.finally\(/, 'Promise.prototype.finally (Chromium 63)'],
  [/structuredClone\(/, 'structuredClone (Chromium 98)'],
  [/\.trimStart\(|\.trimEnd\(/, 'trimStart/trimEnd (Chromium 66)'],
  [/Object\.hasOwn\(/, 'Object.hasOwn (Chromium 93)'],
  [/\.findLast(Index)?\(/, 'findLast (Chromium 97)'],
  [/\bglobalThis\b/, 'globalThis (Chromium 71)'],
];

test('no built-ins newer than Chromium 56 in src/', () => {
  const found: string[] = [];
  for (const file of fs.readdirSync(src).filter((f) => f.endsWith('.ts'))) {
    const text = fs.readFileSync(path.join(src, file), 'utf8');
    for (const [re, what] of TOO_NEW) if (re.test(text)) found.push(`${file}: ${what}`);
  }
  assert.deepEqual(found, []);
});
