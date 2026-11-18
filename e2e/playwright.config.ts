import { defineConfig } from '@playwright/test';

// Browser end-to-end tests for Matriz. Not part of `mvn verify`.
// Run from this folder: `npx playwright test` (see tests/create-task.spec.ts for the variables).
// Chromium: by default Playwright uses its own Chromium (install it once with `npx playwright install chromium`,
// or point PLAYWRIGHT_BROWSERS_PATH at a folder that already has it). To use another Chromium build,
// set E2E_CHROMIUM_PATH to its executable.
const chromiumPath = process.env.E2E_CHROMIUM_PATH;
// Chromium refuses to start its sandbox as root (typical in containers), so --no-sandbox is added only then.
const runsAsRoot = process.getuid?.() === 0;

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
      ...(chromiumPath ? { executablePath: chromiumPath } : {}),
      args: runsAsRoot ? ['--no-sandbox'] : [],
    },
  },
});
