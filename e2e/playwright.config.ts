import { defineConfig, devices } from '@playwright/test';

/**
 * E2E-Tests (docs/testbarkeit.md). Jeder Test läuft mobil und auf dem Desktop.
 * Backend (Profil dev) und Frontend werden gestartet, falls sie nicht schon laufen.
 * Die Datenbank muss laufen: scripts/db-start.sh (lokal) bzw. Service-Container (CI).
 */
export default defineConfig({
  testDir: './tests',
  // Alle Tests teilen sich eine Datenbank und setzen sie zurück: nacheinander ausführen.
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
      name: 'mobil',
      use: { viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2, userAgent: devices['Pixel 7'].userAgent },
    },
    {
      name: 'desktop',
      use: { viewport: { width: 1280, height: 800 } },
    },
  ],
  webServer: [
    {
      command: 'mvn -B -q quarkus:dev -Dquarkus.console.enabled=false',
      cwd: '../backend',
      url: 'http://localhost:8080/api/test/uhr',
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
