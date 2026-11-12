import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests through the real UI against BOTH domains.
 * Prerequisite: both backends are running (scripts/run-local.sh domain-a / domain-b).
 * Playwright starts the two Vite dev servers itself.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    // Use the locally installed Google Chrome; the bundled Chromium download can be blocked
    // by TLS-intercepting networks, and disabling certificate checks is not an option.
    channel: 'chrome',
    locale: 'zh-CN',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
  },
  webServer: [
    { command: 'npm run dev:a', url: 'http://localhost:5173', reuseExistingServer: true, timeout: 60_000 },
    { command: 'npm run dev:b', url: 'http://localhost:5174', reuseExistingServer: true, timeout: 60_000 },
  ],
});
