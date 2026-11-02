import { defineConfig } from 'vite';
import { resolve } from 'node:path';

// Relative base so `npm run build` output can be opened from any static host. Two pages: the TV (index.html) and the
// phone or laptop page the API keys QR opens (keys.html, F16).
export default defineConfig({
  base: './',
  server: { port: 5173, open: true },
  build: {
    rollupOptions: {
      input: { main: resolve(__dirname, 'index.html'), keys: resolve(__dirname, 'keys.html') },
    },
  },
});
