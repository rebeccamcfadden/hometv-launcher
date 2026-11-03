import { defineConfig } from 'vite';
import { resolve } from 'node:path';

// Relative base so `npm run build` output can be opened from any static host. Four pages: the TV (index.html), the
// phone or laptop page the API keys QR opens (keys.html, F16), F40's cards side by side (compare.html), and the phone
// remote (remote.html, F27).
export default defineConfig({
  base: './',
  server: { port: 5173, open: true },
  build: {
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
