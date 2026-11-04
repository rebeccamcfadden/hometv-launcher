// Copies the install guide (guide/: a static page and its pictures) to the site's root, dist/, after Vite has built the
// TV demo into dist/demo/. Pages publishes dist/ as it is (.github/workflows/pages.yml), so the guide is the root and
// the demo, with all its own relative links and pictures, lives at /demo/.
import { cpSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const dist = join(here, '..', 'dist');
if (!existsSync(join(dist, 'demo', 'index.html'))) {
  console.error('guide/build.mjs: dist/demo/index.html is missing; run vite build first');
  process.exit(1);
}
cpSync(join(here, 'index.html'), join(dist, 'index.html'));
cpSync(join(here, 'img'), join(dist, 'img'), { recursive: true });
console.log('guide copied to dist/ (the demo is in dist/demo/)');
