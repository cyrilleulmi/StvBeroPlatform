import { defineConfig, devices } from '@playwright/test';

/**
 * E2E tests (docs/testability.md). Every test runs on mobile and on desktop.
 * Backend (profile dev) and frontend are started if they are not already running. The tests reset the
 * database before every test: to keep an app you are trying out by hand, run scripts/e2e-isolated.sh,
 * which gives the tests their own database and ports.
 * The database must be running: scripts/db-start.sh (local) or service container (CI).
 */
export default defineConfig({
  testDir: './tests',
  // All tests share one database and reset it: run them one after another.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:4200',
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
      use: { viewport: { width: 1280, height: 800 } },
    },
  ],
  // scripts/e2e-isolated.sh starts its own backend and frontend on other ports and passes BASE_URL
  webServer: process.env.E2E_NO_WEBSERVER ? undefined : [
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
