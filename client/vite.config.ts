/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

/**
 * One client instance per mail domain. The dev/preview server proxies /api to that
 * domain's backend so the SPA and API share one origin (session cookie is SameSite=Strict,
 * no CORS needed).
 *
 *   npm run dev:a  -> http://localhost:5173 -> domain-a backend (:8081)
 *   npm run dev:b  -> http://localhost:5174 -> domain-b backend (:8082)
 */

// Content Security Policy for the built app (vite preview). Ant Design injects runtime
// <style> tags, hence 'unsafe-inline' for styles only; scripts are strictly same-origin.
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');

export default defineConfig(({ mode }) => {
  // Only VITE_* variables: never pull the whole process environment into the config.
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const target = env.VITE_API_TARGET || 'http://localhost:8081';
  const port = Number(env.VITE_PORT || 5173);
  const proxy = {
    '/api': { target, changeOrigin: false, secure: true },
  };

  return {
    plugins: [react()],
    server: { port, strictPort: true, proxy },
    preview: {
      port,
      strictPort: true,
      proxy,
      headers: {
        'Content-Security-Policy': CSP,
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'DENY',
        'Referrer-Policy': 'no-referrer',
      },
    },
    build: { sourcemap: false, chunkSizeWarningLimit: 1500 },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      css: false,
    },
  };
});
