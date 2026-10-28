import { defineConfig } from 'vite';

// Relative base so `npm run build` output can be opened from any static host.
export default defineConfig({
  base: './',
  server: { port: 5173, open: true },
});
