import { defineConfig } from 'vite';
import { resolve } from 'node:path';

// Relative base so `npm run build` output can be opened from any static host. The build goes to dist/demo/: the site's
// root is the install guide (guide/, copied by guide/build.mjs), and the demo lives at /demo/. Four pages: the TV (index.html), the
// phone or laptop page the API keys QR opens (keys.html, F16), F40's cards side by side (compare.html), and the phone
// remote (remote.html, F27).
export default defineConfig({
  base: './',
  // Logic shared with HomeTV for Samsung (packages/core), imported as @hometv/core/<file>.
  resolve: { alias: { '@hometv/core': resolve(__dirname, '../packages/core/src') } },
  server: { port: 5173, open: true, fs: { allow: [__dirname, resolve(__dirname, '../packages/core')] } },
  build: {
    outDir: 'dist/demo',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        keys: resolve(__dirname, 'keys.html'),
        compare: resolve(__dirname, 'compare.html'),
        remote: resolve(__dirname, 'remote.html'),
      },
    },
  },
});
