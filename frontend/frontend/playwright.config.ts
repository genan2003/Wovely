import { defineConfig, devices } from '@playwright/test';

/**
 * E2E tests for critical user flows. `npm run e2e` starts `ng serve -c e2e` on :4300: a client-only
 * build (no SSR), so every backend call goes through the browser and is mocked per test with
 * `page.route`. A dedicated port keeps it from reusing the docker frontend on :4200, and without SSR
 * a running backend can't leak real data in through Angular's HTTP transfer cache.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4300',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npx ng serve -c e2e --port 4300',
    url: 'http://localhost:4300',
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
  },
});
