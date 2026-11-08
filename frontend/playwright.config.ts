import { defineConfig, devices } from '@playwright/test';

/**
 * Local-first E2E (P7-D9 / P7-D15). Requires app stack running:
 *   FE: http://localhost:5173 (vite) or http://localhost:3000 (compose)
 *   BE: http://localhost:8080
 *
 * Set E2E_BASE_URL to override. CI job intentionally not enabled by default.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 60_000,
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
