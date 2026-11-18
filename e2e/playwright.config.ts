import { defineConfig } from '@playwright/test';

// Browser end-to-end tests for Matriz. Not part of `mvn verify`.
// Run from this folder: `npx playwright test` (see tests/create-task.spec.ts for the variables).
// Chromium: the preinstalled build is used; override its path with E2E_CHROMIUM_PATH.
const chromiumPath =
  process.env.E2E_CHROMIUM_PATH ?? '/opt/pw-browsers/chromium-1243/chrome-linux64/chrome';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
    launchOptions: {
      executablePath: chromiumPath,
      // Needed when the browser runs as root in a container.
      args: ['--no-sandbox'],
    },
  },
});
