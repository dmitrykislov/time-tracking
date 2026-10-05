/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    // Straight into the jar's static resources; `mvn package` picks it up.
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8020' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./test/setup.ts'],
    css: false,
  },
});
