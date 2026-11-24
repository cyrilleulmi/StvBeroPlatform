import { defineConfig, devices } from '@playwright/test';

/**
 * E2E tests (docs/testability.md, docs/architecture.md).
 *
 * Two runs (E-107):
 *   fast (default)       every test on mobile; on desktop only the tests tagged `@desktop`, i.e. those that check
 *                        something the desktop layout does differently, and one smoke test per page.
 *   full (E2E_FULL=true) every test on mobile and on desktop. The CI runs it after every merge and every night.
 *
 * Two ways to start them:
 *   scripts/e2e.sh       builds the app once and starts one backend with its own database per worker
 *                        (E2E_STACKS). The tests run in parallel and leave an app you are trying out alone.
 *   npx playwright test  one worker against the app on :4200/:8080, started here if it is not running. The tests
 *                        reset its database before every test. The database must be running (scripts/db-start.sh).
 */
const stacks = (process.env.E2E_STACKS ?? '').split(',').filter(Boolean);
const full = process.env.E2E_FULL === 'true';

export default defineConfig({
  testDir: './tests',
  // The tests of one file share a worker and run in order; the files are spread over the workers.
  fullyParallel: false,
  // Every worker has a backend and a database of its own (tests/helpers.ts): never more workers than stacks.
  workers: stacks.length || 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    // With stacks the address comes per worker from tests/helpers.ts
    baseURL: stacks.length ? undefined : (process.env.BASE_URL ?? 'http://localhost:4200'),
    channel: 'chrome',
    locale: 'de-CH',
    timezoneId: 'Europe/Zurich',
    testIdAttribute: 'data-testid',
    contextOptions: { reducedMotion: 'reduce' },
    trace: 'off',
  },
  projects: [
    {
      name: 'mobile',
      use: { viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2, userAgent: devices['Pixel 7'].userAgent },
    },
    {
      name: 'desktop',
      grep: full ? undefined : /@desktop/,
      use: { viewport: { width: 1280, height: 800 } },
    },
  ],
  // scripts/e2e.sh starts its own backends, with the frontend inside
  webServer: stacks.length || process.env.E2E_NO_WEBSERVER ? undefined : [
    {
      command: 'mvn -B -q quarkus:dev -Dquarkus.console.enabled=false',
      cwd: '../backend',
      // Start with the test data set and the simulators: the tests never ask the real swiss unihockey
      // (docs/test-data.md, data sets). A backend that is already running is reset by every test anyway.
      env: { STV_SIMULATION: 'true' },
      url: 'http://localhost:8080/api/test/clock',
      reuseExistingServer: true,
      timeout: 300_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm start -- --port 4200',
      cwd: '../frontend',
      url: 'http://localhost:4200',
      reuseExistingServer: true,
      timeout: 300_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
});
